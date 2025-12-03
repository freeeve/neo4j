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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.neo4j.cypher.internal.javacompat.InternalQueryExecutionEngine;
import org.neo4j.cypher.internal.preparser.FullyParsedQuery;
import org.neo4j.cypher.internal.runtime.InputDataStream;
import org.neo4j.fabric.config.FabricConfig;
import org.neo4j.fabric.executor.QueryStatementLifecycles.StatementLifecycle;
import org.neo4j.fabric.stream.InputDataStreamImpl;
import org.neo4j.fabric.stream.QueryInput;
import org.neo4j.fabric.stream.Record;
import org.neo4j.fabric.stream.Records;
import org.neo4j.fabric.stream.SourceTagging;
import org.neo4j.fabric.stream.StatementResult;
import org.neo4j.fabric.stream.summary.Summary;
import org.neo4j.fabric.transaction.FabricTransactionInfo;
import org.neo4j.graphdb.QueryExecutionType;
import org.neo4j.graphdb.QueryStatistics;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;
import org.neo4j.kernel.impl.query.QueryExecution;
import org.neo4j.kernel.impl.query.QueryExecutionKernelException;
import org.neo4j.kernel.impl.query.QuerySubscriber;
import org.neo4j.kernel.impl.query.TransactionalContext;
import org.neo4j.kernel.impl.query.TransactionalContextFactory;
import org.neo4j.values.AnyValue;
import org.neo4j.values.virtual.ListValue;
import org.neo4j.values.virtual.MapValue;
import org.neo4j.values.virtual.MapValueBuilder;
import org.neo4j.values.virtual.NodeValue;
import org.neo4j.values.virtual.PathValue;
import org.neo4j.values.virtual.RelationshipValue;
import org.neo4j.values.virtual.VirtualNodeValue;
import org.neo4j.values.virtual.VirtualRelationshipValue;
import org.neo4j.values.virtual.VirtualValues;

public class FabricKernelTransaction {
    private final InternalQueryExecutionEngine queryExecutionEngine;
    private final TransactionalContextFactory transactionalContextFactory;
    private final InternalTransaction internalTransaction;
    private final FabricConfig config;
    private final Set<TransactionalContext> openExecutionContexts = ConcurrentHashMap.newKeySet();

    private final FabricTransactionInfo transactionInfo;

    FabricKernelTransaction(
            InternalQueryExecutionEngine queryExecutionEngine,
            TransactionalContextFactory transactionalContextFactory,
            InternalTransaction internalTransaction,
            FabricConfig config,
            FabricTransactionInfo transactionInfo) {
        this.queryExecutionEngine = queryExecutionEngine;
        this.transactionalContextFactory = transactionalContextFactory;
        this.internalTransaction = internalTransaction;
        this.config = config;
        this.transactionInfo = transactionInfo;
    }

    public StatementResult run(
            FullyParsedQuery query,
            MapValue params,
            QueryInput input,
            StatementLifecycle parentLifecycle,
            ExecutionOptions executionOptions) {
        var childExecutionContext = makeChildTransactionalContext(parentLifecycle);
        parentLifecycle.startExecution(true);
        var childQueryMonitor = parentLifecycle.getChildQueryMonitor();
        openExecutionContexts.add(childExecutionContext);

        var queryId = childExecutionContext.executingQuery().internalQueryId();
        try {
            var batchSize = config.getDataStream().getBatchSize();
            var sourcetagging = new Tagging(executionOptions.sourceId());
            Function<AnyValue, AnyValue> valueTagging =
                    executionOptions.addSourceTag() ? sourcetagging::toCompositeDatabaseValue : value -> value;
            var subscriber = new QuerySubscriberImpl(batchSize, valueTagging);
            var execution = queryExecutionEngine.executeQuery(
                    query, params, childExecutionContext, true, convert(input), childQueryMonitor, subscriber);
            return new StatementResultImpl(execution, subscriber, batchSize, childExecutionContext);
        } catch (QueryExecutionKernelException e) {
            // all exception thrown from execution engine are wrapped in QueryExecutionKernelException,
            // let's see if there is something better hidden in it
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw Exceptions.transformUnexpectedError(Status.Statement.ExecutionFailed, cause, queryId);
        }
    }

