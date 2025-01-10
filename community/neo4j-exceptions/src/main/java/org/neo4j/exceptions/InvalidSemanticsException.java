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

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;

public class InvalidSemanticsException extends Neo4jException {
    @Deprecated
    public InvalidSemanticsException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidSemanticsException(ErrorGqlStatusObject gqlStatusObject, String message, Throwable cause) {
        super(gqlStatusObject, message, cause);
    }

    @Deprecated
    public InvalidSemanticsException(String message) {
        super(message);
    }

    public InvalidSemanticsException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    public static InvalidSemanticsException invalidCombinationOfProfileAndExplain() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22000)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N52)
                        .build())
                .build();
        return new InvalidSemanticsException(gql, "Can't mix PROFILE and EXPLAIN");
    }

    public static InvalidSemanticsException unsupportedAccessOfCompositeDatabase(
            String accessedGraph, String sessionGraph) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42002)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N04)
                        .withParam(GqlParams.StringParam.db1, accessedGraph)
                        .withParam(GqlParams.StringParam.db2, sessionGraph)
                        .withParam(GqlParams.StringParam.db3, accessedGraph)
                        .build())
                .build();

        var legacyMessage = "Accessing a composite database and its constituents is only allowed when connected to it. "
                + "Attempted to access '%s' while connected to '%s'".formatted(accessedGraph, sessionGraph);

        return new InvalidSemanticsException(gql, legacyMessage);
    }

    public static InvalidSemanticsException unsupportedRequestOnSystemDatabase(
            String invalidInput, String legacyMessage, boolean addSystemProcedureCause) {
        var systemProcCause = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NA9)
                .build();
        var disallowedOnSystemCauseBase = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N17)
                .withParam(GqlParams.StringParam.input, invalidInput);
        var disallowedOnSystemCause = addSystemProcedureCause
                ? disallowedOnSystemCauseBase.withCause(systemProcCause)
                : disallowedOnSystemCauseBase;

        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(disallowedOnSystemCause.build())
                .build();
        return new InvalidSemanticsException(gql, legacyMessage);
    }

    public static InvalidSemanticsException invalidRegex(String errorMsg, String regex) {
        var gql = GqlHelper.getGql22000_22N11(regex);
        return new InvalidSemanticsException(gql, "Invalid Regex: " + errorMsg, null);
    }

    public static InvalidSemanticsException accessingMultipleGraphsOnlySupportedOnCompositeDatabases(
            String legacyMessage) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NA5)
                        .build())
                .build();
        return new InvalidSemanticsException(gql, legacyMessage);
    }

    public static InvalidSemanticsException cannotMergeNodeNullProperty(String key, String labelString) {
        var gql = GqlHelper.getGql22G03_22N31();
        return new InvalidSemanticsException(
                gql,
                String.format(
                        "Cannot merge the following node because of null property value for '%s': (%s {%s: null})",
                        key, labelString, key));
    }

    public static InvalidSemanticsException cannotMergeNodeNaNProperty(String key, String labelsString) {
        var gql = GqlHelper.getGql22G03_22N31();
        return new InvalidSemanticsException(
                gql,
                String.format(
                        "Cannot merge the following node because of NaN property value for '%s': (%s {%s: NaN})",
                        key, labelsString, key));
    }

    public static InvalidSemanticsException cannotMergeRelPropertyValue(
            String value, String key, String startVarPart, String stringifiedRelType, String endVarPart) {
        var gql = GqlHelper.getGql22G03_22N31();
        return new InvalidSemanticsException(
                gql,
                String.format(
                        "Cannot merge the following relationship because of %s property value for '%s': (%s)-[:%s {%s: %s}]->(%s)",
                        value, key, startVarPart, stringifiedRelType, key, value, endVarPart));
    }

    public static InvalidSemanticsException invalidShardTarget(String action, String db1, String db2) {
        var gql = GqlHelper.getGql42001_42N0A(action, db1, db2);
        return new InvalidSemanticsException(
                gql,
                String.format(
                        "%s is not allowed with a shard target. Connect to `%s` in order to read or write data from `%s`.",
                        action, db1, db2));
    }

    @Override
    public Status status() {
        return Status.Statement.SemanticError;
    }
}
