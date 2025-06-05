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
import org.neo4j.cypher.internal.runtime.IsNoValue
import org.neo4j.cypher.internal.runtime.RelationshipContainer
import org.neo4j.cypher.internal.runtime.interpreted.pipes.VarLengthExpandPipe.projectBackwards
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.operations.CypherTypeValueMapper
import org.neo4j.exceptions.ParameterWrongTypeException
import org.neo4j.memory.EmptyMemoryTracker
import org.neo4j.values.storable.Value
import org.neo4j.values.virtual.VirtualNodeValue
import org.neo4j.values.virtual.VirtualValues

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
      (row: CypherRow, rels: RelationshipContainer, node: VirtualNodeValue) =>
        rowFactory.copyWith(row, relName, rels.asList, toName, node)

    case (None, Some(toName)) =>
      (row: CypherRow, _: RelationshipContainer, node: VirtualNodeValue) =>
        rowFactory.copyWith(row, toName, node)

    case (Some(relName), None) =>
      (row: CypherRow, rels: RelationshipContainer, _: VirtualNodeValue) =>
        rowFactory.copyWith(row, relName, rels.asList)

    case (None, None) =>
      (row: CypherRow, _: RelationshipContainer, _: VirtualNodeValue) =>
        rowFactory.copyWith(row)

  }

  private def varLengthExpand(
    node: VirtualNodeValue,
    state: QueryState,
    maxDepth: Option[Int],
    row: CypherRow
  ): ClosingIterator[(VirtualNodeValue, RelationshipContainer)] = {
    val memoryTracker = state.memoryTrackerForOperatorProvider.memoryTrackerForOperator(id.x)
    val stack = HeapTrackingCollections.newArrayDeque[(VirtualNodeValue, RelationshipContainer)](
      EmptyMemoryTracker.INSTANCE
    )
    stack.push((node, RelationshipContainer.empty(memoryTracker, traversalPathMode, maybeRelName.isDefined)))

    new ClosingIterator[(VirtualNodeValue, RelationshipContainer)] {
      def next(): (VirtualNodeValue, RelationshipContainer) = {
        val (node, rels) = stack.pop()
        if (rels.size < maxDepth.getOrElse(Int.MaxValue) && filteringStep.filterNode(row, state, node)) {
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
            if (filteringStep.filterRelationship(row, state, rel, node, otherNode)) {
              if (rels.canAdd(rel) && filteringStep.filterNode(row, state, otherNode)) {
                stack.push((otherNode, rels.append(rel)))
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
        (node, projectedRels)
      }

      def innerHasNext: Boolean = !stack.isEmpty

      override protected[this] def closeMore(): Unit = stack.close()
    }
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

object VarLengthExpandPipe {

  def projectBackwards(dir: SemanticDirection, projectedDir: SemanticDirection): Boolean =
    if (dir == SemanticDirection.BOTH) {
      projectedDir == SemanticDirection.INCOMING
    } else {
      dir != projectedDir
    }
}
