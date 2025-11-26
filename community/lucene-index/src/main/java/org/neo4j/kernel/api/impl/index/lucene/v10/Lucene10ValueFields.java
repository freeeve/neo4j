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
package org.neo4j.kernel.api.impl.index.lucene.v10;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.neo4j.util.Preconditions;
import org.neo4j.values.storable.DateTimeValue;
import org.neo4j.values.storable.DateValue;
import org.neo4j.values.storable.LocalDateTimeValue;
import org.neo4j.values.storable.LocalTimeValue;
import org.neo4j.values.storable.TemporalValue;
import org.neo4j.values.storable.TimeValue;

final class Lucene10ValueFields {
    private Lucene10ValueFields() {}

    static final class BooleanField extends Field {
        private static final FieldType TYPE;

        private static final BytesRef TRUE = new BytesRef(new byte[1]);
        private static final BytesRef FALSE = new BytesRef(new byte[0]);

        static {
            FieldType type = new FieldType();
            type.setTokenized(false);
            type.setIndexOptions(IndexOptions.DOCS);
            type.freeze();
            TYPE = type;
        }

        BooleanField(String name, boolean value) {
            super(name, TYPE);
            fieldsData = value ? TRUE : FALSE;
        }

        public boolean getBoolValue() {
            return fieldsData == TRUE;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " <" + name + ':' + getBoolValue() + '>';
        }

        static TermQuery newExactQuery(String field, boolean value) {
            Term term = new Term(field, value ? TRUE : FALSE);
            return new TermQuery(term);
        }

        static Query newRangeQuery(String field, boolean lowerValueInclusive, boolean upperValueInclusive) {
            if (lowerValueInclusive && !upperValueInclusive) {
                return new MatchNoDocsQuery();
            } else if (lowerValueInclusive) {
                // range from [true, true]
                return newExactQuery(field, true);
            } else if (!upperValueInclusive) {
                // range from [false, false]
                return newExactQuery(field, false);
            } else {
                // range from [false, true]
                return new FieldExistsQuery(field);
            }
        }
    }

    static final class SingleLongField extends Field {
        private static final FieldType TYPE;

        static {
            FieldType type = new FieldType();
            type.setDimensions(1, Long.BYTES);
            type.freeze();
            TYPE = type;
        }

        SingleLongField(String name, long value) {
            super(name, TYPE);
            fieldsData = value;
        }

        @Override
        public BytesRef binaryValue() {
            return new BytesRef(longToBytes((Long) fieldsData));
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " <" + name + ':' + fieldsData + '>';
        }

        static Query newExactQuery(String field, long value) {
            Preconditions.requireNonNull(field, "Field cannot be null");
            byte[] encodedValue = longToBytes(value);
            return new LongPointRangeQuery(field, encodedValue, encodedValue, 1);
        }

        static Query newRangeQuery(String field, long lowerValueInclusive, long upperValueInclusive) {
            Preconditions.requireNonNull(field, "Field cannot be null");
            if (upperValueInclusive < lowerValueInclusive) {
                return new MatchNoDocsQuery();
            }
            return new LongPointRangeQuery(
                    field, longToBytes(lowerValueInclusive), longToBytes(upperValueInclusive), 1);
        }

        private static final class LongPointRangeQuery extends org.apache.lucene.search.PointRangeQuery {

            private LongPointRangeQuery(String field, byte[] lowerPoint, byte[] upperPoint, int numDims) {
                super(field, lowerPoint, upperPoint, numDims);
            }

            @Override
            protected String toString(int dimension, byte[] value) {
                return Long.toString(NumericUtils.sortableBytesToLong(value, 0));
            }
        }
    }

    static final class SingleInstantField extends Field {
        private static final FieldType TYPE;

        static {
            FieldType type = new FieldType();
            type.setDimensions(1, INSTANT_BYTES);
            type.freeze();
            TYPE = type;
        }

        SingleInstantField(String name, Instant value) {
            super(name, TYPE);
            fieldsData = value;
        }

        @Override
        public BytesRef binaryValue() {
            return new BytesRef(instantToBytes((Instant) fieldsData));
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " <" + name + ':' + fieldsData + '>';
        }

        static Query newExactQuery(String field, Instant value) {
            Preconditions.requireNonNull(field, "Field cannot be null");
            byte[] encodedValue = instantToBytes(value);
            return new InstantPointRangeQuery(field, encodedValue, encodedValue, 1);
        }

        static Query newRangeQuery(String field, Instant lowerValueInclusive, Instant upperValueInclusive) {
            Preconditions.requireNonNull(field, "Field cannot be null");
            if (lowerValueInclusive.isAfter(upperValueInclusive)) {
                return new MatchNoDocsQuery();
            }
            return new InstantPointRangeQuery(
                    field, instantToBytes(lowerValueInclusive), instantToBytes(upperValueInclusive), 1);
        }

        private static final class InstantPointRangeQuery extends org.apache.lucene.search.PointRangeQuery {

            private InstantPointRangeQuery(String field, byte[] lowerPoint, byte[] upperPoint, int numDims) {
                super(field, lowerPoint, upperPoint, numDims);
            }

