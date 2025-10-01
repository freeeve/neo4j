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
package org.neo4j.collection;

import static org.neo4j.collection.PrimitiveLongCollections.resourceIterator;

import java.io.IOException;
import java.util.Arrays;
import org.neo4j.graphdb.Resource;
import org.neo4j.graphdb.ResourceUtils;

public class PrimitiveLongResourceCollections {
    private static final PrimitiveLongResourceIterator EMPTY = new AbstractPrimitiveLongBaseResourceIterator(null) {
        @Override
        protected boolean fetchNext() {
            return false;
        }
    };

    public static PrimitiveLongResourceIterator emptyIterator() {
        return EMPTY;
    }

    public static PrimitiveLongResourceIterator iterator(Resource resource, final long... items) {
        return resourceIterator(PrimitiveLongCollections.iterator(items), resource);
    }

    public static long count(PrimitiveLongResourceIterator iterator) throws IOException {
        long count = 0;
        try (iterator) {
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
        }
        return count;
    }

    public static PrimitiveLongResourceIterator concat(
            PrimitiveLongResourceIterator... primitiveLongResourceIterators) {
        return concat(Arrays.asList(primitiveLongResourceIterators));
    }

    public static PrimitiveLongResourceIterator concat(
            Iterable<PrimitiveLongResourceIterator> primitiveLongResourceIterators) {
        return new PrimitiveLongConcatenatingResourceIterator(primitiveLongResourceIterators);
    }

    /**
     * Returns the complement of the given original iterator in the interval [0, max).
     * It assumes that the original iterator is sorted in ascending order, and contains no duplicates.
     * The returned iterator will also be sorted in ascending order, and contain no duplicates.
     */
    public static PrimitiveLongResourceIterator complement(
            final PrimitiveLongResourceIterator original, final long max) {
        return new PrimitiveLongResourceCollections.AbstractPrimitiveLongBaseResourceIterator(original) {
            // current candidate in [0, limit)
            private long current;
            // lazy "peek" of original
            private boolean originalHasNext;
            private long originalNext;

            // advance original until originalNext >= minNeeded, or original exhausted
            private void ensureOriginalAtLeast(long minNeeded) {
                while (true) {
                    if (!originalHasNext) {
                        if (!original.hasNext()) {
                            return; // exhausted
                        }
                        originalNext = original.next();
                        originalHasNext = true;
                    }
                    if (originalNext >= minNeeded) {
                        return; // good position
                    }
                    // originalNext < minNeeded -> advance original
                    originalHasNext = false; // consume and continue
                }
            }

            @Override
            protected boolean fetchNext() {
                while (current < max) {
                    // Skip negatives in original and position it to >= current (if any left)
                    ensureOriginalAtLeast(current);

                    if (originalHasNext && originalNext == current) {
                        // current is present in original -> skip it in the inverse
                        current++;
                        originalHasNext = false; // consume that value
                        continue;
                    }

                    // Either original is exhausted, or originalNext > current, so current is missing -> emit it
                    return next(current++);
                }
                return false; // finished [0, limit)
            }
        };
    }

    public static long[] asArray(PrimitiveLongResourceIterator iterator) throws IOException {
        try (iterator) {
            return PrimitiveLongCollections.asArray(iterator);
        }
    }

    public abstract static class AbstractPrimitiveLongBaseResourceIterator
            extends PrimitiveLongCollections.AbstractPrimitiveLongBaseIterator
            implements PrimitiveLongResourceIterator {
        private Resource resource;

        public AbstractPrimitiveLongBaseResourceIterator(Resource resource) {
            this.resource = resource;
        }

        @Override
        public void close() {
            if (resource != null) {
                resource.close();
                resource = null;
            }
        }
    }

    private static final class PrimitiveLongConcatenatingResourceIterator
            extends PrimitiveLongCollections.PrimitiveLongConcatenatingIterator
            implements PrimitiveLongResourceIterator {
        private final Iterable<PrimitiveLongResourceIterator> iterators;
        private volatile boolean closed;

        private PrimitiveLongConcatenatingResourceIterator(Iterable<PrimitiveLongResourceIterator> iterators) {
            super(iterators.iterator());
            this.iterators = iterators;
        }

        @Override
        protected boolean fetchNext() {
            return !closed && super.fetchNext();
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                ResourceUtils.closeAll(iterators);
            }
        }
    }
}
