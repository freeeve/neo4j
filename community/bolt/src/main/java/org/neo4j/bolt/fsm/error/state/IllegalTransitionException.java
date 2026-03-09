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
package org.neo4j.bolt.fsm.error.state;

import org.neo4j.bolt.fsm.error.ConnectionTerminating;
import org.neo4j.bolt.fsm.state.State;
import org.neo4j.boltmessages.request.RequestMessage;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.api.exceptions.Status.HasStatus;
import org.neo4j.kernel.api.exceptions.Status.Request;

/**
 * Represents an error case in which a state machine does not define a transition for the desired
 * request and is thus unable to handle it.
 */
public class IllegalTransitionException extends IllegalRequestException implements HasStatus, ConnectionTerminating {
    private final State state;
    private final RequestMessage request;

    private IllegalTransitionException(ErrorGqlStatusObject gqlStatusObject, State state, RequestMessage request) {

        super(
                gqlStatusObject,
                "Message of type " + request.getClass().getSimpleName() + " cannot be handled by a session in the "
                        + state.name() + " state.");

        this.state = state;
        this.request = request;
    }

    public static IllegalTransitionException illegalTransition(State state, RequestMessage request) {
        var gql = GqlHelper.getGql08N06(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N10)
                .withParam(GqlParams.StringParam.msg, request.getClass().getSimpleName())
                .withParam(GqlParams.StringParam.boltServerState, state.name())
                .build());
        return new IllegalTransitionException(gql, state, request);
    }

    @Override
    public Status status() {
        return Request.Invalid;
    }

    public State getState() {
        return this.state;
    }

    public RequestMessage getRequest() {
        return this.request;
    }
}
