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

import org.neo4j.collection.trackable.HeapTrackingCollections
import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.logical.plans.TraversalPathMode
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.TraversalContainer
import org.neo4j.cypher.internal.runtime.interpreted.pipes.VarLengthExpandIterator.projectBackwards
import org.neo4j.memory.EmptyMemoryTracker
import org.neo4j.memory.MemoryTracker
import org.neo4j.values.virtual.VirtualNodeValue
import org.neo4j.values.virtual.VirtualValues

case class VarLengthExpandIterator(
  storeRelationships: Boolean,
  dir: SemanticDirection,
  projectedDir: SemanticDirection,
  types: RelationshipTypes,
  traversalPathMode: TraversalPathMode,
  filteringStep: TraversalPredicates = TraversalPredicates.NONE
) {

  def varLengthExpand(
    node: VirtualNodeValue,
    state: QueryState,
    maxDepth: Option[Int],
    row: CypherRow,
    memoryTracker: MemoryTracker
  ): ClosingIterator[(VirtualNodeValue, TraversalContainer)] = {
    val stack = HeapTrackingCollections.newArrayDeque[(VirtualNodeValue, TraversalContainer)](
      EmptyMemoryTracker.INSTANCE
    )
    stack.push((
      node,
      TraversalContainer.withStartNode(node.id(), memoryTracker, traversalPathMode, storeRelationships)
    ))

    new ClosingIterator[(VirtualNodeValue, TraversalContainer)] {
      def next(): (VirtualNodeValue, TraversalContainer) = {
        val (node, traversalContainer) = stack.pop()
        if (
          traversalContainer.size < maxDepth.getOrElse(Int.MaxValue) &&
          filteringStep.filterNode(row, state, node)
        ) {
          val relationships = state.query.getRelationshipsForIds(node.id(), dir, types.types(state.query))

          // relationships get immediately exhausted. Therefore we do not need a ClosingIterator here.
          while (relationships.hasNext) {
            val rel = VirtualValues.relationship(
              relationships.next(),
              relationships.startNodeId(),
              relationships.endNodeId(),
              relationships.typeId()
            )
            val otherNode = VirtualValues.node(relationships.otherNodeId(node.id()))

            if (
              filteringStep.filterNode(row, state, otherNode)
              && filteringStep.filterRelationship(row, state, rel, node, otherNode)
            ) {
              if (traversalContainer.canAdd(otherNode, rel)) {
                stack.push((otherNode, traversalContainer.append(otherNode, rel)))
              }
            }
          }
        }

        val projectedContainer = {
          if (projectBackwards(dir, projectedDir)) {
            traversalContainer.withReversedRelationships
          } else {
            traversalContainer
          }
        }
        traversalContainer.close()
        (node, projectedContainer)
      }

      def innerHasNext: Boolean = !stack.isEmpty

      override protected[this] def closeMore(): Unit = stack.close()
    }
  }

}

object VarLengthExpandIterator {

  def projectBackwards(dir: SemanticDirection, projectedDir: SemanticDirection): Boolean =
    if (dir == SemanticDirection.BOTH) {
      projectedDir == SemanticDirection.INCOMING
    } else {
      dir != projectedDir
    }
}
