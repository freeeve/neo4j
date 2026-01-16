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
package org.neo4j.cli;

import org.neo4j.kernel.api.exceptions.ConsoleFriendlyException;

public class CommandFailedException extends ConsoleFriendlyException {
    private static final int DEFAULT_ERROR_EXIT_CODE = ExitCode.FAIL;

    private final int exitCode;

    public CommandFailedException(String message) {
        this(message, DEFAULT_ERROR_EXIT_CODE);
    }

    public CommandFailedException(String message, int exitCode) {
        this(message, exitCode, true);
    }

    public CommandFailedException(String message, int exitCode, boolean singleHandedlyOrganiseConsoleOutput) {
        super(message, singleHandedlyOrganiseConsoleOutput);
        this.exitCode = exitCode;
    }

    public CommandFailedException(Throwable cause) {
        this(cause, DEFAULT_ERROR_EXIT_CODE);
    }

    public CommandFailedException(Throwable cause, int exitCode) {
        this(cause, exitCode, true);
    }

    public CommandFailedException(Throwable cause, int exitCode, boolean singleHandedlyOrganiseConsoleOutput) {
        super(cause, singleHandedlyOrganiseConsoleOutput);
        this.exitCode = exitCode;
    }

    public CommandFailedException(String message, Throwable cause) {
        this(message, cause, DEFAULT_ERROR_EXIT_CODE);
    }

    public CommandFailedException(String message, Throwable cause, int exitCode) {
        this(message, cause, exitCode, true);
    }

    public CommandFailedException(
            String message, Throwable cause, int exitCode, boolean singleHandedlyOrganiseConsoleOutput) {
        super(message, cause, singleHandedlyOrganiseConsoleOutput);
        this.exitCode = exitCode;
    }

    public int getExitCode() {
        return exitCode;
    }
}
