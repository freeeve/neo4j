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

public class ErrorResponse {
    public int code;
    public String message;

    public ErrorCode code() {
        switch (this.code) {
            case 1000:
                return ErrorCode.TOKEN_ROTATION_DUE;
            case 1001:
                return ErrorCode.TOKEN_EXPIRED;
            case 1002:
                return ErrorCode.TOKEN_REVOKED;
            case 1003:
                return ErrorCode.ACCESS_DENIED;
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format(
                "code=%d (%s), message='%s'", code, code() != null ? code().getStatusMessage() : "", message);
    }
}
