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
import org.neo4j.util.MarkerInterface;

/// A processor that can update a collection of records with new state
public interface IndexSettingsProcessor {

    /// Inplace mutation of the collection of records needing verification
    void updateForVerification(KnownSettingRecords records);

    /// Inplace mutation of the collection of records
    void updateForAuthoritativeRead(KnownSettingRecords records);

    Set<IndexSetting> settings();

    /// An [IndexSettingsProcessor] that can produce an [org.neo4j.internal.schema.IndexConfigValidationRecord.Valid]
    /// record for all [#settings()]
    @MarkerInterface
    interface ValidatingIndexSettingsProcessor extends IndexSettingsProcessor {}
}
