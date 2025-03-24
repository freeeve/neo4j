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

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import picocli.CommandLine.ExitCode;

@DisabledOnOs(OS.WINDOWS)
class StopPermissionsTest extends StopTestBase {

    @Test
    void testStopWithNoPermissions() {
        execute("start");
        clearOutAndErr();
        assertThat(execute(List.of("stop"), Collections.emptyMap())).isEqualTo(ExitCode.SOFTWARE);
        assertThat(out.toString())
                .contains("Stopping Neo4j")
                .contains("failed to stop")
                .contains("Neo4j (pid:" + handler.runningPid
                        + ") process could not be stopped. Do you have the right permissions?");
        assertThat(err.toString()).contains("Failed to stop");
    }

    @Override
    protected ProcessManager getProcessManager(Bootloader bootloader, ProcessHandler processHandler) {
        return new NoPermissionToStopDbmsProcessManager(bootloader, processHandler);
    }

    private static class NoPermissionToStopDbmsProcessManager extends StopTestBase.StopTestProcessManagerBase {
        NoPermissionToStopDbmsProcessManager(Bootloader bootloader, ProcessHandler handler) {
            super(bootloader, handler);
        }

        @Override
        void configureProcessHandleMock(ProcessHandle ph, long pid) {
            doAnswer(inv -> pid).when(ph).pid();
            doAnswer(inv -> handler.isRunning()).when(ph).isAlive();
            // Simulates OS access control denying to destroy process
            doAnswer(inv -> false).when(ph).destroy();
        }
    }
}
