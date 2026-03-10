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

import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import org.neo4j.exceptions.InvalidArgumentException;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.schema.IndexConfig;
import org.neo4j.internal.schema.IndexConfigValidationRecord;
import org.neo4j.internal.schema.IndexConfigValidationRecord.UnrecognizedSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecords;
import org.neo4j.internal.schema.IndexProviderDescriptor;
import org.neo4j.internal.schema.MutableIndexConfigValidationRecords;
import org.neo4j.values.storable.Value;

public abstract class IndexConfigValidationWrapper {
    private final IndexProviderDescriptor descriptor;
    private final SortedSet<String> validSettingNames;
    private final SortedSet<String> possibleValidSettingNames;

    private final IndexConfig config;
    private final SortedMap<IndexSetting, Object> settings;

    protected IndexConfigValidationWrapper(
            IndexProviderDescriptor descriptor,
            IndexConfig config,
            SortedMap<IndexSetting, Object> settings,
            SortedSet<String> validSettingNames,
            SortedSet<String> possibleValidSettingNames) {
        this.descriptor = descriptor;
        this.validSettingNames = validSettingNames;
        this.possibleValidSettingNames = possibleValidSettingNames;
        this.config = validateSettingNames(config);
        this.settings = validatePossibleSettingNames(settings);
    }

    public IndexProviderDescriptor descriptor() {
        return descriptor;
    }

    public IndexConfig config() {
        return config;
    }

    public <T extends Value> T getValue(String setting) {
        if (!validSettingNames.contains(setting)) {
            throw unrecognizedSetting(setting, validSettingNames);
        }
        return config.get(setting);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(IndexSetting setting) {
        final String settingName = setting.getSettingName();
        if (!possibleValidSettingNames.contains(settingName)) {
            throw unrecognizedSetting(settingName, possibleValidSettingNames);
        }
        return (T) settings.get(setting);
    }

    public static MutableIndexConfigValidationRecords validateSettingNames(
            Set<String> settingNames, Set<String> validSettingNames) {
        final MutableIndexConfigValidationRecords validationRecords = new MutableIndexConfigValidationRecords();
        for (final String settingName : settingNames) {
            if (!validSettingNames.contains(settingName)) {
                validationRecords.with(new UnrecognizedSetting(settingName));
            }
        }
        return validationRecords;
    }

    private IndexConfig validateSettingNames(IndexConfig config) {
        assertValidSettingNames(validateSettingNames(config.settingNames(), validSettingNames), validSettingNames);
        return config;
    }

    private SortedMap<IndexSetting, Object> validatePossibleSettingNames(SortedMap<IndexSetting, Object> settings) {
        final Set<String> settingNames = new HashSet<>(settings.size());
        for (final IndexSetting setting : settings.keySet()) {
            settingNames.add(setting.getSettingName());
        }

        assertValidSettingNames(
                validateSettingNames(settingNames, possibleValidSettingNames), possibleValidSettingNames);
        return settings;
    }

    private static void assertValidSettingNames(
            IndexConfigValidationRecords validationRecords, Iterable<String> validSettingNames) {
        if (validationRecords.valid()) {
            return;
        }

        // fail on first
        final IndexConfigValidationRecord invalidRecord = validationRecords.getFirstInvalidRecordOrNull();
        if (invalidRecord == null) {
            throw new IllegalStateException("%s were invalid but found a null %s"
                    .formatted(
                            IndexConfigValidationRecords.class.getSimpleName(),
                            IndexConfigValidationRecord.class.getSimpleName()));
        }
        throw unrecognizedSetting(invalidRecord.settingName(), validSettingNames);
    }

    public static InvalidArgumentException unrecognizedSetting(String settingName, Iterable<String> validSettingNames) {
        return InvalidArgumentException.invalidIndexConfig(settingName, validSettingNames);
    }
}
