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
import java.util.stream.StreamSupport;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.kernel.api.exceptions.Status;

public class ParameterNotFoundException extends Neo4jException {

    private ParameterNotFoundException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    public static ParameterNotFoundException expectedParam(String expectedParam, Iterable<String> gotParams) {
        var gql = GqlHelper.getGql42001_42N81(
                List.of(expectedParam),
                StreamSupport.stream(gotParams.spliterator(), false).toList());
        return new ParameterNotFoundException(gql, String.format("Expected parameter(s): %s", expectedParam));
    }

    public static ParameterNotFoundException expectedParamList(
            String expectedParamsString, List<String> expectedParams, Iterable<String> gotParams) {
        var gql = GqlHelper.getGql42001_42N81(
                expectedParams,
                StreamSupport.stream(gotParams.spliterator(), false).toList());
        return new ParameterNotFoundException(gql, String.format("Expected parameter(s): %s", expectedParamsString));
    }

    public static ParameterNotFoundException expectedParamNamed(String expectedParam, Iterable<String> gotParams) {
        var gql = GqlHelper.getGql42001_42N81(
                List.of(expectedParam),
                StreamSupport.stream(gotParams.spliterator(), false).toList());
        return new ParameterNotFoundException(gql, String.format("Expected a parameter named %s", expectedParam));
    }

    @Override
    public Status status() {
        return Status.Statement.ParameterMissing;
    }
}
