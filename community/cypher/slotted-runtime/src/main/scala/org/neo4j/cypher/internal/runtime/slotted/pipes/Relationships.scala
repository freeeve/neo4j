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

    case class WriteAll(relOffset: Int, startOffset: Int, endOffset: Int) extends RelationshipWriter {

      override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
        row.setLongAt(relOffset, rel)
        row.setLongAt(startOffset, startNode)
        row.setLongAt(endOffset, endNode)
      }
    }

    case class WriteStart(relOffset: Int, startOffset: Int) extends RelationshipWriter {

      override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
        row.setLongAt(relOffset, rel)
        row.setLongAt(startOffset, startNode)
      }
    }

    case class WriteEnd(relOffset: Int, endOffset: Int) extends RelationshipWriter {

      override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
        row.setLongAt(relOffset, rel)
        row.setLongAt(endOffset, endNode)
      }
    }

    case class WriteRelationshipOnly(relOffset: Int) extends RelationshipWriter {

      override def writeRow(row: CypherRow, rel: Long, startNode: Long, endNode: Long): Unit = {
        row.setLongAt(relOffset, rel)
      }
    }
  }

  def compileRelationshipWriter(
    relOffset: Int,
    maybeStartNode: Option[Int],
    maybeEndNode: Option[Int]
  ): RelationshipWriter = (maybeStartNode, maybeEndNode) match {
    case (Some(start), Some(end)) => RelationshipWriter.WriteAll(relOffset, start, end)
    case (None, Some(end))        => RelationshipWriter.WriteEnd(relOffset, end)
    case (Some(start), None)      => RelationshipWriter.WriteStart(relOffset, start)
    case (None, None)             => RelationshipWriter.WriteRelationshipOnly(relOffset)
  }
}
