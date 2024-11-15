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
package org.neo4j.genai.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.event.DatabaseEventContext;
import org.neo4j.graphdb.event.DatabaseEventListenerAdapter;
import org.neo4j.kernel.extension.ExtensionFactory;
import org.neo4j.kernel.extension.context.ExtensionContext;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;
import org.neo4j.kernel.monitoring.DatabaseEventListeners;

/**
 * A utility class holding monitors to communicate with the metric system if that's present (enterprise edition only)
 */
public final class Monitors {

    // To avoid creating a new Proxy on each method call in a database
    private static final Map<String, Object> MONITORS = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T extends GenAIMonitor> T getMonitor(GraphDatabaseService graphDatabaseService, Class<T> type) {
        var databaseName = graphDatabaseService == null ? "n/a" : graphDatabaseService.databaseName();
        return (T) MONITORS.computeIfAbsent(
                databaseName + "-" + type.getCanonicalName(), key -> getMonitor0(graphDatabaseService, type));
    }

    private static void removeMonitorFor(String databaseName) {

        var prefix = databaseName + "-";
        MONITORS.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
    }

    private static <T extends GenAIMonitor> T getMonitor0(GraphDatabaseService graphDatabaseService, Class<T> type) {
        org.neo4j.monitoring.Monitors monitors;
        // Using the dependency resolver or graph database api directly would sandbox the UDFs and we don't want that,
        // so that this plugin keeps usable without any additional configuration.
        if (graphDatabaseService instanceof GraphDatabaseAPI graphDatabaseAPI) {
            monitors = graphDatabaseAPI.getDependencyResolver().resolveDependency(org.neo4j.monitoring.Monitors.class);
        } else {
            monitors = new org.neo4j.monitoring.Monitors();
        }
        return monitors.newMonitor(type);
    }

    @ServiceProvider
    public static class MonitorsCleaner extends ExtensionFactory<MonitorsCleaner.Dependencies> {

        public MonitorsCleaner() {
            super("genai-monitors-cleaner");
        }

        @Override
        public Lifecycle newInstance(ExtensionContext context, MonitorsCleaner.Dependencies dependencies) {

            return new LifecycleAdapter() {
                @Override
                public void start() {
                    dependencies
                            .databaseEventListeners()
                            .registerDatabaseEventListener(new DatabaseEventListenerAdapter() {

                                @Override
                                public void databaseShutdown(DatabaseEventContext eventContext) {
                                    removeMonitorFor(eventContext.getDatabaseName());
                                }

                                @Override
                                public void databasePanic(DatabaseEventContext eventContext) {
                                    removeMonitorFor(eventContext.getDatabaseName());
                                }
                            });
                }
            };
        }

        public interface Dependencies {

            DatabaseEventListeners databaseEventListeners();
        }
    }
}
