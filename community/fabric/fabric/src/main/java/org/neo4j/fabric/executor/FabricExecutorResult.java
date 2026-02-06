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

import static org.neo4j.notifications.StandardGqlStatusObject.isStandardGqlStatusCode;

import java.util.HashSet;
import java.util.List;
import org.neo4j.fabric.stream.FragmentResult;
import org.neo4j.fabric.stream.Record;
import org.neo4j.fabric.stream.StatementResult;
import org.neo4j.fabric.stream.summary.MergedSummary;
import org.neo4j.fabric.stream.summary.Summary;
import org.neo4j.graphdb.GqlStatusObject;
import org.neo4j.graphdb.Notification;
import org.neo4j.graphdb.QueryExecutionType;
import org.neo4j.notifications.NotificationImplementation;
import org.neo4j.notifications.StandardGqlStatusObject;

class FabricExecutorResult implements StatementResult {

    private final FragmentResult fragmentResult;
    private final List<NotificationImplementation> planNotifications;
    private final boolean produceResults;
    private final QueryStatementLifecycles.StatementLifecycle lifecycle;
    private final ProfilingContext profilingContext;
    private StandardGqlStatusObject standardGqlStatusObject;

    FabricExecutorResult(
            FragmentResult fragmentResult,
            List<NotificationImplementation> planNotifications,
            boolean produceResults,
            QueryStatementLifecycles.StatementLifecycle lifecycle,
            ProfilingContext profilingContext) {
        this.fragmentResult = fragmentResult;
        this.planNotifications = planNotifications;
        this.produceResults = produceResults;
        this.lifecycle = lifecycle;
        this.profilingContext = profilingContext;
        if (produceResults) {
            standardGqlStatusObject = StandardGqlStatusObject.NO_DATA;
        } else {
            standardGqlStatusObject = StandardGqlStatusObject.OMITTED_RESULT;
        }
    }

    @Override
    public List<String> columns() {
        if (produceResults) {
            return fragmentResult.columns();
        }

        return List.of();
    }

    @Override
    public Record next() {
        try {
            if (produceResults) {
                var record = fragmentResult.next();
                if (record == null) {
                    lifecycle.endSuccess();
                } else {
                    standardGqlStatusObject = StandardGqlStatusObject.SUCCESS;
                }

                return record;
            }

            while (fragmentResult.next() != null) {}

            lifecycle.endSuccess();
            return null;
        } catch (RuntimeException e) {
            lifecycle.endFailure(e);
            throw e;
        }
    }

    @Override
    public Summary consume() {
        var executionSummary = fragmentResult.consume();
        profilingContext.close();
        var mergedNotifications = new HashSet<Notification>();
        mergedNotifications.addAll(planNotifications);
        mergedNotifications.addAll(executionSummary.getNotifications());

        var mergedGqlStatusObjects = new HashSet<GqlStatusObject>();
        mergedGqlStatusObjects.addAll(planNotifications);
        mergedGqlStatusObjects.addAll(executionSummary.getGqlStatusObjects().stream()
                .filter(gso -> !isStandardGqlStatusCode(gso.gqlStatus()))
                .toList());
        mergedGqlStatusObjects.add(standardGqlStatusObject);
        return new MergedSummary(
                null, executionSummary.getQueryStatistics(), mergedNotifications, mergedGqlStatusObjects);
    }

    @Override
    public QueryExecutionType executionType() {
        var executionType = fragmentResult.executionType();
        // Query profiling works differently for composite queries.
        // It does not return a profiled plan, but writes the profiling information
        // into a file. However, Bolt Server does not like if a profiled query
        // does not return a plan.
        if (executionType.isProfiled()) {
            return QueryExecutionType.query(executionType.queryType());
        }

        return fragmentResult.executionType();
    }
}
