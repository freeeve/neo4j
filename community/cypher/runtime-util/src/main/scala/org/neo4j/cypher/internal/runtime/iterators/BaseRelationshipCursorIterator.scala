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
package org.neo4j.cypher.internal.runtime.iterators

import org.neo4j.cypher.internal.runtime.ClosingRelationshipIterator
import org.neo4j.storageengine.api.RelationshipVisitor

abstract class BaseRelationshipCursorIterator extends ClosingRelationshipIterator {

  import BaseRelationshipCursorIterator.NOT_INITIALIZED
  import BaseRelationshipCursorIterator.NO_ID

  private var _next = NOT_INITIALIZED
  protected var relTypeId: Int = NO_ID
  protected var source: Long = NO_ID
  protected var target: Long = NO_ID

  override def relationshipVisit[EXCEPTION <: Exception](
    relationshipId: Long,
    visitor: RelationshipVisitor[EXCEPTION]
  ): Boolean = {
    visitor.visit(relationshipId, relTypeId, source, target)
    true
  }

  protected def fetchNext(): Long

  override def innerHasNext: Boolean = {
    if (_next == NOT_INITIALIZED) {
      _next = fetchNext()
    }

    _next >= 0
  }

  override def startNodeId(): Long = source

  override def endNodeId(): Long = target

  override def typeId(): Int = relTypeId

  /**
   * Store the current state in case the underlying cursor is closed when calling next.
   */
  protected def storeState(): Unit

  override def next(): Long = {
    if (!hasNext) {
      close()
      Iterator.empty.next()
    }

    val current = _next
    storeState()
    // Note that if no more elements are found cursors
    // will be closed so no need to do an extra check after fetching
    _next = fetchNext()

    current
  }

  override def close(): Unit
}

object BaseRelationshipCursorIterator {
  private val NOT_INITIALIZED = -2L
  private val NO_ID = -1

  val EMPTY = new BaseRelationshipCursorIterator {
    override protected def fetchNext(): Long = -1L
    override protected def storeState(): Unit = ()
    override def close(): Unit = ()
  }
}
