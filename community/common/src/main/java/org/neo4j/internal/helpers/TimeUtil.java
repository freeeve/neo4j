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
package org.neo4j.internal.helpers;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class TimeUtil {
    public static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

    public static final ConcurrentHashMap<Integer, ZoneOffset> SECONDS_CACHE = new ConcurrentHashMap<>();

    public static final String VALID_TIME_DESCRIPTION =
            "Valid units are: `ns`, `μs`, `ms`, `s`, `m`, `h` and `d`; default unit is `s`";

    public static final Function<String, Long> parseTimeMillis = timeWithOrWithoutUnit -> {
        int unitIndex = -1;
        for (int i = 0; i < timeWithOrWithoutUnit.length(); i++) {
            char ch = timeWithOrWithoutUnit.charAt(i);
            if (!Character.isDigit(ch)) {
                unitIndex = i;
                break;
            }
        }
        if (unitIndex == -1) {
            return DEFAULT_TIME_UNIT.toMillis(Integer.parseInt(timeWithOrWithoutUnit));
        }

        if (unitIndex == 0) {
            throw new IllegalArgumentException("Missing numeric value");
        }

        // We have digits
        int unitIndexEnd = 0;
        long timeInNanos = 0L;
        while (unitIndexEnd < timeWithOrWithoutUnit.length()) {
            int amountIndex = unitIndexEnd;
            unitIndexEnd = timeWithOrWithoutUnit.length();
            for (int i = unitIndex; i < timeWithOrWithoutUnit.length(); i++) {
                char ch = timeWithOrWithoutUnit.charAt(i);
                if (Character.isDigit(ch)) {
                    unitIndexEnd = i;
                    break;
                }
            }

            String unit =
                    timeWithOrWithoutUnit.substring(unitIndex, unitIndexEnd).toLowerCase(Locale.ROOT);
            int amount = Integer.parseInt(timeWithOrWithoutUnit.substring(amountIndex, unitIndex));
            timeInNanos += toNanos(unit, amount);

            for (int i = unitIndexEnd; i < timeWithOrWithoutUnit.length(); i++) {
                char ch = timeWithOrWithoutUnit.charAt(i);
                if (!Character.isDigit(ch)) {
                    unitIndex = i;
                    break;
                }
            }
        }
        return NANOSECONDS.toMillis(timeInNanos);
    };

    private static long toNanos(String unit, int amount) {
        return switch (unit) {
            case "ns" -> NANOSECONDS.toNanos(amount);
            case "us", "μs" -> MICROSECONDS.toNanos(amount);
            case "ms" -> MILLISECONDS.toNanos(amount);
            case "s" -> SECONDS.toNanos(amount);
            case "m" -> MINUTES.toNanos(amount);
            case "h" -> HOURS.toNanos(amount);
            case "d" -> DAYS.toNanos(amount);
            default ->
                throw new IllegalArgumentException("Unrecognized unit `" + unit + "`. " + VALID_TIME_DESCRIPTION);
        };
    }

    public static String nanosToString(long nanos) {
        assert nanos >= 0;
        long nanoSeconds = nanos;
        StringBuilder timeString = new StringBuilder();

        long days = DAYS.convert(nanoSeconds, NANOSECONDS);
        if (days > 0) {
            nanoSeconds -= DAYS.toNanos(days);
            timeString.append(days).append('d');
        }
        long hours = HOURS.convert(nanoSeconds, NANOSECONDS);
        if (hours > 0) {
            nanoSeconds -= HOURS.toNanos(hours);
            timeString.append(hours).append('h');
        }
        long minutes = MINUTES.convert(nanoSeconds, NANOSECONDS);
        if (minutes > 0) {
            nanoSeconds -= MINUTES.toNanos(minutes);
            timeString.append(minutes).append('m');
        }
        long seconds = SECONDS.convert(nanoSeconds, NANOSECONDS);
        if (seconds > 0) {
            nanoSeconds -= SECONDS.toNanos(seconds);
            timeString.append(seconds).append('s');
        }
        long milliseconds = MILLISECONDS.convert(nanoSeconds, NANOSECONDS);
        if (milliseconds > 0) {
            nanoSeconds -= MILLISECONDS.toNanos(milliseconds);
            timeString.append(milliseconds).append("ms");
        }
        long microseconds = MICROSECONDS.convert(nanoSeconds, NANOSECONDS);
        if (microseconds > 0) {
            nanoSeconds -= MICROSECONDS.toNanos(microseconds);
            timeString.append(microseconds).append("μs");
        }
        if (nanoSeconds > 0 || timeString.isEmpty()) {
            timeString.append(nanoSeconds).append("ns");
        }
        return timeString.toString();
    }

    /**
     * Obtains an instance of {@code ZoneOffset} specifying the total offset in seconds
     * <p>
     * The offset must be in the range {@code -18:00} to {@code +18:00}, which corresponds to -64800 to +64800.
     *
     * @param totalSeconds  the total time-zone offset in seconds, from -64800 to +64800
     * @return the ZoneOffset, not null
     * @throws DateTimeException if the offset is not in the required range
     */
    public static ZoneOffset zoneOffsetOfTotalSeconds(int totalSeconds) {
        // Due to a regression introduced by https://bugs.openjdk.org/browse/JDK-8288723
        // we have to have our own cache for now. Regression was up to 25x in some benchmarks
        // so the addition memory usage is warranted.
        if (totalSeconds % (15 * 60) == 0) {
            Integer totalSecs = totalSeconds;
            ZoneOffset result = SECONDS_CACHE.get(totalSecs);
            if (result == null) {
                result = ZoneOffset.ofTotalSeconds(totalSeconds);
                SECONDS_CACHE.putIfAbsent(totalSecs, result);
            }
            return result;
        } else {
            return ZoneOffset.ofTotalSeconds(totalSeconds);
        }
    }

    private TimeUtil() {
        throw new AssertionError(); // no instances
    }
}
