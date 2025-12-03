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
package org.neo4j.fabric.bolt;

import org.neo4j.bolt.dbapi.BoltQueryExecution;
import org.neo4j.cypher.internal.javacompat.ResultSubscriber;
import org.neo4j.fabric.executor.Exceptions;
import org.neo4j.fabric.stream.Record;
import org.neo4j.fabric.stream.StatementResult;
import org.neo4j.fabric.stream.summary.Summary;
import org.neo4j.graphdb.ExecutionPlanDescription;
import org.neo4j.graphdb.GqlStatusObject;
import org.neo4j.graphdb.Notification;
import org.neo4j.graphdb.QueryExecutionType;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.impl.query.QueryExecution;
import org.neo4j.kernel.impl.query.QuerySubscriber;

public class BoltQueryExecutionImpl implements BoltQueryExecution {
    private final QueryExecutionImpl queryExecution;
    private final QuerySubscriber subscriber;

    public BoltQueryExecutionImpl(StatementResult statementResult, QuerySubscriber subscriber) {
        this.subscriber = subscriber;
        queryExecution = new QueryExecutionImpl(statementResult, subscriber);
    }

    public void initialize() throws Exception {
        // Mimic eager execution as triggered in org.neo4j.cypher.internal.result.StandardInternalExecutionResult

        boolean isWriteOnly = queryExecution.executionType().queryType() == QueryExecutionType.QueryType.WRITE;
        boolean isReadOnly = queryExecution.executionType().queryType() == QueryExecutionType.QueryType.READ_ONLY;
        boolean isExplain = queryExecution.executionType().isExplained();
        boolean noResult = queryExecution.fieldNames().length == 0;

        boolean triggerArtificialDemand = isWriteOnly || isExplain || noResult;

        if (triggerArtificialDemand) {
            queryExecution.request(1);
            queryExecution.await();
        }

        if (subscriber instanceof ResultSubscriber rs && (!isReadOnly || isExplain)) {
            rs.materialize(queryExecution);
        }
    }

    @Override
    public QueryExecution queryExecution() {
        return queryExecution;
    }

    @Override
    public void close() {}

    @Override
    public void terminate() {
        queryExecution.cancel();
    }

    private static class QueryExecutionImpl implements QueryExecution {

        private final StatementResult statementResult;
        private final QuerySubscriber subscriber;
        private boolean hasMore = true;
        private boolean initialised;
        private Summary cachedSummary;

        private QueryExecutionImpl(StatementResult statementResult, QuerySubscriber subscriber) {
            this.statementResult = statementResult;
            this.subscriber = subscriber;
        }

        private Summary getSummary() {
            if (cachedSummary == null) {
                cachedSummary = statementResult.consume();
            }
            return cachedSummary;
        }

        @Override
        public QueryExecutionType executionType() {
            return statementResult.executionType();
        }

        @Override
        public ExecutionPlanDescription executionPlanDescription() {
            return getSummary().executionPlanDescription();
        }

        @Override
        public Iterable<Notification> getNotifications() {
            return getSummary().getNotifications();
        }

        @Override
        public Iterable<GqlStatusObject> getGqlStatusObjects() {
            return getSummary().getGqlStatusObjects();
        }

        @Override
        public String[] fieldNames() {
            return statementResult.columns().toArray(new String[0]);
        }

        @Override
        public void request(long numberOfRecords) throws Exception {
            if (!hasMore) {
                return;
            }

            if (!initialised) {
                initialised = true;
                subscriber.onResult(statementResult.columns().size());
            }

            try {
                for (int i = 0; i < numberOfRecords; i++) {
                    Record record = statementResult.next();

                    if (record == null) {
                        hasMore = false;
                        subscriber.onResultCompleted(getSummary().getQueryStatistics());
                        return;
                    }

                    subscriber.onRecord();
                    publishFields(record);
                    subscriber.onRecordCompleted();
                }
            } catch (Exception e) {
                throw Exceptions.transformUnexpectedError(Status.Statement.ExecutionFailed, e);
            }
        }

        private void publishFields(Record record) throws Exception {
            for (int i = 0; i < statementResult.columns().size(); i++) {
                subscriber.onField(i, record.getValue(i));
            }
        }

        @Override
        public void cancel() {
            getSummary();
        }

        @Override
        public boolean await() {
            return hasMore;
        }
    }
}
