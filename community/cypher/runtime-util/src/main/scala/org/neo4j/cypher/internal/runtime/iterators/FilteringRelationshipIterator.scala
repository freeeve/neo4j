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
import org.neo4j.internal.kernel.api.PropertyCursor
import org.neo4j.internal.kernel.api.PropertyIndexQuery
import org.neo4j.internal.kernel.api.Read
import org.neo4j.internal.kernel.api.RelationshipScanCursor
import org.neo4j.kernel.impl.newapi.CursorPredicates

abstract class FilteringRelationshipIterator(
  longIterator: ClosingLongIterator,
  read: Read,
  cursor: RelationshipScanCursor
) extends BaseRelationshipCursorIterator {
  protected def test(cursor: RelationshipScanCursor): Boolean

  override protected def fetchNext(): Long = {
    while (longIterator.hasNext) {
      val id = longIterator.next()
      read.singleRelationship(id, cursor)

      // The node may have been deleted in this transaction, so check that it has not been
      if (cursor.next() && test(cursor)) {
        return id
      }
    }

    -1L
  }

  override protected def storeState(): Unit = {
    source = cursor.sourceNodeReference()
    target = cursor.targetNodeReference()
    relTypeId = cursor.`type`()
  }

  override def close(): Unit = longIterator.close()
}

class PropertyFilteringRelationshipIterator(
  scan: ClosingLongIterator,
  read: Read,
  relCursor: RelationshipScanCursor,
  propCursor: PropertyCursor,
  indexQueries: Array[PropertyIndexQuery]
) extends FilteringRelationshipIterator(scan, read, relCursor) {

  protected def test(cursor: RelationshipScanCursor): Boolean = {
    cursor.properties(propCursor)
    CursorPredicates.propertiesMatch(propCursor, indexQueries)
  }
}
