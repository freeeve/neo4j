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
import org.neo4j.cypher.internal.logical.plans.QueryExpression
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.ClosingLongIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.PrimitiveLongHelper
import org.neo4j.cypher.internal.runtime.QueryContext
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.NodeVectorIndexSearchPipe.predicate
import org.neo4j.cypher.internal.runtime.interpreted.pipes.RelationshipVectorIndexSearchPipe.vectorSearchCursor
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.operations.CypherCoercions.validateAndConvertVectorIndexQuery
import org.neo4j.cypher.operations.CypherFunctions
import org.neo4j.internal.kernel.api.IndexReadSession
import org.neo4j.internal.kernel.api.RelationshipValueIndexCursor
import org.neo4j.values.AnyValue
import org.neo4j.values.storable.Values
import org.neo4j.values.storable.Values.NO_VALUE
import org.neo4j.values.virtual.VirtualValues

abstract class RelationshipVectorIndexSearchPipe(
  properties: Array[Int],
  vectorExpression: Expression,
  limitExpression: Expression,
  queryIndexId: Int,
  filterExpression: Option[QueryExpression[Expression]]
) extends Pipe {

  protected def newRow(
    row: CypherRow,
    relationship: Long,
    source: Long,
    target: Long,
    typ: Int,
    score: Float
  ): CypherRow

  protected def iteratorFrom(cursor: RelationshipValueIndexCursor): RelationshipVectorSearchIterator

  protected def internalCreateResults(
    state: QueryState
  ): ClosingIterator[CypherRow] = {
    val incomingRow = state.newRowWithArgument(rowFactory)
    val index = state.queryIndexes(queryIndexId)
    val vector = vectorExpression(incomingRow, state)
    if (vector eq NO_VALUE) {
      ClosingIterator.empty
    } else {
      val cursor = vectorSearchCursor(
        state.query,
        index,
        limitExpression,
        vector,
        filterExpression,
        properties,
        incomingRow,
        state
      )

      val iterator = iteratorFrom(cursor)
      PrimitiveLongHelper.map(
        iterator,
        _ =>
          newRow(
            state.newRowWithArgument(rowFactory),
            iterator.reference,
            iterator.source,
            iterator.target,
            iterator.typ,
            iterator.score
          )
      )
    }
  }

  class RelationshipVectorSearchIterator(cursor: RelationshipValueIndexCursor) extends ClosingLongIterator {
    private[this] var hasFetchedNext = false
    private[this] var exhausted = false

    def typ: Int = cursor.`type`()

    def score: Float = cursor.score()

    def reference: Long = cursor.reference()

    def source: Long = cursor.sourceNodeReference()

    def target: Long = cursor.targetNodeReference()

    override protected[this] def innerHasNext: Boolean = {
      if (!hasFetchedNext) {
        hasFetchedNext = true
        if (!fetchNext()) {
          exhausted = true
        }
      }
      !exhausted
    }

    override def next(): Long = {
      if (!hasNext) {
        close()
        throw new NoSuchElementException
      }
      hasFetchedNext = false
      cursor.reference()
    }

    override def close(): Unit = {
      cursor.close()
    }

    protected[this] def fetchNext(): Boolean = {
      while (cursor.next() && cursor.readFromStore()) {
        return true
      }
      false
    }
  }

  class UndirectedRelationshipVectorSearchIterator(cursor: RelationshipValueIndexCursor)
      extends RelationshipVectorSearchIterator(cursor) {
    private[this] var emitSibling: Boolean = false

    override protected[this] def fetchNext(): Boolean = {
      if (emitSibling) {
        emitSibling = false
        true
      } else {
        val next = super.fetchNext()
        if (next) {
          emitSibling = cursor.sourceNodeReference() != cursor.targetNodeReference()
        }
        next
      }
    }

    override def source: Long = {
      if (emitSibling) {
        cursor.sourceNodeReference()
      } else {
        cursor.targetNodeReference()
      }
    }

    override def target: Long = {
      if (emitSibling) {
        cursor.targetNodeReference()
      } else {
        cursor.sourceNodeReference()
      }
    }
  }
}

abstract class BaseRelationshipVectorIndexSearchPipe(
  ident: Option[String],
  fromNode: Option[String],
  toNode: Option[String],
  score: Option[String],
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

  private val relationshipWriter: Relationships.RelationshipWriter =
    Relationships.compileRelationshipWriter(ident, fromNode, toNode)

  private[this] val _newRow: (CypherRow, Long, Long, Long, Int, Float) => CypherRow = {
    score match {
      case Some(value) =>
        (incomingRow: CypherRow, relationship: Long, source: Long, target: Long, typ: Int, score: Float) =>
          val row = relationshipWriter.writeRow(
            rowFactory,
            incomingRow,
            VirtualValues.relationship(relationship, source, target, typ),
            VirtualValues.node(source),
            VirtualValues.node(target)
          )
          row.set(value, Values.floatValue(score))
          row
      case None => (incomingRow: CypherRow, relationship: Long, source: Long, target: Long, typ: Int, _: Float) =>
          relationshipWriter.writeRow(
            rowFactory,
            incomingRow,
            VirtualValues.relationship(relationship, source, target, typ),
            VirtualValues.node(source),
            VirtualValues.node(target)
          )
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

case class DirectedRelationshipVectorIndexSearchPipe(
  ident: Option[String],
  fromNode: Option[String],
  toNode: Option[String],
  score: Option[String],
  properties: Array[Int],
  vectorExpression: Expression,
  limitExpression: Expression,
  queryIndexId: Int,
  filterExpression: Option[QueryExpression[Expression]]
)(val id: Id = Id.INVALID_ID)
    extends BaseRelationshipVectorIndexSearchPipe(
      ident,
      fromNode,
      toNode,
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

case class UndirectedRelationshipVectorIndexSearchPipe(
  ident: Option[String],
  fromNode: Option[String],
  toNode: Option[String],
  score: Option[String],
  properties: Array[Int],
  vectorExpression: Expression,
  limitExpression: Expression,
  queryIndexId: Int,
  filterExpression: Option[QueryExpression[Expression]]
)(val id: Id = Id.INVALID_ID)
    extends BaseRelationshipVectorIndexSearchPipe(
      ident,
      fromNode,
      toNode,
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

object RelationshipVectorIndexSearchPipe {

  def vectorSearchCursor(
    query: QueryContext,
    index: IndexReadSession,
    limit: Expression,
    vector: AnyValue,
    filter: Option[QueryExpression[Expression]],
    properties: Array[Int],
    row: CypherRow,
    state: QueryState
  ): RelationshipValueIndexCursor = {
    val l = CypherFunctions.asNonNegativeIntExact(limit(row, state))
    if (l == 0) {
      RelationshipValueIndexCursor.EMPTY
    } else {
      val queries =
        predicate(l, validateAndConvertVectorIndexQuery(index.reference(), vector), filter, properties, row, state)
      if (queries.nonEmpty) {
        query.relationshipIndexSeek(
          index,
          needsValues = false,
          IndexOrderNone,
          queries
        )
      } else {
        RelationshipValueIndexCursor.EMPTY
      }
    }
  }

}
