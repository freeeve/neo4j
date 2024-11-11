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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.neo4j.graphdb.ExecutionPlanDescription;
import org.neo4j.graphdb.GqlStatusObject;
import org.neo4j.graphdb.Notification;
import org.neo4j.notifications.NotificationImplementation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MergedSummaryBuilder {

    private final List<Mono<Summary>> summaries = new ArrayList<>();
    private final List<NotificationImplementation> planNotifications;

    public MergedSummaryBuilder(List<NotificationImplementation> planNotifications) {
        this.planNotifications = planNotifications;
    }

    public synchronized void addSummary(Mono<Summary> summary) {
        summaries.add(summary);
    }

    public Mono<Summary> build(Mono<ExecutionPlanDescription> executionPlanDescription) {
        return Flux.merge(summaries).collectList().map(collectedSummaries -> {
            var statistics = new MergedQueryStatistics();
            var notifications = new HashSet<Notification>(planNotifications);
            var gqlStatusObjects = new HashSet<GqlStatusObject>(planNotifications);
            Collection<GqlStatusObject> lastAddedGqlStatusObjects = null;
            for (var summary : collectedSummaries) {
                statistics.add(summary.getQueryStatistics());
                notifications.addAll(summary.getNotifications());
                gqlStatusObjects.addAll(summary.getGqlStatusObjects());
                lastAddedGqlStatusObjects = summary.getGqlStatusObjects();
            }

            return new MergedSummary(
                    executionPlanDescription, statistics, notifications, gqlStatusObjects, lastAddedGqlStatusObjects);
        });
    }
}
