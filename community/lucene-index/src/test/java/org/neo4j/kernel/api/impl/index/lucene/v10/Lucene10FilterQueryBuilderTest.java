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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.values.storable.CoordinateReferenceSystem.CARTESIAN;
import static org.neo4j.values.storable.Values.NO_VALUE;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.ConstantScoreQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.kernel.api.StatementConstants;
import org.neo4j.kernel.api.impl.schema.vector.VectorDocumentStructure;
import org.neo4j.values.storable.DateTimeValue;
import org.neo4j.values.storable.DateValue;
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
        var field = Lucene10DocumentsFactory.indexableField(documentStructure, fieldPosition, value);
        index.addField(field, analyzer);
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
    public void testString() {

        int keyIndex = KEY_INDEX;
        addField(keyIndex, Values.of("bravo"));
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
        addField(keyIndex1, Values.of("bravo"));
        assertThat(scoreForQuery(keyIndex1, PropertyIndexQuery.all(1))).isEqualTo(1.0f);
    }

    @Test
    public void testInteger() {

        int keyIndex = KEY_INDEX;
        addField(keyIndex, Values.of(42));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 42))).isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex + 1, PropertyIndexQuery.exact(1, 42))).isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 43))).isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 41))).isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 42.0))).isEqualTo(1.0f);
    }

    @Test
    public void testFloat() {
        int keyIndex = KEY_INDEX;
        addField(keyIndex, Values.of(42.5));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 42.5))).isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 42))).isEqualTo(0.0f);

        keyIndex++;
        addField(keyIndex, Values.of(42.0));
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
        addField(keyIndex, Values.of(true));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, true))).isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, false))).isEqualTo(0.0f);
    }

    @Test
    public void testBooleanFalse() {
        int keyIndex = KEY_INDEX;
        addField(keyIndex, Values.of(false));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, true))).isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, false))).isEqualTo(1.0f);
    }

    @Test
    public void testConjunction() {
        int keyIndex = KEY_INDEX;
        addField(keyIndex++, Values.of("alpha"));
        addField(keyIndex++, Values.of(25.4));
        addField(keyIndex, Values.of(true));
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
        addField(keyIndex, Values.of(42));

        assertThatThrownBy(
                        () -> scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, Values.pointValue(CARTESIAN, 2, 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected value type in filter predicate");
        assertThatThrownBy(() -> scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, Values.of(Period.of(10, 2, 12)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected value type in filter predicate");
        assertThatThrownBy(() -> scoreForQuery(
                        keyIndex, PropertyIndexQuery.exact(1, Values.of(Duration.of(10, ChronoUnit.SECONDS)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected value type in filter predicate");
    }

    @Test
    public void testAllMatchesInteger() {
        int keyIndex = 4;
        addField(keyIndex, Values.of(4));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.all(1))).isEqualTo(1.0f);
    }

    @Test
    public void testAllMatchesFloat() {
        int keyIndex = 4;
        addField(keyIndex, Values.of(7.5f));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.all(1))).isEqualTo(1.0f);
    }

    @Test
    public void testAllMatchesBoolean() {
        int keyIndex = 4;
        addField(keyIndex, Values.of(true));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.all(1))).isEqualTo(1.0f);
    }

    @Test
    public void testTemporalQueryTypes() {
        var APOLLO_UTC = ZonedDateTime.of(1969, 07, 20, 20, 17, 0, 0, ZoneOffset.UTC);
        addAndCheckFieldsAreIndependent(allTemporalValues(APOLLO_UTC));
    }

    @Test
    public void testTemporalQueryTypeRanges() {
        var APOLLO_UTC = ZonedDateTime.of(1969, 07, 20, 20, 17, 0, 0, ZoneOffset.UTC);
        addAndCheckFieldsAreIndependent(allTemporalValues(APOLLO_UTC));

        var temporals = allTemporalValues(APOLLO_UTC);
        for (var temporal : temporals) {
            if (temporal.isSupported(ChronoField.YEAR)) {
                checkTemporalRange(temporal, ChronoField.YEAR, ChronoUnit.YEARS);
                checkTemporalRange(temporal, ChronoField.INSTANT_SECONDS, ChronoUnit.SECONDS);
                checkTemporalRange(temporal, ChronoField.DAY_OF_YEAR, ChronoUnit.DAYS);
                checkTemporalRange(temporal, ChronoField.HOUR_OF_DAY, ChronoUnit.HOURS);
                checkTemporalRange(temporal, ChronoField.OFFSET_SECONDS, ChronoUnit.SECONDS);
            }
        }
    }

    @Test
    public void testStringRange() {

        int keyIndex = KEY_INDEX;
        addField(keyIndex, Values.of("bravo"));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, "alpha", true, "charlie", false)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, "bravo", true, "charlie", false)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, "bravo", false, "charlie", false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, "alpha", false, "bravo", true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, "alpha", false, "bravo", false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, Values.charValue('a'), true, Values.charValue('b'), true)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testCharRange() {

        int keyIndex = KEY_INDEX;
        addField(keyIndex, Values.of('b'));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of('a'), true, Values.of('c'), false)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of('b'), true, Values.of('c'), false)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of('b'), false, Values.of('c'), false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of('a'), false, Values.of('b'), true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of('a'), false, Values.of('b'), false)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testIntegerRange() {

        int keyIndex = KEY_INDEX;
        addField(keyIndex, Values.of(42));

        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 41, true, 43, true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 42, true, 43, true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 42, false, 43, true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 38, false, 42, true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 38, false, 42, false)))
                .isEqualTo(0.0f);

        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 42, false, 42.5, true)))
                .isEqualTo(0.0f);

        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 41.9f, true, 42.1f, true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 42.0f, true, 42.1f, false)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 42.0f, false, 43.0f, true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 38.0f, false, 42.0, true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 38.0f, false, 42.0, false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 42.3f, false, 42.5, false)))
                .isEqualTo(0.0f);

        // empty ranges
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 42.5f, false, 42.3, false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 44, false, 43, false)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testFloatRange() {

        int keyIndex = KEY_INDEX;
        addField(keyIndex, Values.of(42.5));

        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 41, true, 43, true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 42, true, 43, true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 42, false, 43, true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 38, false, 42, true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 38, false, 42.5, false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 38, false, 42.5, false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 42.4, false, 42.6, false)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 42.5, true, 42.5, true)))
                .isEqualTo(1.0f);
        // check empty range queries
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 42.5, true, 42.5, false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, 42.5, false, 42.5, true)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testBigFloatRange() {

        int keyIndex = KEY_INDEX;
        var bigFloat = 9.23e18; // large than Long.MAX_VALUE
        addField(keyIndex, Values.of(bigFloat));

        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Long.MIN_VALUE, true, Long.MAX_VALUE, true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Long.MIN_VALUE, true, 10e18, true)))
                .isEqualTo(1.0f);
    }

    @Test
    public void testFiniteFloatRange() {
        assertThat(scoreForQuery(4, PropertyIndexQuery.range(1, Double.NaN, true, 42, true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(4, PropertyIndexQuery.range(1, 42, true, Double.NaN, true)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testBooleanRangeTrue() {

        int keyIndex = KEY_INDEX;
        addField(keyIndex, Values.of(true));

        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(false), true, Values.of(true), true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(false), true, Values.of(true), false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(false), true, Values.of(false), true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(false), false, Values.of(true), true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(true), true, Values.of(true), true)))
                .isEqualTo(1.0f);
        // empty ranges
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(true), false, Values.of(true), true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(
                        keyIndex, PropertyIndexQuery.range(1, Values.of(true), false, Values.of(false), false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(true), false, Values.of(false), true)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testBooleanRangeFalse() {

        int keyIndex = KEY_INDEX;
        addField(keyIndex, Values.of(false));

        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(false), true, Values.of(true), true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(false), true, Values.of(true), false)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(false), true, Values.of(false), true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(false), false, Values.of(true), true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(true), true, Values.of(true), true)))
                .isEqualTo(0.0f);
        // empty ranges
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(true), false, Values.of(true), true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(true), true, Values.of(false), false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.range(1, Values.of(true), true, Values.of(false), true)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testTemporalRangePoint() {
        var APOLLO_11_LOCALTIME = LocalDateTime.of(1969, 7, 20, 20, 17, 0);
        var APOLLO_11_UTC = ZonedDateTime.of(APOLLO_11_LOCALTIME, ZoneOffset.UTC);
        var APOLLO_12_LOCALTIME = LocalDateTime.of(1969, 11, 24, 20, 58, 24);
        var APOLLO_12_UTC = ZonedDateTime.of(APOLLO_12_LOCALTIME, ZoneOffset.UTC);

        int keyIndex = KEY_INDEX;
        addField(keyIndex, Values.of(APOLLO_11_UTC));
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, Values.of(APOLLO_11_UTC), true, Values.of(APOLLO_11_UTC), true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, Values.of(APOLLO_12_UTC), true, Values.of(APOLLO_12_UTC), true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, Values.of(APOLLO_11_UTC), false, Values.of(APOLLO_11_UTC), true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, Values.of(APOLLO_11_UTC), true, Values.of(APOLLO_11_UTC), false)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, Values.of(APOLLO_11_UTC), false, Values.of(APOLLO_11_UTC), false)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testTemporalRangeInfinityMax() {

        addField(KEY_INDEX, Values.of(DateTimeValue.MAX_VALUE));
        assertThat(scoreForQuery(KEY_INDEX, PropertyIndexQuery.exact(1, Values.of(DateTimeValue.MAX_VALUE))))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        KEY_INDEX,
                        PropertyIndexQuery.range(1, Values.of(DateTimeValue.MAX_VALUE), true, NO_VALUE, true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        KEY_INDEX,
                        PropertyIndexQuery.range(1, Values.of(DateTimeValue.MAX_VALUE), false, NO_VALUE, true)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testTemporalRangeInfinityMin() {

        addField(KEY_INDEX, Values.of(DateTimeValue.MIN_VALUE));
        assertThat(scoreForQuery(KEY_INDEX, PropertyIndexQuery.exact(1, Values.of(DateTimeValue.MIN_VALUE))))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        KEY_INDEX,
                        PropertyIndexQuery.range(1, NO_VALUE, true, Values.of(DateTimeValue.MIN_VALUE), true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        KEY_INDEX,
                        PropertyIndexQuery.range(1, NO_VALUE, true, Values.of(DateTimeValue.MIN_VALUE), false)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testTemporalRangeWider() {
        var APOLLO_12_LOCALTIME = LocalDateTime.of(1969, 11, 24, 20, 58, 24);
        var APOLLO_12_UTC = ZonedDateTime.of(APOLLO_12_LOCALTIME, ZoneOffset.UTC);
        var APOLLO_14_LOCALTIME = LocalDateTime.of(1971, 2, 9, 21, 05, 0);
        var APOLLO_14_UTC = ZonedDateTime.of(APOLLO_14_LOCALTIME, ZoneOffset.UTC);
        var APOLLO_16_LOCALTIME = LocalDateTime.of(1972, 4, 25, 05, 47, 0);
        var APOLLO_16_UTC = ZonedDateTime.of(APOLLO_16_LOCALTIME, ZoneOffset.UTC);

        int keyIndex = KEY_INDEX;
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, Values.of(APOLLO_12_UTC), true, Values.of(APOLLO_16_UTC), true)))
                .isEqualTo(0.0f);

        addField(keyIndex, Values.of(APOLLO_14_UTC));

        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, Values.of(APOLLO_14_UTC), true, Values.of(APOLLO_14_UTC), true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, Values.of(APOLLO_14_UTC), true, Values.of(APOLLO_16_UTC), true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, Values.of(APOLLO_12_UTC), true, Values.of(APOLLO_14_UTC), true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, Values.of(APOLLO_12_UTC), true, Values.of(APOLLO_16_UTC), true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(
                                1,
                                Values.of(APOLLO_14_UTC.plusSeconds(1)),
                                true,
                                Values.of(APOLLO_14_UTC.plusSeconds(2)),
                                true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(
                                1,
                                Values.of(APOLLO_14_UTC.minusSeconds(1)),
                                true,
                                Values.of(APOLLO_14_UTC.plusSeconds(2)),
                                true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(
                                1,
                                Values.of(APOLLO_14_UTC.minusSeconds(1).plusNanos(1000)),
                                true,
                                Values.of(APOLLO_14_UTC.plusSeconds(1).minusNanos(1000)),
                                true)))
                .isEqualTo(1.0f);
    }

    @Test
    public void testTemporalRangeImplementationArtifacts() {

        var APOLLO_16_LOCALTIME = LocalDateTime.of(1972, 4, 25, 05, 47, 0);
        var APOLLO_16_UTC = ZonedDateTime.of(APOLLO_16_LOCALTIME, ZoneOffset.UTC);

        int keyIndex = KEY_INDEX;
        addField(keyIndex, Values.of(APOLLO_16_UTC.plusNanos(10_000_000)));

        // This is now correct because temporal range querying builds a compound query
        // to take care of the nanosecond ranges within the endpoint seconds of the time range.
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(
                                1,
                                Values.of(APOLLO_16_UTC.minusNanos(10_000_000)),
                                true,
                                Values.of(APOLLO_16_UTC),
                                true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(
                                1,
                                Values.of(APOLLO_16_UTC.minusSeconds(2).plusNanos(20_000_000)),
                                true,
                                Values.of(APOLLO_16_UTC),
                                true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(
                                1,
                                Values.of(APOLLO_16_UTC.minusSeconds(2).plusNanos(20_000_000)),
                                true,
                                Values.of(APOLLO_16_UTC.plusNanos(15_000_000)),
                                true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(
                                1,
                                Values.of(APOLLO_16_UTC.minusSeconds(2).plusNanos(20_000_000)),
                                true,
                                Values.of(APOLLO_16_UTC.plusSeconds(1).plusNanos(5_000_000)),
                                true)))
                .isEqualTo(1.0f);
    }

    @Test
    public void testBadRangeQueryType() {
        int keyIndex = KEY_INDEX;
        addField(keyIndex, Values.of(42));

        assertThatThrownBy(() -> scoreForQuery(
                        keyIndex, PropertyIndexQuery.range(1, Values.of(41), true, Values.charValue('b'), true)))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining(
                        "class org.neo4j.values.storable.CharValue cannot be cast to class org.neo4j.values.storable.NumberValue");

        assertThatThrownBy(() -> scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, Values.of(LocalTime.of(10, 30, 45)), true, Values.of('b'), true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected value type in filter predicate");

        // Incomparable, so 0.0f
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(
                                1,
                                Values.of(Duration.of(10, ChronoUnit.MINUTES)),
                                true,
                                Values.of(Duration.of(20, ChronoUnit.MINUTES)),
                                true)))
                .isEqualTo(0.0f);
    }

    @Test
    public void badIndexFieldValue() {
        int keyIndex = KEY_INDEX;
        assertThatThrownBy(() -> addField(keyIndex, Values.of(new int[] {42, 43})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported value type: IntegerArray for vector index field 4");
    }

    @Test
    public void temporalDate() {
        var APOLLO_11_LOCALTIME = LocalDateTime.of(1969, 7, 20, 20, 17, 0);
        var APOLLO_11_UTC = ZonedDateTime.of(APOLLO_11_LOCALTIME, ZoneOffset.UTC);
        var APOLLO_11_DATE = DateValue.date(LocalDate.from(APOLLO_11_LOCALTIME));

        int keyIndex = KEY_INDEX;
        addField(keyIndex, APOLLO_11_DATE);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(1, Values.of(APOLLO_11_DATE), true, Values.of(APOLLO_11_DATE), true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        keyIndex,
                        PropertyIndexQuery.range(
                                1,
                                Values.of(APOLLO_11_UTC.minusDays(1)),
                                true,
                                Values.of(APOLLO_11_UTC.plusDays(1)),
                                true)))
                .isEqualTo(0.0f);
    }

    private void checkTemporalRange(Temporal temporal, TemporalField field, TemporalUnit unit) {
        if (temporal.isSupported(field)) {
            assertThat(scoreForQuery(
                            KEY_INDEX,
                            PropertyIndexQuery.range(
                                    1,
                                    Values.of(temporal.minus(1, unit)),
                                    false,
                                    Values.of(temporal.plus(1, unit)),
                                    false)))
                    .isEqualTo(1.0f);
            assertThat(scoreForQuery(
                            KEY_INDEX,
                            PropertyIndexQuery.range(
                                    1, Values.of(temporal), false, Values.of(temporal.plus(1, unit)), true)))
                    .isEqualTo(0.0f);
            assertThat(scoreForQuery(
                            KEY_INDEX,
                            PropertyIndexQuery.range(
                                    1, Values.of(temporal), true, Values.of(temporal.plus(1, unit)), false)))
                    .isEqualTo(1.0f);
            assertThat(scoreForQuery(
                            KEY_INDEX,
                            PropertyIndexQuery.range(
                                    1, Values.of(temporal.minus(1, unit)), false, Values.of(temporal), true)))
                    .isEqualTo(1.0f);
            assertThat(scoreForQuery(
                            KEY_INDEX,
                            PropertyIndexQuery.range(
                                    1, Values.of(temporal.minus(1, unit)), false, Values.of(temporal), false)))
                    .isEqualTo(0.0f);
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
                assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, Values.of(tQuery))))
                        .isEqualTo(0.0f);
            }

            addField(keyIndex, Values.of(time));
            unwritten.removeFirst();
            written.add(time);

            for (var tQuery : written) {
                assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, Values.of(tQuery))))
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
    }
}
