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

import org.neo4j.cypher.internal.logical.plans.EntityFilterQueryExpression
import org.neo4j.cypher.internal.logical.plans.QueryExpression
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.BaseNodeVectorIndexSearchPipe
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.internal.kernel.api.NodeValueIndexCursor
import org.neo4j.values.storable.Values

case class NodeVectorIndexSearchSlottedPipe(
  offset: Int,
  score: Option[Int],
  properties: Array[Int],
  vectorExpression: Expression,
  limitExpression: Expression,
  queryIndexId: Int,
  entityFilterExpression: EntityFilterQueryExpression[Expression],
  propertyFilterExpression: Option[QueryExpression[Expression]]
)(val id: Id = Id.INVALID_ID)
    extends BaseNodeVectorIndexSearchPipe(
      properties,
      vectorExpression,
      limitExpression,
      queryIndexId,
      entityFilterExpression,
      propertyFilterExpression
    ) {

  private[this] val _newRow: (CypherRow, NodeValueIndexCursor) => CypherRow = score match {
    case Some(value) => (row: CypherRow, cursor: NodeValueIndexCursor) =>
        row.setLongAt(offset, cursor.nodeReference())
        row.setRefAt(value, Values.floatValue(cursor.score()))
        row
    case None => (row: CypherRow, cursor: NodeValueIndexCursor) =>
        row.setLongAt(offset, cursor.nodeReference())
        row
  }

  override protected def newRow(row: CypherRow, cursor: NodeValueIndexCursor): CypherRow = _newRow(row, cursor)
}
