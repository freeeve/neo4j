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

import org.neo4j.cypher.internal.logical.plans.QueryExpression
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.RelationshipVectorIndexSearchPipe
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.internal.kernel.api.RelationshipValueIndexCursor
import org.neo4j.values.storable.Values

abstract class BaseRelationshipVectorIndexSearchSlottedPipe(
  relOffset: Option[Int],
  fromOffset: Option[Int],
  toOffset: Option[Int],
  score: Option[Int],
  properties: Array[Int],
  vectorExpression: Expression,
  limitExpression: Expression,
  queryIndexId: Int,
  filterExpression: Option[QueryExpression[Expression]]
) extends RelationshipVectorIndexSearchPipe(
      properties,
      vectorExpression,
      limitExpression,
      queryIndexId,
      filterExpression
    ) {

  private val relationshipWriter = Relationships.compileRelationshipWriter(relOffset, fromOffset, toOffset)

  private[this] val _newRow: (CypherRow, Long, Long, Long, Int, Float) => CypherRow = {
    score match {
      case Some(value) =>
        (incomingRow: CypherRow, relationship: Long, source: Long, target: Long, typ: Int, score: Float) =>
          relationshipWriter.writeRow(incomingRow, relationship, source, target)
          incomingRow.setRefAt(value, Values.floatValue(score))
          incomingRow
      case None => (incomingRow: CypherRow, relationship: Long, source: Long, target: Long, typ: Int, _: Float) =>
          relationshipWriter.writeRow(incomingRow, relationship, source, target)
          incomingRow
    }
  }

  override protected def newRow(
    row: CypherRow,
    relationship: Long,
    source: Long,
    target: Long,
    typ: Int,
    score: Float
  ): CypherRow = _newRow(row, relationship, source, target, typ, score)
}

case class DirectedRelationshipVectorIndexSearchSlottedPipe(
  relOffset: Option[Int],
  fromOffset: Option[Int],
  toOffset: Option[Int],
  score: Option[Int],
  properties: Array[Int],
  vectorExpression: Expression,
  limitExpression: Expression,
  queryIndexId: Int,
  filterExpression: Option[QueryExpression[Expression]]
)(val id: Id = Id.INVALID_ID) extends BaseRelationshipVectorIndexSearchSlottedPipe(
      relOffset,
      fromOffset,
      toOffset,
      score,
      properties,
      vectorExpression,
      limitExpression,
      queryIndexId,
      filterExpression
    ) {

  override protected def iteratorFrom(cursor: RelationshipValueIndexCursor): RelationshipVectorSearchIterator =
    new RelationshipVectorSearchIterator(cursor)
}

case class UndirectedRelationshipVectorIndexSearchSlottedPipe(
  relOffset: Option[Int],
  fromOffset: Option[Int],
  toOffset: Option[Int],
  score: Option[Int],
  properties: Array[Int],
  vectorExpression: Expression,
  limitExpression: Expression,
  queryIndexId: Int,
  filterExpression: Option[QueryExpression[Expression]]
)(val id: Id = Id.INVALID_ID) extends BaseRelationshipVectorIndexSearchSlottedPipe(
      relOffset,
      fromOffset,
      toOffset,
      score,
      properties,
      vectorExpression,
      limitExpression,
      queryIndexId,
      filterExpression
    ) {

  override protected def iteratorFrom(cursor: RelationshipValueIndexCursor): RelationshipVectorSearchIterator =
    new UndirectedRelationshipVectorSearchIterator(cursor)
}
