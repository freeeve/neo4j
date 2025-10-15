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

import java.util.HashSet;
import java.util.Set;
import org.neo4j.graphdb.GqlStatusObject;
import org.neo4j.graphdb.Notification;

public class NotificationManagerImpl implements NotificationManager {

    private Set<Notification> notifications = new HashSet<>();
    private Set<GqlStatusObject> gqlStatus = new HashSet<>();

    @Override
    public void addNotification(Notification notification) {
        notifications.add(notification);
    }

    @Override
    public void addGqlStatus(GqlStatusObject gqlStatusObject) {
        gqlStatus.add(gqlStatusObject);
    }

    @Override
    public Iterable<Notification> retrieveAndClearNotifications() {
        var pointer = notifications;
        notifications = new HashSet<>();
        return pointer;
    }

    @Override
    public Iterable<GqlStatusObject> retrieveAndClearGqlStatusObjects() {
        var pointer = gqlStatus;
        gqlStatus = new HashSet<>();
        return pointer;
    }
}
