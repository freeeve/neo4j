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
import org.neo4j.internal.schema.IndexConfigValidationRecord.IncorrectType;
import org.neo4j.internal.schema.IndexConfigValidationRecord.MissingSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Pending;
import org.neo4j.internal.schema.IndexConfigValidationRecord.RecordWithSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Valid;

/// A [SingleIndexSettingProcessor] that transforms a valid value from one [IndexSetting] to another
public abstract class SingleIndexSettingMigrator<FROM> extends SingleIndexSettingProcessor {
    protected final Class<FROM> fromType;
    protected final IndexSetting fromSetting;
    protected final IndexSetting toSetting;

    protected SingleIndexSettingMigrator(IndexSetting fromSetting, Class<FROM> fromType, IndexSetting toSetting) {
        super(fromSetting);
        this.fromType = fromType;
        this.fromSetting = fromSetting;
        this.toSetting = toSetting;
    }

    protected abstract Object migrate(FROM value);

    /// Migrates the value from one [Valid] record to a [Pending] record of a new setting using [#migrate(FROM)].
    /// If a non-valid [RecordWithSetting] is provided a [MissingSetting] will be returned.
    @Override
    public RecordWithSetting processForVerification(RecordWithSetting record) {
        if (!(record instanceof final Valid valid)) {
            return new MissingSetting(toSetting);
        }
        if (!fromType.isInstance(valid.value())) {
            return new IncorrectType(toSetting, null, fromType);
        }

        return new Pending(toSetting, migrate(valid.valueAs(fromType)), null);
    }

    @Override
    public RecordWithSetting processForAuthoritativeRead(RecordWithSetting record) {
        if (!(record instanceof final Valid valid)) {
            return new MissingSetting(toSetting);
        }

        return new Valid(toSetting, migrate(valid.valueAs(fromType)), null);
    }

    @Override
    public void updateForVerification(KnownSettingRecords records) {
        records.upsert(processForVerification(records.get(setting)));
    }

    @Override
    public void updateForAuthoritativeRead(KnownSettingRecords records) {
        records.upsert(processForAuthoritativeRead(records.get(setting)));
    }

    @Override
    public Set<IndexSetting> settings() {
        return Set.of(fromSetting, toSetting);
    }
}
