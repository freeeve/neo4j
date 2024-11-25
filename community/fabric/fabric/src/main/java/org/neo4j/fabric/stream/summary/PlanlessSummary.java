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
package org.neo4j.fabric.stream.summary;

import static org.neo4j.notifications.StandardGqlStatusObject.isStandardGqlStatusCode;

import java.util.Collection;
import java.util.HashSet;
import org.neo4j.graphdb.GqlStatusObject;
import org.neo4j.graphdb.Notification;
import org.neo4j.graphdb.QueryStatistics;

/**
 * Composite databases do not support PROFILE,
 * so there is no point in Composite runtime dealing with {@link Summary#executionPlanDescription()}
 * when executing a query.
 */
public record PlanlessSummary(
        Collection<Notification> getNotifications,
        Collection<GqlStatusObject> getGqlStatusObjects,
        QueryStatistics getQueryStatistics) {

    /**
     * Merges the two summaries.
     * Standard GQL status codes are taken only from the summary2.
     * The reason for that in many cases we want to take the standard
     * GQL status codes only from one summary.
     * For example when we merge summary from a subquery, we need to present only
     * the standard GQL status codes from the outer query. So a subquery summary
     * should be submitted as summary1 into this method.
     */
    public static PlanlessSummary merge(PlanlessSummary summary1, PlanlessSummary summary2) {
        var mergedStatistics = new MergedQueryStatistics();
        mergedStatistics.add(summary1.getQueryStatistics());
        mergedStatistics.add(summary2.getQueryStatistics());

        var mergedNotifications = new HashSet<Notification>();
        mergedNotifications.addAll(summary1.getNotifications());
        mergedNotifications.addAll(summary2.getNotifications());

        var mergedGqlStatusObjects = new HashSet<GqlStatusObject>();
        mergedGqlStatusObjects.addAll(summary1.getGqlStatusObjects().stream()
                .filter(gso -> isStandardGqlStatusCode(gso.gqlStatus()))
                .toList());
        mergedGqlStatusObjects.addAll(summary2.getGqlStatusObjects());

        return new PlanlessSummary(mergedNotifications, mergedGqlStatusObjects, mergedStatistics);
    }
}
