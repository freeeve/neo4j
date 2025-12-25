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

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.values.storable.CoordinateReferenceSystem.CARTESIAN;
import static org.neo4j.values.storable.DurationValue.duration;
import static org.neo4j.values.storable.Values.NO_VALUE;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;
import java.time.zone.ZoneRulesProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.exceptions.InvalidArgumentException;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.kernel.api.StatementConstants;
import org.neo4j.kernel.api.impl.schema.vector.VectorDocumentStructure;
import org.neo4j.values.storable.CoordinateReferenceSystem;
import org.neo4j.values.storable.DateTimeValue;
import org.neo4j.values.storable.DateValue;
import org.neo4j.values.storable.LocalTimeValue;
import org.neo4j.values.storable.TemporalValue;
import org.neo4j.values.storable.TimeValue;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.ValueGroup;
import org.neo4j.values.storable.Values;

public class Lucene10FilterQueryBuilderTest {

    private static final int KEY_INDEX = 4;

    Analyzer analyzer = new KeywordAnalyzer();
    VectorDocumentStructure documentStructure = new TestVectorDocumentStructure();
    MemoryIndex index;

    @BeforeEach
    public void setUp() throws Exception {
        index = new MemoryIndex();
    }

    PropertyIndexQuery[] queries = new PropertyIndexQuery[10 /*enough*/];

    private void addField(int fieldPosition, Value value) {
        if (value == null) {
            return;
        }

        var exists = new StringField(
                Lucene10DocumentsFactory.EXISTS_KEY,
                new BytesRef(Lucene10ValueFields.intToBytes(fieldPosition)),
                Store.NO);
        index.addField(exists, analyzer);

        Lucene10DocumentsFactory.addIndexableFields(
                documentStructure, fieldPosition, value, field -> index.addField(field, analyzer));
    }

    private float scoreForQuery(int position, PropertyIndexQuery... queries) {
        Arrays.fill(this.queries, PropertyIndexQuery.all(StatementConstants.NO_SUCH_PROPERTY_KEY));
        if (queries != null) {
            int queryPosition = position;
            for (PropertyIndexQuery query : queries) {
                this.queries[queryPosition++] = query;
            }
        }
        var luceneQuery = Lucene10FilterQueryBuilder.build(documentStructure, 0, this.queries);
        return index.search(new ConstantScoreQuery(luceneQuery));
    }

