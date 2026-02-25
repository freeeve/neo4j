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
package org.neo4j.kernel.api.impl.schema.vector;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.helpers.InclusiveRange;
import org.neo4j.internal.schema.IndexConfigValidationRecords.IncorrectType;
import org.neo4j.internal.schema.IndexConfigValidationRecords.IndexConfigValidationRecord;
import org.neo4j.internal.schema.IndexConfigValidationRecords.InvalidValue;
import org.neo4j.internal.schema.IndexConfigValidationRecords.MissingSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecords.Pending;
import org.neo4j.internal.schema.IndexConfigValidationRecords.UnrecognizedSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecords.Valid;
import org.neo4j.internal.schema.SettingsAccessor;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.BooleanValue;
import org.neo4j.values.storable.IntegralValue;
import org.neo4j.values.storable.NoValue;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

class IndexSettingValidators {
    abstract static class IndexSettingValidator<VALUE extends Value, TYPE> {
        protected final IndexSetting setting;
        protected final TYPE createDefault;
        protected final TYPE readDefault;

        protected IndexSettingValidator(IndexSetting setting) {
            this(setting, null);
        }

        protected IndexSettingValidator(IndexSetting setting, TYPE defaultValue) {
            this(setting, defaultValue, defaultValue);
        }

        protected IndexSettingValidator(IndexSetting setting, TYPE readDefault, TYPE createDefault) {
            this.setting = setting;
            this.readDefault = readDefault;
            this.createDefault = createDefault;
        }

        abstract TYPE map(VALUE value);

        abstract Value map(TYPE value);

        abstract IndexConfigValidationRecord validate(SettingsAccessor accessor);

        protected IndexConfigValidationRecord extractOrDefault(SettingsAccessor accessor) {
            if (accessor.containsSetting(setting)) {
                return new Pending(setting, accessor.get(setting));
            }
            if (createDefault == null) {
                return new MissingSetting(setting);
            }
            return new Valid(setting, createDefault, map(createDefault));
        }

        protected Valid trustIsValid(SettingsAccessor accessor) {
            final TYPE value = accessor.containsSetting(setting) ? map((VALUE) accessor.get(setting)) : readDefault;
            return new Valid(setting, value, map(value));
        }

        protected IndexSetting setting() {
            return setting;
        }
    }

    static class ReadDefaultOnly<TYPE> extends IndexSettingValidator<NoValue, TYPE> {
        protected ReadDefaultOnly(IndexSetting setting, TYPE readDefault) {
            super(setting, readDefault, null);
        }

        @Override
        TYPE map(NoValue value) {
            return readDefault;
        }

        @Override
        NoValue map(TYPE value) {
            return NoValue.NO_VALUE;
        }

        @Override
        IndexConfigValidationRecord validate(SettingsAccessor accessor) {
            if (accessor.containsSetting(setting)) {
                return new UnrecognizedSetting(setting.getSettingName());
            }
            return new Valid(setting, readDefault, map(readDefault));
        }
    }

    static final class BooleanValidator extends IndexSettingValidator<BooleanValue, Boolean> {
        private final Set<Boolean> booleans;

        BooleanValidator(
                IndexSetting setting, Set<Boolean> supportedBooleans, boolean readDefault, boolean writeDefault) {
            super(setting, readDefault, writeDefault);
            this.booleans = Collections.unmodifiableSet(supportedBooleans);
            assert this.booleans.contains(readDefault) && this.booleans.contains(writeDefault);
        }

        @Override
        Boolean map(BooleanValue value) {
            return value.booleanValue();
        }

        @Override
        Value map(Boolean value) {
            return Values.booleanValue(value);
        }

        @Override
        public IndexConfigValidationRecord validate(SettingsAccessor accessor) {
            final IndexConfigValidationRecord record = extractOrDefault(accessor);
            if (!(record instanceof final Pending pending)) {
                return record;
            }

            final AnyValue rawValue = pending.rawValue();
            if (rawValue == Values.NO_VALUE) {
                return new InvalidValue(pending, null, booleans);
            }
            if (!(rawValue instanceof final BooleanValue booleanValue)) {
                return new IncorrectType(pending, BooleanValue.class);
            }

            final Boolean quantization = map(booleanValue);
            return quantization == null
                    ? new InvalidValue(pending, booleans)
                    : new Valid(setting, quantization, map(quantization));
        }
    }

