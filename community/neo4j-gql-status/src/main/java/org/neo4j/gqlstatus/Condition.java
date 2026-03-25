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

import java.util.Locale;

// The first four entries of the enum must be in this specific order for GQL-status objects to be sorted in severity
// order
public enum Condition {
    NO_DATA,
    WARNING,
    SUCCESSFUL_COMPLETION,
    INFORMATIONAL,
    CONNECTION_EXCEPTION,
    DATA_EXCEPTION,
    SYNTAX_ERROR_OR_ACCESS_RULE_VIOLATION,
    GENERAL_PROCESSING_EXCEPTION,
    SYSTEM_CONFIGURATION_OR_OPERATION_EXCEPTION,
    PROCEDURE_EXCEPTION,
    FUNCTION_EXCEPTION,
    DEPENDENT_OBJECT_ERROR,
    GRAPH_TYPE_VIOLATION,
    INVALID_TRANSACTION_STATE,
    INVALID_TRANSACTION_TERMINATION,
    TRANSACTION_ROLLBACK;

    public static Condition fromClass(String classNbr) {
        return switch (classNbr) {
            case "00" -> SUCCESSFUL_COMPLETION;
            case "01" -> WARNING;
            case "02" -> NO_DATA;
            case "03" -> INFORMATIONAL;
            case "08" -> CONNECTION_EXCEPTION;
            case "22" -> DATA_EXCEPTION;
            case "25" -> INVALID_TRANSACTION_STATE;
            case "2D" -> INVALID_TRANSACTION_TERMINATION;
            case "40" -> TRANSACTION_ROLLBACK;
            case "42" -> SYNTAX_ERROR_OR_ACCESS_RULE_VIOLATION;
            case "50" -> GENERAL_PROCESSING_EXCEPTION;
            case "51" -> SYSTEM_CONFIGURATION_OR_OPERATION_EXCEPTION;
            case "52" -> PROCEDURE_EXCEPTION;
            case "53" -> FUNCTION_EXCEPTION;
            case "G1" -> DEPENDENT_OBJECT_ERROR;
            case "G2" -> GRAPH_TYPE_VIOLATION;
            default -> throw new IllegalArgumentException("Unexpected class for GqlStatusInfoCodes: " + classNbr);
        };
    }

    public static String createStandardDescription(Condition condition, String subcondition) {
        return switch (condition) {
            case WARNING -> "warn: " + subcondition;
            case INFORMATIONAL -> "info: " + subcondition;
            case SUCCESSFUL_COMPLETION -> {
                String successBaseMessage = "note: successful completion";
                if (subcondition.isEmpty()) {
                    yield successBaseMessage;
                } else {
                    yield successBaseMessage + " - " + subcondition;
                }
            }
            case NO_DATA -> {
                String successBaseMessage = "note: no data";
                if (subcondition.isEmpty()) {
                    yield successBaseMessage;
                } else {
                    yield successBaseMessage + " - " + subcondition;
                }
            }
            default -> {
                String exceptionBaseMessage = "error: " + condition.createConditionString();
                if (subcondition.isEmpty()) {
                    yield exceptionBaseMessage;
                } else {
                    yield exceptionBaseMessage + " - " + subcondition;
                }
            }
        };
    }

    private String createConditionString() {
        return this.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
