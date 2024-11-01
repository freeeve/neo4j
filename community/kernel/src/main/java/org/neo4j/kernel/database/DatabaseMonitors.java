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
package org.neo4j.kernel.database;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.logging.internal.DatabaseLogProvider;
import org.neo4j.monitoring.Monitors;
import org.neo4j.util.VisibleForTesting;

public class DatabaseMonitors extends Monitors implements Lifecycle {

    private final Set<Object> registeredMonitors = ConcurrentHashMap.newKeySet();

    public DatabaseMonitors(Monitors parentMonitors, DatabaseLogProvider internalLogProvider) {
        super(parentMonitors, internalLogProvider);
    }

    @Override
    public void addMonitorListener(Object monitorListener, String... tags) {
        registeredMonitors.add(monitorListener);
        super.addMonitorListener(monitorListener, tags);
    }

    @Override
    public void removeMonitorListener(Object monitorListener) {
        registeredMonitors.remove(monitorListener);
        super.removeMonitorListener(monitorListener);
    }

    @Override
    public void init() throws Exception {}

    @Override
    public void start() throws Exception {}

    @Override
    public void stop() throws Exception {}

    @Override
    public void shutdown() throws Exception {
        registeredMonitors.forEach(super::removeMonitorListener);
        registeredMonitors.clear();
    }

    @VisibleForTesting
    public Set<Object> getRegisteredMonitors() {
        return registeredMonitors;
    }
}
