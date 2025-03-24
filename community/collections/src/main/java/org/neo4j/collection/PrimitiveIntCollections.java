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

import java.util.NoSuchElementException;
import org.eclipse.collections.api.iterator.IntIterator;

public final class PrimitiveIntCollections {
    private PrimitiveIntCollections() {
        // nop
    }

    /**
     * Base iterator for simpler implementations of {@link IntIterator}s.
     */
    public abstract static class AbstractPrimitiveIntBaseIterator implements IntIterator {
        private boolean hasNextDecided;
        private boolean hasNext;
        protected int next;

        @Override
        public boolean hasNext() {
            if (!hasNextDecided) {
                hasNext = fetchNext();
                hasNextDecided = true;
            }
            return hasNext;
        }

        @Override
        public int next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more elements in " + this);
            }
            hasNextDecided = false;
            return next;
        }

        /**
         * Fetches the next item in this iterator. Returns whether a next item was found. If a next
         * item was found, that value must have been set inside the implementation of this method
         * using {@link #next(int)}.
         */
        protected abstract boolean fetchNext();

        /**
         * Called from inside an implementation of {@link #fetchNext()} if a next item was found.
         * This method returns {@code true} so that it can be used in shorthand conditionals
         * like:
         * <pre>
         * protected boolean fetchNext()
         * {
         *     return source.hasNext() ? next( source.next() ) : false;
         * }
         * </pre>
         *
         * @param nextItem the next item found.
         */
        protected boolean next(int nextItem) {
            next = nextItem;
            hasNext = true;
            return true;
        }
    }
}
