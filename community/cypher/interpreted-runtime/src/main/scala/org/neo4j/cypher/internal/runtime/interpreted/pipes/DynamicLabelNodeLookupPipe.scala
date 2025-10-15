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
import org.neo4j.cypher.internal.runtime.ReadWriteRow
import org.neo4j.cypher.internal.runtime.interpreted.TransactionBoundQueryContext.PrimitiveCursorIterator
import org.neo4j.cypher.internal.runtime.interpreted.TransactionBoundQueryContext.ReferenceCursorIterator
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.IntersectionNodeByLabelsScanPipe.intersectionIterator
import org.neo4j.cypher.internal.runtime.interpreted.pipes.UnionNodeByLabelsScanPipe.unionIterator
import org.neo4j.cypher.internal.runtime.makeValueNeoSafe
import org.neo4j.cypher.internal.util.SeqSupport.RichSeq
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.operations.CypherFunctions
import org.neo4j.internal.kernel.api.NodeCursor
import org.neo4j.internal.kernel.api.PropertyCursor
import org.neo4j.internal.kernel.api.PropertyIndexQuery
import org.neo4j.internal.kernel.api.Read
import org.neo4j.internal.schema.IndexDescriptor
import org.neo4j.kernel.impl.newapi.CursorPredicates
import org.neo4j.token.api.TokenConstants.NO_TOKEN
import org.neo4j.values.AnyValue
import org.neo4j.values.virtual.VirtualValues

import java.util.Comparator

import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.math.Ordering.comparatorToOrdering

case class DynamicLabelNodeLookupPipe(
  ident: String,
  labelExpr: Expression,
  operator: DynamicElement.SetOperator,
  propertyExpressions: Map[PropertyKeyToken, Expression]
)(val id: Id = Id.INVALID_ID) extends Pipe {

  protected def internalCreateResults(state: QueryState): ClosingIterator[CypherRow] = {
    val context = state.newRowWithArgument(rowFactory)
    val propertyLookups = DynamicLabelNodeLookupBase.mapPropertyLookups(propertyExpressions, context, state)
    DynamicLabelNodeLookupIterator(state, labelExpr.apply(context, state), propertyLookups, operator)
      .toIterator(n => rowFactory.copyWith(context, ident, VirtualValues.node(n)))
  }
}

abstract class DynamicLabelNodeLookupBase[A](state: QueryState) {
  protected def propertyFilter(iterator: A, propertyExprs: Array[PropertyIndexQuery]): A
  protected def labelFilter(iterator: A, labels: Array[Int]): A
  protected def seek(index: IndexDescriptor, properties: Seq[PropertyIndexQuery.ExactPredicate]): A
  protected def allNodes: A
  protected def empty: A
  protected def allLabels(labels: Array[Int]): A
  protected def anyLabel(labels: Array[Int]): A
  protected def getIndexComparator: Comparator[IndexDescriptor]

  private def findIndicesForLabel(
    labelId: Int,
    predicates: Seq[PropertyIndexQuery]
  ): Iterator[IndexDescriptor] = {
    predicates.orderedSubsets.flatMap { predicateSubset =>
      val indices = state.query.indexReferences(labelId, EntityType.NODE, predicateSubset.map(_.propertyKeyId): _*)
      indices.asScala.filter {
        index => predicateSubset.forall(p => index.getCapability.isQuerySupported(p.`type`(), p.valueCategory()))
      }
    }
  }

  private def findIndicesForLabels(
    labelIds: Seq[Int],
    predicates: Seq[PropertyIndexQuery]
  ): Iterator[IndexDescriptor] = {
    labelIds.iterator.flatMap(findIndicesForLabel(_, predicates))
  }

  private def labelScan(labels: Array[Int], operator: DynamicElement.SetOperator): A = {
    operator match {
      case All if labels.isEmpty => allNodes
      case Any if labels.isEmpty => empty
      case All                   => allLabels(labels)
      case Any                   => anyLabel(labels)
    }
  }