    static final class IntegerValidator extends IndexSettingValidator<IntegralValue, Integer> {
        private final InclusiveRange<Integer> supportedRange;

        IntegerValidator(IndexSetting setting, InclusiveRange<Integer> supportedRange, Integer defaultValue) {
            super(setting, defaultValue);
            this.supportedRange = supportedRange;
            assert defaultValue == null || this.supportedRange.contains(defaultValue);
        }

        @Override
        Integer map(IntegralValue value) {
            return (int) value.longValue();
        }

        @Override
        Value map(Integer value) {
            return Values.intValue(value);
        }

        @Override
        IndexConfigValidationRecord validate(SettingsAccessor accessor) {
            final IndexConfigValidationRecord record = extractOrDefault(accessor);
            if (!(record instanceof final Pending pending)) {
                return record;
            }

            final AnyValue rawValue = pending.rawValue();
            if (rawValue == Values.NO_VALUE) {
                return new InvalidValue(pending, null, supportedRange);
            }
            if (!(rawValue instanceof final IntegralValue integralValue)) {
                return new IncorrectType(pending, IntegralValue.class);
            }

            final Integer value = map(integralValue);
            return supportedRange.contains(value)
                    ? new Valid(setting, value, map(value))
                    : new InvalidValue(pending, value, supportedRange);
        }
    }

    static final class OptionalIntValidator extends IndexSettingValidator<IntegralValue, OptionalInt> {
        private final InclusiveRange<Integer> supportedRange;

        OptionalIntValidator(IndexSetting setting, InclusiveRange<Integer> supportedRange, OptionalInt defaultValue) {
            super(setting, defaultValue);
            this.supportedRange = supportedRange;
            assert defaultValue == null
                    || defaultValue.isEmpty()
                    || this.supportedRange.contains(defaultValue.getAsInt());
        }

        @Override
        OptionalInt map(IntegralValue dimensions) {
            return OptionalInt.of((int) dimensions.longValue());
        }

        @Override
        Value map(OptionalInt dimensions) {
            return dimensions.isPresent() ? Values.intValue(dimensions.getAsInt()) : NoValue.NO_VALUE;
        }

        @Override
        IndexConfigValidationRecord validate(SettingsAccessor accessor) {
            final IndexConfigValidationRecord record = extractOrDefault(accessor);
            if (!(record instanceof final Pending pending)) {
                return record;
            }

            final AnyValue rawValue = pending.rawValue();
            if (rawValue == Values.NO_VALUE) {
                return new InvalidValue(pending, null, supportedRange);
            }
            if (!(rawValue instanceof final IntegralValue integralValue)) {
                return new IncorrectType(pending, IntegralValue.class);
            }

            final OptionalInt dimensions = map(integralValue);
            return supportedRange.contains(dimensions.orElseThrow(() ->
                            new IllegalStateException("'%s' should not be empty at this point.".formatted(setting))))
                    ? new Valid(setting, dimensions, map(dimensions))
                    : new InvalidValue(pending, dimensions, supportedRange);
        }
    }

    abstract static class StringLookupValidator<TYPE> extends IndexSettingValidator<TextValue, TYPE> {
        private final Map<String, TYPE> lookup;

        StringLookupValidator(IndexSetting setting, Map<String, TYPE> supported, TYPE defaultValue) {
            super(setting, defaultValue);
            this.lookup = Collections.unmodifiableMap(supported);
            assert defaultValue == null || this.lookup.containsValue(defaultValue);
        }

        protected String key(TextValue textValue) {
            return textValue.stringValue().toUpperCase(Locale.ROOT);
        }

        protected abstract String key(TYPE type);

        @Override
        TYPE map(TextValue textValue) {
            return lookup.get(key(textValue));
        }

        @Override
        Value map(TYPE type) {
            return Values.stringValue(key(type));
        }

        @Override
        IndexConfigValidationRecord validate(SettingsAccessor accessor) {
            final IndexConfigValidationRecord record = extractOrDefault(accessor);
            if (!(record instanceof final Pending pending)) {
                return record;
            }

            final AnyValue rawValue = pending.rawValue();
            if (rawValue == Values.NO_VALUE) {
                return new InvalidValue(pending, null, lookup.keySet());
            }
            if (!(rawValue instanceof final TextValue textValue)) {
                return new IncorrectType(pending, TextValue.class);
            }

            final TYPE type = map(textValue);
            return type == null ? new InvalidValue(pending, lookup.keySet()) : new Valid(setting, type, map(type));
        }
    }
}
