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
// java
package org.neo4j.server.security.auth;

import java.util.Objects;
import org.apache.shiro.lang.util.ByteSource;
import org.eclipse.collections.api.list.ImmutableList;
import org.neo4j.internal.kernel.api.security.AuthenticationResult;

public class AuthenticationInfo {
    private final Neo4jPrincipal principal;
    private final Object credentials;
    private final ByteSource credentialsSalt;
    private final AuthenticationResult authenticationResult;
    private final ImmutableList<ValidityCheck> validityChecks;

    public AuthenticationInfo(
            Neo4jPrincipal principal,
            Object credentials,
            ByteSource credentialsSalt,
            AuthenticationResult authenticationResult,
            ImmutableList<ValidityCheck> validityChecks) {
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(authenticationResult, "authenticationResult must not be null");
        Objects.requireNonNull(validityChecks, "validityChecks must not be null");

        this.principal = principal;
        this.credentials = credentials;
        this.credentialsSalt = credentialsSalt;
        this.authenticationResult = authenticationResult;
        this.validityChecks = validityChecks;
    }

    public Neo4jPrincipal principal() {
        return principal;
    }

    public Object credentials() {
        return credentials;
    }

    public ByteSource credentialsSalt() {
        return credentialsSalt;
    }

    public AuthenticationResult authenticationResult() {
        return authenticationResult;
    }

    public ImmutableList<ValidityCheck> validityChecks() {
        return validityChecks;
    }

    @Override
    public String toString() {
        return principal.toString();
    }
}
