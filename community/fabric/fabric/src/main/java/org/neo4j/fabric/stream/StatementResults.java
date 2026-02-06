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
package org.neo4j.fabric.stream;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.neo4j.fabric.executor.ProfilingContext;
import org.neo4j.fabric.executor.QueryTypes;
import org.neo4j.fabric.stream.summary.PlanlessSummary;
import org.neo4j.fabric.stream.summary.Summary;
import org.neo4j.graphdb.QueryExecutionType;
import org.neo4j.graphdb.QueryStatistics;

public final class StatementResults {
    private StatementResults() {}

    public static StatementResult emptyStream(List<String> columns, Summary summary, QueryExecutionType executionType) {
        return new StatementResult() {
            @Override
            public List<String> columns() {
                return columns;
            }

            @Override
            public Record next() {
                return null;
            }

            @Override
            public Summary consume() {
                return summary;
            }

            @Override
            public QueryExecutionType executionType() {
                return executionType;
            }
        };
    }

    public static FragmentResult toFragmentResult(
            StatementResult statementResult, ProfilingContext.QueryFragment profilingFragment) {
        return new FragmentResult() {

            @Override
            public List<String> columns() {
                return statementResult.columns();
            }

            @Override
            public Record next() {
                return statementResult.next();
            }

            @Override
            public PlanlessSummary consume() {
                var summary = statementResult.consume();
                if (summary.executionPlanDescription() != null) {
                    profilingFragment.finish(summary.executionPlanDescription());
                }
                return new PlanlessSummary(
                        summary.getNotifications(), summary.getGqlStatusObjects(), summary.getQueryStatistics());
            }

            @Override
            public QueryExecutionType executionType() {
                return statementResult.executionType();
            }
        };
    }

    public static FragmentResult mergeUnion(FragmentResult lhs, FragmentResult rhs) {
        // The union output columns is copied from the lhs output columns, therefor we need to change the order
        // of the rhs output columns.
        boolean rearangeColumns = !lhs.columns().equals(rhs.columns());
        List<Integer> rhsOutputOrder;
        if (rearangeColumns) {
            rhsOutputOrder = lhs.columns().stream().map(rhs.columns()::indexOf).toList();
        } else {
            rhsOutputOrder = null;
        }

        var executionType = merge(lhs.executionType(), rhs.executionType());

        return new FragmentResult() {

            boolean lhsCompletd = false;

            @Override
            public List<String> columns() {
                return lhs.columns();
            }

            @Override
            public Record next() {
                if (!lhsCompletd) {
                    var record = lhs.next();
                    if (record != null) {
                        return record;
                    }

                    lhsCompletd = true;
                }

                var record = rhs.next();
                if (rearangeColumns && record != null) {
                    return rearrangeRecordOrder(record, rhsOutputOrder);
                }

                return record;
            }

            @Override
            public PlanlessSummary consume() {
                var lhsSummary = lhs.consume();
                var rhsSummary = rhs.consume();
                return PlanlessSummary.merge(lhsSummary, rhsSummary);
            }

            @Override
            public QueryExecutionType executionType() {
                return executionType;
            }

            private Record rearrangeRecordOrder(Record record, List<Integer> columns) {
                var values = columns.stream().map(record::getValue).collect(Collectors.toList());
                return Records.of(values);
            }
        };
    }

    public static QueryExecutionType merge(QueryExecutionType a, QueryExecutionType b) {
        if (a == null) {
            return b;
        }

        if (b == null) {
            return a;
        }

        return QueryTypes.merge(a, b);
    }

    public static FragmentResult distinct(FragmentResult original) {
        var records = new HashSet<Record>();
        return new DelegatingFragmentResult(original) {

            @Override
            public Record next() {
                while (true) {
                    var record = delegate.next();
                    if (record == null) {
                        return null;
                    }

                    if (records.add(record)) {
                        return record;
                    }
                }
            }
        };
    }

    public static FragmentResult emptyFragment() {
        return new FragmentResult() {

            @Override
            public List<String> columns() {
                return List.of();
            }

            @Override
            public Record next() {
                return null;
            }

            @Override
            public PlanlessSummary consume() {
                return new PlanlessSummary(Collections.emptyList(), Collections.emptyList(), QueryStatistics.EMPTY);
            }

            @Override
            public QueryExecutionType executionType() {
                return null;
            }
        };
    }

    public static FragmentResult oneRecord(List<String> columns, Record r, QueryExecutionType executionType) {
        return new FragmentResult() {

            private Record record = r;

            @Override
            public List<String> columns() {
                return columns;
            }

            @Override
            public Record next() {
                var result = record;
                if (record != null) {
                    record = null;
                }

                return result;
            }

            @Override
            public PlanlessSummary consume() {
                return new PlanlessSummary(Collections.emptyList(), Collections.emptyList(), QueryStatistics.EMPTY);
            }

            @Override
            public QueryExecutionType executionType() {
                return executionType;
            }
        };
    }
}
