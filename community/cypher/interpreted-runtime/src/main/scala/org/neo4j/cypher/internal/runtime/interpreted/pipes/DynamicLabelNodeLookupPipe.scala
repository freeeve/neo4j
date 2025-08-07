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
import org.neo4j.cypher.internal.logical.plans.IndexOrderNone
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.ClosingLongIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.PrimitiveLongHelper
import org.neo4j.cypher.internal.runtime.interpreted.TransactionBoundQueryContext.PrimitiveCursorIterator
import org.neo4j.cypher.internal.runtime.interpreted.TransactionBoundQueryContext.ReferenceCursorIterator
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.DynamicLabelNodeLookupPipe.getNodes
import org.neo4j.cypher.internal.runtime.interpreted.pipes.IntersectionNodeByLabelsScanPipe.intersectionIterator
import org.neo4j.cypher.internal.runtime.interpreted.pipes.UnionNodeByLabelsScanPipe.unionIterator
import org.neo4j.cypher.internal.runtime.makeValueNeoSafe
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.operations.CypherFunctions
import org.neo4j.internal.kernel.api.NodeCursor
import org.neo4j.internal.kernel.api.PropertyCursor
import org.neo4j.internal.kernel.api.PropertyIndexQuery
import org.neo4j.internal.kernel.api.PropertyIndexQuery.ExactPredicate
import org.neo4j.internal.kernel.api.Read
import org.neo4j.internal.schema.IndexDescriptor
import org.neo4j.kernel.impl.newapi.CursorPredicates
import org.neo4j.token.api.TokenConstants.NO_TOKEN
import org.neo4j.values.virtual.VirtualValues

case class DynamicLabelNodeLookupPipe(
  ident: String,
  labelExpr: Expression,
  operator: DynamicElement.SetOperator,
  propertyExpressions: Map[PropertyKeyToken, Expression]
)(val id: Id = Id.INVALID_ID) extends Pipe {

  protected def internalCreateResults(state: QueryState): ClosingIterator[CypherRow] = {
    val baseContext = state.newRowWithArgument(rowFactory)
    val nodes = getNodes(labelExpr, operator, baseContext, state, propertyExpressions)
    PrimitiveLongHelper.map(nodes, n => rowFactory.copyWith(baseContext, ident, VirtualValues.node(n)))
  }
}

object DynamicLabelNodeLookupPipe {