    @Test
    void propertyExists() {
        int indexablePropertyIndex = 3;
        addField(indexablePropertyIndex, Values.utf8Value("indexed"));

        int nonIndexablePropertyIndex = 4;
        addField(nonIndexablePropertyIndex, Values.pointValue(CoordinateReferenceSystem.CARTESIAN, 0.f, 1.f));

        int noValuePropertyIndex = 5;
        addField(noValuePropertyIndex, null);

        assertThat(scoreForQuery(indexablePropertyIndex, PropertyIndexQuery.exists(1)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(nonIndexablePropertyIndex, PropertyIndexQuery.exists(2)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(noValuePropertyIndex, PropertyIndexQuery.exists(3)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testString() {

        int keyIndex = KEY_INDEX;
        addField(keyIndex, asValue("bravo"));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, "bravo")))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex + 1, PropertyIndexQuery.exact(1, "bravo")))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, "alpha")))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, Values.charValue('a'))))
                .isEqualTo(0.0f);
    }

    @Test
    public void testAllMatchesString() {
        int keyIndex1 = 4;
        addField(keyIndex1, asValue("bravo"));
        assertThat(scoreForQuery(keyIndex1, PropertyIndexQuery.all(1))).isEqualTo(1.0f);
    }

    @Test
    public void testInteger() {

        int keyIndex = KEY_INDEX;
        addField(keyIndex, asValue(42));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 42))).isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex + 1, PropertyIndexQuery.exact(1, 42))).isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 43))).isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 41))).isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 42.0))).isEqualTo(1.0f);
    }

    @Test
    public void testFloat() {
        int keyIndex = KEY_INDEX;
        addField(keyIndex, asValue(42.5));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 42.5))).isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 42))).isEqualTo(0.0f);

        keyIndex++;
        addField(keyIndex, asValue(42.0));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 42.0))).isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 42))).isEqualTo(1.0f);
    }

    @Test
    public void testFiniteFloatExact() {
        assertThat(scoreForQuery(4, PropertyIndexQuery.exact(1, Double.NaN))).isEqualTo(0.0f);
        assertThat(scoreForQuery(4, PropertyIndexQuery.exact(1, Double.POSITIVE_INFINITY)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(4, PropertyIndexQuery.exact(1, Double.NEGATIVE_INFINITY)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testBooleanTrue() {
        int keyIndex = KEY_INDEX;
        addField(keyIndex, asValue(true));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, true))).isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, false))).isEqualTo(0.0f);
    }

    @Test
    public void testBooleanFalse() {
        int keyIndex = KEY_INDEX;
        addField(keyIndex, asValue(false));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, true))).isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, false))).isEqualTo(1.0f);
    }

    @Test
    public void testConjunction() {
        int keyIndex = KEY_INDEX;
        addField(keyIndex++, asValue("alpha"));
        addField(keyIndex++, asValue(25.4));
        addField(keyIndex, asValue(true));
        assertThat(scoreForQuery(
                        KEY_INDEX,
                        PropertyIndexQuery.exact(1, "alpha"),
                        PropertyIndexQuery.exact(1, 25.4),
                        PropertyIndexQuery.exact(1, true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        KEY_INDEX,
                        PropertyIndexQuery.exact(1, "bravo"),
                        PropertyIndexQuery.exact(1, 25.4),
                        PropertyIndexQuery.exact(1, true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(
                        KEY_INDEX,
                        PropertyIndexQuery.exact(1, "alpha"),
                        PropertyIndexQuery.exact(1, 25.3),
                        PropertyIndexQuery.exact(1, true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(
                        KEY_INDEX,
                        PropertyIndexQuery.exact(1, "alpha"),
                        PropertyIndexQuery.exact(1, 25.4),
                        PropertyIndexQuery.exact(1, false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(
                        4,
                        PropertyIndexQuery.exact(1, "alpha"),
                        PropertyIndexQuery.all(1),
                        PropertyIndexQuery.exact(1, true)))
                .isEqualTo(1.0f);
    }

    @Test
    public void testBadExactQueryType() {
        int keyIndex = KEY_INDEX;
        addField(keyIndex, asValue(42));

        assertThatThrownBy(
                        () -> scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, Values.pointValue(CARTESIAN, 2, 2))))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("""
                    Status: 22G03
                    Message:\s
                    Subcondition: invalid value type
                    Position:\s
                    Caused by:   \s
                        Status: 22N01\
                    """);
    }

    @Test
    public void testAllMatchesInteger() {
        int keyIndex = 4;
        addField(keyIndex, asValue(4));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.all(1))).isEqualTo(1.0f);
    }

    @Test
    public void testAllMatchesFloat() {
        int keyIndex = 4;
        addField(keyIndex, asValue(7.5f));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.all(1))).isEqualTo(1.0f);
    }

    @Test
    public void testAllMatchesBoolean() {
        int keyIndex = 4;
        addField(keyIndex, asValue(true));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.all(1))).isEqualTo(1.0f);
    }

    @Test
    public void testTemporalQueryTypes() {
        var APOLLO_UTC = ZonedDateTime.of(1969, 7, 20, 20, 17, 0, 0, ZoneOffset.UTC);
        addAndCheckFieldsAreIndependent(allTemporalValues(APOLLO_UTC));
    }

    @Test
    public void testTemporalQueryTypeRanges() {
        var APOLLO_UTC = ZonedDateTime.of(1969, 7, 20, 20, 17, 0, 0, ZoneOffset.UTC);
        addAndCheckFieldsAreIndependent(allTemporalValues(APOLLO_UTC));

        var temporals = allTemporalValues(APOLLO_UTC);
        for (var temporal : temporals) {
            if (temporal.isSupported(ChronoField.YEAR)) {
                checkTemporalRange(temporal, ChronoField.YEAR, ChronoUnit.YEARS);
                checkTemporalRange(temporal, ChronoField.INSTANT_SECONDS, SECONDS);
                checkTemporalRange(temporal, ChronoField.DAY_OF_YEAR, DAYS);
                checkTemporalRange(temporal, ChronoField.HOUR_OF_DAY, ChronoUnit.HOURS);
                checkTemporalRange(temporal, ChronoField.OFFSET_SECONDS, SECONDS);
            }
        }
    }

    @Test
    public void testStringRange() {

        addField(KEY_INDEX, asValue("bravo"));
        assertInRangeTF("alpha", "charlie");
        assertInRangeTF("bravo", "charlie");
        assertOutRangeFF("bravo", "charlie");
        assertInRangeFT("alpha", "bravo");
        assertOutRangeFF("alpha", "bravo");
        assertOutRangeTT('a', 'b');
    }

    @Test
    public void testCharRange() {

        addField(KEY_INDEX, asValue('b'));
        assertInRangeTF('a', 'c');
        assertInRangeTF('b', 'c');
        assertOutRangeFF('b', 'c');
        assertInRangeFT('a', 'b');
        assertOutRangeFF('a', 'b');
    }

    @Test
    public void testIntegerRange() {

        addField(KEY_INDEX, asValue(42));

        assertInRangeTT(41, 43);
        assertInRangeTT(42, 43);
        assertOutRangeFT(42, 43);
        assertInRangeFT(38, 42);
        assertOutRangeFF(38, 42);

        assertOutRangeFT(42, 42.5);
        assertInRangeFF(41.9f, 42.1f);
        assertInRangeTF(42.0f, 42.1f);
        assertOutRangeFT(42.0f, 43.0f);
        assertInRangeFT(38.0f, 42.0);
        assertOutRangeFF(38.0f, 42.0);
        assertOutRangeFF(42.3f, 42.5);

        // empty ranges
        assertOutRangeFF(42.5, 42.3f);
        assertOutRangeFF(44, 43);
    }

    @Test
    public void testFloatRange() {

        addField(KEY_INDEX, asValue(42.5));

        assertInRangeTT(41, 43);
        assertInRangeTT(42, 43);
        assertInRangeFT(42, 43);
        assertOutRangeFT(38, 42);
        assertOutRangeFF(38, 42.5);
        assertInRangeFF(42.4, 42.6);
        assertInRangeFF(42.49, 42.51);
        assertInRangeTT(42.5, 42.5);
        // check empty range queries
        assertOutRangeTF(42.5, 42.5);
        assertOutRangeFT(42.5, 42.5);
    }

    @Test
    public void testBigFloatRange() {

        var bigFloat = 9.23e18; // large than Long.MAX_VALUE
        addField(KEY_INDEX, asValue(bigFloat));

        assertOutRangeTT(Long.MIN_VALUE, Long.MAX_VALUE);
        assertInRangeTT(Long.MIN_VALUE, 10e18);
    }

    @Test
    public void testFiniteFloatRange() {
        assertOutRangeTT(Double.NaN, 42);
        assertOutRangeTT(42, Double.NaN);
    }

    @Test
    public void testBooleanRangeTrue() {

        addField(KEY_INDEX, asValue(true));

        assertInRangeTT(false, true);
        assertOutRangeTF(false, true);
        assertOutRangeTT(false, false);
        assertInRangeFT(false, true);
        assertInRangeTT(true, true);
        // empty ranges
        assertOutRangeFT(true, true);
        assertOutRangeFF(true, false);
        assertOutRangeFT(true, false);
    }

    @Test
    public void testBooleanRangeFalse() {

        addField(KEY_INDEX, asValue(false));

        assertInRangeTT(false, true);
        assertInRangeTF(false, true);
        assertInRangeTT(false, false);
        assertOutRangeFT(false, true);
        assertOutRangeTT(true, true);
        // empty ranges
        assertOutRangeFT(true, true);
        assertOutRangeTF(true, false);
        assertOutRangeTT(true, false);
    }

    @Test
    public void testTemporalRangePoint() {
        var APOLLO_11_LOCALTIME = LocalDateTime.of(1969, 7, 20, 20, 17, 0);
        var APOLLO_11_UTC = ZonedDateTime.of(APOLLO_11_LOCALTIME, ZoneOffset.UTC);
        var APOLLO_12_LOCALTIME = LocalDateTime.of(1969, 11, 24, 20, 58, 24);
        var APOLLO_12_UTC = ZonedDateTime.of(APOLLO_12_LOCALTIME, ZoneOffset.UTC);

        addField(KEY_INDEX, asValue(APOLLO_11_UTC));
        assertInRangeTT(APOLLO_11_UTC, APOLLO_11_UTC);
        assertOutRangeTT(APOLLO_12_UTC, APOLLO_12_UTC);
        assertOutRangeFT(APOLLO_11_UTC, APOLLO_11_UTC);
        assertOutRangeTF(APOLLO_11_UTC, APOLLO_11_UTC);
        assertOutRangeFF(APOLLO_11_UTC, APOLLO_11_UTC);
    }

    @Test
    public void testTemporalRangeInfinityMax() {

        addField(KEY_INDEX, asValue(DateTimeValue.MAX_VALUE));
        assertExactHit(DateTimeValue.MAX_VALUE);
        assertInRangeTT(DateTimeValue.MAX_VALUE, NO_VALUE);
        assertOutRangeFT(DateTimeValue.MAX_VALUE, NO_VALUE);
    }

    @Test
    public void testTemporalRangeInfinityMin() {

        addField(KEY_INDEX, asValue(DateTimeValue.MIN_VALUE));
        assertThat(scoreForQuery(KEY_INDEX, PropertyIndexQuery.exact(1, asValue(DateTimeValue.MIN_VALUE))))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        KEY_INDEX, PropertyIndexQuery.range(1, NO_VALUE, true, asValue(DateTimeValue.MIN_VALUE), true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        KEY_INDEX,
                        PropertyIndexQuery.range(1, NO_VALUE, true, asValue(DateTimeValue.MIN_VALUE), false)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testTemporalRangeWider() {
        var APOLLO_12_LOCALTIME = LocalDateTime.of(1969, 11, 24, 20, 58, 24);
        var APOLLO_12_UTC = ZonedDateTime.of(APOLLO_12_LOCALTIME, ZoneOffset.UTC);
        var APOLLO_14_LOCALTIME = LocalDateTime.of(1971, 2, 9, 21, 5, 0);
        var APOLLO_14_UTC = ZonedDateTime.of(APOLLO_14_LOCALTIME, ZoneOffset.UTC);
        var APOLLO_16_LOCALTIME = LocalDateTime.of(1972, 4, 25, 5, 47, 0);
        var APOLLO_16_UTC = ZonedDateTime.of(APOLLO_16_LOCALTIME, ZoneOffset.UTC);

        assertOutRangeTT(APOLLO_12_UTC, APOLLO_16_UTC);

        addField(KEY_INDEX, asValue(APOLLO_14_UTC));

        assertInRangeTT(APOLLO_14_UTC, APOLLO_14_UTC);
        assertInRangeTT(APOLLO_14_UTC, APOLLO_16_UTC);
        assertInRangeTT(APOLLO_12_UTC, APOLLO_14_UTC);
        assertInRangeTT(APOLLO_12_UTC, APOLLO_16_UTC);
        assertOutRangeTT(APOLLO_14_UTC.plusSeconds(1), APOLLO_14_UTC.plusSeconds(2));
        assertInRangeTT(APOLLO_14_UTC.minusSeconds(1), APOLLO_14_UTC.plusSeconds(2));
        assertInRangeTT(APOLLO_14_UTC.minusSeconds(1), APOLLO_14_UTC.plusSeconds(2));
    }

    @Test
    public void testTemporalRangeImplementationArtifacts() {

        var APOLLO_16_LOCALTIME = LocalDateTime.of(1972, 4, 25, 5, 47, 0);
        var APOLLO_16_UTC = ZonedDateTime.of(APOLLO_16_LOCALTIME, ZoneOffset.UTC);

        addField(KEY_INDEX, asValue(APOLLO_16_UTC.plusNanos(10_000_000)));

        // This is now correct because temporal range querying builds a compound query
        // to take care of the nanosecond ranges within the endpoint seconds of the time range.
        assertOutRangeTT(APOLLO_16_UTC.minusNanos(10_000_000), APOLLO_16_UTC);
        assertOutRangeTT(APOLLO_16_UTC.plusNanos(20_000_000), APOLLO_16_UTC);
        assertInRangeTT(APOLLO_16_UTC.minusSeconds(2).plusNanos(20_000_000), APOLLO_16_UTC.plusNanos(15_000_000));
        assertInRangeTT(
                APOLLO_16_UTC.minusSeconds(2).plusNanos(20_000_000),
                APOLLO_16_UTC.plusSeconds(1).plusNanos(5_000_000));
    }

    @Test
    public void testTemporalCypherTimezoneCompatibility() {}

    @Test
    public void testBadRangeQueryType() {
        int keyIndex = KEY_INDEX;
        addField(keyIndex, asValue(42));

        assertThatThrownBy(() -> scoreForQuery(
                        keyIndex, PropertyIndexQuery.range(1, asValue(41), true, Values.charValue('b'), true)))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("class org.neo4j.values.storable.CharValue cannot be cast to class"
                        + " org.neo4j.values.storable.NumberValue");

        assertThatThrownBy(() -> scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, asValue(LocalTime.of(10, 30, 45)), true, asValue('b'), true)))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("""
                    Status: 22G03
                    Message:\s
                    Subcondition: invalid value type
                    Position:\s
                    Caused by:   \s
                        Status: 22N01\
                    """);

        // Incomparable, so 0.0f
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(
                                1,
                                asValue(Duration.of(10, ChronoUnit.MINUTES)),
                                true,
                                asValue(Duration.of(20, ChronoUnit.MINUTES)),
                                true)))
                .isEqualTo(0.0f);
    }

    @Test
    public void temporalDateTypesDontClash() {
        var APOLLO_11_LOCALTIME = LocalDateTime.of(1969, 7, 20, 20, 17, 0);
        var APOLLO_11_UTC = ZonedDateTime.of(APOLLO_11_LOCALTIME, ZoneOffset.UTC);
        var APOLLO_11_DATE = DateValue.date(LocalDate.from(APOLLO_11_LOCALTIME));

        addField(KEY_INDEX, APOLLO_11_DATE);
        assertInRangeTT(APOLLO_11_DATE, APOLLO_11_DATE);
        // Different types:
        assertOutRangeTT(APOLLO_11_UTC.minusDays(1), APOLLO_11_UTC.plusDays(1));
    }

    @Test
    public void temporalDateConfirmIdAsOffsetBeforeIdAsRegion() {
        var VOYAGER_2_OFFSET = DateTimeValue.parse("1977-02-20T19:29:44Z", ZoneOffset::systemDefault);
        var VOYAGER_2_REGION = DateTimeValue.parse("1977-02-20T19:29:44[Europe/Dublin]", ZoneOffset::systemDefault);
        assertThat(Values.COMPARATOR.compare(VOYAGER_2_OFFSET, VOYAGER_2_REGION))
                .isNegative();
        addField(KEY_INDEX, VOYAGER_2_REGION);
        assertInRangeTT(VOYAGER_2_OFFSET, VOYAGER_2_OFFSET.plus(Duration.ofDays(10)));
        assertInRangeFT(VOYAGER_2_OFFSET, VOYAGER_2_OFFSET.plus(Duration.ofDays(10)));
        assertInRangeTT(VOYAGER_2_REGION, VOYAGER_2_OFFSET.plus(Duration.ofDays(10)));
        assertOutRangeFT(VOYAGER_2_REGION, VOYAGER_2_OFFSET.plus(Duration.ofDays(10)));
    }

    /// An initial implementation of String for zone offsets was broken.
    /// GMT < GMT0 but "\[GMT0\]" < "\[GMT\]".
    @Test
    public void versionsOfGMT() {
        var before = DateTimeValue.parse("2019-06-03T05:00:00.000[Brazil/DeNoronha]", ZoneOffset::systemDefault);
        var mid = DateTimeValue.parse("2019-06-03T07:00:00.000[GMT]", ZoneOffset::systemDefault);
        var after = DateTimeValue.parse("2019-06-03T07:00:00.000[GMT0]", ZoneOffset::systemDefault);
        addField(KEY_INDEX, mid);
        assertInRangeTT(before, mid);
        assertInRangeTT(before, after);
    }

    private void checkRangeUpperExists(TemporalValue<?, ?> lower, TemporalValue<?, ?> upper) {
        assertThat(Values.COMPARATOR.compare(lower, upper)).isNegative();
        addField(KEY_INDEX, upper);

        assertOutRangeTF(NO_VALUE, lower);
        assertOutRangeTT(NO_VALUE, lower);
        assertOutRangeTF(NO_VALUE, upper);
        assertInRangeTT(NO_VALUE, upper);

        assertInRangeTT(lower, NO_VALUE);
        assertInRangeFT(lower, NO_VALUE);
        assertInRangeTT(upper, NO_VALUE);
        assertOutRangeFT(upper, NO_VALUE);
    }

    private void checkRangeLowerExists(TemporalValue<?, ?> lower, TemporalValue<?, ?> upper) {
        assertThat(Values.COMPARATOR.compare(lower, upper)).isNegative();
        addField(KEY_INDEX, lower);

        assertOutRangeTF(NO_VALUE, lower);
        assertInRangeTT(NO_VALUE, lower);
        assertInRangeTF(NO_VALUE, upper);
        assertInRangeTT(NO_VALUE, upper);

        assertInRangeTT(lower, NO_VALUE);
        assertOutRangeFT(lower, NO_VALUE);
        assertOutRangeTT(upper, NO_VALUE);
        assertOutRangeFT(upper, NO_VALUE);
    }

    @Test
    public void rangeWithOpenEndSameZoneOffsetLowerExists() {
        var lower = DateTimeValue.parse("2019-06-03T05:00:00.000-0200", ZoneOffset::systemDefault);
        var upper = DateTimeValue.parse("2019-06-03T05:00:00.000[Brazil/DeNoronha]", ZoneOffset::systemDefault);
        checkRangeLowerExists(lower, upper);
    }

    @Test
    public void rangeWithOpenEndSameZoneOffsetUpperExists() {
        var lower = DateTimeValue.parse("2019-06-03T05:00:00.000-0200", ZoneOffset::systemDefault);
        var upper = DateTimeValue.parse("2019-06-03T05:00:00.000[Brazil/DeNoronha]", ZoneOffset::systemDefault);
        checkRangeUpperExists(lower, upper);
    }

    @Test
    public void rangeWithOpenEndSameInstantLowerExists() {
        var lower = DateTimeValue.parse("2019-06-03T05:00:00.000[Brazil/DeNoronha]", ZoneOffset::systemDefault);
        var upper = DateTimeValue.parse("2019-06-03T07:00:00.000[GMT]", ZoneOffset::systemDefault);
        checkRangeLowerExists(lower, upper);
    }

    @Test
    public void rangeWithOpenEndSameInstantUpperExists() {
        var lower = DateTimeValue.parse("2019-06-03T05:00:00.000[Brazil/DeNoronha]", ZoneOffset::systemDefault);
        var upper = DateTimeValue.parse("2019-06-03T07:00:00.000[GMT]", ZoneOffset::systemDefault);
        checkRangeUpperExists(lower, upper);
    }

    @Test
    public void rangeWithOpenEndLowerExists() {
        var lower = DateTimeValue.parse("2019-06-03T07:00:00.000[GMT]", ZoneOffset::systemDefault);
        var upper = DateTimeValue.parse("2019-06-03T06:00:00.000[Brazil/DeNoronha]", ZoneOffset::systemDefault);
        checkRangeLowerExists(lower, upper);
    }

    @Test
    public void rangeWithOpenEndUpperExists() {
        var lower = DateTimeValue.parse("2019-06-03T07:00:00.000[GMT]", ZoneOffset::systemDefault);
        var upper = DateTimeValue.parse("2019-06-03T06:00:00.000[Brazil/DeNoronha]", ZoneOffset::systemDefault);
        checkRangeUpperExists(lower, upper);
    }

    @Test
    public void temporalTime() {
        var APOLLO_11_LOCALTIME = LocalDateTime.of(1969, 7, 20, 20, 17, 0);
        var tZ4 = OffsetTime.ofInstant(APOLLO_11_LOCALTIME.toInstant(ZoneOffset.ofHours(0)), ZoneOffset.ofHours(4));
        var tZ5 = OffsetTime.ofInstant(APOLLO_11_LOCALTIME.toInstant(ZoneOffset.ofHours(0)), ZoneOffset.ofHours(5));
        var offsetTime =
                OffsetTime.ofInstant(APOLLO_11_LOCALTIME.toInstant(ZoneOffset.ofHours(0)), ZoneOffset.ofHours(6));
        var tZ7 = OffsetTime.ofInstant(APOLLO_11_LOCALTIME.toInstant(ZoneOffset.ofHours(0)), ZoneOffset.ofHours(7));
        var tZ8 = OffsetTime.ofInstant(APOLLO_11_LOCALTIME.toInstant(ZoneOffset.ofHours(0)), ZoneOffset.ofHours(8));
        addField(KEY_INDEX, TimeValue.time(offsetTime));
        assertInRangeTT(offsetTime, offsetTime);
        assertOutRangeFT(offsetTime, offsetTime);
        assertOutRangeTF(offsetTime, offsetTime);
        assertInRangeTT(tZ4, tZ8);
        assertInRangeFF(tZ5, tZ7);
    }

    @Test
    public void temporalLocalTime() {
        var storedTime = LocalTime.of(14, 35, 15, 63000);
        addField(KEY_INDEX, LocalTimeValue.localTime(storedTime));
        assertInRangeTT(LocalTime.of(13, 55), LocalTime.of(15, 3));
        assertOutRangeTT(LocalTime.of(14, 36), LocalTime.of(15, 3));
        assertOutRangeTT(LocalTime.of(14, 36), LocalTime.of(15, 3));
        assertExactMiss(LocalTime.of(14, 35));
        assertExactMiss(LocalTime.of(14, 36));
        assertExactMiss(LocalTime.of(14, 35, 15));
        assertExactHit(LocalTime.of(14, 35, 15, 63000));
    }

    @ParameterizedTest
    @EnumSource(
            value = ChronoUnit.class,
            names = {"NANOS", "MILLIS", "MICROS", "SECONDS", "HOURS", "DAYS", "WEEKS", "YEARS"}) //
    public void temporalWithOffsetExact(ChronoUnit chronoUnit) {
        var VOYAGER_2 = ZonedDateTime.ofInstant(
                LocalDateTime.of(1977, 8, 20, 14, 29, 44),
                ZoneOffset.ofHoursMinutes(-6, 0),
                ZoneId.of("America/Chicago"));
        addField(KEY_INDEX, asValue(VOYAGER_2));
        assertExactHit(VOYAGER_2);
        assertExactMiss(VOYAGER_2.minus(1, chronoUnit));
        assertExactMiss(VOYAGER_2.plus(1, chronoUnit));
        var paris = VOYAGER_2.withZoneSameInstant(ZoneId.of("Europe/Paris"));
        assertExactMiss(paris);
    }

    @Test
    public void temporalWithOffsetRange() {
        var VOYAGER_2 = ZonedDateTime.ofInstant(
                LocalDateTime.of(1977, 8, 20, 14, 29, 44),
                ZoneOffset.ofHoursMinutes(-6, 0),
                ZoneId.of("America/Chicago"));
        addField(KEY_INDEX, asValue(VOYAGER_2));
        var anchorage = VOYAGER_2.withZoneSameInstant(ZoneId.of("America/Anchorage"));
        var paris = VOYAGER_2.withZoneSameInstant(ZoneId.of("Europe/Paris"));
        assertInRangeFF(anchorage, paris);
        assertOutRangeFF(paris, anchorage);

        assertInRangeFF(
                VOYAGER_2.plusHours(6).minusDays(1), VOYAGER_2.minusHours(6).plusDays(1));
        assertInRangeFF(
                VOYAGER_2.plusHours(6).minusDays(2), VOYAGER_2.minusHours(6).plusDays(1));
        assertInRangeFF(
                VOYAGER_2.plusHours(6).minusDays(1), VOYAGER_2.minusHours(6).plusDays(2));
        assertInRangeFF(
                VOYAGER_2.plusHours(6).minusDays(2), VOYAGER_2.minusHours(6).plusDays(2));
        assertOutRangeFF(VOYAGER_2.plusHours(6), VOYAGER_2.minusHours(6).plusDays(2));
        assertOutRangeFF(VOYAGER_2.plusHours(6), VOYAGER_2.plusHours(8));
        assertOutRangeFF(VOYAGER_2.minusHours(6), VOYAGER_2.minusHours(8));
        assertOutRangeFF(VOYAGER_2.minusHours(8), VOYAGER_2.minusHours(6));
        assertInRangeFF(VOYAGER_2.minusHours(6), VOYAGER_2.plusDays(1).minusHours(8));
        assertInRangeFF(VOYAGER_2.minusHours(8), VOYAGER_2.plusDays(1).minusHours(6));
    }

    private Value asValue(Object v) {
        if (v instanceof Value value) {
            return value;
        }
        return Values.of(v);
    }

    private void assertInRangeFF(Object from, Object to) {
        assertThat(scoreForQuery(KEY_INDEX, PropertyIndexQuery.range(1, asValue(from), false, asValue(to), false)))
                .isEqualTo(1.0f);
    }

    private void assertInRangeFT(Object from, Object to) {
        assertThat(scoreForQuery(KEY_INDEX, PropertyIndexQuery.range(1, asValue(from), false, asValue(to), true)))
                .isEqualTo(1.0f);
    }

    private void assertInRangeTF(Object from, Object to) {
        assertThat(scoreForQuery(KEY_INDEX, PropertyIndexQuery.range(1, asValue(from), true, asValue(to), false)))
                .isEqualTo(1.0f);
    }

    private void assertInRangeTT(Object from, Object to) {
        assertThat(scoreForQuery(KEY_INDEX, PropertyIndexQuery.range(1, asValue(from), true, asValue(to), true)))
                .isEqualTo(1.0f);
    }

    private void assertOutRangeFF(Object from, Object to) {
        assertThat(scoreForQuery(KEY_INDEX, PropertyIndexQuery.range(1, asValue(from), false, asValue(to), false)))
                .isEqualTo(0.0f);
    }

    private void assertOutRangeFT(Object from, Object to) {
        assertThat(scoreForQuery(KEY_INDEX, PropertyIndexQuery.range(1, asValue(from), false, asValue(to), true)))
                .isEqualTo(0.0f);
    }

    private void assertOutRangeTF(Object from, Object to) {
        assertThat(scoreForQuery(KEY_INDEX, PropertyIndexQuery.range(1, asValue(from), true, asValue(to), false)))
                .isEqualTo(0.0f);
    }

    private void assertOutRangeTT(Object from, Object to) {
        assertThat(scoreForQuery(KEY_INDEX, PropertyIndexQuery.range(1, asValue(from), true, asValue(to), true)))
                .isEqualTo(0.0f);
    }

    private void assertExactHit(Object from) {
        assertThat(scoreForQuery(KEY_INDEX, PropertyIndexQuery.exact(1, asValue(from))))
                .isEqualTo(1.0f);
    }

    private void assertExactMiss(Object from) {
        assertThat(scoreForQuery(KEY_INDEX, PropertyIndexQuery.exact(1, asValue(from))))
                .isEqualTo(0.0f);
    }

    private static List<String> sortedZoneIdsWithOffset(ZonedDateTime reference, ZoneOffset offset) {
        List<String> zoneIds = new ArrayList<>();
        for (var zone : ZoneRulesProvider.getAvailableZoneIds()) {
            var referenceInZone = reference.withZoneSameInstant(ZoneId.of(zone));
            if (offset.equals(referenceInZone.getOffset())) {
                zoneIds.add(zone);
            }
        }
        Collections.sort(zoneIds);
        return zoneIds;
    }

    static final ZonedDateTime VOYAGER_2 = ZonedDateTime.ofInstant(
            LocalDateTime.of(1977, 8, 20, 14, 29, 44), ZoneOffset.ofHours(-6), ZoneId.of("America/Chicago"));

    private static Stream<Arguments> provideSortedZoneIdsWithOffset() {
        return sortedZoneIdsWithOffset(VOYAGER_2, ZoneOffset.ofHours(-6)).stream()
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("provideSortedZoneIdsWithOffset")
    public void temporalWithOffsetZoneIdRange(String zoneId) {
        var entry = VOYAGER_2.withZoneSameInstant(ZoneId.of(zoneId));
        addField(KEY_INDEX, asValue(entry));
        var zoneIdsWithSameOffset = sortedZoneIdsWithOffset(VOYAGER_2, ZoneOffset.ofHours(-6));
        for (String before : zoneIdsWithSameOffset) {
            for (String after : zoneIdsWithSameOffset) {
                if (before.compareTo(zoneId) < 0 && zoneId.compareTo(after) < 0) {
                    assertInRangeFF(
                            VOYAGER_2.withZoneSameInstant(ZoneId.of(before)),
                            VOYAGER_2.withZoneSameInstant(ZoneId.of(after)));
                } else {
                    assertOutRangeFF(
                            VOYAGER_2.withZoneSameInstant(ZoneId.of(before)),
                            VOYAGER_2.withZoneSameInstant(ZoneId.of(after)));
                }
            }
        }
    }

    @Test
    public void testDuration() {
        int keyIndex = KEY_INDEX;
        var value = duration(1, 1, 1, 1);
        addField(keyIndex, value);

        // exact
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, value))).isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex + 1, PropertyIndexQuery.exact(1, value)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, duration(0, 0, 0, 1))))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, duration(0, 0, 1, 0))))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, duration(0, 1, 0, 0))))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, duration(1, 0, 0, 0))))
                .isEqualTo(0.0f);

        // range
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, value, true, value, true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, value, true, value, false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, value, false, value, true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, value, false, value, false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, value, true, null, false)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, value, false, null, false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, null, false, value, true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, null, false, value, false)))
                .isEqualTo(0.0f);
    }

    private void checkTemporalRange(Temporal temporal, TemporalField field, TemporalUnit unit) {
        if (temporal.isSupported(field)) {
            assertInRangeFF(temporal.minus(1, unit), temporal.plus(1, unit));
            assertInRangeTF(temporal, temporal.plus(1, unit));
            assertInRangeFT(temporal.minus(1, unit), temporal);
            assertOutRangeFF(temporal.minus(1, unit), temporal);
        }
    }

    private List<Temporal> allTemporalValues(ZonedDateTime zonedDateTime) {

        var list = new ArrayList<Temporal>();
        list.add(zonedDateTime);
        list.add(zonedDateTime.toLocalDateTime());
        list.add(zonedDateTime.toLocalTime());
        list.add(zonedDateTime.toLocalDate());
        list.add(zonedDateTime.toOffsetDateTime().toOffsetTime());
        return list;
    }

    private void addAndCheckFieldsAreIndependent(List<Temporal> temporals) {

        int keyIndex = KEY_INDEX;

        var unwritten = new ArrayList<>(temporals);
        var written = new ArrayList<>();
        while (!unwritten.isEmpty()) {
            var time = unwritten.getFirst();
            for (var tQuery : unwritten) {
                assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, asValue(tQuery))))
                        .isEqualTo(0.0f);
            }

            addField(keyIndex, asValue(time));
            unwritten.removeFirst();
            written.add(time);

            for (var tQuery : written) {
                assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, asValue(tQuery))))
                        .as("Expected to find " + tQuery + " in the index. Written list "
                                + Arrays.toString(written.toArray()))
                        .isEqualTo(1.0f);
            }
        }
    }

    private static class TestVectorDocumentStructure extends VectorDocumentStructure {
        @Override
        public String vectorValueKeyFor(int dimensions) {
            return "vectorValueKeyFor" + dimensions + "Dimensions";
        }

        @Override
        public String booleanValueKeyFor(int propertyIndex) {
            return "booleanValueKeyFor" + propertyIndex + "Value";
        }

        @Override
        public String integralValueKeyFor(int propertyIndex) {
            return "integralValueKeyFor" + propertyIndex + "Value";
        }

        @Override
        public String floatingValueKeyFor(int propertyIndex) {
            return "floatingValueKeyFor" + propertyIndex + "Value";
        }

        @Override
        public String textValueKeyFor(int propertyIndex) {
            return "textValueKeyFor" + propertyIndex + "Value";
        }

        @Override
        public String temporalValueKeyFor(int propertyIndex, ValueGroup group) {
            return "temporalValueKeyFor" + propertyIndex + "Group" + group.name();
        }

        @Override
        public String zoneOffsetValueKeyFor(int propertyIndex, ValueGroup group) {
            return "zoneOffsetValueKeyFor" + propertyIndex + "Group" + group.name();
        }

        @Override
        public String zoneIdValueKeyFor(int propertyIndex, ValueGroup group) {
            return "zoneIdValueKeyFor" + propertyIndex + "Group" + group.name();
        }

        @Override
        public String durationNanosValueKeyFor(int propertyIndex) {
            return "nanoseconds" + propertyIndex + "Value";
        }

        @Override
        public String durationSecondsValueKeyFor(int propertyIndex) {
            return "seconds" + propertyIndex + "Value";
        }

        @Override
        public String durationDaysValueKeyFor(int propertyIndex) {
            return "days" + propertyIndex + "Value";
        }

        @Override
        public String durationMonthsValueKeyFor(int propertyIndex) {
            return "months" + propertyIndex + "Value";
        }
    }
}
