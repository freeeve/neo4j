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

import java.util.Objects;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.helpers.InclusiveRange;
import org.neo4j.internal.schema.IndexConfigValidationRecord.IncorrectType;
import org.neo4j.internal.schema.IndexConfigValidationRecord.InvalidValue;
import org.neo4j.internal.schema.IndexConfigValidationRecord.MissingSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Pending;
import org.neo4j.internal.schema.IndexConfigValidationRecord.RecordWithSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Unprocessed;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Valid;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.BooleanValue;
import org.neo4j.values.storable.IntegralValue;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.storable.Values;

public class IndexSettingExtractors {
    private IndexSettingExtractors() {}

    private abstract static class RawIndexSettingExtractor implements IndexSettingExtractor {
        private final IndexSetting setting;

        protected RawIndexSettingExtractor(IndexSetting setting) {
            this.setting = setting;
        }

        @Override
        public IndexSetting setting() {
            return setting;
        }

        protected RecordWithSetting extractRawValue(SettingsAccessor accessor) {
            if (!accessor.containsSetting(setting)) {
                return new MissingSetting(setting);
            }

            final AnyValue value = accessor.get(setting);
            if (value == null || value == Values.NO_VALUE) {
                return new MissingSetting(setting);
            }

            return new Unprocessed(setting, value);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" + settingName() + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(setting);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || this.getClass() != obj.getClass()) {
                return false;
            }
            final RawIndexSettingExtractor that = (RawIndexSettingExtractor) obj;
            return Objects.equals(this.setting, that.setting);
        }
    }

    public static final class BooleanExtractor extends RawIndexSettingExtractor {
        public static BooleanExtractor of(IndexSetting setting) {
            return new BooleanExtractor(setting);
        }

        private BooleanExtractor(IndexSetting setting) {
            super(setting);
        }

        @Override
        public RecordWithSetting extractForValidation(SettingsAccessor accessor) {
            final RecordWithSetting record = extractRawValue(accessor);
            if (!(record instanceof final Unprocessed unprocessed)) {
                return record;
            }
            if (!(unprocessed.rawValue() instanceof final BooleanValue booleanValue)) {
                return new IncorrectType(unprocessed, BooleanValue.class);
            }

            return new Pending(unprocessed, booleanValue.booleanValue(), booleanValue);
        }

        @Override
        public RecordWithSetting extractForAuthoritativeRead(SettingsAccessor accessor) {
            final RecordWithSetting record = extractRawValue(accessor);
            if (!(record instanceof final Unprocessed unprocessed)) {
                return record;
            }

            final BooleanValue booleanValue = (BooleanValue) unprocessed.rawValue();
            return new Valid(unprocessed, booleanValue.booleanValue(), booleanValue);
        }
    }

    public static final class IntegerExtractor extends RawIndexSettingExtractor {
        private static final InclusiveRange<Long> INTEGER_RANGE =
                new InclusiveRange<>((long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE);

        public static IntegerExtractor of(IndexSetting setting) {
            return new IntegerExtractor(setting);
        }

        private IntegerExtractor(IndexSetting setting) {
            super(setting);
        }

        @Override
        public RecordWithSetting extractForValidation(SettingsAccessor accessor) {
            final RecordWithSetting record = extractRawValue(accessor);
            if (!(record instanceof final Unprocessed unprocessed)) {
                return record;
            }
            if (!(unprocessed.rawValue() instanceof final IntegralValue integralValue)) {
                return new IncorrectType(unprocessed, IntegralValue.class);
            }
            // cannot go via IntegralValue::intValue as LongValue::intValue will throw
            final long longValue = integralValue.longValue();
            if (!INTEGER_RANGE.contains(longValue)) {
                return new InvalidValue(unprocessed, longValue, INTEGER_RANGE);
            }

            return new Pending(unprocessed, (int) longValue, integralValue);
        }

        @Override
        public RecordWithSetting extractForAuthoritativeRead(SettingsAccessor accessor) {
            final RecordWithSetting record = extractRawValue(accessor);
            if (!(record instanceof final Unprocessed unprocessed)) {
                return record;
            }

            final IntegralValue integralValue = (IntegralValue) unprocessed.rawValue();
            return new Valid(unprocessed, (int) integralValue.longValue(), integralValue);
        }
    }

    public static final class StringExtractor extends RawIndexSettingExtractor {
        public static StringExtractor of(IndexSetting setting) {
            return new StringExtractor(setting);
        }

        private StringExtractor(IndexSetting setting) {
            super(setting);
        }

        @Override
        public RecordWithSetting extractForValidation(SettingsAccessor accessor) {
            final RecordWithSetting record = extractRawValue(accessor);
            if (!(record instanceof final Unprocessed unprocessed)) {
                return record;
            }
            if (!(unprocessed.rawValue() instanceof final TextValue textValue)) {
                return new IncorrectType(unprocessed, TextValue.class);
            }

            return new Pending(unprocessed, textValue.stringValue(), textValue);
        }

        @Override
        public RecordWithSetting extractForAuthoritativeRead(SettingsAccessor accessor) {
            final RecordWithSetting record = extractRawValue(accessor);
            if (!(record instanceof final Unprocessed unprocessed)) {
                return record;
            }

            final TextValue textValue = (TextValue) unprocessed.rawValue();
            return new Valid(unprocessed, textValue.stringValue(), textValue);
        }
    }
}