    private TransactionalContext makeChildTransactionalContext(StatementLifecycle lifecycle) {
        var parentQuery = lifecycle.getMonitoredQuery();
        var queryExecutionConfiguration = transactionInfo.getQueryExecutionConfiguration();

        if (lifecycle instanceof QueryStatementLifecycles.StatementLifecycleImpl li
                && li.isParentChildMonitoringMode()) {
            // Cypher engine reports separately for each child query
            String queryText = "Internal query for parent query id: " + parentQuery.id();
            MapValue params = MapValue.EMPTY;
            return transactionalContextFactory.newContext(
                    internalTransaction, queryText, parentQuery, params, queryExecutionConfiguration);
        } else {
            // Cypher engine reports directly to parent query
            return transactionalContextFactory.newContextForQuery(
                    internalTransaction, parentQuery, queryExecutionConfiguration);
        }
    }

    private InputDataStream convert(QueryInput input) {
        return new InputDataStreamImpl(input);
    }

    public void commit() {
        if (internalTransaction.isOpen()) {
            closeContexts();
            internalTransaction.commit();
        }
    }

    public void rollback() {
        if (internalTransaction.isOpen()) {
            closeContexts();
            internalTransaction.rollback();
        }
    }

    private void closeContexts() {
        openExecutionContexts.forEach(TransactionalContext::close);
    }

    public void terminate(Status reason) {
        terminateIfPossible(reason);
    }

    public void terminateIfPossible(Status reason) {
        if (internalTransaction.isOpen()
                && internalTransaction.terminationReason().isEmpty()) {
            internalTransaction.terminate(reason);
        }
    }

    /**
     * This is a hack to be able to get an InternalTransaction for the TestFabricTransaction tx wrapper
     */
    @Deprecated
    public InternalTransaction getInternalTransaction() {
        return internalTransaction;
    }

    public long transactionSequenceNumber() {
        return internalTransaction.kernelTransaction().getTransactionSequenceNumber();
    }

    private class StatementResultImpl implements StatementResult {

        private final QueryExecution queryExecution;
        private final QuerySubscriberImpl querySubscriber;
        private final int batchSize;
        private final TransactionalContext executionContext;

        private StatementResultImpl(
                QueryExecution queryExecution,
                QuerySubscriberImpl querySubscriber,
                int batchSize,
                TransactionalContext executionContext) {
            this.queryExecution = queryExecution;
            this.querySubscriber = querySubscriber;
            this.batchSize = batchSize;
            this.executionContext = executionContext;
        }

        @Override
        public List<String> columns() {
            return Arrays.asList(queryExecution.fieldNames());
        }

        @Override
        public Record next() {
            var record = querySubscriber.batch.poll();
            if (record != null) {
                return record;
            }

            if (querySubscriber.error != null) {
                throw Exceptions.transformUnexpectedError(Status.Statement.ExecutionFailed, querySubscriber.error);
            }

            // Presence of statistics means that the stream has been consumed.
            if (querySubscriber.statistics != null) {
                if (executionContext.isOpen()) {
                    openExecutionContexts.remove(executionContext);
                    executionContext.close();
                }
                return null;
            }

            // If we are here, it means that the batch has been consumed, but not the entire stream,
            // we have request more
            try {
                queryExecution.request(batchSize);
                queryExecution.await();
            } catch (Exception e) {
                throw Exceptions.transformUnexpectedError(Status.Statement.ExecutionFailed, e);
            }

            // If we are here, it means that the batch was consumed, but not the entire stream,
            // we have requested more and therefore have to go through the method again.
            return next();
        }

        @Override
        public Summary consume() {
            if (querySubscriber.statistics == null) {
                queryExecution.cancel();
            }

            return new LocalExecutionSummary(queryExecution, querySubscriber.statistics);
        }

        @Override
        public QueryExecutionType executionType() {
            return queryExecution.executionType();
        }
    }

    private static class QuerySubscriberImpl implements QuerySubscriber {

