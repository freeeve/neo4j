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
package org.neo4j.internal.batchimport.cache.idmapping.cuckoo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.neo4j.internal.batchimport.cache.NumberArrayFactories.OFF_HEAP;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.batchimport.api.PropertyValueLookup;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.batchimport.api.input.ReadableGroups;
import org.neo4j.internal.batchimport.cache.idmapping.IdMapper;
import org.neo4j.internal.batchimport.input.Groups;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.test.Race;

class CuckooIdMapperTest {
    private final Group emptyGroup = new Groups().getOrCreate(null);

    private static Stream<Integer> processors() {
        Collection<Integer> data = new ArrayList<>();
        data.add(1);
        data.add(2);
        int bySystem = Runtime.getRuntime().availableProcessors() - 1;
        if (bySystem > 2) {
            data.add(bySystem);
        }
        return data.stream();
    }

    @Test
    void simpleLong() throws KeyCollisionException {
        try (IdMapper idMapper = getCuckooIdMapperForLongs()) {
            IdMapper.Setter setter = idMapper.newSetter(0);
            setter.put(10L, 0, emptyGroup);
            setter.put(25L, 1, emptyGroup);
            setter.put(79L, 2, emptyGroup);
            setter.put(2L, 3, emptyGroup);
            setter.put(100L, 4, emptyGroup);
            setter.put(5000L, 5, emptyGroup);
            IdMapper.Getter getter = idMapper.newGetter(0);
            assertThat(getter.get(10L, emptyGroup)).isEqualTo(0);
            assertThat(getter.get(25L, emptyGroup)).isEqualTo(1);
            assertThat(getter.get(79L, emptyGroup)).isEqualTo(2);
            assertThat(getter.get(2L, emptyGroup)).isEqualTo(3);
            assertThat(getter.get(100L, emptyGroup)).isEqualTo(4);
            assertThat(getter.get(5000L, emptyGroup)).isEqualTo(5);
            assertThat(getter.get(3L, emptyGroup)).isEqualTo(IdMapper.ID_NOT_FOUND);
        }
    }

    @Test
    void simpleString() throws KeyCollisionException {
        Map<Long, String> values = Map.of(2L, "foo", 3L, "bar", 4L, "baz", 5L, "kaz", 6L, "taz", 7L, "maz");
        try (IdMapper idMapper = getCuckooIdMapperForString(values)) {
            IdMapper.Setter setter = idMapper.newSetter(0);
            setter.put("foo", 2, emptyGroup);
            setter.put("bar", 3, emptyGroup);
            setter.put("baz", 4, emptyGroup);
            setter.put("kaz", 5, emptyGroup);
            setter.put("taz", 6, emptyGroup);
            setter.put("maz", 7, emptyGroup);
            IdMapper.Getter getter = idMapper.newGetter(0);
            assertThat(getter.get("foo", emptyGroup)).isEqualTo(2);
            assertThat(getter.get("bar", emptyGroup)).isEqualTo(3);
            assertThat(getter.get("baz", emptyGroup)).isEqualTo(4);
            assertThat(getter.get("kaz", emptyGroup)).isEqualTo(5);
            assertThat(getter.get("taz", emptyGroup)).isEqualTo(6);
            assertThat(getter.get("maz", emptyGroup)).isEqualTo(7);
            assertThat(getter.get("sna", emptyGroup)).isEqualTo(IdMapper.ID_NOT_FOUND);
        }
    }

