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
package org.neo4j.logging;

import org.neo4j.logging.log4j.Neo4jLogMarker;

public class Neo4jInternalErrorLogMessage implements Neo4jLogMessage {
    private final Neo4jLogMarker marker;
    private final String message;
    private final Throwable throwable;

    public Neo4jInternalErrorLogMessage(Neo4jLogMarker marker, String message, Throwable throwable) {
        this.marker = marker;
        this.message = message;
        this.throwable = throwable;
    }

    @Override
    public Neo4jLogMarker getMarker() {
        return marker;
    }

    @Override
    public String getFormattedMessage() {
        return message;
    }

    @Override
    public Object[] getParameters() {
        return new Object[0];
    }

    @Override
    public Throwable getThrowable() {
        return throwable;
    }
}
