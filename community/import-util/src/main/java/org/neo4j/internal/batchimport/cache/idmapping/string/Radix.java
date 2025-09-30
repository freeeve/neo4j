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
package org.neo4j.internal.batchimport.cache.idmapping.string;

import static java.lang.Math.pow;

import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;
import org.neo4j.function.Factory;

/**
 * Calculates and keeps radix counts. Uses a {@link RadixCalculator} to calculate an integer radix value
 * from a long value.
 */
public abstract class Radix {
    public static final Factory<Radix> LONG = LongRadix::new;

    public static final Factory<Radix> STRING = StringRadix::new;

    private final LongAdder nullCount = new LongAdder();
    private AtomicLongArray radixIndexCount;

    /**
     * A way of noting radix of a certain value, without actually registering that radix.
     * This is useful since the radix buckets layout may change depending on the seen radixes.
     * After the bucket distribution have been established it's much more straight forward to register
     * the actual radixes in parallel.
     */
    public void preRegisterRadixOf(long value) {}

    public void initialize(long numberOfItems) {
        if (numberOfItems > 0) {
            radixIndexCount = new AtomicLongArray((int) pow(2, RadixCalculator.RADIX_BITS - 1));
        }
    }

    public void registerRadixOf(long value) {
        assert radixIndexCount != null : "This instance should have been initialized prior to registering values";
        int radix = calculator().radixOf(value);
        if (radix == RadixCalculator.NULL_RADIX) {
            nullCount.add(1);
        } else {
            radixIndexCount.incrementAndGet(radix);
        }
    }

    public long getNullCount() {
        return nullCount.sum();
    }

    public long[] getRadixIndexCounts() {
        if (radixIndexCount == null) {
            return new long[0];
        }

        // TODO expose something other than long[] as getter instead
        long[] array = new long[radixIndexCount.length()];
        for (int i = 0; i < array.length; i++) {
            array[i] = radixIndexCount.get(i);
        }
        return array;
    }

    public abstract RadixCalculator calculator();

    @Override
    public String toString() {
        return Radix.class.getSimpleName() + "." + getClass().getSimpleName();
    }

    public static class StringRadix extends Radix {
        private final RadixCalculator calculator;

        public StringRadix() {
            this.calculator = new RadixCalculator.StringRadixCalculator();
        }

        @Override
        public RadixCalculator calculator() {
            return calculator;
        }
    }

    public static class LongRadix extends Radix {
        private volatile int radixShift;
        private final RadixCalculator calculator;

        public LongRadix() {
            this.calculator = new RadixCalculator.LongRadixCalculator(() -> radixShift);
        }

        @Override
        public RadixCalculator calculator() {
            return calculator;
        }

        @Override
        public void preRegisterRadixOf(long val) {
            long shiftVal = (val & ~RadixCalculator.LENGTH_BITS) >> (RadixCalculator.RADIX_BITS - 1 + radixShift);
            if (shiftVal > 0) {
                synchronized (this) {
                    shiftVal = (val & ~RadixCalculator.LENGTH_BITS) >> (RadixCalculator.RADIX_BITS - 1 + radixShift);
                    while (shiftVal > 0) {
                        radixShift++;
                        shiftVal = shiftVal >> 1;
                    }
                }
            }
        }
    }
}
