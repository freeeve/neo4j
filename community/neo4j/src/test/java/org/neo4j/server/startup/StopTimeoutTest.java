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
package org.neo4j.server.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import picocli.CommandLine.ExitCode;

@DisabledOnOs(OS.WINDOWS)
class StopTimeoutTest extends StopTestBase {

    @Test
    void testStopWithEnvironmentalVariableTimeout() {
        execute("start");
        clearOutAndErr();
        assertThat(execute(List.of("stop"), Map.of(Bootloader.ENV_NEO4J_SHUTDOWN_TIMEOUT, "1")))
                .isEqualTo(ExitCode.SOFTWARE);
        assertFailedToStopMessages(1);
    }

    @Test
    void testRestartWithEnvironmentalVariableTimeout() {
        execute("start");
        clearOutAndErr();
        assertThat(execute(List.of("restart"), Map.of(Bootloader.ENV_NEO4J_SHUTDOWN_TIMEOUT, "1")))
                .isEqualTo(ExitCode.SOFTWARE);
        assertFailedToStopMessages(1);
    }

    @Test
    void testStopWithCommandOptionTimeout() {
        execute("start");
        clearOutAndErr();
        // Let's also provide NEO4J_SHUTDOWN_TIMEOUT to test that the option has higher priority.
        assertThat(execute(List.of("stop", "--shutdown-timeout=2"), Map.of(Bootloader.ENV_NEO4J_SHUTDOWN_TIMEOUT, "1")))
                .isEqualTo(ExitCode.SOFTWARE);
        assertFailedToStopMessages(2);
    }

    @Test
    void testRestartWithCommandOptionTimeout() {
        execute("start");
        clearOutAndErr();
        // Let's also provide NEO4J_SHUTDOWN_TIMEOUT to test that the option has higher priority.
        assertThat(execute(
                        List.of("restart", "--shutdown-timeout=2"), Map.of(Bootloader.ENV_NEO4J_SHUTDOWN_TIMEOUT, "1")))
                .isEqualTo(ExitCode.SOFTWARE);
        assertFailedToStopMessages(2);
    }

    private void assertFailedToStopMessages(int expectedTimeout) {
        assertThat(out.toString())
                .contains("Stopping Neo4j")
                .contains("failed to stop")
                .contains("Neo4j (pid:" + handler.runningPid + ") took more than " + expectedTimeout
                        + " seconds to stop.")
                .contains("Please see ")
                .contains("logs/neo4j.log for details");
        assertThat(err.toString()).contains("Failed to stop");
    }

    @Override
    protected ProcessManager getProcessManager(Bootloader bootloader, ProcessHandler processHandler) {
        return new NotStoppingDbmsProcessManager(bootloader, processHandler);
    }

    private static class NotStoppingDbmsProcessManager extends StopTestBase.StopTestProcessManagerBase {
        NotStoppingDbmsProcessManager(Bootloader bootloader, ProcessHandler handler) {
            super(bootloader, handler);
        }

        @Override
        void configureProcessHandleMock(ProcessHandle ph, long pid) {
            doAnswer(inv -> pid).when(ph).pid();
            doAnswer(inv -> handler.isRunning()).when(ph).isAlive();
            // Simulates a process that does not respect being asked to stop.
            // Returning true here just means the request to stop has been
            // successfully issues by the OS.
            doAnswer(inv -> true).when(ph).destroy();
        }
    }
}
