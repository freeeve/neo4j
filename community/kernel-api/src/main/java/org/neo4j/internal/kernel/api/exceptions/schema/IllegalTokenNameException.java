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
package org.neo4j.internal.kernel.api.exceptions.schema;

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.token.api.TokenType;

public class IllegalTokenNameException extends SchemaKernelException {
    private IllegalTokenNameException(ErrorGqlStatusObject gqlStatusObject, String tokenName) {
        super(
                gqlStatusObject,
                Status.Schema.TokenNameError,
                String.format(
                        "%s is not a valid token name. " + "Token names cannot be empty or contain any null-bytes.",
                        tokenName != null ? "'" + tokenName + "'" : "Null"));
    }

    public static IllegalTokenNameException invalidTokenName(String tokenName, TokenType type) {
        var gql = GqlHelper.getGql42001_42I11(type.getName(), tokenName, 0, 0, 0);
        return new IllegalTokenNameException(gql, tokenName);
    }
}
