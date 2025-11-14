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
package org.neo4j.test.extension.timeout;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.PreInterruptCallback;
import org.junit.jupiter.api.extension.PreInterruptContext;
import org.neo4j.internal.helpers.NamedThreadFactory;

public class TimeoutGuardExtension implements PreInterruptCallback {
    private static final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("HangingTestMonitor"));

    @Override
    public void beforeThreadInterrupt(PreInterruptContext preInterruptContext, ExtensionContext extensionContext) {
        // Most tests will handle interruptions and stop execution without issues. However, some may not, so we
        // will check their status after a period of time and terminate the VM if they are unresponsive.
        executor.schedule(
                new HangingTestWatchTask(
                        preInterruptContext.getThreadToInterrupt(), extensionContext.getRequiredTestMethod()),
                1,
                TimeUnit.MINUTES);
    }

    private static class HangingTestWatchTask implements Runnable {
        private final Thread testThread;
        private final Method requiredTestMethod;

        public HangingTestWatchTask(Thread testThread, Method requiredTestMethod) {
            this.testThread = testThread;
            this.requiredTestMethod = requiredTestMethod;
        }

        @Override
        public void run() {
            var clazz = requiredTestMethod.getDeclaringClass().getName();
            var methodName = requiredTestMethod.getName();
            for (StackTraceElement stackTraceElement : testThread.getStackTrace()) {
                if (clazz.equals(stackTraceElement.getClassName())
                        && methodName.equals(stackTraceElement.getMethodName())) {
                    String message = """
                                                      ***WARNING***
                            Test monitor terminating hanging execution for test %s.%s
                            After the test timeout was reached, an interruption attempt was made; however, the test did not progress within the allocated grace period. Terminating the VM.""".formatted(clazz, methodName);

                    printWarning(System.out, message);
                    printWarning(System.err, message);
                    System.exit(1);
                }
            }
        }

        private static void printWarning(PrintStream out, String message) {
            out.println(message);
            out.flush();
        }
    }
}
