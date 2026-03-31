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
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.gqlstatus.GqlRuntimeException;

public class DataAfterQuoteException extends GqlRuntimeException implements ObfuscatableException {
    private static final String messageTemplate =
            "Characters after an ending quote in a CSV field are not supported. See '%s' at position %s. This is read as `%s`.";
    private final SourceTraceability source;
    private final String sourceDescription;
    private final long position;
    private final String readValue;

    public DataAfterQuoteException(SourceTraceability source, String readValue) {
        super(
                GqlHelper.get22NAC(source.sourceDescription(), source.position(), readValue),
                messageTemplate.formatted(source.sourceDescription(), source.position(), readValue));
        this.source = source;
        this.sourceDescription = source.sourceDescription();
        this.position = source.position();
        this.readValue = readValue;
    }

    public SourceTraceability source() {
        return source;
    }

    @Override
    public String getMessage() {
        return messageTemplate.formatted(sourceDescription, position, readValue);
    }

    @Override
    public String obfuscatedMessage(String obfuscatedValue) {
        return messageTemplate.formatted(obfuscatedValue, position, obfuscatedValue);
    }
}
