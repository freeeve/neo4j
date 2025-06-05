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

import org.neo4j.collection.trackable.HeapTrackingCollections
import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.logical.plans.TraversalPathMode
import org.neo4j.cypher.internal.physicalplanning.Slot
import org.neo4j.cypher.internal.physicalplanning.SlotConfiguration
import org.neo4j.cypher.internal.physicalplanning.SlotConfigurationUtils.makeGetPrimitiveNodeFromSlotFunctionFor
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.RelationshipContainer
import org.neo4j.cypher.internal.runtime.RelationshipIterator
import org.neo4j.cypher.internal.runtime.interpreted.pipes.Pipe
import org.neo4j.cypher.internal.runtime.interpreted.pipes.PipeWithSource
import org.neo4j.cypher.internal.runtime.interpreted.pipes.QueryState
import org.neo4j.cypher.internal.runtime.interpreted.pipes.RelationshipTypes
import org.neo4j.cypher.internal.runtime.interpreted.pipes.TraversalPredicates
import org.neo4j.cypher.internal.runtime.interpreted.pipes.VarLengthExpandPipe.projectBackwards
import org.neo4j.cypher.internal.runtime.slotted.SlottedRow
import org.neo4j.cypher.internal.runtime.slotted.helpers.NullChecker.entityIsNull
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.values.virtual.VirtualValues

/**
 * On predicates... to communicate the tested entity to the predicate, expressions
 * variable slots have been allocated. The offsets of these slots are `temp*Offset`.
 * If no predicate exists the offset will be `SlottedPipeMapper.NO_PREDICATE_OFFSET`
 */
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
  type LNode = Long

  // ===========================================================================
  // Compile-time initializations
  // ===========================================================================
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
      (resultRow: CypherRow, rels: RelationshipContainer, toNode: Long) =>
        resultRow.setLongAt(n.offset, toNode)
        resultRow.setRefAt(r, rels.asList)
        resultRow

    case (Some(r), to, expandAll) if to.isEmpty || !expandAll =>
      (resultRow: CypherRow, rels: RelationshipContainer, _: Long) =>
        resultRow.setRefAt(r, rels.asList)
        resultRow

    case (None, Some(n), true) =>
      (resultRow: CypherRow, _: RelationshipContainer, toNode: Long) =>
        resultRow.setLongAt(n.offset, toNode)
        resultRow
    case _ => (resultRow: CypherRow, _: RelationshipContainer, _: Long) => resultRow
  }

  // ===========================================================================
  // Runtime code
  // ===========================================================================

  private def varLengthExpand(
    node: LNode,
    state: QueryState,
    row: CypherRow
  ): ClosingIterator[(LNode, RelationshipContainer)] = {
    val memoryTracker = state.memoryTrackerForOperatorProvider.memoryTrackerForOperator(id.x)
    val stackOfNodes = HeapTrackingCollections.newLongStack(memoryTracker)
    val stackOfRelContainers = HeapTrackingCollections.newArrayDeque[RelationshipContainer](memoryTracker)
    stackOfNodes.push(node)
    stackOfRelContainers.push(RelationshipContainer.empty(memoryTracker, traversalPathMode, maybeRelOffset.isDefined))

    new ClosingIterator[(LNode, RelationshipContainer)] {
      override def next(): (LNode, RelationshipContainer) = {
        val fromNode = stackOfNodes.pop()
        val rels: RelationshipContainer = stackOfRelContainers.pop()
        if (rels.size < maxDepth.getOrElse(Int.MaxValue)) {
          val relationships: RelationshipIterator =
            state.query.getRelationshipsForIds(fromNode, dir, types.types(state.query))

          // relationships get immediately exhausted. Therefore we do not need a ClosingIterator here.
          while (relationships.hasNext) {
            val relId = relationships.next()
            if (rels.canAdd(relId)) {
              // Before expanding, check that both the relationship and node in question fulfil the predicate
              val rel = state.query.relationshipById(
                relId,
                relationships.startNodeId(),
                relationships.endNodeId(),
                relationships.typeId()
              )
              val otherNode = VirtualValues.node(relationships.otherNodeId(fromNode))

              if (
                predicates.filterNode(row, state, otherNode) &&
                predicates.filterRelationship(row, state, rel, VirtualValues.node(fromNode), otherNode)
              ) {
                stackOfNodes.push(relationships.otherNodeId(fromNode))
                stackOfRelContainers.push(rels.append(VirtualValues.relationship(
                  relId,
                  relationships.startNodeId(),
                  relationships.endNodeId(),
                  relationships.typeId()
                )))
              }
            }
          }
        }

        val projectedRels = {
          if (projectBackwards(dir, projectedDir)) {
            rels.reverse
          } else {
            rels
          }
        }
        rels.close()
        (fromNode, projectedRels)
      }

      override def innerHasNext: Boolean = !stackOfNodes.isEmpty

      override protected[this] def closeMore(): Unit = {
        stackOfNodes.close()
        stackOfRelContainers.close()
      }
    }
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

            val paths: ClosingIterator[(LNode, RelationshipContainer)] = varLengthExpand(fromNode, state, inputRow)
            paths collect {
              case (toNode: LNode, rels) if rels.size >= min && isToNodeValid(inputRow, toNode) =>
                writer(newRow(inputRow), rels, toNode)
            }
          } else {
            ClosingIterator.empty
          }
        }
    }
  }

  private def isToNodeValid(row: CypherRow, node: LNode): Boolean =
    shouldExpandAll || getToNodeFunction.applyAsLong(row) == node
}
