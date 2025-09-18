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
package org.neo4j.internal.batchimport.cache.idmapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.iterator.LongIterator;
import org.eclipse.collections.api.list.primitive.LongList;
import org.junit.jupiter.api.Test;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomSupportExtension;

@RandomSupportExtension
class IdMappersTest {
    private static final long MAX_VALUE = 100;

    @Inject
    private RandomSupport random;

    @Test
    void shouldMergeSkipListOnlyIdMapperDuplicates() {
        // given
        var duplicateIds = LongLists.immutable.of(randomIds());

        // when
        var result = IdMappers.combineSkipListSorted(duplicateIds.longIterator(), LongSets.immutable.empty());

        // then
        assertThat(LongLists.immutable.ofAll(toList(result))).isEqualTo(duplicateIds);
    }

    @Test
    void shouldMergeSkipListOnlyOtherViolations() {
        // given
        var otherViolatingIds = LongSets.immutable.of(randomIds());

        // when
        var result = IdMappers.combineSkipListSorted(LongLists.immutable.empty().longIterator(), otherViolatingIds);

        // then
        assertThat(LongLists.immutable.ofAll(toList(result))).isEqualTo(otherViolatingIds.toSortedList());
    }

    @Test
    void shouldMergeSkipList() {
        // given
        var duplicateIds = LongLists.immutable.of(randomIds());
        var otherViolatingIds = LongSets.immutable.of(randomIds());

        // when
        var result = IdMappers.combineSkipListSorted(duplicateIds.longIterator(), otherViolatingIds);

        // then
        var expected = LongSets.mutable.ofAll(duplicateIds);
        expected.addAll(otherViolatingIds);
        assertThat(LongLists.immutable.ofAll(toList(result))).isEqualTo(expected.toSortedList());
    }

    @Test
    void shouldMergeSkipFilterOnlyIdMapperDuplicates() {
        // given
        var duplicateIds = LongSets.immutable.of(randomIds());

        // when
        var result = IdMappers.combineSkipFilter(duplicateIds::contains, LongSets.immutable.empty());

        // then
        for (long test = 0; test < MAX_VALUE; test++) {
            assertThat(result.test(test)).isEqualTo(duplicateIds.contains(test));
        }
    }

    @Test
    void shouldMergeSkipFilterOnlyOtherViolations() {
        // given
        var otherViolatingIds = LongSets.immutable.of(randomIds());

        // when
        var result = IdMappers.combineSkipFilter(null, otherViolatingIds);

        // then
        for (long test = 0; test < MAX_VALUE; test++) {
            assertThat(result.test(test)).isEqualTo(otherViolatingIds.contains(test));
        }
    }

    @Test
    void shouldMergeSkipFilter() {
        // given
        var duplicateIds = LongSets.immutable.of(randomIds());
        var otherViolatingIds = LongSets.immutable.of(randomIds());

        // when
        var result = IdMappers.combineSkipFilter(duplicateIds::contains, otherViolatingIds);

        // then
        for (long test = 0; test < MAX_VALUE; test++) {
            assertThat(result.test(test)).isEqualTo(otherViolatingIds.contains(test) || duplicateIds.contains(test));
        }
    }

    @Test
    void shouldReturnNullSkipFilterOnBothEmpty() {
        // when/then
        assertThat(IdMappers.combineSkipFilter(null, null)).isNull();
        assertThat(IdMappers.combineSkipFilter(null, LongSets.immutable.empty()))
                .isNull();
    }

    private LongList toList(LongIterator iterator) {
        var list = LongLists.mutable.empty();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return list;
    }

    private long[] randomIds() {
        int count = random.nextInt(2, 10);
        var set = LongSets.mutable.empty();
        for (int i = 0; i < count; i++) {
            set.add(random.nextLong(MAX_VALUE));
        }
        return set.toSortedArray();
    }
}
