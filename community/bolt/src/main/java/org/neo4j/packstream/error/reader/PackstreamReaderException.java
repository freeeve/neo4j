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
package org.neo4j.packstream.error.reader;

import java.util.Set;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.ErrorMessageHolder;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.packstream.error.PackstreamException;

public class PackstreamReaderException extends PackstreamException {

    protected PackstreamReaderException(ErrorGqlStatusObject gqlStatusObject, String message, String legacyMessage) {
        super(gqlStatusObject, message, legacyMessage);
    }

    protected PackstreamReaderException(
            ErrorGqlStatusObject gqlStatusObject, String message, String legacyMessage, Throwable cause) {
        super(gqlStatusObject, message, legacyMessage, cause);
    }

    public static PackstreamReaderException internalError(String msgTitle, String message) {
        var gql = GqlHelper.get50N00(msgTitle, message);
        return new PackstreamReaderException(gql, ErrorMessageHolder.getMessage(gql, message), message);
    }

    public static PackstreamReaderException duplicateMapKey(String key) {
        // DRI-003 (When it gets wrapped in an IllegalStructArgumentException
        // it will get the GQL code 08N06 with this (22N54) as a cause)
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N54)
                .withParam(GqlParams.StringParam.mapKey, key)
                .build();
        var legacyMessage = "Duplicate map key: \"" + key + "\"";
        return new PackstreamReaderException(gql, ErrorMessageHolder.getMessage(gql, legacyMessage), legacyMessage);
    }

    public static PackstreamReaderException illegalElement(
            String elementName, String description, String legacyMessage) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N60)
                .withParam(GqlParams.StringParam.item, elementName)
                .withParam(GqlParams.StringParam.msg, description)
                .build();

        return new PackstreamReaderException(gql, ErrorMessageHolder.getMessage(gql, legacyMessage), legacyMessage);
    }

    public static PackstreamReaderException unknownDriverInterfaceType(long type, Set<Long> expectedType) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N00)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N01)
                        .withParam(GqlParams.StringParam.value, "driver interface type")
                        .withParam(
                                GqlParams.ListParam.valueTypeList,
                                expectedType.stream().toList())
                        .withParam(GqlParams.StringParam.valueType, String.valueOf(type))
                        .build())
                .build();
        var legacyMessage = "Unknown driver interface type " + type;
        return new PackstreamReaderException(gql, ErrorMessageHolder.getMessage(gql, legacyMessage), legacyMessage);
    }
}
