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

import static java.math.BigInteger.probablePrime;
import static org.neo4j.internal.batchimport.cache.idmapping.IdMapper.ID_NOT_FOUND;
import static org.neo4j.internal.helpers.Numbers.ceilingPowerOfTwo;
import static org.neo4j.io.IOUtils.closeAllUnchecked;
import static org.neo4j.util.Preconditions.checkArgument;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.neo4j.internal.batchimport.cache.LongArray;
import org.neo4j.internal.batchimport.cache.NumberArrayFactory;
import org.neo4j.internal.batchimport.cache.idmapping.IdMapper;
import org.neo4j.internal.helpers.ArrayUtil;
import org.neo4j.memory.MemoryTracker;

/**
 * A lock-free implementation of a cuckoo table that support concurrent inserts and gets.
 * Do note that the memory guarantees for concurrent inserts together with reads are non-existing.
 * It's loosely based on the paper
 * <a href="https://www.cse.chalmers.se/~tsigas/papers/ICDCS14.pdf">Lock-free Cuckoo Hashing</a>
 * by Nhan Nguyen and Philippas Tsigas. There is some change to the algorithm since the published
 * code contains a logical error in the {@code help_relocate} method. "Program 7 line 174" in the
 * paper should check {@code src = dst & dst is not marked}, otherwise the marked bit is risking
 * being clobbered.
 * <p>
 * The values must be unique. Values are 6 bytes wide, so we can store up to 281 trillion node id's
 * which should last for quite some time.
 * <p>
 * The {@code expectedNumberOfEntries} must be somewhat accurate for this implementation to function.
 * It starts out with 4 backing tables, which should statistically allow a
 * load factor of 96% while not degrading in performance. Since this is not guaranteed,
 * there is a fallback that will add additional tables. This addition, however, is not lock-free.
 * <p>
 * To allow for CAS we operate on {@code long}s, thus we have 2 bytes for metadata. The layout of
 * the values are:
 * <pre>
 *     ┏━ Relocation bit. Set if a relocation is in progress.
 *     ┃ ┏━ Relocation destination.
 *     ┃ ┃     ┏━ Partial key.
 *     ┃ ┃     ┃             ┏━ Value.
 *     ┃┏┻┓┏━━━┻━━━━━━━┓ ┏━━━┻━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
 *     RDDDPPPP PPPPPPPP VVVVVVVV VVVVVVVV VVVVVVVV VVVVVVVV VVVVVVVV VVVVVVVV
 *     1        2        3        4        5        6        7        8
 * </pre>
 * The partial key is used to avoid unnecessary lookups for keys that does not match the partial,
 * significantly reducing the number page faults.
 */
public class CuckooTable implements AutoCloseable {
    private static final double INITIAL_LOAD_FACTOR = 0.95;
    private static final int INITIAL_TABLES = 4;
    private static final int MAX_TABLES = 8;
    private static final long RELOCATING_BIT = 0x8000000000000000L;
    private static final long VALUE_MASK = 0x0000FFFFFFFFFFFFL;
    private static final long DESTINATION_MASK = 0x7000000000000000L;
    private static final int DESTINATION_SHIFT = 60;
    private static final int PARTIAL_KEY_MASK = 0xFFF;
    private static final int PARTIAL_KEY_SHIFT = 48;
    private static final int MAX_RELOCATIONS = 20;
    private static final long EMPTY_ENTRY = 0;

    private final long tablesSize;
    private final NumberArrayFactory arrayFactory;
    private final MemoryTracker memoryTracker;
    private final int hashShift;

    private final AtomicInteger currentNumberOfTables;
    private final LongArray[] tables;
    private final LongArray keys;
    private final RandomMultiplicativeHashing[] hashing;
    private final AtomicLong zeroValue = new AtomicLong(ID_NOT_FOUND);

    public CuckooTable(long expectedNumberOfEntries, NumberArrayFactory arrayFactory, MemoryTracker memoryTracker) {
        tablesSize = calculateTableSize(expectedNumberOfEntries);
        this.arrayFactory = arrayFactory;
        this.memoryTracker = memoryTracker;
        hashShift = Long.SIZE - Long.numberOfTrailingZeros(tablesSize);
        int chunkSize = Math.clamp(expectedNumberOfEntries, 1, ArrayUtil.MAX_ARRAY_SIZE);
        keys = arrayFactory.newDynamicLongArray(chunkSize, 0, memoryTracker);
        currentNumberOfTables = new AtomicInteger(INITIAL_TABLES);
        tables = new LongArray[MAX_TABLES];
        hashing = new RandomMultiplicativeHashing[MAX_TABLES];
        for (int i = 0; i < INITIAL_TABLES; i++) {
            tables[i] = arrayFactory.newLongArray(tablesSize, 0, memoryTracker);
            hashing[i] = new RandomMultiplicativeHashing(hashShift);
        }
    }

