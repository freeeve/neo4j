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
package org.neo4j.server.startup.validation;

import static org.neo4j.server.startup.validation.ConfigValidationSummary.ValidationResult.ERRORS;
import static org.neo4j.server.startup.validation.ConfigValidationSummary.ValidationResult.OK;
import static org.neo4j.server.startup.validation.ConfigValidationSummary.ValidationResult.WARNINGS;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;
import org.neo4j.logging.Log;
import org.neo4j.util.VisibleForTesting;

public class ConfigValidationSummary {
    private ValidationResult result = OK;
    private final List<Event> events = new ArrayList<>();

    public ConfigValidationSummary() {}

    public void print(PrintStream out, boolean verbose) {
        for (var event : events) {
            event.print(out, verbose);
            out.println();
        }
    }

    public String message(boolean verbose) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        print(new PrintStream(output), verbose);
        return output.toString();
    }

    public ValidationResult result() {
        return result;
    }

    public void add(Event event) {
        result = result.and(event.result());
        events.add(event);
    }

    public void printClosingStatement(PrintStream out) {
        out.println(closingStatement());
    }

    public String closingStatement() {
        if (result == ERRORS) {
            return "Configuration file validation failed.";
        } else if (result == WARNINGS) {
            return "Configuration file validation successful (with warnings).";
        } else {
            return "Configuration file validation successful.";
        }
    }

    public void log(Log log) {
        for (var event : events) {
            event.log(log);
        }
    }

    public enum ValidationResult {
        OK,
        WARNINGS,
        ERRORS;

        @VisibleForTesting
        ValidationResult and(ValidationResult other) {
            return ObjectUtils.max(this, other);
        }
    }

    public interface Event {
        void print(PrintStream out, boolean verbose);

        ValidationResult result();

        default Event treatErrorsAsWarnings() {
            return new NoBigDealEvent(this);
        }

        void log(Log log);
    }

    private record NoBigDealEvent(Event delegate) implements Event {
        @Override
        public void print(PrintStream out, boolean verbose) {
            delegate.print(out, verbose);
        }

        @Override
        public ValidationResult result() {
            var actualResult = delegate.result();
            if (actualResult == ERRORS) {
                return WARNINGS;
            }
            return actualResult;
        }

        @Override
        public Event treatErrorsAsWarnings() {
            return this;
        }

        @Override
        public void log(Log log) {
            delegate.log(new NoBigDealLog(log));
        }

        private record NoBigDealLog(Log delegate) implements Log {

            @Override
            public boolean isDebugEnabled() {
                return delegate.isDebugEnabled();
            }

            @Override
            public boolean isWarnEnabled() {
                return delegate.isWarnEnabled();
            }

            @Override
            public boolean isInfoEnabled() {
                return delegate.isInfoEnabled();
            }

            @Override
            public boolean isErrorEnabled() {
                return delegate.isErrorEnabled();
            }

            @Override
            public void debug(String message) {
                delegate.debug(message);
            }

            @Override
            public void debug(String message, Throwable throwable) {
                delegate.debug(message, throwable);
            }

            @Override
            public void debug(String format, Object... arguments) {
                delegate.debug(format, arguments);
            }

            @Override
            public void info(String message) {
                delegate.info(message);
            }

            @Override
            public void info(String message, Throwable throwable) {
                delegate.info(message, throwable);
            }

            @Override
            public void info(String format, Object... arguments) {
                delegate.info(format, arguments);
            }

            @Override
            public void warn(String message) {
                delegate.warn(message);
            }

            @Override
            public void warn(String message, Throwable throwable) {
                delegate.warn(message, throwable);
            }

            @Override
            public void warn(String format, Object... arguments) {
                delegate.warn(format, arguments);
            }

            @Override
            public void error(String message) {
                // reduce error logs down to warning
                delegate.warn(message);
            }

            @Override
            public void error(String message, Throwable throwable) {
                // reduce error logs down to warning
                delegate.warn(message, throwable);
            }

            @Override
            public void error(String format, Object... arguments) {
                // reduce error logs down to warning
                delegate.warn(format, arguments);
            }
        }
    }

    public static class ResultEvent implements Event {
        private final List<ConfigValidationIssue> issues;
        private final String label;
        private ValidationResult result;

        public ResultEvent(String label, List<ConfigValidationIssue> issues) {
            this.label = label;
            this.issues = issues;
            this.result = OK;

            for (var issue : issues) {
                result = result.and(issue.isError() ? ERRORS : WARNINGS);
            }
        }

        @Override
        public void print(PrintStream out, boolean verbose) {
            out.printf("Validating %s%n", label);
            printIssueCount(issues, out);

            for (var issue : issues) {
                printIssue(issue, out, verbose);
            }
        }

        private void printIssueCount(List<ConfigValidationIssue> issues, PrintStream out) {
            if (issues.isEmpty()) {
                out.printf("No issues found.%n");
            } else {
                out.printf("%d issue%s found.%n", issues.size(), issues.size() == 1 ? "" : "s");
            }
        }

        private void printIssue(ConfigValidationIssue issue, PrintStream out, boolean verbose) {
            out.printf("%s%n", issue.getMessage());
            if (verbose) {
                issue.printStackTrace(out);
            }
        }

        @Override
        public ValidationResult result() {
            return result;
        }

        @Override
        public ResultEvent treatErrorsAsWarnings() {
            var issues =
                    this.issues.stream().map(ConfigValidationIssue::asWarning).collect(Collectors.toList());
            return new ResultEvent(label, issues);
        }

        @Override
        public void log(Log log) {
            for (var issue : issues) {
                if (issue.isError()) {
                    log.error(issue.getLogMessage());
                } else {
                    log.warn(issue.getLogMessage());
                }
            }
        }
    }

    public static class ErrorEvent implements Event {
        private final String label;
        private final Exception exception;

        public ErrorEvent(String label, Exception exception) {
            this.label = label;
            this.exception = exception;
        }

        @Override
        public void print(PrintStream out, boolean verbose) {
            out.printf("Error when validating %s: %s%n", label, exception.getMessage());
            if (verbose) {
                exception.printStackTrace(out);
            }
        }

        @Override
        public ValidationResult result() {
            return ERRORS;
        }

        private String message() {
            return String.format("Error when validating %s: %s", label, exception.getMessage());
        }

        @Override
        public void log(Log log) {
            log.error(message());
        }
    }

    public static class MessageEvent implements Event {
        private final String message;

        public MessageEvent(String message) {
            this.message = message;
        }

        @Override
        public void print(PrintStream out, boolean verbose) {
            out.println(message);
        }

        @Override
        public ValidationResult result() {
            return OK;
        }

        @Override
        public void log(Log log) {
            // do not log
        }
    }
}
