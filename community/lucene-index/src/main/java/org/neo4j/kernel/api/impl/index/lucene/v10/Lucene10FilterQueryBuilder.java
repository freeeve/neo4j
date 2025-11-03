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

import org.apache.lucene.document.KeywordField;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.ExactPredicate;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.RangePredicate;
import org.neo4j.kernel.api.impl.schema.vector.VectorDocumentStructure;
import org.neo4j.util.Preconditions;
import org.neo4j.values.storable.BooleanValue;
import org.neo4j.values.storable.FloatingPointValue;
import org.neo4j.values.storable.IntegralValue;
import org.neo4j.values.storable.NumberValue;
import org.neo4j.values.storable.StringValue;
import org.neo4j.values.storable.Value;

final class Lucene10FilterQueryBuilder {

    private final VectorDocumentStructure vectorDocumentStructure;

    private Lucene10FilterQueryBuilder(VectorDocumentStructure documentStructure) {
        this.vectorDocumentStructure = documentStructure;
    }

    private Query queryForPredicate(int propertyIndex, PropertyIndexQuery predicate) {
        return switch (predicate) {
            case ExactPredicate exactPredicate -> queryForExact(propertyIndex, exactPredicate);
            case RangePredicate<?> rangePredicate -> queryForRange(propertyIndex, rangePredicate);
            case null, default ->
                throw new IllegalArgumentException(
                        "Unexpected filter query predicate (neither " + "range query nor exact query) " + predicate);
        };
    }

    private Query queryForExact(int propertyIndex, ExactPredicate predicate) {
        return switch (predicate.value()) {
            case BooleanValue b ->
                Lucene10ValueFields.BooleanField.newExactQuery(
                        vectorDocumentStructure.booleanValueKeyFor(propertyIndex), b.booleanValue());
            // IntegralValue case is distinct from the FloatingPointValue case
            // Just in case any checks in `SingleLongField.newExactQuery` fail first,
            // before `SingleDoubleField.newExactQuery` is called;
            // the error message in this case is likely to mention integers.
            case IntegralValue i -> {
                Query integralQuery = Lucene10ValueFields.SingleLongField.newExactQuery(
                        vectorDocumentStructure.integralValueKeyFor(propertyIndex), i.longValue());
                Query floatingPointQuery = Lucene10ValueFields.SingleDoubleField.newExactQuery(
                        vectorDocumentStructure.floatingValueKeyFor(propertyIndex), i.doubleValue());
                yield eitherQuery(integralQuery, floatingPointQuery);
            }
            case FloatingPointValue f -> {
                double doubleValue = f.doubleValue();
                Preconditions.checkArgument(
                        Double.isFinite(doubleValue), "Floating point value for query must be " + "finite");
                Query floatingPointQuery = Lucene10ValueFields.SingleDoubleField.newExactQuery(
                        vectorDocumentStructure.floatingValueKeyFor(propertyIndex), doubleValue);
                Query integralQuery = Lucene10ValueFields.SingleLongField.newExactQuery(
                        vectorDocumentStructure.integralValueKeyFor(propertyIndex), f.longValue());
                yield eitherQuery(integralQuery, floatingPointQuery);
            }
            case StringValue s ->
                KeywordField.newExactQuery(vectorDocumentStructure.textValueKeyFor(propertyIndex), s.stringValue());
            case null -> null;
            default ->
                throw new IllegalArgumentException(
                        String.format("Unexpected value type in filter predicate '%s'", predicate));
        };
    }

