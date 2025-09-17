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
package org.neo4j.exceptions;

import java.time.format.DateTimeParseException;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;

/**
 * {@code TemporalParseException} is thrown if parsing of a TemporalValue is unsuccessful.
 * The constructor parameters {@code parsedData} and {@code errorIndex} can optionally be provided
 * in order to conform with Java's {@code DateTimeParseException} and {@code SyntaxException}.
 */
public class TemporalParseException extends SyntaxException {

    private TemporalParseException(ErrorGqlStatusObject gqlStatusObject, String errorMsg, Throwable cause) {
        super(gqlStatusObject, errorMsg, cause);
    }

    private TemporalParseException(
            ErrorGqlStatusObject gqlStatusObject, String errorMsg, String parsedData, int errorIndex) {
        super(gqlStatusObject, errorMsg, parsedData, errorIndex);
    }

    private TemporalParseException(
            ErrorGqlStatusObject gqlStatusObject, String errorMsg, String parsedData, int errorIndex, Throwable cause) {
        super(gqlStatusObject, errorMsg, parsedData, errorIndex, cause);
    }

    public static TemporalParseException cannotProcessDateTime(String input, Throwable e) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N11)
                        .withParam(GqlParams.StringParam.input, input)
                        .build())
                .build();
        return new TemporalParseException(gql, e.getMessage(), e);
    }

    public static TemporalParseException cannotParseText(String type, String text) {
        var gql = GqlHelper.getGql22007_22N36(text, type);
        return new TemporalParseException(gql, "Text cannot be parsed to a " + type, text, 0);
    }

    public static TemporalParseException cannotParseInput(String type, String text, String input) {
        var gql = GqlHelper.getGql22007_22N36(text, type);
        return new TemporalParseException(gql, "Cannot parse " + text + " as a " + type, input, 0);
    }

    public static TemporalParseException cannotParseInputInvalidCharacter(String type, String text, String input) {
        var gql = GqlHelper.getGql22007_22N36_42I65(text, type);
        return new TemporalParseException(gql, "Cannot parse " + text + " as a " + type, input, 0);
    }

    public static TemporalParseException cannotParseToDateHint(String input) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22007)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N35)
                        .withParam(GqlParams.StringParam.input, input)
                        .build())
                .build();
        return new TemporalParseException(
                gql,
                "Text cannot be parsed to a Date. Hint, year+month needs to have two digits for "
                        + "month (e.g. 2015-02) and " + "ordinal dates three digits (e.g. 2015-032).",
                null);
    }

    public static TemporalParseException mismatchedPattern(String pattern, String input, String valueType) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22007)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I68)
                        .withParam(GqlParams.StringParam.input1, pattern)
                        .withParam(GqlParams.StringParam.input2, input)
                        .withParam(GqlParams.StringParam.valueType, valueType)
                        .build())
                .build();
        return new TemporalParseException(
                gql,
                String.format(
                        "Pattern, `%s`, does not match input, `%s`. Verify that the pattern is valid for constructing `%s`.",
                        pattern, input, valueType),
                null);
    }

    public static TemporalParseException failedToProcessDateTime(String prettyValue, DateTimeParseException e) {
        var gql = GqlHelper.getGql22000_22N11(prettyValue);
        return new TemporalParseException(gql, e.getMessage(), e.getParsedString(), e.getErrorIndex(), e);
    }

    public static TemporalParseException cannotProcessCause(String value, Throwable e) {
        var gql = GqlHelper.getGql22000_22N11(value);
        return new TemporalParseException(gql, e.getMessage(), e);
    }

    public static TemporalParseException invalidTimeZone(String timeZone, DateTimeParseException e) {
        var gql = GqlHelper.getGql22000_22N11(timeZone);
        return new TemporalParseException(
                gql, "Invalid value for TimeZone: " + e.getMessage(), e.getParsedString(), e.getErrorIndex(), e);
    }
}
