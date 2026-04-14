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
package org.neo4j.bolt.fsm.error;

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.ErrorMessageHolder;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.api.exceptions.Status.HasStatus;

/**
 * Admission Control exceptions in the context of bolt, This class of exception is transient.
 */
public final class AdmissionControlException extends StateMachineException implements HasStatus, ErrorGqlStatusObject {

    private final ErrorGqlStatusObject gqlStatusObject;
    private final String legacyMessage;

    private AdmissionControlException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(ErrorMessageHolder.getMessage(gqlStatusObject, message));
        this.gqlStatusObject = gqlStatusObject;
        this.legacyMessage = message;
    }

    public static AdmissionControlException resourceExhaustion() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N59)
                .withDiagnosticRecordProperty(BoltDiagnosticRecordProperty.IDEMPOTENT, true)
                .build();
        var legacyMessage = Status.Request.ResourceExhaustion.code().description();
        return new AdmissionControlException(gql, legacyMessage);
    }

    @Override
    public Status status() {
        return Status.Request.ResourceExhaustion;
    }

    @Override
    public ErrorGqlStatusObject gqlStatusObject() {
        return gqlStatusObject;
    }

    @Override
    public String legacyMessage() {
        return legacyMessage;
    }
}
