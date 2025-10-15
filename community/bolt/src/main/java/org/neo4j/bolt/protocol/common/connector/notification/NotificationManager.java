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

import org.neo4j.graphdb.GqlStatusObject;
import org.neo4j.graphdb.Notification;
import org.neo4j.internal.helpers.collection.Iterables;

/**
 * Enables bolt server to add and return its own notifications in addition to those
 * provided by Cypher.
 */
public interface NotificationManager {

    NotificationManager NOOP = new NoopNotificationManager();

    void addNotification(Notification notification);

    void addGqlStatus(GqlStatusObject gqlStatusObject);

    Iterable<Notification> retrieveAndClearNotifications();

    Iterable<GqlStatusObject> retrieveAndClearGqlStatusObjects();

    class NoopNotificationManager implements NotificationManager {

        @Override
        public void addNotification(Notification notification) {}

        @Override
        public void addGqlStatus(GqlStatusObject gqlStatusObject) {}

        @Override
        public Iterable<Notification> retrieveAndClearNotifications() {
            return Iterables.empty();
        }

        @Override
        public Iterable<GqlStatusObject> retrieveAndClearGqlStatusObjects() {
            return Iterables.empty();
        }
    }
}
