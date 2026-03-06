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
package org.neo4j.internal.schema;

import java.util.Collections;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.schema.IndexConfigValidationRecord.State;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Valid;

public class IndexConfigValidationRecords {
    private static final Function<State, SortedSet<IndexConfigValidationRecord>> NEW_RECORD_SORTED_SET =
            ignored -> new TreeSet<>();

    private final SortedMap<State, SortedSet<IndexConfigValidationRecord>> records = new TreeMap<>();

    public IndexConfigValidationRecords with(IndexConfigValidationRecord record) {
        records.computeIfAbsent(record.state(), NEW_RECORD_SORTED_SET).add(record);
        return this;
    }

    public Iterable<IndexConfigValidationRecord> get(State state) {
        final SortedSet<IndexConfigValidationRecord> recordsForState = records.get(state);
        return recordsForState != null ? Collections.unmodifiableSortedSet(recordsForState) : Iterables.empty();
    }

    public boolean invalid() {
        for (final State state : records.keySet()) {
            if (state != State.VALID) {
                return true;
            }
        }
        return false;
    }

    public boolean valid() {
        return !invalid();
    }

    public Iterable<Valid> validRecords() {
        final SortedSet<IndexConfigValidationRecord> shouldBeValidRecords = records.get(State.VALID);
        if (shouldBeValidRecords == null || shouldBeValidRecords.isEmpty()) {
            return Iterables.empty();
        }

        final SortedSet<Valid> validRecords = new TreeSet<>();
        for (final IndexConfigValidationRecord record : shouldBeValidRecords) {
            if (!(record instanceof final Valid valid)) {
                throw new IllegalStateException("%s has %s state but was not an instance of %s"
                        .formatted(record, State.VALID, Valid.class.getSimpleName()));
            }
            validRecords.add(valid);
        }
        return Collections.unmodifiableSortedSet(validRecords);
    }

    public Iterable<IndexConfigValidationRecord> invalidRecords() {
        final SortedSet<IndexConfigValidationRecord> invalidRecords = new TreeSet<>();
        for (final State state : State.INVALID_STATES) {
            final SortedSet<IndexConfigValidationRecord> invalidRecordsForState = records.get(state);
            if (invalidRecordsForState == null || invalidRecordsForState.isEmpty()) {
                continue;
            }

            for (final IndexConfigValidationRecord record : invalidRecordsForState) {
                if (record instanceof final Valid valid) {
                    throw new IllegalStateException("%s has %s state but was an instance of %s"
                            .formatted(valid, state, Valid.class.getSimpleName()));
                }
                invalidRecords.add(record);
            }
        }
        return invalidRecords.isEmpty() ? Iterables.empty() : Collections.unmodifiableSortedSet(invalidRecords);
    }

    public IndexConfigValidationRecord getFirstInvalidRecordOrNull() {
        for (final State state : State.INVALID_STATES) {
            final SortedSet<IndexConfigValidationRecord> invalidRecordsForState = records.get(state);
            if (invalidRecordsForState == null || invalidRecordsForState.isEmpty()) {
                continue;
            }

            for (final IndexConfigValidationRecord record : invalidRecordsForState) {
                if (record instanceof final Valid valid) {
                    throw new IllegalStateException("%s has %s state but was an instance of %s"
                            .formatted(valid, state, Valid.class.getSimpleName()));
                }
                return record;
            }
        }
        return null;
    }
}
