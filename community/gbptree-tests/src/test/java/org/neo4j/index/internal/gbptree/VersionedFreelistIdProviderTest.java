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

import static java.nio.file.StandardOpenOption.CREATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.neo4j.index.internal.gbptree.FreeListIdProviderTest.assertEmpty;
import static org.neo4j.index.internal.gbptree.FreeListIdProviderTest.fillPageWithRandomBytes;
import static org.neo4j.index.internal.gbptree.FreeListIdProviderTest.readCursor;
import static org.neo4j.index.internal.gbptree.FreeListIdProviderTest.writeCursor;
import static org.neo4j.index.internal.gbptree.Generation.generation;
import static org.neo4j.index.internal.gbptree.Generation.stableGeneration;
import static org.neo4j.index.internal.gbptree.Generation.unstableGeneration;
import static org.neo4j.index.internal.gbptree.VersionSupportingFreelist.VersionSupportingFreelistStorage.ENTRY_SIZE;
import static org.neo4j.index.internal.gbptree.VersionSupportingFreelist.VersionSupportingFreelistStorage.VERSIONED_ENTRY_SIZE;
import static org.neo4j.index.internal.gbptree.VersionSupportingFreelist.VersionSupportingFreelistStorage.maxEntries;
import static org.neo4j.io.pagecache.context.CursorContext.NULL_CONTEXT;
import static org.neo4j.test.Race.throwing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.index.internal.gbptree.IdProvider.IdProviderVisitor;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.PagedFile;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.io.pagecache.context.VersionContext;
import org.neo4j.io.pagecache.tracing.cursor.PageCursorTracer;
import org.neo4j.test.Race;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomSupportExtension;
import org.neo4j.test.extension.pagecache.EphemeralPageCacheExtension;
import org.neo4j.test.utils.TestDirectory;

@EphemeralPageCacheExtension
@RandomSupportExtension
class VersionedFreelistIdProviderTest {
    private static final int PAYLOAD_SIZE = 128;
    private static final long GENERATION_ONE = GenerationSafePointer.MIN_GENERATION;
    private static final long GENERATION_TWO = GENERATION_ONE + 1;
    private static final long GENERATION_THREE = GENERATION_TWO + 1;
    private static final long BASE_ID = 5;
    private static final long FIRST_NEW_ID = BASE_ID + 2;

    @Inject
    private PageCache pageCache;

    @Inject
    TestDirectory directory;

    @Inject
    RandomSupport random;

    private final FreeListIdProviderTest.FreelistPageMonitor monitor = new FreeListIdProviderTest.FreelistPageMonitor();
    private VersionedFreelistIdProvider freelist;
    private PagedFile pagedFile;
    private long oldestVisibleVersion;
    private long releaseId;

    @BeforeEach
    void setUp() throws IOException {
        pagedFile = pageCache.map(directory.file("freelist"), PAYLOAD_SIZE, "db", Sets.immutable.of(CREATE));
        freelist = new VersionedFreelistIdProvider(pagedFile, monitor);
        freelist.initializeAfterCreation(writeCursor(pagedFile), BASE_ID);
        oldestVisibleVersion = 1;
        releaseId = 2;
    }

    @AfterEach
    void tearDown() {
        if (pagedFile != null) {
            pagedFile.close();
        }
    }

    @Test
    void shouldReleaseAndAcquireIdFromGenerationsList() throws IOException {
        // given
        long releasedId = 11;
        fillPageWithRandomBytes(pagedFile, releasedId);

        // when
        freelist.releaseId(GENERATION_ONE, GENERATION_TWO, releasedId, writeCursor(pagedFile), NULL_CONTEXT);
        freelist.flush(GENERATION_ONE, GENERATION_TWO, writeCursor(pagedFile), NULL_CONTEXT);
        long acquiredId = freelist.acquireNewId(GENERATION_TWO, writeCursor(pagedFile), NULL_CONTEXT);

        // then
        assertThat(acquiredId).isEqualTo(releasedId);
        try (var cursor = readCursor(pagedFile).create()) {
            cursor.next(acquiredId);
            assertEmpty(cursor);
        }
    }

