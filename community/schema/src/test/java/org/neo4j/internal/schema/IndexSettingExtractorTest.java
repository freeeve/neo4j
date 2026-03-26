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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.BOOLEAN;
import static org.assertj.core.api.InstanceOfAssertFactories.INTEGER;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.neo4j.internal.schema.IndexSettingTestUtils.settings;

import java.util.function.Supplier;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.helpers.InclusiveRange;
import org.neo4j.internal.schema.IndexSettingExtractors.BooleanExtractor;
import org.neo4j.internal.schema.IndexSettingExtractors.IntegerExtractor;
import org.neo4j.internal.schema.IndexSettingExtractors.StringExtractor;
import org.neo4j.internal.schema.IndexSettingRecord.IncorrectType;
import org.neo4j.internal.schema.IndexSettingRecord.InvalidValue;
import org.neo4j.internal.schema.IndexSettingRecord.MissingSetting;
import org.neo4j.internal.schema.IndexSettingRecord.Pending;
import org.neo4j.internal.schema.IndexSettingRecord.RecordWithStorable;
import org.neo4j.internal.schema.IndexSettingRecord.RecordWithValue;
import org.neo4j.internal.schema.IndexSettingRecord.Valid;
import org.neo4j.internal.schema.IndexSettingTestUtils.TestIndexSetting;
import org.neo4j.values.storable.BooleanValue;
import org.neo4j.values.storable.IntValue;
import org.neo4j.values.storable.IntegralValue;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.storable.Values;

class IndexSettingExtractorTest {
    abstract static class ExtractorTestBase {
        protected final IndexSetting setting;
        protected final IndexSettingExtractor extractor;

        protected ExtractorTestBase(IndexSetting setting, ExtractorConstructor constructor) {
            this.setting = setting;
            this.extractor = constructor.constructFor(setting);
        }

        @Test
        void correctSetting() {
            assertThat(extractor).extracting(IndexSettingExtractor::setting).isEqualTo(setting);
        }

        @Test
        void missingSetting() {
            final SettingsAccessor accessor = settings();
            assertThat(accessor.settings()).isEmpty();
            extractForValidationAndAssertRecord(accessor, MissingSetting.class);
            extractForAuthoritativeReadAndAssertRecord(accessor, MissingSetting.class);
        }

        @Test
        void missingValue() {
            final SettingsAccessor accessor = settingValue(null);
            assertThat(accessor.containsSetting(setting)).isTrue();
            assertThat(accessor.get(setting)).isEqualTo(Values.NO_VALUE);
            extractForValidationAndAssertRecord(accessor, MissingSetting.class);
            extractForAuthoritativeReadAndAssertRecord(accessor, MissingSetting.class);
        }

        protected SettingsAccessor settingValue(Object value) {
            return settings(setting, value);
        }

        protected <RECORD extends IndexSettingRecord> ObjectAssert<RECORD> extractForValidationAndAssertRecord(
                SettingsAccessor accessor, Class<RECORD> type) {
            return assertThat(extractor.extractForValidation(accessor)).asInstanceOf(type(type));
        }

        protected ObjectAssert<Valid> extractForAuthoritativeReadAndAssertRecord(SettingsAccessor accessor) {
            return extractForAuthoritativeReadAndAssertRecord(accessor, Valid.class);
        }

        protected <RECORD extends IndexSettingRecord> ObjectAssert<RECORD> extractForAuthoritativeReadAndAssertRecord(
                SettingsAccessor accessor, Class<RECORD> type) {
            return assertThat(extractor.extractForAuthoritativeRead(accessor)).asInstanceOf(type(type));
        }

        protected interface ExtractorConstructor {
            IndexSettingExtractor constructFor(IndexSetting setting);
        }
    }

    @Nested
    class BooleanExtractorTest extends ExtractorTestBase {
        BooleanExtractorTest() {
            super(TestIndexSetting.BOOLEAN, BooleanExtractor::of);
        }

