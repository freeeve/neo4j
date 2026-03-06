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
import static org.neo4j.internal.schema.IndexConfigValidationRecord.State.VALID;
import static org.neo4j.internal.schema.InternalIndexSetting.VECTOR_QUANTIZATION_TYPE;
import static org.neo4j.kernel.api.impl.schema.IndexConfigUtils.INDEX_SETTING_COMPARATOR;
import static org.neo4j.kernel.api.impl.schema.vector.IndexConfigValidationWrapper.unrecognizedSetting;
import static org.neo4j.values.storable.Values.NO_VALUE;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.eclipse.collections.api.PrimitiveIterable;
import org.neo4j.exceptions.InternalException;
import org.neo4j.exceptions.InvalidArgumentException;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.helpers.InclusiveRange;
import org.neo4j.internal.schema.IndexConfig;
import org.neo4j.internal.schema.IndexConfigValidationRecord;
import org.neo4j.internal.schema.IndexConfigValidationRecord.IncorrectType;
import org.neo4j.internal.schema.IndexConfigValidationRecord.InvalidValue;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Valid;
import org.neo4j.internal.schema.IndexConfigValidationRecords;
import org.neo4j.internal.schema.IndexProviderDescriptor;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.values.storable.Value;

public class VectorIndexConfigUtils {
    static final IndexSetting DIMENSIONS = IndexSetting.vector_Dimensions();
    static final IndexSetting SIMILARITY_FUNCTION = IndexSetting.vector_Similarity_Function();
    static final IndexSetting QUANTIZATION_ENABLED = IndexSetting.vector_Quantization_Enabled();
    static final IndexSetting HNSW_M = IndexSetting.vector_Hnsw_M();
    static final IndexSetting HNSW_EF_CONSTRUCTION = IndexSetting.vector_Hnsw_Ef_Construction();

    public static final SortedMap<IndexSetting, KernelVersion> INDEX_SETTING_INTRODUCED_VERSIONS;

    static {
        final SortedMap<IndexSetting, KernelVersion> indexSettingIntroducedVersions =
                new TreeMap<>(INDEX_SETTING_COMPARATOR);
        indexSettingIntroducedVersions.put(DIMENSIONS, KernelVersion.VERSION_NODE_VECTOR_INDEX_INTRODUCED);
        indexSettingIntroducedVersions.put(SIMILARITY_FUNCTION, KernelVersion.VERSION_NODE_VECTOR_INDEX_INTRODUCED);
        indexSettingIntroducedVersions.put(
                QUANTIZATION_ENABLED, KernelVersion.VERSION_VECTOR_QUANTIZATION_AND_HYPER_PARAMS);
        indexSettingIntroducedVersions.put(VECTOR_QUANTIZATION_TYPE, KernelVersion.VERSION_VECTOR_BINARY_QUANTIZATION);
        indexSettingIntroducedVersions.put(HNSW_M, KernelVersion.VERSION_VECTOR_QUANTIZATION_AND_HYPER_PARAMS);
        indexSettingIntroducedVersions.put(
                HNSW_EF_CONSTRUCTION, KernelVersion.VERSION_VECTOR_QUANTIZATION_AND_HYPER_PARAMS);
        INDEX_SETTING_INTRODUCED_VERSIONS = Collections.unmodifiableSortedMap(indexSettingIntroducedVersions);
    }

    static SortedMap<IndexSetting, Object> toValidSettings(Iterable<Valid> validRecords) {
        final TreeMap<IndexSetting, Object> validSettings =
                new TreeMap<>(Comparator.comparing(IndexSetting::getSettingName, CASE_INSENSITIVE_ORDER));
        for (final Valid valid : validRecords) {
            validSettings.put(valid.setting(), valid.value());
        }
        return validSettings;
    }

    static IndexConfig toIndexConfig(Iterable<Valid> validRecords) {
        return toIndexConfig(validRecords, valid -> true);
    }

    static IndexConfig toIndexConfig(Iterable<Valid> validRecords, Iterable<String> validSettingNames) {
        return toIndexConfig(validRecords, containsSettingNamePredicate(validSettingNames));
    }

    private static <RECORD extends IndexConfigValidationRecord> Predicate<RECORD> containsSettingNamePredicate(
            Iterable<String> settingNames) {
        return record -> {
            for (final String settingName : settingNames) {
                if (record.settingName().equals(settingName)) {
                    return true;
                }
            }
            return false;
        };
    }

    static IndexConfig toIndexConfig(Iterable<Valid> validRecords, Predicate<Valid> filter) {
        final Map<String, Value> settings = new HashMap<>();
        for (final Valid valid : validRecords) {
            if (filter.test(valid) && valid.stored() != null && valid.stored() != NO_VALUE) {
                settings.put(valid.settingName(), valid.stored());
            }
        }
        return IndexConfig.with(settings);
    }

    static void assertValidRecords(
            IndexConfigValidationRecords validationRecords,
            IndexProviderDescriptor descriptor,
            Iterable<String> validSettingNames) {
        // fail on first
        final IndexConfigValidationRecord invalidRecord = validationRecords.getFirstInvalidRecordOrNull();
        if (invalidRecord == null) {
            return;
        }

        // When we can rely on Java 21, might be worth refactoring to use
        // JEP 441: Pattern Matching for switch
        final var settingName = invalidRecord.settingName();
        throw switch (invalidRecord.state()) {
            // this is a logic error
            case VALID ->
                InternalException.indexNotApplicable(
                        descriptor.name(),
                        "%s should not be %s at this point. Provided: %s"
                                .formatted(IndexConfigValidationRecord.class.getSimpleName(), VALID, invalidRecord));

            // this is an implementation mistake
            case PENDING ->
                InternalException.indexNotApplicable(
                        descriptor.name(), "Validation for '%s' is incomplete.".formatted(settingName));

            // these are likely user mistakes
            case UNRECOGNIZED_SETTING -> unrecognizedSetting(invalidRecord.settingName(), validSettingNames);
            case MISSING_SETTING -> InvalidArgumentException.missingIndexConfig(settingName);
            case INCORRECT_TYPE -> {
                final var incorrectType = (IncorrectType) invalidRecord;
                yield InvalidArgumentException.invalidType(
                        settingName,
                        incorrectType.rawValue().prettify(),
                        incorrectType.targetType().getSimpleName(),
                        incorrectType.rawValue().getTypeName());
            }
            case INVALID_VALUE -> {
                final var invalidValue = (InvalidValue) invalidRecord;
                final var valid = invalidValue.valid();
                if (valid instanceof final InclusiveRange<?> range) {
                    var actualRawValue = invalidValue.rawValue() != null ? invalidValue.rawValue() : NO_VALUE;
                    yield InvalidArgumentException.outOfRange(
                            settingName,
                            actualRawValue.prettify(),
                            actualRawValue.getTypeName(),
                            range.min().toString(),
                            range.max().toString());
                } else if (valid instanceof Iterable<?> || valid instanceof PrimitiveIterable) {
                    yield InvalidArgumentException.invalidIndexInput(
                            invalidValue.rawValue().prettify(),
                            settingName,
                            "'%s' is an unsupported '%s'. Supported: %s"
                                    .formatted(invalidValue.rawValue().prettify(), settingName, valid));
                }

                // this is an implementation mistake
                yield InternalException.indexNotApplicable(
                        descriptor.name(), "Unhandled valid value type '%s' for '%s'. Provided: %s");
            }
        };
    }
}
