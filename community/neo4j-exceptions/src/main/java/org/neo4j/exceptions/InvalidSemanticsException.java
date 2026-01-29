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

import java.util.List;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;

public class InvalidSemanticsException extends Neo4jException {

    private InvalidSemanticsException(ErrorGqlStatusObject gqlStatusObject, String message, Throwable cause) {
        super(gqlStatusObject, message, cause);
    }

    private InvalidSemanticsException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    public static InvalidSemanticsException internalError(String msgTitle, String message) {
        var gql = GqlHelper.get50N00(msgTitle, message);
        return new InvalidSemanticsException(gql, message);
    }

    public static InvalidArgumentException compositeUnsupportedInCommunity() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N27)
                .withParam(GqlParams.StringParam.feat, "Composite database")
                .withParam(GqlParams.StringParam.edition, "community edition")
                .build();
        return new InvalidArgumentException(gql, "Composite database is not supported in Community Edition");
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
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
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
        var pattern = String.format("(%s {%s: null})", labelString, key);
        var gql = GqlHelper.getGql22G03_22N31("node", key, pattern, "null");
        return new InvalidSemanticsException(
                gql,
                String.format(
                        "Cannot merge the following node because of null property value for '%s': (%s {%s: null})",
                        key, labelString, key));
    }

    public static InvalidSemanticsException cannotMergeNodeNaNProperty(String key, String labelsString) {
        var pattern = String.format("(%s {%s: NaN})", labelsString, key);
        var gql = GqlHelper.getGql22G03_22N31("node", key, pattern, "NaN");
        return new InvalidSemanticsException(
                gql,
                String.format(
                        "Cannot merge the following node because of NaN property value for '%s': (%s {%s: NaN})",
                        key, labelsString, key));
    }

    public static InvalidSemanticsException cannotMergeRelPropertyValue(
            String value, String key, String startVarPart, String stringifiedRelType, String endVarPart) {
        var pattern =
                String.format("(%s)-[:%s {%s: %s}]->(%s)", startVarPart, stringifiedRelType, key, value, endVarPart);
        var gql = GqlHelper.getGql22G03_22N31("relationship", key, pattern, value);
        return new InvalidSemanticsException(
                gql,
                String.format(
                        "Cannot merge the following relationship because of %s property value for '%s': (%s)-[:%s {%s: %s}]->(%s)",
                        value, key, startVarPart, stringifiedRelType, key, value, endVarPart));
    }

    public static InvalidSemanticsException invalidShardTarget(String action, String db1, String db2) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N0A)
                        .withParam(GqlParams.StringParam.action, action)
                        .withParam(GqlParams.StringParam.db1, db1)
                        .withParam(GqlParams.StringParam.db2, db2)
                        .build())
                .build();
        return new InvalidSemanticsException(
                gql,
                String.format(
                        "%s is not allowed with a shard target. Target the sharded database `%s` instead of `%s`.",
                        action, db1, db2));
    }

    public static InvalidSemanticsException invalidShardedTarget(String db1, String db2) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N0B)
                        .withParam(GqlParams.StringParam.db1, db1)
                        .withParam(GqlParams.StringParam.db2, db2)
                        .build())
                .build();
        return new InvalidSemanticsException(
                gql,
                String.format(
                        "The database identified by `%s` is sharded. Drop the database `%s` before recreating.",
                        db1, db2));
    }

    public static InvalidSemanticsException invalidAlterStandardTarget(String action) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N0C)
                        .withParam(GqlParams.StringParam.action, action)
                        .withParam(GqlParams.StringParam.typeDescription, "standard database")
                        .build())
                .build();
        return new InvalidSemanticsException(
                gql, String.format("%s is not allowed with a standard database target", action));
    }

    public static InvalidSemanticsException invalidAlterShardedTarget(String action) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N0C)
                        .withParam(GqlParams.StringParam.action, action)
                        .withParam(GqlParams.StringParam.typeDescription, "sharded database")
                        .build())
                .build();
        return new InvalidSemanticsException(
                gql, String.format("%s is not allowed with a sharded database target", action));
    }

    public static InvalidSemanticsException invalidAlterGraphShardTarget(String action) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N0C)
                        .withParam(GqlParams.StringParam.action, action)
                        .withParam(GqlParams.StringParam.typeDescription, "graph shard")
                        .build())
                .build();
        return new InvalidSemanticsException(gql, String.format("%s is not allowed with a graph shard target", action));
    }

    public static InvalidSemanticsException invalidAlterShardTarget(String action) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N0C)
                        .withParam(GqlParams.StringParam.action, action)
                        .withParam(GqlParams.StringParam.typeDescription, "property shard")
                        .build())
                .build();
        return new InvalidSemanticsException(
                gql, String.format("%s is not allowed with a property shard target", action));
    }

    public static InvalidSemanticsException profileNotSupportedOnComposite() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N06)
                        .withParam(GqlParams.StringParam.action, "PROFILE")
                        .build())
                .build();
        return new InvalidSemanticsException(gql, "'PROFILE' is not supported on composite databases.");
    }

    public static InvalidSemanticsException routingNotSupportedInEmbedded() {
        return new InvalidSemanticsException(
                ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N05)
                        .build(),
                "Query routing is not available in embedded sessions. Try running the query using a Neo4j driver or the HTTP API.");
    }

    public static InvalidSemanticsException expectedStaticGraphSelection() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N72)
                        .build())
                .build();
        return new InvalidSemanticsException(gql, "Expected static graph selection");
    }

    public static InvalidSemanticsException unsupportedAccessOfStandardDb(String graph, String composite) {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N05)
                        .withParam(GqlParams.StringParam.db1, graph)
                        .withParam(GqlParams.StringParam.db2, composite)
                        .withParam(GqlParams.StringParam.db3, graph)
                        .build())
                .build();

        return new InvalidSemanticsException(
                gql,
                String.format(
                        "When connected to a composite database, access is allowed only to its constituents. "
                                + "Attempted to access '%s' while connected to '%s'",
                        graph, composite));
    }

    public static InvalidSemanticsException missingTransactionId() {
        var gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N06)
                .withParam(GqlParams.ListParam.inputList, List.of("transaction id"))
                .build();

        return new InvalidSemanticsException(
                gql, "Missing transaction id to terminate, the transaction id can be found using `SHOW TRANSACTIONS`.");
    }

    @Override
    public Status status() {
        return Status.Statement.SemanticError;
    }
}
