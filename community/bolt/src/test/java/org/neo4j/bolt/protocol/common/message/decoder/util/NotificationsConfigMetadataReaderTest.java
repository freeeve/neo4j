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
package org.neo4j.bolt.protocol.common.message.decoder.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.bolt.testing.util.ErrorUtil;
import org.neo4j.boltmessages.notifications.NotificationsConfig;
import org.neo4j.kernel.impl.query.NotificationConfiguration;
import org.neo4j.packstream.error.reader.PackstreamReaderException;
import org.neo4j.values.storable.Values;
import org.neo4j.values.virtual.ListValueBuilder;
import org.neo4j.values.virtual.MapValue;
import org.neo4j.values.virtual.MapValueBuilder;

class NotificationsConfigMetadataReaderTest {
    private static final String MINIMUM_SEVERITY_KEY = "notifications_minimum_severity";
    private static final String DISABLED_CATEGORIES_KEY = "notifications_disabled_categories";
    private static final String DISABLED_CLASSIFICATION_KEY = "notifications_disabled_classifications";
    private static final String DISABLE_ALL_NOTIFICATIONS = "OFF";

    @ParameterizedTest
    @MethodSource("nullValues")
    public void shouldAcceptAndMapNullValues(NotificationsConfigSupplier subject) throws PackstreamReaderException {
        var config = subject.get();
        var serverConfig = config.buildConfiguration(null);

        assertThat(serverConfig.severityLevel()).isEqualTo(NotificationConfiguration.Severity.INFORMATION);
        assertThat(serverConfig.disabledCategories()).isEqualTo(Collections.emptySet());
    }

    static Stream<Arguments> nullValues() {
        return buildSubjectsForValues(Map.of(), Map.of());
    }

    @ParameterizedTest
    @MethodSource("emptyListForCategories")
    public void shouldAcceptEmptyListForCategories(NotificationsConfigSupplier subject)
            throws PackstreamReaderException {
        var config = subject.get();
        var serverConfig = config.buildConfiguration(null);

        assertThat(serverConfig.disabledCategories()).isEqualTo(Collections.emptySet());
    }

    static Stream<Arguments> emptyListForCategories() {
        return buildSubjectsForValues(
                Map.of(DISABLED_CLASSIFICATION_KEY, List.of()), Map.of(DISABLED_CATEGORIES_KEY, List.of()));
    }

    @ParameterizedTest
    @MethodSource("unrecognizedSeverity")
    public void shouldThrowWhenUnrecognizedSeverity(NotificationsConfigSupplier subject) {
        assertThatThrownBy(subject::get)
                .isInstanceOf(PackstreamReaderException.class)
                .hasMessageContaining(ErrorUtil.useNewMessage("08N06: General network protocol error.")
                        .whenLegacyFallbackTo("severity"));
    }

    static Stream<Arguments> unrecognizedSeverity() {
        return buildSubjectsForValues(Map.of(MINIMUM_SEVERITY_KEY, "made up"), Map.of(MINIMUM_SEVERITY_KEY, "made up"));
    }

    @ParameterizedTest
    @MethodSource("unrecognizedCategory")
    public void shouldThrowWhenUnrecognizedCategory(NotificationsConfigSupplier subject) {
        assertThatThrownBy(subject::get)
                .isInstanceOf(PackstreamReaderException.class)
                .hasMessageContaining(ErrorUtil.useNewMessage("08N06: General network protocol error.")
                        .whenLegacyFallbackTo("category"));
    }

    static Stream<Arguments> unrecognizedCategory() {
        return buildSubjectsForValues(
                Map.of(DISABLED_CLASSIFICATION_KEY, List.of("made up")),
                Map.of(DISABLED_CATEGORIES_KEY, List.of("made up")));
    }

    @ParameterizedTest
    @MethodSource("validSeverities")
    public void shouldMapSeverityValues(
            NotificationsConfigSupplier subject, NotificationConfiguration.Severity expected)
            throws PackstreamReaderException {
        var config = subject.get();

        var serverConfig = config.buildConfiguration(null);
        assertThat(serverConfig.severityLevel()).isEqualTo(expected);
    }

