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
package org.neo4j.csv.reader;

public class ColumnMismatchException extends RuntimeException {
    private static final String messageTemplate =
            "Wrong number of columns in a row. This might be caused by illegal quotes that cause separators to be ignored. See '%s' at position %s. This is read as `%s`.";

    public ColumnMismatchException(SourceTraceability source, String readValue, Throwable cause) {
        super(messageTemplate.formatted(source.sourceDescription(), source.position(), readValue), cause);
    }
}
