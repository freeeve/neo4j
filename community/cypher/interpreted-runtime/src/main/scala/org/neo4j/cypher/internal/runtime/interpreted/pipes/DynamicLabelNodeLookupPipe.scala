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

import org.neo4j.common.EntityType
import org.neo4j.cypher.internal.expressions.PropertyKeyToken
import org.neo4j.cypher.internal.logical.plans.DynamicElement
import org.neo4j.cypher.internal.logical.plans.DynamicElement.All
import org.neo4j.cypher.internal.logical.plans.DynamicElement.Any
import org.neo4j.cypher.internal.logical.plans.IndexOrder
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.ClosingLongIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.PrimitiveLongHelper
import org.neo4j.cypher.internal.runtime.interpreted.TransactionBoundQueryContext.PrimitiveCursorIterator
import org.neo4j.cypher.internal.runtime.interpreted.TransactionBoundQueryContext.ReferenceCursorIterator
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.DynamicLabelNodeLookupPipe.getNodes
import org.neo4j.cypher.internal.runtime.interpreted.pipes.IntersectionNodeByLabelsScanPipe.intersectionIterator
import org.neo4j.cypher.internal.runtime.interpreted.pipes.LazyLabel.UNKNOWN
import org.neo4j.cypher.internal.runtime.interpreted.pipes.UnionNodeByLabelsScanPipe.unionIterator
import org.neo4j.cypher.internal.runtime.makeValueNeoSafe
import org.neo4j.cypher.internal.util.PropertyKeyId
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.operations.CypherFunctions
import org.neo4j.internal.kernel.api.NodeCursor
import org.neo4j.internal.kernel.api.PropertyCursor
import org.neo4j.internal.kernel.api.PropertyIndexQuery
import org.neo4j.internal.kernel.api.Read
import org.neo4j.internal.schema.IndexDescriptor
import org.neo4j.kernel.impl.newapi.CursorPredicates
import org.neo4j.values.virtual.VirtualValues

case class DynamicLabelNodeLookupPipe(
  ident: String,
  labelExpr: Expression,
  operator: DynamicElement.SetOperator,
  indexOrder: IndexOrder,
  propertyExpressions: Map[PropertyKeyToken, Expression]
)(val id: Id = Id.INVALID_ID) extends Pipe {

  protected def internalCreateResults(state: QueryState): ClosingIterator[CypherRow] = {
    val baseContext = state.newRowWithArgument(rowFactory)
    val nodes = getNodes(labelExpr, operator, indexOrder, baseContext, state, propertyExpressions)
    PrimitiveLongHelper.map(nodes, n => rowFactory.copyWith(baseContext, ident, VirtualValues.node(n)))
  }
}

object DynamicLabelNodeLookupPipe {

  def getNodes(
    labelExpr: Expression,
    operator: DynamicElement.SetOperator,
    indexOrder: IndexOrder,
    context: CypherRow,
    state: QueryState,
    propertyExprs: Map[PropertyKeyToken, Expression]
  ): ClosingLongIterator = {
    val labels =
      CypherFunctions.getDynamicLabels(labelExpr.apply(context, state))
        .map(LazyLabel(_)) // TODO: we don't need to use LazyLabel here, just do the id lookup immediately

    def labelScan =
      (labels, operator) match {
        case (Array(), Any) => ClosingLongIterator.empty
        case (Array(), All) => state.query.nodeReadOps.all

        case (Array(label), _) =>
          val id = label.getId(state.query)
          if (id != UNKNOWN) {
            state.query.getNodesByLabel(state.nodeLabelTokenReadSession.get, id, indexOrder)
          } else {
            ClosingLongIterator.empty
          }

        case (_, Any) =>
          unionIterator(state.query, labels, indexOrder, state.nodeLabelTokenReadSession.get)

        case (_, All) =>
          intersectionIterator(state.query, labels, indexOrder, state.nodeLabelTokenReadSession.get)
      }

    def filteredScan = {
      val scan = labelScan

      val indexQueries = propertyExprs
        .map { case (propertyKey, expr) => predicate(propertyKey, expr) }
        .toArray[PropertyIndexQuery]

      new PropertyFilterIterator(
        scan,
        state.query.dataRead,
        state.cursors.nodeCursor,
        state.cursors.propertyCursor,
        indexQueries
      )
    }

    def predicate(propertyKey: PropertyKeyToken, expression: Expression) = {
      val value = expression.apply(context, state)
      PropertyIndexQuery.exact(propertyKey.nameId.id, makeValueNeoSafe(value))
    }

    if (propertyExprs.isEmpty) {
      labelScan
    } else if (propertyExprs.size > 1 || labels.length != 1) {
      filteredScan
    } else {
      val (propertyKey, expr) = propertyExprs.head
      val label = labels.head

      findIndex(state, label, propertyKey.nameId) match {
        case Some(index) =>
          val cursor = state.query.nodeIndexSeek(
            state.query.dataRead.indexReadSession(index),
            needsValues = false, // TODO: get this from the plan?
            indexOrder,
            Seq(predicate(propertyKey, expr))
          )
          new ReferenceCursorIterator(cursor)

        case None => filteredScan
      }
    }
  }

  def findIndex(state: QueryState, label: LazyLabel, propertyKey: PropertyKeyId): Option[IndexDescriptor] = {
    val labelId = label.getId(state.query)

    if (labelId == LazyLabel.UNKNOWN) {
      None
    } else {
      val iter = state.query.indexReferences(labelId, EntityType.NODE, propertyKey.id)
      if (iter.hasNext) {
        Some(iter.next())
      } else {
        None
      }
    }
  }

  class PropertyFilterIterator(
    scan: ClosingLongIterator,
    read: Read,
    nodeCursor: NodeCursor,
    propCursor: PropertyCursor,
    indexQueries: Array[PropertyIndexQuery]
  ) extends PrimitiveCursorIterator {

    protected def fetchNext(): Long = {
      while (scan.hasNext) {
        val id = scan.next()
        read.singleNode(id, nodeCursor)

        // The node may have been deleted in this transaction, so check that it has not been
        if (nodeCursor.next()) {
          nodeCursor.properties(propCursor)

          if (CursorPredicates.propertiesMatch(propCursor, indexQueries)) {
            return id
          }
        }
      }

      -1L
    }

    def close(): Unit = scan.close()
  }
}