    @Override
    public void close() {
        closeAllUnchecked(keys, () -> closeAllUnchecked(tables));
    }

    /**
     * Lookup a value from the table.
     *
     * @param key key to look for.
     * @return the value associated with {@code key} or {@link IdMapper#ID_NOT_FOUND} if key was not found.
     */
    public long get(long key) {
        if (key == 0) {
            return zeroValue.getPlain();
        }

        int num = currentNumberOfTables.getPlain();
        int partialKey = keyToPartial(key);
        for (int i = 0; i < num; i++) {
            long entry = tables[i].get(hashing[i].hash(key));
            if (isInUse(entry) && getPartialKey(entry) == partialKey) {
                long value = getValue(entry);
                if (keys.get(value) == key) {
                    return value;
                }
            }
        }
        return ID_NOT_FOUND;
    }

    /**
     * Insert a value into the table.
     *
     * @param key key to insert.
     * @param value value to associate with the key.
     */
    public void insert(final long key, final long value) throws KeyCollisionException {
        checkValue(value);

        if (key == 0) {
            if (zeroValue.compareAndSet(ID_NOT_FOUND, value)) {
                return;
            } else {
                throw new KeyCollisionException(key);
            }
        }

        if (!keys.compareAndSet(value, 0, key)) {
            throw new IllegalArgumentException("Non unique value inserted " + value);
        }
        int partialKey = keyToPartial(key);
        while (true) {
            int numberOfTables = currentNumberOfTables.get();
            // Try to insert into empty slot
            if (insertToEmptySlot(key, value, partialKey, numberOfTables)) {
                return;
            }

            // No empty slot, make one!
            if (!relocate(key, numberOfTables)) {
                // Unable to find an empty slot, grow table and try again
                expand(numberOfTables);
            }
        }
    }

    /**
     * Removes a previously inserted value from the table.
     * This method isn't thread safe, in that it isn't safe to run concurrently with other calls
     * to {@link #insert(long, long)}.
     *
     * @param value value previously inserted.
     * @return whether the value was removed, i.e. if it existed in the table.
     */
    public boolean remove(final long value) {
        checkValue(value);

        long key = keys.get(value);
        if (key == 0) {
            zeroValue.set(ID_NOT_FOUND);
            return false;
        }

        keys.set(value, 0);
        deleteValue(key, value, currentNumberOfTables.getPlain());
        return true;
    }

    private static void checkValue(long value) {
        checkArgument(
                (value & VALUE_MASK) == value,
                "Value must be in the range: 0 <= value <= " + VALUE_MASK + " but was " + (value & VALUE_MASK));
    }

