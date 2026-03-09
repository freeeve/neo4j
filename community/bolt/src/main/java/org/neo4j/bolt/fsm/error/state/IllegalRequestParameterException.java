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
package org.neo4j.bolt.fsm.error.state;

import org.neo4j.boltmessages.request.RequestMessage;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.util.VisibleForTesting;

public class IllegalRequestParameterException extends IllegalRequestException {

    @VisibleForTesting
    public IllegalRequestParameterException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    public static IllegalRequestParameterException nullValueNotAllowed(long statementId) {
        return new IllegalRequestParameterException(
                GqlHelper.getGql08N06(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22004)
                        .build()),
                "No such statement: " + statementId);
    }

    public static IllegalRequestParameterException invalidServerState(RequestMessage message, String serverState) {
        var gql = GqlHelper.getGql08N06(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N10)
                .withParam(GqlParams.StringParam.msg, String.valueOf(message))
                .withParam(GqlParams.StringParam.boltServerState, serverState)
                .build());

        return new IllegalRequestParameterException(
                gql,
                "Request of type " + message.getClass().getName() + " is not permitted while failed or interrupted");
    }
}
