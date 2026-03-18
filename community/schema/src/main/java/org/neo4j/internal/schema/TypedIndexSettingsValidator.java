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

import static org.neo4j.internal.schema.IndexConfigUtils.assertValidRecords;

import org.neo4j.internal.schema.IndexConfigValidationRecord.Valid;

public abstract class TypedIndexSettingsValidator<CONFIG extends TypedIndexConfig>
        extends IndexSettingsValidator.Delegate {

    private final IndexProviderDescriptor descriptor;

    protected TypedIndexSettingsValidator(IndexProviderDescriptor descriptor, IndexSettingsValidator delegate) {
        super(delegate);
        this.descriptor = descriptor;
    }

    /// Validate the provided provisional settings into a [TypedIndexConfig]
    /// @see #validate(SettingsAccessor)
    public CONFIG validateToTypedConfig(SettingsAccessor accessor) {
        return validateToTypedConfig(validate(accessor));
    }

    /// Intermediate method for optimization purposes, if the [IndexConfigValidationRecords] are precomputed
    /// @see #validateToTypedConfig(SettingsAccessor)
    public CONFIG validateToTypedConfig(IndexConfigValidationRecords records) {
        assertValidRecords(records, descriptor, acceptedSettings());
        return toTypedConfig(records.validRecords());
    }

    /// Interpret an existing authoritative settings into a [TypedIndexConfig]
    /// @see #interpretAuthoritative(SettingsAccessor)
    public CONFIG interpretAuthoritativeToTypedConfig(SettingsAccessor accessor) {
        return interpretAuthoritativeToTypedConfig(interpretAuthoritative(accessor));
    }

    /// Intermediate method for optimization purposes, if the [IndexConfigValidationRecords] are precomputed
    /// @see #interpretAuthoritative(SettingsAccessor)
    public CONFIG interpretAuthoritativeToTypedConfig(Iterable<Valid> records) {
        return toTypedConfig(records);
    }

    /// Internal method to convert the provided valid records into a [TypedIndexConfig]
    protected abstract CONFIG toTypedConfig(Iterable<Valid> records);
}
