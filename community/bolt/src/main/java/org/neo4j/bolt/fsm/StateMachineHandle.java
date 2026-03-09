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
package org.neo4j.bolt.fsm;

import org.neo4j.bolt.fsm.error.NoSuchStateException;
import org.neo4j.bolt.fsm.error.StateMachineException;
import org.neo4j.bolt.fsm.state.State;
import org.neo4j.bolt.fsm.state.StateReference;
import org.neo4j.bolt.protocol.common.fsm.response.ResponseHandler;
import org.neo4j.boltmessages.request.RequestMessage;
import org.neo4j.dbms.admissioncontrol.AdmissionControlToken;

/**
 * Encapsulates owner-specific finite state machine functionality.
 * <p />
 * Note: The methods within this interface are generally not thread-safe and should only ever be
 * accessed from within the context of a Bolt worker.
 */
public interface StateMachineHandle extends StateMachine {

    /**
     * Alters the default state to which this instance shall return when reset.
     *
     * @param state a state reference.
     * @throws NoSuchStateException when no state with the given reference exists within the state
     *                              machine configuration.
     */
    void defaultState(StateReference state) throws NoSuchStateException;

    /**
     * Processes a request within the scope of a state machine instance and advances its state
     * accordingly.
     *
     * @param message a request message.
     * @param handler a response handler.
     * @throws StateMachineException when the state machine fails to transition and is incapable of
     *                               recovering from this condition.
     * @see State#process(Context, RequestMessage, ResponseHandler)
     */
    default void process(RequestMessage message, ResponseHandler handler) throws StateMachineException {
        process(message, handler, null);
    }

    /**
     * Processes a request within the scope of a state machine instance and advances its state
     * accordingly.
     *
     * @param message a request message.
     * @param handler a response handler.
     * @param admissionControlToken a token to await handling message to ensure server health.
     * @throws StateMachineException when the state machine fails to transition and is incapable of
     *                               recovering from this condition.
     * @see State#process(Context, RequestMessage, ResponseHandler)
     */
    void process(RequestMessage message, ResponseHandler handler, AdmissionControlToken admissionControlToken)
            throws StateMachineException;

    /**
     * Mark this state machine as failed.
     */
    void fail();

    /**
     * Reverts this state machine back to its default state and clears any remaining errors.
     */
    void reset();
}
