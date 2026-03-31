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

import static org.neo4j.gqlstatus.GqlHelper.getCompleteMessage;
import static org.neo4j.gqlstatus.GqlHelper.getGql22G03_22N27;
import static org.neo4j.gqlstatus.GqlHelper.getGql42N51;

import java.util.List;
import java.util.Locale;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;

public class ParameterWrongTypeException extends Neo4jException {

    private ParameterWrongTypeException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    public static ParameterWrongTypeException internalError(String msgTitle, String message) {
        var gql = GqlHelper.get50N00(msgTitle, message);
        return new ParameterWrongTypeException(gql, message);
    }

    public static ParameterWrongTypeException expectedNodeFoundInstead(
            String got, String gotPretty, String gotCypherType) {
        var gql = GqlHelper.getGql22G03_22N01(gotPretty, List.of("NODE"), gotCypherType);
        return new ParameterWrongTypeException(gql, String.format("Expected to find a node but found %s instead", got));
    }

    public static ParameterWrongTypeException expectedNodeAtFoundInstead(
            String path, String got, String gotPretty, String gotCypherType) {
        var gql = GqlHelper.getGql22G03_22N01(gotPretty, List.of("NODE"), gotCypherType);
        return new ParameterWrongTypeException(
                gql, String.format("Expected to find a node at '%s' but found %s instead", path, got));
    }

    public static ParameterWrongTypeException expectedEntityAtRefSlotFoundInstead(
            int refSlot, String entity, String got, String gotPretty, String gotCypherType) {
        var gql = GqlHelper.getGql22G03_22N01(gotPretty, List.of(entity.toUpperCase(Locale.ROOT)), gotCypherType);
        return new ParameterWrongTypeException(
                gql, String.format("Expected to find a %s at ref slot %s but found %s instead", entity, refSlot, got));
    }

    public static ParameterWrongTypeException expectedEntityAtLongSlotFoundInstead(
            int longSlot, String entity, String got, String gotPretty, String gotCypherType) {
        var gql = GqlHelper.getGql22G03_22N01(gotPretty, List.of(entity.toUpperCase(Locale.ROOT)), gotCypherType);
        return new ParameterWrongTypeException(
                gql,
                String.format("Expected to find a %s at long slot %s but found %s instead", entity, longSlot, got));
    }

    public static ParameterWrongTypeException expectedRelFoundInstead(
            String got, String gotPretty, String gotCypherType) {
        var gql = GqlHelper.getGql22G03_22N01(gotPretty, List.of("NODE"), gotCypherType);
        return new ParameterWrongTypeException(
                gql, String.format("Expected to find a relationship but found %s instead", got));
    }

    public static ParameterWrongTypeException expectedNodeOrRelLabels(String gotPretty, String gotCypherType) {
        var gql = GqlHelper.getGql22G03_22N01(gotPretty, List.of("NODE", "RELATIONSHIP"), gotCypherType);
        return new ParameterWrongTypeException(gql, "Expected a node or relationship when checking types or labels.");
    }

    public static ParameterWrongTypeException expectedBoolean(String paramName, String input, String prettifiedInput) {
        var gql =
                getGql22G03_22N27(prettifiedInput, GqlParams.StringParam.param.process(paramName), List.of("BOOLEAN"));
        return new ParameterWrongTypeException(
                gql, String.format("Expected parameter $%s to have type Boolean but was %s", paramName, input));
    }

    public static ParameterWrongTypeException expectedStringOrStringList(
            String command, String input, String prettifiedInput) {
        var gql = getGql22G03_22N27(prettifiedInput, command, List.of("STRING", "LIST<STRING>"));
        return new ParameterWrongTypeException(
                gql, String.format("Expected a string or a list of strings, but got: %s", input));
    }

    public static ParameterWrongTypeException expectedStringOrStringList2(String paramName, String input) {
        var context = "parameter $`%s`".formatted(paramName);
        var gql = getGql22G03_22N27(input, context, List.of("STRING", "LIST<STRING>"));
        return new ParameterWrongTypeException(
                gql,
                "Expected parameter `$%s` to be a non-empty String or a non-empty List of non-empty Strings but was `%s`."
                        .formatted(paramName, input));
    }

    public static ParameterWrongTypeException expectedListParameterToContainStrings(String paramName, String input) {
        var context = "parameter $`%s`".formatted(paramName);
        var gql = getGql22G03_22N27(input, context, List.of("LIST<STRING>"));
        return new ParameterWrongTypeException(
                gql,
                "Expected parameter `$%s` to only contain non-empty Strings but contained `%s`."
                        .formatted(paramName, input));
    }

