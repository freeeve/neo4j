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

import static java.lang.String.CASE_INSENSITIVE_ORDER;

import java.util.Collections;
import java.util.Comparator;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.Value;

public class IndexConfigValidationRecords {
    private static final Function<State, SortedSet<IndexConfigValidationRecord>> NEW_RECORD_SORTED_SET =
            ignored -> new TreeSet<>();

    private final SortedMap<State, SortedSet<IndexConfigValidationRecord>> records = new TreeMap<>();

    public IndexConfigValidationRecords with(IndexConfigValidationRecord record) {
        records.computeIfAbsent(record.state(), NEW_RECORD_SORTED_SET).add(record);
        return this;
    }

    public SortedSet<IndexConfigValidationRecord> get(State state) {
        final SortedSet<IndexConfigValidationRecord> recordsForState = records.get(state);
        return recordsForState != null
                ? Collections.unmodifiableSortedSet(recordsForState)
                : Collections.emptySortedSet();
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

    public SortedSet<Valid> validRecords() {
        final SortedSet<IndexConfigValidationRecord> shouldBeValidRecords = records.get(State.VALID);
        if (shouldBeValidRecords == null || shouldBeValidRecords.isEmpty()) {
            return Collections.emptySortedSet();
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

    public SortedSet<IndexConfigValidationRecord> invalidRecords() {
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
        return invalidRecords.isEmpty()
                ? Collections.emptySortedSet()
                : Collections.unmodifiableSortedSet(invalidRecords);
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

    public enum State {
        VALID,
        PENDING,
        UNRECOGNIZED_SETTING,
        MISSING_SETTING,
        INCORRECT_TYPE,
        INVALID_VALUE;

        public static final SortedSet<State> INVALID_STATES;

        static {
            final TreeSet<State> invalidStates = new TreeSet<>();
            for (final State state : values()) {
                if (state != VALID) {
                    invalidStates.add(state);
                }
            }
            INVALID_STATES = Collections.unmodifiableSortedSet(invalidStates);
        }
    }

    @FunctionalInterface
    public interface NamedSetting {
        String settingName();
    }

    public interface KnownSetting extends NamedSetting {
        IndexSetting setting();

        @Override
        default String settingName() {
            return setting().getSettingName();
        }
    }

    public sealed interface IndexConfigValidationRecord extends NamedSetting, Comparable<IndexConfigValidationRecord> {
        Comparator<IndexConfigValidationRecord> COMPARATOR = Comparator.comparing(IndexConfigValidationRecord::state)
                .thenComparing(NamedSetting::settingName, CASE_INSENSITIVE_ORDER);

        State state();

        @Override
        default int compareTo(IndexConfigValidationRecord other) {
            return COMPARATOR.compare(this, other);
        }
    }

    public record Valid(IndexSetting setting, Object value, Value stored)
            implements KnownSetting, IndexConfigValidationRecord {
        public Valid(Pending pending, Value stored) {
            this(pending.setting, pending.value, stored);
        }

        @Override
        public State state() {
            return State.VALID;
        }

        public <T> T get() {
            return (T) value;
        }
    }

    public sealed interface Invalid extends IndexConfigValidationRecord {}

    public record Pending(IndexSetting setting, AnyValue rawValue, Object value) implements KnownSetting, Invalid {
        public Pending(IndexSetting setting, AnyValue rawValue) {
            this(setting, rawValue, null);
        }

        public Pending(Pending pending, Object value) {
            this(pending.setting, pending.rawValue, value);
        }

        @Override
        public State state() {
            return State.PENDING;
        }

        public <T> T get() {
            return (T) value;
        }
    }

    public record UnrecognizedSetting(String settingName) implements Invalid {
        @Override
        public State state() {
            return State.UNRECOGNIZED_SETTING;
        }
    }

    public record MissingSetting(IndexSetting setting) implements KnownSetting, Invalid {
        @Override
        public State state() {
            return State.MISSING_SETTING;
        }
    }

    public record IncorrectType(IndexSetting setting, AnyValue rawValue, Class<?> targetType)
            implements KnownSetting, Invalid {
        public IncorrectType(Pending pending, Class<?> targetType) {
            this(pending.setting, pending.rawValue, targetType);
        }

        @Override
        public State state() {
            return State.INCORRECT_TYPE;
        }

        public Class<?> providedType() {
            return rawValue.getClass();
        }

        public String providedTypeString() {
            return rawValue.prettify();
        }
    }

    public record InvalidValue(IndexSetting setting, AnyValue rawValue, Object value, Object valid)
            implements KnownSetting, Invalid {
        public InvalidValue(Pending pending, Object value, Object valid) {
            this(pending.setting, pending.rawValue, value, valid);
        }

        public InvalidValue(Pending pending, Object valid) {
            this(pending, pending.value, valid);
        }

        @Override
        public State state() {
            return State.INVALID_VALUE;
        }
    }
}
