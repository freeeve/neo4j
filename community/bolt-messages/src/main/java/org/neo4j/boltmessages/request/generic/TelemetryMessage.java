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

package org.neo4j.boltmessages.request.generic;

import org.neo4j.boltmessages.request.RequestMessage;

public record TelemetryMessage(DriverInterfaceType interfaceType) implements RequestMessage {
    public static final short SIGNATURE = 0x54;

    @Override
    public String toString() {
        return "TelemetryMessage{" + "interfaceType=" + interfaceType + '}';
    }

    public enum DriverInterfaceType {
        TRANSACTION_FUNCTION(0),
        UNMANAGED_TRANSACTION(1),
        MANAGED_TRANSACTION(2),
        EXECUTE_QUERY(3);

        private final long marker;

        DriverInterfaceType(long marker) {
            this.marker = marker;
        }

        public long getMarker() {
            return marker;
        }
    }
}
