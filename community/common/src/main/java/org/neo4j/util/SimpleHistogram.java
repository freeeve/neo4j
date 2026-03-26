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
package org.neo4j.util;

import static java.lang.String.format;
import static org.neo4j.internal.helpers.Format.numberToStringWithGroups;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

public class SimpleHistogram {
    private final int[] limits;
    private final AtomicLongArray registers;
    private final LongAdder totalCount = new LongAdder();

    public SimpleHistogram(int... limits) {
        this.limits = limits;
        this.registers = new AtomicLongArray(limits.length + 1 /*all the ones above the highest limit*/);
    }

    public void register(int count) {
        totalCount.add(count);
        for (int i = 0; i < limits.length; i++) {
            int limit = limits[i];
            if (count <= limit) {
                registers.incrementAndGet(i);
                return;
            }
        }
        registers.incrementAndGet(limits.length);
    }

    public long totalRegisters() {
        long total = 0;
        for (int i = 0; i < registers.length(); i++) {
            total += registers.get(i);
        }
        return total;
    }

    public long totalCount() {
        return totalCount.sum();
    }

    public void clear() {
        for (int i = 0; i < registers.length(); i++) {
            registers.set(i, 0);
        }
        totalCount.reset();
    }

    public double averageCountPerRegister() {
        var totalRegisters = totalRegisters();
        return totalRegisters == 0 ? 0 : (double) totalCount() / totalRegisters;
    }

    public void addFrom(SimpleHistogram other) {
        if (!Arrays.equals(limits, other.limits)) {
            throw new RuntimeException("Different limits");
        }
        for (int i = 0; i < registers.length(); i++) {
            registers.addAndGet(i, other.registers.get(i));
        }
        totalCount.add(other.totalCount.sum());
    }

    @Override
    public String toString() {
        long totalRegisters = totalRegisters();
        if (totalRegisters == 0) {
            return "<none registered>";
        }

        StringBuilder builder = new StringBuilder(format(
                "count:%s, registers:%s, avg:%.2f",
                numberToStringWithGroups(totalCount(), '.'),
                numberToStringWithGroups(totalRegisters, '.'),
                averageCountPerRegister()));
        int prevLimit = -1;
        long accumulatedRegisters = 0;
        for (int i = 0; i < limits.length; i++) {
            long register = registers.get(i);
            int limit = limits[i];
            if (register > 0) {
                accumulatedRegisters += register;
                String limitString =
                        i == 0 || prevLimit + 1 == limit ? String.valueOf(limit) : (prevLimit + 1) + "-" + limit;
                addRegisterString(builder, limitString, register, accumulatedRegisters, totalRegisters);
            }
            prevLimit = limit;
        }
        if (registers.get(limits.length) > 0) {
            addRegisterString(builder, ">" + prevLimit, registers.get(limits.length), totalRegisters, totalRegisters);
        }
        return builder.toString();
    }

    private void addRegisterString(
            StringBuilder builder, String limitString, long register, long accumulatedRegisters, long totalRegisters) {
        builder.append(String.format(
                "%n%12s: %12s  %6.2f%%  (^%6.2f%%)",
                limitString,
                numberToStringWithGroups(register, '.'),
                percent(register, totalRegisters),
                percent(accumulatedRegisters, totalRegisters)));
    }

    public static SimpleHistogram combine(Stream<SimpleHistogram> sources) {
        return combine(sources.iterator());
    }

    public static SimpleHistogram combine(Iterator<SimpleHistogram> sources) {
        if (!sources.hasNext()) {
            return new SimpleHistogram();
        }
        SimpleHistogram first = sources.next();
        SimpleHistogram histogram = new SimpleHistogram(first.limits);
        histogram.addFrom(first);
        sources.forEachRemaining(histogram::addFrom);
        return histogram;
    }

    private static double percent(long part, long whole) {
        return zeroSafeDiv(100D * part, whole);
    }

    private static double zeroSafeDiv(double part, double whole) {
        return whole == 0 ? whole : part / whole;
    }
}
