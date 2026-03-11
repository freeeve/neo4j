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

import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.QUANTIZATION_ENABLED;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.QUANTIZATION_TYPE;

import org.neo4j.internal.schema.IndexConfigValidationRecord.Pending;
import org.neo4j.internal.schema.IndexConfigValidationRecord.RecordWithSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Valid;
import org.neo4j.internal.schema.IndexSettingsProcessor.ValidatingIndexSettingsProcessor;
import org.neo4j.internal.schema.SingleIndexSettingMigrator;

class VectorIndexSettingsProcessors {
    private VectorIndexSettingsProcessors() {}

    static final class SimpleQuantizationEnabledToTypeMigrator extends SingleIndexSettingMigrator<Boolean>
            implements ValidatingIndexSettingsProcessor {
        private final VectorQuantizationType correspondingEnabledType;

        static SimpleQuantizationEnabledToTypeMigrator of(VectorQuantizationType enabled) {
            return new SimpleQuantizationEnabledToTypeMigrator(enabled);
        }

        private SimpleQuantizationEnabledToTypeMigrator(VectorQuantizationType correspondingEnabledType) {
            super(QUANTIZATION_ENABLED, Boolean.class, QUANTIZATION_TYPE);
            this.correspondingEnabledType = correspondingEnabledType;
        }

        @Override
        protected Object migrate(Boolean value) {
            return value ? correspondingEnabledType : VectorQuantizationType.NONE;
        }

        @Override
        public RecordWithSetting processForVerification(RecordWithSetting record) {
            final RecordWithSetting migratedRecord = super.processForVerification(record);
            return switch (migratedRecord) {
                case Pending pending when pending.setting().equals(toSetting) -> new Valid(pending);
                default -> migratedRecord;
            };
        }
    }
}
