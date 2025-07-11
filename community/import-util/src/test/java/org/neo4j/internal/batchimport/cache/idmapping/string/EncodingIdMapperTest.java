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
package org.neo4j.internal.batchimport.cache.idmapping.string;

import static java.lang.Math.toIntExact;
import static java.lang.System.currentTimeMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.neo4j.collection.PrimitiveLongCollections.count;
import static org.neo4j.internal.batchimport.cache.idmapping.string.EncodingIdMapper.NO_MONITOR;
import static org.neo4j.internal.helpers.progress.ProgressMonitorFactory.NONE;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Stream;
import org.apache.commons.lang3.mutable.MutableLong;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.batchimport.api.PropertyValueLookup;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.function.Factory;
import org.neo4j.internal.batchimport.cache.NumberArrayFactories;
import org.neo4j.internal.batchimport.cache.idmapping.IdMapper;
import org.neo4j.internal.batchimport.cache.idmapping.cuckoo.KeyCollisionException;
import org.neo4j.internal.batchimport.input.Groups;
import org.neo4j.internal.helpers.progress.ProgressListener;
import org.neo4j.internal.helpers.progress.ProgressMonitorFactory;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.test.Race;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomExtension;

@ExtendWith(RandomExtension.class)
public class EncodingIdMapperTest {
    private static final PropertyValueLookup CONVERT_TO_STRING = r -> new PropertyValueLookup.Lookup() {
        @Override
        public Object lookupProperty(long nodeId, MemoryTracker memoryTracker) {
            return String.valueOf(nodeId);
        }

        @Override
        public void close() {}
    };
    private static final PropertyValueLookup FAILING_LOOKUP = r -> new PropertyValueLookup.Lookup() {
        @Override
        public Object lookupProperty(long nodeId, MemoryTracker memoryTracker) {
            throw new RuntimeException("Should not be called");
        }

        @Override
        public void close() {}
    };

    @Inject
    private RandomSupport random;

