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
package org.neo4j.shell.printer;

import static java.lang.System.lineSeparator;
import static org.fusesource.jansi.internal.CLibrary.STDERR_FILENO;
import static org.fusesource.jansi.internal.CLibrary.STDOUT_FILENO;
import static org.fusesource.jansi.internal.CLibrary.isatty;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.exceptions.DiscoveryException;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.internal.helpers.Exceptions;
import org.neo4j.shell.cli.ErrorFormat;
import org.neo4j.shell.cli.Format;
import org.neo4j.shell.exception.AnsiFormattedException;
import org.neo4j.shell.log.Logger;

/**
 * A basic logger which prints Ansi formatted text to STDOUT and STDERR
 */
public class AnsiPrinter implements Printer {
    private static final String ERROR_50N42 = "50N42";
    private static final Logger log = Logger.create();
    private static final List<String> TRIM_STATUS_DESC_START = List.of("error: ", "warn: ", "info: ");
    private final PrintStream out;
    private final PrintStream err;
    private final ErrorFormat errorFormat;
    private Format format;

    public AnsiPrinter(Format format, ErrorFormat errorFormat, PrintStream out, PrintStream err) {
        this.format = format;
        this.out = out;
        this.err = err;
        this.errorFormat = errorFormat;

        try {
            if (isOutputInteractive()) {
                Ansi.setEnabled(true);
                AnsiConsole.systemInstall();
            } else {
                Ansi.setEnabled(false);
            }
        } catch (Throwable t) {
            log.warn("Not running on a distro with standard c library, disabling Ansi", t);
            Ansi.setEnabled(false);
        }
    }

    private static Throwable getRootCause(final Throwable th) {
        Throwable cause = th;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * @return true if the shell is outputting to a TTY, false otherwise (e.g., we are writing to a file)
     * @throws UnsatisfiedLinkError maybe if standard c library can't be found
     * @throws NoClassDefFoundError maybe if standard c library can't be found
     */
    private static boolean isOutputInteractive() {
        return 1 == isatty(STDOUT_FILENO) && 1 == isatty(STDERR_FILENO);
    }

    @Override
    public Format getFormat() {
        return format;
    }

    @Override
    public void setFormat(Format format) {
        this.format = format;
    }

    @Override
    public void printError(Throwable throwable) {
        printError(getFormattedMessage(throwable));
    }

    @Override
    public void printError(String s) {
        err.println(s);
    }

    @Override
    public void printOut(final String msg) {
        out.println(msg);
    }

    /**
     * Formatting for Bolt exceptions.
     */
    public String getFormattedMessage(final Throwable e) {
        AnsiFormattedText msg = AnsiFormattedText.s().brightRed();

        if (e instanceof AnsiFormattedException ae) {
            msg.append(ae.getFormattedMessage());
        } else if (e instanceof ClientException
                && e.getMessage() != null
                // TODO Replace with status code (but still be backwards compatible!)
                && e.getMessage().contains("Missing username")) {
            // Username and password was not specified
            msg.append(e.getMessage())
                    .append("\nPlease specify --username, and optionally --password, as argument(s)")
                    .append("\nor as environment variable(s), NEO4J_USERNAME, and NEO4J_PASSWORD respectively.")
                    .append("\nSee --help for more info.");
        } else if (errorFormat == ErrorFormat.GQL
                && e instanceof Neo4jException driverException
                // For code 50N42 (generic gql code), we fallback to legacy mode and only display exception message.
                // This way we avoid reporting the generic error for servers that have not converted to gql.
                && !ERROR_50N42.equals(driverException.gqlStatus())) {
            boolean first = true;
            for (final var gqlError : collectGqlExceptions(driverException)) {
                if (!first) msg.append("  ");
                msg.append(gqlError.gqlStatus())
                        .append(": ")
                        .append(trimStatusDesc(gqlError.statusDescription()))
                        .append(lineSeparator());
                first = false;
            }
        } else if (errorFormat == ErrorFormat.STACKTRACE) {
            msg.append(Exceptions.stringify(e));
        } else {
            Throwable cause = e;

            // Get the suppressed root cause of ServiceUnavailableExceptions
            if (e instanceof ServiceUnavailableException) {
                Throwable[] suppressed = e.getSuppressed();
                for (Throwable s : suppressed) {
                    if (s instanceof DiscoveryException) {
                        cause = getRootCause(s);
                        break;
                    }
                }
            }

            if (cause.getMessage() != null) {
                msg.append(cause.getMessage());
            } else {
                msg.append(cause.getClass().getSimpleName());
            }
        }

        return msg.resetAndRender();
    }

    private static List<Neo4jException> collectGqlExceptions(Neo4jException e) {
        final var exceptionsWithCauses = new ArrayList<Neo4jException>();
        while (e != null) {
            exceptionsWithCauses.add(e);
            e = e.gqlCause().orElse(null);
        }
        return exceptionsWithCauses.reversed();
    }

    private static String trimStatusDesc(String in) {
        for (final var toTrim : TRIM_STATUS_DESC_START) {
            if (in.startsWith(toTrim)) return in.substring(toTrim.length());
        }
        return in;
    }
}
