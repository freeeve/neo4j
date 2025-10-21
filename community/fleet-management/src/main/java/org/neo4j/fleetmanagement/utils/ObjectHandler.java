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
package org.neo4j.fleetmanagement.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ObjectHandler {

    public static Object handleList(List<?> list) {
        if (list.isEmpty()) {
            return list;
        }
        boolean allBasicTypes = list.stream().allMatch(ObjectHandler::isBasicType);
        if (allBasicTypes) {
            return list;
        }
        return list.stream()
                .map(item -> {
                    if (item instanceof List) {
                        return handleList((List<?>) item);
                    } else if (item instanceof Map) {
                        return handleMap((Map<?, ?>) item);
                    } else if (!isBasicType(item)) {
                        return item.toString();
                    } else {
                        return item;
                    }
                })
                .collect(Collectors.toList());
    }

    public static Object handleMap(Map<?, ?> map) {
        if (map.isEmpty()) {
            return map;
        }
        Map<Object, Object> result = new HashMap<>();
        map.forEach((key, value) -> {
            if (value != null) {
                if (value instanceof List) {
                    result.put(key, handleList((List<?>) value));
                } else if (value instanceof Map) {
                    result.put(key, handleMap((Map<?, ?>) value));
                } else if (!isBasicType(value)) {
                    result.put(key, value.toString());
                } else {
                    result.put(key, value);
                }
            } else {
                result.put(key, null);
            }
        });
        return result;
    }

    public static boolean isBasicType(Object obj) {
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean;
    }
}
