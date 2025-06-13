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
package org.neo4j.monitoring;

/**
 * Callback for {@link ExceptionHandlerService}.
 */
@FunctionalInterface
public interface ExceptionHandler {
    /**
     * Called when an exception is raised. Since multiple exception might happen at the same time this
     * method needs to handle concurrent calls. This might also be called from a time sensitive thread,
     * so any blocking operations should be offloaded to a different thread.
     *
     * @param message additional message.
     * @param exception the exception that occurred.
     */
    void onException(String message, Throwable exception);
}
