package org.neo4j.fleetmanagement.transactions.editions;

import java.util.List;
import org.neo4j.fleetmanagement.topology.model.Database;
import org.neo4j.fleetmanagement.transactions.model.ResultMap;

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
public class Shared {
    public static Database getDatabase(ResultMap r) {
        var db = new Database();

        db.name = r.getString("name");
        db.aliases = r.getStringList("aliases");
        db.access = r.getString("access");
        // Only available for online or deallocating databases
        db.databaseId = r.getString("databaseID", null);
        db.requestedStatus = r.getString("requestedStatus");
        db.currentStatus = r.getString("currentStatus");
        db._default = r.getBoolean("default");
        db.home = r.getBoolean("home");
        // Only available for online or deallocating databases
        db.lastCommittedTxn = r.getInteger("lastCommittedTxn", null);
        // Only available for online or deallocating databases
        db.replicationLag = r.getInteger("replicationLag", null);
        db.statusMessage = r.getString("statusMessage");
        // Null for composite databases & spd
        db.currentPrimariesCount = r.getInteger("currentPrimariesCount", null);
        // Null for composite databases & spd
        db.currentSecondariesCount = r.getInteger("currentSecondariesCount", null);
        // Null for composite databases & spd
        db.requestedPrimariesCount = r.getInteger("requestedPrimariesCount", null);
        // Null for composite databases & spd
        db.requestedSecondariesCount = r.getInteger("requestedSecondariesCount", null);
        db.creationTime = r.getZonedDateTime("creationTime").toEpochSecond();
        db.lastStartTime = r.getZonedDateTime("lastStartTime").toEpochSecond();
        // Null for offline, deallocating or composite databases & spd
        db.store = r.getString("store", null);
        db.writer = r.getBoolean("writer");

        db.type = r.getString("type");
        if (db.isComposite()) {
            db.role = null;
        } else {
            db.role = r.getString("role", "unknown");
        }
        db.graphShards = getStringListIfPresent(r, "graphShards");
        db.propertyShards = getStringListIfPresent(r, "propertyShards");

        return db;
    }

    private static List<String> getStringListIfPresent(ResultMap r, String key) {
        if (r.get(key) != null) {
            return r.getStringList(key);
        }
        return null;
    }
}
