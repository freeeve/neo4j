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
package org.neo4j.bolt.protocol.common.fsm.response;

import org.neo4j.bolt.protocol.common.connector.connection.Connection;
import org.neo4j.boltmessages.response.RecordMessage;
import org.neo4j.values.AnyValue;
import org.neo4j.values.virtual.VirtualValues;

public final class JavaObjectdRecordHandler implements RecordHandler {

    private final Connection connection;
    private final int numberOfFields;

    private AnyValue[] currentValues;
    private int i;

    public JavaObjectdRecordHandler(Connection connection, int numberOfFields) {
        this.connection = connection;
        this.numberOfFields = numberOfFields;
    }

    @Override
    public void onBegin() {
        currentValues = new AnyValue[this.numberOfFields];
        this.i = 0;
    }

    @Override
    public void onField(AnyValue value) {
        currentValues[i++] = value;
    }

    @Override
    public void onCompleted() {
        connection.writeAndFlush(new RecordMessage(VirtualValues.list(currentValues)));
    }

    @Override
    public void onFailure() {}

    public static class Factory implements RecordHandler.Factory {
        private final Connection connection;

        public Factory(Connection connection) {
            this.connection = connection;
        }

        @Override
        public JavaObjectdRecordHandler newInstance(int numberOfFields) {
            return new JavaObjectdRecordHandler(connection, numberOfFields);
        }
    }
}
