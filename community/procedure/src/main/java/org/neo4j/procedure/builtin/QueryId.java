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
package org.neo4j.procedure.builtin;

import org.neo4j.kernel.api.exceptions.InvalidArgumentsException;

public final class QueryId {
    public static final String PREFIX = "query-";
    private static final String EXPECTED_FORMAT = "query-<id>";
    private static final String EXPECTED_FORMAT_MSG = "(expected format: %s)".formatted(EXPECTED_FORMAT);

    private QueryId() {}

    /**
     * Parse a string that represents a query id.
     *
     * @param queryIdText the text to parse
     * @param argumentName the name of the procedure argument that contains the query id text
     * @param procedureName the name of the procedure calling in here
     */
    public static long parse(String queryIdText, String argumentName, String procedureName)
            throws InvalidArgumentsException {
        try {
            if (!queryIdText.startsWith(PREFIX)) {
                throw InvalidArgumentsException.invalidProcedureArgument(
                        queryIdText, argumentName, procedureName, EXPECTED_FORMAT, "Expected prefix " + PREFIX, null);
            }
            String qid = queryIdText.substring(PREFIX.length());
            var internalId = Long.parseLong(qid);
            if (internalId <= 0) {
                throw InvalidArgumentsException.invalidProcedureArgument(
                        queryIdText,
                        argumentName,
                        procedureName,
                        EXPECTED_FORMAT,
                        "Negative ids are not supported " + EXPECTED_FORMAT_MSG,
                        null);
            }
            return internalId;
        } catch (Exception e) {
            if (e instanceof InvalidArgumentsException iae) {
                throw iae;
            }
            throw InvalidArgumentsException.invalidProcedureArgument(
                    queryIdText,
                    argumentName,
                    procedureName,
                    EXPECTED_FORMAT,
                    "Could not parse id " + queryIdText + " " + EXPECTED_FORMAT_MSG,
                    e);
        }
    }
}
