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
package org.neo4j.internal.kernel.api.security;

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.internal.kernel.api.security.AbstractSecurityLog.ContextInfo;

/**
 * Helper for logging errors with GQL code 42NFF ("Access denied, see the security logs for details.")
 * The intent is that each 42NFF constructor call should go through this class
 */
public class SecurityExceptionLogger {

    protected final AbstractSecurityLog securityLog;

    public SecurityExceptionLogger(AbstractSecurityLog securityLog) {
        this.securityLog = securityLog;
    }

    public <T extends Throwable & ErrorGqlStatusObject> T logAndGet(T exception) {
        return logAndGet(exception.legacyMessage(), exception);
    }

    public <T extends Throwable & ErrorGqlStatusObject> T logAndGet(String message, T exception) {
        securityLog.error(message, GqlStatusInfoCodes.STATUS_42NFF.getGqlStatus());
        return exception;
    }

    // with loginContext

    public <T extends Throwable & ErrorGqlStatusObject> T logAndGet(LoginContext context, T exception) {
        return logAndGet(context, null, exception.legacyMessage(), exception);
    }

    public <T extends Throwable & ErrorGqlStatusObject> T logAndGet(
            LoginContext context, String database, T exception) {
        return logAndGet(context, database, exception.legacyMessage(), exception);
    }

    public <T extends Throwable & ErrorGqlStatusObject> T logAndGet(
            LoginContext context, String database, String message, T exception) {
        securityLog.error(ContextInfo.from(context, database), message, GqlStatusInfoCodes.STATUS_42NFF.getGqlStatus());
        return exception;
    }

    // with securityContext

    public <T extends Throwable & ErrorGqlStatusObject> T logAndGet(SecurityContext context, T exception) {
        return logAndGet(context, exception.legacyMessage(), exception);
    }

    public <T extends Throwable & ErrorGqlStatusObject> T logAndGet(
            SecurityContext context, String message, T exception) {
        securityLog.error(ContextInfo.from(context), message, GqlStatusInfoCodes.STATUS_42NFF.getGqlStatus());
        return exception;
    }
}
