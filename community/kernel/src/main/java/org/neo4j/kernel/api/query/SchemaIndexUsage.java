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
package org.neo4j.kernel.api.query;

import java.util.HashMap;
import java.util.Map;
import org.neo4j.util.Preconditions;

public class SchemaIndexUsage extends IndexUsage {
    private final String[] labels;
    private final String[] propertyKeys;
    private final int[] propertyKeyIds;
    private final int[] labelIds;

    public SchemaIndexUsage(
            String identifier, int[] labelIds, String[] labels, int[] propertyKeyIds, String[] propertyKeys) {
        super(identifier);
        Preconditions.checkArgument(labelIds.length == labels.length, "label names and ids must match");
        this.labels = labels;
        this.labelIds = labelIds;
        this.propertyKeys = propertyKeys;
        this.propertyKeyIds = propertyKeyIds;
    }

    public int[] getLabelIds() {
        return labelIds;
    }

    public int[] getPropertyKeyIds() {
        return propertyKeyIds;
    }

    @Override
    public Map<String, String> asMap() {
        Map<String, String> map = new HashMap<>();
        map.put("indexType", "SCHEMA INDEX");
        map.put("entityType", "NODE");
        map.put("identifier", identifier);
        for (int i = 0; i < labels.length; i++) {
            String labelKey = labels.length > 1 ? "label_" + i : "label";
            String labelIdKey = labels.length > 1 ? "labelId_" + i : "labelId";
            map.put(labelKey, labels[i]);
            map.put(labelIdKey, String.valueOf(labelIds[i]));
        }
        for (int i = 0; i < propertyKeys.length; i++) {
            String key = (propertyKeys.length > 1) ? "propertyKey_" + i : "propertyKey";
            map.put(key, propertyKeys[i]);
        }
        return map;
    }
}
