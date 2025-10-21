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
package org.neo4j.fleetmanagement.communication.model;

public enum ErrorCode {
    TOKEN_ROTATION_DUE(1000, "Fleet management token needs to be rotated - rotating now"),
    TOKEN_EXPIRED(1001, "Fleet management token is permanently expired - register a new one to resume operation"),
    TOKEN_REVOKED(1002, "Fleet management token is revoked - register a new one to resume operation"),
    ACCESS_DENIED(1003, "Fleet management access denied - check your permissions");

    private final int code;
    private final String statusMessage;

    ErrorCode(int i, String statusMessage) {
        this.code = i;
        this.statusMessage = statusMessage;
    }

    public int getCode() {
        return code;
    }

    public String getStatusMessage() {
        return statusMessage;
    }
}
