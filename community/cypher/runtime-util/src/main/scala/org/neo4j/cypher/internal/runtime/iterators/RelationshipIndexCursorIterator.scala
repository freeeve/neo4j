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

import org.neo4j.internal.kernel.api.RelationshipIndexCursor
import org.neo4j.storageengine.api.RelationshipVisitor

class RelationshipIndexCursorIterator(cursor: RelationshipIndexCursor) extends BaseRelationshipCursorIterator {

  override def relationshipVisit[EXCEPTION <: Exception](
    relationshipId: Long,
    visitor: RelationshipVisitor[EXCEPTION]
  ): Boolean = {
    visitor.visit(relationshipId, relTypeId, source, target)
    true
  }

  override protected def fetchNext(): Long = {
    while (cursor.next()) {
      /* protect against concurrent deletes */
      if (cursor.readFromStore()) {
        return cursor.relationshipReference()
      }
    }
    -1L
  }

  override protected def storeState(): Unit = {
    relTypeId = cursor.`type`()
    source = cursor.sourceNodeReference()
    target = cursor.targetNodeReference()
  }

  override def close(): Unit = cursor.close()
}