    private final Groups groups = new Groups();
    private final Group globalGroup = groups.getOrCreate(null);

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldHandleGreatAmountsOfStuff(int processors) throws KeyCollisionException {
        // GIVEN
        try (IdMapper idMapper = mapper(new StringEncoder(), Radix.STRING, EncodingIdMapper.NO_MONITOR, processors)) {
            IdMapper.Setter setter = idMapper.newSetter();
            PropertyValueLookup inputIdLookup = CONVERT_TO_STRING;
            int count = 300_000;

            // WHEN
            try (var lookup = inputIdLookup.newLookup(true)) {
                for (long nodeId = 0; nodeId < count; nodeId++) {
                    setter.put(lookup.lookupProperty(nodeId, INSTANCE), nodeId, globalGroup);
                }
            }
            idMapper.prepare(inputIdLookup, mock(Collector.class), NONE, LongSets.immutable.empty());

            // THEN
            try (var getter = idMapper.newGetter();
                    var lookup = inputIdLookup.newLookup(true)) {
                for (long nodeId = 0; nodeId < count; nodeId++) {
                    // the UUIDs here will be generated in the same sequence as above because we reset the random
                    Object id = lookup.lookupProperty(nodeId, INSTANCE);
                    if (getter.get(id, globalGroup) == IdMapper.ID_NOT_FOUND) {
                        fail("Couldn't find " + id + " even though I added it just previously");
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldReturnExpectedValueForNotFound(int processors) {
        // GIVEN
        try (IdMapper idMapper = mapper(new StringEncoder(), Radix.STRING, EncodingIdMapper.NO_MONITOR, processors)) {
            idMapper.prepare(values(), mock(Collector.class), NONE, LongSets.immutable.empty());

            // WHEN
            long id;
            try (var getter = idMapper.newGetter()) {
                id = getter.get("123", globalGroup);
            }

            // THEN
            assertEquals(IdMapper.ID_NOT_FOUND, id);
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldReportProgressForSortAndDetect(int processors) {
        // GIVEN
        try (IdMapper idMapper = mapper(new StringEncoder(), Radix.STRING, EncodingIdMapper.NO_MONITOR, processors)) {
            ProgressMonitorFactory progressMonitorFactory = mock(ProgressMonitorFactory.class);
            when(progressMonitorFactory.singlePart(anyString(), anyLong())).thenReturn(mock(ProgressListener.class));
            idMapper.prepare(values(), mock(Collector.class), progressMonitorFactory, LongSets.immutable.empty());

            // WHEN
            long id;
            try (var getter = idMapper.newGetter()) {
                id = getter.get("123", globalGroup);
            }

            // THEN
            assertEquals(IdMapper.ID_NOT_FOUND, id);
            verify(progressMonitorFactory).singlePart(anyString(), anyLong());
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldEncodeShortStrings(int processors) throws KeyCollisionException {
        // GIVEN
        try (IdMapper mapper = mapper(new StringEncoder(), Radix.STRING, EncodingIdMapper.NO_MONITOR, processors)) {

            // WHEN
            IdMapper.Setter setter = mapper.newSetter();
            setter.put("123", 0, globalGroup);
            setter.put("456", 1, globalGroup);
            mapper.prepare(values("123", "456"), mock(Collector.class), NONE, LongSets.immutable.empty());

            // THEN
            try (var getter = mapper.newGetter()) {
                assertEquals(1L, getter.get("456", globalGroup));
                assertEquals(0L, getter.get("123", globalGroup));
            }
        }
    }

    @Test
    public void shouldDiscardEmptyStringWhenEmptyNotMapped() throws KeyCollisionException {
        // GIVEN
        try (IdMapper mapper = mapper(new StringEncoder(), Radix.STRING, EncodingIdMapper.NO_MONITOR, 1)) {

            // WHEN
            mapper.newSetter().put("1", 1, globalGroup);
            mapper.prepare(values(null, "1"), mock(Collector.class), NONE, LongSets.immutable.empty());

            // THEN
            try (var getter = mapper.newGetter()) {
                assertEquals(1L, getter.get("1", globalGroup));
                assertEquals(IdMapper.ID_NOT_FOUND, getter.get("", globalGroup));
            }
        }
    }

    @Test
    public void shouldDetectUnknownInputIdWhenStrict() throws KeyCollisionException {
        final var existingInputId = "A";
        final var nonExistingCollidingInputId = "B";
        StringEncoder encoder = mock(StringEncoder.class);
        when(encoder.encode(existingInputId)).thenReturn(13L);
        when(encoder.encode(nonExistingCollidingInputId)).thenReturn(13L);

        assertThat(encoder.encode(existingInputId)).isEqualTo(encoder.encode(nonExistingCollidingInputId));

        // Now the strict mapper should detect that these two are not the same by using the property value lookup
        try (var mapper = strictMapper(encoder, Radix.STRING, EncodingIdMapper.NO_MONITOR, 1)) {
            final var nodeId = 7L;
            mapper.newSetter().put(existingInputId, nodeId, globalGroup);
            Collector collector = mock(Collector.class);
            mapper.prepare(alwaysReturn(existingInputId), collector, NONE, LongSets.immutable.empty());

            try (var getter = mapper.newGetter()) {
                assertEquals(nodeId, getter.get(existingInputId, globalGroup));
                assertEquals(IdMapper.ID_NOT_FOUND, getter.get(nonExistingCollidingInputId, globalGroup));
            }
        }
    }

    @Test
    public void shouldFindEncodedShortStringsWithNonAscii() throws KeyCollisionException {
        // GIVEN
        try (IdMapper mapper = mapper(new StringEncoder(), Radix.STRING, EncodingIdMapper.NO_MONITOR, 1)) {

            // WHEN
            var v1 = "P_Évora";
            var v2 = "P_Setúbal";
            IdMapper.Setter setter = mapper.newSetter();
            setter.put(v1, 0, globalGroup);
            setter.put(v2, 1, globalGroup);
            mapper.prepare(values(v1, v2), mock(Collector.class), NONE, LongSets.immutable.empty());

            // THEN
            try (var getter = mapper.newGetter()) {
                assertEquals(1L, getter.get(v2, globalGroup));
                assertEquals(0L, getter.get(v1, globalGroup));
            }
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldEncodeSmallSetOfRandomData(int processors) throws KeyCollisionException {
        // GIVEN
        int size = random.nextInt(10_000) + 2;
        ValueType type = ValueType.values()[random.nextInt(ValueType.values().length)];
        try (IdMapper mapper = mapper(type.encoder(), type.radix(), EncodingIdMapper.NO_MONITOR, processors)) {

            // WHEN
            IdMapper.Setter setter = mapper.newSetter();
            ValueGenerator values = new ValueGenerator(type.data(random.random()));
            for (int nodeId = 0; nodeId < size; nodeId++) {
                setter.put(values.lookupProperty(nodeId, INSTANCE), nodeId, globalGroup);
            }
            mapper.prepare(values, mock(Collector.class), NONE, LongSets.immutable.empty());

            // THEN
            try (var getter = mapper.newGetter()) {
                for (int nodeId = 0; nodeId < size; nodeId++) {
                    Object value = values.values.get(nodeId);
                    assertEquals(nodeId, getter.get(value, globalGroup), "Expected " + value + " to map to " + nodeId);
                }
            }
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldReportCollisionsForSameInputId(int processors) throws KeyCollisionException {
        // GIVEN
        try (IdMapper mapper = mapper(new StringEncoder(), Radix.STRING, EncodingIdMapper.NO_MONITOR, processors)) {
            IdMapper.Setter setter = mapper.newSetter();
            PropertyValueLookup values = values("10", "9", "10");
            try (var lookup = values.newLookup(true)) {
                for (int i = 0; i < 3; i++) {
                    setter.put(lookup.lookupProperty(i, INSTANCE), i, globalGroup);
                }
            }

            // WHEN
            Collector collector = mock(Collector.class);
            mapper.prepare(values, collector, NONE, LongSets.immutable.empty());

            // THEN
            verify(collector).collectDuplicateNode("10", 2, globalGroup);
            verifyNoMoreInteractions(collector);
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldCopeWithCollisionsBasedOnDifferentInputIds(int processors) throws KeyCollisionException {
        // GIVEN
        EncodingIdMapper.Monitor monitor = mock(EncodingIdMapper.Monitor.class);
        Encoder encoder = mock(Encoder.class);
        when(encoder.encode(any())).thenReturn(12345L);
        try (IdMapper mapper = mapper(encoder, Radix.STRING, monitor, processors)) {
            IdMapper.Setter setter = mapper.newSetter();
            PropertyValueLookup ids = values("10", "9");
            try (var lookup = ids.newLookup(true)) {
                for (int i = 0; i < 2; i++) {
                    setter.put(lookup.lookupProperty(i, INSTANCE), i, globalGroup);
                }
            }

            // WHEN
            Collector collector = mock(Collector.class);
            mapper.prepare(ids, collector, NONE, LongSets.immutable.empty());

            // THEN
            verifyNoMoreInteractions(collector);
            verify(monitor).numberOfCollisions(2);
            try (var getter = mapper.newGetter()) {
                assertEquals(0L, getter.get("10", globalGroup));
                assertEquals(1L, getter.get("9", globalGroup));
            }
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldCopeWithMixedActualAndAccidentalCollisions(int processors) throws KeyCollisionException {
        // GIVEN
        EncodingIdMapper.Monitor monitor = mock(EncodingIdMapper.Monitor.class);
        Encoder encoder = mock(Encoder.class);
        // Create these explicit instances so that we can use them in mock, even for same values
        String a = "a";
        String b = "b";
        String c = "c";
        String a2 = "a";
        String e = "e";
        String f = "f";
        when(encoder.encode(a)).thenReturn(1L);
        when(encoder.encode(b)).thenReturn(1L);
        when(encoder.encode(c)).thenReturn(3L);
        when(encoder.encode(a2)).thenReturn(1L);
        when(encoder.encode(e)).thenReturn(2L);
        when(encoder.encode(f)).thenReturn(1L);
        Group groupA = groups.getOrCreate("A");
        Group groupB = groups.getOrCreate("B");
        try (IdMapper mapper = mapper(encoder, Radix.STRING, monitor, processors)) {
            IdMapper.Setter setter = mapper.newSetter();
            PropertyValueLookup ids = values("a", "b", "c", "a", "e", "f");
            Group[] groups = new Group[] {groupA, groupA, groupA, groupB, groupB, groupB};

            // a/A --> 1
            // b/A --> 1 accidental collision with a/A
            // c/A --> 3
            // a/B --> 1 actual collision with a/A
            // e/B --> 2
            // f/B --> 1 accidental collision with a/A

            // WHEN
            try (var lookup = ids.newLookup(true)) {
                for (int i = 0; i < 6; i++) {
                    setter.put(lookup.lookupProperty(i, INSTANCE), i, groups[i]);
                }
            }
            Collector collector = mock(Collector.class);
            mapper.prepare(ids, collector, NONE, LongSets.immutable.empty());

            // THEN
            verify(monitor).numberOfCollisions(4);
            try (var getter = mapper.newGetter()) {
                assertEquals(0L, getter.get(a, groupA));
                assertEquals(1L, getter.get(b, groupA));
                assertEquals(2L, getter.get(c, groupA));
                assertEquals(3L, getter.get(a2, groupB));
                assertEquals(4L, getter.get(e, groupB));
                assertEquals(5L, getter.get(f, groupB));
            }
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldBeAbleToHaveDuplicateInputIdButInDifferentGroups(int processors) throws KeyCollisionException {
        // GIVEN
        EncodingIdMapper.Monitor monitor = mock(EncodingIdMapper.Monitor.class);
        Group firstGroup = groups.getOrCreate("first");
        Group secondGroup = groups.getOrCreate("second");
        try (IdMapper mapper = mapper(new StringEncoder(), Radix.STRING, monitor, processors)) {
            IdMapper.Setter setter = mapper.newSetter();
            PropertyValueLookup ids = values("10", "9", "10");
            int id = 0;
            // group 0
            try (var lookup = ids.newLookup(true)) {
                setter.put(lookup.lookupProperty(id, INSTANCE), id++, firstGroup);
                setter.put(lookup.lookupProperty(id, INSTANCE), id++, firstGroup);
                // group 1
                setter.put(lookup.lookupProperty(id, INSTANCE), id, secondGroup);
            }
            Collector collector = mock(Collector.class);
            mapper.prepare(ids, collector, NONE, LongSets.immutable.empty());

            // WHEN/THEN
            verifyNoMoreInteractions(collector);
            verify(monitor).numberOfCollisions(0);
            try (var getter = mapper.newGetter()) {
                assertEquals(0L, getter.get("10", firstGroup));
                assertEquals(1L, getter.get("9", firstGroup));
                assertEquals(2L, getter.get("10", secondGroup));
            }
            assertFalse(mapper.leftOverDuplicateNodesIds().hasNext());
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldOnlyFindInputIdsInSpecificGroup(int processors) throws KeyCollisionException {
        // GIVEN
        Group firstGroup = groups.getOrCreate("first");
        Group secondGroup = groups.getOrCreate("second");
        Group thirdGroup = groups.getOrCreate("third");
        try (IdMapper mapper = mapper(new StringEncoder(), Radix.STRING, EncodingIdMapper.NO_MONITOR, processors)) {
            IdMapper.Setter setter = mapper.newSetter();
            PropertyValueLookup ids = values("8", "9", "10");
            int id = 0;
            try (var lookup = ids.newLookup(true)) {
                setter.put(lookup.lookupProperty(id, INSTANCE), id++, firstGroup);
                setter.put(lookup.lookupProperty(id, INSTANCE), id++, secondGroup);
                setter.put(lookup.lookupProperty(id, INSTANCE), id, thirdGroup);
            }
            mapper.prepare(ids, mock(Collector.class), NONE, LongSets.immutable.empty());

            // WHEN/THEN
            try (var getter = mapper.newGetter()) {
                assertEquals(0L, getter.get("8", firstGroup));
                assertEquals(IdMapper.ID_NOT_FOUND, getter.get("8", secondGroup));
                assertEquals(IdMapper.ID_NOT_FOUND, getter.get("8", thirdGroup));

                assertEquals(IdMapper.ID_NOT_FOUND, getter.get("9", firstGroup));
                assertEquals(1L, getter.get("9", secondGroup));
                assertEquals(IdMapper.ID_NOT_FOUND, getter.get("9", thirdGroup));

                assertEquals(IdMapper.ID_NOT_FOUND, getter.get("10", firstGroup));
                assertEquals(IdMapper.ID_NOT_FOUND, getter.get("10", secondGroup));
                assertEquals(2L, getter.get("10", thirdGroup));
            }
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldHandleManyGroups(int processors) throws KeyCollisionException {
        // GIVEN
        int size = 256; // which results in GLOBAL (0) + 1-256 = 257 groups, i.e. requiring two bytes
        for (int i = 0; i < size; i++) {
            groups.getOrCreate("" + i);
        }
        try (IdMapper mapper = mapper(new LongEncoder(), Radix.LONG, EncodingIdMapper.NO_MONITOR, processors)) {
            IdMapper.Setter setter = mapper.newSetter();

            // WHEN
            Integer[] values = new Integer[size];
            for (int i = 0; i < size; i++) {
                values[i] = i;
                setter.put(i, i, groups.get("" + i));
            }
            // null since this test should have been set up to not run into collisions
            mapper.prepare(values(values), mock(Collector.class), NONE, LongSets.immutable.empty());

            // THEN
            try (var getter = mapper.newGetter()) {
                for (int i = 0; i < size; i++) {
                    assertEquals(i, getter.get(i, groups.get("" + i)));
                }
            }
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldDetectCorrectDuplicateInputIdsWhereManyAccidentalInManyGroups(int processors)
            throws KeyCollisionException {
        // GIVEN
        final var encoder = new ControlledEncoder(new LongEncoder());
        final int idsPerGroup = 20;
        int groupCount = 5;
        for (int i = 0; i < groupCount; i++) {
            groups.getOrCreate("Group " + i);
        }
        try (var mapper = mapper(
                encoder,
                Radix.LONG,
                EncodingIdMapper.NO_MONITOR,
                ParallelSort.DEFAULT,
                numberOfCollisions ->
                        new LongCollisionValues(NumberArrayFactories.OFF_HEAP, numberOfCollisions, INSTANCE),
                processors)) {
            IdMapper.Setter setter = mapper.newSetter();
            Function<Long, Integer> nodeIdToGroupId = nodeId -> toIntExact(nodeId / idsPerGroup);
            PropertyValueLookup ids = r -> new PropertyValueLookup.Lookup() {
                @Override
                public Object lookupProperty(long nodeId, MemoryTracker memoryTracker) {
                    int groupId = nodeIdToGroupId.apply(nodeId);
                    // Let the first 10% in each group be accidental collisions with each other
                    // i.e. all first 10% in each group collides with all other first 10% in each group
                    if (nodeId % idsPerGroup < 2) { // Let these colliding values encode into the same eId as well,
                        // so that they are definitely marked as collisions
                        encoder.useThisIdToEncodeNoMatterWhatComesIn(1234567L);
                        return nodeId % idsPerGroup;
                    }

                    // The other 90% will be accidental collisions for something else
                    encoder.useThisIdToEncodeNoMatterWhatComesIn((long) (123456 - groupId));
                    return nodeId;
                }

                @Override
                public void close() {}
            };

            // WHEN
            var lookup = ids.newLookup(true);
            int count = idsPerGroup * groupCount;
            for (long nodeId = 0; nodeId < count; nodeId++) {
                var groupId = nodeIdToGroupId.apply(nodeId);
                var inputId = lookup.lookupProperty(nodeId, INSTANCE);
                setter.put(inputId, nodeId, groups.get(groupId));
            }
            Collector collector = mock(Collector.class);

            mapper.prepare(ids, collector, NONE, LongSets.immutable.empty());

            // THEN
            verifyNoMoreInteractions(collector);
            try (var getter = mapper.newGetter()) {
                for (long nodeId = 0; nodeId < count; nodeId++) {
                    var groupId = nodeIdToGroupId.apply(nodeId);
                    var inputId = lookup.lookupProperty(nodeId, INSTANCE);
                    var actual = getter.get(inputId, groups.get(groupId));
                    assertEquals(nodeId, actual);
                }
            }
            verifyNoMoreInteractions(collector);
            assertFalse(mapper.leftOverDuplicateNodesIds().hasNext());
            lookup.close();
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldHandleHolesInIdSequence(int processors) throws KeyCollisionException {
        // GIVEN
        try (IdMapper mapper = mapper(new LongEncoder(), Radix.LONG, EncodingIdMapper.NO_MONITOR, processors)) {
            IdMapper.Setter setter = mapper.newSetter();
            List<Object> ids = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                if (!random.nextBoolean()) {
                    Long id = (long) i;
                    ids.add(id);
                    setter.put(id, i, globalGroup);
                }
            }

            // WHEN
            mapper.prepare(values(ids.toArray()), mock(Collector.class), NONE, LongSets.immutable.empty());

            // THEN
            try (var getter = mapper.newGetter()) {
                for (Object id : ids) {
                    assertEquals(((Long) id).longValue(), getter.get(id, globalGroup));
                }
            }
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldHandleLargeAmountsOfDuplicateNodeIds(int processors) throws KeyCollisionException {
        // GIVEN
        try (IdMapper mapper = mapper(new LongEncoder(), Radix.LONG, EncodingIdMapper.NO_MONITOR, processors)) {
            IdMapper.Setter setter = mapper.newSetter();
            long nodeId = 0;
            int high = 10;
            // a list of input ids
            List<Object> ids = new ArrayList<>();
            for (int run = 0; run < 2; run++) {
                for (long i = 0; i < high / 2; i++) {
                    ids.add(high - (i + 1));
                    ids.add(i);
                }
            }
            // fed to the IdMapper
            for (Object inputId : ids) {
                setter.put(inputId, nodeId++, globalGroup);
            }

            // WHEN
            Collector collector = mock(Collector.class);
            mapper.prepare(values(ids.toArray()), collector, NONE, LongSets.immutable.empty());

            // THEN
            verify(collector, times(high)).collectDuplicateNode(any(Object.class), anyLong(), any());
            assertEquals(high, count(mapper.leftOverDuplicateNodesIds()));
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldDetectLargeAmountsOfCollisions(int processors) throws KeyCollisionException {
        // GIVEN
        try (IdMapper mapper = mapper(new StringEncoder(), Radix.STRING, EncodingIdMapper.NO_MONITOR, processors)) {
            IdMapper.Setter setter = mapper.newSetter();
            int count = 20_000;
            List<Object> ids = new ArrayList<>();
            long id = 0;

            // Generate and add all input ids
            for (int elements = 0; elements < count; elements++) {
                String inputId = UUID.randomUUID().toString();
                for (int i = 0; i < 2; i++) {
                    ids.add(inputId);
                    setter.put(inputId, id++, globalGroup);
                }
            }

            // WHEN
            var reportedCount = new AtomicLong();
            var collector = mock(Collector.class);
            doAnswer(invocationOnMock -> {
                        reportedCount.incrementAndGet();
                        return null;
                    })
                    .when(collector)
                    .collectDuplicateNode(any(Object.class), anyLong(), any());
            mapper.prepare(values(ids.toArray()), collector, NONE, LongSets.immutable.empty());

            // THEN
            assertEquals(count, reportedCount.intValue());
        }
    }

    @ParameterizedTest(name = "processors:{0}")
    @MethodSource("data")
    public void shouldPutFromMultipleThreads(int processors) throws Throwable {
        // GIVEN
        try (IdMapper idMapper = mapper(new StringEncoder(), Radix.STRING, EncodingIdMapper.NO_MONITOR, processors)) {
            AtomicLong highNodeId = new AtomicLong();
            int batchSize = 1234;
            Race race = new Race();
            PropertyValueLookup inputIdLookup = CONVERT_TO_STRING;
            int countPerThread = 30_000;
            race.addContestants(processors, () -> {
                try (var lookup = inputIdLookup.newLookup(true)) {
                    int cursor = batchSize;
                    long nextNodeId = 0;
                    IdMapper.Setter setter = idMapper.newSetter();
                    for (int j = 0; j < countPerThread; j++) {
                        if (cursor == batchSize) {
                            nextNodeId = highNodeId.getAndAdd(batchSize);
                            cursor = 0;
                        }
                        long nodeId = nextNodeId++;
                        cursor++;

                        setter.put(lookup.lookupProperty(nodeId, INSTANCE), nodeId, globalGroup);
                    }
                } catch (KeyCollisionException e) {
                    throw new RuntimeException(e);
                }
            });

            // WHEN
            race.go();
            idMapper.prepare(inputIdLookup, mock(Collector.class), NONE, LongSets.immutable.empty());

            // THEN
            int count = processors * countPerThread;
            int countWithGapsWorstCase = count + batchSize * processors;
            int correctHits = 0;
            try (var getter = idMapper.newGetter();
                    var lookup = inputIdLookup.newLookup(true)) {
                for (long nodeId = 0; nodeId < countWithGapsWorstCase; nodeId++) {
                    long result = getter.get(lookup.lookupProperty(nodeId, INSTANCE), globalGroup);
                    if (result != -1) {
                        assertEquals(nodeId, result);
                        correctHits++;
                    }
                }
            }
            assertEquals(count, correctHits);
        }
    }

    @Test
    void shouldSkipNullValues() throws KeyCollisionException {
        // GIVEN
        MutableLong highDataIndex = new MutableLong();
        MutableLong highTrackerIndex = new MutableLong();
        EncodingIdMapper.Monitor monitor = new EncodingIdMapper.Monitor() {
            @Override
            public void preparing(long highestSetDataIndex, long highestSetTrackerIndex) {
                highDataIndex.setValue(highestSetDataIndex);
                highTrackerIndex.setValue(highestSetTrackerIndex);
            }
        };
        try (IdMapper idMapper = mapper(new LongEncoder(), Radix.LONG, monitor, 4)) {
            IdMapper.Setter setter = idMapper.newSetter();
            long count = 1_000;
            for (long id = 0; id < count; id++) {
                long nodeId = id * 2;
                setter.put(id, nodeId, globalGroup);
            }

            // WHEN
            idMapper.prepare(FAILING_LOOKUP, Collector.EMPTY, NONE, LongSets.immutable.empty());

            // THEN
            assertEquals((count - 1) * 2, highDataIndex.longValue());
            assertEquals(count - 1, highTrackerIndex.longValue());
        }
    }

    @Test
    void shouldCompleteQuicklyForMostlyGapValues() throws KeyCollisionException {
        // given
        int nThreads = 4;
        var encoder = new LongEncoder();
        var numberOfComparisons = new AtomicInteger();
        ParallelSort.Comparator comparator = new ParallelSort.Comparator() {
            @Override
            public boolean lt(long left, long pivot) {
                numberOfComparisons.incrementAndGet();
                return ParallelSort.DEFAULT.lt(left, pivot);
            }

            @Override
            public boolean ge(long right, long pivot) {
                numberOfComparisons.incrementAndGet();
                return ParallelSort.DEFAULT.ge(right, pivot);
            }

            @Override
            public long dataValue(long dataValue) {
                return ParallelSort.DEFAULT.dataValue(dataValue);
            }
        };
        try (IdMapper idMapper = mapper(encoder, Radix.LONG, NO_MONITOR, comparator, autoDetect(encoder), nThreads)) {
            IdMapper.Setter setter = idMapper.newSetter();
            int count = nThreads * 1_000;
            MutableLong nextNodeId = new MutableLong();
            for (long id = 0; id < count; id++) {
                setter.put(id, nextNodeId.getAndAdd(random.nextInt(50, 100)), globalGroup);
            }

            // when
            idMapper.prepare(FAILING_LOOKUP, Collector.EMPTY, NONE, LongSets.immutable.empty());

            // then before making the fix where the IdMapper would skip "null" values this test would have taken
            // multiple
            // weeks. We can also assert this by checking how many comparisons have been made when sorting
            assertThat(numberOfComparisons.get()).isLessThan(nextNodeId.intValue() / 4);
        }
    }

    @Test
    void shouldHandleEqualIdsInMultipleGroups() {
        // given
        var movie = groups.getOrCreate("Movie");
        var actor = groups.getOrCreate("Actor");
        try (var idMapper = mapper(new StringEncoder(), Radix.STRING, NO_MONITOR, 1)) {

            // when
            IdMapper.Setter setter = idMapper.newSetter();
            var data = Map.of(546L, "1", 0L, "1", 547L, "2", 1L, "2", 548L, "3", 2L, "3");
            data.forEach((key, value) -> {
                try {
                    setter.put(value, key, key > 100 ? actor : movie);
                } catch (KeyCollisionException e) {
                    throw new RuntimeException(e);
                }
            });
            idMapper.prepare(mapValues(data), Collector.EMPTY, NONE, LongSets.immutable.empty());

            // then
            try (var getter = idMapper.newGetter()) {
                assertThat(getter.get("1", actor)).isEqualTo(546);
                assertThat(getter.get("1", movie)).isEqualTo(0);
                assertThat(getter.get("2", actor)).isEqualTo(547);
                assertThat(getter.get("2", movie)).isEqualTo(1);
                assertThat(getter.get("3", actor)).isEqualTo(548);
                assertThat(getter.get("3", movie)).isEqualTo(2);
            }
        }
    }

    private PropertyValueLookup mapValues(Map<Long, String> data) {
        return r -> new PropertyValueLookup.Lookup() {
            @Override
            public Object lookupProperty(long nodeId, MemoryTracker memoryTracker) {
                return data.get(nodeId);
            }

            @Override
            public void close() {}
        };
    }

    private static PropertyValueLookup values(Object... values) {
        return r -> new PropertyValueLookup.Lookup() {
            @Override
            public Object lookupProperty(long nodeId, MemoryTracker memoryTracker) {
                return values[toIntExact(nodeId)];
            }

            @Override
            public void close() {}
        };
    }

    private IdMapper strictMapper(
            Encoder encoder, Factory<Radix> radix, EncodingIdMapper.Monitor monitor, int processors) {
        return mapper(encoder, true, radix, monitor, processors);
    }

    private IdMapper mapper(Encoder encoder, Factory<Radix> radix, EncodingIdMapper.Monitor monitor, int processors) {
        return mapper(encoder, false, radix, monitor, processors);
    }

    private IdMapper mapper(
            Encoder encoder, boolean strict, Factory<Radix> radix, EncodingIdMapper.Monitor monitor, int processors) {
        return new EncodingIdMapper(
                NumberArrayFactories.OFF_HEAP,
                encoder,
                strict,
                radix,
                monitor,
                RANDOM_TRACKER_FACTORY,
                groups,
                autoDetect(encoder),
                10_000,
                processors,
                ParallelSort.DEFAULT,
                INSTANCE);
    }

    private EncodingIdMapper mapper(
            Encoder encoder,
            Factory<Radix> radix,
            EncodingIdMapper.Monitor monitor,
            ParallelSort.Comparator comparator,
            LongFunction<CollisionValues> collisionValuesFactory,
            int processors) {
        return new EncodingIdMapper(
                NumberArrayFactories.OFF_HEAP,
                encoder,
                false,
                radix,
                monitor,
                RANDOM_TRACKER_FACTORY,
                groups,
                collisionValuesFactory,
                1_000,
                processors,
                comparator,
                INSTANCE);
    }

    private static Stream<Integer> data() {
        Collection<Integer> data = new ArrayList<>();
        data.add(1);
        data.add(2);
        int bySystem = Runtime.getRuntime().availableProcessors() - 1;
        if (bySystem > 2) {
            data.add(bySystem);
        }
        return data.stream();
    }

    private static LongFunction<CollisionValues> autoDetect(Encoder encoder) {
        return numberOfCollisions -> encoder instanceof LongEncoder
                ? new LongCollisionValues(NumberArrayFactories.OFF_HEAP, numberOfCollisions, INSTANCE)
                : new StringCollisionValues(NumberArrayFactories.OFF_HEAP, numberOfCollisions, INSTANCE);
    }

    private static final TrackerFactory RANDOM_TRACKER_FACTORY = (arrayFactory, size) -> currentTimeMillis() % 2 == 0
            ? new IntTracker(arrayFactory.newIntArray(size, IntTracker.DEFAULT_VALUE, INSTANCE))
            : new BigIdTracker(arrayFactory.newByteArray(size, BigIdTracker.DEFAULT_VALUE, INSTANCE));

    private PropertyValueLookup alwaysReturn(Object id) {
        return r -> new PropertyValueLookup.Lookup() {
            @Override
            public Object lookupProperty(long nodeId, MemoryTracker memoryTracker) {
                return id;
            }

            @Override
            public void close() {}
        };
    }

    private static class ValueGenerator implements PropertyValueLookup, PropertyValueLookup.Lookup {
        private final Factory<Object> generator;
        private final List<Object> values = new ArrayList<>();
        private final Set<Object> deduper = new HashSet<>();

        ValueGenerator(Factory<Object> generator) {
            this.generator = generator;
        }

        @Override
        public Lookup newLookup(boolean readOnly) {
            return this;
        }

        @Override
        public Object lookupProperty(long nodeId, MemoryTracker memoryTracker) {
            while (true) {
                Object value = generator.newInstance();
                if (deduper.add(value)) {
                    values.add(value);
                    return value;
                }
            }
        }

        @Override
        public void close() {}
    }

    private enum ValueType {
        LONGS {
            @Override
            Encoder encoder() {
                return new LongEncoder();
            }

            @Override
            Factory<Radix> radix() {
                return Radix.LONG;
            }

            @Override
            Factory<Object> data(final Random random) {
                return () -> random.nextInt(1_000_000_000);
            }
        },
        LONGS_AS_STRINGS {
            @Override
            Encoder encoder() {
                return new StringEncoder();
            }

            @Override
            Factory<Radix> radix() {
                return Radix.STRING;
            }

            @Override
            Factory<Object> data(final Random random) {
                return () -> String.valueOf(random.nextInt(1_000_000_000));
            }
        },
        VERY_LONG_STRINGS {
            final char[] CHARS = "½!\"#¤%&/()=?`´;:,._-<>".toCharArray();

            @Override
            Encoder encoder() {
                return new StringEncoder();
            }

            @Override
            Factory<Radix> radix() {
                return Radix.STRING;
            }

            @Override
            Factory<Object> data(final Random random) {
                return new Factory<>() {
                    @Override
                    public Object newInstance() {
                        // Randomize length, although reduce chance of really long strings
                        int length = 1500;
                        for (int i = 0; i < 4; i++) {
                            length = random.nextInt(length) + 20;
                        }
                        char[] chars = new char[length];
                        for (int i = 0; i < length; i++) {
                            char ch;
                            if (random.nextBoolean()) { // A letter
                                ch = randomLetter(random);
                            } else {
                                ch = CHARS[random.nextInt(CHARS.length)];
                            }
                            chars[i] = ch;
                        }
                        return new String(chars);
                    }

                    private char randomLetter(Random random) {
                        int base;
                        if (random.nextBoolean()) { // lower case
                            base = 'a';
                        } else { // upper case
                            base = 'A';
                        }
                        int size = 'z' - 'a';
                        return (char) (base + random.nextInt(size));
                    }
                };
            }
        };

        abstract Encoder encoder();

        abstract Factory<Radix> radix();

        abstract Factory<Object> data(Random random);
    }
}
