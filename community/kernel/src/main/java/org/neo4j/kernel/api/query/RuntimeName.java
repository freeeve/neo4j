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
package org.neo4j.kernel.api.query;

import java.util.Locale;

public enum RuntimeName {
    INTERPRETED,
    SLOTTED,
    PIPELINED,
    PARALLEL,
    SCHEMA,
    SYSTEM,
    GRAPH_ENGINE;

    private final String textOutput;
    private final String displayName;

    RuntimeName() {
        this.textOutput = name().replace('_', ' ');
        this.displayName = textOutput.toLowerCase(Locale.ROOT);
    }

    /**
     * Returns a human-readable uppercase name, e.g. "GRAPH ENGINE".
     */
    public String toTextOutput() {
        return textOutput;
    }

    /**
     * Returns a lowercase display name, e.g. "graph engine".
     */
    public String asString() {
        return displayName;
    }

    /**
     * Looks up a RuntimeName from a case-insensitive string, e.g. "pipelined" or "GRAPH ENGINE".
     */
    public static RuntimeName fromName(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT).replace(' ', '_'));
    }
}
