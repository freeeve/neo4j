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
package org.neo4j.exceptions;

import java.util.List;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;

public class CypherTypeException extends Neo4jException {
    public CypherTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    public CypherTypeException(ErrorGqlStatusObject gqlStatusObject, String message, Throwable cause) {
        super(gqlStatusObject, message, cause);
    }

    public CypherTypeException(String message) {
        super(message);
    }

    public CypherTypeException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    public static CypherTypeException invalidType(
            String value, List<String> expectedTypes, String actualType, String signature) {
        return new CypherTypeException(
                ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22G12)
                        .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N01)
                                .withParam(GqlParams.StringParam.value, value)
                                .withParam(GqlParams.ListParam.valueTypeList, expectedTypes)
                                .withParam(GqlParams.StringParam.valueType, actualType)
                                .build())
                        .build(),
                String.format("Wrong type. Expected %s, got %s", signature, actualType));
    }

    @Override
    public Status status() {
        return Status.Statement.TypeError;
    }
}
