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
package org.neo4j.shell.prettyprint;

import java.util.ArrayList;
import java.util.List;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.SummaryCounters;
import org.neo4j.shell.cli.Format;

public class StatisticsCollector {
    private Format format;

    public StatisticsCollector(Format format) {
        this.format = format;
    }

    public String collect(ResultSummary summary) {
        if (Format.VERBOSE == format) {
            return collectStatistics(summary);
        } else {
            return "";
        }
    }

    private enum Operation {
        ADDED("added", "Added"),
        CREATED("created", "Created"),
        DELETED("deleted", "Deleted"),
        SET("set", "Set"),
        REMOVED("removed", "Removed");

        private final String lower;
        private final String capitalized;

        Operation(String lower, String capitalized) {
            this.lower = lower;
            this.capitalized = capitalized;
        }
    }

    private enum Element {
        NODE("node", "nodes"),
        RELATIONSHIP("relationship", "relationships"),
        PROPERTY("property", "properties"),
        LABEL("label", "labels"),
        INDEX("index", "indexes"),
        CONSTRAINT("constraint", "constraints");

        private final String singular;
        private final String plural;

        Element(String singular, String plural) {
            this.singular = singular;
            this.plural = plural;
        }
    }

    private static String collectStatistics(ResultSummary summary) {
        List<String> statistics = new ArrayList<>();
        SummaryCounters counters = summary.counters();
        if (counters == null) {
            return "";
        }
        addStatistic(statistics, Operation.CREATED, counters.nodesCreated(), Element.NODE);
        addStatistic(statistics, Operation.DELETED, counters.nodesDeleted(), Element.NODE);
        addStatistic(statistics, Operation.CREATED, counters.relationshipsCreated(), Element.RELATIONSHIP);
        addStatistic(statistics, Operation.DELETED, counters.relationshipsDeleted(), Element.RELATIONSHIP);
        addStatistic(statistics, Operation.SET, counters.propertiesSet(), Element.PROPERTY);
        addStatistic(statistics, Operation.ADDED, counters.labelsAdded(), Element.LABEL);
        addStatistic(statistics, Operation.REMOVED, counters.labelsRemoved(), Element.LABEL);
        addStatistic(statistics, Operation.ADDED, counters.indexesAdded(), Element.INDEX);
        addStatistic(statistics, Operation.REMOVED, counters.indexesRemoved(), Element.INDEX);
        addStatistic(statistics, Operation.ADDED, counters.constraintsAdded(), Element.CONSTRAINT);
        addStatistic(statistics, Operation.REMOVED, counters.constraintsRemoved(), Element.CONSTRAINT);
        return String.join(", ", statistics);
    }

    private static void addStatistic(List<String> statistics, Operation operation, int number, Element element) {
        if (number > 0) {
            String operationCaseAdjusted = (statistics.isEmpty()) ? operation.capitalized : operation.lower;
            String elementNumberAdjusted = (number == 1) ? element.singular : element.plural;
            statistics.add(String.format("%s %d %s", operationCaseAdjusted, number, elementNumberAdjusted));
        }
    }
}
