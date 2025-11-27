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

import org.neo4j.cypher.internal.logical.plans.AllQueryExpression
import org.neo4j.cypher.internal.logical.plans.CompositeQueryExpression
import org.neo4j.cypher.internal.logical.plans.IndexOrderNone
import org.neo4j.cypher.internal.logical.plans.QueryExpression
import org.neo4j.cypher.internal.logical.plans.RangeQueryExpression
import org.neo4j.cypher.internal.logical.plans.SingleQueryExpression
import org.neo4j.cypher.internal.macros.AssertMacros.checkOnlyWhenAssertionsAreEnabled
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.QueryContext
import org.neo4j.cypher.internal.runtime.ReadableRow
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.InequalitySeekRangeExpression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.EntityIndexSeeker.computeIndexRangeQuery
import org.neo4j.cypher.internal.runtime.interpreted.pipes.NodeVectorIndexSearchPipe.vectorSearchCursor
import org.neo4j.cypher.internal.runtime.makeValueNeoSafe
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.operations.CypherCoercions.validateAndConvertVectorIndexQuery
import org.neo4j.cypher.operations.CypherFunctions
import org.neo4j.exceptions.InternalException
import org.neo4j.internal.kernel.api.IndexReadSession
import org.neo4j.internal.kernel.api.NodeValueIndexCursor
import org.neo4j.internal.kernel.api.PropertyIndexQuery
import org.neo4j.values.AnyValue
import org.neo4j.values.storable.Values.NO_VALUE
import org.neo4j.values.storable.Values.floatValue
import org.neo4j.values.virtual.VirtualValues

abstract class BaseNodeVectorIndexSearchPipe(
  properties: Array[Int],
  vectorExpression: Expression,
  limitExpression: Expression,
  queryIndexId: Int,
  filterExpression: Option[QueryExpression[Expression]]
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
        private[this] val cursor = vectorSearchCursor(
          query,
          index,
          limitExpression,
          vector,
          filterExpression,
          properties,
          incomingRow,
          state
        )

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
  properties: Array[Int],
  vectorExpression: Expression,
  limitExpression: Expression,
  queryIndexId: Int,
  filterExpression: Option[QueryExpression[Expression]]
)(val id: Id = Id.INVALID_ID)
    extends BaseNodeVectorIndexSearchPipe(
      properties,
      vectorExpression,
      limitExpression,
      queryIndexId,
      filterExpression
    ) {

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
    limit: Expression,
    vector: AnyValue,
    filter: Option[QueryExpression[Expression]],
    properties: Array[Int],
    row: CypherRow,
    state: QueryState
  ): NodeValueIndexCursor = {
    val l = CypherFunctions.asNonNegativeIntExact(limit(row, state))
    if (l == 0) {
      NodeValueIndexCursor.EMPTY
    } else {
      val queries =
        predicate(l, validateAndConvertVectorIndexQuery(index.reference(), vector), filter, properties, row, state)
      if (queries.nonEmpty) {
        query.nodeIndexSeek(
          index,
          needsValues = false,
          IndexOrderNone,
          queries
        )
      } else {
        NodeValueIndexCursor.EMPTY
      }
    }
  }

  def predicate(
    limit: Int,
    vector: Array[Float],
    filter: Option[QueryExpression[Expression]],
    properties: Array[Int],
    row: ReadableRow,
    state: QueryState
  ): Array[PropertyIndexQuery] = {
    val nearestPredicate: PropertyIndexQuery.NearestNeighborsPredicate =
      PropertyIndexQuery.nearestNeighbors(limit, vector)

    filter match {
      case Some(SingleQueryExpression(expression)) =>
        checkOnlyWhenAssertionsAreEnabled(properties.length == 2)
        makeValueNeoSafe.safeOrEmpty(expression(row, state)) match {
          case Some(value) => Array(nearestPredicate, PropertyIndexQuery.exact(properties(1), value))
          case None        =>
            // empty means no possible results
            Array.empty
        }

      case Some(RangeQueryExpression(rangeWrapper)) =>
        checkOnlyWhenAssertionsAreEnabled(properties.length == 2)
        rangeWrapper match {
          case InequalitySeekRangeExpression(innerRange) =>
            val inner = computeIndexRangeQuery(
              innerRange.flatMapBounds(expr => makeValueNeoSafe.safeOrEmpty(expr(row, state))),
              properties(1)
            )
            // empty means no possible results
            if (inner.isEmpty) {
              Array.empty
            } else {
              nearestPredicate +: inner
            }

          case notSupported =>
            throw InternalException.internalError(
              this.getClass.getSimpleName,
              s"$notSupported not supported in vector searches"
            )
        }

      case Some(CompositeQueryExpression(inner)) =>
        require(inner.length == properties.length - 1)
        compositePredicate(nearestPredicate, inner, properties, row, state)

      case Some(notSupported) =>
        throw InternalException.internalError(
          this.getClass.getSimpleName,
          s"$notSupported not supported in vector searches"
        )
      case None => Array(nearestPredicate)
    }
  }

  private def compositePredicate(
    nearestPredicate: PropertyIndexQuery.NearestNeighborsPredicate,
    inner: Seq[QueryExpression[Expression]],
    properties: Array[Int],
    row: ReadableRow,
    state: QueryState
  ): Array[PropertyIndexQuery] = {

    val predicates = new Array[PropertyIndexQuery](properties.length)
    predicates(0) = nearestPredicate
    var i = 1
    while (i < properties.length) {
      inner(i - 1) match {
        case SingleQueryExpression(expression) =>
          makeValueNeoSafe.safeOrEmpty(expression(row, state)) match {
            case Some(value) =>
              predicates(i) = PropertyIndexQuery.exact(properties(i), value)
            case None =>
              return Array.empty
          }

        case RangeQueryExpression(rangeWrapper) =>
          rangeWrapper match {
            case InequalitySeekRangeExpression(innerRange) =>
              val inner = computeIndexRangeQuery(
                innerRange.flatMapBounds(expr => makeValueNeoSafe.safeOrEmpty(expr(row, state))),
                properties(i)
              )
              // empty means no possible results
              if (inner.isEmpty) {
                return Array.empty
              } else if (inner.length == 1) {
                predicates(i) = inner.head
              } else {
                throw InternalException.internalError(
                  this.getClass.getSimpleName,
                  s"$rangeWrapper not supported in vector searches"
                )
              }

            case notSupported =>
              throw InternalException.internalError(
                this.getClass.getSimpleName,
                s"$notSupported not supported in vector searches"
              )
          }
        case AllQueryExpression() =>
          predicates(i) = PropertyIndexQuery.all(properties(i))

        case notSupported =>
          throw InternalException.internalError(
            this.getClass.getSimpleName,
            s"$notSupported not supported in vector searches"
          )
      }
      i += 1
    }
    predicates
  }
}
