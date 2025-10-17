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

import org.neo4j.cypher.internal.runtime.ClosingLongIterator
import org.neo4j.internal.kernel.api.NodeCursor
import org.neo4j.internal.kernel.api.PropertyCursor
import org.neo4j.internal.kernel.api.PropertyIndexQuery
import org.neo4j.internal.kernel.api.Read
import org.neo4j.kernel.impl.newapi.CursorPredicates

abstract class FilteringNodeIterator(
  longIterator: ClosingLongIterator,
  read: Read,
  cursor: NodeCursor
) extends PrimitiveCursorIterator {

  protected def test(cursor: NodeCursor): Boolean

  protected def fetchNext(): Long = {
    while (longIterator.hasNext) {
      val id = longIterator.next()
      read.singleNode(id, cursor)

      // The node may have been deleted in this transaction, so check that it has not been
      if (cursor.next() && test(cursor)) {
        return id
      }
    }

    -1L
  }

  override def close(): Unit = longIterator.close()
}

class PropertyFilteringNodeIterator(
  scan: ClosingLongIterator,
  read: Read,
  nodeCursor: NodeCursor,
  propCursor: PropertyCursor,
  indexQueries: Array[PropertyIndexQuery]
) extends FilteringNodeIterator(scan, read, nodeCursor) {

  protected def test(nodeCursor: NodeCursor): Boolean = {
    nodeCursor.properties(propCursor)
    CursorPredicates.propertiesMatch(propCursor, indexQueries)
  }
}

class LabelFilteringNodeIterator(
  scan: ClosingLongIterator,
  read: Read,
  nodeCursor: NodeCursor,
  labels: Seq[Int]
) extends FilteringNodeIterator(scan, read, nodeCursor) {

  protected def test(nodeCursor: NodeCursor): Boolean = {
    labels.forall(nodeCursor.hasLabel)
  }
}
