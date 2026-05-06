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
package org.neo4j.fleetmanagement.bootstrap;

import org.neo4j.fleetmanagement.configuration.ClusterSync;
import org.neo4j.fleetmanagement.configuration.State;
import org.neo4j.fleetmanagement.utils.Logger;
import org.neo4j.logging.Log;

public abstract class FleetManagerTask implements Runnable {
    protected final Log userLog;
    protected final Logger fleetManagerLog;
    protected final State state;
    protected final ClusterSync clusterSync;

    public FleetManagerTask(State state, ClusterSync clusterSync) {
        this.userLog = Logger.getNeo4jLogger();
        this.fleetManagerLog = Logger.getFleetManagerLogger();
        this.state = state;
        this.clusterSync = clusterSync;
    }

    @Override
    public void run() {
        var className = this.getClass().getSimpleName();
        try {
            fleetManagerLog.debug("Running %s", className);
            this.clusterSync.run();
            execute();
        } catch (Exception e) {
            userLog.error("Fleet manager failed to run " + className, e);
        } catch (Error e) {
            // Catch Error separately as it's not a subclass of Exception
            userLog.error("Fleet manager failed to run " + className + " with Error", e);
            throw e; // Re-throw Error as it indicates a serious problem
        }
    }

    protected abstract void execute();
}
