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

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import org.neo4j.cli.ExecutionContext;
import picocli.CommandLine;

@CommandLine.Command(name = "generate-cli-dump", description = "Generates cli dump")
public class CliDumpGenerator implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Output directory")
    private Path output;

    private void dumpCli(Neo4jAdminCommand command) throws IOException {
        CommandLine commandLine = command.getActualAdminCommand(new ExecutionContext(output, output));
        String name = commandLine
                .getCommand()
                .getClass()
                .getAnnotation(CommandLine.Command.class)
                .name();
        Path file = output.resolve(name + "_dump.csv");
        String dump = new CliDumper(true, true, false).dump(commandLine);
        Files.writeString(file, dump, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public Integer call() throws Exception {
        if (Files.exists(output) && !Files.isDirectory(output)) {
            throw new FileAlreadyExistsException(output.toString() + " Needs to be a directory");
        }
        Files.createDirectories(output);

        dumpCli(new Neo4jAdminCommand(Environment.SYSTEM));
        dumpCli(new Neo4jCommand(Environment.SYSTEM));
        return 0;
    }

    public static void main(String[] args) {
        new CommandLine(new CliDumpGenerator()).execute(args);
    }
}
