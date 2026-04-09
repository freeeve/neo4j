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
package org.neo4j.internal.batchimport.input;

import static org.neo4j.util.Preconditions.checkState;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.primitive.ObjectIntMaps;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.batchimport.api.input.IdType;
import org.neo4j.batchimport.api.input.ReadableGroups;
import org.neo4j.csv.reader.VectorExtractor;

/**
 * Mapping from name to {@link Group}. Assigns proper {@link Group#id() ids} to created groups.
 */
public class Groups implements ReadableGroups {
    private final Map<Group, IdTypeIndexing> groupIdTypeIndexing = Maps.mutable.empty();
    private final Map<String, Group> byName = new HashMap<>();
    private final List<Group> byId = new ArrayList<>();
    private int nextId = 0;

    public Groups() {}

    /**
     * @param name group name or {@code null} for the "global" group.
     * otherwise if {@code null} the globally defined {@link IdType} is used.
     * @return {@link Group} for the given name. If the group doesn't already exist it will be created
     * with a new id. If {@code name} is {@code null} then the "global" group is returned.
     * This method also prevents mixing global and non-global groups, i.e. if first call is {@code null},
     * then consecutive calls have to specify {@code null} name as well. The same holds true for non-null values.
     */
    public synchronized Group getOrCreate(String name) {
        Group group = byName.get(name);
        if (group == null) {
            byName.put(name, group = new Group(nextId++, name));
            byId.add(group);
        }
        return group;
    }

    public synchronized String getSpecificIdType(String groupName, int idTypeIndex) {
        return internalGetSpecificIdType(internalGet(groupName), idTypeIndex);
    }

    public synchronized String getSpecificIdType(Group group, int idTypeIndex) {
        return internalGetSpecificIdType(group, idTypeIndex);
    }

    public synchronized String bindIdType(Group group, String column, String specificIdType) throws HeaderException {
        if (specificIdType != null && VectorExtractor.COL_NAME.equals(specificIdType.toUpperCase(Locale.ROOT))) {
            throw new HeaderException("vector is not allowed as an id-type");
        }

        IdTypeIndexing indexing = groupIdTypeIndexing.computeIfAbsent(group, k -> new IdTypeIndexing());
        String boundType = indexing.bind(column, specificIdType);
        checkState(
                Objects.equals(specificIdType, boundType),
                "Group '%s' has a different specific type for column '%s'."
                        + " Was created with '%s' and later used with '%s'",
                group.name(),
                column,
                boundType,
                specificIdType);
        return boundType;
    }

    @Override
    public synchronized Group get(String name) {
        return internalGet(name);
    }

    @Override
    public Group get(int id) {
        if (id < 0 || id >= byId.size()) {
            throw new HeaderException("Group with id " + id + " not found");
        }
        return byId.get(id);
    }

    private String groupNames() {
        return Arrays.toString(byName.keySet().toArray(new String[0]));
    }

    @Override
    public int size() {
        return nextId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Groups groups)) {
            return false;
        }
        return nextId == groups.nextId && Objects.equals(byName, groups.byName) && Objects.equals(byId, groups.byId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byName, byId, nextId);
    }

    private Group internalGet(String name) {
        Group group = byName.get(name);
        if (group == null) {
            throw new HeaderException("Group '" + name + "' not found. Available groups are: " + groupNames());
        }
        return group;
    }

    private String internalGetSpecificIdType(Group group, int idTypeIndex) {
        IdTypeIndexing indexing = groupIdTypeIndexing.get(group);
        return (indexing == null) ? null : indexing.get(idTypeIndex);
    }

    private static class IdTypeIndexing implements Serializable {

        private final MutableObjectIntMap<String> idTypeTracking = ObjectIntMaps.mutable.empty();

        private final List<String> idTypes = Lists.mutable.empty();

        private String bind(String column, String specificIdType) {
            if (idTypeTracking.containsKey(column)) {
                return idTypes.get(idTypeTracking.get(column));
            } else {
                var nextIndex = idTypes.size();
                idTypes.add(specificIdType);
                idTypeTracking.put(column, nextIndex);
                return specificIdType;
            }
        }

        private String get(int ix) {
            return (ix < idTypes.size()) ? idTypes.get(ix) : null;
        }
    }
}
