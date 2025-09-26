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
package org.neo4j.collection.trackable;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.eclipse.collections.api.LongIterable;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.block.function.primitive.LongToObjectFunction;
import org.eclipse.collections.api.block.function.primitive.ObjectLongToObjectFunction;
import org.eclipse.collections.api.block.predicate.primitive.LongPredicate;
import org.eclipse.collections.api.block.procedure.primitive.LongProcedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.iterator.LongIterator;
import org.eclipse.collections.impl.block.factory.primitive.LongPredicates;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.eclipse.collections.impl.primitive.AbstractLongIterable;
import org.eclipse.collections.impl.utility.internal.primitive.LongIteratorIterate;

public abstract class LongIterableAdapter extends AbstractLongIterable {
    @Override
    public long[] toArray() {
        long[] array = new long[size()];
        LongIterator itr = longIterator();
        int index = 0;
        while (itr.hasNext()) {
            array[index++] = itr.next();
        }
        return array;
    }

    @Override
    public boolean contains(long value) {
        return anySatisfy(LongPredicates.equal(value));
    }

    @Override
    public void each(LongProcedure procedure) {
        LongIteratorIterate.forEach(longIterator(), procedure);
    }

    @Override
    public LongIterable select(LongPredicate predicate) {
        return select(predicate, LongLists.mutable.empty());
    }

    @Override
    public LongIterable reject(LongPredicate predicate) {
        return reject(predicate, LongLists.mutable.empty());
    }

    @Override
    public <V> RichIterable<V> collect(LongToObjectFunction<? extends V> function) {
        return collect(function, Lists.mutable.empty());
    }

    @Override
    public long detectIfNone(LongPredicate predicate, long ifNone) {
        return LongIteratorIterate.detectIfNone(longIterator(), predicate, ifNone);
    }

    @Override
    public int count(LongPredicate predicate) {
        return LongIteratorIterate.count(longIterator(), predicate);
    }

    @Override
    public boolean anySatisfy(LongPredicate predicate) {
        return LongIteratorIterate.anySatisfy(longIterator(), predicate);
    }

    @Override
    public boolean allSatisfy(LongPredicate predicate) {
        return LongIteratorIterate.allSatisfy(longIterator(), predicate);
    }

    @Override
    public <T> T injectInto(T injectedValue, ObjectLongToObjectFunction<? super T, ? extends T> function) {
        return LongIteratorIterate.injectInto(longIterator(), injectedValue, function);
    }

    @Override
    public long sum() {
        return LongIteratorIterate.sum(longIterator());
    }

    @Override
    public long max() {
        return LongIteratorIterate.max(longIterator());
    }

    @Override
    public long min() {
        return LongIteratorIterate.min(longIterator());
    }

    @Override
    public int size() {
        return count(LongPredicates.alwaysTrue());
    }

    @Override
    public void appendString(Appendable appendable, String start, String separator, String end) {
        try {
            appendable.append(start);
            LongIterator itr = longIterator();
            boolean first = true;
            while (itr.hasNext()) {
                if (!first) {
                    appendable.append(separator);
                }
                first = false;
                appendable.append(Long.toString(itr.next()));
            }
            appendable.append(end);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
