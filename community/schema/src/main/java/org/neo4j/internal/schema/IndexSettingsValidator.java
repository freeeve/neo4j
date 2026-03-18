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

import java.util.Set;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.schema.IndexSettingRecord.Valid;

public interface IndexSettingsValidator {

    /// Validate the provided provisional settings into validation records
    IndexSettingRecordsByState validate(SettingsAccessor accessor);

    /// Interpret an existing authoritative settings into valid records
    Iterable<Valid> interpretAuthoritative(SettingsAccessor accessor);

    /// Settings accepted as persisted input by this validator
    Set<IndexSetting> acceptedSettings();

    class Delegate implements IndexSettingsValidator {
        private final IndexSettingsValidator delegate;

        public Delegate(IndexSettingsValidator delegate) {
            this.delegate = delegate;
        }

        @Override
        public IndexSettingRecordsByState validate(SettingsAccessor accessor) {
            return delegate.validate(accessor);
        }

        @Override
        public Iterable<Valid> interpretAuthoritative(SettingsAccessor accessor) {
            return delegate.interpretAuthoritative(accessor);
        }

        @Override
        public Set<IndexSetting> acceptedSettings() {
            return delegate.acceptedSettings();
        }
    }
}
