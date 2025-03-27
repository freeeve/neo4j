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
package org.neo4j.graphdb.security;

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlRuntimeException;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;

/**
 * Thrown when required authorization info has expired in the Neo4j auth cache
 */
public class AuthorizationExpiredException extends GqlRuntimeException implements Status.HasStatus {
    private static final Status statusCode = Status.Security.AuthorizationExpired;

    public static final String LDAP_AUTH_INFO_EXPIRED = "LDAP authorization info expired.";
    private static final String OIDC_AUTH_INFO_EXPIRED = "OIDC authorization info expired.";

    @Deprecated
    public AuthorizationExpiredException(String message) {
        super(message);
    }

    private AuthorizationExpiredException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    private AuthorizationExpiredException(ErrorGqlStatusObject gqlStatusObject, String message, Throwable cause) {
        super(gqlStatusObject, message, cause);
    }

    public static AuthorizationExpiredException ldapAuthInfoExpired() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NFF)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NFE)
                        .build())
                .build();
        return new AuthorizationExpiredException(gql, LDAP_AUTH_INFO_EXPIRED);
    }

    public static AuthorizationExpiredException oidcAuthInfoExpired() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NFF)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NFE)
                        .build())
                .build();
        return new AuthorizationExpiredException(gql, OIDC_AUTH_INFO_EXPIRED);
    }

    public static AuthorizationExpiredException pluginAuthInfoExpired(String plugin) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NFF)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NFE)
                        .build())
                .build();
        return new org.neo4j.graphdb.security.AuthorizationExpiredException(
                gql, "Plugin '" + plugin + "' authorization info expired.");
    }

    public static AuthorizationExpiredException pluginAuthInfoExpiredWithCause(String plugin, Throwable e) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NFF)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NFE)
                        .build())
                .build();
        return new org.neo4j.graphdb.security.AuthorizationExpiredException(
                gql, "Plugin '" + plugin + "' authorization info expired: " + e.getMessage(), e);
    }

    /** The Neo4j status code associated with this exception type. */
    @Override
    public Status status() {
        return statusCode;
    }
}
