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
package org.neo4j.bolt.protocol.common.connector.error;

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams.StringParam;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.api.exceptions.Status.HasStatus;
import org.neo4j.kernel.api.exceptions.Status.Request;

/**
 * Notifies a client about illegal database access within the context of a limited or restricted
 * connector.
 * <p/>
 * At the moment, this exception is primarily used for database access via the domain socket
 * connector as accessing databases other than the system database is not currently permitted within
 * this context.
 */
public class IllegalDatabaseAccessException extends ConnectorException implements HasStatus {
    private final String db;

    public IllegalDatabaseAccessException(String db, String message) {
        super(message);

        this.db = db;
    }

    public IllegalDatabaseAccessException(String db, String message, Throwable cause) {
        super(message, cause);

        this.db = db;
    }

    @Override
    protected ErrorGqlStatusObject createGqlStatusObject() {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N79)
                .withParam(StringParam.db, this.db)
                .build();
    }

    @Override
    public Status status() {
        return Request.Invalid;
    }
}
