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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.QUANTIZATION_ENABLED;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.QUANTIZATION_TYPE;

import java.util.EnumSet;
import java.util.Optional;
import java.util.function.BiPredicate;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.internal.schema.IndexConfigUtils.HasSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.IncorrectType;
import org.neo4j.internal.schema.IndexConfigValidationRecord.InvalidValue;
import org.neo4j.internal.schema.IndexConfigValidationRecord.MissingSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Pending;
import org.neo4j.internal.schema.IndexConfigValidationRecord.RecordWithSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.RecordWithStorable;
import org.neo4j.internal.schema.IndexConfigValidationRecord.RecordWithValue;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Valid;
import org.neo4j.internal.schema.IndexSettingsProcessor;
import org.neo4j.internal.schema.KnownSettingRecords;
import org.neo4j.internal.schema.SingleIndexSettingProcessor;
import org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.QuantizationTypeLookup;
import org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.SimpleQuantizationEnabledToTypeMigrator;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

class VectorIndexSettingsProcessorsTest {
    abstract static class TestBase {
        protected KnownSettingRecords records;

        @BeforeEach
        void setup() {
            records = new KnownSettingRecords();
        }
    }

    @Nested
    class SimpleQuantizationEnabledToTypeMigratorTest extends TestBase {
        private static final VectorQuantizationType CORRESPONDING_ENABLED_TYPE = VectorQuantizationType.SCALAR;
        private static final SingleIndexSettingProcessor MIGRATOR =
                SimpleQuantizationEnabledToTypeMigrator.of(CORRESPONDING_ENABLED_TYPE);

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void invalidValue(boolean enabled) {
            final RecordWithSetting record =
                    records.upsert(new Pending(QUANTIZATION_ENABLED, enabled, Values.booleanValue(enabled)));

            final RecordWithSetting processedRecord = MIGRATOR.processForVerification(record);
            assertThat(processedRecord)
                    .asInstanceOf(type(InvalidValue.class))
                    .extracting(HasSetting::setting, InvalidValue::value, InvalidValue::valid)
                    .containsExactly(QUANTIZATION_TYPE, null, VectorQuantizationType.class);

            MIGRATOR.updateForVerification(records);
            assertThat(records.get(QUANTIZATION_ENABLED)).isSameAs(record);
            assertThat(records.get(QUANTIZATION_TYPE)).isEqualTo(processedRecord);
        }