    public static ParameterWrongTypeException expectedParameterToBeString(
            String paramName, String input, String prettifiedInput) {
        var cause =
                getGql22G03_22N27(prettifiedInput, GqlParams.StringParam.param.process(paramName), List.of("STRING"));
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N51)
                .withParam(GqlParams.StringParam.param, paramName)
                .withCause(cause)
                .build();
        return new ParameterWrongTypeException(
                gql, String.format("Expected parameter $%s to have type String but was %s", paramName, input));
    }

    public static ParameterWrongTypeException expectedParameterToBeString42N51(
            boolean prettyPrint, String paramName, String input, String prettifiedInput) {
        String p;
        String v;

        if (prettyPrint) {
            p = String.format("`$%s`", paramName);
            v = String.format("`%s`.", prettifiedInput);
        } else {
            p = String.format("$%s", paramName);
            v = input;
        }
        var gql = getGql42N51(
                paramName,
                getGql22G03_22N27(prettifiedInput, GqlParams.StringParam.param.process(paramName), List.of("STRING")));
        return new ParameterWrongTypeException(
                gql, String.format("Expected parameter %s to have type String but was %s", p, v));
    }

    public static ParameterWrongTypeException expectedStringButGotValue(
            String command, String input, String prettifiedInput) {
        var gql = getGql22G03_22N27(prettifiedInput, command, List.of("STRING"));
        return new ParameterWrongTypeException(gql, String.format("Expected a string, but got: %s", input));
    }

    public static ParameterWrongTypeException expectedStringButGotType(
            String paramName, String inputType, String prettifiedInput) {
        var gql = getGql22G03_22N27(prettifiedInput, GqlParams.StringParam.param.process(paramName), List.of("STRING"));
        return new ParameterWrongTypeException(gql, String.format("Expected String, but got: %s", inputType));
    }

    public static ParameterWrongTypeException expectedString(String key, String inputType, String prettifiedInput) {
        var gql = getGql22G03_22N27(prettifiedInput, key, List.of("STRING"));
        return new ParameterWrongTypeException(
                gql, String.format("Parameter '%s' has the wrong type, expected String, got %s.", key, inputType));
    }

    public static ParameterWrongTypeException expectedPasswordToBeString(String passwordParameter, String type) {
        var gql = getGql42N51(
                passwordParameter,
                getGql22G03_22N27(
                        "******", // Obfuscate the wrongly formatted password to not leak sensitive information
                        GqlParams.StringParam.param.process(passwordParameter),
                        List.of("STRING")));
        return new ParameterWrongTypeException(
                gql,
                String.format(
                        "Expected password parameter $%s to have type String but was %s", passwordParameter, type));
    }

    public static ParameterWrongTypeException onlyStringValuesAsPassword(
            String paramName, String expectedType, String inputType) {
        var gql = getGql42N51(paramName, getGql22G03_22N27("type " + inputType, "password", List.of("STRING")));
        return new ParameterWrongTypeException(
                gql, String.format("Only %s values are accepted as password, got: %s", expectedType, inputType));
    }

    public static ParameterWrongTypeException expectedParameterToBeLiteral(
            String paramName, String input, String prettifiedInput) {
        var gql = getGql22G03_22N27(
                prettifiedInput,
                GqlParams.StringParam.param.process(paramName),
                List.of(
                        "BOOLEAN",
                        "STRING",
                        "INTEGER",
                        "FLOAT",
                        "DATE",
                        "LOCAL TIME",
                        "ZONED TIME",
                        "DURATION",
                        "POINT",
                        "LIST<BOOLEAN>",
                        "LIST<STRING>",
                        "LIST<INTEGER>",
                        "LIST<FLOAT>",
                        "LIST<DATE>",
                        "LIST<LOCAL TIME>",
                        "LIST<ZONED TIME>",
                        "LIST<DURATION>",
                        "LIST<POINT>"));
        return new ParameterWrongTypeException(
                gql, String.format("Expected parameter $%s to be a single literal value but was %s", paramName, input));
    }

    public static ParameterWrongTypeException parameterWrongType(
            String paramName, String prettifiedInput, String component, String validType) {
        var gql = getGql42N51(paramName, getGql22G03_22N27(prettifiedInput, component, List.of(validType)));

        return new ParameterWrongTypeException(gql, getCompleteMessage(gql));
    }

    @Override
    public Status status() {
        return Status.Statement.TypeError;
    }
}
