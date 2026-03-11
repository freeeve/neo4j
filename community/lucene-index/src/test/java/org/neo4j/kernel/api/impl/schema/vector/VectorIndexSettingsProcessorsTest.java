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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.internal.schema.IndexConfigUtils.HasSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.IncorrectType;
import org.neo4j.internal.schema.IndexConfigValidationRecord.MissingSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Pending;
import org.neo4j.internal.schema.IndexConfigValidationRecord.RecordWithSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.RecordWithStorable;
import org.neo4j.internal.schema.IndexConfigValidationRecord.RecordWithValue;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Valid;
import org.neo4j.internal.schema.KnownSettingRecords;
import org.neo4j.internal.schema.SingleIndexSettingProcessor;
import org.neo4j.kernel.api.impl.schema.vector.VectorIndexSettingsProcessors.SimpleQuantizationEnabledToTypeMigrator;
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
        void missingValue(boolean enabled) {
            final RecordWithSetting record =
                    records.upsert(new Pending(QUANTIZATION_ENABLED, enabled, Values.booleanValue(enabled)));

            final RecordWithSetting processedRecord = MIGRATOR.processForVerification(record);
            assertThat(processedRecord)
                    .asInstanceOf(type(MissingSetting.class))
                    .extracting(HasSetting::setting)
                    .isEqualTo(QUANTIZATION_TYPE);

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
}
