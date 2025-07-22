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
package org.neo4j.values.storable;

import static org.neo4j.values.utils.TemporalUtil.NANOS_PER_SECOND;

import java.util.Locale;
import org.neo4j.exceptions.UnsupportedTemporalUnitException;

/**
 * Defines all valid field accessors for durations
 */
public enum DurationFields {
    YEARS("years") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return months / 12;
        }
    },
    MONTHS("months") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return months;
        }
    },
    MONTHS_OF_YEAR("monthsofyear") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return months % 12;
        }
    },
    MONTHS_OF_QUARTER("monthsofquarter") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return months % 3;
        }
    },
    QUARTERS("quarters") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return months / 3;
        }
    },
    QUARTERS_OF_YEAR("quartersofyear") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return (months / 3) % 4;
        }
    },
    WEEKS("weeks") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return days / 7;
        }
    },
    DAYS_OF_WEEK("daysofweek") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return days % 7;
        }
    },
    DAYS("days") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return days;
        }
    },
    HOURS("hours") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return seconds / 3600;
        }
    },
    MINUTES_OF_HOUR("minutesofhour") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return (seconds / 60) % 60;
        }
    },
    MINUTES("minutes") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return seconds / 60;
        }
    },
    SECONDS_OF_MINUTE("secondsofminute") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return seconds % 60;
        }
    },
    SECONDS("seconds") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return seconds;
        }
    },
    MILLISECONDS_OF_SECOND("millisecondsofsecond") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return nanos / 1000_000;
        }
    },
    MILLISECONDS("milliseconds") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return seconds * 1000 + nanos / 1000_000;
        }
    },
    MICROSECONDS_OF_SECOND("microsecondsofsecond") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return nanos / 1000;
        }
    },
    MICROSECONDS("microseconds") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return seconds * 1000_000 + nanos / 1000;
        }
    },
    NANOSECONDS_OF_SECOND("nanosecondsofsecond") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return nanos;
        }
    },
    NANOSECONDS("nanoseconds") {
        @Override
        public long asTimeStamp(long months, long days, long seconds, long nanos) {
            return seconds * NANOS_PER_SECOND + nanos;
        }
    };

    public final String propertyKey;

    DurationFields(String propertyKey) {
        this.propertyKey = propertyKey;
    }

    public abstract long asTimeStamp(long months, long days, long seconds, long nanos);

    public static DurationFields fromName(String fieldName) {
        return switch (fieldName.toLowerCase(Locale.ROOT)) {
            case "years" -> YEARS;
            case "months" -> MONTHS;
            case "monthsofyear" -> MONTHS_OF_YEAR;
            case "monthsofquarter" -> MONTHS_OF_QUARTER;
            case "quarters" -> QUARTERS;
            case "quartersofyear" -> QUARTERS_OF_YEAR;
            case "weeks" -> WEEKS;
            case "daysofweek" -> DAYS_OF_WEEK;
            case "days" -> DAYS;
            case "hours" -> HOURS;
            case "minutesofhour" -> MINUTES_OF_HOUR;
            case "minutes" -> MINUTES;
            case "secondsofminute" -> SECONDS_OF_MINUTE;
            case "seconds" -> SECONDS;
            case "millisecondsofsecond" -> MILLISECONDS_OF_SECOND;
            case "milliseconds" -> MILLISECONDS;
            case "microsecondsofsecond" -> MICROSECONDS_OF_SECOND;
            case "microseconds" -> MICROSECONDS;
            case "nanosecondsofsecond" -> NANOSECONDS_OF_SECOND;
            case "nanoseconds" -> NANOSECONDS;
            default -> throw UnsupportedTemporalUnitException.noSuchField(fieldName, "DURATION");
        };
    }
}