    private Query queryForNumericRangeFrom(
            int propertyIndex, NumberValue from, boolean fromInclusive, Value to, boolean toInclusive) {
        Preconditions.checkArgument(to instanceof NumberValue, "Range \"to\" value must be a number");
        double fromDouble = from.doubleValue();
        NumberValue toValue = (NumberValue) to;
        double toDouble = toValue.doubleValue();
        Preconditions.checkArgument(Double.isFinite(fromDouble), "Range \"from\" must be finite");
        Preconditions.checkArgument(Double.isFinite(toDouble), "Range \"to\" must be finite");

        boolean rangeIsIntegral = from instanceof IntegralValue && toValue instanceof IntegralValue;
        Query longQuery = null;
        if (rangeIsIntegral) {
            // if both from and to are integral values, the bounds should be stepped as integers
            // and we should do the integral checking first
            long fromLong = from.longValue();
            if (!fromInclusive && fromLong < Long.MAX_VALUE) fromLong++;
            long toLong = toValue.longValue();
            if (!toInclusive && toLong > Long.MIN_VALUE) toLong--;
            longQuery = Lucene10ValueFields.SingleLongField.newRangeQuery(
                    vectorDocumentStructure.integralValueKeyFor(propertyIndex), fromLong, toLong);
        }
        // Now build a floating point query
        if (!fromInclusive) fromDouble = Math.nextUp(fromDouble);
        if (!toInclusive) toDouble = Math.nextDown(toDouble);
        Query doubleQuery = Lucene10ValueFields.SingleDoubleField.newRangeQuery(
                vectorDocumentStructure.floatingValueKeyFor(propertyIndex), fromDouble, toDouble);
        if (!rangeIsIntegral) {
            // build an integral query with floating point values
            long fromIntegral = Double.valueOf(Math.ceil(fromDouble)).longValue();
            long toIntegral = Double.valueOf(Math.floor(toDouble)).longValue();
            // These values may now be out of range. For example, 42.3 .. 42.6 becomes 43 .. 42
            // In this specific case, we know the query will return nothing, but we should not error;
            // the caller supplied us with a valid floating range, which we will use.
            // we should instead return only the floating point / double query.
            if (toIntegral < fromIntegral) {
                return doubleQuery;
            }
            longQuery = Lucene10ValueFields.SingleLongField.newRangeQuery(
                    vectorDocumentStructure.integralValueKeyFor(propertyIndex), fromIntegral, toIntegral);
        }
        return eitherQuery(longQuery, doubleQuery);
    }

    private Query queryForRange(int propertyIndex, RangePredicate<?> predicate) {
        Value from = predicate.fromValue();
        boolean fromInclusive = predicate.fromInclusive();
        Value to = predicate.toValue();
        boolean toInclusive = predicate.toInclusive();
        return switch (from) {
            case BooleanValue bFrom -> {
                if (to instanceof BooleanValue bTo) {
                    if (bFrom.booleanValue() && !fromInclusive) {
                        throw new IllegalArgumentException(
                                "Lower bound true for boolean range may not be exclusive. The range will always be "
                                        + "empty.");
                    }
                    if (!bTo.booleanValue() && !toInclusive) {
                        throw new IllegalArgumentException(
                                "Upper bound false for boolean range may not be exclusive. The range will always be "
                                        + "empty.");
                    }
                    yield Lucene10ValueFields.BooleanField.newRangeQuery(
                            vectorDocumentStructure.booleanValueKeyFor(propertyIndex),
                            bFrom.booleanValue() == fromInclusive,
                            bTo.booleanValue() && toInclusive);
                } else {
                    throw new IllegalArgumentException("Upper bound for boolean range must be a boolean value");
                }
            }
            case NumberValue nFrom -> queryForNumericRangeFrom(propertyIndex, nFrom, fromInclusive, to, toInclusive);
            case StringValue sFrom -> {
                if (to instanceof StringValue sTo) {
                    Preconditions.checkArgument(
                            sFrom.compareTo(sTo) <= 0, "Upper bound for string range cannot precede the lower bound");
                    yield TermRangeQuery.newStringRange(
                            vectorDocumentStructure.textValueKeyFor(propertyIndex),
                            sFrom.stringValue(),
                            sTo.stringValue(),
                            fromInclusive,
                            toInclusive);
                } else {
                    throw new IllegalArgumentException("Upper bound for string range must be a string value");
                }
            }
            case null -> null;
            default ->
                throw new IllegalArgumentException(
                        String.format("Unexpected value type in filter predicate '%s'", predicate));
        };
    }

    private static BooleanQuery eitherQuery(Query q1, Query q2) {
        return new BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(1)
                .add(new ConstantScoreQuery(q1), Occur.SHOULD)
                .add(new ConstantScoreQuery(q2), Occur.SHOULD)
                .build();
    }

    /**
     *
     * @param documentStructure how to map field indexes to Lucene document key values
     * @param filterQueryFrom first query which is a filter query (e.g. 1 when preceded by a vector query)
     * @param filterQueries list of queries
     * @return
     */
    static Query build(
            VectorDocumentStructure documentStructure, int filterQueryFrom, PropertyIndexQuery... filterQueries) {
        BooleanQuery.Builder builder = null;
        var queryFactory = new Lucene10FilterQueryBuilder(documentStructure);
        for (int i = filterQueryFrom; i < filterQueries.length; i++) {
            var filterQuery = filterQueries[i];
            if (filterQuery != null) {
                if (builder == null) {
                    builder = new BooleanQuery.Builder();
                }
                builder.add(queryFactory.queryForPredicate(i, filterQuery), Occur.FILTER);
            }
        }
        if (builder == null) {
            return new MatchAllDocsQuery();
        }
        return builder.build();
    }
}
