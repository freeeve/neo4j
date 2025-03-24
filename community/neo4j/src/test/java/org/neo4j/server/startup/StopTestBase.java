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

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.neo4j.cli.CommandFailedException;
import picocli.CommandLine;

abstract class StopTestBase extends ServerProcessTestBase {

    protected final ProcessHandler handler = new ProcessHandler();

    @Override
    protected CommandLine createCommand(
            PrintStream out,
            PrintStream err,
            Function<String, String> envLookup,
            Function<String, String> propLookup,
            Runtime.Version version) {
        var environment = new Environment(out, err, envLookup, propLookup, version);
        var command = new Neo4jCommand(environment) {
            @Override
            protected Bootloader.Dbms createDbmsBootloader() {
                var bootloader = spy(new Bootloader.Dbms(TestEntryPoint.class, environment, expandCommands, verbose));
                ProcessManager pm = getProcessManager(bootloader, handler);
                doAnswer(inv -> pm).when(bootloader).processManager();
                return bootloader;
            }
        };

        return Neo4jCommand.asCommandLine(command, environment);
    }

    protected abstract ProcessManager getProcessManager(Bootloader bootloader, ProcessHandler processHandler);

    protected abstract static class StopTestProcessManagerBase extends ProcessManager {

        protected final ProcessHandler handler;

        StopTestProcessManagerBase(Bootloader bootloader, ProcessHandler handler) {
            super(bootloader);
            this.handler = handler;
        }

        @Override
        long run(List<String> command, ProcessStages processStages) throws CommandFailedException {
            if (commandMatches(command, "start")) {
                return handler.start();
            }

            throw new UnsupportedOperationException();
        }

        @Override
        Long getPidFromFile() {
            return handler.isRunning() ? handler.runningPid : null;
        }

        @Override
        Optional<ProcessHandle> getProcessHandle(long pid) throws CommandFailedException {
            if (handler.isRunning()) {
                ProcessHandle ph = mock(ProcessHandle.class);
                configureProcessHandleMock(ph, pid);
                return Optional.of(ph);
            }
            return Optional.empty();
        }

        abstract void configureProcessHandleMock(ProcessHandle handle, long pid);

        private static boolean commandMatches(List<String> command, String string) {
            return command.stream().anyMatch(cmd -> cmd.contains(string));
        }
    }

    private static class TestEntryPoint implements EntryPoint {

        @Override
        public int getPriority() {
            return Priority.HIGH.ordinal();
        }
    }
}
