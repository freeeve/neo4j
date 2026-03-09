/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.fabric.executor;

import static scala.jdk.javaapi.CollectionConverters.asJava;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.neo4j.boltmessages.AccessMode;
import org.neo4j.cypher.internal.ast.GraphSelection;
import org.neo4j.cypher.internal.expressions.AutoExtractedParameter;
import org.neo4j.cypher.internal.expressions.Expression;
import org.neo4j.cypher.internal.preparser.FullyParsedQuery;
import org.neo4j.cypher.internal.runtime.CypherRow;
import org.neo4j.exceptions.InvalidSemanticsException;
import org.neo4j.fabric.eval.Catalog;
import org.neo4j.fabric.eval.UseEvaluation;
import org.neo4j.fabric.planning.FabricPlan;
import org.neo4j.fabric.planning.FabricPlanner;
import org.neo4j.fabric.planning.FabricQuery;
import org.neo4j.fabric.planning.Fragment;
import org.neo4j.fabric.planning.QueryType;
import org.neo4j.fabric.stream.DelegatingFragmentResult;
import org.neo4j.fabric.stream.FragmentResult;
import org.neo4j.fabric.stream.Record;
import org.neo4j.fabric.stream.Records;
import org.neo4j.fabric.stream.StatementResults;
import org.neo4j.fabric.stream.summary.PlanlessSummary;
import org.neo4j.fabric.transaction.FabricTransaction;
import org.neo4j.fabric.transaction.TransactionMode;
import org.neo4j.graphdb.QueryExecutionType;
import org.neo4j.kernel.database.DatabaseReference;
import org.neo4j.kernel.impl.query.QueryRoutingMonitor;
import org.neo4j.notifications.NotificationImplementation;
import org.neo4j.values.AnyValue;
import org.neo4j.values.virtual.MapValue;
import org.neo4j.values.virtual.MapValueBuilder;
import org.neo4j.values.virtual.PathValue;
import org.neo4j.values.virtual.VirtualNodeValue;
import org.neo4j.values.virtual.VirtualRelationshipValue;
import org.neo4j.values.virtual.VirtualValues;

abstract class SingleQueryFragmentExecutor {

    private final FabricPlanner.PlannerInstance plannerInstance;
    private final FabricTransaction.FabricExecutionContext ctx;
    private final UseEvaluation.Instance useEvaluator;
    private final FabricPlan plan;
    private final MapValue queryParams;
    private final AccessMode accessMode;
    private final QueryStatementLifecycles.StatementLifecycle lifecycle;
    private final QueryRoutingMonitor queryRoutingMonitor;
    private final Tracer tracer;
    private final FragmentExecutor fragmentExecutor;

    SingleQueryFragmentExecutor(
            FabricPlanner.PlannerInstance plannerInstance,
            FabricTransaction.FabricExecutionContext ctx,
            UseEvaluation.Instance useEvaluator,
            FabricPlan plan,
            MapValue queryParams,
            AccessMode accessMode,
            QueryStatementLifecycles.StatementLifecycle lifecycle,
            QueryRoutingMonitor queryRoutingMonitor,
            Tracer tracer,
            FragmentExecutor fragmentExecutor) {
        this.plannerInstance = plannerInstance;
        this.ctx = ctx;
        this.useEvaluator = useEvaluator;
        this.plan = plan;
        this.queryParams = queryParams;
        this.accessMode = accessMode;
        this.lifecycle = lifecycle;
        this.queryRoutingMonitor = queryRoutingMonitor;
        this.tracer = tracer;
        this.fragmentExecutor = fragmentExecutor;
    }

    MapValue queryParams() {
        return queryParams;
    }

    FabricTransaction.FabricExecutionContext ctx() {
        return ctx;
    }

    FragmentExecutor fragmentExecutor() {
        return fragmentExecutor;
    }

    PrepareResult prepare(Fragment.Exec fragment, Record argument) {
        ctx.validateStatementType(fragment.query(), fragment.statementType());
        Map<String, AnyValue> argumentValues = argumentValues(fragment, argument);

        Catalog.GraphWithNotification graph =
                evalUse(fragment.use().graphSelection(), argumentValues, ctx.getSessionDatabaseReference());

        validateCanUseGraph(graph.graph(), ctx.getSessionDatabaseReference());

        var transactionMode = getTransactionMode(
                fragment.queryType(), graph.graph().reference().toPrettyString());
        return new PrepareResult(graph, argumentValues, transactionMode);
    }

