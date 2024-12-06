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

import java.util.Map;
import org.neo4j.bolt.protocol.common.message.AccessMode;
import org.neo4j.cypher.internal.preparser.FullyParsedQuery;
import org.neo4j.fabric.eval.UseEvaluation;
import org.neo4j.fabric.planning.FabricPlan;
import org.neo4j.fabric.planning.FabricPlanner;
import org.neo4j.fabric.planning.Fragment;
import org.neo4j.fabric.stream.FragmentResult;
import org.neo4j.fabric.stream.QueryInput;
import org.neo4j.fabric.stream.Record;
import org.neo4j.fabric.stream.StatementResult;
import org.neo4j.fabric.stream.StatementResults;
import org.neo4j.fabric.transaction.FabricTransaction;
import org.neo4j.fabric.transaction.TransactionMode;
import org.neo4j.kernel.impl.query.QueryRoutingMonitor;
import org.neo4j.values.AnyValue;
import org.neo4j.values.virtual.MapValue;
import org.neo4j.values.virtual.MapValueBuilder;
import org.neo4j.values.virtual.VirtualValues;

/**
 * Standard query means other than CALL IN TRANSACTION in this context.
 */
class StandardQueryExecutor extends SingleQueryFragmentExecutor {

    private final Fragment.Exec fragment;

    StandardQueryExecutor(
            Fragment.Exec fragment,
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
        super(
                plannerInstance,
                ctx,
                useEvaluator,
                plan,
                queryParams,
                accessMode,
                lifecycle,
                queryRoutingMonitor,
                tracer,
                fragmentExecutor);
        this.fragment = fragment;
    }

    FragmentResult run(Record argument) {
        var prepareResult = prepare(fragment, argument);
        MapValue parameters =
                addParamsFromRecord(queryParams(), prepareResult.argumentValues(), asJava(fragment.parameters()));
        return doExecuteFragment(
                fragment, parameters, prepareResult.graph(), prepareResult.transactionMode(), () -> fragmentExecutor()
                        .run(fragment.input(), argument));
    }

    @Override
    FragmentResult runRemote(
            Location.Remote location,
            ExecutionOptions options,
            String query,
            TransactionMode transactionMode,
            MapValue params) {
        var result = ctx().getRemote().run(location, options, query, transactionMode, params);
        return StatementResults.toFragmentResult(result);
    }

    @Override
    FragmentResult runLocal(
            Location.Local location,
            TransactionMode transactionMode,
            QueryStatementLifecycles.StatementLifecycle parentLifecycle,
            FullyParsedQuery query,
            MapValue params,
            FragmentResult input,
            ExecutionOptions executionOptions,
            Boolean targetsComposite) {
        var queryInput = new QueryInput() {

            @Override
            public Record next() {
                return input.next();
            }

            @Override
            public void consume() {
                input.consume();
            }
        };

        StatementResult result = ctx().getLocal()
                .run(
                        location,
                        transactionMode,
                        parentLifecycle,
                        query,
                        params,
                        queryInput,
                        executionOptions,
                        targetsComposite);
        return StatementResults.toFragmentResult(result);
    }

    private MapValue addParamsFromRecord(MapValue params, Map<String, AnyValue> record, Map<String, String> bindings) {
        int resultSize = params.size() + bindings.size();
        if (resultSize == 0) {
            return VirtualValues.EMPTY_MAP;
        }
        MapValueBuilder builder = new MapValueBuilder(resultSize);
        params.foreach(builder::add);
        bindings.forEach((var, par) -> builder.add(par, validateValue(record.get(var))));
        return builder.build();
    }
}
