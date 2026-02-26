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
package org.neo4j.csv.reader;

import org.neo4j.exceptions.ObfuscatableException;
import org.neo4j.gqlstatus.Condition;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlRuntimeException;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.gqlstatus.ObfuscatableErrorGqlStatusObject;

public class IllegalMultilineFieldException extends GqlRuntimeException
        implements ObfuscatableException, ObfuscatableErrorGqlStatusObject {
    private static final String messageTemplate =
            "Multi-line fields are illegal in this context. Verify that there is not a missing end quote in '%s' at position %s.";
    private final SourceTraceability source;
    private final String sourceDescription;
    private final long position;

    public IllegalMultilineFieldException(SourceTraceability source) {
        super(
                GqlHelper.get22NAE(source.sourceDescription(), source.position()),
                messageTemplate.formatted(source.sourceDescription(), source.position()));
        this.source = source;
        this.sourceDescription = source.sourceDescription();
        this.position = source.position();
    }

    public SourceTraceability source() {
        return source;
    }

    @Override
    public ErrorGqlStatusObject gqlStatusObject() {
        return super.gqlStatusObject();
    }

    @Override
    public String getMessage() {
        return messageTemplate.formatted(sourceDescription, position);
    }

    @Override
    public String obfuscatedMessage(String obfuscatedValue) {
        return messageTemplate.formatted(obfuscatedValue, position);
    }

    @Override
    public String obfuscatedStatusDescription() {
        return String.format(
                "%s. %s",
                Condition.createStandardDescription(
                        GqlStatusInfoCodes.STATUS_22NAE.getCondition(),
                        GqlStatusInfoCodes.STATUS_22NAE.getSubCondition()),
                obfuscatedMessage("******"));
    }
}
