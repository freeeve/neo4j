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
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.INDEX_SETTING_COMPARATOR;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.assertValidRecords;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.toIndexConfig;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.toValidSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.neo4j.exceptions.InvalidArgumentException;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecords;
import org.neo4j.internal.schema.IndexConfigValidationRecords.Valid;
import org.neo4j.internal.schema.SettingsAccessor;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.api.impl.schema.vector.IndexSettingValidators.IndexSettingValidator;
import org.neo4j.kernel.api.impl.schema.vector.IndexSettingValidators.ReadDefaultOnly;
import org.neo4j.values.storable.Value;

public interface VectorIndexSettingsValidator {

    IndexConfigValidationRecords validate(SettingsAccessor settings);

    default VectorIndexConfig validateToVectorIndexConfig(SettingsAccessor settings) {
        return validateToVectorIndexConfig(settings, validate(settings));
    }

    VectorIndexConfig validateToVectorIndexConfig(
            SettingsAccessor settings, IndexConfigValidationRecords validationRecords);

    VectorIndexConfig trustIsValidToVectorIndexConfig(SettingsAccessor settings);

    VectorIndexConfig trustIsValidToVectorIndexConfig(IndexConfigValidationRecords validationRecords);

    Set<IndexSetting> validSettings();

    class Validators implements VectorIndexSettingsValidator {
        private final VectorIndexVersion version;
        private final SortedSet<IndexSettingValidator<? extends Value, ?>> validators;
        private final SortedSet<IndexSetting> validSettings;
        private final SortedSet<String> validSettingNames;
        private final SortedSet<String> handledSettingNames;

        @SafeVarargs
        Validators(VectorIndexVersion version, IndexSettingValidator<? extends Value, ?>... validators) {
            this.version = version;

            // check we've not passed multiple validators for the same setting
            final Set<String> seenSettingNames = new HashSet<>(validators.length);
            final List<IndexSettingValidator<? extends Value, ?>> checkedValidators =
                    new ArrayList<>(validators.length);
            for (final IndexSettingValidator<? extends Value, ?> validator : validators) {
                if (!seenSettingNames.add(validator.setting().getSettingName())) {
                    throw new IllegalStateException("Expected a single %s to be provided for '%s', multiple given."
                            .formatted(
                                    IndexSettingValidator.class.getSimpleName(),
                                    validator.setting().getSettingName()));
                }
                checkedValidators.add(validator);
            }
            final SortedSet<IndexSettingValidator<? extends Value, ?>> sortedValidators = new TreeSet<>(
                    Comparator.comparing(validator -> validator.setting().getSettingName(), CASE_INSENSITIVE_ORDER));
            sortedValidators.addAll(checkedValidators);
            this.validators = Collections.unmodifiableSortedSet(sortedValidators);

            final Set<IndexSetting> handledSettings = new HashSet<>(this.validators.size());
            final SortedSet<String> handledSettingNames = new TreeSet<>(CASE_INSENSITIVE_ORDER);
            for (final IndexSettingValidator<? extends Value, ?> validator : this.validators) {
                final IndexSetting setting = validator.setting();
                handledSettings.add(setting);
                handledSettingNames.add(setting.getSettingName());
            }
            this.handledSettingNames = Collections.unmodifiableSortedSet(handledSettingNames);

            final Set<IndexSetting> readDefaultOnlySettings = new HashSet<>();
            for (final IndexSettingValidator<? extends Value, ?> validator : this.validators) {
                if (validator instanceof final ReadDefaultOnly<?> readDefaultOnly) {
                    readDefaultOnlySettings.add(readDefaultOnly.setting());
                }
            }

            final SortedSet<IndexSetting> validSettings = new TreeSet<>(INDEX_SETTING_COMPARATOR);
            final SortedSet<String> validSettingNames = new TreeSet<>(CASE_INSENSITIVE_ORDER);
            for (final IndexSetting setting : handledSettings) {
                if (!readDefaultOnlySettings.contains(setting)) {
                    validSettings.add(setting);
                    validSettingNames.add(setting.getSettingName());
                }
            }
            this.validSettings = Collections.unmodifiableSortedSet(validSettings);
            this.validSettingNames = Collections.unmodifiableSortedSet(validSettingNames);
        }

        @Override
        public IndexConfigValidationRecords validate(SettingsAccessor settings) {
            final IndexConfigValidationRecords validationRecords =
                    IndexConfigValidationWrapper.validateSettingNames(settings.settingNames(), handledSettingNames);
            for (final IndexSettingValidator<? extends Value, ?> validator : validators) {
                validationRecords.with(validator.validate(settings));
            }
            return validationRecords;
        }

        @Override
        public VectorIndexConfig validateToVectorIndexConfig(
                SettingsAccessor settings, IndexConfigValidationRecords validationRecords) {
            assertValidRecords(validationRecords, version.descriptor(), validSettingNames);
            final Iterable<Valid> validRecords = validationRecords.validRecords();
            return new VectorIndexConfig(
                    version,
                    toIndexConfig(validRecords, validSettingNames),
                    toValidSettings(validRecords),
                    validSettingNames,
                    handledSettingNames);
        }

        @Override
        public VectorIndexConfig trustIsValidToVectorIndexConfig(SettingsAccessor settings) {
            final List<Valid> validRecords = new ArrayList<>(validators.size());
            for (final IndexSettingValidator<? extends Value, ?> validator : validators) {
                validRecords.add(validator.trustIsValid(settings));
            }
            return new VectorIndexConfig(
                    version,
                    toIndexConfig(validRecords),
                    toValidSettings(validRecords),
                    validSettingNames,
                    handledSettingNames);
        }

        @Override
        public VectorIndexConfig trustIsValidToVectorIndexConfig(IndexConfigValidationRecords validationRecords) {
            final var validRecords = validationRecords.validRecords();
            return new VectorIndexConfig(
                    version,
                    toIndexConfig(validRecords),
                    toValidSettings(validRecords),
                    validSettingNames,
                    handledSettingNames);
        }

        @Override
        public Set<IndexSetting> validSettings() {
            return validSettings;
        }
    }

    class ValidatorNotFound implements VectorIndexSettingsValidator {
        private final InvalidArgumentException exception;

        ValidatorNotFound(InvalidArgumentException exception) {
            this.exception = exception;
        }

        @Override
        public IndexConfigValidationRecords validate(SettingsAccessor settings) {
            throw exception;
        }

        @Override
        public VectorIndexConfig validateToVectorIndexConfig(SettingsAccessor settings) {
            throw exception;
        }

        @Override
        public VectorIndexConfig validateToVectorIndexConfig(
                SettingsAccessor settings, IndexConfigValidationRecords validationRecords) {
            throw exception;
        }

        @Override
        public VectorIndexConfig trustIsValidToVectorIndexConfig(SettingsAccessor settings) {
            throw exception;
        }

        @Override
        public VectorIndexConfig trustIsValidToVectorIndexConfig(IndexConfigValidationRecords validationRecords) {
            throw exception;
        }

        @Override
        public Set<IndexSetting> validSettings() {
            return Collections.emptySet();
        }
    }

    class ValidatorNotFoundForKernelVersion extends ValidatorNotFound {
        ValidatorNotFoundForKernelVersion(VectorIndexVersion version, KernelVersion kernelVersion) {
            super(InvalidArgumentException.internalError(
                    "Validator Not Found",
                    ("%s not found for '%s' on '%s'."
                            .formatted(
                                    VectorIndexSettingsValidator.class.getSimpleName(),
                                    version.descriptor().name(),
                                    kernelVersion))));
        }
    }
}
