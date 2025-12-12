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
package org.neo4j.cypher.jdbc.mapping.id;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.neo4j.values.ElementIdMapper;

/**
 * Assign stable (within a single transaction) global ids based on external virtual nodes and relationships.
 * Implementations needs to be thread safe.
 */
public interface GlobalIds {
    /**
     * Returns the global node id for the specified external id.
     *
     * @param schemaId identifies the schema which this node is part of (roughly the labels)
     * @param externalId external identifier of the node
     */
    long nodeId(String schemaId, Object externalId);

    /**
     * Returns the global relationship id for the specified external id.
     *
     * @param schemaId identifies the schema which this relationship is part of (roughly the type)
     * @param externalId external identifier of the relationship
     */
    long relationshipId(String schemaId, Object externalId);

    ElementIdMapper idMapper();

    static GlobalIds create(ElementIdMapper elementIdMapper) {
        return new CachingGlobalIds(elementIdMapper);
    }
}

// Note, this implementation can be optimised for speed and/or size.
final class CachingGlobalIds implements GlobalIds {
    private final ElementIdMapper elementIdMapper;
    private final AtomicLong nodeCounter = new AtomicLong(0);
    private final AtomicLong relationshipCounter = new AtomicLong(0);
    private final Map<Key, Long> assignedNodeIds = new ConcurrentHashMap<>();
    private final Map<Key, Long> assignedRelationshipIds = new ConcurrentHashMap<>();

    CachingGlobalIds(ElementIdMapper elementIdMapper) {
        this.elementIdMapper = elementIdMapper;
    }

    @Override
    public long nodeId(String schemaId, Object externalId) {
        return assignedNodeIds.computeIfAbsent(new Key(schemaId, externalId), key -> nodeCounter.decrementAndGet());
    }

    @Override
    public long relationshipId(String schemaId, Object externalId) {
        return assignedRelationshipIds.computeIfAbsent(
                new Key(schemaId, externalId), key -> relationshipCounter.decrementAndGet());
    }

    @Override
    public ElementIdMapper idMapper() {
        return elementIdMapper;
    }

    private record Key(String schemaId, Object externalId) {}
}