    @Test
    void shouldReleaseAndAcquireIdFromVersionsList() throws IOException {
        // given
        long releasedId = 11;
        fillPageWithRandomBytes(pagedFile, releasedId);

        // when
        freelist.releaseIdWithVersion(
                GENERATION_ONE, GENERATION_TWO, releasedId, writeCursor(pagedFile), cursorContext());
        freelist.flush(GENERATION_ONE, GENERATION_TWO, writeCursor(pagedFile), cursorContext());

        oldestVisibleVersion++;
        long acquiredId = freelist.acquireNewId(GENERATION_TWO, writeCursor(pagedFile), cursorContext());

        // then
        assertThat(acquiredId).isEqualTo(releasedId);
        try (var cursor = readCursor(pagedFile).create()) {
            cursor.next(acquiredId);
            assertEmpty(cursor);
        }
    }

    @Test
    void shouldNotReleaseVisiblePageIds() throws IOException {
        // given
        long releasedId = 11;
        fillPageWithRandomBytes(pagedFile, releasedId);

        // when releasing with committing tx releasedId
        freelist.releaseIdWithVersion(
                GENERATION_ONE, GENERATION_TWO, releasedId, writeCursor(pagedFile), cursorContext());
        freelist.flush(GENERATION_ONE, GENERATION_TWO, writeCursor(pagedFile), cursorContext());

        // and the oldest visible tx is before release version
        long acquiredId = freelist.acquireNewId(GENERATION_TWO, writeCursor(pagedFile), cursorContext());

        // then acquire new releasedId, not the released one
        assertThat(acquiredId).isEqualTo(FIRST_NEW_ID);
    }

    @Test
    void shouldAcquireFromBothLists() throws IOException {
        // given
        long releasedId1 = 11;
        fillPageWithRandomBytes(pagedFile, releasedId1);
        long releasedId2 = 22;
        fillPageWithRandomBytes(pagedFile, releasedId2);

        // when releasing with and without version
        freelist.releaseId(GENERATION_ONE, GENERATION_TWO, releasedId1, writeCursor(pagedFile), cursorContext());
        freelist.releaseIdWithVersion(
                GENERATION_ONE, GENERATION_TWO, releasedId2, writeCursor(pagedFile), cursorContext());
        freelist.flush(GENERATION_ONE, GENERATION_TWO, writeCursor(pagedFile), cursorContext());

        // and the oldest visible version has passed
        oldestVisibleVersion++;
        long acquiredId1 = freelist.acquireNewId(GENERATION_TWO, writeCursor(pagedFile), cursorContext());
        long acquiredId2 = freelist.acquireNewId(GENERATION_TWO, writeCursor(pagedFile), cursorContext());

        // then acquire from both lists
        assertThat(acquiredId1).isEqualTo(releasedId2);
        assertThat(acquiredId2).isEqualTo(releasedId1);
    }

    @Test
    void shouldAcquireFromGenerationsListIfVersionedIdsAreStillVisible() throws IOException {
        // given
        long releasedId1 = 11;
        fillPageWithRandomBytes(pagedFile, releasedId1);
        long releasedId2 = 22;
        fillPageWithRandomBytes(pagedFile, releasedId2);

        // when releasing with committing tx id
        freelist.releaseId(GENERATION_ONE, GENERATION_TWO, releasedId1, writeCursor(pagedFile), cursorContext());
        freelist.releaseIdWithVersion(
                GENERATION_ONE, GENERATION_TWO, releasedId2, writeCursor(pagedFile), cursorContext());
        freelist.flush(GENERATION_ONE, GENERATION_TWO, writeCursor(pagedFile), cursorContext());

        // and the oldest visible version is before release version
        long acquiredId1 = freelist.acquireNewId(GENERATION_TWO, writeCursor(pagedFile), cursorContext());
        long acquiredId2 = freelist.acquireNewId(GENERATION_TWO, writeCursor(pagedFile), cursorContext());

        // then acquire from generation list
        assertThat(acquiredId1).isEqualTo(releasedId1);
        assertThat(acquiredId2).isEqualTo(FIRST_NEW_ID);
    }

    @Test
    void shouldFlushWithCorrectVersions() throws IOException {
        // given a few versioned release ids
        var expectedFreelistEntries = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            expectedFreelistEntries.add(new VersionSupportingFreelist.FreelistEntry(id, GENERATION_TWO, releaseId));
            fillPageWithRandomBytes(pagedFile, id);
            freelist.releaseIdWithVersion(GENERATION_ONE, GENERATION_TWO, id, writeCursor(pagedFile), cursorContext());
            releaseId++;
        }

