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
package org.neo4j.kernel.impl.index.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.kernel.impl.index.vector.VectorSSFQueryResult.field;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.assertj.core.api.AbstractBooleanAssert;
import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.security.PropertyRule.ComparisonOperator;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomSupportExtension;
import org.neo4j.values.storable.DateTimeValue;
import org.neo4j.values.storable.TimeValue;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

@RandomSupportExtension
public class VectorSSFTemporalCompatibilityTest extends VectorSSFTestBase {

    @Inject
    protected RandomSupport random;

    @Test
    public void checkCypherDateTimesOrder() {
        var allDateTimes = sortByDateTimeZoneOffsetAndId(generateTestZonedDateTimeStrings());

        long previousEpoch = Long.MIN_VALUE;
        String previousDate = null;
        DateTimeValue previousDateTime = DateTimeValue.MIN_VALUE;
        for (var date : allDateTimes) {
            var dateTime = DateTimeValue.parse(date, ZoneId::systemDefault);
            long epoch = dateTime.asObjectCopy().toEpochSecond();
            assertThat(previousEpoch)
                    .as(
                            "%s with epoch %d is not >= previous %d with epoch %d\n",
                            date, epoch, previousDate, previousEpoch)
                    .isLessThanOrEqualTo(epoch);
            assertThat(Values.COMPARATOR.compare(previousDateTime, dateTime))
                    .as("Expected %s(%s) strictly < %s(%s)", previousDate, previousDateTime, date, dateTime)
                    .isNegative();
            if (previousDate != null) {
                assertThatCypherDateTimes(previousDate, ComparisonOperator.LESS_THAN, date)
                        .isTrue();
            }
            previousEpoch = epoch;
            previousDate = date;
            previousDateTime = dateTime;
        }
    }

    @Test
    public void checkCypherTimesOrder() {
        var allDateTimes = sortByTimeZoneOffsetAndId(generateTestZonedTimeStrings());

        String previousDate = null;
        TimeValue previousTime = TimeValue.MIN_VALUE;
        for (var date : allDateTimes) {
            var time = TimeValue.parse(date, ZoneId::systemDefault);
            assertThat(Values.COMPARATOR.compare(previousTime, time))
                    .as("Expected %s(%s) strictly < %s(%s)", previousDate, previousTime, date, time)
                    .isNegative();
            if (previousDate != null) {
                assertThatCypherTimes(previousDate, ComparisonOperator.LESS_THAN, date)
                        .isTrue();
            }
            previousDate = date;
            previousTime = time;
        }
    }

    @Test
    public void checkSSFExactParameterDateTimesOrderRespectsCypher() throws Exception {
        var allDateTimes = generateTestZonedDateTimeStrings();
        assertThatSSFExactParameterOrderRespectsCypher(
                        "birthdate", allDateTimes, datetime -> DateTimeValue.parse(datetime, ZoneId::systemDefault))
                .isTrue();
    }

    @Test
    public void checkSSFExactParameterTimesOrderRespectsCypher() throws Exception {

        var allTimes = generateTestZonedTimeStrings();
        assertThatSSFExactParameterOrderRespectsCypher(
                        "alarmtime", allTimes, time -> TimeValue.parse(time, ZoneId::systemDefault))
                .isTrue();
    }

    @Test
    public void checkSSFRangeParameterDateTimesOrderRespectsCypher() throws Exception {

        var allDateTimes = sortByDateTimeZoneOffsetAndId(generateTestZonedDateTimeStrings());
        assertThatSSFRangeParameterOrderRespectsCypher(
                        "birthdatetime", allDateTimes, dateTime -> DateTimeValue.parse(dateTime, ZoneId::systemDefault))
                .isTrue();
    }