  def getNodes(
    labelExpr: Expression,
    operator: DynamicElement.SetOperator,
    context: CypherRow,
    state: QueryState,
    propertyExprs: Map[PropertyKeyToken, Expression]
  ): ClosingLongIterator = {
    val labels =
      CypherFunctions.getDynamicLabels(labelExpr.apply(context, state))
        .map(state.query.nodeLabel)

    def filterProperties(
      iterator: ClosingLongIterator,
      propertyPredicates: Iterator[ExactPredicate]
    ): ClosingLongIterator =
      new PropertyFilteringNodeIterator(
        iterator,
        state.query.dataRead,
        state.cursors.nodeCursor,
        state.cursors.propertyCursor,
        propertyPredicates.toArray[PropertyIndexQuery]
      )

    def filterHasLabels(iterator: ClosingLongIterator, labels: Array[Int]): ClosingLongIterator =
      new LabelFilteringNodeIterator(
        iterator,
        state.query.dataRead,
        state.cursors.nodeCursor,
        labels
      )

    def labelScan = {
      operator match {
        case All if labels.isEmpty => state.query.nodeReadOps.all
        case Any if labels.isEmpty => ClosingLongIterator.empty

        case Any =>
          unionIterator(state.query, labels, IndexOrderNone, state.nodeLabelTokenReadSession.get)

        case All =>
          intersectionIterator(state.query, labels, IndexOrderNone, state.nodeLabelTokenReadSession.get)
      }
    }

    def predicate(propertyKey: PropertyKeyToken, expression: Expression) = {
      val value = expression.apply(context, state)
      PropertyIndexQuery.exact(propertyKey.nameId.id, makeValueNeoSafe(value))
    }

    def seek(index: IndexDescriptor, properties: Seq[PropertyIndexQuery]) = {
      val cursor = state.query.nodeIndexSeek(
        state.query.dataRead.indexReadSession(index),
        needsValues = false, // we already know the values because we only support ExactPredicate
        IndexOrderNone,
        properties
      )
      new ReferenceCursorIterator(cursor)
    }

    def findCompoundIndex(labelId: Int, propertyKeys: Seq[PropertyKeyToken]): Option[IndexDescriptor] = {
      if (labelId == NO_TOKEN) {
        None
      } else {
        val iter = state.query.indexReferences(labelId, EntityType.NODE, propertyKeys.map(_.nameId.id): _*)
        if (iter.hasNext) {
          Some(iter.next())
        } else {
          None
        }
      }
    }

    def findIndex(labelId: Int, propertyKey: PropertyKeyToken): Option[IndexDescriptor] = {
      if (labelId == NO_TOKEN) {
        None
      } else {
        val iter = state.query.indexReferences(labelId, EntityType.NODE, propertyKey.nameId.id)
        if (iter.hasNext) {
          Some(iter.next())
        } else {
          None
        }
      }
    }

    val iter = if (propertyExprs.isEmpty) {
      Some(labelScan)
    } else if (labels.length == 1 && propertyExprs.size == 1) {
      // SINGLE LABEL, SINGLE PROPERTY
      val (propertyKey, expr) = propertyExprs.head
      val label = labels.head

      findIndex(label, propertyKey)
        .map(seek(_, Seq(predicate(propertyKey, expr))))
    } else if (labels.length > 1 && !labels.contains(NO_TOKEN) && operator == All && propertyExprs.size == 1) {
      // ALL MULTIPLE LABELS, SINGLE PROPERTY
      val (propertyKey, expr) = propertyExprs.head

      // naive implementation: use the first index that is found for any of the labels and then apply label filter
      labels.iterator
        .flatMap(label =>
          findIndex(label, propertyKey)
            .map(index =>
              filterHasLabels(
                seek(index, Seq(predicate(propertyKey, expr))),
                labels.filterNot(_ == label)
              )
            )
        ).nextOption()
    } else if (propertyExprs.size > 1 && labels.length == 1) {
      // SINGLE LABEL, MULTIPLE PROPERTIES

      // look for a compound index first
      findCompoundIndex(labels.head, propertyExprs.keys.toSeq)
        .map(seek(_, propertyExprs.map((predicate _).tupled).toSeq))
        .orElse {
          // if no compound index, use the first index for any of the properties and then apply property filter
          propertyExprs.iterator
            .flatMap { case (propertyKey, expr) =>
              findIndex(labels.head, propertyKey)
                .map { index =>
                  val otherPredicates = propertyExprs
                    .iterator
                    .filterNot(_._1 == propertyKey)
                    .map((predicate _).tupled)

                  filterProperties(
                    seek(index, Seq(predicate(propertyKey, expr))),
                    otherPredicates
                  )
                }
            }.nextOption()
        }
    } else None

    iter.getOrElse {
      val indexQueries = propertyExprs
        .iterator
        .map((predicate _).tupled)

      filterProperties(labelScan, indexQueries)
    }
  }

  abstract class FilteringNodeIterator(
    nodeIdIterator: ClosingLongIterator,
    read: Read,
    nodeCursor: NodeCursor
  ) extends PrimitiveCursorIterator {

    protected def test(nodeCursor: NodeCursor): Boolean

    protected def fetchNext(): Long = {
      while (nodeIdIterator.hasNext) {
        val id = nodeIdIterator.next()
        read.singleNode(id, nodeCursor)

        // The node may have been deleted in this transaction, so check that it has not been
        if (nodeCursor.next() && test(nodeCursor)) {
          return id
        }
      }

      -1L
    }

    def close(): Unit = nodeIdIterator.close()
  }

  class PropertyFilteringNodeIterator(
    scan: ClosingLongIterator,
    read: Read,
    nodeCursor: NodeCursor,
    propCursor: PropertyCursor,
    indexQueries: Array[PropertyIndexQuery]
  ) extends FilteringNodeIterator(scan, read, nodeCursor) {

    protected def test(nodeCursor: NodeCursor): Boolean = {
      nodeCursor.properties(propCursor)
      CursorPredicates.propertiesMatch(propCursor, indexQueries)
    }
  }

  class LabelFilteringNodeIterator(
    scan: ClosingLongIterator,
    read: Read,
    nodeCursor: NodeCursor,
    labels: Seq[Int]
  ) extends FilteringNodeIterator(scan, read, nodeCursor) {

    protected def test(nodeCursor: NodeCursor): Boolean = {
      labels.forall(nodeCursor.hasLabel)
    }
  }
}
