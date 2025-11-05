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

import org.neo4j.cypher.internal.logical.plans.IndexOrderNone
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.QueryContext
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.NodeVectorIndexSearchPipe.vectorSearchCursor
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.operations.CypherCoercions.validateAndConvertVectorIndexQuery
import org.neo4j.cypher.operations.CypherFunctions
import org.neo4j.internal.kernel.api.IndexReadSession
import org.neo4j.internal.kernel.api.NodeValueIndexCursor
import org.neo4j.internal.kernel.api.PropertyIndexQuery
import org.neo4j.values.AnyValue
import org.neo4j.values.storable.Values.NO_VALUE
import org.neo4j.values.storable.Values.floatValue
import org.neo4j.values.virtual.VirtualValues

abstract class BaseNodeVectorIndexSearchPipe(
  vectorExpression: Expression,
  limitExpression: Expression,
  queryIndexId: Int
) extends Pipe {

  protected def newRow(row: CypherRow, cursor: NodeValueIndexCursor): CypherRow

  protected def internalCreateResults(
    state: QueryState
  ): ClosingIterator[CypherRow] = {
    val query = state.query
    val incomingRow = state.newRowWithArgument(rowFactory)
    val index = state.queryIndexes(queryIndexId)
    val vector = vectorExpression(incomingRow, state)
    if (vector eq NO_VALUE) {
      ClosingIterator.empty
    } else {
      new ClosingIterator[CypherRow]() {
        private[this] val cursor = vectorSearchCursor(query, index, limitExpression(incomingRow, state), vector)
        private[this] var _hasNext: java.lang.Boolean = _
        override protected[this] def closeMore(): Unit = cursor.close()
        override def next(): CypherRow = {
          if (hasNext) {
            val r = newRow(incomingRow, cursor)
            _hasNext = null
            r
          } else {
            throw new NoSuchElementException
          }
        }
        override protected[this] def innerHasNext: Boolean = {
          if (_hasNext == null) {
            _hasNext = cursor.next()
          }
          _hasNext
        }
      }
    }
  }
}

case class NodeVectorIndexSearchPipe(
  node: String,
  score: Option[String],
  vectorExpression: Expression,
  limitExpression: Expression,
  queryIndexId: Int
)(val id: Id = Id.INVALID_ID)
    extends BaseNodeVectorIndexSearchPipe(vectorExpression, limitExpression, queryIndexId) {

  private[this] val _newRow: (CypherRow, NodeValueIndexCursor) => CypherRow = score match {
    case Some(value) => (incomingRow: CypherRow, cursor: NodeValueIndexCursor) =>
        rowFactory.copyWith(
          incomingRow,
          node,
          VirtualValues.node(cursor.nodeReference()),
          value,
          floatValue(cursor.score())
        )
    case None => (incomingRow: CypherRow, cursor: NodeValueIndexCursor) =>
        rowFactory.copyWith(incomingRow, node, VirtualValues.node(cursor.nodeReference()))
  }

  override protected def newRow(row: CypherRow, cursor: NodeValueIndexCursor): CypherRow = _newRow(row, cursor)
}

object NodeVectorIndexSearchPipe {

  def vectorSearchCursor(
    query: QueryContext,
    index: IndexReadSession,
    limit: AnyValue,
    vector: AnyValue
  ): NodeValueIndexCursor = {
    val l = CypherFunctions.asNonNegativeIntExact(limit)
    if (l == 0) {
      NodeValueIndexCursor.EMPTY
    } else {
      query.nodeIndexSeek(
        index,
        needsValues = false,
        IndexOrderNone,
        Seq(PropertyIndexQuery.nearestNeighbors(
          l,
          validateAndConvertVectorIndexQuery(index.reference(), vector)
        ))
      )
    }
  }
}