  def getNodes(
    labelExpr: AnyValue,
    operator: DynamicElement.SetOperator,
    propertyConstraints: Array[PropertyIndexQuery.ExactPredicate]
  ): A = {
    val labels =
      CypherFunctions.getDynamicLabels(labelExpr)
        .map(state.query.nodeLabel)

    val iter = (labels, operator) match {
      case (Array(), _) =>
        None
      case _ if propertyConstraints.isEmpty =>
        Some(labelScan(labels, operator))
      case (labels, All) if labels.contains(NO_TOKEN) =>
        None
      case (labels, Any) if labels.forall(_ == NO_TOKEN) =>
        None

      case (labels, All) =>
        val propPredicates = propertyConstraints.toSeq
        findIndicesForLabels(labels, propPredicates).maxOption(comparatorToOrdering(getIndexComparator))
          .map { index =>
            val otherLabels = labels.filterNot(_ == index.schema().getLabelId)
            val (indexProps, otherProps) =
              propPredicates.partition(p => index.schema().getPropertyIds.contains(p.propertyKeyId()))

            (otherLabels.nonEmpty, otherProps.nonEmpty) match {
              case (true, true) =>
                labelFilter(
                  propertyFilter(seek(index, indexProps), otherProps.toArray),
                  otherLabels
                )
              case (true, false) =>
                labelFilter(seek(index, indexProps), otherLabels)
              case (false, true) =>
                propertyFilter(seek(index, indexProps), otherProps.toArray)
              case (false, false) =>
                seek(index, indexProps)
            }
          }

      // for Any we can't union results of index value seeks because they don't have guaranteed order, so only support
      // single label
      case (Array(label), Any) =>
        val propPredicates = propertyConstraints.toSeq
        findIndicesForLabel(label, propPredicates).maxOption(comparatorToOrdering(getIndexComparator))
          .map { index =>
            val (indexProps, otherProps) =
              propPredicates.partition(p => index.schema().getPropertyIds.contains(p.propertyKeyId()))

            if (otherProps.nonEmpty) {
              propertyFilter(seek(index, indexProps), otherProps.toArray)
            } else {
              seek(index, indexProps)
            }
          }

      case _ =>
        None
    }

    iter.getOrElse {
      if (propertyConstraints.nonEmpty) {
        propertyFilter(labelScan(labels, operator), propertyConstraints.asInstanceOf[Array[PropertyIndexQuery]])
      } else {
        labelScan(labels, operator)
      }
    }
  }
}

object DynamicLabelNodeLookupBase {

  def mapPropertyLookups(
    propExpressions: Map[PropertyKeyToken, Expression],
    context: ReadWriteRow,
    state: QueryState
  ): Array[PropertyIndexQuery.ExactPredicate] = {
    propExpressions.view
      .map { case (prop, expr) =>
        PropertyIndexQuery.exact(prop.nameId.id, makeValueNeoSafe(expr(context, state)))
      }
      .toArray
  }
}

case class DynamicLabelNodeLookupIterator(
  state: QueryState,
  labelExpression: AnyValue,
  propertyConstraints: Array[PropertyIndexQuery.ExactPredicate],
  operator: DynamicElement.SetOperator
) extends DynamicLabelNodeLookupBase[ClosingLongIterator](state) {

  private lazy val nodeIterator = getNodes(labelExpression, operator, propertyConstraints)

  override protected def getIndexComparator: Comparator[IndexDescriptor] =
    state.indexComparatorFactory.createComparator(state.query.dataRead, state.query.transactionalContext.schemaRead)

  override protected def propertyFilter(
    iterator: ClosingLongIterator,
    propertyExprs: Array[PropertyIndexQuery]
  ): ClosingLongIterator = {
    new PropertyFilteringNodeIterator(
      iterator,
      state.query.dataRead,
      state.cursors.nodeCursor,
      state.cursors.propertyCursor,
      propertyExprs
    )
  }

  override protected def labelFilter(iterator: ClosingLongIterator, labels: Array[Int]): ClosingLongIterator =
    new LabelFilteringNodeIterator(
      iterator,
      state.query.dataRead,
      state.cursors.nodeCursor,
      labels
    )

  override protected def seek(
    index: IndexDescriptor,
    properties: Seq[PropertyIndexQuery.ExactPredicate]
  ): ReferenceCursorIterator = {
    val cursor = state.query.nodeIndexSeek(
      state.query.dataRead.indexReadSession(index),
      needsValues = false, // we already know the values because we only support ExactPredicate
      IndexOrderNone,
      properties
    )
    new ReferenceCursorIterator(cursor)
  }

  override protected def allNodes: ClosingLongIterator = state.query.nodeReadOps.all

  override protected def allLabels(labels: Array[Int]): ClosingLongIterator =
    intersectionIterator(state.query, labels, IndexOrderNone, state.nodeLabelTokenReadSession.get)

  override protected def anyLabel(labels: Array[Int]): ClosingLongIterator =
    unionIterator(state.query, labels, IndexOrderNone, state.nodeLabelTokenReadSession.get)

  override protected def empty: ClosingLongIterator = ClosingLongIterator.empty

  def toIterator(rowMapper: Long => CypherRow): ClosingIterator[CypherRow] = nodeIterator.mapToObj(rowMapper)
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
