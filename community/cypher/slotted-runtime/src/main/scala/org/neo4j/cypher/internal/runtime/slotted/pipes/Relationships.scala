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
import org.neo4j.cypher.internal.runtime.RelationshipIterator
import org.neo4j.internal.kernel.api.RelationshipCursor
import org.neo4j.internal.kernel.api.RelationshipIndexCursor

object Relationships {

  sealed trait RelationshipWriter {
    def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit

    def writeRow(
      row: CypherRow,
      relationship: Long,
      relationshipIterator: RelationshipIterator
    ): Unit

    def writeRow(
      row: CypherRow,
      cursor: RelationshipCursor
    ): Unit

    def readFromStore(cursor: RelationshipIndexCursor): Boolean
  }

  private object RelationshipWriter {

    case object DoNothing extends RelationshipWriter {
      override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {}

      override def writeRow(row: CypherRow, relationship: Long, relationshipIterator: RelationshipIterator): Unit = {}

      override def writeRow(row: CypherRow, cursor: RelationshipCursor): Unit = {}

      override def readFromStore(cursor: RelationshipIndexCursor): Boolean = true
    }
  }

  def compileRelationshipWriter(
    maybeRel: Option[Int],
    maybeStartNode: Option[Int],
    maybeEndNode: Option[Int]
  ): RelationshipWriter = (maybeRel, maybeStartNode, maybeEndNode) match {
    case (Some(relOffset), Some(startOffset), Some(endOffset)) =>
      new RelationshipWriter {
        override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
          row.setLongAt(relOffset, rel)
          row.setLongAt(startOffset, startNode)
          row.setLongAt(endOffset, endNode)
        }

        override def writeRow(row: CypherRow, relationship: Long, relationshipIterator: RelationshipIterator): Unit = {
          row.setLongAt(relOffset, relationship)
          row.setLongAt(startOffset, relationshipIterator.startNodeId())
          row.setLongAt(endOffset, relationshipIterator.endNodeId())
        }

        override def writeRow(row: CypherRow, cursor: RelationshipCursor): Unit = {
          row.setLongAt(relOffset, cursor.relationshipReference())
          row.setLongAt(startOffset, cursor.sourceNodeReference())
          row.setLongAt(endOffset, cursor.targetNodeReference())
        }

        override def readFromStore(cursor: RelationshipIndexCursor): Boolean = cursor.readFromStore()
      }
    case (Some(relOffset), None, Some(endOffset)) =>
      new RelationshipWriter {
        override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
          row.setLongAt(relOffset, rel)
          row.setLongAt(endOffset, endNode)
        }

        override def writeRow(row: CypherRow, relationship: Long, relationshipIterator: RelationshipIterator): Unit = {
          row.setLongAt(relOffset, relationship)
          row.setLongAt(endOffset, relationshipIterator.endNodeId())
        }

        override def writeRow(row: CypherRow, cursor: RelationshipCursor): Unit = {
          row.setLongAt(relOffset, cursor.relationshipReference())
          row.setLongAt(endOffset, cursor.targetNodeReference())
        }

        override def readFromStore(cursor: RelationshipIndexCursor): Boolean = cursor.readFromStore()
      }
    case (Some(relOffset), Some(startOffset), None) =>
      new RelationshipWriter {
        override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
          row.setLongAt(relOffset, rel)
          row.setLongAt(startOffset, startNode)
        }

        override def writeRow(row: CypherRow, relationship: Long, relationshipIterator: RelationshipIterator): Unit = {
          row.setLongAt(relOffset, relationship)
          row.setLongAt(startOffset, relationshipIterator.startNodeId())
        }

        override def writeRow(row: CypherRow, cursor: RelationshipCursor): Unit = {
          row.setLongAt(relOffset, cursor.relationshipReference())
          row.setLongAt(startOffset, cursor.sourceNodeReference())
        }

        override def readFromStore(cursor: RelationshipIndexCursor): Boolean = cursor.readFromStore()
      }
    case (Some(relOffset), None, None) =>
      new RelationshipWriter {
        override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
          row.setLongAt(relOffset, rel)
        }

        override def writeRow(row: CypherRow, relationship: Long, relationshipIterator: RelationshipIterator): Unit = {
          row.setLongAt(relOffset, relationship)
        }

        override def writeRow(row: CypherRow, cursor: RelationshipCursor): Unit = {
          row.setLongAt(relOffset, cursor.relationshipReference())
        }

        override def readFromStore(cursor: RelationshipIndexCursor): Boolean = true
      }
    case (None, Some(startOffset), Some(endOffset)) =>
      new RelationshipWriter {
        override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
          row.setLongAt(startOffset, startNode)
          row.setLongAt(endOffset, endNode)
        }

        override def writeRow(row: CypherRow, relationship: Long, relationshipIterator: RelationshipIterator): Unit = {
          row.setLongAt(startOffset, relationshipIterator.startNodeId())
          row.setLongAt(endOffset, relationshipIterator.endNodeId())
        }

        override def writeRow(row: CypherRow, cursor: RelationshipCursor): Unit = {
          row.setLongAt(startOffset, cursor.sourceNodeReference())
          row.setLongAt(endOffset, cursor.targetNodeReference())
        }

        override def readFromStore(cursor: RelationshipIndexCursor): Boolean = cursor.readFromStore()
      }
    case (None, None, Some(endOffset)) =>
      new RelationshipWriter {
        override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
          row.setLongAt(endOffset, endNode)
        }

        override def writeRow(row: CypherRow, relationship: Long, relationshipIterator: RelationshipIterator): Unit = {
          row.setLongAt(endOffset, relationshipIterator.endNodeId())
        }

        override def writeRow(row: CypherRow, cursor: RelationshipCursor): Unit = {
          row.setLongAt(endOffset, cursor.targetNodeReference())
        }

        override def readFromStore(cursor: RelationshipIndexCursor): Boolean = cursor.readFromStore()
      }
    case (None, Some(startOffset), None) =>
      new RelationshipWriter {
        override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
          row.setLongAt(startOffset, startNode)
        }

        override def writeRow(row: CypherRow, relationship: Long, relationshipIterator: RelationshipIterator): Unit = {
          row.setLongAt(startOffset, relationshipIterator.startNodeId())
        }

        override def writeRow(row: CypherRow, cursor: RelationshipCursor): Unit = {
          row.setLongAt(startOffset, cursor.sourceNodeReference())
        }

        override def readFromStore(cursor: RelationshipIndexCursor): Boolean = cursor.readFromStore()
      }
    case (None, None, None) => RelationshipWriter.DoNothing
  }
}
