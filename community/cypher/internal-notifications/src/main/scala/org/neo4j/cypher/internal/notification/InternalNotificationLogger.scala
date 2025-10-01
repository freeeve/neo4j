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
package org.neo4j.cypher.internal.notification

/**
 * A NotificationLogger records notifications.
 */
sealed trait InternalNotificationLogger {
  def log(notification: InternalNotification): Unit

  def notifications: Set[InternalNotification]
}

/**
 * A null implementation that discards all notifications.
 */
case object devNullLogger extends InternalNotificationLogger {
  override def log(notification: InternalNotification): Unit = {}

  override def notifications: Set[InternalNotification] = Set.empty
}

/**
 * NotificationLogger that records all notifications for later retrieval.
 */
class RecordingNotificationLogger() extends InternalNotificationLogger {
  private val builder = Set.newBuilder[InternalNotification]

  def log(notification: InternalNotification): Unit = builder += notification

  def notifications: Set[InternalNotification] = builder.result()
}

/**
 * Forwards calls to multiple other loggers.
 */
class ComposedNotificationLogger(loggers: InternalNotificationLogger*) extends InternalNotificationLogger {

  override def log(notification: InternalNotification): Unit =
    loggers.foreach(_.log(notification))

  override def notifications: Set[InternalNotification] =
    loggers.view.flatMap(_.notifications).toSet
}
