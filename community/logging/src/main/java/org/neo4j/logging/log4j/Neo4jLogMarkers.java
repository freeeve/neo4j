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
package org.neo4j.logging.log4j;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class Neo4jLogMarkers {
    private static final Marker INTERNAL_ERROR_PARENT_MARKER = MarkerManager.getMarker("INTERNAL_ERROR");
    private static final Marker VIRTUAL_GRAPH_PARENT_MARKER = MarkerManager.getMarker("VIRTUAL_GRAPH");
    public static final Neo4jLogMarker KERNEL =
            new Neo4jLogMarker(MarkerManager.getMarker("KERNEL").setParents(INTERNAL_ERROR_PARENT_MARKER));
    public static final Neo4jLogMarker CYPHER =
            new Neo4jLogMarker(MarkerManager.getMarker("CYPHER").setParents(INTERNAL_ERROR_PARENT_MARKER));
    public static final Neo4jLogMarker VIRTUAL_GRAPH_CONFIG =
            new Neo4jLogMarker(MarkerManager.getMarker("CONFIG").setParents(VIRTUAL_GRAPH_PARENT_MARKER));
    public static final Neo4jLogMarker VIRTUAL_GRAPH_CONNECTION =
            new Neo4jLogMarker(MarkerManager.getMarker("CONNECTION").setParents(VIRTUAL_GRAPH_PARENT_MARKER));
}
