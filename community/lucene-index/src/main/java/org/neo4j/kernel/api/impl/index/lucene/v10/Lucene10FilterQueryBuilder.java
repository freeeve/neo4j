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

import static org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory.EXISTS_KEY;
import static org.neo4j.values.storable.Values.NO_VALUE;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.KeywordField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.util.BytesRef;
import org.neo4j.internal.kernel.api.PropertyIndexQuery;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.ExactPredicate;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.ExistsPredicate;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.IncomparableExactPredicate;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.IncomparableRangePredicate;
import org.neo4j.internal.kernel.api.PropertyIndexQuery.RangePredicate;
import org.neo4j.internal.schema.IndexQuery.IndexQueryType;
import org.neo4j.kernel.api.impl.index.lucene.v10.Lucene10ValueFields.SingleInstantField;
import org.neo4j.kernel.api.impl.index.lucene.v10.Lucene10ValueFields.SingleIntegerField;
import org.neo4j.kernel.api.impl.index.lucene.v10.Lucene10ValueFields.TemporalOffsetWithId;
import org.neo4j.kernel.api.impl.index.lucene.v10.Lucene10ValueFields.TemporalWithZone;
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
import org.neo4j.values.storable.ValueGroup;
import org.neo4j.values.storable.Values;

final class Lucene10FilterQueryBuilder {

    private final VectorDocumentStructure vectorDocumentStructure;

    private Lucene10FilterQueryBuilder(VectorDocumentStructure documentStructure) {
        this.vectorDocumentStructure = documentStructure;
    }

    private Query queryForPredicate(int propertyIndex, PropertyIndexQuery predicate) {
        return switch (predicate) {
            case ExistsPredicate ignored when propertyIndex == 0 -> new MatchAllDocsQuery();
            case ExistsPredicate ignored -> queryForExists(propertyIndex);
            case IncomparableExactPredicate ignored -> new MatchNoDocsQuery();
            case ExactPredicate exactPredicate -> queryForExact(propertyIndex, exactPredicate);
            case IncomparableRangePredicate<?> ignored -> new MatchNoDocsQuery();
            case RangePredicate<?> rangePredicate -> queryForRange(propertyIndex, rangePredicate);
            case null, default ->
                throw new IllegalArgumentException("Unexpected filter query predicate. Expected one of ["
                        + IndexQueryType.EXISTS
                        + ", "
                        + IndexQueryType.EXACT
                        + ", "
                        + IndexQueryType.RANGE
                        + "]. Provided: "
                        + predicate);
        };
    }

