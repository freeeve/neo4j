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

object Relationships {

  sealed trait RelationshipWriter {
    def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit
  }

  private object RelationshipWriter {

    case object DoNothing extends RelationshipWriter {
      override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {}
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
      }
    case (Some(relOffset), None, Some(endOffset)) =>
      new RelationshipWriter {
        override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
          row.setLongAt(relOffset, rel)
          row.setLongAt(endOffset, endNode)
        }
      }
    case (Some(relOffset), Some(startOffset), None) =>
      new RelationshipWriter {
        override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
          row.setLongAt(relOffset, rel)
          row.setLongAt(startOffset, startNode)
        }
      }
    case (Some(relOffset), None, None) =>
      new RelationshipWriter {
        override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
          row.setLongAt(relOffset, rel)
        }
      }
    case (None, Some(startOffset), Some(endOffset)) =>
      new RelationshipWriter {
        override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
          row.setLongAt(startOffset, startNode)
          row.setLongAt(endOffset, endNode)
        }
      }
    case (None, None, Some(endOffset)) =>
      new RelationshipWriter {
        override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
          row.setLongAt(endOffset, endNode)
        }
      }
    case (None, Some(startOffset), None) =>
      new RelationshipWriter {
        override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
          row.setLongAt(startOffset, startNode)
        }
      }
    case (None, None, None) => RelationshipWriter.DoNothing
  }
}
