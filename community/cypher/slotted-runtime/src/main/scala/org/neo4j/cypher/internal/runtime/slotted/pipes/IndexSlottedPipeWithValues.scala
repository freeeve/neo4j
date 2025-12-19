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
package org.neo4j.cypher.internal.runtime.slotted.pipes

import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.interpreted.pipes.IndexIteratorBase
import org.neo4j.cypher.internal.runtime.interpreted.pipes.Pipe
import org.neo4j.cypher.internal.runtime.interpreted.pipes.QueryState
import org.neo4j.internal.kernel.api.NodeValueIndexCursor
import org.neo4j.internal.kernel.api.RelationshipValueIndexCursor

/**
 * Provides helper methods for slotted index pipes that get nodes together with actual property values.
 */
trait IndexSlottedPipeWithValues extends Pipe {

  // Offset of the long slot of node variable
  val offset: Option[Int]
  // the indices of the index properties where we will get values
  val indexPropertyIndices: Array[Int]
  // the offsets of the cached node property slots where we will set values
  val indexPropertySlotOffsets: Array[Int]

  class SlottedNodeIndexIterator(state: QueryState, cursor: NodeValueIndexCursor)
      extends IndexIteratorBase[CypherRow](cursor) {

    override protected def fetchNext(): CypherRow = {
      if (cursor.next()) {
        val slottedContext = state.newRowWithArgument(rowFactory)
        offset.foreach(o => slottedContext.setLongAt(o, cursor.nodeReference()))
        var i = 0
        while (i < indexPropertyIndices.length) {
          val value = cursor.propertyValue(indexPropertyIndices(i))
          slottedContext.setCachedPropertyAt(indexPropertySlotOffsets(i), value)
          i += 1
        }
        slottedContext
      } else null
    }
  }

  class SlottedRelationshipIndexIterator(
    state: QueryState,
    startOffset: Option[Int],
    endOffset: Option[Int],
    cursor: RelationshipValueIndexCursor
  ) extends IndexIteratorBase[CypherRow](cursor) {
    private val relationshipWriter = Relationships.compileRelationshipWriter(offset, startOffset, endOffset)

    override protected def fetchNext(): CypherRow = {
      while (cursor.next() && relationshipWriter.readFromStore(cursor)) {
        val slottedContext = state.newRowWithArgument(rowFactory)
        relationshipWriter.writeRow(
          slottedContext,
          cursor
        )
        var i = 0
        while (i < indexPropertyIndices.length) {
          val value = cursor.propertyValue(indexPropertyIndices(i))
          slottedContext.setCachedPropertyAt(indexPropertySlotOffsets(i), value)
          i += 1
        }
        return slottedContext
      }
      null
    }
  }

  class SlottedUndirectedRelationshipIndexIterator(
    state: QueryState,
    startOffset: Option[Int],
    endOffset: Option[Int],
    cursor: RelationshipValueIndexCursor
  ) extends IndexIteratorBase[CypherRow](cursor) {

    private var emitSibling: Boolean = false
    private val relationshipWriter = Relationships.compileRelationshipWriter(offset, startOffset, endOffset)

    override protected def fetchNext(): CypherRow = {
      val newContext =
        if (emitSibling) {
          emitSibling = false
          val slottedContext = state.newRowWithArgument(rowFactory)
          relationshipWriter.writeRow(
            slottedContext,
            cursor.relationshipReference(),
            cursor.targetNodeReference(),
            cursor.sourceNodeReference()
          )
          slottedContext
        } else {
          var slottedContext: CypherRow = null
          while (slottedContext == null && cursor.next()) {
            // NOTE: sourceNodeReference and targetNodeReference is not implemented yet on the cursor
            if (cursor.readFromStore()) {
              val start = cursor.sourceNodeReference()
              val end = cursor.targetNodeReference()
              slottedContext = state.newRowWithArgument(rowFactory)
              relationshipWriter.writeRow(slottedContext, cursor.relationshipReference(), start, end)

              // For self-loops, we don't emit sibling
              emitSibling = start != end
            }
          }
          slottedContext
        }

      if (newContext != null) {
        var i = 0
        while (i < indexPropertyIndices.length) {
          val value = cursor.propertyValue(indexPropertyIndices(i))
          newContext.setCachedPropertyAt(indexPropertySlotOffsets(i), value)
          i += 1
        }
      }
      newContext
    }
  }

}
