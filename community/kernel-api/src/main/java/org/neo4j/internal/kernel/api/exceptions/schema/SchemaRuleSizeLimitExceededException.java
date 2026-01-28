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
package org.neo4j.internal.kernel.api.exceptions.schema;

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlRuntimeException;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.values.AnyValueWriter.EntityMode;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;
import org.neo4j.values.utils.PrettyPrinter;

public final class SchemaRuleSizeLimitExceededException extends GqlRuntimeException implements Status.HasStatus {

    private SchemaRuleSizeLimitExceededException(
            ErrorGqlStatusObject errorGqlStatusObject, String property, String input, int size, int maxSize) {
        super(errorGqlStatusObject, message(property, input, size, maxSize));
    }

    public static SchemaRuleSizeLimitExceededException schemaRuleEntryTooLarge(
            String property, Object input, int size, int maxSize) {
        final String inputString = inputToString(input);
        final ErrorGqlStatusObject gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I76)
                .withParam(GqlParams.StringParam.item, property)
                .withParam(GqlParams.StringParam.input, inputString)
                .withParam(GqlParams.NumberParam.bytes1, size)
                .withParam(GqlParams.NumberParam.bytes2, maxSize)
                .build();

        return new SchemaRuleSizeLimitExceededException(gql, property, inputString, size, maxSize);
    }

    private static String inputToString(Object input) {
        final Value inputValue = input instanceof final Value value ? value : Values.unsafeOf(input, true);
        return inputValue != null ? inputToString(inputValue) : String.valueOf(input);
    }

    private static String inputToString(Value value) {
        final PrettyPrinter pp = new PrettyPrinter("", EntityMode.REFERENCE, 100);
        value.writeTo(pp);
        return pp.value();
    }

    private static String message(String entryName, String input, int size, int maxSize) {
        return "The provided index or constraint %s '%s' (%d bytes) exceeded limit of %d bytes."
                .formatted(entryName, input, size, maxSize);
    }

    @Override
    public Status status() {
        return Status.Schema.SchemaRuleEntrySizeLimitError;
    }
}
