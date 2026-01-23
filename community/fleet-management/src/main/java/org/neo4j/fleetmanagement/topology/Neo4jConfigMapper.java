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
package org.neo4j.fleetmanagement.topology;

import static org.neo4j.fleetmanagement.configuration.Configuration.NEO4J_CONFIGS_CHANGE;
import static org.neo4j.fleetmanagement.utils.ObjectHandler.handleList;
import static org.neo4j.fleetmanagement.utils.ObjectHandler.handleMap;
import static org.neo4j.fleetmanagement.utils.ObjectHandler.isBasicType;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.neo4j.configuration.Config;
import org.neo4j.fleetmanagement.configuration.Configuration;
import org.neo4j.fleetmanagement.utils.PatternCompiler;

public class Neo4jConfigMapper implements PropertyChangeListener {
    private final Config config;
    private final Configuration configuration;
    private List<Pattern> neo4jConfigs;

    public Neo4jConfigMapper(Config config, Configuration configuration) {
        this.configuration = configuration;
        this.config = config;
        neo4jConfigs = List.of();
        updateNeo4jConfigs(configuration.getNeo4jConfigKeyGlobs());
    }

    public void start() {
        configuration.addPropertyChangeListener(this);
    }

    public Map<String, Object> mapConfig() {
        Map<String, Object> configMap = new HashMap<>();
        config.getDeclaredSettings().forEach((name, setting) -> {
            if (neo4jConfigs.stream().noneMatch(pattern -> pattern.matcher(name).matches())) {
                return;
            }
            Object value = config.get(setting);
            if (value != null) {
                if (value instanceof List) {
                    value = handleList((List<?>) value);
                } else if (value instanceof Map) {
                    value = handleMap((Map<?, ?>) value);
                } else if (!isBasicType(value)) {
                    value = value.toString();
                }
            }
            configMap.put(name, value);
        });
        return configMap;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(NEO4J_CONFIGS_CHANGE)) {
            updateNeo4jConfigs(configuration.getNeo4jConfigKeyGlobs());
        }
    }

    private void updateNeo4jConfigs(List<String> newNeo4jConfigs) {
        if (newNeo4jConfigs == null) {
            return;
        }
        this.neo4jConfigs =
                newNeo4jConfigs.stream().map(PatternCompiler::constructPattern).collect(Collectors.toList());
    }
}
