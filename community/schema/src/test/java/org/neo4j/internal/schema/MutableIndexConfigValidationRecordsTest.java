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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.helpers.ArrayUtil;
import org.neo4j.internal.schema.IndexConfigValidationRecord.IncorrectType;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Invalid;
import org.neo4j.internal.schema.IndexConfigValidationRecord.InvalidValue;
import org.neo4j.internal.schema.IndexConfigValidationRecord.MissingSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Pending;
import org.neo4j.internal.schema.IndexConfigValidationRecord.State;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Unprocessed;
import org.neo4j.internal.schema.IndexConfigValidationRecord.UnrecognizedSetting;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Valid;
import org.neo4j.internal.schema.IndexSettingTestUtils.TestIndexSetting;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

class MutableIndexConfigValidationRecordsTest {
    private MutableIndexConfigValidationRecords records;

    @BeforeEach
    void setup() {
        records = new MutableIndexConfigValidationRecords();
    }

    @Test
    void emptyOnCreation() {
        assertThat(records).isEmpty();
    }

    @ParameterizedTest
    @EnumSource
    void noRecordsForMissingState(State state) {
        assertThat(records.get(state)).isEmpty();
    }

    @Test
    void withRecord() {
        final IndexConfigValidationRecord record = recordFor(State.VALID, TestIndexSetting.INTEGER);
        assertThat(records.with(record)).as("allows for chaining").isSameAs(records);
        assertThat(records).containsExactly(record);
    }

    @ParameterizedTest
    @MethodSource
    void withRecordDuplicateSetting(IndexConfigValidationRecord duplicateSetting) {
        final IndexConfigValidationRecord record = recordFor(State.VALID, TestIndexSetting.INTEGER);
        records.with(record);

        assertThatThrownBy(() -> records.with(duplicateSetting))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContainingAll(
                        "Expected a single", IndexConfigValidationRecord.class.getSimpleName(),
                        "to be provided for each", IndexSetting.class.getSimpleName(),
                        "Provided duplicates for", duplicateSetting.settingName());
    }

    static Stream<Named<IndexConfigValidationRecord>> withRecordDuplicateSetting() {
        return Stream.of(
                Named.of("same state, same setting", recordFor(State.VALID, TestIndexSetting.INTEGER)),
                Named.of("different state, same setting", recordFor(State.INCORRECT_TYPE, TestIndexSetting.INTEGER)));
    }

    @Test
    void withIterableOfRecords() {
        final List<IndexConfigValidationRecord> otherRecords = new ArrayList<>();
        for (final IndexSetting setting : TestIndexSetting.values()) {
            otherRecords.add(recordFor(State.VALID, setting));
        }
        assertThat(records.with(otherRecords)).as("allows for chaining").isSameAs(records);
        assertThat(records).containsExactlyInAnyOrderElementsOf(otherRecords);
    }

