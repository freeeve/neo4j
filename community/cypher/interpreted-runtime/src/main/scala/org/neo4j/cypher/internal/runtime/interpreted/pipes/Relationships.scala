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
import org.neo4j.cypher.internal.runtime.RelationshipIterator
import org.neo4j.internal.kernel.api.RelationshipCursor
import org.neo4j.values.virtual.VirtualNodeValue
import org.neo4j.values.virtual.VirtualRelationshipValue
import org.neo4j.values.virtual.VirtualValues

object Relationships {

  sealed trait RelationshipWriter {

    def writeRow(
      rowFactory: CypherRowFactory,
      row: CypherRow,
      rel: VirtualRelationshipValue,
      startNode: VirtualNodeValue,
      endNode: VirtualNodeValue
    ): CypherRow

    def writeRow(
      rowFactory: CypherRowFactory,
      row: CypherRow,
      relationship: Long,
      relationshipIterator: RelationshipIterator
    ): CypherRow

    def writeRow(
      rowFactory: CypherRowFactory,
      row: CypherRow,
      cursor: RelationshipCursor
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

        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          relationship: Long,
          relationshipIterator: RelationshipIterator
        ): CypherRow = {
          val startNode = relationshipIterator.startNodeId()
          val endNode = relationshipIterator.endNodeId()
          rowFactory.copyWith(
            row,
            relName,
            VirtualValues.relationship(relationship, startNode, endNode, relationshipIterator.typeId()),
            startName,
            VirtualValues.node(startNode),
            endName,
            VirtualValues.node(endNode)
          )
        }

        override def writeRow(rowFactory: CypherRowFactory, row: CypherRow, cursor: RelationshipCursor): CypherRow = {
          val startNode = cursor.sourceNodeReference()
          val endNode = cursor.targetNodeReference()
          rowFactory.copyWith(
            row,
            relName,
            VirtualValues.relationship(cursor.reference(), startNode, endNode, cursor.`type`()),
            startName,
            VirtualValues.node(startNode),
            endName,
            VirtualValues.node(endNode)
          )
        }
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

        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          relationship: Long,
          relationshipIterator: RelationshipIterator
        ): CypherRow = {
          rowFactory.copyWith(
            row,
            relName,
            VirtualValues.relationship(relationship),
            endName,
            VirtualValues.node(relationshipIterator.endNodeId())
          )
        }

        override def writeRow(rowFactory: CypherRowFactory, row: CypherRow, cursor: RelationshipCursor): CypherRow = {
          rowFactory.copyWith(
            row,
            relName,
            VirtualValues.relationship(cursor.reference()),
            endName,
            VirtualValues.node(cursor.targetNodeReference())
          )
        }
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

        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          relationship: Long,
          relationshipIterator: RelationshipIterator
        ): CypherRow = {
          rowFactory.copyWith(
            row,
            relName,
            VirtualValues.relationship(relationship),
            startName,
            VirtualValues.node(relationshipIterator.startNodeId())
          )
        }

        override def writeRow(rowFactory: CypherRowFactory, row: CypherRow, cursor: RelationshipCursor): CypherRow = {
          rowFactory.copyWith(
            row,
            relName,
            VirtualValues.relationship(cursor.reference()),
            startName,
            VirtualValues.node(cursor.sourceNodeReference())
          )
        }
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

        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          relationship: Long,
          relationshipIterator: RelationshipIterator
        ): CypherRow = {
          rowFactory.copyWith(
            row,
            relName,
            VirtualValues.relationship(relationship)
          )
        }

        override def writeRow(rowFactory: CypherRowFactory, row: CypherRow, cursor: RelationshipCursor): CypherRow = {
          rowFactory.copyWith(
            row,
            relName,
            VirtualValues.relationship(cursor.reference())
          )
        }
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

        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          relationship: Long,
          relationshipIterator: RelationshipIterator
        ): CypherRow = {
          rowFactory.copyWith(
            row,
            startName,
            VirtualValues.node(relationshipIterator.startNodeId()),
            endName,
            VirtualValues.node(relationshipIterator.endNodeId())
          )
        }
        override def writeRow(rowFactory: CypherRowFactory, row: CypherRow, cursor: RelationshipCursor): CypherRow = {
          rowFactory.copyWith(
            row,
            startName,
            VirtualValues.node(cursor.sourceNodeReference()),
            endName,
            VirtualValues.node(cursor.targetNodeReference())
          )
        }
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

        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          relationship: Long,
          relationshipIterator: RelationshipIterator
        ): CypherRow = {
          rowFactory.copyWith(
            row,
            endName,
            VirtualValues.node(relationshipIterator.endNodeId())
          )
        }

        override def writeRow(rowFactory: CypherRowFactory, row: CypherRow, cursor: RelationshipCursor): CypherRow = {
          rowFactory.copyWith(
            row,
            endName,
            VirtualValues.node(cursor.targetNodeReference())
          )
        }
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

        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          relationship: Long,
          relationshipIterator: RelationshipIterator
        ): CypherRow = {
          rowFactory.copyWith(
            row,
            startName,
            VirtualValues.node(relationshipIterator.startNodeId())
          )
        }

        override def writeRow(rowFactory: CypherRowFactory, row: CypherRow, cursor: RelationshipCursor): CypherRow = {
          rowFactory.copyWith(
            row,
            startName,
            VirtualValues.node(cursor.sourceNodeReference())
          )
        }
      }
    case (None, None, None) => new RelationshipWriter {

        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          rel: VirtualRelationshipValue,
          startNode: VirtualNodeValue,
          endNode: VirtualNodeValue
        ): CypherRow = row

        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          relationship: Long,
          relationshipIterator: RelationshipIterator
        ): CypherRow = row

        override def writeRow(rowFactory: CypherRowFactory, row: CypherRow, cursor: RelationshipCursor): CypherRow = row

      }
  }
}