        private final Function<AnyValue, AnyValue> valueTagging;
        private QueryStatistics statistics = null;
        private final Queue<Record> batch;
        private int numberOfFields = -1;
        private List<AnyValue> recordValues = null;
        private Throwable error;

        private QuerySubscriberImpl(int batchSize, Function<AnyValue, AnyValue> valueTagging) {
            this.batch = new ArrayDeque<>(batchSize);
            this.valueTagging = valueTagging;
        }

        @Override
        public void onResult(int numberOfFields) {
            this.numberOfFields = numberOfFields;
        }

        @Override
        public void onRecord() {
            this.recordValues = new ArrayList<>(numberOfFields);
        }

        @Override
        public void onField(int offset, AnyValue value) {
            recordValues.add(offset, valueTagging.apply(value));
        }

        @Override
        public void onRecordCompleted() {
            batch.add(Records.of(recordValues));
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onResultCompleted(QueryStatistics statistics) {
            this.statistics = statistics;
        }
    }

    private static class Tagging {

        private final long sourceTagId;
        private final long sourceId;

        public Tagging(long sourceId) {
            this.sourceTagId = SourceTagging.makeSourceTag(sourceId);
            this.sourceId = sourceId;
        }

        private AnyValue toCompositeDatabaseValue(AnyValue value) {
            if (value instanceof VirtualNodeValue) {
                if (value instanceof NodeValue node) {
                    return toCompositeDatabaseValue(node);
                } else {
                    throw unableToTagError(value);
                }
            } else if (value instanceof VirtualRelationshipValue) {
                if (value instanceof RelationshipValue rel) {
                    return toCompositeDatabaseValue(rel);
                } else {
                    throw unableToTagError(value);
                }
            } else if (value instanceof PathValue pv) {
                return toCompositeDatabaseValue(pv);
            } else if (value instanceof ListValue lv) {
                return toCompositeDatabaseValue(lv);
            } else if (value instanceof MapValue mv) {
                return toCompositeDatabaseValue(mv);
            } else {
                return value;
            }
        }

        private NodeValue toCompositeDatabaseValue(NodeValue n) {
            return VirtualValues.compositeGraphNodeValue(
                    tag(n.id()), n.elementId(), sourceId, n.labels(), n.properties());
        }

        private RelationshipValue toCompositeDatabaseValue(RelationshipValue r) {
            return VirtualValues.compositeGraphRelationshipValue(
                    r.id(),
                    r.elementId(),
                    sourceId,
                    VirtualValues.node(tag(r.startNodeId()), r.startNode().elementId(), sourceId),
                    VirtualValues.node(tag(r.endNodeId()), r.endNode().elementId(), sourceId),
                    r.type(),
                    r.properties());
        }

        private PathValue toCompositeDatabaseValue(PathValue pathValue) {
            return VirtualValues.path(
                    Arrays.stream(pathValue.nodes())
                            .map(this::toCompositeDatabaseValue)
                            .toArray(NodeValue[]::new),
                    Arrays.stream(pathValue.relationships())
                            .map(this::toCompositeDatabaseValue)
                            .toArray(RelationshipValue[]::new));
        }

        private ListValue toCompositeDatabaseValue(ListValue listValue) {
            return VirtualValues.list(Arrays.stream(listValue.asArray())
                    .map(this::toCompositeDatabaseValue)
                    .toArray(AnyValue[]::new));
        }

        private MapValue toCompositeDatabaseValue(MapValue mapValue) {
            if (mapValue.isEmpty()) {
                return mapValue;
            }
            MapValueBuilder builder = new MapValueBuilder(mapValue.size());
            mapValue.foreach((key, value) -> builder.add(key, toCompositeDatabaseValue(value)));
            return builder.build();
        }

        private long tag(long id) {
            return SourceTagging.tagId(id, sourceTagId);
        }

        private static FabricException unableToTagError(AnyValue value) {
            return FabricException.internalError(
                    FabricKernelTransaction.class.getSimpleName(),
                    Status.General.UnknownError,
                    "Unable to add graph id to entity of type " + value.getTypeName());
        }
    }
}