    /// random seed used: 1764585918538L
    /// checkSSFRangeParameterDateTimesOrderRespectsCypher
    @Test
    public void checkTemporalWithZoneFix() throws Exception {
        var allDateTimes = List.of(
                "2019-06-02T21:00:00.000[Atlantic/Cape_Verde]",
                "2019-06-02T21:00:00.000[Etc/GMT+1]",
                "2019-06-02T22:00:00.000+0000",
                "2019-06-02T22:00:00.000[Africa/Abidjan]",
                "2019-06-02T22:00:00.000[Africa/Accra]",
                "2019-06-02T22:00:00.000[Africa/Bamako]");

        var names = randomNames(allDateTimes.size());
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "name", "birthdatetime");
        // Don't USE embedding 0, as we always query for that...
        var embeddings = EMBEDDINGS.count() - 1;
        try (final Transaction tx = db.beginTx()) {
            for (int i = 0; i < allDateTimes.size(); i++) {
                var datetime = DateTimeValue.parse(allDateTimes.get(i), ZoneId::systemDefault);
                createTestNode(
                        tx,
                        Map.of(
                                "id",
                                i,
                                "name",
                                names.get(i),
                                "birthdatetime",
                                datetime,
                                EMBEDDING_NAME,
                                EMBEDDINGS.get((i % embeddings) + 1)));
            }
            tx.commit();
        }
        var fromTime = DateTimeValue.parse(allDateTimes.get(2), ZoneId::systemDefault);
        var toTime = DateTimeValue.parse(allDateTimes.get(4), ZoneId::systemDefault);
        var midTime = DateTimeValue.parse(allDateTimes.get(3), ZoneId::systemDefault);
        assertThat(Values.COMPARATOR.compare(fromTime, toTime)).isNegative();
        assertThat(Values.COMPARATOR.compare(fromTime, midTime)).isNegative();
        assertThat(Values.COMPARATOR.compare(midTime, toTime)).isNegative();
        assertThatCypherDateTimes(allDateTimes.get(2), ComparisonOperator.LESS_THAN, allDateTimes.get(4))
                .isTrue();
        assertThatCypherDateTimes(allDateTimes.get(2), ComparisonOperator.LESS_THAN, allDateTimes.get(3))
                .isTrue();
        assertThatCypherDateTimes(allDateTimes.get(3), ComparisonOperator.LESS_THAN, allDateTimes.get(4))
                .isTrue();
        assertThatCypherDateTimes(allDateTimes.get(2), ComparisonOperator.GREATER_THAN, allDateTimes.get(4))
                .isFalse();
        assertThatCypherDateTimes(allDateTimes.get(2), ComparisonOperator.GREATER_THAN, allDateTimes.get(3))
                .isFalse();
        assertThatCypherDateTimes(allDateTimes.get(3), ComparisonOperator.GREATER_THAN, allDateTimes.get(4))
                .isFalse();