    private static Stream<Arguments> validSeverities() {
        return Stream.of(
                        List.of("INFORMATION", NotificationConfiguration.Severity.INFORMATION),
                        List.of("WARNING", NotificationConfiguration.Severity.WARNING),
                        List.of("information", NotificationConfiguration.Severity.INFORMATION),
                        List.of("warning", NotificationConfiguration.Severity.WARNING))
                .flatMap(supportedSeverities -> buildSubjectsForValues(
                                Map.of(MINIMUM_SEVERITY_KEY, supportedSeverities.getFirst()),
                                Map.of(MINIMUM_SEVERITY_KEY, supportedSeverities.getFirst()))
                        .map(arguments -> Arguments.of(arguments.get()[0], supportedSeverities.getLast())));
    }

    @ParameterizedTest
    @MethodSource("validCategories")
    public void shouldMapCategoryValues(
            NotificationsConfigSupplier subject, Set<NotificationConfiguration.Category> expected)
            throws PackstreamReaderException {
        var config = subject.get();

        var serverConfig = config.buildConfiguration(null);
        assertThat(serverConfig.disabledCategories()).isEqualTo(expected);
    }

    private static Stream<Arguments> validCategories() {
        return Stream.of(
                        List.of("PERFORMANCE", Set.of(NotificationConfiguration.Category.PERFORMANCE)),
                        List.of("DEPRECATION", Set.of(NotificationConfiguration.Category.DEPRECATION)),
                        List.of("UNRECOGNIZED", Set.of(NotificationConfiguration.Category.UNRECOGNIZED)),
                        List.of("HINT", Set.of(NotificationConfiguration.Category.HINT)),
                        List.of("GENERIC", Set.of(NotificationConfiguration.Category.GENERIC)),
                        List.of("UNSUPPORTED", Set.of(NotificationConfiguration.Category.UNSUPPORTED)),
                        List.of("performance", Set.of(NotificationConfiguration.Category.PERFORMANCE)),
                        List.of("deprecation", Set.of(NotificationConfiguration.Category.DEPRECATION)),
                        List.of("unrecognized", Set.of(NotificationConfiguration.Category.UNRECOGNIZED)),
                        List.of("hint", Set.of(NotificationConfiguration.Category.HINT)),
                        List.of("generic", Set.of(NotificationConfiguration.Category.GENERIC)),
                        List.of("unsupported", Set.of(NotificationConfiguration.Category.UNSUPPORTED)))
                .flatMap(supportedCategories -> buildSubjectsForValues(
                                Map.of(DISABLED_CLASSIFICATION_KEY, List.of(supportedCategories.getFirst())),
                                Map.of(DISABLED_CATEGORIES_KEY, List.of(supportedCategories.getFirst())))
                        .map(arguments -> Arguments.of(arguments.get()[0], supportedCategories.getLast())));
    }

    private static Stream<Arguments> buildSubjectsForValues(Map<String, Object> map, Map<String, Object> legacyMap) {
        return Stream.of(
                Arguments.of(getReadFromMapValue(toMapValue(map))),
                Arguments.of(getReadLegacyFromMapValue(toMapValue(legacyMap))),
                Arguments.of(getReadFromMap(map)),
                Arguments.of(getReadLegacyFromMap(legacyMap)));
    }

    private static MapValue toMapValue(Map<String, Object> map) {
        final var mapBuilder = new MapValueBuilder(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof List<?> list) {
                final var listBuilder = ListValueBuilder.newListBuilder(list.size());
                for (Object item : list) {
                    listBuilder.add(Values.of(item));
                }
                mapBuilder.add(entry.getKey(), listBuilder.build());
            } else {
                mapBuilder.add(entry.getKey(), Values.of(entry.getValue()));
            }
        }
        return mapBuilder.build();
    }

    private static NotificationsConfigSupplier getReadFromMapValue(MapValue meta) {
        return () -> NotificationsConfigMetadataReader.readFromMapValue(meta);
    }

    private static NotificationsConfigSupplier getReadLegacyFromMapValue(MapValue meta) {
        return () -> NotificationsConfigMetadataReader.readLegacyFromMapValue(meta);
    }

    private static NotificationsConfigSupplier getReadFromMap(Map<String, Object> meta) {
        return () -> NotificationsConfigMetadataReader.readFromMap(meta);
    }

    private static NotificationsConfigSupplier getReadLegacyFromMap(Map<String, Object> meta) {
        return () -> NotificationsConfigMetadataReader.readLegacyFromMap(meta);
    }

    interface NotificationsConfigSupplier {
        NotificationsConfig get() throws PackstreamReaderException;
    }
}
