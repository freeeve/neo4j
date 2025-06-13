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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.jupiter.api.Test;
import org.neo4j.logging.AssertableLogProvider;

class ExceptionHandlerServiceTest {
    private final AssertableLogProvider logProvider = new AssertableLogProvider();
    private final ExceptionHandlerService exceptionHandlerService = new ExceptionHandlerService(logProvider);

    @Test
    void noHandlers() {
        assertDoesNotThrow(() -> exceptionHandlerService.raiseException("Test", new RuntimeException()));
    }

    @Test
    void invokeAllHandlers() {
        MutableInt invokeCounter1 = new MutableInt();
        ExceptionHandler exceptionHandler1 = (message, exception) -> invokeCounter1.increment();
        MutableInt invokeCounter2 = new MutableInt();
        ExceptionHandler exceptionHandler2 = (message, exception) -> invokeCounter2.increment();

        // Only 1
        exceptionHandlerService.addExceptionHandler(exceptionHandler1);
        exceptionHandlerService.raiseException("Test", new RuntimeException());
        assertEquals(1, invokeCounter1.intValue());
        assertEquals(0, invokeCounter2.intValue());

        // Both 1 and 2
        exceptionHandlerService.addExceptionHandler(exceptionHandler2);
        exceptionHandlerService.raiseException("Test", new RuntimeException());
        assertEquals(2, invokeCounter1.intValue());
        assertEquals(1, invokeCounter2.intValue());

        // Only 2
        exceptionHandlerService.removeExceptionHandler(exceptionHandler1);
        exceptionHandlerService.raiseException("Test", new RuntimeException());
        assertEquals(2, invokeCounter1.intValue());
        assertEquals(2, invokeCounter2.intValue());

        // None
        exceptionHandlerService.removeExceptionHandler(exceptionHandler2);
        exceptionHandlerService.raiseException("Test", new RuntimeException());
        assertEquals(2, invokeCounter1.intValue());
        assertEquals(2, invokeCounter2.intValue());
    }

    @Test
    void correctException() {
        RuntimeException exception = new RuntimeException();
        ExceptionHandler handler = (message, e) -> assertEquals(exception, e);
        exceptionHandlerService.addExceptionHandler(handler);
        exceptionHandlerService.raiseException("Test", exception);
    }

    @Test
    void invokeAllEvenOnFailure() {
        ExceptionHandler faultyExceptionHandler = (message, exception) -> {
            throw new IllegalStateException();
        };
        MutableInt invokeCounter = new MutableInt();
        ExceptionHandler exceptionHandler = (message, exception) -> invokeCounter.increment();
        exceptionHandlerService.addExceptionHandler(faultyExceptionHandler);
        exceptionHandlerService.addExceptionHandler(exceptionHandler);

        exceptionHandlerService.raiseException("Test", new RuntimeException());
        assertEquals(1, invokeCounter.intValue());
        Iterator<String> logLines = logProvider.logLines();
        String next = logLines.next();
        assertTrue(next.contains("Error raised during error handling"));
        assertFalse(logLines.hasNext());
    }
}