        // when
        freelist.flush(GENERATION_ONE, GENERATION_TWO, writeCursor(pagedFile), cursorContext());

        // then
        var freelistEntries = new ArrayList<VersionSupportingFreelist.FreelistEntry>();
        IdProviderVisitor visitor = new IdProviderVisitor.Adaptor() {
            @Override
            public void freelistEntry(long pageId, long generation, long releaseVersion, int pos) {
                freelistEntries.add(new VersionSupportingFreelist.FreelistEntry(pageId, generation, releaseVersion));
            }
        };

        freelist.visitFreelist(visitor, readCursor(pagedFile));
        assertThat(freelistEntries).isEqualTo(expectedFreelistEntries);
    }

    @Test
    void shouldReleaseAndAcquireIdsFromMultiplePages() throws IOException {
        // given ids in multiple pages
        long firstId = freelist.acquireNewId(GENERATION_ONE, writeCursor(pagedFile), cursorContext());
        int numberOfVersionedIds = releaseUntilRotation(true, firstId);
        int numberOfGenerationIds = releaseUntilRotation(false, firstId + numberOfVersionedIds);
        freelist.flush(GENERATION_ONE, GENERATION_TWO, writeCursor(pagedFile), cursorContext());

        // when acquiring, then should get from lists
        oldestVisibleVersion++;
        for (long i = 0; i < numberOfVersionedIds + numberOfGenerationIds; i++) {
            long acquiredId = freelist.acquireNewId(GENERATION_TWO, writeCursor(pagedFile), cursorContext());
            assertThat(acquiredId).isEqualTo(firstId + i);
        }
    }

    @Test
    void shouldPutFreedFreelistPagesIntoFreelistAsWell() throws IOException {
        long versionFreelistId = BASE_ID;
        long generationFreelistId = BASE_ID + 1;
        long firstId = freelist.acquireNewId(GENERATION_ONE, writeCursor(pagedFile), cursorContext());
        int numberOfVersionedIds = releaseUntilRotation(true, firstId);
        int numberOfGenerationIds = releaseUntilRotation(false, firstId + numberOfVersionedIds);
        freelist.flush(GENERATION_ONE, GENERATION_TWO, writeCursor(pagedFile), cursorContext());

        oldestVisibleVersion++;
        for (int i = 0; i < numberOfVersionedIds + numberOfGenerationIds; i++) {
            long acquiredId = freelist.acquireNewId(GENERATION_TWO, writeCursor(pagedFile), cursorContext());
            assertThat(acquiredId).isEqualTo(firstId + i);
        }
        freelist.flush(GENERATION_ONE, GENERATION_TWO, writeCursor(pagedFile), cursorContext());

        assertThat(freelist.acquireNewId(GENERATION_THREE, writeCursor(pagedFile), cursorContext()))
                .isEqualTo(generationFreelistId);
        assertThat(freelist.acquireNewId(GENERATION_THREE, writeCursor(pagedFile), cursorContext()))
                .isEqualTo(versionFreelistId);
    }

    @Test
    void shouldStayBoundUnderStress() throws Exception {
        // GIVEN
        MutableLongSet acquired = new LongHashSet();
        List<Long> acquiredList = new ArrayList<>(); // for quickly finding random to remove
        long stableGeneration = GENERATION_ONE;
        long unstableGeneration = GENERATION_TWO;
        int iterations = 1000;

        // WHEN
        for (int i = 0; i < iterations; i++) {
            for (int j = 0; j < 10; j++) {
                if (random.nextBoolean()) {
                    // acquire
                    int count = random.intBetween(5, 10);
                    for (int k = 0; k < count; k++) {
                        long acquiredId =
                                freelist.acquireNewId(stableGeneration, writeCursor(pagedFile), cursorContext());
                        var added = acquired.add(acquiredId);
                        assertTrue(added);
                        acquiredList.add(acquiredId);
                    }
                } else {
                    // release
                    int count = random.intBetween(5, 20);
                    for (int k = 0; k < count && !acquired.isEmpty(); k++) {
                        long id = acquiredList.remove(random.nextInt(acquiredList.size()));
                        assertTrue(acquired.remove(id));
                        if (k % 2 == 0) {
                            freelist.releaseIdWithVersion(
                                    id, stableGeneration, id, writeCursor(pagedFile), cursorContext());
                        } else {
                            freelist.releaseId(
                                    stableGeneration, unstableGeneration, id, writeCursor(pagedFile), cursorContext());
                        }
                    }
                }
            }

            for (long id : acquiredList) {
                freelist.releaseId(stableGeneration, unstableGeneration, id, writeCursor(pagedFile), cursorContext());
            }
            acquiredList.clear();
            acquired.clear();

            // checkpoint, sort of
            stableGeneration = unstableGeneration;
            unstableGeneration++;

            // run next version, sort of
            oldestVisibleVersion = releaseId;
            releaseId++;
        }
    }

    @Test
    void shouldStayBoundUnderMultiThreadedStress() {
        // given
        AtomicInteger checkpoints = new AtomicInteger();
        AtomicInteger numIdsAcquired = new AtomicInteger();
        AtomicInteger numIdsAcquiredThisCheckpoint = new AtomicInteger();
        AtomicInteger highestNumIdsAcquiredInCheckpoint = new AtomicInteger();
        Race race = new Race().withEndCondition(() -> checkpoints.get() >= 100 && numIdsAcquired.get() >= 10_000);
        ReadWriteLock checkpointLock = new ReentrantReadWriteLock();
        AtomicLong generation = new AtomicLong(generation(GENERATION_ONE, GENERATION_TWO));
        race.addContestants(4, throwing(() -> {
            checkpointLock.readLock().lock();
            try {
                long gen = generation.get();
                long stableGeneration = stableGeneration(gen);
                long unstableGeneration = unstableGeneration(gen);
                int count = ThreadLocalRandom.current().nextInt(1, 10);
                long[] ids = new long[count];
                for (int i = 0; i < count; i++) {
                    ids[i] = freelist.acquireNewId(stableGeneration, writeCursor(pagedFile), cursorContext());
                }
                for (long id : ids) {
                    freelist.releaseId(
                            stableGeneration, unstableGeneration, id, writeCursor(pagedFile), cursorContext());
                }
                numIdsAcquiredThisCheckpoint.addAndGet(count);
            } finally {
                checkpointLock.readLock().unlock();
            }
        }));
        race.addContestant(throwing(() -> {
            Thread.sleep(ThreadLocalRandom.current().nextInt(10));
            checkpointLock.writeLock().lock();
            try {
                long gen = generation.get();
                long unstableGeneration = unstableGeneration(gen);
                freelist.flush(stableGeneration(gen), unstableGeneration, writeCursor(pagedFile), cursorContext());
                generation.set(generation(unstableGeneration, unstableGeneration + 1));
                checkpoints.incrementAndGet();
                int idsThisCheckpoint = numIdsAcquiredThisCheckpoint.getAndSet(0);
                numIdsAcquired.addAndGet(idsThisCheckpoint);
                highestNumIdsAcquiredInCheckpoint.set(
                        Integer.max(highestNumIdsAcquiredInCheckpoint.get(), idsThisCheckpoint));
            } finally {
                checkpointLock.writeLock().unlock();
            }
        }));

        // when
        race.goUnchecked();

        // then the last id of the freelist after all that should be >= 80% of the highest number of ids acquired in any
        // single checkpoint.
        // This accounts for actual freelist pages for storing the freelist entries (page size is small resulting in
        // roughly one freelist page per 12 ids)
        assertThat((double) highestNumIdsAcquiredInCheckpoint.get() / freelist.lastId())
                .isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void shouldVisitUnacquiredIdsFromBothLists() throws IOException {
        // GIVEN a couple of released ids
        MutableLongSet expected = new LongHashSet();
        for (int i = 0; i < 100; i++) {
            expected.add(freelist.acquireNewId(GENERATION_ONE, writeCursor(pagedFile), cursorContext()));
        }
        expected.forEach(id -> {
            try {
                if (id % 2 == 0) {
                    freelist.releaseIdWithVersion(
                            GENERATION_ONE, GENERATION_TWO, id, writeCursor(pagedFile), cursorContext());
                } else {
                    freelist.releaseId(GENERATION_ONE, GENERATION_TWO, id, writeCursor(pagedFile), cursorContext());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        freelist.flush(GENERATION_ONE, GENERATION_TWO, writeCursor(pagedFile), cursorContext());

        // and only a few acquired
        oldestVisibleVersion++;
        for (int i = 0; i < 10; i++) {
            long acquiredId = freelist.acquireNewId(GENERATION_TWO, writeCursor(pagedFile), cursorContext());
            assertTrue(expected.remove(acquiredId));
        }

        // WHEN/THEN
        freelist.visitFreelist(
                new IdProvider.IdProviderVisitor.Adaptor() {
                    @Override
                    public void freelistEntry(long pageId, long generation, long releaseVersion, int pos) {
                        assertTrue(expected.remove(pageId));
                    }
                },
                readCursor(pagedFile));
        assertTrue(expected.isEmpty());
    }

    @Test
    void shouldVisitFreelistPageIdsFromBothLists() throws IOException {
        // GIVEN a couple of released ids
        MutableLongSet expected = new LongHashSet();
        // Add the already allocated free-list page ids
        expected.add(BASE_ID);
        expected.add(BASE_ID + 1);
        monitor.set(new FreeListIdProvider.Monitor() {
            @Override
            public void acquiredFreelistPageId(long freelistPageId) {
                expected.add(freelistPageId);
            }
        });

        for (int i = 0; i < 100; i++) {
            long id = freelist.acquireNewId(GENERATION_ONE, writeCursor(pagedFile), cursorContext());
            if (id % 2 == 0) {
                freelist.releaseIdWithVersion(
                        GENERATION_ONE, GENERATION_TWO, id, writeCursor(pagedFile), cursorContext());
            } else {
                freelist.releaseId(GENERATION_ONE, GENERATION_TWO, id, writeCursor(pagedFile), cursorContext());
            }
        }
        freelist.flush(GENERATION_ONE, GENERATION_TWO, writeCursor(pagedFile), cursorContext());
        assertTrue(!expected.isEmpty());

        // WHEN/THEN
        freelist.visitFreelist(
                new IdProvider.IdProviderVisitor.Adaptor() {
                    @Override
                    public void beginFreelistPage(long pageId) {
                        assertTrue(expected.remove(pageId));
                    }
                },
                readCursor(pagedFile));
        assertTrue(expected.isEmpty());
    }

    @Test
    void shouldVisitUnreleasedFreelistPageIdsFromBothLists() throws IOException {
        // GIVEN a couple of released ids
        MutableLongSet expected = new LongHashSet();
        for (int i = 0; i < 10; i++) {
            long id = freelist.acquireNewId(GENERATION_ONE, writeCursor(pagedFile), cursorContext());
            if (id % 2 == 0) {
                freelist.releaseIdWithVersion(
                        GENERATION_ONE, GENERATION_TWO, id, writeCursor(pagedFile), cursorContext());
            } else {
                freelist.releaseId(GENERATION_ONE, GENERATION_TWO, id, writeCursor(pagedFile), cursorContext());
            }
            expected.add(id);
        }

        // WHEN/THEN
        freelist.visitFreelist(
                new IdProvider.IdProviderVisitor.Adaptor() {
                    @Override
                    public void freelistEntryFromReleaseCache(long pageId) {
                        assertTrue(expected.remove(pageId));
                    }
                },
                readCursor(pagedFile));
        assertTrue(expected.isEmpty());
    }

    private int releaseUntilRotation(boolean versioned, long startId) throws IOException {
        long releaseId = startId;
        int entrySize = versioned ? VERSIONED_ENTRY_SIZE : ENTRY_SIZE;
        int maxEntries = maxEntries(PAYLOAD_SIZE, entrySize);
        int entries = maxEntries + maxEntries / 2;

        // Until rotation
        for (int i = 0; i < entries; i++) {
            if (versioned) {
                freelist.releaseIdWithVersion(
                        GENERATION_ONE, GENERATION_TWO, releaseId++, writeCursor(pagedFile), cursorContext());
            } else {
                freelist.releaseId(
                        GENERATION_ONE, GENERATION_TWO, releaseId++, writeCursor(pagedFile), cursorContext());
            }
        }

        return entries;
    }

    private CursorContext cursorContext() {
        CursorContext cursorContext = mock(CursorContext.class);
        VersionContext versionContext = mock(VersionContext.class);
        when(cursorContext.getVersionContext()).thenReturn(versionContext);
        when(cursorContext.getCursorTracer()).thenReturn(PageCursorTracer.NULL);
        when(versionContext.oldestVisibilityHorizon()).thenReturn(oldestVisibleVersion);
        when(versionContext.committingTransactionId()).thenReturn(releaseId);
        return cursorContext;
    }
}
