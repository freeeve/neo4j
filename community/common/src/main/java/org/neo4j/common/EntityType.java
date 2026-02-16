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
package org.neo4j.common;

/**
 * Type of graph entity. The two types, Nodes and Relationships, represent objects that can have properties
 * associated with them, as well as labeled with additional type information. Nodes have labels, and relationships
 * have relationship types.
 * <p>
 * NOTE: do not changing the id when modifying this enum.
 */
public enum EntityType {
    NODE((byte) 0),
    RELATIONSHIP((byte) 1);

    public static final EntityType[] ENTITY_TYPES = EntityType.values();
    private final byte id;

    EntityType(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }

    public static EntityType of(byte id) {
        for (EntityType type : ENTITY_TYPES) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("No such entity type: " + id);
    }
}
