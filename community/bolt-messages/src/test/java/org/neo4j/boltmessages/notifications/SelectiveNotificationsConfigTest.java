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
package org.neo4j.boltmessages.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.kernel.impl.query.NotificationConfiguration;

public class SelectiveNotificationsConfigTest {

    @Test
    public void shouldAcceptAndMapNullValues() {
        var config = new org.neo4j.boltmessages.notifications.SelectiveNotificationsConfig(null, null);

        var serverConfig = config.buildConfiguration(null);
        assertThat(serverConfig.severityLevel()).isEqualTo(NotificationConfiguration.Severity.INFORMATION);
        assertThat(serverConfig.disabledCategories()).isEqualTo(Collections.emptySet());
    }

    @Test
    public void shouldAcceptEmptyListForCategories() {
        var config = new org.neo4j.boltmessages.notifications.SelectiveNotificationsConfig(null, Set.of());

        var serverConfig = config.buildConfiguration(null);

        assertThat(serverConfig.disabledCategories()).isEqualTo(Collections.emptySet());
    }

    private static Stream<Arguments> severities() {
        return Stream.of(
                Arguments.of(NotificationConfiguration.Severity.INFORMATION),
                Arguments.of(NotificationConfiguration.Severity.WARNING));
    }

    @ParameterizedTest
    @MethodSource("severities")
    public void shouldMapSeverityValues(NotificationConfiguration.Severity expected) {
        var config = new org.neo4j.boltmessages.notifications.SelectiveNotificationsConfig(expected, null);

        var serverConfig = config.buildConfiguration(null);
        assertThat(serverConfig.severityLevel()).isEqualTo(expected);
    }

    private static Stream<Arguments> categories() {
        return Stream.of(
                Arguments.of(Set.of(NotificationConfiguration.Category.PERFORMANCE)),
                Arguments.of(Set.of(NotificationConfiguration.Category.DEPRECATION)),
                Arguments.of(Set.of(NotificationConfiguration.Category.UNRECOGNIZED)),
                Arguments.of(Set.of(NotificationConfiguration.Category.HINT)),
                Arguments.of(Set.of(NotificationConfiguration.Category.GENERIC)),
                Arguments.of(Set.of(NotificationConfiguration.Category.UNSUPPORTED)));
    }

    @ParameterizedTest
    @MethodSource("categories")
    public void shouldMapCategoryValues(Set<NotificationConfiguration.Category> expected) {
        var config = new org.neo4j.boltmessages.notifications.SelectiveNotificationsConfig(null, expected);

        var serverConfig = config.buildConfiguration(null);
        assertThat(serverConfig.disabledCategories()).isEqualTo(expected);
    }

    @Test
    public void shouldMergeConfigWithSpecified() {
        var driver = new org.neo4j.boltmessages.notifications.SelectiveNotificationsConfig(
                NotificationConfiguration.Severity.WARNING, null);
        var session = new org.neo4j.boltmessages.notifications.SelectiveNotificationsConfig(
                null, Set.of(NotificationConfiguration.Category.HINT));

        var serverConfig = session.buildConfiguration(driver);
        assertThat(serverConfig.severityLevel()).isEqualTo(NotificationConfiguration.Severity.WARNING);
        assertThat(serverConfig.disabledCategories()).isEqualTo(Set.of(NotificationConfiguration.Category.HINT));
    }

    @Test
    public void shouldIgnoreParentConfigsWithSpecified() {
        var driver = new org.neo4j.boltmessages.notifications.SelectiveNotificationsConfig(
                NotificationConfiguration.Severity.WARNING, Set.of(NotificationConfiguration.Category.HINT));
        var session = new org.neo4j.boltmessages.notifications.SelectiveNotificationsConfig(
                NotificationConfiguration.Severity.INFORMATION, Set.of());

        var serverConfig = session.buildConfiguration(driver);
        assertThat(serverConfig.severityLevel()).isEqualTo(NotificationConfiguration.Severity.INFORMATION);
        assertThat(serverConfig.disabledCategories()).isEqualTo(Set.of());
    }
}
