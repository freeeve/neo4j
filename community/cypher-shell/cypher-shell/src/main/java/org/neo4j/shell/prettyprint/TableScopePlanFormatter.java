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

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toMap;
import static org.neo4j.shell.prettyprint.OutputFormatter.repeatConditionally;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.neo4j.driver.Value;
import org.neo4j.driver.summary.Plan;

public class TableScopePlanFormatter extends AbstractTablePlanFormatter {

    public static final String OPERATOR = "Cypher";
    public static final int MAX_OPERATOR_COLUMN_WIDTH = 50;

    private static final String INCOMING_CONSTANTS = "Incoming consts";
    private static final String INCOMING_VARIABLES = "Incoming vars";
    private static final String INCOMING_GROUPING_KEYS = "Incoming grouping keys";
    private static final String INCOMING_TOPOLOGY_CONSTANTS = "Incoming topology vars";
    private static final String INCOMING_PREDICATE_VARIABLES = "Incoming predicate vars";
    private static final String INCOMING_PATH_VARIABLES = "Incoming path vars";
    private static final String REFERENCED = "Referenced";
    private static final String DECLARED_CONSTANTS = "Declared consts";
    private static final String DECLARED_VARIABLES = "Declared vars";
    private static final String RESULT_COLUMNS = "Result columns";
    private static final String OUTGOING_CONSTANTS = "Outgoing consts";
    private static final String OUTGOING_VARIABLES = "Outgoing vars";

    private static final Pattern NEWLINE_WHITESPACE_PATTERN = Pattern.compile("\\s*\\R\\s*");
    private static final List<String> HEADERS = asList(
            OPERATOR,
            INCOMING_CONSTANTS,
            INCOMING_VARIABLES,
            INCOMING_GROUPING_KEYS,
            INCOMING_TOPOLOGY_CONSTANTS,
            INCOMING_PREDICATE_VARIABLES,
            INCOMING_PATH_VARIABLES,
            REFERENCED,
            DECLARED_CONSTANTS,
            DECLARED_VARIABLES,
            RESULT_COLUMNS,
            OUTGOING_CONSTANTS,
            OUTGOING_VARIABLES);

    @Override
    protected String operatorHeader() {
        return OPERATOR;
    }

    @Override
    protected List<String> headers() {
        return HEADERS;
    }

    @Override
    protected Stream<List<TableRow>> children(Plan plan, Level level, Map<String, Integer> columns) {
        List<? extends Plan> children = plan.children();
        switch (children.size()) {
            case 0:
                return Stream.empty();
            case 1:
                return Stream.of(accumulate(children.get(0), level.onlyChild(), columns));
            case 2: {
                Level firstChildLevel = level.firstChild();
                return Stream.of(
                        accumulate(children.get(0), firstChildLevel, columns),
                        accumulate(children.get(1), firstChildLevel.lastSibling(), columns));
            }
            default: // > 2
            {
                Level firstChildLevel = level.firstChild();
                int lastChildIndex = children.size() - 1;
                return Stream.concat(
                        Stream.concat(
                                Stream.of(accumulate(children.get(0), firstChildLevel, columns)),
                                children.subList(1, lastChildIndex).stream()
                                        .map((child) -> accumulate(child, firstChildLevel.sibling(), columns))),
                        Stream.of(accumulate(children.get(lastChildIndex), firstChildLevel.lastSibling(), columns)));
            }
        }
    }

    @Override
    protected String operatorType(Plan plan, Level level) {
        String operatorString =
                NEWLINE_WHITESPACE_PATTERN.matcher(plan.operatorType()).replaceAll(" ");
        int availableWith = MAX_OPERATOR_COLUMN_WIDTH - level.line().length();
        return (operatorString.length() > availableWith)
                ? operatorString.substring(0, availableWith - 3) + "..."
                : operatorString;
    }

    @Override
    protected Map<String, Cell> details(Plan plan, Map<String, Integer> columns) {
        Map<String, Value> args = plan.arguments();

        Stream<Optional<Pair<String, Cell>>> formattedPlan = args.entrySet().stream()
                .map(e -> {
                    Value value = e.getValue();
                    switch (e.getKey()) {
                        case "incoming constants":
                            return mapping(INCOMING_CONSTANTS, new LeftJustifiedCell(value.asString()), columns);
                        case "incoming variables":
                            return mapping(INCOMING_VARIABLES, new LeftJustifiedCell(value.asString()), columns);
                        case "incoming grouping keys":
                            return mapping(INCOMING_GROUPING_KEYS, new LeftJustifiedCell(value.asString()), columns);
                        case "incoming topology variables":
                            return mapping(
                                    INCOMING_TOPOLOGY_CONSTANTS, new LeftJustifiedCell(value.asString()), columns);
                        case "incoming predicate variables":
                            return mapping(
                                    INCOMING_PREDICATE_VARIABLES, new LeftJustifiedCell(value.asString()), columns);
                        case "incoming path variables":
                            return mapping(INCOMING_PATH_VARIABLES, new LeftJustifiedCell(value.asString()), columns);
                        case "referenced":
                            return mapping(REFERENCED, new LeftJustifiedCell(value.asString()), columns);
                        case "declared constants": {
                            String valueString = value.asString().trim();
                            if (valueString.isEmpty()) {
                                return Optional.empty();
                            } else {
                                return mapping(DECLARED_CONSTANTS, new LeftJustifiedCell(valueString), columns);
                            }
                        }
                        case "declared variables":
                            return mapping(DECLARED_VARIABLES, new LeftJustifiedCell(value.asString()), columns);
                        case "result columns":
                            return mapping(RESULT_COLUMNS, new LeftJustifiedCell(value.asString()), columns);
                        case "outgoing constants": {
                            String valueString = value.asString().trim();
                            if (valueString.isEmpty()) {
                                return Optional.empty();
                            } else {
                                return mapping(OUTGOING_CONSTANTS, new LeftJustifiedCell(value.asString()), columns);
                            }
                        }
                        case "outgoing variables":
                            return mapping(OUTGOING_VARIABLES, new LeftJustifiedCell(value.asString()), columns);
                        default:
                            return Optional.empty();
                    }
                });

        return formattedPlan.filter(Optional::isPresent).collect(toMap(o -> o.get()._1, o -> o.get()._2));
    }

