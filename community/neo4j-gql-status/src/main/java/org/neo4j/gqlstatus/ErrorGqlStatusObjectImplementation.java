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
package org.neo4j.gqlstatus;

import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ErrorGqlStatusObjectImplementation extends CommonGqlStatusObjectImplementation
        implements ErrorGqlStatusObject {
    private boolean isCause = false;
    private ErrorGqlStatusObject cause;
    private final Map<GqlParams.GqlParam, Object> paramMap;
    private final GqlStatusInfoCodes gqlStatusInfoCode;

    private ErrorGqlStatusObjectImplementation(
            GqlStatusInfoCodes gqlStatusInfoCode,
            Map<GqlParams.GqlParam, Object> parameters,
            ErrorGqlStatusObject cause,
            DiagnosticRecord diagnosticRecord) {
        super(gqlStatusInfoCode, diagnosticRecord, parameters);
        this.gqlStatusInfoCode = gqlStatusInfoCode;
        this.cause = cause;
        this.paramMap = replaceNulls(parameters);
    }

    // Decrease the risk of NullPointers in errors
    private Map<GqlParams.GqlParam, Object> replaceNulls(Map<GqlParams.GqlParam, Object> parameters) {
        return parameters.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> e.getValue() == null ? "null" : e.getValue()));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ErrorGqlStatusObjectImplementation gql
                && Objects.equals(gqlStatusInfoCode, gql.gqlStatusInfoCode)
                && Objects.equals(diagnosticRecord, gql.diagnosticRecord)
                && Objects.equals(cause, gql.cause)
                && Objects.equals(paramMap, gql.paramMap);
    }

    public static Builder from(GqlStatusInfoCodes gqlStatusInfo) {
        return new Builder(gqlStatusInfo);
    }

    @Override
    public Optional<ErrorGqlStatusObject> cause() {
        return Optional.ofNullable(cause);
    }

    public boolean isCause() {
        return isCause;
    }

    @Override
    public ErrorGqlStatusObject gqlStatusObject() {
        return this;
    }

    public void setCause(ErrorGqlStatusObject cause) {
        this.cause = cause;
        removeLoops(cause);
        propagatePositions(this.diagnosticRecord, cause);
    }

    private void removeCause() {
        this.cause = null;
    }

    public ErrorGqlStatusObject insertCause(ErrorGqlStatusObjectImplementation newCause) {
        if (this.cause == null) {
            setCause(newCause);
        } else {
            if (this.cause instanceof ErrorGqlStatusObjectImplementation implCause) {
                setCause(implCause.insertCause(newCause));
            } else {
                // It is possible we will never end up in this case, but in that case we make sure that all codes are
                // preserved.
                newCause.setCause(this.cause);
                setCause(newCause);
            }
        }
        return this;
    }

    public void markAsCause() {
        isCause = true;
    }

    @Override
    public void adjustPosition(int oldOffset, int oldLine, int oldColumn, int newOffset, int newLine, int newCol) {
        super.adjustPosition(oldOffset, oldLine, oldColumn, newOffset, newLine, newCol);
        cause().ifPresent(gqlStatusObjectCause -> {
            if (gqlStatusObjectCause instanceof ErrorGqlStatusObjectImplementation errorGqlStatusObjectImplementation) {
                // Recursive call for the chain of causes
                errorGqlStatusObjectImplementation.adjustPosition(
                        oldOffset, oldLine, oldColumn, newOffset, newLine, newCol);
            }
        });
    }

    @Override
    public String getMessage() {
        String gqlMessagePart = this.gqlStatusInfoCode.getMessage(paramMap);
        if (!gqlMessagePart.isEmpty()) {
            return String.format("%s: %s", gqlStatus(), gqlMessagePart);
        }
        return gqlStatus();
    }

    @Override
    public String legacyMessage() {
        return "";
    }

    @Override
    public String toString() {
        return recToString();
    }

    private String recToString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("Status: ");
        sb.append(gqlStatusInfoCode.getStatusString().trim());
        sb.append("\n");
        sb.append("Message: ");
        sb.append(insertMessageParameters(paramMap).trim());
        sb.append("\n");
        sb.append("Subcondition: ");
        sb.append(gqlStatusInfoCode.getSubCondition().trim());
        diagnosticRecord.getPosition().ifPresent(s -> {
            sb.append("\n");
            sb.append("Position: ");
            sb.append(s);
        });
        if (cause != null) {
            sb.append("\n");
            sb.append("Caused by:");

            return sb.append(indent(4, cause.toString())).toString();
        } else {
            return sb.toString();
        }
    }

    public static String indent(int n, String input) {
        String indent = " ".repeat(n);
        String[] lines = input.split("\n");

        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            sb.append(indent).append(line).append("\n");
        }

        if (!sb.isEmpty()) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }

    public static class Builder {
        private ErrorGqlStatusObject cause = null;
        private final Map<GqlParams.GqlParam, Object> paramMap = new HashMap<>();
        private final GqlStatusInfoCodes gqlStatusInfoCode;
        private final DiagnosticRecord.Builder diagnosticRecordBuilder = DiagnosticRecord.from();

        private Builder(GqlStatusInfoCodes gqlStatusInfo) {
            this.gqlStatusInfoCode = gqlStatusInfo;
        }

        public Builder withParam(GqlParams.StringParam param, String value) {
            if (value != null) {
                this.paramMap.put(param, value);
            }
            return this;
        }

        public Builder withParam(GqlParams.BooleanParam param, boolean value) {
            this.paramMap.put(param, value);
            return this;
        }

        public Builder withParam(GqlParams.NumberParam param, Number value) {
            if (value != null) {
                this.paramMap.put(param, value);
            }
            return this;
        }

        public Builder withParam(GqlParams.ListParam param, List<?> value) {
            if (value != null) {
                this.paramMap.put(param, value);
            }
            return this;
        }

        public Builder withCause(ErrorGqlStatusObject cause) {
            if (cause instanceof ErrorGqlStatusObjectImplementation c) {
                c.markAsCause();
            }
            this.cause = cause;
            return this;
        }

        public <T> Builder withDiagnosticRecordProperty(DiagnosticRecordProperty<T> property, T value) {
            diagnosticRecordBuilder.withProperty(property, value);
            return this;
        }

        public Builder atPosition(int offset, int line, int col) {
            // Assert that the position is valid (only run in tests)
            // An invalid position might indicate that offset, line and column is provided in incorrect order
            assert line < 1 // Default/test position
                    || (line == 1 && col == offset + 1)
                    || (line > 1 && offset >= col);
            diagnosticRecordBuilder.atPosition(offset, line, col);
            return this;
        }

        public ErrorGqlStatusObject build() {
            diagnosticRecordBuilder.withClassification(gqlStatusInfoCode.getClassification());
            DiagnosticRecord diagnosticRecord = diagnosticRecordBuilder.build();
            /*
             * Theoretically, it would be enough to run removeLoops() and propagatePositions() when we are on the
             * top-level error, but there is no way to know if we are constructing a cause or a top-level error.
             * As errors seldom have more than 3 causes,
             * we can live with the inefficiency of running them on every step.
             */
            removeLoops(cause);
            propagatePositions(diagnosticRecord, cause);
            return new ErrorGqlStatusObjectImplementation(gqlStatusInfoCode, paramMap, cause, diagnosticRecord);
        }

        public ErrorGqlStatusObjectImplementation buildImpl() {
            return (ErrorGqlStatusObjectImplementation) build();
        }
    }

    private static void removeLoops(ErrorGqlStatusObject cause) {
        ErrorGqlStatusObject currentCause = cause;
        final List<ErrorGqlStatusObject> list = new ArrayList<>();
        while (currentCause != null) {
            if (list.contains(currentCause)) {
                ((ErrorGqlStatusObjectImplementation) list.getLast()).removeCause();
                break;
            } else {
                list.add(currentCause);
                currentCause = currentCause.cause().orElse(null);
            }
        }
    }

    private static void propagatePositions(
            DiagnosticRecord currentDiagnosticRecord, ErrorGqlStatusObject currentCause) {
        if (currentCause instanceof ErrorGqlStatusObjectImplementation c) {
            // The current error has no position but its cause has one => propagate cause position to current error
            if (!currentDiagnosticRecord.hasPosition() && c.diagnosticRecord.hasPosition()) {
                var position = c.diagnosticRecord.getPositionMap();
                currentDiagnosticRecord.updatePosition(
                        position.getOrDefault("offset", -1),
                        position.getOrDefault("line", -1),
                        position.getOrDefault("column", -1));
            }
            // The current error has a position but its cause does not => propagate current error position to cause
            else if (currentDiagnosticRecord.hasPosition() && !c.diagnosticRecord.hasPosition()) {
                var position = currentDiagnosticRecord.getPositionMap();
                c.diagnosticRecord.updatePosition(
                        position.getOrDefault("offset", -1),
                        position.getOrDefault("line", -1),
                        position.getOrDefault("column", -1));
            }
            // Continue down the chain of causes
            propagatePositions(c.diagnosticRecord, c.cause);
        }
    }
}
