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
package org.neo4j.cypher.internal.runtime.interpreted.pipes

import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.values.virtual.VirtualNodeValue
import org.neo4j.values.virtual.VirtualRelationshipValue

object Relationships {

  sealed trait RelationshipWriter {

    def writeRow(
      rowFactory: CypherRowFactory,
      row: CypherRow,
      rel: VirtualRelationshipValue,
      startNode: VirtualNodeValue,
      endNode: VirtualNodeValue
    ): CypherRow
  }

  def compileRelationshipWriter(
    maybeRelationship: Option[String],
    maybeFromNode: Option[String],
    maybeToNode: Option[String]
  ): RelationshipWriter = (maybeRelationship, maybeFromNode, maybeToNode) match {
    case (Some(relName), Some(startName), Some(endName)) =>
      new RelationshipWriter {
        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          rel: VirtualRelationshipValue,
          startNode: VirtualNodeValue,
          endNode: VirtualNodeValue
        ): CypherRow =
          rowFactory.copyWith(
            row,
            relName,
            rel,
            startName,
            startNode,
            endName,
            endNode
          )
      }
    case (Some(relName), None, Some(endName)) =>
      new RelationshipWriter {
        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          rel: VirtualRelationshipValue,
          startNode: VirtualNodeValue,
          endNode: VirtualNodeValue
        ): CypherRow =
          rowFactory.copyWith(
            row,
            relName,
            rel,
            endName,
            endNode
          )
      }
    case (Some(relName), Some(startName), None) =>
      new RelationshipWriter {
        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          rel: VirtualRelationshipValue,
          startNode: VirtualNodeValue,
          endNode: VirtualNodeValue
        ): CypherRow =
          rowFactory.copyWith(
            row,
            relName,
            rel,
            startName,
            startNode
          )
      }
    case (Some(relName), None, None) =>
      new RelationshipWriter {
        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          rel: VirtualRelationshipValue,
          startNode: VirtualNodeValue,
          endNode: VirtualNodeValue
        ): CypherRow =
          rowFactory.copyWith(
            row,
            relName,
            rel
          )
      }

    case (None, Some(startName), Some(endName)) =>
      new RelationshipWriter {
        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          rel: VirtualRelationshipValue,
          startNode: VirtualNodeValue,
          endNode: VirtualNodeValue
        ): CypherRow =
          rowFactory.copyWith(
            row,
            startName,
            startNode,
            endName,
            endNode
          )
      }
    case (None, None, Some(endName)) =>
      new RelationshipWriter {
        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          rel: VirtualRelationshipValue,
          startNode: VirtualNodeValue,
          endNode: VirtualNodeValue
        ): CypherRow =
          rowFactory.copyWith(
            row,
            endName,
            endNode
          )
      }
    case (None, Some(startName), None) =>
      new RelationshipWriter {
        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          rel: VirtualRelationshipValue,
          startNode: VirtualNodeValue,
          endNode: VirtualNodeValue
        ): CypherRow =
          rowFactory.copyWith(
            row,
            startName,
            startNode
          )
      }
    case (None, None, None) => new RelationshipWriter {

        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          rel: VirtualRelationshipValue,
          startNode: VirtualNodeValue,
          endNode: VirtualNodeValue
        ): CypherRow = row
      }
  }
}