    @ParameterizedTest
    @MethodSource
    @MethodSource("duplicateSettings")
    void withIterableOfRecordsDuplicateSettings(Iterable<IndexConfigValidationRecord> duplicateSettings) {
        final IndexSetting[] settings = ArrayUtil.without(TestIndexSetting.values(), TestIndexSetting.OBJECT);
        for (final IndexSetting setting : settings) {
            records.with(recordFor(State.VALID, setting));
        }

        final AbstractThrowableAssert<?, ?> duplicateAssert = assertThatThrownBy(() -> records.with(duplicateSettings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContainingAll(
                        "Expected a single",
                        IndexConfigValidationRecord.class.getSimpleName(),
                        "to be provided for each",
                        IndexSetting.class.getSimpleName(),
                        "Provided duplicates for");
        duplicateSettings.forEach(record -> duplicateAssert.hasMessageContaining(record.settingName()));
    }

    static Stream<Named<Iterable<IndexConfigValidationRecord>>> withIterableOfRecordsDuplicateSettings() {
        return Stream.of(Named.of(
                "duplicates within iterable",
                List.of(
                        recordFor(State.VALID, TestIndexSetting.OBJECT),
                        recordFor(State.INVALID_VALUE, TestIndexSetting.OBJECT))));
    }

    @Test
    void withRecords() {
        final MutableIndexConfigValidationRecords otherRecords = new MutableIndexConfigValidationRecords();
        for (final IndexSetting setting : TestIndexSetting.values()) {
            otherRecords.with(recordFor(State.VALID, setting));
        }
        assertThat(records.with(otherRecords)).as("allows for chaining").isSameAs(records);
        assertThat(records).containsExactlyInAnyOrderElementsOf(otherRecords);
    }

    @ParameterizedTest
    @MethodSource("duplicateSettings")
    void withRecordsDuplicateSettings(Iterable<IndexConfigValidationRecord> duplicateSettings) {
        for (final IndexSetting setting : TestIndexSetting.values()) {
            records.with(recordFor(State.VALID, setting));
        }
        final MutableIndexConfigValidationRecords otherRecords = new MutableIndexConfigValidationRecords();
        duplicateSettings.forEach(otherRecords::with);

        final AbstractThrowableAssert<?, ?> duplicateAssert = assertThatThrownBy(() -> records.with(otherRecords))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContainingAll(
                        "Expected a single",
                        IndexConfigValidationRecord.class.getSimpleName(),
                        "to be provided for each",
                        IndexSetting.class.getSimpleName(),
                        "Provided duplicates for");
        duplicateSettings.forEach(record -> duplicateAssert.hasMessageContaining(record.settingName()));
    }

    static Stream<Named<Iterable<IndexConfigValidationRecord>>> duplicateSettings() {
        return Stream.of(
                Named.of(
                        "same states, same settings",
                        List.of(
                                recordFor(State.VALID, TestIndexSetting.INTEGER),
                                recordFor(State.VALID, TestIndexSetting.STRING))),
                Named.of(
                        "different states, same settings",
                        List.of(
                                recordFor(State.INCORRECT_TYPE, TestIndexSetting.INTEGER),
                                recordFor(State.INVALID_VALUE, TestIndexSetting.STRING))));
    }

    @Test
    void get() {
        final List<IndexConfigValidationRecord> validRecords = List.of(
                recordFor(State.VALID, TestIndexSetting.INTEGER), recordFor(State.VALID, TestIndexSetting.BOOLEAN));
        final List<IndexConfigValidationRecord> missingSettingRecords = List.of(
                recordFor(State.MISSING_SETTING, TestIndexSetting.STRING),
                recordFor(State.MISSING_SETTING, TestIndexSetting.OBJECT));
        final List<IndexConfigValidationRecord> incorrectTypeRecords =
                List.of(recordFor(State.INCORRECT_TYPE, TestIndexSetting.DOUBLE));
        records.with(validRecords).with(missingSettingRecords).with(incorrectTypeRecords);

        assertThat(records.get(State.VALID)).containsExactlyInAnyOrderElementsOf(validRecords);
        assertThat(records.get(State.MISSING_SETTING)).containsExactlyInAnyOrderElementsOf(missingSettingRecords);
        assertThat(records.get(State.INCORRECT_TYPE)).containsExactlyInAnyOrderElementsOf(incorrectTypeRecords);
    }

    @Test
    void invalid() {
        final List<IndexConfigValidationRecord> validRecords = List.of(
                recordFor(State.VALID, TestIndexSetting.INTEGER), recordFor(State.VALID, TestIndexSetting.BOOLEAN));
        final List<IndexConfigValidationRecord> missingSettingRecords = List.of(
                recordFor(State.MISSING_SETTING, TestIndexSetting.STRING),
                recordFor(State.MISSING_SETTING, TestIndexSetting.OBJECT));
        final List<IndexConfigValidationRecord> incorrectTypeRecords =
                List.of(recordFor(State.INCORRECT_TYPE, TestIndexSetting.DOUBLE));
        records.with(validRecords).with(missingSettingRecords).with(incorrectTypeRecords);

        assertThat(records.invalid()).isTrue();
        assertThat(records.valid()).isFalse();

        assertThat(records.validRecords())
                .map(IndexConfigValidationRecord.class::cast)
                .containsExactlyInAnyOrderElementsOf(validRecords);

        final Iterable<Invalid> invalidRecords = records.invalidRecords();
        assertThat(invalidRecords)
                .map(IndexConfigValidationRecord.class::cast)
                .containsAll(missingSettingRecords)
                .containsAll(incorrectTypeRecords)
                .doesNotContainAnyElementsOf(validRecords);

        assertThat(records.getFirstInvalidRecordOrNull())
                .isEqualTo(invalidRecords.iterator().next());
    }

    @Test
    void valid() {
        final List<IndexConfigValidationRecord> validRecords = List.of(
                recordFor(State.VALID, TestIndexSetting.INTEGER),
                recordFor(State.VALID, TestIndexSetting.BOOLEAN),
                recordFor(State.VALID, TestIndexSetting.STRING));
        records.with(validRecords);

        assertThat(records.invalid()).isFalse();
        assertThat(records.valid()).isTrue();

        assertThat(records.validRecords())
                .map(IndexConfigValidationRecord.class::cast)
                .containsExactlyInAnyOrderElementsOf(validRecords);

        assertThat(records.invalidRecords()).isEmpty();
        assertThat(records.getFirstInvalidRecordOrNull()).isNull();
    }

    private static IndexConfigValidationRecord recordFor(State state, IndexSetting setting) {
        final int value = 42;
        final Value storable = Values.intValue(42);
        return switch (state) {
            case VALID -> new Valid(setting, value, storable);
            case UNPROCESSED -> new Unprocessed(setting, storable);
            case PENDING -> new Pending(setting, value, storable);
            case UNRECOGNIZED_SETTING -> new UnrecognizedSetting("unknown-" + setting.getSettingName());
            case MISSING_SETTING -> new MissingSetting(setting);
            case INCORRECT_TYPE -> new IncorrectType(setting, (float) value, Integer.class);
            case INVALID_VALUE -> new InvalidValue(setting, -23, Set.of(23, 42, 64));
        };
    }
}
