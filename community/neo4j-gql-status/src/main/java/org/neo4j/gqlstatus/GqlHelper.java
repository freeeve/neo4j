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

import java.util.List;

public class GqlHelper {

    public static ErrorGqlStatusObject getGql08N06(ErrorGqlStatusObject cause) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N06)
                .withCause(cause)
                .build();
    }

    public static ErrorGqlStatusObject getGql08N06_22N03(
            String component, String valueType, Number lower, Number upper, Number value) {
        return GqlHelper.getGql08N06(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N03)
                .withParam(GqlParams.StringParam.component, component)
                .withParam(GqlParams.StringParam.valueType, valueType)
                .withParam(GqlParams.NumberParam.lower, lower)
                .withParam(GqlParams.NumberParam.upper, upper)
                .withParam(GqlParams.StringParam.value, String.valueOf(value))
                .build());
    }

    public static ErrorGqlStatusObject getGql08N06_22N04(String input, String context, List<String> inputList) {
        return getGql08N06(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N04)
                .withParam(GqlParams.StringParam.input, input)
                .withParam(GqlParams.StringParam.context, context)
                .withParam(GqlParams.ListParam.inputList, inputList)
                .build());
    }

    public static ErrorGqlStatusObject getGql08N09(String databaseName) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N09)
                .withParam(GqlParams.StringParam.db, databaseName)
                .build();
    }

    public static ErrorGqlStatusObject getGql08N11_22N01(String value, List<String> valueTypeList, String valueType) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N11)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N01)
                        .withParam(GqlParams.StringParam.value, value)
                        .withParam(GqlParams.ListParam.valueTypeList, valueTypeList)
                        .withParam(GqlParams.StringParam.valueType, valueType)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22000_22N11(String input) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N11)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.input, input)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22000_22N51(String dbName) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N51)
                        .withParam(GqlParams.StringParam.db, dbName)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22003(String value, int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22003)
                .withParam(GqlParams.StringParam.value, value)
                .atPosition(offset, line, column)
                .build();
    }

    public static ErrorGqlStatusObject getGql22003_22N03(
            String component, String valueType, Number lower, Number upper, String value) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22003)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N03)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.component, component)
                        .withParam(GqlParams.StringParam.valueType, valueType)
                        .withParam(GqlParams.NumberParam.lower, lower)
                        .withParam(GqlParams.NumberParam.upper, upper)
                        .withParam(GqlParams.StringParam.value, value)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22N38_22N03(
            String fun, String component, String valueType, Number lower, Number upper, Object value) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N38)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withParam(GqlParams.StringParam.value, fun)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N03)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.component, component)
                        .withParam(GqlParams.StringParam.valueType, valueType)
                        .withParam(GqlParams.NumberParam.lower, lower)
                        .withParam(GqlParams.NumberParam.upper, upper)
                        .withParam(GqlParams.StringParam.value, String.valueOf(value))
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22007_22N03(
            String component, String valueType, Number lower, Number upper, Object value) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22007)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N03)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.component, component)
                        .withParam(GqlParams.StringParam.valueType, valueType)
                        .withParam(GqlParams.NumberParam.lower, lower)
                        .withParam(GqlParams.NumberParam.upper, upper)
                        .withParam(GqlParams.StringParam.value, String.valueOf(value))
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22007_22N12(String input) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22007)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N12)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.input, input)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22000_22N21(String crs) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N21)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.crs, crs)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22000_22N48(String runtime, String cause) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N48)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.runtime, runtime)
                        .withParam(GqlParams.StringParam.cause, cause)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22G03_22N01(String value, List<String> valueTypeList, String valueType) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22G03)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N01)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.value, value)
                        .withParam(GqlParams.ListParam.valueTypeList, valueTypeList)
                        .withParam(GqlParams.StringParam.valueType, valueType)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22G03_22N27(String input, String context, List<String> validTypes) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22G03)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N27)
                        .withParam(GqlParams.StringParam.input, input)
                        .withParam(GqlParams.StringParam.context, context)
                        .withParam(GqlParams.ListParam.valueTypeList, validTypes)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22G03_22N27(
            String input, String context, List<String> validTypes, int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22G03)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N27)
                        .withParam(GqlParams.StringParam.input, input)
                        .withParam(GqlParams.StringParam.context, context)
                        .withParam(GqlParams.ListParam.valueTypeList, validTypes)
                        .atPosition(offset, line, column)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22G03_22N31() {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22G03)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N31)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22G03_22N39(String value) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22G03)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N39)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.value, value)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22G05_22N15(String component, String temporal) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22G05)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N15)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.component, component)
                        .withParam(GqlParams.StringParam.temporal, temporal)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22007_22N25(String valueType, String temporal) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22007)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N25)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.valueType, valueType)
                        .withParam(GqlParams.StringParam.temporal, temporal)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22007_22N36(String input, String valueType) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22007)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N36)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.input, input)
                        .withParam(GqlParams.StringParam.valueType, valueType)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject get22015_22N28(String operation) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22015)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N28)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.operation, operation)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22N05_22N84(String input, String context, int upper) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N05)
                .withParam(GqlParams.StringParam.input, input)
                .withParam(GqlParams.StringParam.context, context)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N84)
                        .withParam(GqlParams.NumberParam.upper, upper)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22N38_22N01(
            String functionName, String value, List<String> valueTypeList, String valueType) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N38)
                .withParam(GqlParams.StringParam.value, functionName)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N01)
                        .withParam(GqlParams.StringParam.value, value)
                        .withParam(GqlParams.ListParam.valueTypeList, valueTypeList)
                        .withParam(GqlParams.StringParam.valueType, valueType)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22N38_22N03(
            String fun, String component, String valueType, Number lower, Number upper, Number value) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N38)
                .withParam(GqlParams.StringParam.value, GqlParams.StringParam.fun.process(fun))
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N03)
                        .withParam(GqlParams.StringParam.component, component)
                        .withParam(GqlParams.StringParam.valueType, valueType)
                        .withParam(GqlParams.NumberParam.lower, lower)
                        .withParam(GqlParams.NumberParam.upper, upper)
                        .withParam(GqlParams.StringParam.value, String.valueOf(value))
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22N38_22NB1(
            String function, List<String> expectedTypeList, String actualType) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N38)
                .withParam(GqlParams.StringParam.value, function)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NB1)
                        .withParam(GqlParams.ListParam.valueTypeList, expectedTypeList)
                        .withParam(GqlParams.StringParam.input, actualType)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject get22N69_52N02(String idxDescrOrName, String proc) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N69)
                .withParam(GqlParams.StringParam.idxDescrOrName, idxDescrOrName)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N02)
                        .withParam(GqlParams.StringParam.proc, proc)
                        .build())
                .build();
    }

    private static ErrorGqlStatusObject getGql22N77(
            String entityType, long entityId, String tokenType, String token, String[] propKeyList) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N77)
                .withParam(GqlParams.StringParam.entityType, entityType)
                .withParam(GqlParams.NumberParam.entityId, entityId)
                .withParam(GqlParams.StringParam.tokenType, tokenType)
                .withParam(GqlParams.StringParam.token, token)
                .withParam(GqlParams.ListParam.propKeyList, List.of(propKeyList))
                .build();
    }

    public static ErrorGqlStatusObject getGql22N77_nodes(long nodeId, String token, String[] propKeyList) {
        return getGql22N77("NODE", nodeId, "label", token, propKeyList);
    }

    public static ErrorGqlStatusObject getGql22N77_relationships(
            long relationshipId, String token, String[] propKeyList) {
        return getGql22N77("RELATIONSHIP", relationshipId, "type", token, propKeyList);
    }

    public static ErrorGqlStatusObject getGql22N81(String exprType, String context, int offset, int line, int column) {
        var builder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N81)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withParam(GqlParams.StringParam.exprType, exprType)
                .withParam(GqlParams.StringParam.context, context);
        if (offset >= 0) {
            builder.atPosition(offset, line, column);
        }
        return builder.build();
    }

    public static ErrorGqlStatusObject getGql22N81(String exprType, String context) {
        return getGql22N81(exprType, context, -1, -1, -1);
    }

    public static ErrorGqlStatusObject getGql22NA0_22NA4(String predicate) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NA0)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NA4)
                        .withParam(GqlParams.StringParam.pred, predicate)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22NA0_22NA5(String predicate) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NA0)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NA5)
                        .withParam(GqlParams.StringParam.pred, predicate)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22NA0_22NA6(String predicate) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NA0)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NA6)
                        .withParam(GqlParams.StringParam.pred, predicate)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql22NA0_22NB0(String predicate) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NA0)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NB0)
                        .withParam(GqlParams.StringParam.pred, predicate)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42N45(int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N45)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(offset, line, column)
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_22N04(
            String input, String context, List<?> validTypes, int offset, int line, int column) {
        ErrorGqlStatusObjectImplementation.Builder main = ErrorGqlStatusObjectImplementation.from(
                        GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column);
        ErrorGqlStatusObjectImplementation.Builder cause = ErrorGqlStatusObjectImplementation.from(
                        GqlStatusInfoCodes.STATUS_22N04)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(offset, line, column)
                .withParam(GqlParams.StringParam.input, input)
                .withParam(GqlParams.StringParam.context, context)
                .withParam(GqlParams.ListParam.inputList, validTypes);
        if (offset >= 0) {
            main.atPosition(offset, line, column);
            cause.atPosition(offset, line, column);
        }
        return main.withCause(cause.build()).build();
    }

    public static ErrorGqlStatusObject getGql42001_22N04(String input, String variable, List<?> validTypes) {
        return getGql42001_22N04(input, variable, validTypes, -1, -1, -1);
    }

    public static ErrorGqlStatusObject getGql42001_42I06(
            String input, List<String> valueList, int offset, int line, int column) {
        return getGql42001_42I06_withCause(input, valueList, offset, line, column, null);
    }

    public static ErrorGqlStatusObject getGql42001_42I06_withCause(
            String input, List<String> valueList, int offset, int line, int column, ErrorGqlStatusObject cause) {
        var gqlBuilder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I06)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(offset, line, column)
                .withParam(GqlParams.StringParam.input, input)
                .withParam(GqlParams.ListParam.valueList, valueList);
        var causeChain = cause != null ? gqlBuilder.withCause(cause).build() : gqlBuilder.build();
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(causeChain)
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42I20(
            String input, String labelExpr, int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I20)
                        .withParam(GqlParams.StringParam.input, input)
                        .withParam(GqlParams.StringParam.labelExpr, labelExpr)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .atPosition(offset, line, column)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42I23(String funcName, int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I23)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .atPosition(offset, line, column)
                        .withParam(GqlParams.StringParam.fun, funcName)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42I25(int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I25)
                        .atPosition(offset, line, column)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42I29(
            String input, String replacement, int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I29)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .atPosition(offset, line, column)
                        .withParam(GqlParams.StringParam.input, input)
                        .withParam(GqlParams.StringParam.replacement, replacement)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42I45(String action, int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I45)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .atPosition(offset, line, column)
                        .withParam(GqlParams.StringParam.action, action)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42I47(String msg, int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I47)
                        .withParam(GqlParams.StringParam.msg, msg)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .atPosition(offset, line, column)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42I48(int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I48)
                        .atPosition(offset, line, column)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N07(String variable, int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N07)
                        .atPosition(offset, line, column)
                        .withParam(GqlParams.StringParam.variable, variable)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N0A(String action, String db1, String db2) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N0A)
                        .withParam(GqlParams.StringParam.action, action)
                        .withParam(GqlParams.StringParam.db1, db1)
                        .withParam(GqlParams.StringParam.db2, db2)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N14(String clause, String cmd, int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N14)
                        .atPosition(offset, line, column)
                        .withParam(GqlParams.StringParam.clause, clause)
                        .withParam(GqlParams.StringParam.cmd, cmd)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N22(int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N22)
                        .atPosition(offset, line, column)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N24(
            String input1, String input2, int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N24)
                        .atPosition(offset, line, column)
                        .withParam(GqlParams.StringParam.input1, input1)
                        .withParam(GqlParams.StringParam.input2, input2)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N32(String keyword, int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N32)
                        .atPosition(offset, line, column)
                        .withParam(GqlParams.StringParam.keyword, keyword)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N39(int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N39)
                        .atPosition(offset, line, column)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N40(String funcName, int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N40)
                        .atPosition(offset, line, column)
                        .withParam(GqlParams.StringParam.fun, funcName)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N42(int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N42)
                        .atPosition(offset, line, column)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N45(int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .atPosition(offset, line, column)
                .withCause(getGql42N45(offset, line, column))
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N47(int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N47)
                        .atPosition(offset, line, column)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N53(int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N53)
                        .atPosition(offset, line, column)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N57(String expr, int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N57)
                        .atPosition(offset, line, column)
                        .withParam(GqlParams.StringParam.expr, expr)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42001_42N71(int offset, int line, int column) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .atPosition(offset, line, column)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N71)
                        .atPosition(offset, line, column)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42002_42N00(String db) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42002)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N00)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.db, db)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42002_42NA8_ifRelevant42N51_42N00(
            String command, String db, String paramName) {
        var invalidDbCause = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withParam(GqlParams.StringParam.db, db)
                .build();

        return getGql42002_42NA8_ifRelevant42N51_cause(command, invalidDbCause, paramName);
    }

    public static ErrorGqlStatusObject getGql42002_42N09(String user) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42002)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N09)
                        .withParam(GqlParams.StringParam.user, user)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42002_42N81(String param, List<?> paramList) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42002)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N81)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.param, param)
                        .withParam(GqlParams.ListParam.paramList, paramList)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql42002_42NA8_ifRelevant42N51_42N09(
            String command, String user, String paramName) {
        var invalidUserCause = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N09)
                .withParam(GqlParams.StringParam.user, user)
                .build();

        return getGql42002_42NA8_ifRelevant42N51_cause(command, invalidUserCause, paramName);
    }

    public static ErrorGqlStatusObject getGql42002_42NA8_ifRelevant42N51_42N10(
            String command, String role, String paramName) {
        var invalidRoleCause = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N10)
                .withParam(GqlParams.StringParam.role, role)
                .build();

        return getGql42002_42NA8_ifRelevant42N51_cause(command, invalidRoleCause, paramName);
    }

    private static ErrorGqlStatusObject getGql42002_42NA8_ifRelevant42N51_cause(
            String command, ErrorGqlStatusObject cause, String paramName) {
        var gqlBuilder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42002);
        var invalidCommandCause = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NA8)
                .withParam(GqlParams.StringParam.cmd, command);

        var invalidParamCause = paramName == null
                ? null
                : ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N51)
                        .withParam(GqlParams.StringParam.param, paramName)
                        .withCause(cause)
                        .build();

        return invalidParamCause != null
                ? gqlBuilder
                        .withCause(
                                invalidCommandCause.withCause(invalidParamCause).build())
                        .build()
                : gqlBuilder
                        .withCause(invalidCommandCause.withCause(cause).build())
                        .build();
    }

    public static ErrorGqlStatusObject getGql42N51(String parameter, ErrorGqlStatusObject cause) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N51)
                .withParam(GqlParams.StringParam.param, parameter)
                .withCause(cause)
                .build();
    }

    public static ErrorGqlStatusObject get50N00(String msgTitle, String msg) {
        return get50N00(msgTitle, msg, -1, -1, -1);
    }

    public static ErrorGqlStatusObject get50N00(String msgTitle, String msg, int offset, int line, int column) {
        var builder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withParam(GqlParams.StringParam.msgTitle, msgTitle)
                .withParam(GqlParams.StringParam.msg, msg);
        if (offset >= 0) builder.atPosition(offset, line, column);
        return builder.build();
    }

    public static ErrorGqlStatusObject get50N22(String database) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N22)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withParam(GqlParams.StringParam.db, database)
                .build();
    }

    public static ErrorGqlStatusObject get51N00() {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .build();
    }

    public static ErrorGqlStatusObject get51N00_50N00(String msgTitle, String msg) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N00)
                        .withParam(GqlParams.StringParam.msgTitle, msgTitle)
                        .withParam(GqlParams.StringParam.msg, msg)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql51N00_51N18(String procMethod) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N18)
                        .withClassification(ErrorClassification.CLIENT_ERROR)
                        .withParam(GqlParams.StringParam.procMethod, procMethod)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject get51N00_52N35(String procClass, String msg) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N35)
                        .withParam(GqlParams.StringParam.procClass, procClass)
                        .withParam(GqlParams.StringParam.msg, msg)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject get51N00_53N35(String funClass, String msg) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N00)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_53N35)
                        .withParam(GqlParams.StringParam.funClass, funClass)
                        .withParam(GqlParams.StringParam.msg, msg)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject getGql52N02_52N11(String procedure) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N02)
                .withParam(GqlParams.StringParam.proc, procedure)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N11)
                        .build())
                .build();
    }

    public static ErrorGqlStatusObject get52N34(String proc) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N34)
                .withClassification(ErrorClassification.CLIENT_ERROR)
                .withParam(GqlParams.StringParam.proc, proc)
                .build();
    }

    public static ErrorGqlStatusObject get52N37(String procedureName, ErrorGqlStatusObject cause) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N37)
                .withParam(GqlParams.StringParam.proc, procedureName)
                .withCause(cause)
                .build();
    }

    public static ErrorGqlStatusObject get52U00(String procedureName, Throwable e) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52U00)
                .withParam(GqlParams.StringParam.proc, procedureName)
                .withParam(GqlParams.StringParam.msgTitle, e.getClass().getName())
                .withParam(GqlParams.StringParam.msg, e.getMessage())
                .build();
    }

    public static ErrorGqlStatusObject get53N34(String funcName) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_53N34)
                .withParam(GqlParams.StringParam.fun, funcName)
                .build();
    }

    public static ErrorGqlStatusObject get53N37(String funcName, ErrorGqlStatusObject cause) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_53N37)
                .withParam(GqlParams.StringParam.fun, funcName)
                .withCause(cause)
                .build();
    }

    public static ErrorGqlStatusObject get53U00(String funcName, Throwable e) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_53U00)
                .withParam(GqlParams.StringParam.fun, funcName)
                .withParam(GqlParams.StringParam.msgTitle, e.getClass().getName())
                .withParam(GqlParams.StringParam.msg, e.getMessage())
                .build();
    }

    /**
     * Append the exception cause as the bottom GQL cause of the inner ErrorGqlStatusObject if the following applies
     * - the exception cause is an ErrorGqlStatusObject (and not e.g. a generic Java exception)
     * - the inner ErrorGqlStatusObject is of type ErrorGqlStatusObjectImplementation
     * (this should always be true, but is needed for casting)
     *
     * @param gqlStatusObject The current inner ErrorGqlStatusObject
     * @param cause The exception cause
     * @return The replaced inner ErrorGqlStatusObject
     */
    public static ErrorGqlStatusObject getInnerGqlStatusObject(ErrorGqlStatusObject gqlStatusObject, Throwable cause) {
        if (cause instanceof ErrorGqlStatusObject gqlStatusObjectCause) {
            // There are exceptions which are wrappers on top of other exceptions for being compliant with checked
            // exceptions and re-thrown.
            // On those scenarios, the original gqlStatusObject is copied to the new exception and
            // the wrapped exception is added as a cause.
            // So, we don't need to put this gqlStatusObject on the chain since it is already the same object.
            if (gqlStatusObject == gqlStatusObjectCause) {
                return gqlStatusObject;
            }
            return getErrorObjectWithRewrittenCause(gqlStatusObject, gqlStatusObjectCause);
        } else {
            return gqlStatusObject;
        }
    }

    /**
     * Generate a message based on the message of the ErrorGqlStatusObject and appending the messages from the causes.
     *
     * @param gqlStatusObject The ErrorGqlStatusObject
     * @return The message
     */
    public static String getCompleteMessage(ErrorGqlStatusObject gqlStatusObject) {
        if (gqlStatusObject.cause().isPresent()) {
            return gqlStatusObject.getMessage()
                    + System.lineSeparator()
                    + getCompleteMessage(gqlStatusObject.cause().get());
        } else {
            return gqlStatusObject.getMessage();
        }
    }

    private static ErrorGqlStatusObject getErrorObjectWithRewrittenCause(
            ErrorGqlStatusObject gqlStatusObject, ErrorGqlStatusObject exceptionCause) {
        // This should always be true, but needed for casting
        if (gqlStatusObject.gqlStatusObject() instanceof ErrorGqlStatusObjectImplementation gsoImplementation) {
            ErrorGqlStatusObject newCause;
            if (gqlStatusObject.cause().isPresent()) {
                var currentCause = gqlStatusObject.cause().get();
                if (currentCause.equals(exceptionCause)) {
                    return gqlStatusObject;
                }
                newCause = getErrorObjectWithRewrittenCause(currentCause, exceptionCause);
            } else {
                // Bottom of the current cause chain => add the Java cause as a GQL cause here
                if (exceptionCause.gqlStatusObject() != null) {
                    /*
                     * The Java cause is an exception implementing ErrorGqlStatusObject
                     * and is having an inner error object
                     * => add the inner error object as GQL cause
                     */
                    newCause = exceptionCause.gqlStatusObject();
                } else {
                    /*
                     * The Java cause is an exception implementing ErrorGqlStatusObject
                     * but is not is having an inner error object
                     * => the cause was not ported to the new framework yet
                     * => add the default error object as GQL cause
                     */
                    newCause = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N42)
                            .build();
                }
            }
            gsoImplementation.setCause(newCause);
            return gsoImplementation;
        }
        return gqlStatusObject;
    }
}
