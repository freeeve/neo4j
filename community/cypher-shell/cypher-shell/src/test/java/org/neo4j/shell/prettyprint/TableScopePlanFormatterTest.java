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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.neo4j.shell.prettyprint.OutputFormatter.NEWLINE;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Value;
import org.neo4j.driver.internal.value.StringValue;
import org.neo4j.driver.summary.Plan;

class TableScopePlanFormatterTest {
    private final TableScopePlanFormatter tableScopePlanFormatter = new TableScopePlanFormatter();

    @Test
    void justTheQuery() {
        Plan plan = mock(Plan.class);
        Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
        when(plan.arguments()).thenReturn(args);
        when(plan.operatorType()).thenReturn("Query");

        assertThat(tableScopePlanFormatter.formatPlan(plan))
                .isEqualTo(String.join(
                        NEWLINE,
                        "+--------+---------------+",
                        "| Cypher | Incoming vars |",
                        "+--------+---------------+",
                        "| +Query | x             |",
                        "+--------+---------------+",
                        ""));
    }

    @Test
    void theQueryWithOneChild() {
        Plan childPlan = mock(Plan.class);
        {
            Plan plan = childPlan;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn("Child");
        }

        Plan mainPlan = mock(Plan.class);
        {
            Plan plan = mainPlan;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn("Query");
            doReturn(List.of(childPlan)).when(plan).children();
        }

        assertThat(tableScopePlanFormatter.formatPlan(mainPlan))
                .isEqualTo(String.join(
                        NEWLINE,
                        "+----------+---------------+",
                        "| Cypher   | Incoming vars |",
                        "+----------+---------------+",
                        "| +Query   | x             |",
                        "|  \\       +---------------+",
                        "|   +Child | x             |",
                        "+----------+---------------+",
                        ""));
    }

    @Test
    void theQueryWithOneChildAndOneChild() {
        Plan childPlan11 = mock(Plan.class);
        {
            Plan plan = childPlan11;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn("C11");
        }
        Plan childPlan1 = mock(Plan.class);
        {
            Plan plan = childPlan1;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn("C1");
            doReturn(List.of(childPlan11)).when(plan).children();
        }

        Plan mainPlan = mock(Plan.class);
        {
            Plan plan = mainPlan;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn("Query");
            doReturn(List.of(childPlan1)).when(plan).children();
        }

        assertThat(tableScopePlanFormatter.formatPlan(mainPlan))
                .isEqualTo(String.join(
                        NEWLINE,
                        "+----------+---------------+",
                        "| Cypher   | Incoming vars |",
                        "+----------+---------------+",
                        "| +Query   | x             |",
                        "|  \\       +---------------+",
                        "|   +C1    | x             |",
                        "|    \\     +---------------+",
                        "|     +C11 | x             |",
                        "+----------+---------------+",
                        ""));
    }

    @Test
    void theQueryWithLongMultilineText() {
        Plan childPlan11 = mock(Plan.class);
        {
            Plan plan = childPlan11;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn(String.join(NEWLINE, "Stet clita", "     kasd gubergren."));
        }
        Plan childPlan1 = mock(Plan.class);
        {
            Plan plan = childPlan1;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType())
                    .thenReturn(String.join(
                            NEWLINE,
                            "At vero eos et accusam et justo duo dolores et ea rebum.",
                            "Stet clita",
                            "     kasd gubergren."));
            doReturn(List.of(childPlan11)).when(plan).children();
        }

        Plan mainPlan = mock(Plan.class);
        {
            Plan plan = mainPlan;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType())
                    .thenReturn(String.join(
                            NEWLINE,
                            "Lorem ipsum dolor sit amet,  ",
                            "      consetetur",
                            "      sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua.",
                            "At vero eos et accusam et justo duo dolores et ea rebum.",
                            "Stet clita",
                            "     kasd gubergren."));
            doReturn(List.of(childPlan1)).when(plan).children();
        }

        assertThat(tableScopePlanFormatter.formatPlan(mainPlan))
                .isEqualTo(String.join(
                        NEWLINE,
                        "+----------------------------------------------------+---------------+",
                        "| Cypher                                             | Incoming vars |",
                        "+----------------------------------------------------+---------------+",
                        "| +Lorem ipsum dolor sit amet, consetetur sadipsc... | x             |",
                        "|  \\                                                 +---------------+",
                        "|   +At vero eos et accusam et justo duo dolores ... | x             |",
                        "|    \\                                               +---------------+",
                        "|     +Stet clita kasd gubergren.                    | x             |",
                        "+----------------------------------------------------+---------------+",
                        ""));
    }

    @Test
    void theQueryWithTwoChildren() {
        Plan childPlan1 = mock(Plan.class);
        {
            Plan plan = childPlan1;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn("Child1");
        }
        Plan childPlan2 = mock(Plan.class);
        {
            Plan plan = childPlan2;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn("Child2");
        }

        Plan mainPlan = mock(Plan.class);
        {
            Plan plan = mainPlan;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn("Query");
            doReturn(List.of(childPlan1, childPlan2)).when(plan).children();
        }

        assertThat(tableScopePlanFormatter.formatPlan(mainPlan))
                .isEqualTo(String.join(
                        NEWLINE,
                        "+-----------+---------------+",
                        "| Cypher    | Incoming vars |",
                        "+-----------+---------------+",
                        "| +Query    | x             |",
                        "|  \\        +---------------+",
                        "|   +Child1 | x             |",
                        "|   |       +---------------+",
                        "|   +Child2 | x             |",
                        "+-----------+---------------+",
                        ""));
    }

    @Test
    void theQueryWithMultipleChildren() {
        Plan childPlan = mock(Plan.class);
        {
            Plan plan = childPlan;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn("C");
        }
        Plan childPlan11 = mock(Plan.class);
        {
            Plan plan = childPlan11;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn("C11");
            doReturn(List.of(childPlan, childPlan, childPlan)).when(plan).children();
        }
        Plan childPlan1 = mock(Plan.class);
        {
            Plan plan = childPlan1;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn("C1");
            doReturn(List.of(childPlan11)).when(plan).children();
        }
        Plan childPlan2 = mock(Plan.class);
        {
            Plan plan = childPlan2;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn("C2");
            doReturn(List.of(childPlan, childPlan)).when(plan).children();
        }

        Plan mainPlan = mock(Plan.class);
        {
            Plan plan = mainPlan;
            Map<String, Value> args = Collections.singletonMap("incoming variables", new StringValue("x"));
            when(plan.arguments()).thenReturn(args);
            when(plan.operatorType()).thenReturn("Query");
            doReturn(List.of(childPlan1, childPlan2)).when(plan).children();
        }

        assertThat(tableScopePlanFormatter.formatPlan(mainPlan))
                .isEqualTo(String.join(
                        NEWLINE,
                        "+----------+---------------+",
                        "| Cypher   | Incoming vars |",
                        "+----------+---------------+",
                        "| +Query   | x             |",
                        "|  \\       +---------------+",
                        "|   +C1    | x             |",
                        "|   |\\     +---------------+",
                        "|   | +C11 | x             |",
                        "|   |  \\   +---------------+",
                        "|   |   +C | x             |",
                        "|   |   |  +---------------+",
                        "|   |   +C | x             |",
                        "|   |   |  +---------------+",
                        "|   |   +C | x             |",
                        "|   |      +---------------+",
                        "|   +C2    | x             |",
                        "|    \\     +---------------+",
                        "|     +C   | x             |",
                        "|     |    +---------------+",
                        "|     +C   | x             |",
                        "+----------+---------------+",
                        ""));
    }
}