    private boolean insertToEmptySlot(final long key, final long value, final int partial, int numberOfTables)
            throws KeyCollisionException {
        while (true) {
            int candidateTable = -1;
            long candidateEntry = -1;
            long candidateHash = -1;
            for (int i = 0; i < numberOfTables; i++) {
                long hash = hashing[i].hash(key);
                long entry = tables[i].get(hash);

                if (isRelocating(entry)) {
                    helpMove(i, hash, entry);
                    continue;
                }

                if (isInUse(entry)) {
                    // Already occupied, make sure it's not a collision
                    if (getPartialKey(entry) == partial) {
                        long currentValue = getValue(entry);
                        if (keys.get(currentValue) == key) {
                            keys.set(value, 0);
                            throw new KeyCollisionException(key);
                        }
                    }
                } else {
                    if (candidateTable == -1) {
                        candidateTable = i;
                        candidateEntry = entry;
                        candidateHash = hash;
                    }
                }
            }

            if (candidateTable != -1) {
                if (!tables[candidateTable].compareAndSet(candidateHash, candidateEntry, makeEntry(value, partial))) {
                    // Someone manage to insert into the empty slot before us, retry
                    continue;
                }
                int postNum = currentNumberOfTables.get();
                if (postNum != numberOfTables) {
                    // The number of tables increased while we were inserting.
                    deleteValue(key, value, numberOfTables);
                    numberOfTables = postNum;
                    continue;
                }
                if (!validateNoDuplicates(key, partial, postNum)) {
                    // Duplicate keys found, this happens if the same key is inserted concurrently.
                    // Delete our key and retry.
                    deleteValue(key, value, numberOfTables);
                    continue;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    private void deleteValue(long key, long value, int numberOfTables) {
        restart:
        while (true) {
            // Re-read because another thread may have expanded the backing tables and relocated
            // our entry into one of the newly-added tables — tables[numberOfTables..current-1] —
            // which the caller-supplied bound would not cover, causing a livelock.
            numberOfTables = currentNumberOfTables.get();
            for (int i = 0; i < numberOfTables; i++) {
                long hash = hashing[i].hash(key);
                long entry = tables[i].get(hash);
                if (isRelocating(entry)) {
                    helpMove(i, hash, entry);
                    continue restart;
                }
                if (!isInUse(entry)) {
                    continue;
                }

                long currentValue = getValue(entry);
                if (currentValue == value) {
                    if (tables[i].compareAndSet(hash, entry, EMPTY_ENTRY)) {
                        return;
                    }
                    continue restart;
                }
            }
        }
    }

    /**
     * Validate that our insert is the only one for this key.
     *
     * @param key the key.
     * @param numberOfTables number of backing tables at the start of this insertions
     * @return {@code true} if there are no duplicates, {@code false} otherwise.
     */
    private boolean validateNoDuplicates(long key, int partial, int numberOfTables) {
        restart:
        while (true) {
            int count = 0;
            for (int i = 0; i < numberOfTables; i++) {
                long hash = hashing[i].hash(key);
                long entry = tables[i].get(hash);
                if (isRelocating(entry)) {
                    helpMove(i, hash, entry);
                    continue restart;
                }
                if (!isInUse(entry)) {
                    continue;
                }

                if (getPartialKey(entry) == partial) {
                    long currentValue = getValue(entry);
                    long currentKey = keys.get(currentValue);

                    if (currentKey == key) {
                        count++;
                    }
                }
            }

            return count <= 1;
        }
    }

    /**
     * This will create an empty slot for the provided key. This is done in two stages, first a
     * cuckoo path is discovered, then the operations are done in reverse order. This ensures
     * that there is never any missing values in the tables, since it's only the "zero" that is
     * moved between places.
     *
     * @param key the key to find a free slot for.
     * @param numberOfTables number of backing tables at the start of this insertions
     * @return {@code true} if we managed to find an empty slot and move it into place. If {@code false}
     *          is returned, this implies that we have run into a cycle in the cuckoo graph, and we need to
     *          expand that backing arrays to resolve this.
     */
    private boolean relocate(long key, int numberOfTables) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        long[] route = new long[MAX_RELOCATIONS * 2];
        boolean found;

        path_discovery:
        while (true) {
            int tbl = nextRandomTable(-1, numberOfTables, rnd);
            long idx = hashing[tbl].hash(key);
            found = false;
            int depth = 0;
            do {
                long entry = tables[tbl].get(idx);

                while (isRelocating(entry)) {
                    helpMove(tbl, idx, entry);
                    entry = tables[tbl].get(idx);
                }

                route[depth * 2] = tbl;
                route[depth * 2 + 1] = idx;

                if (isInUse(entry)) {
                    tbl = nextRandomTable(tbl, numberOfTables, rnd);
                    idx = hashing[tbl].hash(keys.get(getValue(entry)));
                } else {
                    found = true;
                }
            } while (!found && ++depth < MAX_RELOCATIONS);

            if (found) {
                for (int i = depth; i >= 0; i--) {
                    int srcTbl = (int) route[i * 2];
                    long srcIdx = route[i * 2 + 1];
                    long srcEntry = tables[srcTbl].get(srcIdx);

                    if (isRelocating(srcEntry)) {
                        helpMove(srcTbl, srcIdx, srcEntry);
                        // Someone else is relocating the entry, so we have to find another path
                        continue path_discovery;
                    }

                    if (isInUse(srcEntry)) {
                        long destEntry = tables[tbl].get(idx);
                        if (isInUse(destEntry)) {
                            continue path_discovery; // Some joinked our 0! We have to find another one...
                        }
                        moveEntry(srcTbl, srcIdx, tbl, srcEntry);
                    }
                    tbl = srcTbl;
                    idx = srcIdx;
                }
            }
            break;
        }

        return found;
    }

    /**
     * Mark the entry for relocation and move it.
     */
    private void moveEntry(int srcTbl, long srcIndex, int destTbl, long srcEntry) {
        while (!isRelocating(srcEntry)) {
            srcEntry = tables[srcTbl].compareAndExchange(srcIndex, srcEntry, setRelocating(srcEntry, destTbl));
            if (!isInUse(srcEntry)) {
                return;
            }
        }
        move(srcTbl, srcIndex, destTbl, srcEntry);
    }

    /**
     * Help another thread move an entry.
     */
    private void helpMove(int srcTbl, long srcIndex, long srcEntry) {
        move(srcTbl, srcIndex, getDestinationTable(srcEntry), srcEntry);
    }

    /**
     * Move an entry from {@code srcTbl} to {@code destTbl}.
     * <pre>
     * (1) If this CAS fails, that indicates one of two things. Either someone else helped us move our entry, or
     *     someone else managed to insert a different entry before us.
     * (2) Since the previous CAS was successful we now can empty the source slot. At this point we don't care if
     *     this succeeds or not, because if it fails that means that someone else managed to CAS in (3).
     * (3) If we find the value in both the source and the target slot, that means that we have observed (1) but
     *     not (2) yet. Here we help out by clearing the source slot since we probably already have the cache
     *     -line loaded in this thread.
     * (4) If we get here we might have failed the move. If we failed we have to clear the relocation bit. This
     *     CAS will only succeed if neither (2) nor (3) was executed before. But the CAS can still fail
     *     if someone else did (4) before us.
     * </pre>
     */
    private void move(int srcTbl, long srcIndex, int destTbl, long srcEntry) {
        long srcValue = getValue(srcEntry);
        long destHash = hashing[destTbl].hash(keys.get(srcValue));
        long destEntry = tables[destTbl].get(destHash);

        if (!isInUse(destEntry)) {
            if (tables[destTbl].compareAndSet(destHash, EMPTY_ENTRY, clearRelocating(srcEntry))) { // (1)
                tables[srcTbl].compareAndSet(srcIndex, srcEntry, EMPTY_ENTRY); // (2)
                return;
            }
        }

        if (srcValue == getValue(destEntry) && !isRelocating(destEntry)) {
            tables[srcTbl].compareAndSet(srcIndex, srcEntry, EMPTY_ENTRY); // (3)
            return;
        }

        tables[srcTbl].compareAndSet(srcIndex, srcEntry, clearRelocating(srcEntry)); // (4)
    }

    private synchronized void expand(int oldNumberOfTables) {
        int n = currentNumberOfTables.get();
        if (n > oldNumberOfTables) {
            return; // Someone else resized before us.
        }
        hashing[oldNumberOfTables] = new RandomMultiplicativeHashing(hashShift);
        LongArray newTables = arrayFactory.newLongArray(tablesSize, 0, memoryTracker);
        tables[oldNumberOfTables] = newTables;

        currentNumberOfTables.incrementAndGet();
    }

    private static long calculateTableSize(long expectedNumberOfEntries) {
        double capacity = expectedNumberOfEntries / INITIAL_LOAD_FACTOR;
        long minCapacityPerTable = (long) (capacity / INITIAL_TABLES);
        return ceilingPowerOfTwo(Math.max(minCapacityPerTable, 1024L));
    }

    private static int nextRandomTable(int tbl, int numberOfTables, ThreadLocalRandom rnd) {
        int t = tbl;
        while (t == tbl) {
            t = rnd.nextInt(numberOfTables);
        }
        return t;
    }

    private static int keyToPartial(long key) {
        int i = Long.hashCode(key);
        int a = (i >>> 16) ^ (i & 0xFFFF);
        return a & CuckooTable.PARTIAL_KEY_MASK;
    }

    private static int getPartialKey(long entry) {
        return (int) ((entry >>> PARTIAL_KEY_SHIFT) & CuckooTable.PARTIAL_KEY_MASK);
    }

    private static long getValue(long entry) {
        return entry & VALUE_MASK;
    }

    private static boolean isInUse(long entry) {
        return entry != EMPTY_ENTRY;
    }

    private static boolean isRelocating(long entry) {
        return (entry & RELOCATING_BIT) != 0;
    }

    private static long setRelocating(long entry, long destTbl) {
        return entry & ~DESTINATION_MASK | RELOCATING_BIT | (destTbl << DESTINATION_SHIFT);
    }

    private static long clearRelocating(long entry) {
        return entry & ~RELOCATING_BIT | DESTINATION_MASK;
    }

    private static int getDestinationTable(long entry) {
        return (int) ((entry & DESTINATION_MASK) >>> DESTINATION_SHIFT);
    }

    private static long makeEntry(long value, int partialKey) {
        // The destination is unused when RELOCATING_BIT is not set, so we add it here to make sure that the
        // entry will not be equal to EMPTY_ENTRY(0) even if both the partial key and value is 0
        return ((((long) partialKey) << PARTIAL_KEY_SHIFT) | (value & VALUE_MASK) | DESTINATION_MASK);
    }

    /**
     * Fastest hashing(tm)
     */
    private static final class RandomMultiplicativeHashing {
        private final long shift;
        private final long factor;

        private RandomMultiplicativeHashing(int shift) {
            this.shift = shift;
            this.factor = probablePrime(64, ThreadLocalRandom.current()).longValue();
        }

        private long hash(long key) {
            return (factor * key) >>> shift;
        }
    }
}
