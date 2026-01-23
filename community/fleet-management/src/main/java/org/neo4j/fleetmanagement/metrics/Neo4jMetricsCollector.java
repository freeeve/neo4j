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
package org.neo4j.fleetmanagement.metrics;

import static org.neo4j.fleetmanagement.configuration.Configuration.METRICS_CHANGE;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.neo4j.configuration.Config;
import org.neo4j.fleetmanagement.communication.model.DataPoint;
import org.neo4j.fleetmanagement.configuration.Configuration;
import org.neo4j.fleetmanagement.metrics.model.MetricsDefinition;
import org.neo4j.fleetmanagement.utils.Logger;
import org.neo4j.fleetmanagement.utils.PatternCompiler;
import org.neo4j.graphdb.config.Setting;

public class Neo4jMetricsCollector implements ICollector, PropertyChangeListener {
    private static final String[] percentiles = {"99thPercentile", "75thPercentile", "50thPercentile"};
    private static final String[] quantileLabels = {"0.99", "0.75", "0.50"};

    private Map<Pattern, MetricsDefinition> jmxMetricSpecifications;
    private final Configuration configuration;
    private final String query;

    public Neo4jMetricsCollector(Config config, Configuration configuration) {
        this.configuration = configuration;
        jmxMetricSpecifications = new HashMap<>();
        updateJmxMetricSpecifications(configuration.getMetrics());
        query = String.format("%s.metrics:*", getMetricsPrefix(config));
    }

    public void start() {
        configuration.addPropertyChangeListener(this);
    }

    private static String getMetricsPrefixConfiguration() {
        return "server.metrics.prefix";
    }

    private static String getMetricsPrefix(Config config) {
        String metricsPrefixConfig = getMetricsPrefixConfiguration();
        Map<String, Setting<Object>> declaredSettings = config.getDeclaredSettings();
        if (declaredSettings.containsKey(metricsPrefixConfig)) {
            return config.get(declaredSettings.get(metricsPrefixConfig)).toString();
        } else {
            return "neo4j";
        }
    }

    @Override
    public void collect(Map<String, List<DataPoint>> data) {
        Logger.getFleetManagerLogger().debug(String.format("will use query %s to collect metrics=", query));
        var jmxServer = ManagementFactory.getPlatformMBeanServer();
        try {
            for (ObjectName name : jmxServer.queryNames(new ObjectName(query), null)) {
                MetricContext metricContext = getMetricContext(name);
                if (metricContext != null) {
                    processMetric(jmxServer, metricContext, data);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void processMetric(MBeanServer jmxServer, MetricContext metricContext, Map<String, List<DataPoint>> data)
            throws Exception {
        switch (metricContext.getMetricDefinition().getJmxMetricSpecification().metricType) {
            case GAUGE:
                var value = jmxServer.getAttribute(metricContext.getName(), "Value");
                data.computeIfAbsent(metricContext.getMetricDefinition().getName(), k -> new ArrayList<>())
                        .add(new DataPoint(metricContext.getTags(), ((Number) value).doubleValue()));
                break;
            case HISTOGRAM:
                for (int i = 0; i < percentiles.length; i++) {
                    String percentileAttr = percentiles[i];
                    String percentileLabel = quantileLabels[i];
                    Object percentileValue = jmxServer.getAttribute(metricContext.getName(), percentileAttr);
                    Map<String, String> tagsWithPercentile = new HashMap<>(metricContext.getTags());
                    tagsWithPercentile.put("quantile", percentileLabel);
                    data.computeIfAbsent(metricContext.getMetricDefinition().getName(), k -> new ArrayList<>())
                            .add(new DataPoint(tagsWithPercentile, ((Number) percentileValue).doubleValue()));
                }
                break;
            case COUNTER:
                var count = jmxServer.getAttribute(metricContext.getName(), "Count");
                data.computeIfAbsent(metricContext.getMetricDefinition().getName(), k -> new ArrayList<>())
                        .add(new DataPoint(metricContext.getTags(), ((Number) count).doubleValue()));
                break;
        }
    }

    private MetricContext getMetricContext(ObjectName name) {
        var strippedName = name.getCanonicalName().replace("neo4j.metrics:name=", "");
        Map<String, String> tags = new HashMap<>();
        for (var entry : jmxMetricSpecifications.entrySet()) {
            var matcher = entry.getKey().matcher(strippedName);
            if (matcher.matches()) {
                MetricsDefinition metricDef = entry.getValue();
                if (metricDef.getTags() == null) {
                    return new MetricContext(metricDef, tags, name);
                }
                for (String tag : metricDef.getTags()) {
                    try {
                        if (matcher.group(tag) != null) {
                            tags.put(tag, matcher.group(tag));
                        }
                    } catch (IllegalArgumentException e) {
                        // Regex group with name doesn't exist, skip reporting metric
                        return null;
                    }
                }
                return new MetricContext(metricDef, tags, name);
            }
        }
        return null;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(METRICS_CHANGE)) {
            updateJmxMetricSpecifications(configuration.getMetrics());
        }
    }

    private void updateJmxMetricSpecifications(List<MetricsDefinition> metricsSpec) {
        if (metricsSpec == null) {
            return;
        }
        // Pre-compile patterns for efficient matching
        jmxMetricSpecifications = new java.util.HashMap<>();
        for (MetricsDefinition def : metricsSpec) {
            String metricName = def.getMetricName();
            jmxMetricSpecifications.put(PatternCompiler.constructPattern(metricName), def);
        }
    }
}

class MetricContext {
    private final MetricsDefinition metricDefinition;
    private final Map<String, String> tags;
    private final ObjectName name;

    public MetricContext(MetricsDefinition metricDefinition, Map<String, String> tags, ObjectName name) {
        this.metricDefinition = metricDefinition;
        this.tags = tags;
        this.name = name;
    }

    public ObjectName getName() {
        return name;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public MetricsDefinition getMetricDefinition() {
        return metricDefinition;
    }
}
