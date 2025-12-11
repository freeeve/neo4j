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
package org.neo4j.fleetmanagement.systemgraph.versions;

import org.neo4j.dbms.database.ComponentVersion;
import org.neo4j.dbms.database.KnownSystemComponentVersion;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.NullLog;

public abstract class KnownFleetManagementComponentVersion extends KnownSystemComponentVersion {
    protected final KnownFleetManagementComponentVersion previous;

    KnownFleetManagementComponentVersion(
            ComponentVersion componentVersion, KnownFleetManagementComponentVersion previous) {
        super(componentVersion, NullLog.getInstance());
        this.previous = previous;
    }

    // Should check what version we're upgrading from, and if older than previous, then call
    // the upgrade function of the previous version recursively.
    public abstract void upgradeFleetGraph(Transaction tx, int fromVersion) throws Exception;
}