            @Override
            protected String toString(int dimension, byte[] value) {
                return bytesToInstant(value).toString();
            }
        }
    }

    static final class SingleDoubleField extends Field {
        private static final FieldType TYPE;

        static {
            FieldType type = new FieldType();
            type.setDimensions(1, Double.BYTES);
            type.freeze();
            TYPE = type;
        }

        SingleDoubleField(String name, double value) {
            super(name, TYPE);
            fieldsData = value;
        }

        @Override
        public BytesRef binaryValue() {
            return new BytesRef(doubleToBytes((Double) fieldsData));
        }

        @Override
        public void setDoubleValue(double value) {
            super.setLongValue(NumericUtils.doubleToSortableLong(value));
        }

        @Override
        public void setLongValue(long value) {
            throw new IllegalArgumentException("Cannot change value type from Double to Long");
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " <" + name + ':' + fieldsData + '>';
        }

        static Query newExactQuery(String field, double value) {
            Preconditions.requireNonNull(field, "Field cannot be null");
            byte[] encodedValue = doubleToBytes(value);
            return new DoublePointRangeQuery(field, encodedValue, encodedValue, 1);
        }

        static Query newRangeQuery(String field, double lowerValueInclusive, double upperValueInclusive) {
            Preconditions.requireNonNull(field, "field cannot be null");
            Preconditions.checkArgument(
                    !Double.isNaN(lowerValueInclusive), "NaN is not a valid lower bound for a range");
            Preconditions.checkArgument(
                    !Double.isNaN(upperValueInclusive), "NaN is not a valid upper bound for a range");
            if (lowerValueInclusive > upperValueInclusive) {
                return new MatchNoDocsQuery();
            }
            return new DoublePointRangeQuery(
                    field, doubleToBytes(lowerValueInclusive), doubleToBytes(upperValueInclusive), 1);
        }

        private static byte[] doubleToBytes(double value) {
            return longToBytes(NumericUtils.doubleToSortableLong(value));
        }

        private static final class DoublePointRangeQuery extends org.apache.lucene.search.PointRangeQuery {

            private DoublePointRangeQuery(String field, byte[] lowerPoint, byte[] upperPoint, int numDims) {
                super(field, lowerPoint, upperPoint, numDims);
            }

            @Override
            protected String toString(int dimension, byte[] value) {
                return Double.toString(NumericUtils.sortableLongToDouble(NumericUtils.sortableBytesToLong(value, 0)));
            }
        }
    }

    private static Instant instantOf(ZonedDateTime zdt) {
        return Instant.ofEpochSecond(zdt.toEpochSecond(), zdt.getNano());
    }

    /// Express a temporal value in terms of an Instant
    /// Two instants from 2 different temporal values of the same type
    /// are comparable in the natural way as (epoch seconds, nano within second)
    ///
    /// Instant values returned are only comparable for the same subtypes of temporal values
    /// This is fine because different types are stored in different namespaces,
    /// so they are never actually compared.
    ///
    /// Some of these values could be 8 bytes
    /// Whereas an instant in general requires 12 bytes
    ///
    static Instant instantFromTemporal(TemporalValue<?, ?> tv) {

        try {
            return switch (tv) {
                case DateTimeValue dateTimeValue -> instantOf(dateTimeValue.asObjectCopy());
                case LocalDateTimeValue localDateTimeValue ->
                    instantOf(ZonedDateTime.of(localDateTimeValue.asObjectCopy(), ZoneOffset.UTC));
                case LocalTimeValue localTimeValue -> {
                    var offset = Duration.between(LocalTime.MIN, localTimeValue.asObjectCopy());
                    yield Instant.ofEpochSecond(offset.getSeconds(), offset.getNano());
                }
                case DateValue dateValue ->
                    // 00:00:00 UTC on the date in question
                    instantOf(ZonedDateTime.of(dateValue.asObjectCopy(), LocalTime.ofSecondOfDay(0), ZoneOffset.UTC));
                case TimeValue timeValue -> {
                    // a fake instant calculated as duration from the earliest possible offset time
                    var offset = Duration.between(OffsetTime.MIN, timeValue.asObjectCopy());
                    yield Instant.ofEpochSecond(offset.getSeconds(), offset.getNano());
                }
                default -> null;
            };
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(
                    String.format("Temporal value must represent a valid date/time %s", tv), e);
        }
    }

    private static byte[] longToBytes(long value) {
        byte[] data = new byte[Long.BYTES];
        NumericUtils.longToSortableBytes(value, data, 0);
        return data;
    }

    private static final int INSTANT_BYTES = Long.BYTES + Integer.BYTES;

    private static byte[] instantToBytes(Instant value) {
        byte[] data = new byte[INSTANT_BYTES];
        NumericUtils.longToSortableBytes(value.getEpochSecond(), data, 0);
        NumericUtils.intToSortableBytes(value.getNano(), data, Long.BYTES);
        return data;
    }

    private static Instant bytesToInstant(byte[] data) {
        long seconds = NumericUtils.sortableBytesToLong(data, 0);
        int nanos = NumericUtils.sortableBytesToInt(data, Long.BYTES);
        return Instant.ofEpochSecond(seconds, nanos);
    }
}
