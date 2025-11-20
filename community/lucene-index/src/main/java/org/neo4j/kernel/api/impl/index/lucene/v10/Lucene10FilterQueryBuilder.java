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

import static org.neo4j.values.storable.Values.NO_VALUE;

import java.time.Instant;
import org.apache.lucene.document.KeywordField;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.ExactPredicate;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.RangePredicate;
import org.neo4j.internal.schema.IndexQuery;
import org.neo4j.kernel.api.impl.schema.vector.VectorDocumentStructure;
import org.neo4j.values.storable.BooleanValue;
import org.neo4j.values.storable.FloatingPointValue;
import org.neo4j.values.storable.IntegralValue;
import org.neo4j.values.storable.NoValue;
import org.neo4j.values.storable.NumberValue;
import org.neo4j.values.storable.NumberValues;
import org.neo4j.values.storable.StringValue;
import org.neo4j.values.storable.TemporalValue;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

final class Lucene10FilterQueryBuilder {

    private final VectorDocumentStructure vectorDocumentStructure;

    private Lucene10FilterQueryBuilder(VectorDocumentStructure documentStructure) {
        this.vectorDocumentStructure = documentStructure;
    }

    private Query queryForPredicate(int propertyIndex, PropertyIndexQuery predicate) {
        return switch (predicate) {
            case PropertyIndexQuery.IncomparableExactPredicate ignored -> new MatchNoDocsQuery();
            case ExactPredicate exactPredicate -> queryForExact(propertyIndex, exactPredicate);
            case PropertyIndexQuery.IncomparableRangePredicate<?> ignored -> new MatchNoDocsQuery();
            case RangePredicate<?> rangePredicate -> queryForRange(propertyIndex, rangePredicate);
            case null, default ->
                throw new IllegalArgumentException(
                        "Unexpected filter query predicate (neither " + "range query nor exact query) " + predicate);
        };
    }

