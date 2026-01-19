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

import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.logical.plans.TraversalPathMode
import org.neo4j.cypher.internal.physicalplanning.Slot
import org.neo4j.cypher.internal.physicalplanning.SlotConfiguration
import org.neo4j.cypher.internal.physicalplanning.SlotConfigurationUtils.makeGetPrimitiveNodeFromSlotFunctionFor
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.TraversalContainer
import org.neo4j.cypher.internal.runtime.interpreted.pipes.Pipe
import org.neo4j.cypher.internal.runtime.interpreted.pipes.PipeWithSource
import org.neo4j.cypher.internal.runtime.interpreted.pipes.QueryState
import org.neo4j.cypher.internal.runtime.interpreted.pipes.RelationshipTypes
import org.neo4j.cypher.internal.runtime.interpreted.pipes.TraversalPredicates
import org.neo4j.cypher.internal.runtime.interpreted.pipes.VarLengthExpandIterator
import org.neo4j.cypher.internal.runtime.slotted.SlottedRow
import org.neo4j.cypher.internal.runtime.slotted.helpers.NullChecker.entityIsNull
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.values.virtual.VirtualNodeValue
import org.neo4j.values.virtual.VirtualValues

case class VarLengthExpandSlottedPipe(
  source: Pipe,
  fromSlot: Slot,
  maybeRelOffset: Option[Int],
  maybeToSlot: Option[Slot],
  dir: SemanticDirection,
  projectedDir: SemanticDirection,
  types: RelationshipTypes,
  min: Int,
  maxDepth: Option[Int],
  shouldExpandAll: Boolean,
  slots: SlotConfiguration,
  predicates: TraversalPredicates,
  argumentSize: SlotConfiguration.Size,
  traversalPathMode: TraversalPathMode
)(val id: Id = Id.INVALID_ID) extends PipeWithSource(source) {
  private val getFromNodeFunction = makeGetPrimitiveNodeFromSlotFunctionFor(fromSlot, throwOnTypeError = false)

  private val getToNodeFunction =
    if (shouldExpandAll) {
      null
    } // We only need this getter in the ExpandInto case
    else {
      makeGetPrimitiveNodeFromSlotFunctionFor(maybeToSlot.get, throwOnTypeError = false)
    }

  private def newRow(inputRow: CypherRow) = {
    val resultRow = SlottedRow(slots)
    resultRow.copyFrom(inputRow, argumentSize.nLongs, argumentSize.nReferences)
    resultRow
  }

  private val writer = (maybeRelOffset, maybeToSlot, shouldExpandAll) match {
    case (Some(r), Some(n), true) =>
      (resultRow: CypherRow, rels: TraversalContainer, toNode: Long) =>
        resultRow.setLongAt(n.offset, toNode)
        resultRow.setRefAt(r, rels.relationshipsAsList)
        resultRow

    case (Some(r), to, expandAll) if to.isEmpty || !expandAll =>
      (resultRow: CypherRow, rels: TraversalContainer, _: Long) =>
        resultRow.setRefAt(r, rels.relationshipsAsList)
        resultRow

    case (None, Some(n), true) =>
      (resultRow: CypherRow, _: TraversalContainer, toNode: Long) =>
        resultRow.setLongAt(n.offset, toNode)
        resultRow
    case _ => (resultRow: CypherRow, _: TraversalContainer, _: Long) => resultRow
  }

  private def varLengthExpand(
    node: VirtualNodeValue,
    state: QueryState,
    row: CypherRow
  ): ClosingIterator[(VirtualNodeValue, TraversalContainer)] = {
    val memoryTracker = state.memoryTrackerForOperatorProvider.memoryTrackerForOperator(id.x)
    VarLengthExpandIterator(maybeRelOffset.isDefined, dir, projectedDir, types, traversalPathMode, predicates)
      .varLengthExpand(node, state, maxDepth, row, memoryTracker)
  }

  protected def internalCreateResults(
    input: ClosingIterator[CypherRow],
    state: QueryState
  ): ClosingIterator[CypherRow] = {
    input.flatMap {
      inputRow =>
        val fromNode = getFromNodeFunction.applyAsLong(inputRow)
        if (entityIsNull(fromNode)) {
          ClosingIterator.empty
        } else {
          // Ensure that the start-node also adheres to the node predicate
          if (predicates.filterNode(inputRow, state, state.query.nodeById(fromNode))) {

            val paths: ClosingIterator[(VirtualNodeValue, TraversalContainer)] =
              varLengthExpand(VirtualValues.node(fromNode), state, inputRow)
            paths collect {
              case (toNode: VirtualNodeValue, rels) if rels.size >= min && isToNodeValid(inputRow, toNode) =>
                writer(newRow(inputRow), rels, toNode.id())
            }
          } else {
            ClosingIterator.empty
          }
        }
    }
  }

  private def isToNodeValid(row: CypherRow, node: VirtualNodeValue): Boolean =
    shouldExpandAll || getToNodeFunction.applyAsLong(row) == node.id()
}
