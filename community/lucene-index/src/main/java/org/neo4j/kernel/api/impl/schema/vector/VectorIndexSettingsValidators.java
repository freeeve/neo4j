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

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static org.neo4j.internal.schema.IndexConfigUtils.INDEX_SETTING_COMPARATOR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.UnrecognizedSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Valid;
import org.neo4j.internal.schema.IndexConfigValidationRecords;
import org.neo4j.internal.schema.IndexSettingsValidator;
import org.neo4j.internal.schema.MutableIndexConfigValidationRecords;
import org.neo4j.internal.schema.SettingsAccessor;
import org.neo4j.internal.schema.TypedIndexSettingsValidator;
import org.neo4j.kernel.api.impl.schema.vector.LegacyIndexSettingValidators.LegacyIndexSettingValidator;
import org.neo4j.kernel.api.impl.schema.vector.LegacyIndexSettingValidators.LegacyReadDefaultOnly;
import org.neo4j.values.storable.Value;

class VectorIndexSettingsValidators {
    private VectorIndexSettingsValidators() {}

    public static class LegacyVersionedValidator extends TypedIndexSettingsValidator<VectorIndexConfig> {
        private final VectorIndexVersion version;

        @SafeVarargs
        LegacyVersionedValidator(
                VectorIndexVersion version, LegacyIndexSettingValidator<? extends Value, ?>... validators) {
            super(version.descriptor(), new LegacyValidator(validators));
            this.version = version;
        }

        @Override
        public VectorIndexConfig toTypedConfig(Iterable<Valid> records) {
            return new VectorIndexConfig(version, acceptedSettings(), records);
        }
    }

    private static class LegacyValidator implements IndexSettingsValidator {
        private final SortedSet<LegacyIndexSettingValidator<? extends Value, ?>> validators;
        private final SortedSet<IndexSetting> acceptedSettings;
        private final SortedSet<String> handledSettingNames;

        @SafeVarargs
        LegacyValidator(LegacyIndexSettingValidator<? extends Value, ?>... validators) {
            // check we've not passed multiple validators for the same setting
            final Set<String> seenSettingNames = new HashSet<>(validators.length);
            final List<LegacyIndexSettingValidator<? extends Value, ?>> checkedValidators =
                    new ArrayList<>(validators.length);
            for (final LegacyIndexSettingValidator<? extends Value, ?> validator : validators) {
                if (!seenSettingNames.add(validator.setting().getSettingName())) {
                    throw new IllegalStateException("Expected a single %s to be provided for '%s', multiple given."
                            .formatted(
                                    LegacyIndexSettingValidator.class.getSimpleName(),
                                    validator.setting().getSettingName()));
                }
                checkedValidators.add(validator);
            }
            final SortedSet<LegacyIndexSettingValidator<? extends Value, ?>> sortedValidators = new TreeSet<>(
                    Comparator.comparing(validator -> validator.setting().getSettingName(), CASE_INSENSITIVE_ORDER));
            sortedValidators.addAll(checkedValidators);
            this.validators = Collections.unmodifiableSortedSet(sortedValidators);

            final Set<IndexSetting> handledSettings = new HashSet<>(this.validators.size());
            final SortedSet<String> handledSettingNames = new TreeSet<>(CASE_INSENSITIVE_ORDER);
            for (final LegacyIndexSettingValidator<? extends Value, ?> validator : this.validators) {
                final IndexSetting setting = validator.setting();
                handledSettings.add(setting);
                handledSettingNames.add(setting.getSettingName());
            }
            this.handledSettingNames = Collections.unmodifiableSortedSet(handledSettingNames);

            final Set<IndexSetting> readDefaultOnlySettings = new HashSet<>();
            for (final LegacyIndexSettingValidator<? extends Value, ?> validator : this.validators) {
                if (validator instanceof final LegacyReadDefaultOnly<?> readDefaultOnly) {
                    readDefaultOnlySettings.add(readDefaultOnly.setting());
                }
            }

            final SortedSet<IndexSetting> acceptedSettings = new TreeSet<>(INDEX_SETTING_COMPARATOR);
            for (final IndexSetting setting : handledSettings) {
                if (!readDefaultOnlySettings.contains(setting)) {
                    acceptedSettings.add(setting);
                }
            }
            this.acceptedSettings = Collections.unmodifiableSortedSet(acceptedSettings);
        }

        @Override
        public IndexConfigValidationRecords validate(SettingsAccessor accessor) {
            Set<String> settingNames = accessor.settingNames();
            final MutableIndexConfigValidationRecords validationRecords = new MutableIndexConfigValidationRecords();
            for (final String settingName : settingNames) {
                if (!handledSettingNames.contains(settingName)) {
                    validationRecords.with(new UnrecognizedSetting(settingName));
                }
            }

            for (final LegacyIndexSettingValidator<? extends Value, ?> validator : validators) {
                validationRecords.with(validator.validate(accessor));
            }
            return validationRecords.toUnmodifiable();
        }

        @Override
        public Iterable<Valid> interpretAuthoritative(SettingsAccessor accessor) {
            final List<Valid> records = new ArrayList<>(validators.size());
            for (final LegacyIndexSettingValidator<? extends Value, ?> validator : validators) {
                records.add(validator.trustIsValid(accessor));
            }
            return records;
        }

        @Override
        public Set<IndexSetting> acceptedSettings() {
            return acceptedSettings;
        }
    }
}
