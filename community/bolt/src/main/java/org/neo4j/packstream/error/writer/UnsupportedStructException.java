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
package org.neo4j.packstream.error.writer;

import org.neo4j.bolt.protocol.io.StructType;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlRuntimeException;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;

public class UnsupportedStructException extends GqlRuntimeException implements Status.HasStatus {
    private final Status status;

    private UnsupportedStructException(ErrorGqlStatusObject gqlStatusObject, StructType type) {
        super(
                gqlStatusObject,
                String.format(
                        "Struct tag: 0x%02X representing type %s is not supported" + " for this protocol version",
                        type.getTag(), type.name()));
        this.status = Status.Request.Invalid;
    }

    public static UnsupportedStructException unsupportedVectorStruct() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N00)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NBD)
                        .withParam(GqlParams.StringParam.value, String.format("0x%02X", StructType.VECTOR.getTag()))
                        .build())
                .build();
        return new UnsupportedStructException(gql, StructType.VECTOR);
    }

    @Override
    public Status status() {
        return status;
    }
}
