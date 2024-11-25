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
package org.neo4j.fabric.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.neo4j.fabric.stream.FragmentResult;
import org.neo4j.fabric.stream.Record;
import org.neo4j.fabric.stream.Records;
import org.neo4j.fabric.stream.summary.PlanlessSummary;
import org.neo4j.graphdb.QueryExecutionType;

public class ApplyExecutor implements FragmentResult {

    private final List<Supplier<PlanlessSummary>> summaries = new ArrayList<>();
    private final List<String> columns;
    private final FragmentResult input;
    private final boolean unitInner;
    private final QueryExecutionType queryExecutionType;
    private final Function<Record, FragmentResult> fragmentExecutor;
    private FragmentResult currentBatch;
    private Record inputRecord;

    public ApplyExecutor(
            List<String> columns,
            FragmentResult input,
            boolean unitInner,
            QueryExecutionType queryExecutionType,
            Function<Record, FragmentResult> fragmentExecutor) {
        this.columns = columns;
        this.input = input;
        this.unitInner = unitInner;
        this.queryExecutionType = queryExecutionType;
        this.fragmentExecutor = fragmentExecutor;
        summaries.add(input::consume);
    }

    @Override
    public List<String> columns() {
        return columns;
    }

    @Override
    public Record next() {
        if (currentBatch != null) {
            Record innerRecord = currentBatch.next();
            if (innerRecord != null) {
                return Records.join(inputRecord, innerRecord);
            } else {
                currentBatch = null;
                // We have either exhausted an inner stream producing records
                // or a unit inner stream.
                // We have to produce the input record in the latter case.
                if (unitInner) {
                    return inputRecord;
                }
            }
        }

        inputRecord = input.next();
        if (inputRecord == null) {
            return null;
        }

        currentBatch = fragmentExecutor.apply(inputRecord);
        summaries.add(currentBatch::consume);
        // We have just produced a new batch, so let's go again.
        return next();
    }

    @Override
    public PlanlessSummary consume() {
        return summaries.stream()
                .map(Supplier::get)
                .reduce(PlanlessSummary::merge)
                .orElse(null);
    }

    @Override
    public QueryExecutionType executionType() {
        return queryExecutionType;
    }
}
