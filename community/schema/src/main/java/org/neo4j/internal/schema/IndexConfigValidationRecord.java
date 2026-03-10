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
import java.util.SortedSet;
import java.util.TreeSet;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.schema.IndexConfigUtils.HasSetting;
import org.neo4j.internal.schema.IndexConfigUtils.NamedSetting;
import org.neo4j.util.MarkerInterface;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.Value;

public sealed interface IndexConfigValidationRecord extends NamedSetting, Comparable<IndexConfigValidationRecord> {
    Comparator<IndexConfigValidationRecord> COMPARATOR = Comparator.comparing(IndexConfigValidationRecord::state)
            .thenComparing(NamedSetting::settingName, CASE_INSENSITIVE_ORDER);

    State state();

    @Override
    default int compareTo(IndexConfigValidationRecord other) {
        return COMPARATOR.compare(this, other);
    }

    enum State {
        VALID,
        UNPROCESSED,
        UNRECOGNIZED_SETTING,
        MISSING_SETTING,
        INCORRECT_TYPE,
        INVALID_VALUE;

        public static final SortedSet<State> INVALID_STATES;

        static {
            final SortedSet<State> invalidStates = new TreeSet<>();
            for (final State state : values()) {
                if (state != VALID) {
                    invalidStates.add(state);
                }
            }
            INVALID_STATES = Collections.unmodifiableSortedSet(invalidStates);
        }
    }

    sealed interface RecordWithSetting extends IndexConfigValidationRecord, HasSetting {}

    sealed interface RecordWithValue extends RecordWithSetting {
        Object value();

        @SuppressWarnings("unchecked")
        default <T> T valueAs(Class<T> type) {
            return (T) value();
        }

        @SuppressWarnings("unchecked")
        default <T> T get() {
            return (T) value();
        }
    }

    sealed interface RecordWithStorable extends RecordWithValue {
        Value storable();
    }

    record Valid(IndexSetting setting, Object value, Value storable) implements RecordWithStorable {
        public Valid(RecordWithStorable hasStorable) {
            this(hasStorable.setting(), hasStorable.value(), hasStorable.storable());
        }

        public Valid(RecordWithStorable hasStorable, Object value) {
            this(hasStorable.setting(), value, hasStorable.storable());
        }

        public Valid(RecordWithValue hasValue, Value storable) {
            this(hasValue.setting(), hasValue.value(), storable);
        }

        public Valid(RecordWithSetting hasSetting, Object value, Value storable) {
            this(hasSetting.setting(), value, storable);
        }

        @Override
        public State state() {
            return State.VALID;
        }
    }

    @MarkerInterface
    sealed interface Invalid extends IndexConfigValidationRecord {}

    record Unprocessed(IndexSetting setting, AnyValue rawValue) implements RecordWithSetting, Invalid {
        @Override
        public State state() {
            return State.UNPROCESSED;
        }
    }

    record UnrecognizedSetting(String settingName) implements Invalid {
        public UnrecognizedSetting(NamedSetting namedSetting) {
            this(namedSetting.settingName());
        }

        @Override
        public State state() {
            return State.UNRECOGNIZED_SETTING;
        }
    }

    record MissingSetting(IndexSetting setting) implements RecordWithSetting, Invalid {
        public MissingSetting(RecordWithSetting hasSetting) {
            this(hasSetting.setting());
        }

        @Override
        public State state() {
            return State.MISSING_SETTING;
        }
    }

    record IncorrectType(IndexSetting setting, Object value, Class<?> targetType) implements RecordWithValue, Invalid {
        public IncorrectType(RecordWithValue hasValue, Class<?> targetType) {
            this(hasValue.setting(), hasValue.value(), targetType);
        }

        public IncorrectType(Unprocessed unprocessed, Class<?> targetType) {
            this(unprocessed.setting, unprocessed.rawValue, targetType);
        }

        @Override
        public State state() {
            return State.INCORRECT_TYPE;
        }

        public Class<?> providedType() {
            return value.getClass();
        }
    }

    record InvalidValue(IndexSetting setting, Object value, Object valid) implements RecordWithValue, Invalid {
        public InvalidValue(RecordWithValue hasValue, Object valid) {
            this(hasValue.setting(), hasValue.value(), valid);
        }

        public InvalidValue(RecordWithSetting hasSetting, Object value, Object valid) {
            this(hasSetting.setting(), value, valid);
        }

        @Override
        public State state() {
            return State.INVALID_VALUE;
        }
    }
}
