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
package org.neo4j.cloud.storage;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public record StorageTag(String key, String value) {
    public static final String STORAGE_TAGS = "storage_tags";

    public static StorageTag parse(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Invalid tag. Tag must not be empty or null!");
        }

        try {
            var inputSplit = input.split(":");
            if (inputSplit.length != 2) {
                throw new IllegalArgumentException(
                        String.format("Invalid tag. Tag must be in the format '<key>:<value>'! Provided: '%s'", input));
            }
            return new StorageTag(inputSplit[0], inputSplit[1]);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid tag. Tag must be in the format '<key>:<value>'! Provided: '%s'", input));
        }
    }

    public static void setTags(Path path, Collection<StorageTag> tags) {
        if (!tags.isEmpty()) {
            if (path instanceof StoragePath storagePath) {
                storagePath.addMetadata(STORAGE_TAGS, tags);
            } else {
                throw new IllegalArgumentException("Cannot set tags on path which doesn't point to Object Storage");
            }
        }
    }

    public static Collection<StorageTag> getTags(StoragePath path) {
        //noinspection unchecked
        return (Collection<StorageTag>) path.metadata().getOrDefault(StorageTag.STORAGE_TAGS, List.<StorageTag>of());
    }

    public static Map<String, String> asMap(Collection<StorageTag> tags) {
        return tags.stream().collect(Collectors.toMap(StorageTag::key, StorageTag::value));
    }
}
