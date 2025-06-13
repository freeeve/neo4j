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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.neo4j.logging.InternalLog;
import org.neo4j.logging.InternalLogProvider;

/**
 * A service that can receive exceptions and propagate them to registered handlers.
 */
public class ExceptionHandlerService {
    private final List<ExceptionHandler> exceptionHandlers = new CopyOnWriteArrayList<>();
    private final InternalLog log;

    public ExceptionHandlerService(InternalLogProvider logProvider) {
        this.log = logProvider.getLog(ExceptionHandlerService.class);
    }

    /**
     * Call to propagate an exception to the handlers. This method will not
     * throw any exceptions, so it's "safe" to call during error handling.
     *
     * @param message message to use.
     * @param t exception that occurred.
     */
    public void raiseException(String message, Throwable t) {
        try {
            for (ExceptionHandler exceptionHandler : exceptionHandlers) {
                try {
                    exceptionHandler.onException(message, t);
                } catch (Exception ex) {
                    log.error("Error raised during error handling", ex);
                }
            }
        } catch (Exception ignored) {
            // Make sure that this is safe to call during error handling
        }
    }

    /**
     * Register an exception handler for this service.
     *
     * @param exceptionHandler exception handler.
     */
    public void addExceptionHandler(ExceptionHandler exceptionHandler) {
        exceptionHandlers.add(exceptionHandler);
    }

    /**
     * Unregister an exception handler for this service.
     *
     * @param exceptionHandler exception handler.
     */
    public void removeExceptionHandler(ExceptionHandler exceptionHandler) {
        exceptionHandlers.remove(exceptionHandler);
    }
}
