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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.neo4j.internal.batchimport.cache.NumberArrayFactories;
import org.neo4j.internal.batchimport.cache.NumberArrayFactory;
import org.neo4j.internal.batchimport.cache.idmapping.IdMapper;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.test.Race;
import org.neo4j.test.extension.testdirectory.TestDirectoryExtension;

@TestDirectoryExtension
class CuckooTableTest {

    @Test
    void simple() throws KeyCollisionException {
        try (CuckooTable cuckooTable = new CuckooTable(512, getArrayFactory(), EmptyMemoryTracker.INSTANCE)) {
            cuckooTable.insert(0, 11);
            cuckooTable.insert(1, 0);
            cuckooTable.insert(2, 12);
            cuckooTable.insert(3, 13);

            assertThat(cuckooTable.get(0)).isEqualTo(11);
            assertThat(cuckooTable.get(1)).isZero();
            assertThat(cuckooTable.get(2)).isEqualTo(12);
            assertThat(cuckooTable.get(3)).isEqualTo(13);
            assertThat(cuckooTable.get(4)).isEqualTo(IdMapper.ID_NOT_FOUND);
        }
    }

    @Test
    void randomInteractions() throws KeyCollisionException {
        int n = 1000000;
        Random rnd = new Random();
        ArrayList<Long> insertedKeys = new ArrayList<>();
        Set<Long> duplicates = new HashSet<>();
        try (CuckooTable cuckooTable = new CuckooTable(n, getArrayFactory(), EmptyMemoryTracker.INSTANCE)) {
            for (int i = 0; i < n; i++) {
                long key = rnd.nextInt();
                while (!duplicates.add(key)) {
                    long k = key;
                    long id = n + 1 + i;
                    assertThatCode(() -> cuckooTable.insert(k, id)).isInstanceOf(KeyCollisionException.class);
                    key = rnd.nextInt();
                }
                insertedKeys.add(key);
                cuckooTable.insert(key, i + 1);
            }

            for (int i = 0; i < insertedKeys.size(); i++) {
                assertThat(cuckooTable.get(insertedKeys.get(i))).isEqualTo(i + 1);
            }
        }
    }

    @Test
    void concurrentInteractions() throws Throwable {
        int n = 500_000;
        int t = 40;
        long[][] keys = getRandomMatrixKeys(t, n);
        int totalSize = n * t;
        try (CuckooTable cuckooTable = new CuckooTable(totalSize, getArrayFactory(), EmptyMemoryTracker.INSTANCE)) {
            Race race = new Race();
            for (int i = 0; i < t; i++) {
                long[] threadKeys = keys[i];
                long threadId = i;
                race.addContestant(() -> {
                    for (int j = 0; j < threadKeys.length; j++) {
                        long l = threadKeys[j];
                        long id = threadId * n + j;
                        try {
                            cuckooTable.insert(l, id + 1);
                        } catch (KeyCollisionException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
            race.go();

            race = new Race();
            for (int i = 0; i < t; i++) {
                long[] key = keys[i];
                long threadId = i;
                race.addContestant(() -> {
                    for (int j = 0; j < n; j++) {
                        long id = threadId * n + j;
                        long k = key[j];
                        assertThat(cuckooTable.get(k)).isEqualTo(id + 1);
                    }
                });
            }
            race.go();
        }
    }

    @Test
    void concurrentCollisionDetection() throws Throwable {
        int n = 1024 * 8;
        int t = 40;
        ArrayList<Long> keys = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            keys.add((long) i);
        }

        try (CuckooTable cuckooTable =
                new CuckooTable((long) (n * 0.5), getArrayFactory(), EmptyMemoryTracker.INSTANCE)) {
            long[] counts = new long[t];
            Race race = new Race();
            for (int i = 0; i < t; i++) {
                ArrayList<Long> threadList = new ArrayList<>(keys);
                Collections.shuffle(threadList);
                int threadId = i;
                race.addContestant(() -> {
                    for (int j = 0; j < threadList.size(); j++) {
                        long l = threadList.get(j);
                        long id = threadId * n + j;
                        try {
                            cuckooTable.insert(l, id);
                        } catch (KeyCollisionException e) {
                            counts[threadId]++;
                        }
                    }
                });
            }
            race.go();

            long sum = Arrays.stream(counts).sum();
            assertThat(sum).isEqualTo(n * (t - 1));
        }
    }

    @Test
    void shouldRemoveInsertedEntry() throws KeyCollisionException {
        // given
        try (var table = new CuckooTable(100, getArrayFactory(), EmptyMemoryTracker.INSTANCE)) {
            long key = 3;
            long value = 5;
            table.insert(key, value);

            // when
            boolean removed = table.remove(value);

            // then
            assertThat(removed).isTrue();
            assertThat(table.remove(value)).isFalse();
        }
    }

    private static long[][] getRandomMatrixKeys(int t, int n) {
        Random rnd = new Random();
        long[][] keys = new long[t][];
        for (int i = 0; i < t; i++) {
            keys[i] = new long[n];
        }

        for (int i = 0; i < t; i++) {
            for (int j = 0; j < n; j++) {
                keys[i][j] = Math.abs(rnd.nextLong());
            }
        }
        return keys;
    }

    private NumberArrayFactory getArrayFactory() {
        return NumberArrayFactories.OFF_HEAP;
    }
}