    @Test
    void dontAllowDuplicateActualIds() throws KeyCollisionException {
        try (IdMapper idMapper = getCuckooIdMapperForString(Map.of(2L, "foo"))) {
            idMapper.newSetter(0).put("foo", 2, emptyGroup);
            assertThatCode(() -> idMapper.newSetter(0).put("bar", 2, emptyGroup))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void detectDuplicateKeys() throws KeyCollisionException {
        try (IdMapper idMapper = getCuckooIdMapperForString(Map.of(2L, "foo"))) {
            idMapper.newSetter(0).put("foo", 2, emptyGroup);
            assertThatCode(() -> idMapper.newSetter(0).put("foo", 3, emptyGroup))
                    .isInstanceOf(KeyCollisionException.class);
        }
    }

    @Test
    void handleStringHashCollisions() throws KeyCollisionException {
        // Force a hash collision between "bar" and "baz"
        StringHash badHash = key -> switch (key) {
            case "foo" -> 1234;
            case "bar", "baz" -> 4321;
            default -> throw new AssertionError("Unexpected key: " + key);
        };
        Map<Long, String> values = Map.of(1234L, "foo", 4321L, "bar");

        try (IdMapper idMapper = getCuckooIdMapperForString(values, badHash)) {
            IdMapper.Setter setter = idMapper.newSetter(0);
            setter.put("foo", 2, emptyGroup);
            setter.put("bar", 3, emptyGroup);
            setter.put("baz", 4, emptyGroup);
            IdMapper.Getter getter = idMapper.newGetter(0);
            assertThat(getter.get("foo", emptyGroup)).isEqualTo(2);
            assertThat(getter.get("bar", emptyGroup)).isEqualTo(3);
            assertThat(getter.get("baz", emptyGroup)).isEqualTo(4);
        }
    }

    @Test
    void handleGroups() throws KeyCollisionException {
        Groups groups = new Groups();
        Group g1 = groups.getOrCreate("G1");
        Group g2 = groups.getOrCreate("G2");
        Group g3 = groups.getOrCreate("G3");
        try (IdMapper idMapper = new CuckooIdMapper(10, OFF_HEAP, groups, EmptyMemoryTracker.INSTANCE)) {
            IdMapper.Setter setter = idMapper.newSetter(0);
            setter.put(10L, 2, g1);
            setter.put(10L, 3, g2);
            setter.put(10L, 4, g3);
            IdMapper.Getter getter = idMapper.newGetter(0);
            assertThat(getter.get(10L, g1)).isEqualTo(2);
            assertThat(getter.get(10L, g2)).isEqualTo(3);
            assertThat(getter.get(10L, g3)).isEqualTo(4);
        }
    }

    @Test
    void handleManyGroups() throws KeyCollisionException {
        Groups groups = new Groups();
        int size = 512;
        for (int i = 0; i < size; i++) {
            groups.getOrCreate("" + i);
        }
        try (IdMapper mapper = new StringCuckooIdMapper(size, OFF_HEAP, groups, INSTANCE, null)) {
            IdMapper.Setter setter = mapper.newSetter(0);
            for (int i = 0; i < size; i++) {
                setter.put(i, i, groups.get("" + i));
            }

            try (var getter = mapper.newGetter(0)) {
                for (int i = 0; i < size; i++) {
                    assertThat(getter.get(i, groups.get("" + i))).isEqualTo(i);
                }
            }
        }
    }

    @Test
    void emptyStringShouldNotBeFound() throws KeyCollisionException {
        try (IdMapper mapper = getCuckooIdMapperForString(Map.of(1L, "1"))) {
            mapper.newSetter(0).put("1", 1, emptyGroup);

            try (var getter = mapper.newGetter(0)) {
                assertThat(getter.get("1", emptyGroup)).isEqualTo(1);
                assertThat(getter.get("", emptyGroup)).isEqualTo(IdMapper.ID_NOT_FOUND);
            }
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("processors")
    public void shouldPutFromMultipleThreads(int processors) throws Throwable {
        // GIVEN
        long countPerThread = 30_000;
        try (IdMapper idMapper =
                new StringCuckooIdMapper(countPerThread * processors, OFF_HEAP, ReadableGroups.EMPTY, INSTANCE, null)) {
            AtomicLong highNodeId = new AtomicLong();
            long batchSize = 1234;
            Race race = new Race();
            race.addContestants(processors, c -> () -> {
                long cursor = batchSize;
                long nextNodeId = 0;
                IdMapper.Setter setter = idMapper.newSetter(c);
                for (int j = 0; j < countPerThread; j++) {
                    if (cursor == batchSize) {
                        nextNodeId = highNodeId.getAndAdd(batchSize);
                        cursor = 0;
                    }
                    long nodeId = nextNodeId++;
                    cursor++;

                    try {
                        setter.put(String.valueOf(nodeId), nodeId, emptyGroup);
                    } catch (KeyCollisionException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            // WHEN
            race.go();

            // THEN
            long count = processors * countPerThread;
            long countWithGapsWorstCase = count + batchSize * processors;
            int correctHits = 0;
            try (var getter = idMapper.newGetter(0)) {
                for (long nodeId = 0; nodeId < countWithGapsWorstCase; nodeId++) {
                    long result = getter.get(String.valueOf(nodeId), emptyGroup);
                    if (result != -1) {
                        assertThat(result).isEqualTo(nodeId);
                        correctHits++;
                    }
                }
            }
            assertThat(correctHits).isEqualTo(count);
        }
    }

    private static CuckooIdMapper getCuckooIdMapperForLongs() {
        return new CuckooIdMapper(10, OFF_HEAP, ReadableGroups.EMPTY, EmptyMemoryTracker.INSTANCE);
    }

    private static CuckooIdMapper getCuckooIdMapperForString(Map<Long, String> stringStore) {
        return new StringCuckooIdMapper(
                10, OFF_HEAP, ReadableGroups.EMPTY, EmptyMemoryTracker.INSTANCE, new InMemoryLookup(stringStore));
    }

    private static CuckooIdMapper getCuckooIdMapperForString(Map<Long, String> stringStore, StringHash stringHash) {
        return new StringCuckooIdMapper(
                10,
                OFF_HEAP,
                ReadableGroups.EMPTY,
                EmptyMemoryTracker.INSTANCE,
                stringHash,
                new InMemoryLookup(stringStore));
    }

    private static class InMemoryLookup implements PropertyValueLookup {
        private final Map<Long, String> store;

        private InMemoryLookup(Map<Long, String> store) {
            this.store = store;
        }

        @Override
        public Lookup newLookup(boolean readOnly) {
            return new Lookup() {
                @Override
                public Object lookupProperty(long nodeId, MemoryTracker memoryTracker) {
                    return store.get(nodeId);
                }

                @Override
                public void close() {}
            };
        }
    }
}
