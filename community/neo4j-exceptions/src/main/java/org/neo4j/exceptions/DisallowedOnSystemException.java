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

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;

/**
 * Thrown when the operation is <STRONG>not allowed on</STRONG> the system database.
 * <p>
 * Not to be confused with the opposite {@link org.neo4j.exceptions.NotSystemDatabaseException}
 * which is thrown when the operation <STRONG>requires</STRONG> the system database.
 * <p>
 * GQL status code: 42N17
 */
public class DisallowedOnSystemException extends InvalidTargetDatabaseException {

    private DisallowedOnSystemException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    @Override
    public Status status() {
        return Status.Statement.InvalidTargetDatabaseError;
    }

    public static DisallowedOnSystemException disallowedOnSystemException(String oldMessage, String input) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N17)
                .withParam(GqlParams.StringParam.input, input)
                .build();
        return new DisallowedOnSystemException(gql, oldMessage);
    }
}