        @ParameterizedTest
        @ValueSource(strings = {"false", "true"})
        void incorrectType(String value) {
            final RecordWithSetting record =
                    records.upsert(new Valid(QUANTIZATION_ENABLED, value, Values.utf8Value(value)));

            final RecordWithSetting processedRecord = MIGRATOR.processForVerification(record);
            assertThat(processedRecord)
                    .asInstanceOf(type(IncorrectType.class))
                    .extracting(HasSetting::setting, IncorrectType::targetType)
                    .containsExactly(QUANTIZATION_TYPE, Boolean.class);

            MIGRATOR.updateForVerification(records);
            assertThat(records.get(QUANTIZATION_ENABLED)).isSameAs(record);
            assertThat(records.get(QUANTIZATION_TYPE)).isEqualTo(processedRecord);
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void validForVerification(boolean enabled) {
            final RecordWithSetting record =
                    records.upsert(new Valid(QUANTIZATION_ENABLED, enabled, Values.booleanValue(enabled)));
            final VectorQuantizationType processedValue =
                    enabled ? CORRESPONDING_ENABLED_TYPE : VectorQuantizationType.NONE;

            final RecordWithSetting processedRecord = MIGRATOR.processForVerification(record);
            assertThat(processedRecord)
                    .asInstanceOf(type(Valid.class))
                    .extracting(HasSetting::setting, RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(QUANTIZATION_TYPE, processedValue, null);

            MIGRATOR.updateForVerification(records);
            assertThat(records.get(QUANTIZATION_ENABLED)).isSameAs(record);
            assertThat(records.get(QUANTIZATION_TYPE)).isEqualTo(processedRecord);
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void validForAuthoritativeRead(boolean enabled) {
            final RecordWithSetting record =
                    records.upsert(new Valid(QUANTIZATION_ENABLED, enabled, Values.booleanValue(enabled)));
            final VectorQuantizationType processedValue =
                    enabled ? CORRESPONDING_ENABLED_TYPE : VectorQuantizationType.NONE;

            final RecordWithSetting processedRecord = MIGRATOR.processForAuthoritativeRead(record);
            assertThat(processedRecord)
                    .asInstanceOf(type(Valid.class))
                    .extracting(HasSetting::setting, RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(QUANTIZATION_TYPE, processedValue, null);

            MIGRATOR.updateForAuthoritativeRead(records);
            assertThat(records.get(QUANTIZATION_ENABLED)).isSameAs(record);
            assertThat(records.get(QUANTIZATION_TYPE)).isEqualTo(processedRecord);
        }
    }

    @Nested
    class QuantizationTypeLookupTest extends TestBase {
        private static final IndexSettingsProcessor LOOKUP =
                QuantizationTypeLookup.of(EnumSet.allOf(VectorQuantizationType.class));

        // ===================================
        //  Invalid tuples of (enabled, type)
        // ===================================

        @ParameterizedTest
        @EnumSource
        void invalidValueViaEnabled(VectorQuantizationType type) {
            final Value storable = Values.utf8Value(type.name());
            final RecordWithSetting enabledRecord =
                    records.upsert(new MissingSetting(VectorIndexConfigUtils.QUANTIZATION_ENABLED));
            records.upsert(new Pending(QUANTIZATION_TYPE, type.name(), storable));
            LOOKUP.updateForVerification(records);

            assertThat(records.get(QUANTIZATION_ENABLED)).isSameAs(enabledRecord);

            assertThat(records.get(QUANTIZATION_TYPE))
                    .asInstanceOf(type(Valid.class))
                    .extracting(RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(type, storable);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(booleans = {false, true})
        void invalidValueViaType(Boolean enabled) {
            final Optional<Boolean> optionalEnabled = Optional.ofNullable(enabled);
            final Value storable = optionalEnabled.map(Values::of).orElse(Values.NO_VALUE);
            records.upsert(new Pending(QUANTIZATION_ENABLED, optionalEnabled, storable));
            final RecordWithSetting typeRecord =
                    records.upsert(new MissingSetting(VectorIndexConfigUtils.QUANTIZATION_TYPE));
            LOOKUP.updateForVerification(records);

            assertThat(records.get(QUANTIZATION_ENABLED))
                    .asInstanceOf(type(Valid.class))
                    .extracting(RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(optionalEnabled, storable);

            assertThat(records.get(QUANTIZATION_TYPE)).isSameAs(typeRecord);
        }

        @Test
        void invalidValueViaBoth() {
            final RecordWithSetting enabledRecord =
                    records.upsert(new MissingSetting(VectorIndexConfigUtils.QUANTIZATION_ENABLED));
            final RecordWithSetting typeRecord =
                    records.upsert(new MissingSetting(VectorIndexConfigUtils.QUANTIZATION_TYPE));
            LOOKUP.updateForVerification(records);

            assertThat(records.get(QUANTIZATION_ENABLED)).isSameAs(enabledRecord);
            assertThat(records.get(QUANTIZATION_TYPE)).isSameAs(typeRecord);
        }

        @Test
        void conflict() {
            final boolean enabled = true;
            final Optional<Boolean> optionalEnabled = Optional.of(enabled);
            final VectorQuantizationType type = VectorQuantizationType.NONE;
            final Value storable = Values.utf8Value(type.name());
            records.upsert(new Pending(QUANTIZATION_ENABLED, optionalEnabled, Values.booleanValue(enabled)));
            records.upsert(new Pending(QUANTIZATION_TYPE, type.name(), storable));
            LOOKUP.updateForVerification(records);

            final ObjectAssert<InvalidValue> invalidEnabledValueAssert =
                    assertThat(records.get(QUANTIZATION_ENABLED)).asInstanceOf(type(InvalidValue.class));
            invalidEnabledValueAssert.extracting(RecordWithValue::value).isEqualTo(optionalEnabled);
            invalidEnabledValueAssert.extracting(InvalidValue::valid).isInstanceOf(BiPredicate.class);

            final ObjectAssert<InvalidValue> invalidTypeValueAssert =
                    assertThat(records.get(QUANTIZATION_TYPE)).asInstanceOf(type(InvalidValue.class));
            invalidTypeValueAssert.extracting(RecordWithValue::value).isEqualTo(type);
            invalidTypeValueAssert.extracting(InvalidValue::valid).isInstanceOf(BiPredicate.class);
        }

        @ParameterizedTest
        @EnumSource(mode = Mode.EXCLUDE, names = "NONE")
        void conflict(VectorQuantizationType type) {
            final boolean enabled = false;
            final Optional<Boolean> optionalEnabled = Optional.of(enabled);
            final Value storable = Values.utf8Value(type.name());
            records.upsert(new Pending(QUANTIZATION_ENABLED, optionalEnabled, Values.booleanValue(enabled)));
            records.upsert(new Pending(QUANTIZATION_TYPE, type.name(), storable));
            LOOKUP.updateForVerification(records);

            final ObjectAssert<InvalidValue> invalidEnabledValueAssert =
                    assertThat(records.get(QUANTIZATION_ENABLED)).asInstanceOf(type(InvalidValue.class));
            invalidEnabledValueAssert.extracting(RecordWithValue::value).isEqualTo(optionalEnabled);
            invalidEnabledValueAssert.extracting(InvalidValue::valid).isInstanceOf(BiPredicate.class);

            final ObjectAssert<InvalidValue> invalidTypeValueAssert =
                    assertThat(records.get(QUANTIZATION_TYPE)).asInstanceOf(type(InvalidValue.class));
            invalidTypeValueAssert.extracting(RecordWithValue::value).isEqualTo(type);
            invalidTypeValueAssert.extracting(InvalidValue::valid).isInstanceOf(BiPredicate.class);
        }

        // =================================
        //  Valid tuples of (enabled, type)
        // =================================

        @ParameterizedTest
        @EnumSource
        void missingEnabledForVerification(VectorQuantizationType type) {
            final Value storable = Values.utf8Value(type.name());
            records.upsert(new Pending(QUANTIZATION_ENABLED, Optional.empty(), Values.NO_VALUE));
            records.upsert(new Pending(QUANTIZATION_TYPE, type.name(), storable));
            LOOKUP.updateForVerification(records);

            assertThat(records.get(QUANTIZATION_ENABLED))
                    .asInstanceOf(type(Valid.class))
                    .extracting(RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(Optional.empty(), Values.NO_VALUE);

            assertThat(records.get(QUANTIZATION_TYPE))
                    .asInstanceOf(type(Valid.class))
                    .extracting(RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(type, storable);
        }

        @ParameterizedTest
        @EnumSource
        void missingEnabledForAuthoritativeRead(VectorQuantizationType type) {
            final Value storable = Values.utf8Value(type.name());
            final Valid enabledRecord =
                    records.upsert(new Valid(QUANTIZATION_ENABLED, Optional.empty(), Values.NO_VALUE));
            records.upsert(new Valid(QUANTIZATION_TYPE, type.name(), storable));
            LOOKUP.updateForAuthoritativeRead(records);

            assertThat(records.get(QUANTIZATION_ENABLED)).isEqualTo(enabledRecord);

            assertThat(records.get(QUANTIZATION_TYPE))
                    .asInstanceOf(type(Valid.class))
                    .extracting(RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(type, storable);
        }

        @Test
        void disabledForVerification() {
            final boolean enabled = false;
            final Optional<Boolean> optionalEnabled = Optional.of(enabled);
            final VectorQuantizationType type = VectorQuantizationType.NONE;
            final Value storable = Values.utf8Value(type.name());
            records.upsert(new Pending(QUANTIZATION_ENABLED, optionalEnabled, Values.booleanValue(enabled)));
            records.upsert(new Pending(QUANTIZATION_TYPE, type.name(), storable));
            LOOKUP.updateForVerification(records);

            assertThat(records.get(QUANTIZATION_ENABLED))
                    .asInstanceOf(type(Valid.class))
                    .extracting(RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(optionalEnabled, Values.NO_VALUE);

            assertThat(records.get(QUANTIZATION_TYPE))
                    .asInstanceOf(type(Valid.class))
                    .extracting(RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(type, storable);
        }

        @Test
        void disabledForAuthoritativeRead() {
            final boolean enabled = false;
            final Optional<Boolean> optionalEnabled = Optional.of(enabled);
            final VectorQuantizationType type = VectorQuantizationType.NONE;
            final Value storable = Values.utf8Value(type.name());
            records.upsert(new Valid(QUANTIZATION_ENABLED, optionalEnabled, Values.booleanValue(enabled)));
            records.upsert(new Valid(QUANTIZATION_TYPE, type.name(), storable));
            LOOKUP.updateForAuthoritativeRead(records);

            assertThat(records.get(QUANTIZATION_ENABLED))
                    .asInstanceOf(type(Valid.class))
                    .extracting(RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(optionalEnabled, Values.NO_VALUE);

            assertThat(records.get(QUANTIZATION_TYPE))
                    .asInstanceOf(type(Valid.class))
                    .extracting(RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(type, storable);
        }

        @ParameterizedTest
        @EnumSource(mode = Mode.EXCLUDE, names = "NONE")
        void enabledForVerification(VectorQuantizationType type) {
            final boolean enabled = true;
            final Optional<Boolean> optionalEnabled = Optional.of(enabled);
            final Value storable = Values.utf8Value(type.name());
            records.upsert(new Pending(QUANTIZATION_ENABLED, optionalEnabled, Values.booleanValue(enabled)));
            records.upsert(new Pending(QUANTIZATION_TYPE, type.name(), storable));
            LOOKUP.updateForVerification(records);

            assertThat(records.get(QUANTIZATION_ENABLED))
                    .asInstanceOf(type(Valid.class))
                    .extracting(RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(optionalEnabled, Values.NO_VALUE);

            assertThat(records.get(QUANTIZATION_TYPE))
                    .asInstanceOf(type(Valid.class))
                    .extracting(RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(type, storable);
        }

        @ParameterizedTest
        @EnumSource(mode = Mode.EXCLUDE, names = "NONE")
        void enabledForAuthoritativeRead(VectorQuantizationType type) {
            final boolean enabled = true;
            final Optional<Boolean> optionalEnabled = Optional.of(enabled);
            final Value storable = Values.utf8Value(type.name());
            records.upsert(new Valid(QUANTIZATION_ENABLED, optionalEnabled, Values.booleanValue(enabled)));
            records.upsert(new Valid(QUANTIZATION_TYPE, type.name(), storable));
            LOOKUP.updateForAuthoritativeRead(records);

            assertThat(records.get(QUANTIZATION_ENABLED))
                    .asInstanceOf(type(Valid.class))
                    .extracting(RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(optionalEnabled, Values.NO_VALUE);

            assertThat(records.get(QUANTIZATION_TYPE))
                    .asInstanceOf(type(Valid.class))
                    .extracting(RecordWithValue::value, RecordWithStorable::storable)
                    .containsExactly(type, storable);
        }
    }
}
