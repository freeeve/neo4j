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
package org.neo4j.bolt.protocol.common.connector.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.GqlStatusObject;
import org.neo4j.graphdb.Notification;

public class NotificationManagerImplTest {

    private NotificationManager notificationManager;

    @BeforeEach
    public void setUp() {
        notificationManager = new NotificationManagerImpl();
    }

    @Test
    public void shouldReturnAndClearNotifications() {
        var notification = mock(Notification.class);

        notificationManager.addNotification(notification);

        assertThat(notificationManager.retrieveAndClearNotifications()).hasSameElementsAs(List.of(notification));
        assertThat(notificationManager.retrieveAndClearNotifications()).isEmpty();
    }

    @Test
    public void shouldReturnAndClearGqlStatus() {
        var gqlStatus = mock(GqlStatusObject.class);

        notificationManager.addGqlStatus(gqlStatus);

        assertThat(notificationManager.retrieveAndClearGqlStatusObjects()).hasSameElementsAs(List.of(gqlStatus));
        assertThat(notificationManager.retrieveAndClearNotifications()).isEmpty();
    }

    @Test
    public void shouldReturnMultipleNotifications() {
        var notificationA = mock(Notification.class);
        var notificationB = mock(Notification.class);

        notificationManager.addNotification(notificationA);
        notificationManager.addNotification(notificationB);

        assertThat(notificationManager.retrieveAndClearNotifications())
                .hasSameElementsAs(List.of(notificationA, notificationB));
        assertThat(notificationManager.retrieveAndClearNotifications()).isEmpty();
    }

    @Test
    public void shouldReturnMultipleGqlStatus() {
        var gqlStatusA = mock(GqlStatusObject.class);
        var gqlStatusB = mock(GqlStatusObject.class);

        notificationManager.addGqlStatus(gqlStatusA);
        notificationManager.addGqlStatus(gqlStatusB);

        assertThat(notificationManager.retrieveAndClearGqlStatusObjects())
                .hasSameElementsAs(List.of(gqlStatusA, gqlStatusB));
        assertThat(notificationManager.retrieveAndClearNotifications()).isEmpty();
    }

    @Test
    public void shouldReturnOnlyOneNotificationForMultipleOfTheSame() {
        var notification = mock(Notification.class);

        notificationManager.addNotification(notification);
        notificationManager.addNotification(notification);

        assertThat(notificationManager.retrieveAndClearNotifications()).hasSameElementsAs(List.of(notification));
        assertThat(notificationManager.retrieveAndClearNotifications()).isEmpty();
    }

    @Test
    public void shouldReturnOnlyOneStatusForMultipleOfTheSame() {
        var gqlStatus = mock(GqlStatusObject.class);

        notificationManager.addGqlStatus(gqlStatus);
        notificationManager.addGqlStatus(gqlStatus);

        assertThat(notificationManager.retrieveAndClearGqlStatusObjects()).hasSameElementsAs(List.of(gqlStatus));
        assertThat(notificationManager.retrieveAndClearNotifications()).isEmpty();
    }
}
