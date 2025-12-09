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
package org.neo4j.internal.indexcommand;

import java.util.Optional;
import java.util.function.LongFunction;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.storageengine.api.EagerValueIndexEntryUpdate;
import org.neo4j.storageengine.api.IndexEntryUpdate;
import org.neo4j.storageengine.api.TokenIndexEntryUpdate;

public class IndexCommandConversion {

    public static Optional<IndexEntryUpdate> convertCommandToIndexEntryUpdate(
            IndexUpdateCommand<?> command,
            LongFunction<IndexDescriptor> indexSupplier,
            IndexCommandSelector commandSelector) {
        var index = indexSupplier.apply(command.getIndexId());
        if (index == null) {
            return Optional.empty();
        }
        return switch (command) {
            case TokenIndexUpdateCommand token -> Optional.of(convertTokenCommand(token, index, commandSelector));
            case ValueIndexUpdateCommand value -> Optional.of(convertValueCommand(value, index, commandSelector));
        };
    }

    private static TokenIndexEntryUpdate convertTokenCommand(
            TokenIndexUpdateCommand tokenCommand, IndexDescriptor index, IndexCommandSelector commandSelector) {
        return TokenIndexEntryUpdate.tokenChange(
                tokenCommand.getEntityId(),
                index,
                commandSelector.getBefore(tokenCommand),
                commandSelector.getAfter(tokenCommand));
    }

    private static IndexEntryUpdate convertValueCommand(
            ValueIndexUpdateCommand valueCommand, IndexDescriptor index, IndexCommandSelector commandSelector) {
        return switch (commandSelector.mode(valueCommand)) {
            case ADDED -> EagerValueIndexEntryUpdate.add(valueCommand.getEntityId(), index, valueCommand.getAfter());
            case CHANGED ->
                EagerValueIndexEntryUpdate.change(
                        valueCommand.getEntityId(),
                        index,
                        commandSelector.getBefore(valueCommand),
                        commandSelector.getAfter(valueCommand));
            case REMOVED ->
                EagerValueIndexEntryUpdate.remove(valueCommand.getEntityId(), index, valueCommand.getAfter());
        };
    }
}
