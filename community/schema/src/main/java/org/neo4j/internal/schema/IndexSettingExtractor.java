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

import org.neo4j.internal.schema.IndexConfigUtils.HasSetting;
import org.neo4j.internal.schema.IndexSettingRecord.RecordWithSetting;

/// An extractor of setting values from a [SettingsAccessor]
public interface IndexSettingExtractor extends HasSetting {
    /// Extract a value with a corresponding [org.neo4j.graphdb.schema.IndexSetting] from a [SettingsAccessor]
    ///
    /// If the [SettingsAccessor] does not contain that [org.neo4j.graphdb.schema.IndexSetting], or its extracted
    // value
    /// is `null` or [org.neo4j.values.storable.Values#NO_VALUE], a
    /// [IndexSettingRecord.MissingSetting] should be returned.
    /// @return a [RecordWithSetting] wrapping the extracted result.
    RecordWithSetting extractForValidation(SettingsAccessor accessor);

    /// @see #extractForValidation(SettingsAccessor)
    RecordWithSetting extractForAuthoritativeRead(SettingsAccessor accessor);
}
