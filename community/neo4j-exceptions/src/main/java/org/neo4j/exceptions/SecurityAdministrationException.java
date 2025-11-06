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
 * Thrown when running Enterprise admin commands in community.
 * <p>
 * Same GQL status code as {@link org.neo4j.exceptions.RuntimeUnsupportedException#unsupportedRuntimeInThisVersion}, but different Status
 */
public class SecurityAdministrationException extends CypherExecutionException {
    private SecurityAdministrationException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    @Override
    public Status status() {
        return Status.Statement.UnsupportedAdministrationCommand;
    }

    public static SecurityAdministrationException unsupportedInCommunity(String queryText, String component) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N27)
                .withParam(GqlParams.StringParam.feat, "'%s'".formatted(component))
                .withParam(GqlParams.StringParam.edition, "community edition")
                .build();
        return new SecurityAdministrationException(gql, "Unsupported administration command: " + queryText);
    }

    public static SecurityAdministrationException unsupportedWithAuthDisabled(String command, String oldMessage) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N2A)
                .withParam(GqlParams.StringParam.cmd, command)
                .build();
        return new SecurityAdministrationException(gql, oldMessage);
    }
}