    private static Optional<Pair<String, Cell>> mapping(String key, Cell value, Map<String, Integer> columns) {
        update(columns, key, value.length);
        return Optional.of(Pair.of(key, value));
    }

    private static void update(Map<String, Integer> columns, String key, int length) {
        columns.put(key, Math.max(columns.getOrDefault(key, 0), length));
    }

    @Override
    protected Level rootLevel() {
        return new Root();
    }

    static class Root extends Level {
        @Override
        Level sibling() {
            throw new IllegalStateException("root level has no sibling");
        }

        @Override
        Level lastSibling() {
            throw new IllegalStateException("root level has no sibling");
        }

        @Override
        Level firstChild() {
            BitSet childLastOnLevels = new BitSet();
            childLastOnLevels.set(0, true);
            childLastOnLevels.set(1, false);
            return new Fork(1, childLastOnLevels);
        }

        @Override
        Level onlyChild() {
            BitSet childLastOnLevels = new BitSet();
            childLastOnLevels.set(0, true);
            childLastOnLevels.set(1, true);
            return new Fork(1, childLastOnLevels);
        }

        @Override
        String line() {
            return "+";
        }

        @Override
        Optional<String> connector() {
            return Optional.empty();
        }
    }

    static class Child extends Level {
        private final int level; // 0-based
        private final BitSet lastOnLevels;

        Child(int level, BitSet lastOnLevels) {
            this.level = level;
            this.lastOnLevels = lastOnLevels;
        }

        @Override
        Level sibling() {
            return new Child(level, lastOnLevels);
        }

        @Override
        Level lastSibling() {
            BitSet childLastOnLevels = ((BitSet) lastOnLevels.clone());
            childLastOnLevels.set(level, true);
            return new Child(level, childLastOnLevels);
        }

        @Override
        Level firstChild() {
            BitSet childLastOnLevels = ((BitSet) lastOnLevels.clone());
            childLastOnLevels.set(level + 1, false);
            return new Fork(level + 1, childLastOnLevels);
        }

        @Override
        Level onlyChild() {
            BitSet childLastOnLevels = ((BitSet) lastOnLevels.clone());
            childLastOnLevels.set(level + 1, true);
            return new Fork(level + 1, childLastOnLevels);
        }

        @Override
        String line() {
            return repeatConditionally("  ", "| ", level, lastOnLevels::get) + "+";
        }

        @Override
        Optional<String> connector() {
            return Optional.of(repeatConditionally("  ", "| ", level, lastOnLevels::get) + "|");
        }
    }

    static class Fork extends Level {
        private final int level; // 0-based
        private final BitSet lastOnLevels;

        Fork(int level, BitSet lastOnLevels) {
            this.level = level;
            this.lastOnLevels = lastOnLevels;
        }

        @Override
        Level sibling() {
            return new Child(level, lastOnLevels);
        }

        @Override
        Level lastSibling() {
            BitSet childLastOnLevels = ((BitSet) lastOnLevels.clone());
            childLastOnLevels.set(level, true);
            return new Child(level, childLastOnLevels);
        }

        @Override
        Level firstChild() {
            BitSet childLastOnLevels = ((BitSet) lastOnLevels.clone());
            childLastOnLevels.set(level + 1, false);
            return new Fork(level + 1, childLastOnLevels);
        }

        @Override
        Level onlyChild() {
            BitSet childLastOnLevels = ((BitSet) lastOnLevels.clone());
            childLastOnLevels.set(level + 1, true);
            return new Fork(level + 1, childLastOnLevels);
        }

        @Override
        String line() {
            return repeatConditionally("  ", "| ", level, lastOnLevels::get) + "+";
        }

        @Override
        Optional<String> connector() {
            return Optional.of(repeatConditionally("  ", "| ", level - 1, lastOnLevels::get)
                    + (lastOnLevels.get(level - 1) ? " \\" : "|\\"));
        }
    }
}