    FragmentResult doExecuteFragment(
            Fragment.Exec fragment,
            MapValue parameters,
            Catalog.Graph graph,
            TransactionMode transactionMode,
            Supplier<FragmentResult> executeFragmentInput,
            List<NotificationImplementation> notifications) {
        var location = this.ctx.locationOf(graph, transactionMode.requiresWrite());

        if (location instanceof Location.Local local) {
            FragmentResult input = executeFragmentInput.get();
            if (fragment.executable()) {
                FabricQuery.LocalQuery localQuery = plannerInstance.asLocal(fragment);
                var targetsComposite = plannerInstance.targetsComposite(fragment);
                FragmentResult fragmentResult = runLocalQueryAt(
                        local, transactionMode, localQuery.query(), parameters, targetsComposite, input);
                var executionType = StatementResults.merge(input.executionType(), fragmentResult.executionType());
                return new DelegatingFragmentResult(fragmentResult) {

                    @Override
                    public QueryExecutionType executionType() {
                        return executionType;
                    }

                    @Override
                    public PlanlessSummary consume() {
                        PlanlessSummary summary = PlanlessSummary.merge(input.consume(), super.consume());
                        if (notifications != null) {
                            summary.getNotifications().addAll(notifications);
                            summary.getGqlStatusObjects().addAll(notifications);
                        }
                        return summary;
                    }
                };
            } else {
                return input;
            }
        } else if (location instanceof Location.Remote remote) {
            FabricQuery.RemoteQuery remoteQuery = plannerInstance.asRemote(fragment);
            var extracted = asJava(remoteQuery.extractedLiterals());
            var builder = new MapValueBuilder();
            var evaluator = useEvaluator.evaluator();
            for (Map.Entry<AutoExtractedParameter, Expression> entry : extracted.entrySet()) {
                builder.add(
                        entry.getKey().name(),
                        evaluator.evaluate(entry.getValue(), VirtualValues.EMPTY_MAP, CypherRow.empty()));
            }
            MapValue fullParams = parameters.updatedWith(builder.build());

            var fragmentResult = runRemoteQueryAt(remote, transactionMode, remoteQuery.query(), fullParams);
            return new DelegatingFragmentResult(fragmentResult) {

                @Override
                public PlanlessSummary consume() {
                    PlanlessSummary summary = fragmentResult.consume();
                    if (notifications != null) {
                        summary.getNotifications().addAll(notifications);
                        summary.getGqlStatusObjects().addAll(notifications);
                    }
                    return summary;
                }
            };
        } else {
            throw notImplemented("Invalid graph location", location);
        }
    }

    abstract FragmentResult runRemote(
            Location.Remote location,
            ExecutionOptions options,
            String query,
            TransactionMode transactionMode,
            MapValue params);

    abstract FragmentResult runLocal(
            Location.Local location,
            TransactionMode transactionMode,
            QueryStatementLifecycles.StatementLifecycle parentLifecycle,
            FullyParsedQuery query,
            MapValue params,
            FragmentResult input,
            ExecutionOptions executionOptions,
            Boolean targetsComposite);

    private RuntimeException notImplemented(String msg, Object object) {
        return notImplemented(msg, object.toString());
    }

    private RuntimeException notImplemented(String msg, String info) {
        return InvalidSemanticsException.internalError(this.getClass().getSimpleName(), msg + ": " + info);
    }

    private FragmentResult runRemoteQueryAt(
            Location.Remote location, TransactionMode transactionMode, String queryString, MapValue parameters) {
        var recordTracer = tracer.remoteQueryStart(location, queryString);
        ExecutionOptions executionOptions =
                plan.inCompositeContext() ? new ExecutionOptions(location.graphId()) : new ExecutionOptions();

        lifecycle.startExecution(true);
        FragmentResult remoteResult = runRemote(location, executionOptions, queryString, transactionMode, parameters);
        FragmentResult adjustedResult = new DelegatingFragmentResult(remoteResult) {

            @Override
            public QueryExecutionType executionType() {
                // TODO: We currently need to override here since we can't get it from remote properly
                // but our result here is not as accurate as what the remote might report.
                return EffectiveQueryType.queryExecutionType(plan, accessMode);
            }
        };

        if (location instanceof Location.Remote.Internal) {
            queryRoutingMonitor.queryRoutedRemoteInternal();
        } else if (location instanceof Location.Remote.External) {
            queryRoutingMonitor.queryRoutedRemoteExternal();
        }

        return recordTracer.traceRecords(adjustedResult);
    }

