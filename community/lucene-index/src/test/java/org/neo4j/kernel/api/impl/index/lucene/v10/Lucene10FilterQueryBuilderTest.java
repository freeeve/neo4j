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

import java.time.LocalTime;
import java.util.Arrays;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.ConstantScoreQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.kernel.api.impl.schema.vector.VectorDocumentStructure;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

public class Lucene10FilterQueryBuilderTest {

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
        Arrays.fill(this.queries, null);
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

        int keyIndex = 4;
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
    public void testNullMatchesString() {
        int keyIndex = 4;
        addField(keyIndex, Values.of("bravo"));
        assertThat(scoreForQuery(keyIndex, new PropertyIndexQuery[] {null})).isEqualTo(1.0f);
    }

    @Test
    public void testInteger() {

        int keyIndex = 4;
        addField(keyIndex, Values.of(42));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 42))).isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex + 1, PropertyIndexQuery.exact(1, 42))).isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 43))).isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 41))).isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, 42.0))).isEqualTo(1.0f);
    }

    @Test
    public void testFloat() {
        int keyIndex = 4;
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
        int keyIndex = 4;
        addField(keyIndex, Values.of(true));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, true))).isEqualTo(1.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, false))).isEqualTo(0.0f);
    }

    @Test
    public void testBooleanFalse() {
        int keyIndex = 4;
        addField(keyIndex, Values.of(false));
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, true))).isEqualTo(0.0f);
        assertThat(scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, false))).isEqualTo(1.0f);
    }

    @Test
    public void testConjunction() {
        int keyIndex = 4;
        addField(keyIndex++, Values.of("alpha"));
        addField(keyIndex++, Values.of(25.4));
        addField(keyIndex, Values.of(true));
        assertThat(scoreForQuery(
                        4,
                        PropertyIndexQuery.exact(1, "alpha"),
                        PropertyIndexQuery.exact(1, 25.4),
                        PropertyIndexQuery.exact(1, true)))
                .isEqualTo(1.0f);
        assertThat(scoreForQuery(
                        4,
                        PropertyIndexQuery.exact(1, "bravo"),
                        PropertyIndexQuery.exact(1, 25.4),
                        PropertyIndexQuery.exact(1, true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(
                        4,
                        PropertyIndexQuery.exact(1, "alpha"),
                        PropertyIndexQuery.exact(1, 25.3),
                        PropertyIndexQuery.exact(1, true)))
                .isEqualTo(0.0f);
        assertThat(scoreForQuery(
                        4,
                        PropertyIndexQuery.exact(1, "alpha"),
                        PropertyIndexQuery.exact(1, 25.4),
                        PropertyIndexQuery.exact(1, false)))
                .isEqualTo(0.0f);
    }

    @Test
    public void testInvalidQueryType() {
        int keyIndex = 4;
        addField(keyIndex, Values.of(42));

        assertThatThrownBy(() -> scoreForQuery(keyIndex, PropertyIndexQuery.fulltextSearch("hello")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected filter query predicate");
    }

    @Test
    public void testBadExactQueryType() {
        int keyIndex = 4;
        addField(keyIndex, Values.of(42));

        assertThatThrownBy(
                        () -> scoreForQuery(keyIndex, PropertyIndexQuery.exact(1, Values.of(LocalTime.of(10, 30, 45)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected value type in filter predicate");
    }

    @Test
    public void testNullMatchesInteger() {
        int keyIndex = 4;
        addField(keyIndex, Values.of(4));
        assertThat(scoreForQuery(keyIndex, new PropertyIndexQuery[] {null})).isEqualTo(1.0f);
    }

    @Test
    public void testNullMatchesFloat() {
        int keyIndex = 4;
        addField(keyIndex, Values.of(7.5f));
        assertThat(scoreForQuery(keyIndex, new PropertyIndexQuery[] {null})).isEqualTo(1.0f);
    }

    @Test
    public void testNullMatchesBoolean() {
        int keyIndex = 4;
        addField(keyIndex, Values.of(true));
        assertThat(scoreForQuery(keyIndex, new PropertyIndexQuery[] {null})).isEqualTo(1.0f);
    }

    @Test
    public void testStringRange() {

        int keyIndex = 4;
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
    public void testIntegerRange() {

        int keyIndex = 4;
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

        int keyIndex = 4;
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

        int keyIndex = 4;
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

        int keyIndex = 4;
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

        int keyIndex = 4;
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
    public void testBadRangeQueryType() {
        int keyIndex = 4;
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
    }

    @Test
    public void badIndexFieldValue() {
        int keyIndex = 4;
        assertThatThrownBy(() -> addField(keyIndex, Values.of(new int[] {42, 43})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported value type: IntegerArray for vector index field 4");
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
        public String temporalValueKeyFor(int propertyIndex) {
            return "temporalValueKeyFor" + propertyIndex + "Value";
        }
    }
}