    private Query queryForExists(int propertyIndex) {
        return new TermQuery(new Term(EXISTS_KEY, new BytesRef(Lucene10ValueFields.intToBytes(propertyIndex))));
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
                TemporalWithZone<?, ?> twz = Lucene10ValueFields.storedFromTemporal(tv);
                Query instantQuery = SingleInstantField.newExactQuery(
                        vectorDocumentStructure.temporalValueKeyFor(propertyIndex, tv.valueGroup()), twz.instant());
                if (!twz.hasZoneOffset()) {
                    yield instantQuery;
                }

                Query zoneOffsetQuery = SingleIntegerField.newExactQuery(
                        vectorDocumentStructure.zoneOffsetValueKeyFor(propertyIndex, tv.valueGroup()),
                        twz.zoneOffset().getTotalSeconds());
                if (!twz.hasZoneId()) {
                    yield everyQuery(instantQuery, zoneOffsetQuery);
                }

                Query zoneIdQuery = KeywordField.newExactQuery(
                        vectorDocumentStructure.zoneIdValueKeyFor(propertyIndex, tv.valueGroup()),
                        TemporalWithZone.zoneIdString(twz.zoneId()));
                yield everyQuery(instantQuery, zoneOffsetQuery, zoneIdQuery);
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
            // if both from and to are integral values, the bounds should be stepped as integers,
            // and we should do the integral checking first
            long fromLong = from.longValue();
            if (!fromInclusive && fromLong < Long.MAX_VALUE) {
                fromLong++;
            }
            long toLong = to.longValue();
            if (!toInclusive && toLong > Long.MIN_VALUE) {
                toLong--;
            }
            longQuery = Lucene10ValueFields.SingleLongField.newRangeQuery(
                    vectorDocumentStructure.integralValueKeyFor(propertyIndex), fromLong, toLong);
        }
        // Now build a floating point query
        if (!fromInclusive) {
            fromDouble = Math.nextUp(fromDouble);
        }
        if (!toInclusive) {
            toDouble = Math.nextDown(toDouble);
        }
        Query doubleQuery = Lucene10ValueFields.SingleDoubleField.newRangeQuery(
                vectorDocumentStructure.floatingValueKeyFor(propertyIndex), fromDouble, toDouble);
        if (!rangeIsIntegral) {
            // build an integral query with floating point values
            long fromIntegral = Double.valueOf(Math.ceil(fromDouble)).longValue();
            long toIntegral = Double.valueOf(Math.floor(toDouble)).longValue();
            // These values may now be out of range. For example, 42.3 - 42.6 becomes 43 - 42
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
                return temporalRangeQuery(
                        tvFrom.valueGroup(),
                        Lucene10ValueFields.storedFromTemporal(tvFrom),
                        fromInclusive,
                        Lucene10ValueFields.storedFromTemporal(tvTo),
                        toInclusive,
                        propertyIndex);
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

    private List<Query> temporalRangeQueryUpperSubRange(
            TemporalWithZone<?, ?> twz, boolean inclusive, ValueGroup valueGroup, int propertyIndex) {
        return temporalRangeQueryWithinSingleInstant(
                twz.instant(), null, true, twz.temporalOffsetWithId(), inclusive, valueGroup, propertyIndex);
    }

    private List<Query> temporalRangeQueryLowerSubRange(
            TemporalWithZone<?, ?> twz, boolean inclusive, ValueGroup valueGroup, int propertyIndex) {
        return temporalRangeQueryWithinSingleInstant(
                twz.instant(), twz.temporalOffsetWithId(), inclusive, null, true, valueGroup, propertyIndex);
    }

    private List<Query> temporalRangeQueryWithinSingleInstant(
            Instant instant,
            TemporalOffsetWithId from,
            boolean fromInclusive,
            TemporalOffsetWithId to,
            boolean toInclusive,
            ValueGroup valueGroup,
            int propertyIndex) {

        final int fromOffsetSeconds =
                TemporalOffsetWithId.zoneOffsetOf(from, ZoneOffset.MIN).getTotalSeconds();
        final int toOffsetSeconds =
                TemporalOffsetWithId.zoneOffsetOf(to, ZoneOffset.MAX).getTotalSeconds();
        if (toOffsetSeconds < fromOffsetSeconds) {
            return List.of(new MatchNoDocsQuery());
        }

        String fromZoneId = TemporalOffsetWithId.hasZoneId(from) ? TemporalWithZone.zoneIdString(from.zoneId()) : null;
        String toZoneId = TemporalOffsetWithId.hasZoneId(to) ? TemporalWithZone.zoneIdString(to.zoneId()) : null;

        if (toOffsetSeconds == fromOffsetSeconds && (fromZoneId != null || toZoneId != null)) {
            // special case a zone id range within a single offset
            return List.of(temporalRangeQueryWithinSingleOffset(
                    instant,
                    fromOffsetSeconds,
                    fromZoneId,
                    fromInclusive,
                    toZoneId,
                    toInclusive,
                    valueGroup,
                    propertyIndex));
            // if there are no zone ids, fall through to the bottom range query which handles that case
        }

        // Build a list of the necessary queries
        List<Query> queries = new ArrayList<>();

        final int fromOffsetRange;
        if (TemporalOffsetWithId.hasZoneId(from)) {
            queries.add(temporalRangeQueryWithinSingleOffset(
                    instant, fromOffsetSeconds, fromZoneId, fromInclusive, null, false, valueGroup, propertyIndex));
            fromOffsetRange = fromOffsetSeconds + 1;
        } else if (TemporalOffsetWithId.hasZoneOffset(from)) {
            // adjust bound when query's least significant element is offset
            fromOffsetRange = fromOffsetSeconds + (fromInclusive ? 0 : 1);
        } else {
            fromOffsetRange = fromOffsetSeconds;
        }

        final int toOffsetRange;
        if (TemporalOffsetWithId.hasZoneId(to)) {
            queries.add(temporalRangeQueryWithinSingleOffset(
                    instant, toOffsetSeconds, null, false, toZoneId, toInclusive, valueGroup, propertyIndex));
            toOffsetRange = toOffsetSeconds - 1;
        } else if (TemporalOffsetWithId.hasZoneOffset(to)) {
            // adjust bound when query's least significant element is offset
            toOffsetRange = toOffsetSeconds - (toInclusive ? 0 : 1);
        } else {
            toOffsetRange = toOffsetSeconds;
        }

        if (fromOffsetRange <= toOffsetRange) {
            queries.add(everyQuery(
                    SingleInstantField.newExactQuery(
                            vectorDocumentStructure.temporalValueKeyFor(propertyIndex, valueGroup), instant),
                    SingleIntegerField.newRangeQuery(
                            vectorDocumentStructure.zoneOffsetValueKeyFor(propertyIndex, valueGroup),
                            fromOffsetRange,
                            toOffsetRange)));
        }
        return queries;
    }

    private Query temporalRangeQueryWithinSingleOffset(
            Instant instant,
            int offsetSeconds,
            String fromZoneId,
            boolean fromInclusive,
            String toZoneId,
            boolean toInclusive,
            ValueGroup valueGroup,
            int propertyIndex) {
        return everyQuery(
                SingleInstantField.newExactQuery(
                        vectorDocumentStructure.temporalValueKeyFor(propertyIndex, valueGroup), instant),
                SingleIntegerField.newExactQuery(
                        vectorDocumentStructure.zoneOffsetValueKeyFor(propertyIndex, valueGroup), offsetSeconds),
                TermRangeQuery.newStringRange(
                        vectorDocumentStructure.zoneIdValueKeyFor(propertyIndex, valueGroup),
                        fromZoneId,
                        toZoneId,
                        fromInclusive,
                        toInclusive));
        // if there are no zone ids, fall through to the bottom range query which handles the case
    }

    /// Construct a query for the temporal range defined by the
    /// (`twzFrom`,`fromInclusive`) and (`twzTo`,`toInclusive`) parameters.
    /// Either `twzFrom` or `twzTo` may be `null`, in which case the range is not bounded
    /// in the relevant direction.
    ///
    /// These range queries are required to be consistent with [org.neo4j.values.storable.Values.COMPARATOR]
    /// for subclasses of [org.neo4j.values.storable.TemporalValue]
    ///
    /// A `TemporalWithZone` may have an offset component, and if it has an offset, it may also have a zone.
    /// In particular, [org.neo4j.values.storable.DateTimeValue] and [org.neo4j.values.storable.TimeValue]
    /// carry offsets (and [org.neo4j.values.storable.DateTimeValue] carries a [java.time.ZoneId]);
    ///
    /// The resulting query may be a single query, or an `anyQuery` of multiple queries.
    /// If no `ZoneOffset`s exist within the offset, the result will be a single range query on `instants`.
    /// If one or more `offset`s are supplied, the result will be `anyQuery` formed of a range query of `Instant`s,
    /// and further queries within the end instant formed of a range of `ZoneOffset`.
    /// A third query may also be necessary, formed of a range of `ZoneId` within the endmost `ZoneOffset`.
    /// Consider `TemporalWithZone`s of (Instant,ZoneOffset,ZoneId)
    /// Consider the range (5500,-14400,\[America/Goose_Bay\]),inclusive - (6200,+18000,\[Indian,Maldives\]),exclusive
    /// The resulting set of queries (disjunction) generated here is:
    /// (instant=5500 AND offset=-14400 AND \[America/Goose_Bay\] <= zoneid) - <= because inclusive
    /// OR (instant=5500 AND -14400 < offset)
    /// OR (5501 <= instant <= 6199)
    /// OR (instant=6200 AND offset < +18000)
    /// OR (instant=6200 AND offset = +18000 AND zoneId < \[Indian,Maldives\]) - < because exclusive
    ///
    /// Consider `TemporalWithZone`s of (Instant,ZoneOffset)
    /// /// Consider the range (7000,-3600),inclusive - (9000,+28800),exclusive
    /// The resulting set of queries (disjunction) generated here is:
    /// (instant=7000 AND -3600 <= offset)
    /// OR (7001 <= instant <= 8999)
    /// OR (instant=9000 AND offset < +28800)
    ///
    /// @param valueGroup the temporal value group of the values we are querying
    /// @param twzFrom start of range `null` means the lower range is open
    /// @param fromInclusive does the lower range include values with key equal to {@code twzFrom} ?
    /// @param twzTo end of range `null` means the upper range is open
    /// @param toInclusive does the upper range include values with key equal to {@code twzTo} ?
    /// @param propertyIndex of the field within the index being queried
    /// @return the constructed Lucene query
    ///
    private Query temporalRangeQuery(
            ValueGroup valueGroup,
            TemporalWithZone<?, ?> twzFrom,
            boolean fromInclusive,
            TemporalWithZone<?, ?> twzTo,
            boolean toInclusive,
            int propertyIndex) {

        List<Query> queries = new ArrayList<>();

        Instant minInstant = twzFrom == null ? Instant.MIN : twzFrom.instant();
        Instant maxInstant = twzTo == null ? Instant.MAX : twzTo.instant();

        if (twzFrom != null && twzTo != null) {
            if (TemporalWithZone.compare(twzFrom, twzTo) > 0) {
                return new MatchNoDocsQuery();
            }

            if (maxInstant.compareTo(minInstant) == 0) {
                if (twzFrom.hasZoneOffset()) {
                    return anyQuery(temporalRangeQueryWithinSingleInstant(
                            twzFrom.instant(),
                            twzFrom.temporalOffsetWithId(),
                            fromInclusive,
                            twzTo.temporalOffsetWithId(),
                            toInclusive,
                            valueGroup,
                            propertyIndex));
                } else if (fromInclusive && toInclusive) {
                    return SingleInstantField.newExactQuery(
                            vectorDocumentStructure.temporalValueKeyFor(propertyIndex, valueGroup), minInstant);
                } else {
                    return new MatchNoDocsQuery();
                }
            }
        }

        if (twzFrom != null) {
            if (twzFrom.hasZoneOffset()) {
                queries.addAll(temporalRangeQueryLowerSubRange(twzFrom, fromInclusive, valueGroup, propertyIndex));
                // range query for instants starts at the next instant (see the above method documentation)
                minInstant = minInstant.plusNanos(1);
            } else if (minInstant.isAfter(Instant.MIN)) {
                // adjust bound when query's least significant element is instant
                minInstant = minInstant.plusNanos(fromInclusive ? 0 : 1);
            }
        }

        if (twzTo != null) {
            if (twzTo.hasZoneOffset()) {
                queries.addAll(temporalRangeQueryUpperSubRange(twzTo, toInclusive, valueGroup, propertyIndex));
                // range query for instants starts at the next instant (see the above method documentation)
                maxInstant = maxInstant.minusNanos(1);
            } else if (maxInstant.isBefore(Instant.MAX)) {
                // adjust bound when query's least significant element is instant
                maxInstant = maxInstant.minusNanos(toInclusive ? 0 : 1);
            }
        }

        // Range query for whole instants between offset/id ends
        if (maxInstant.isAfter(minInstant)) {
            queries.add(SingleInstantField.newRangeQuery(
                    vectorDocumentStructure.temporalValueKeyFor(propertyIndex, valueGroup), minInstant, maxInstant));
        }

        return anyQuery(queries);
    }

    private Query booleanRangeQuery(
            BooleanValue from, boolean fromInclusive, BooleanValue to, boolean toInclusive, int propertyIndex) {
        // empty range, x > true or x < false
        if ((from.booleanValue() && !fromInclusive) || (!to.booleanValue() && !toInclusive)) {
            return new MatchNoDocsQuery();
        }

        return Lucene10ValueFields.BooleanField.newRangeQuery(
                vectorDocumentStructure.booleanValueKeyFor(propertyIndex),
                from.booleanValue() == fromInclusive,
                to.booleanValue() && toInclusive);
    }

    private Query rangeWithOpenStart(Value to, boolean toInclusive, int propertyIndex) {
        return switch (to) {
            // empty boolean range, x < false
            case BooleanValue bTo when !bTo.booleanValue() && !toInclusive -> new MatchNoDocsQuery();
            case BooleanValue bTo ->
                Lucene10ValueFields.BooleanField.newRangeQuery(
                        vectorDocumentStructure.booleanValueKeyFor(propertyIndex),
                        false,
                        bTo.booleanValue() && toInclusive);
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
                temporalRangeQuery(
                        tTo.valueGroup(),
                        null,
                        true,
                        Lucene10ValueFields.storedFromTemporal(tTo),
                        toInclusive,
                        propertyIndex);
            case null -> null;
            default ->
                throw new IllegalArgumentException(String.format("Unexpected value type in filter predicate '%s'", to));
        };
    }

    private Query rangeWithOpenEnd(Value from, boolean fromInclusive, int propertyIndex) {
        return switch (from) {
            // empty boolean range, x > true
            case BooleanValue bFrom when bFrom.booleanValue() && !fromInclusive -> new MatchNoDocsQuery();
            case BooleanValue bFrom ->
                Lucene10ValueFields.BooleanField.newRangeQuery(
                        vectorDocumentStructure.booleanValueKeyFor(propertyIndex),
                        bFrom.booleanValue() == fromInclusive,
                        true);
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
                temporalRangeQuery(
                        tFrom.valueGroup(),
                        Lucene10ValueFields.storedFromTemporal(tFrom),
                        fromInclusive,
                        null,
                        true,
                        propertyIndex);
            case null -> null;
            default ->
                throw new IllegalArgumentException(
                        String.format("Unexpected value type in filter predicate '%s'", from));
        };
    }

    private static BooleanQuery anyQuery(Query... queries) {
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder().setMinimumNumberShouldMatch(1);
        for (Query query : queries) {
            queryBuilder.add(new ConstantScoreQuery(query), Occur.SHOULD);
        }
        return queryBuilder.build();
    }

    private static BooleanQuery anyQuery(List<Query> queries) {
        var builder = new BooleanQuery.Builder().setMinimumNumberShouldMatch(1);
        for (Query query : queries) {
            builder.add(new ConstantScoreQuery(query), Occur.SHOULD);
        }
        return builder.build();
    }

    private static BooleanQuery everyQuery(Query... queries) {
        var builder = new BooleanQuery.Builder();
        for (Query query : queries) {
            builder.add(new ConstantScoreQuery(query), Occur.FILTER);
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
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        Lucene10FilterQueryBuilder filterQueryFactory = new Lucene10FilterQueryBuilder(documentStructure);
        for (int i = filterQueryFrom; i < filterQueries.length; i++) {
            PropertyIndexQuery filterQuery = filterQueries[i];
            if (filterQuery.type() != IndexQueryType.ALL) {
                queryBuilder.add(filterQueryFactory.queryForPredicate(i, filterQuery), Occur.FILTER);
            }
        }

        BooleanQuery query = queryBuilder.build();
        return !query.clauses().isEmpty() ? query : new MatchAllDocsQuery();
    }
}