    private Query queryForExact(int propertyIndex, ExactPredicate predicate) {
        return switch (predicate.value()) {
            case NoValue ignore -> new MatchNoDocsQuery();
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
                yield anyQuery(integralQuery, floatingPointQuery);
            }
            case FloatingPointValue f when !Double.isFinite(f.doubleValue()) -> new MatchNoDocsQuery();
            case FloatingPointValue f -> {
                double doubleValue = f.doubleValue();
                Query floatingPointQuery = Lucene10ValueFields.SingleDoubleField.newExactQuery(
                        vectorDocumentStructure.floatingValueKeyFor(propertyIndex), doubleValue);
                if ((NumberValues.numbersEqual(doubleValue, f.longValue()))) {

                    Query integralQuery = Lucene10ValueFields.SingleLongField.newExactQuery(
                            vectorDocumentStructure.integralValueKeyFor(propertyIndex), f.longValue());

                    yield anyQuery(integralQuery, floatingPointQuery);
                } else {
                    yield floatingPointQuery;
                }
            }
            case TextValue s ->
                KeywordField.newExactQuery(vectorDocumentStructure.textValueKeyFor(propertyIndex), s.stringValue());
            case TemporalValue<?, ?> tv -> {
                Instant instant = Lucene10ValueFields.instantFromTemporal(tv);
                yield Lucene10ValueFields.SingleInstantField.newExactQuery(
                        vectorDocumentStructure.temporalValueKeyFor(propertyIndex, tv.valueGroup()), instant);
            }
            case null -> null;
            default ->
                throw new IllegalArgumentException(
                        String.format("Unexpected value type in filter predicate '%s'", predicate));
        };
    }

    private Query queryForNumericRangeFrom(
            int propertyIndex, NumberValue from, boolean fromInclusive, NumberValue to, boolean toInclusive) {
        double fromDouble = from.doubleValue();
        double toDouble = to.doubleValue();

        boolean rangeIsIntegral = from instanceof IntegralValue && to instanceof IntegralValue;
        Query longQuery = null;
        if (rangeIsIntegral) {
            // if both from and to are integral values, the bounds should be stepped as integers
            // and we should do the integral checking first
            long fromLong = from.longValue();
            if (!fromInclusive && fromLong < Long.MAX_VALUE) fromLong++;
            long toLong = to.longValue();
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
        return anyQuery(longQuery, doubleQuery);
    }

    private Query queryForRange(int propertyIndex, RangePredicate<?> predicate) {
        Value from = predicate.fromValue();
        boolean fromInclusive = predicate.fromInclusive();
        Value to = predicate.toValue();
        boolean toInclusive = predicate.toInclusive();
        if (from == NO_VALUE && to == NO_VALUE) {
            return new MatchNoDocsQuery();
        } else if (from == NO_VALUE) {
            // range query in (-Infinity, to) or (-Infinity, to]
            return rangeWithOpenStart(to, toInclusive, propertyIndex);
        } else if (to == NO_VALUE) {
            // range query in (from, Infinity) or [from, Infinity)
            return rangeWithOpenEnd(from, fromInclusive, propertyIndex);
        } else {
            if (from instanceof BooleanValue bFrom && to instanceof BooleanValue bTo) {
                return booleanRangeQuery(bFrom, fromInclusive, bTo, toInclusive, propertyIndex);
            } else if (from instanceof NumberValue nFrom && to instanceof NumberValue nTo) {
                return queryForNumericRangeFrom(propertyIndex, nFrom, fromInclusive, nTo, toInclusive);
            } else if (from instanceof TemporalValue<?, ?> tvFrom && to instanceof TemporalValue<?, ?> tvTo) {
                Instant fromInstant =
                        Lucene10ValueFields.instantFromTemporal(tvFrom).plusNanos(fromInclusive ? 0 : 1);
                Instant toInstant =
                        Lucene10ValueFields.instantFromTemporal(tvTo).minusNanos(toInclusive ? 0 : 1);
                if (fromInstant.isAfter(toInstant)) {
                    return new MatchNoDocsQuery();
                }
                return Lucene10ValueFields.SingleInstantField.newRangeQuery(
                        vectorDocumentStructure.temporalValueKeyFor(propertyIndex, tvFrom.valueGroup()),
                        fromInstant,
                        toInstant);
            } else if (from instanceof TextValue sFrom && to instanceof TextValue sTo) {
                return TermRangeQuery.newStringRange(
                        vectorDocumentStructure.textValueKeyFor(propertyIndex),
                        sFrom.stringValue(),
                        sTo.stringValue(),
                        fromInclusive,
                        toInclusive);
            } else if (from == null || to == null) {
                return null;
            }
            throw new IllegalArgumentException(
                    String.format("Unexpected value type in filter predicate '%s'", predicate));
        }
    }

    private Query booleanRangeQuery(
            BooleanValue from, boolean fromInclusive, BooleanValue to, boolean toInclusive, int propertyIndex) {
        // empty range, x > true or x < false
        if ((from.booleanValue() && !fromInclusive) || (!to.booleanValue() && !toInclusive)) {
            return new MatchNoDocsQuery();
        } else {
            return Lucene10ValueFields.BooleanField.newRangeQuery(
                    vectorDocumentStructure.booleanValueKeyFor(propertyIndex),
                    from.booleanValue() == fromInclusive,
                    to.booleanValue() && toInclusive);
        }
    }

    private Query rangeWithOpenStart(Value to, boolean toInclusive, int propertyIndex) {
        return switch (to) {
            case BooleanValue bTo -> {
                // empty range, x < false
                if (!bTo.booleanValue() && !toInclusive) {
                    yield new MatchNoDocsQuery();
                } else {
                    yield Lucene10ValueFields.BooleanField.newRangeQuery(
                            vectorDocumentStructure.booleanValueKeyFor(propertyIndex),
                            false,
                            bTo.booleanValue() && toInclusive);
                }
            }
            case NumberValue nTo ->
                queryForNumericRangeFrom(propertyIndex, Values.NegInfinity, false, nTo, toInclusive);
            case StringValue sTo ->
                TermRangeQuery.newStringRange(
                        vectorDocumentStructure.textValueKeyFor(propertyIndex),
                        null,
                        sTo.stringValue(),
                        false,
                        toInclusive);
            case TemporalValue<?, ?> tTo ->
                Lucene10ValueFields.SingleInstantField.newRangeQuery(
                        vectorDocumentStructure.temporalValueKeyFor(propertyIndex, tTo.valueGroup()),
                        Instant.MIN,
                        Lucene10ValueFields.instantFromTemporal(tTo).minusNanos(toInclusive ? 0 : 1));
            case null -> null;
            default ->
                throw new IllegalArgumentException(String.format("Unexpected value type in filter predicate '%s'", to));
        };
    }

    private Query rangeWithOpenEnd(Value from, boolean fromInclusive, int propertyIndex) {
        return switch (from) {
            case BooleanValue bFrom -> {
                // empty range, x > true
                if (bFrom.booleanValue() && !fromInclusive) {
                    yield new MatchNoDocsQuery();
                } else {
                    yield Lucene10ValueFields.BooleanField.newRangeQuery(
                            vectorDocumentStructure.booleanValueKeyFor(propertyIndex),
                            bFrom.booleanValue() == fromInclusive,
                            true);
                }
            }
            case NumberValue nFrom ->
                queryForNumericRangeFrom(propertyIndex, nFrom, fromInclusive, Values.Infinity, false);
            case StringValue sFrom ->
                TermRangeQuery.newStringRange(
                        vectorDocumentStructure.textValueKeyFor(propertyIndex),
                        sFrom.stringValue(),
                        null,
                        fromInclusive,
                        false);
            case TemporalValue<?, ?> tFrom ->
                Lucene10ValueFields.SingleInstantField.newRangeQuery(
                        vectorDocumentStructure.temporalValueKeyFor(propertyIndex, tFrom.valueGroup()),
                        Lucene10ValueFields.instantFromTemporal(tFrom).plusNanos(fromInclusive ? 0 : 1),
                        Instant.MAX);
            case null -> null;
            default ->
                throw new IllegalArgumentException(
                        String.format("Unexpected value type in filter predicate '%s'", from));
        };
    }

    private static BooleanQuery anyQuery(Query... queries) {
        var builder = new BooleanQuery.Builder().setMinimumNumberShouldMatch(1);
        for (Query query : queries) {
            builder.add(new ConstantScoreQuery(query), Occur.SHOULD);
        }
        return builder.build();
    }

    /**
     *
     * @param documentStructure how to map field indexes to Lucene document key values
     * @param filterQueryFrom first query which is a filter query (e.g. 1 when preceded by a vector query)
     * @param filterQueries list of queries
     * @return A Lucene 10 query restricting document search to documents matching all the `filterQueries`
     */
    static Query build(
            VectorDocumentStructure documentStructure, int filterQueryFrom, PropertyIndexQuery... filterQueries) {
        BooleanQuery.Builder booleanQueryBuilder = null;
        var lucene10FilterQueryBuilder = new Lucene10FilterQueryBuilder(documentStructure);
        for (int i = filterQueryFrom; i < filterQueries.length; i++) {
            var filterQuery = filterQueries[i];
            if (filterQuery.type() != IndexQuery.IndexQueryType.ALL) {
                if (booleanQueryBuilder == null) {
                    booleanQueryBuilder = new BooleanQuery.Builder();
                }
                booleanQueryBuilder.add(lucene10FilterQueryBuilder.queryForPredicate(i, filterQuery), Occur.FILTER);
            }
        }
        if (booleanQueryBuilder == null) {
            return new MatchAllDocsQuery();
        }
        return booleanQueryBuilder.build();
    }
}
