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
package org.neo4j.fleetmanagement.common;

import static org.apache.commons.lang3.StringUtils.abbreviate;

import java.nio.charset.StandardCharsets;

public class ResponseStatusException extends RuntimeException {

    public ResponseStatusException(int statusCode, byte[] responseBody) {
        super(String.format(
                "HTTP request failed with status code %d and body '%s'",
                statusCode,
                responseBody != null ? abbreviate(new String(responseBody, StandardCharsets.UTF_8), 2000) : "null"));
    }
}
