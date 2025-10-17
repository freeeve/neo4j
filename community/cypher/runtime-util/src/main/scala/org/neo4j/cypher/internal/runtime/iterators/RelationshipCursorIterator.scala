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

import org.neo4j.internal.kernel.api.RelationshipTraversalCursor

class RelationshipCursorIterator(
  selectionCursor: RelationshipTraversalCursor,
  traversalCursor: RelationshipTraversalCursor = null
) extends BaseRelationshipCursorIterator {

  override protected def fetchNext(): Long =
    if (selectionCursor.next()) selectionCursor.relationshipReference()
    else {
      -1L
    }

  override protected def storeState(): Unit = {
    relTypeId = selectionCursor.`type`()
    source = selectionCursor.sourceNodeReference()
    target = selectionCursor.targetNodeReference()
  }

  override def close(): Unit = {
    if (traversalCursor != null && !(traversalCursor eq selectionCursor)) {
      traversalCursor.close()
    }
    selectionCursor.close()
  }
}
