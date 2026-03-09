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
package org.neo4j.bolt.protocol.common.connector.admissioncontrol;

import org.neo4j.boltmessages.request.RequestMessage;
import org.neo4j.dbms.admissioncontrol.AdmissionControlToken;

/**
 * This interface is for managing admission control on behalf of a singular connection.
 */
public interface ConnectionAdmissionControlTracker {
    /**
     * Handles an individual bolt request returning an admission control token if the message requires admission
     * control. If the message does not need to take part in admission control the return value will be null.
     *
     * @param message              bolt request message.
     * @param defaultDatabase      the default database of the user which will be used if no database given in message
     * @return an admission control token to be awaited before the processing of the given message.
     */
    AdmissionControlToken onMessage(RequestMessage message, String defaultDatabase);

    /**
     * Notifies the state machine of a reset event.
     */
    void onReset();
}
