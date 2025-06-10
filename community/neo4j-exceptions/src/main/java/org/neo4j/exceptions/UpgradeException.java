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
 * The upgrade to a new Neo4j version failed.
 */
public class UpgradeException extends Neo4jException {
    private UpgradeException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    public static UpgradeException conflictingDisplayNames(String db1, String db2) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N76)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N87)
                        .withParam(GqlParams.StringParam.db1, db1)
                        .withParam(GqlParams.StringParam.db2, db2)
                        .build())
                .build();

        var legacyMsg = String.format(
                "The upgrade to a new Neo4j version failed. Databases '%s' and '%s' have ambiguous names. Rename one of them before retrying the upgrade.",
                db1, db2);
        return new UpgradeException(gql, legacyMsg);
    }

    @Override
    public Status status() {
        return Status.Statement.ConstraintVerificationFailed;
    }
}
