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
package org.neo4j.index.internal.gbptree;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.index.internal.gbptree.GBPTreeTestUtil.consistencyCheckStrict;
import static org.neo4j.io.pagecache.context.CursorContext.NULL_CONTEXT;

import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.neo4j.test.RandomSupport;

public class GBPTreeDynamicSizeIT extends GBPTreeITBase<RawBytes, RawBytes> {
    @Override
    TestLayout<RawBytes, RawBytes> getLayout(RandomSupport random, int pageSize) {
        return new SimpleByteArrayLayout(
                DynamicSizeUtil.keyValueSizeCapFromPageSize(pageSize) / 2, random.intBetween(0, 10));
    }

    @Override
    Class<RawBytes> getKeyClass() {
        return RawBytes.class;
    }

    @EnumSource(WriterFactory.class)
    @ParameterizedTest
    void shouldValidateSizeAfterMerge(WriterFactory writerFactory) throws Exception {
        try (var writer = createWriter(index, writerFactory)) {
            writer.put(key(1), value(1));
        }

        int targetSize = DynamicSizeUtil.keyValueSizeCapFromPageSize(payloadSize) + 1;

        byte[] expected = random.nextBytes(targetSize);

        try (var writer = createWriter(index, writerFactory)) {
            assertThatThrownBy(() -> writer.merge(key(1), value(2), ((existingKey, newKey, existingValue, newValue) -> {
                        existingValue.bytes = Arrays.copyOf(expected, expected.length);
                        return ValueMerger.MergeResult.MERGED;
                    })))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Index key-value size it too large");
        }

        try (var seek = index.seek(key(1), key(1), NULL_CONTEXT)) {
            assertTrue(seek.next());
            assertEqualsValue(value(1), seek.value());
        }

        consistencyCheckStrict(index);
    }

    @EnumSource(WriterFactory.class)
    @ParameterizedTest
    void shouldValidateSizeAfterReplace(WriterFactory writerFactory) throws Exception {
        try (var writer = createWriter(index, writerFactory)) {
            writer.put(key(1), value(1));
        }

        int targetSize = DynamicSizeUtil.keyValueSizeCapFromPageSize(payloadSize) + 1;

        byte[] expected = random.nextBytes(targetSize);

        try (var writer = createWriter(index, writerFactory)) {
            assertThatThrownBy(() -> writer.merge(key(1), value(2), ((existingKey, newKey, existingValue, newValue) -> {
                        newValue.bytes = Arrays.copyOf(expected, expected.length);
                        return ValueMerger.MergeResult.REPLACED;
                    })))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Index key-value size it too large");
        }

        try (var seek = index.seek(key(1), key(1), NULL_CONTEXT)) {
            assertTrue(seek.next());
            assertEqualsValue(value(1), seek.value());
        }

        consistencyCheckStrict(index);
    }
}
