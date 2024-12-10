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
package org.neo4j.kernel.api.exceptions.schema;

import org.neo4j.common.TokenNameLookup;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.internal.kernel.api.exceptions.schema.SchemaKernelException;
import org.neo4j.internal.schema.SchemaRule;
import org.neo4j.kernel.api.exceptions.Status;

public class EquivalentSchemaRuleAlreadyExistsException extends SchemaKernelException {
    private static final String EQUIVALENT_INDEX = "An equivalent index already exists, '%s'.";
    private static final String EQUIVALENT_CONSTRAINT = "An equivalent constraint already exists, '%s'.";

    private EquivalentSchemaRuleAlreadyExistsException(String message, ErrorGqlStatusObject gqlStatusObject) {
        super(gqlStatusObject, Status.Schema.EquivalentSchemaRuleAlreadyExists, message);
    }

    // KNL-018
    public static EquivalentSchemaRuleAlreadyExistsException cannotCreateConstraint(
            SchemaRule schemaRule, TokenNameLookup tokenNameLookup) {
        var name = schemaRule.getName();
        var userDescription = schemaRule.userDescription(tokenNameLookup);

        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N65)
                .withParam(GqlParams.StringParam.constrDescrOrName, name)
                .build();
        var message = String.format(EQUIVALENT_CONSTRAINT, userDescription);
        return new EquivalentSchemaRuleAlreadyExistsException(message, gql);
    }

    // KNL-024
    public static EquivalentSchemaRuleAlreadyExistsException cannotCreateIndex(
            SchemaRule schemaRule, TokenNameLookup tokenNameLookup) {
        var name = schemaRule.getName();
        var userDescription = schemaRule.userDescription(tokenNameLookup);

        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N70)
                .withParam(GqlParams.StringParam.idxDescrOrName, name)
                .build();
        var message = String.format(EQUIVALENT_INDEX, userDescription);
        return new EquivalentSchemaRuleAlreadyExistsException(message, gql);
    }
}
