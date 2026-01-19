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

import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.logical.plans.TraversalPathMode
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.IsNoValue
import org.neo4j.cypher.internal.runtime.TraversalContainer
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.operations.CypherTypeValueMapper
import org.neo4j.exceptions.ParameterWrongTypeException
import org.neo4j.values.storable.Value
import org.neo4j.values.virtual.VirtualNodeValue

case class VarLengthExpandPipe(
  source: Pipe,
  fromName: String,
  maybeRelName: Option[String],
  maybeToName: Option[String],
  dir: SemanticDirection,
  projectedDir: SemanticDirection,
  types: RelationshipTypes,
  min: Int,
  max: Option[Int],
  nodeInScope: Boolean,
  traversalPathMode: TraversalPathMode,
  filteringStep: TraversalPredicates = TraversalPredicates.NONE
)(val id: Id = Id.INVALID_ID) extends PipeWithSource(source) {

  private val writer = (maybeRelName, maybeToName) match {
    case (Some(relName), Some(toName)) =>
      (row: CypherRow, rels: TraversalContainer, node: VirtualNodeValue) =>
        rowFactory.copyWith(row, relName, rels.relationshipsAsList, toName, node)

    case (None, Some(toName)) =>
      (row: CypherRow, _: TraversalContainer, node: VirtualNodeValue) =>
        rowFactory.copyWith(row, toName, node)

    case (Some(relName), None) =>
      (row: CypherRow, rels: TraversalContainer, _: VirtualNodeValue) =>
        rowFactory.copyWith(row, relName, rels.relationshipsAsList)

    case (None, None) =>
      (row: CypherRow, _: TraversalContainer, _: VirtualNodeValue) =>
        rowFactory.copyWith(row)

  }

  private def varLengthExpand(
    node: VirtualNodeValue,
    state: QueryState,
    maxDepth: Option[Int],
    row: CypherRow
  ): ClosingIterator[(VirtualNodeValue, TraversalContainer)] = {
    val memoryTracker = state.memoryTrackerForOperatorProvider.memoryTrackerForOperator(id.x)
    VarLengthExpandIterator(maybeRelName.isDefined, dir, projectedDir, types, traversalPathMode, filteringStep)
      .varLengthExpand(node, state, maxDepth, row, memoryTracker)
  }

  protected def internalCreateResults(
    input: ClosingIterator[CypherRow],
    state: QueryState
  ): ClosingIterator[CypherRow] = {
    def expand(row: CypherRow, n: VirtualNodeValue): ClosingIterator[CypherRow] = {
      if (filteringStep.filterNode(row, state, n)) {
        val paths = varLengthExpand(n, state, max, row)
        paths.collect {
          case (node, rels) if rels.size >= min && isToNodeValid(row, node) =>
            writer(row, rels, node)
        }
      } else {
        ClosingIterator.empty
      }
    }

    input.flatMap {
      row =>
        {
          row.getByName(fromName) match {
            case node: VirtualNodeValue =>
              expand(row, node)

            case IsNoValue() => ClosingIterator.empty
            case value: Value =>
              throw ParameterWrongTypeException.expectedNodeFoundInstead(
                value.toString,
                value.prettyPrint(),
                CypherTypeValueMapper.valueType(value)
              )
            case value =>
              throw ParameterWrongTypeException.expectedNodeFoundInstead(
                value.toString,
                value.toString,
                CypherTypeValueMapper.valueType(value)
              )
          }
        }
    }
  }

  private def isToNodeValid(row: CypherRow, node: VirtualNodeValue) =
    !nodeInScope || {
      maybeToName match {
        case Some(toName) =>
          row.getByName(toName) match {
            case toNode: VirtualNodeValue =>
              toNode.id == node.id
            case _ =>
              false
          }
        case None => false
      }
    }
}
