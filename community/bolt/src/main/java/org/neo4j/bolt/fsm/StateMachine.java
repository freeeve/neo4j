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

import org.neo4j.bolt.fsm.state.StateReference;
import org.neo4j.bolt.protocol.common.connector.connection.Connection;
import org.neo4j.bolt.protocol.common.connector.connection.ConnectionHandle;

/**
 * Represents a {@link Connection connection}-bound state machine instance which follows the state
 * transitions configured within a parent {@link StateMachineConfiguration configuration}.
 *
 * @see StateMachineConfiguration
 */
public interface StateMachine {

    /**
     * Retrieves the connection for which this context manages state machine state.
     *
     * @return a connection.
     */
    ConnectionHandle connection();

    /**
     * Retrieves the current configured default state.
     *
     * @return a state reference.
     */
    StateReference defaultState();

    /**
     * Identifies whether this state machine has failed in a recoverable fashion and will ignore all
     * further requests until it has been reset.
     *
     * @return true if failed, false otherwise.
     */
    boolean hasFailed();

    /**
     * Identifies whether this state machine has been interrupted and will ignore all further requests
     * until it has been reset.
     * <p/>
     * This method is thread-safe.
     *
     * @return true if interrupted, false otherwise.
     */
    boolean isInterrupted();

    /**
     * Interrupts the execution of the current message processed by this state machine if any.
     * <p/>
     * Note: Currently the executing thread is <em>not</em> directly interrupted by invoking this
     * function. The state machine will merely be placed within an interrupted state in which it
     * cannot handle messages.
     * <p/>
     * This method is thread-safe.
     */
    void interrupt();

    /**
     * Validates whether the state machine remains within a state in which it is capable of
     * performing operations.
     */
    boolean validate();
}
