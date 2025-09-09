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

import java.nio.file.Path;
import java.util.Arrays;
import org.neo4j.cli.ExecutionContext;
import picocli.CommandLine;

public class CliDumper {
    private static final String SEPARATOR = ";"; // can't ba a comma (,) since it's used internally in options
    private final boolean includeHidden;
    private final boolean includeDescription;
    private final boolean includeType;

    public CliDumper(boolean includeHidden, boolean includeDescription, boolean includeOptionType) {
        this.includeHidden = includeHidden;
        this.includeDescription = includeDescription;
        this.includeType = includeOptionType;
    }

    public String dump(Neo4jAdminCommand cmd) {
        return dump(cmd.getActualAdminCommand(new ExecutionContext(Path.of(""), Path.of(""))));
    }

    public String dump(CommandLine cmd) {
        StringBuilder stringBuilder = new StringBuilder();
        int numCommands = getNumCommands(cmd, 1);
        stringBuilder.append(header(numCommands));
        dump(cmd, 0, new String[numCommands], stringBuilder);
        return stringBuilder.toString();
    }

    private void dump(CommandLine cmd, int depth, String[] commands, StringBuilder stringBuilder) {
        CommandLine.Model.CommandSpec spec = cmd.getCommandSpec();
        String name = cmd.getCommandName();
        if (spec.usageMessage().hidden()) {
            if (!includeHidden) {
                return;
            }
            name += " <HIDDEN>";
        }
        commands[depth] = name;

        StringBuilder commandAndParams = new StringBuilder();
        for (String com : commands) {
            commandAndParams.append(com != null ? com : "").append(SEPARATOR);
        }
        String paramOptions = addParams(commandAndParams, spec);
        String comAndParams = commandAndParams.append(SEPARATOR).toString();

        for (CommandLine.Model.OptionSpec os : spec.options()) {
            if (os.hidden() && !includeHidden) {
                continue;
            }
            stringBuilder
                    .append(comAndParams)
                    .append(Arrays.toString(os.names()))
                    .append(os.hidden() && includeHidden ? " <HIDDEN>" : "")
                    .append(SEPARATOR)
                    .append(paramOptions)
                    .append(SEPARATOR)
                    .append(includeType ? " " + os.type().getSimpleName() : "")
                    .append(includeDescription ? asOneLine(os.description()) : "")
                    .append(System.lineSeparator());
        }
        cmd.getSubcommands().values().stream()
                .distinct()
                .forEach(subCmd -> dump(subCmd, depth + 1, commands, stringBuilder));
    }

    private static int getNumCommands(CommandLine cmd, int depth) {
        return cmd.getSubcommands().values().stream()
                .distinct()
                .map(c -> getNumCommands(c, depth + 1))
                .max(Integer::compare)
                .orElse(depth);
    }

    private static String header(int numCommands) {
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= numCommands; i++) {
            header.append("Command ").append(i).append(SEPARATOR);
        }
        header.append("Parameters")
                .append(SEPARATOR)
                .append("Option")
                .append(SEPARATOR)
                .append("Param descriptions")
                .append(SEPARATOR)
                .append("Option description")
                .append(System.lineSeparator());
        return header.toString();
    }

    private String addParams(StringBuilder commandAndParams, CommandLine.Model.CommandSpec spec) {
        var params = spec.positionalParameters();
        StringBuilder paramOptions = new StringBuilder();
        for (CommandLine.Model.PositionalParamSpec param : params) {
            if (param.hidden() && !includeHidden) {
                continue;
            }
            commandAndParams.append(param.paramLabel());
            commandAndParams.append(includeType ? " " + param.type().getSimpleName() : "");
            commandAndParams.append(param.hidden() && includeHidden ? " <HIDDEN>" : "");
            commandAndParams.append(" ");
            paramOptions
                    .append(includeDescription ? asOneLine(param.description()) : "")
                    .append(" ");
        }
        return paramOptions.toString();
    }

    private static String asOneLine(String[] strings) {
        return Arrays.toString(strings).replace(System.lineSeparator(), " ").replace("%n", "");
    }
}