        var RESULT_SIZE = 50;
        var results = queryNodeIndex(
                RESULT_SIZE, allQuery("name"), rangeQuery("birthdatetime", fromTime, true, toTime, true));
        results = new ArrayList<>(results);
        results.sort(Comparator.comparing(v -> ((Integer) v.getValue("id").asObjectCopy())));
        assertThat(results).hasSize(3);
    }

    @Test
    public void checkSSFOpenLowerRangeParameterDateTimesOrderRespectsCypher() throws Exception {

        var allDateTimes = sortByDateTimeZoneOffsetAndId(generateTestZonedDateTimeStrings());
        assertThatSSFOpenLowerRangeParameterOrderRespectsCypher(
                        "birthdatetime", allDateTimes, datetime -> DateTimeValue.parse(datetime, ZoneId::systemDefault))
                .isTrue();
    }

    @Test
    public void checkSSFRangeParameterTimesOrderRespectsCypher() throws Exception {

        var allTimes = sortByTimeZoneOffsetAndId(generateTestZonedTimeStrings());
        assertThatSSFRangeParameterOrderRespectsCypher(
                        "alarmtime", allTimes, time -> TimeValue.parse(time, ZoneId::systemDefault))
                .isTrue();
    }

    @Test
    public void checkSSFOpenLowerRangeParameterTimesOrderRespectsCypher() throws Exception {
        var allTimes = sortByTimeZoneOffsetAndId(generateTestZonedTimeStrings());
        assertThatSSFOpenLowerRangeParameterOrderRespectsCypher(
                        "alarmtime", allTimes, time -> TimeValue.parse(time, ZoneId::systemDefault))
                .isTrue();
    }

    @Test
    public void checkSSFOpenUpperRangeParameterTimesOrderRespectsCypher() throws Exception {

        var allTimes = sortByTimeZoneOffsetAndId(generateTestZonedTimeStrings());
        assertThatSSFOpenUpperRangeParameterOrderRespectsCypher(
                        "alarmtime", allTimes, time -> TimeValue.parse(time, ZoneId::systemDefault))
                .isTrue();
    }

    @Test
    public void checkSSFOpenUpperRangeParameterDateTimesOrderRespectsCypher() throws Exception {

        var allDateTimes = sortByDateTimeZoneOffsetAndId(generateTestZonedDateTimeStrings());
        assertThatSSFOpenUpperRangeParameterOrderRespectsCypher(
                        "birthdatetime", allDateTimes, datetime -> DateTimeValue.parse(datetime, ZoneId::systemDefault))
                .isTrue();
    }

    private AbstractBooleanAssert<?> assertThatCypherDateTimes(String lhs, ComparisonOperator op, String rhs) {
        final String query = "RETURN " + op.toPredicateString("datetime($lhs)", "datetime($rhs)") + " AS comp;";
        return assertThat((Boolean) db.executeTransactionally(
                query, Map.of("lhs", lhs, "rhs", rhs), r -> r.columnAs("comp").next()));
    }

    private AbstractBooleanAssert<?> assertThatCypherTimes(String lhs, ComparisonOperator op, String rhs) {
        final String query = "RETURN " + op.toPredicateString("time($lhs)", "time($rhs)") + " AS comp;";
        return assertThat((Boolean) db.executeTransactionally(
                query, Map.of("lhs", lhs, "rhs", rhs), r -> r.columnAs("comp").next()));
    }

    private <T extends Value> AbstractBooleanAssert<?> assertThatSSFExactParameterOrderRespectsCypher(
            String fieldName, List<String> inputs, Function<String, T> mapper) throws Exception {
        var names = randomNames(inputs.size());
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "name", fieldName);
        createNodesForExactAndRangeParameterChecks(fieldName, inputs, mapper, names);

        for (int i = 0; i < inputs.size(); i++) {
            var time = mapper.apply(inputs.get(i));
            assertThat(queryNodeIndex(allQuery("name"), exactQuery(fieldName, time)))
                    .as("%d name %s %s %s", i, names.get(i), fieldName, time)
                    .hasSize(1)
                    .have(field("name", names.get(i)));
        }
        return assertThat(true);
    }

    private <T extends Value> void createNodesForExactAndRangeParameterChecks(
            String fieldName, List<String> inputs, Function<String, T> mapper, List<String> names) {

        var embeddings = EMBEDDINGS.count() - 1;
        try (final Transaction tx = db.beginTx()) {
            for (int i = 0; i < inputs.size(); i++) {
                var timeValue = mapper.apply(inputs.get(i));
                createTestNode(
                        tx,
                        Map.of(
                                "id",
                                i,
                                "name",
                                names.get(i),
                                fieldName,
                                timeValue,
                                EMBEDDING_NAME,
                                EMBEDDINGS.get((i % embeddings) + 1)));
            }
            tx.commit();
        }
    }

    private <T extends Value> AbstractBooleanAssert<?> assertThatSSFRangeParameterOrderRespectsCypher(
            String fieldName, List<String> inputs, Function<String, T> mapper) throws Exception {

        var names = randomNames(inputs.size());
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "name", fieldName);
        createNodesForExactAndRangeParameterChecks(fieldName, inputs, mapper, names);

        int repeat = 100;
        int resultSize = 50;
        for (int i = 0; i < repeat; i++) {
            int lower = random.nextInt(inputs.size());
            int delta = random.nextInt(Math.min(inputs.size() - lower, resultSize - 1));
            int upper = lower + delta;
            var fromTime = mapper.apply(inputs.get(lower));
            var toTime = mapper.apply(inputs.get(upper));
            var results =
                    queryNodeIndex(resultSize, allQuery("name"), rangeQuery(fieldName, fromTime, true, toTime, true));
            results = new ArrayList<>(results);
            results.sort(Comparator.comparing(v -> ((Integer) v.getValue("id").asObjectCopy())));
            assertThat(results)
                    .as("%d: From %d(%s) to %d(%s)", i, lower, fromTime, upper, toTime)
                    .hasSize(upper - lower + 1);
            int j = lower;
            for (var result : results) {
                assertThat(result).has(field("name", names.get(j)));
                j += 1;
            }
        }
        return assertThat(true);
    }

    private <T extends Value> List<Integer> createNodesForOpenRangeCheck(
            String fieldName, List<String> inputs, Function<String, T> mapper, List<String> names, int numberToCreate) {

        var embeddings = EMBEDDINGS.count() - 1;
        int creationCount = 0;
        List<Integer> timesWithNode = new ArrayList<>();
        double creationProbability = ((double) numberToCreate) / inputs.size();
        try (final Transaction tx = db.beginTx()) {
            for (int i = 0; i < inputs.size(); i++) {
                if (random.nextDouble() < creationProbability && creationCount < numberToCreate) {
                    var datetime = mapper.apply(inputs.get(i));
                    createTestNode(
                            tx,
                            Map.of(
                                    "id",
                                    i,
                                    "name",
                                    names.get(i),
                                    fieldName,
                                    datetime,
                                    EMBEDDING_NAME,
                                    EMBEDDINGS.get((i % embeddings) + 1)));
                    timesWithNode.add(i);
                    creationCount++;
                }
            }
            tx.commit();
        }
        return timesWithNode;
    }

    private <T extends Value> AbstractBooleanAssert<?> assertThatSSFOpenLowerRangeParameterOrderRespectsCypher(
            String fieldName, List<String> inputs, Function<String, T> mapper) throws Exception {

        var names = randomNames(inputs.size());
        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "name", fieldName);
        int RESULT_SIZE = 50;
        List<Integer> timesWithNode = createNodesForOpenRangeCheck(fieldName, inputs, mapper, names, RESULT_SIZE);
        // Don't USE embedding 0, as we always query for that...

        int repeat = 100;
        for (int i = 0; i < repeat; i++) {
            int upper = random.nextInt(inputs.size());
            var toTime = mapper.apply(inputs.get(upper));
            var results = queryNodeIndex(
                    RESULT_SIZE, allQuery("name"), rangeQuery(fieldName, Values.NO_VALUE, true, toTime, true));
            results = new ArrayList<>(results);
            results.sort(Comparator.comparing(v -> ((Integer) v.getValue("id").asObjectCopy())));
            int expected = 0;
            for (var nodeIndex : timesWithNode) {
                if (nodeIndex > upper) break;
                expected += 1;
            }
            assertThat(results)
                    .as("%d: From NO_VALUE to %d(%s)", i, upper, toTime)
                    .hasSize(expected);
            int j = 0;
            for (var result : results) {
                var name = names.get(timesWithNode.get(j));
                assertThat(result).has(field("name", name));
                j += 1;
            }
        }
        return assertThat(true);
    }

    private List<String> randomNames(int count) {
        var names = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            names.add(random.nextAlphaNumericString(4, 12));
        }
        return names;
    }

    private <T extends Value> AbstractBooleanAssert<?> assertThatSSFOpenUpperRangeParameterOrderRespectsCypher(
            String fieldName, List<String> inputs, Function<String, T> mapper) throws Exception {

        var names = randomNames(inputs.size());

        createNodeVectorIndex(VECTOR_INDEX_NAME, EMBEDDINGS.dimensions(), EMBEDDING_NAME, "name", fieldName);
        int RESULT_SIZE = 50;
        List<Integer> timesWithNode = createNodesForOpenRangeCheck(fieldName, inputs, mapper, names, RESULT_SIZE);

        int repeat = 100;
        for (int i = 0; i < repeat; i++) {
            int lower = random.nextInt(inputs.size());
            var fromTime = mapper.apply(inputs.get(lower));
            var results = queryNodeIndex(
                    RESULT_SIZE, allQuery("name"), rangeQuery(fieldName, fromTime, true, Values.NO_VALUE, true));
            results = new ArrayList<>(results);
            results.sort(Comparator.comparing(v -> ((Integer) v.getValue("id").asObjectCopy())));
            int firstExpectedIndex = 0;
            for (var nodeIndex : timesWithNode) {
                if (nodeIndex >= lower) break;
                firstExpectedIndex += 1;
            }
            int expected = timesWithNode.size() - firstExpectedIndex;
            assertThat(results)
                    .as("%d: From %d(%s) to NO_VALUE", i, lower, fromTime)
                    .hasSize(expected);
            int j = firstExpectedIndex;
            for (var result : results) {
                var name = names.get(timesWithNode.get(j));
                assertThat(result).has(field("name", name));
                j += 1;
            }
        }
        return assertThat(true);
    }

    /// Build a comprehensive list of DateTimeValues with zone offset and zone id
    /// this can be sorted (via `sortByDateTimeZoneOffsetAndId`)
    /// and used to validate that
    /// 1. Cypher respects the order, as we expect that it already does
    /// 2.
    private List<String> generateTestZonedDateTimeStrings() {
        var dates = List.of("2019-06-01", "2019-06-02", "2019-06-03");
        var hours = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            hours.add(String.format("%02d:00:00.000", i));
        }
        var offsets = List.of(
                        "-1100", "-1000", "-0900", "-0800", "-0700", "-0600", "-0500", "-0400", "-0300", "-0200",
                        "-0100", "+0000", "+0100", "+0200", "+0300", "+0400", "+0500", "+0600", "+0700", "+0800",
                        "+0900", "+1000", "+1100")
                .reversed();
        var allDateTimes = new ArrayList<String>();
        for (var date : dates) {
            for (var hour : hours) {
                for (var zone : ZoneId.getAvailableZoneIds()) {
                    allDateTimes.add(String.format("%sT%s[%s]", date, hour, zone));
                }
                for (var offset : offsets) {
                    allDateTimes.add(String.format("%sT%s%s", date, hour, offset));
                }
            }
        }
        return allDateTimes;
    }

    private List<String> sortByDateTimeZoneOffsetAndId(List<String> dates) {

        dates.sort((o1, o2) -> Values.COMPARATOR.compare(
                DateTimeValue.parse(o1, ZoneId::systemDefault), DateTimeValue.parse(o2, ZoneId::systemDefault)));
        return dates;
    }

    private List<String> generateTestZonedTimeStrings() {
        var hours = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            hours.add(String.format("%02d:00:00.000", i));
        }
        var offsets = List.of(
                        "-1100", "-1000", "-0900", "-0800", "-0700", "-0600", "-0500", "-0400", "-0300", "-0200",
                        "-0100", "+0000", "+0100", "+0200", "+0300", "+0400", "+0500", "+0600", "+0700", "+0800",
                        "+0900", "+1000", "+1100")
                .reversed();
        var allTimes = new ArrayList<String>();
        for (var hour : hours) {
            // Here we do not use names (timezone ids) - they are not valid for raw times
            for (var offset : offsets) {
                allTimes.add(String.format("%s%s", hour, offset));
            }
        }

        return allTimes;
    }

    private List<String> sortByTimeZoneOffsetAndId(List<String> times) {

        times.sort((o1, o2) -> Values.COMPARATOR.compare(
                TimeValue.parse(o1, ZoneId::systemDefault), TimeValue.parse(o2, ZoneId::systemDefault)));
        return times;
    }
}
