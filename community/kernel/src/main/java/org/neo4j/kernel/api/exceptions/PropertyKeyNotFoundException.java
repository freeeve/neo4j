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
package org.neo4j.kernel.api.exceptions;

import org.neo4j.exceptions.KernelException;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;

public class PropertyKeyNotFoundException extends KernelException {

    private PropertyKeyNotFoundException(ErrorGqlStatusObject gqlStatusObject, String propertyKey, Exception cause) {
        super(
                gqlStatusObject,
                Status.Schema.PropertyKeyAccessFailed,
                cause,
                "Property key '" + propertyKey + "' not found");
    }

    public static PropertyKeyNotFoundException propertyKeyNotFound(String propertyKey, Exception cause) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N63)
                .withParam(GqlParams.StringParam.propKey, propertyKey)
                .build();
        return new PropertyKeyNotFoundException(gql, propertyKey, cause);
    }
}
