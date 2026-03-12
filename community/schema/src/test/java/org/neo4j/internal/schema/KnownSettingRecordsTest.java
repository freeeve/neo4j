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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.neo4j.internal.schema.IndexSettingTestUtils.FAKE_VALUE;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.internal.schema.IndexConfigValidationRecord.InvalidValue;
import org.neo4j.internal.schema.IndexConfigValidationRecord.MissingSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Pending;
import org.neo4j.internal.schema.IndexConfigValidationRecord.RecordWithSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.RecordWithValue;
import org.neo4j.internal.schema.IndexConfigValidationRecord.State;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Valid;
import org.neo4j.internal.schema.IndexSettingTestUtils.TestIndexSetting;
import org.neo4j.internal.schema.KnownSettingRecords.RecordProcessor;
import org.neo4j.values.storable.Values;

class KnownSettingRecordsTest {
    private KnownSettingRecords records;

    @BeforeEach
    void setup() {
        records = new KnownSettingRecords();
    }

    @Test
    void emptyOnCreation() {
        assertThat(records).isEmpty();
    }

    @Test
    void upsertRecord() {
        final RecordWithSetting record = new MissingSetting(TestIndexSetting.STRING);
        assertThat(record).isSameAs(records.upsert(record)).isSameAs(records.get(TestIndexSetting.STRING));

        assertThat(records).containsExactly(record);
    }

    @Test
    void upsertNullRecord() {
        assertThatThrownBy(() -> records.upsert(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("record must not be null");
    }

    @Test
    void upsertKnownSettingRecord() {
        final MissingSetting record = new MissingSetting(TestIndexSetting.STRING);
        assertThat(record).isSameAs(records.upsert(record)).isSameAs(records.get(TestIndexSetting.STRING));
        assertThat(records).containsExactly(record);
    }

    @Test
    void upsertWithNullProcessor() {
        final RecordProcessor processor = record -> null;

        final RecordWithSetting intRecord =
                records.upsert(new Pending(TestIndexSetting.INTEGER, 42, Values.intValue(42)));
        records.upsert(new Pending(TestIndexSetting.STRING, "foo", Values.utf8Value("foo")));
        records.upsertWith(TestIndexSetting.STRING, processor);

        assertThat(records).hasSize(2);
        assertThat(records.get(TestIndexSetting.INTEGER)).isSameAs(intRecord);
        assertThat(records.get(TestIndexSetting.STRING)).isInstanceOf(MissingSetting.class);
    }

    @Test
    void upsertWithProcessor() {
        final RecordProcessor processor = record -> new InvalidValue(record, FAKE_VALUE, null);

        final RecordWithSetting intRecord =
                records.upsert(new Pending(TestIndexSetting.INTEGER, 42, Values.intValue(42)));
        records.upsert(new Pending(TestIndexSetting.STRING, "foo", Values.utf8Value("foo")));
        records.upsertWith(TestIndexSetting.STRING, processor);

        assertThat(records).hasSize(2);
        assertThat(records.get(TestIndexSetting.INTEGER)).isSameAs(intRecord);
        assertThat(records.get(TestIndexSetting.STRING))
                .asInstanceOf(type(InvalidValue.class))
                .extracting(RecordWithValue::value)
                .isEqualTo(FAKE_VALUE);
    }

    @Test
    void upsertWithProcessorNoSettingRecord() {
        final RecordProcessor processor = record -> switch (record) {
            case MissingSetting missingSetting -> new Pending(missingSetting, FAKE_VALUE, null);
            default -> new InvalidValue(record, FAKE_VALUE, null);
        };

        final RecordWithSetting intRecord =
                records.upsert(new Pending(TestIndexSetting.INTEGER, 42, Values.intValue(42)));
        final RecordWithSetting stringRecord =
                records.upsert(new Pending(TestIndexSetting.STRING, "foo", Values.utf8Value("foo")));
        records.upsertWith(TestIndexSetting.OBJECT, processor);

        assertThat(records).hasSize(3);
        assertThat(records.get(TestIndexSetting.INTEGER)).isSameAs(intRecord);
        assertThat(records.get(TestIndexSetting.STRING)).isSameAs(stringRecord);
        assertThat(records.get(TestIndexSetting.OBJECT))
                .asInstanceOf(type(Pending.class))
                .extracting(RecordWithValue::value)
                .isEqualTo(FAKE_VALUE);
    }

    @Test
    void groupByState() {
        final Collection<MissingSetting> missingSetting =
                List.of(records.upsert(new MissingSetting(TestIndexSetting.OBJECT)));

        final Collection<InvalidValue> invalidValue =
                List.of(records.upsert(new InvalidValue(TestIndexSetting.STRING, "foo", Set.of("bar", "baz"))));

        final Collection<Pending> pending = List.of(
                records.upsert(new Pending(TestIndexSetting.INTEGER, 42, Values.intValue(42))),
                records.upsert(new Pending(TestIndexSetting.BOOLEAN, false, Values.NO_VALUE)));

        final Collection<Valid> valid =
                List.of(records.upsert(new Valid(TestIndexSetting.DOUBLE, Math.PI, Values.doubleValue(Math.PI))));

        final IndexConfigValidationRecords recordsByState = records.groupByState();
        assertThat(recordsByState).containsExactlyInAnyOrderElementsOf(records);
        assertThat(recordsByState.get(State.MISSING_SETTING)).containsExactlyInAnyOrderElementsOf(missingSetting);
        assertThat(recordsByState.get(State.INVALID_VALUE)).containsExactlyInAnyOrderElementsOf(invalidValue);
        assertThat(recordsByState.get(State.PENDING)).containsExactlyInAnyOrderElementsOf(pending);
        assertThat(recordsByState.get(State.VALID)).containsExactlyInAnyOrderElementsOf(valid);
    }
}
