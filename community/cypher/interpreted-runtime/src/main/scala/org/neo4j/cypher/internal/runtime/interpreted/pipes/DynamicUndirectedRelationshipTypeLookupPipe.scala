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

import org.neo4j.cypher.internal.logical.plans.DynamicElement
import org.neo4j.cypher.internal.logical.plans.DynamicElement.SetOperator
import org.neo4j.cypher.internal.logical.plans.IndexOrder
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.ClosingLongIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.ReadableRow
import org.neo4j.cypher.internal.runtime.RelationshipIterator
import org.neo4j.cypher.internal.runtime.interpreted.TransactionBoundQueryContext.BaseRelationshipCursorIterator
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.DirectedAllRelationshipsScanPipe.allRelationshipsIterator
import org.neo4j.cypher.internal.runtime.interpreted.pipes.DirectedUnionRelationshipTypesScanPipe.unionTypeIterator
import org.neo4j.cypher.internal.runtime.interpreted.pipes.DynamicUndirectedRelationshipTypeLookupPipe.getIterator
import org.neo4j.cypher.internal.runtime.interpreted.pipes.UndirectedRelationshipTypeScanPipe.UndirectedIterator
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.operations.CypherFunctions
import org.neo4j.cypher.operations.CypherFunctions.GetSingleDynamicTypeResult

case class DynamicUndirectedRelationshipTypeLookupPipe(
  ident: Option[String],
  fromNode: Option[String],
  typeExpr: Expression,
  toNode: Option[String],
  operator: DynamicElement.SetOperator,
  indexOrder: IndexOrder
)(val id: Id = Id.INVALID_ID) extends Pipe {

  protected def internalCreateResults(state: QueryState): ClosingIterator[CypherRow] = {
    val baseContext = state.newRowWithArgument(rowFactory)
    val relIterator = getIterator(state, baseContext, typeExpr, operator, indexOrder)
    new UndirectedIterator(relIterator, ident, fromNode, toNode, rowFactory, state)
  }
}

object DynamicUndirectedRelationshipTypeLookupPipe {

  def getIterator(
    state: QueryState,
    baseContext: ReadableRow,
    typeExpr: Expression,
    operator: SetOperator,
    indexOrder: IndexOrder
  ): ClosingLongIterator with RelationshipIterator = {
    val typeValue = typeExpr.apply(baseContext, state)

    operator match {
      case DynamicElement.All =>
        val typeName = CypherFunctions.getSingleDynamicType(typeValue, state)
        typeName match {
          case _: CypherFunctions.GetSingleDynamicTypeResult.ConflictingDynamicTypes =>
            BaseRelationshipCursorIterator.EMPTY
          case _: CypherFunctions.GetSingleDynamicTypeResult.EmptyDynamicTypeList =>
            allRelationshipsIterator(state.query)
          case dynamicType: GetSingleDynamicTypeResult.SingleDynamicType =>
            state.query.getOptRelTypeId(dynamicType.value()) match {
              case Some(typeId) =>
                state.query.getRelationshipsByType(state.relTypeTokenReadSession.get, typeId, indexOrder)
              case None =>
                BaseRelationshipCursorIterator.EMPTY
            }
        }

      case DynamicElement.Any =>
        val typeNames = CypherFunctions.getDynamicTypes(typeValue)
        unionTypeIterator(
          state,
          typeNames.map(LazyTypeStatic(_)),
          indexOrder,
          state.relTypeTokenReadSession.get
        )
    }
  }
}
