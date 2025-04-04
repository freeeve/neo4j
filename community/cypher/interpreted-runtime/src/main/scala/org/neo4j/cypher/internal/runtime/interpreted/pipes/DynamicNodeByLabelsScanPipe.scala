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

import org.neo4j.cypher.internal.logical.plans.DynamicLabel
import org.neo4j.cypher.internal.logical.plans.DynamicLabel.All
import org.neo4j.cypher.internal.logical.plans.DynamicLabel.Any
import org.neo4j.cypher.internal.logical.plans.IndexOrder
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.ClosingLongIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.PrimitiveLongHelper
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.DynamicNodeByLabelsScanPipe.getNodes
import org.neo4j.cypher.internal.runtime.interpreted.pipes.IntersectionNodeByLabelsScanPipe.intersectionIterator
import org.neo4j.cypher.internal.runtime.interpreted.pipes.LazyLabel.UNKNOWN
import org.neo4j.cypher.internal.runtime.interpreted.pipes.UnionNodeByLabelsScanPipe.unionIterator
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.operations.CypherFunctions
import org.neo4j.values.virtual.VirtualValues

case class DynamicNodeByLabelsScanPipe(
  ident: String,
  labelExpr: Expression,
  operator: DynamicLabel.SetOperator,
  indexOrder: IndexOrder
)(val id: Id = Id.INVALID_ID) extends Pipe {

  protected def internalCreateResults(state: QueryState): ClosingIterator[CypherRow] = {
    val baseContext = state.newRowWithArgument(rowFactory)
    val nodes = getNodes(labelExpr, operator, indexOrder, baseContext, state)
    PrimitiveLongHelper.map(nodes, n => rowFactory.copyWith(baseContext, ident, VirtualValues.node(n)))
  }
}

object DynamicNodeByLabelsScanPipe {

  def getNodes(
    labelExpr: Expression,
    operator: DynamicLabel.SetOperator,
    indexOrder: IndexOrder,
    context: CypherRow,
    state: QueryState
  ): ClosingLongIterator = {
    val labels =
      CypherFunctions.getDynamicLabels(labelExpr.apply(context, state))
        .map(LazyLabel(_))

    (labels, operator) match {
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
  }
}
