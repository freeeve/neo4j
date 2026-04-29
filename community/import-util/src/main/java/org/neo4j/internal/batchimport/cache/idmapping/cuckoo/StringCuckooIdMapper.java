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
package org.neo4j.internal.batchimport.cache.idmapping.cuckoo;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.neo4j.batchimport.api.PropertyValueLookup;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.batchimport.api.input.ReadableGroups;
import org.neo4j.hashing.RapidHash;
import org.neo4j.internal.batchimport.cache.NumberArrayFactory;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.util.VisibleForTesting;

/**
 * A version of the cuckoo mapper that handles {@link String}s.
 * <p>
 * Strings are hashed and stored in the cuckoo table, in the unlikely event of a hash collision,
 * the collisions are stored separately in a simple map and queried first before going to the
 * cuckoo table.
 */
public class StringCuckooIdMapper extends CuckooIdMapper {
    private static final StringHash STRING_HASH = s -> {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        return RapidHash.create().hash(bytes, 0, bytes.length);
    };

    private final StringHash stringHash;
    private final HashMap<IdGroup, Long> collisions = new HashMap<>();
    private final PropertyValueLookup stringArray;

    public StringCuckooIdMapper(
            long estimatedNumberOfNodes,
            NumberArrayFactory arrayFactory,
            ReadableGroups groups,
            MemoryTracker memoryTracker,
            PropertyValueLookup stringArray) {
        this(estimatedNumberOfNodes, arrayFactory, groups, memoryTracker, STRING_HASH, stringArray);
    }

    @VisibleForTesting
    StringCuckooIdMapper(
            long estimatedNumberOfNodes,
            NumberArrayFactory arrayFactory,
            ReadableGroups groups,
            MemoryTracker memoryTracker,
            StringHash stringHash,
            PropertyValueLookup stringArray) {
        super(estimatedNumberOfNodes, arrayFactory, groups, memoryTracker);
        this.stringHash = stringHash;
        this.stringArray = stringArray;
    }

    @Override
    public Setter newSetter(int workerId) {
        return (objectInputId, actualId, group) -> {
            String inputId = objectInputId.toString();

            try {
                cuckooTable.insert(getKey(inputId, group), actualId);
            } catch (KeyCollisionException e) {
                if (isHashCollision(inputId, actualId, group)) {
                    return;
                }
                throw e;
            }
        };
    }

    @Override
    public Getter newGetter(int workerId) {
        return new Getter() {
            @Override
            public long get(Object objectInputId, Group group) {
                String inputId = objectInputId.toString();

                if (!collisions.isEmpty()) {
                    Long collidedActualId = collisions.get(new IdGroup(inputId, group));
                    if (collidedActualId != null) {
                        return collidedActualId;
                    }
                }

                return cuckooTable.get(getKey(inputId, group));
            }

            @Override
            public void close() {}
        };
    }

    private boolean isHashCollision(String inputId, long actualId, Group group) {
        long existingActualId = cuckooTable.get(getKey(inputId, group));
        try (PropertyValueLookup.Lookup lookup = stringArray.newLookup(false)) {
            String existingActualString =
                    String.valueOf(lookup.lookupProperty(existingActualId, EmptyMemoryTracker.INSTANCE));
            if (!inputId.equals(existingActualString)) {
                // Given a good hash function this should be very rare
                synchronized (collisions) {
                    collisions.put(new IdGroup(inputId, group), actualId);
                }
                return true;
            }
            return false;
        }
    }

    private long getKey(String inputId, Group group) {
        return (stringHash.hash(inputId) << groupShift) | group.id();
    }

    private record IdGroup(String id, Group group) {}
}
