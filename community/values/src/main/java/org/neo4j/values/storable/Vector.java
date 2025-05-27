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
package org.neo4j.values.storable;

// TODO: @PublicApi?
//       org.neo4j.graphdb(.vector?)
public interface Vector {
    int dimensions();

    CoordinateType coordinateType();

    enum CoordinateType {
        INTEGER8("INTEGER8 NOT NULL"),
        INTEGER16("INTEGER16 NOT NULL"),
        INTEGER32("INTEGER32 NOT NULL"),
        INTEGER64("INTEGER NOT NULL"),
        FLOAT32("FLOAT32 NOT NULL"),
        FLOAT64("FLOAT NOT NULL");

        private final String normalizedCypherString;

        CoordinateType(String normalizedCypherString) {
            this.normalizedCypherString = normalizedCypherString;
        }

        public String normalizedCypherString() {
            return normalizedCypherString;
        }
    }
}