    private FragmentResult runLocalQueryAt(
            Location.Local location,
            TransactionMode transactionMode,
            FullyParsedQuery query,
            MapValue parameters,
            boolean targetsComposite,
            FragmentResult input) {
        var recordTracer = tracer.localQueryStart(location, query);

        ExecutionOptions executionOptions = plan.inCompositeContext() && !targetsComposite
                ? new ExecutionOptions(location.graphId())
                : new ExecutionOptions();

        FragmentResult localStatementResult = runLocal(
                location, transactionMode, lifecycle, query, parameters, input, executionOptions, targetsComposite);

        queryRoutingMonitor.queryRoutedLocal();

        return recordTracer.traceRecords(localStatementResult);
    }

    static Map<String, AnyValue> argumentValues(Fragment fragment, Record argument) {
        if (argument == null) {
            return Map.of();
        } else {
            return Records.asMap(argument, asJava(fragment.argumentColumns()));
        }
    }

    private Catalog.GraphWithNotification evalUse(
            GraphSelection selection, Map<String, AnyValue> record, DatabaseReference sessionDb) {
        return useEvaluator.evaluate(selection, queryParams, record, sessionDb);
    }

    private void validateCanUseGraph(Catalog.Graph accessedGraph, DatabaseReference sessionDatabaseReference) {
        var sessionGraph = useEvaluator.resolveGraph(sessionDatabaseReference.alias());

        if (sessionGraph instanceof Catalog.Composite) {
            if (!useEvaluator.isConstituentOrSelf(accessedGraph, sessionGraph)) {
                if (!useEvaluator.isSystem(accessedGraph)) {
                    throw InvalidSemanticsException.unsupportedAccessOfStandardDb(
                            useEvaluator.simplifiedQualifiedNameString(accessedGraph),
                            useEvaluator.simplifiedQualifiedNameString(sessionGraph));
                }
            }
        } else {
            if (!useEvaluator.isDatabaseOrAliasInRoot(accessedGraph)) {
                throw InvalidSemanticsException.unsupportedAccessOfCompositeDatabase(
                        useEvaluator.simplifiedQualifiedNameString(accessedGraph),
                        useEvaluator.simplifiedQualifiedNameString(sessionGraph));
            }
        }
    }

    private TransactionMode getTransactionMode(QueryType queryType, String graph) {
        return getTransactionMode(plan, accessMode, queryType, graph);
    }

    static TransactionMode getTransactionMode(
            FabricPlan plan, AccessMode accessMode, QueryType queryType, String graph) {
        var executionType = plan.executionType();
        var queryMode = EffectiveQueryType.effectiveAccessMode(accessMode, executionType, queryType);

        if (accessMode == AccessMode.WRITE) {
            if (queryMode == AccessMode.WRITE) {
                return TransactionMode.DEFINITELY_WRITE;
            } else {
                return TransactionMode.MAYBE_WRITE;
            }
        } else {
            if (queryMode == AccessMode.WRITE) {
                throw FabricException.writingInReadAccessMode(graph);
            } else {
                return TransactionMode.DEFINITELY_READ;
            }
        }
    }

    AnyValue validateValue(AnyValue value, String variable, String graphName) {
        if (value instanceof VirtualNodeValue) {
            throw FabricException.importingValuesInRemoteSubqueries("node", variable, graphName);
        } else if (value instanceof VirtualRelationshipValue) {
            throw FabricException.importingValuesInRemoteSubqueries("relationship", variable, graphName);
        } else if (value instanceof PathValue) {
            throw FabricException.importingValuesInRemoteSubqueries("path", variable, graphName);
        } else {
            return value;
        }
    }

    record PrepareResult(
            Catalog.GraphWithNotification graphWithNotification,
            Map<String, AnyValue> argumentValues,
            TransactionMode transactionMode) {}

    interface Tracer {

        RecordTracer remoteQueryStart(Location.Remote location, String queryString);

        RecordTracer localQueryStart(Location.Local location, FullyParsedQuery query);
    }

    interface RecordTracer {
        FragmentResult traceRecords(FragmentResult fragmentResult);
    }

    interface FragmentExecutor {
        FragmentResult run(Fragment fragment, Record argument);
    }
}
