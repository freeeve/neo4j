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
import org.neo4j.kernel.api.impl.schema.IndexConfigUtils.HasSetting;
import org.neo4j.kernel.api.impl.schema.IndexConfigUtils.NamedSetting;
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
        PENDING,
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

    sealed interface Invalid extends IndexConfigValidationRecord {}

    record Valid(IndexSetting setting, Object value, Value stored) implements HasSetting, IndexConfigValidationRecord {
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

    record Pending(IndexSetting setting, AnyValue rawValue, Object value) implements HasSetting, Invalid {
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

    record UnrecognizedSetting(String settingName) implements Invalid {
        @Override
        public State state() {
            return State.UNRECOGNIZED_SETTING;
        }
    }

    record MissingSetting(IndexSetting setting) implements HasSetting, Invalid {
        @Override
        public State state() {
            return State.MISSING_SETTING;
        }
    }

    record IncorrectType(IndexSetting setting, AnyValue rawValue, Class<?> targetType) implements HasSetting, Invalid {
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

    record InvalidValue(IndexSetting setting, AnyValue rawValue, Object value, Object valid)
            implements HasSetting, Invalid {
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
