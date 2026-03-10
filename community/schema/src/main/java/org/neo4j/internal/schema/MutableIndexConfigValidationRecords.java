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

import static java.lang.String.CASE_INSENSITIVE_ORDER;

import java.util.Collections;
import java.util.Map.Entry;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.schema.IndexConfigValidationRecord.State;

/// A collection of [IndexConfigValidationRecord]s grouped by [State].
/// It is mutable in that it exposes methods for include records.
/// It is not intended to be updated iteratively: there must be entry per [IndexSetting].
public class MutableIndexConfigValidationRecords extends IndexConfigValidationRecords {
    private static final Function<State, SortedSet<IndexConfigValidationRecord>> NEW_RECORD_SORTED_SET =
            ignored -> new TreeSet<>();

    private final SortedSet<String> settingNames = new TreeSet<>(CASE_INSENSITIVE_ORDER);

    public MutableIndexConfigValidationRecords() {
        super(new TreeMap<>());
    }

    public MutableIndexConfigValidationRecords with(IndexConfigValidationRecord record) {
        final String settingName = record.settingName();
        if (!settingNames.add(settingName)) {
            throw duplicateSettings(Set.of(settingName));
        }

        records.computeIfAbsent(record.state(), NEW_RECORD_SORTED_SET).add(record);
        return this;
    }

    public MutableIndexConfigValidationRecords with(Iterable<? extends IndexConfigValidationRecord> records) {
        switch (records) {
            case MutableIndexConfigValidationRecords mutable -> withMutableRecords(mutable);
            case IndexConfigValidationRecords base -> withRecords(base);
            default -> withIterable(records);
        }
        return this;
    }

    public IndexConfigValidationRecords toUnmodifiable() {
        return new IndexConfigValidationRecords(Collections.unmodifiableSortedMap(records));
    }

    private void withIterable(Iterable<? extends IndexConfigValidationRecord> records) {
        final SortedSet<String> seenSettings = new TreeSet<>(CASE_INSENSITIVE_ORDER);
        seenSettings.addAll(settingNames);
        final SortedSet<String> duplicateSettings = new TreeSet<>(CASE_INSENSITIVE_ORDER);
        for (final IndexConfigValidationRecord record : records) {
            final String settingName = record.settingName();
            if (!seenSettings.add(settingName)) {
                duplicateSettings.add(settingName);
            }
        }
        if (!duplicateSettings.isEmpty()) {
            throw duplicateSettings(duplicateSettings);
        }

        for (final IndexConfigValidationRecord record : records) {
            with(record);
        }
    }

    public void withMutableRecords(MutableIndexConfigValidationRecords that) {
        withRecords(that, that.settingNames);
    }

    public void withRecords(IndexConfigValidationRecords that) {
        final SortedSet<String> settingNames = new TreeSet<>(CASE_INSENSITIVE_ORDER);
        for (final IndexConfigValidationRecord record : that) {
            settingNames.add(record.settingName());
        }
        withRecords(that, settingNames);
    }

    private void withRecords(IndexConfigValidationRecords that, SequencedCollection<String> thatSettingNames) {
        final SortedSet<String> duplicateSettings = new TreeSet<>(CASE_INSENSITIVE_ORDER);
        for (final String settingName : thatSettingNames) {
            if (this.settingNames.contains(settingName)) {
                duplicateSettings.add(settingName);
            }
        }
        if (!duplicateSettings.isEmpty()) {
            throw duplicateSettings(duplicateSettings);
        }

        for (final Entry<State, SortedSet<IndexConfigValidationRecord>> entry : that.records.entrySet()) {
            final State state = entry.getKey();
            final SortedSet<IndexConfigValidationRecord> thatRecordsForState = entry.getValue();
            final SortedSet<IndexConfigValidationRecord> thisRecordsForState = this.records.get(state);
            if (thisRecordsForState != null) {
                thisRecordsForState.addAll(thatRecordsForState);
            } else {
                this.records.put(state, thatRecordsForState);
            }
        }
    }

    private static IllegalArgumentException duplicateSettings(Set<String> duplicateSettingNames) {
        return new IllegalArgumentException(
                "Expected a single %s to be provided for each %s. Provided duplicates for: %s"
                        .formatted(
                                IndexConfigValidationRecord.class.getSimpleName(),
                                IndexSetting.class.getSimpleName(),
                                Iterables.toString(duplicateSettingNames, ", ", "[", "]")));
    }
}