        @Test
        void incorrectType() {
            final SettingsAccessor accessor = settingValue(42);

            extractForValidationAndAssertRecord(accessor, IncorrectType.class)
                    .extracting(IncorrectType::targetType)
                    .isEqualTo(BooleanValue.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void extracted(boolean value) {
            final BooleanValue booleanValue = Values.booleanValue(value);
            final SettingsAccessor accessor = settingValue(value);

            final ObjectAssert<Pending> validationAssert = extractForValidationAndAssertRecord(accessor, Pending.class);
            validationAssert.extracting(RecordWithValue::value, BOOLEAN).isEqualTo(value);
            validationAssert
                    .extracting(RecordWithStorable::storable, type(BooleanValue.class))
                    .isEqualTo(booleanValue);

            final ObjectAssert<Valid> authoritativeReadAssert = extractForAuthoritativeReadAndAssertRecord(accessor);
            authoritativeReadAssert.extracting(RecordWithValue::value, BOOLEAN).isEqualTo(value);
            authoritativeReadAssert
                    .extracting(RecordWithStorable::storable, type(BooleanValue.class))
                    .isEqualTo(booleanValue);
        }
    }

    @Nested
    class IntegerExtractorTest extends ExtractorTestBase {
        IntegerExtractorTest() {
            super(TestIndexSetting.INTEGER, IntegerExtractor::of);
        }

        @Test
        void incorrectType() {
            final SettingsAccessor accessor = settingValue("foo");

            extractForValidationAndAssertRecord(accessor, IncorrectType.class)
                    .extracting(IncorrectType::targetType)
                    .isEqualTo(IntegralValue.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {Long.MIN_VALUE, Integer.MIN_VALUE - 1L, Integer.MAX_VALUE + 1L, Long.MAX_VALUE})
        void invalidValue(long value) {
            final SettingsAccessor accessor = settingValue(value);

            final ObjectAssert<InvalidValue> invalidValueAssert =
                    extractForValidationAndAssertRecord(accessor, InvalidValue.class);
            invalidValueAssert.extracting(RecordWithValue::value).isEqualTo(value);
            invalidValueAssert
                    .extracting(InvalidValue::requirement)
                    .extracting(Supplier::get, type(InclusiveRange.class))
                    .extracting(InclusiveRange::min, InclusiveRange::max)
                    .containsExactly((long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE);
        }

        @ParameterizedTest
        @ValueSource(bytes = {Byte.MIN_VALUE, 0, Byte.MAX_VALUE})
        @ValueSource(shorts = {Short.MIN_VALUE, Byte.MIN_VALUE, 0, Byte.MAX_VALUE, Short.MAX_VALUE})
        @ValueSource(
                ints = {
                    Integer.MIN_VALUE,
                    Short.MIN_VALUE,
                    Byte.MIN_VALUE,
                    0,
                    Byte.MAX_VALUE,
                    Short.MAX_VALUE,
                    Integer.MAX_VALUE
                })
        @ValueSource(
                longs = {
                    Integer.MIN_VALUE,
                    Short.MIN_VALUE,
                    Byte.MIN_VALUE,
                    0,
                    Byte.MAX_VALUE,
                    Short.MAX_VALUE,
                    Integer.MAX_VALUE
                })
        void extracted(Number value) {
            final int integer = value.intValue();
            final IntValue storable = Values.intValue(integer);
            final SettingsAccessor accessor = settingValue(value);

            final ObjectAssert<Pending> validationAssert = extractForValidationAndAssertRecord(accessor, Pending.class);
            validationAssert.extracting(RecordWithValue::value, INTEGER).isEqualTo(integer);
            validationAssert
                    .as("should specifically be an % on validation", IntValue.class.getSimpleName())
                    .extracting(RecordWithStorable::storable, type(IntValue.class))
                    .isEqualTo(storable);

            final ObjectAssert<Valid> authoritativeReadAssert = extractForAuthoritativeReadAndAssertRecord(accessor);
            authoritativeReadAssert.extracting(RecordWithValue::value, INTEGER).isEqualTo(integer);
            authoritativeReadAssert
                    .extracting(RecordWithStorable::storable, type(IntegralValue.class))
                    .isEqualTo(storable);
        }
    }

    @Nested
    class StringExtractorTest extends ExtractorTestBase {
        StringExtractorTest() {
            super(TestIndexSetting.STRING, StringExtractor::of);
        }

        @Test
        void incorrectType() {
            final SettingsAccessor accessor = settingValue(42);

            extractForValidationAndAssertRecord(accessor, IncorrectType.class)
                    .extracting(IncorrectType::targetType)
                    .isEqualTo(TextValue.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"foo", "bar", "baz"})
        void extracted(String value) {
            final TextValue textValue = Values.stringValue(value);
            final SettingsAccessor accessor = settingValue(value);

            final ObjectAssert<Pending> validationAssert = extractForValidationAndAssertRecord(accessor, Pending.class);
            validationAssert.extracting(RecordWithValue::value, STRING).isEqualTo(value);
            validationAssert
                    .extracting(RecordWithStorable::storable, type(TextValue.class))
                    .isEqualTo(textValue);

            final ObjectAssert<Valid> authoritativeReadAssert = extractForAuthoritativeReadAndAssertRecord(accessor);
            authoritativeReadAssert.extracting(RecordWithValue::value, STRING).isEqualTo(value);
            authoritativeReadAssert
                    .extracting(RecordWithStorable::storable, type(TextValue.class))
                    .isEqualTo(textValue);
        }
    }
}
