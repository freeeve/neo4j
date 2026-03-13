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
package org.neo4j.bolt.testing.client;

import org.neo4j.boltmessages.request.RequestMessage;
import org.neo4j.boltmessages.response.ResponseMessage;

/**
 * Methods for exchanges {@link org.neo4j.boltmessages.request.RequestMessage} and
 * {@link org.neo4j.boltmessages.response.ResponseMessage} in memory with the database.
 */
public interface UnwiredTestConnection {
    /**
     * Transmits a Request message via this connection
     * @param message an arbitrary bolt message
     * @return a reference to this connection
     */
    UnwiredTestConnection sendRequest(RequestMessage message);

    /**
     * Receives a Response message via this communication
     *
     * @return the received message
     */
    ResponseMessage receiveResponse();
}
