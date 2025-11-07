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
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.ClosingIterator.ScalaSeqAsClosingIterator
import org.neo4j.cypher.internal.runtime.ClosingLongIterator.emptyClosingRelationshipIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.IsNoValue
import org.neo4j.cypher.internal.runtime.interpreted.commands.convert.DirectionConverter.toGraphDb
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.ExpandIntoPipe.getRowNode
import org.neo4j.cypher.internal.runtime.interpreted.pipes.ExpandIntoPipe.traceRelationshipSelectionCursor
import org.neo4j.cypher.internal.runtime.iterators.RelationshipCursorIterator
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.exceptions.InternalException
import org.neo4j.internal.kernel.api.helpers.CachingExpandInto
import org.neo4j.values.storable.Values
import org.neo4j.values.virtual.VirtualNodeValue
import org.neo4j.values.virtual.VirtualValues

import scala.collection.mutable.ListBuffer

case class OptionalExpandIntoPipe(
  source: Pipe,
  fromName: String,
  maybeRelName: Option[String],
  toName: String,
  dir: SemanticDirection,
  types: RelationshipTypes,
  predicate: Option[Expression]
)(val id: Id = Id.INVALID_ID)
    extends PipeWithSource(source) {

  private val kernelDirection = toGraphDb(dir)

  protected def internalCreateResults(
    input: ClosingIterator[CypherRow],
    state: QueryState
  ): ClosingIterator[CypherRow] = {
    val query = state.query
    val expandInto = new CachingExpandInto(
      query.transactionalContext.kernelQueryContext,
      kernelDirection,
      state.memoryTrackerForOperatorProvider.memoryTrackerForOperator(id.x)
    )
    state.query.resources.trace(expandInto)
    input.flatMap {
      row =>
        val fromNode = getRowNode(row, fromName)
        fromNode match {
          case fromNode: VirtualNodeValue =>
            val toNode = getRowNode(row, toName)

            toNode match {
              case IsNoValue() =>
                maybeRelName.foreach(relName => row.set(relName, Values.NO_VALUE))
                ClosingIterator.single(row)
              case n: VirtualNodeValue =>
                val traversalCursor = query.traversalCursor()
                val fromCursor = query.nodeCursor()
                val toCursor = query.nodeCursor()
                try {
                  val selectionCursor =
                    expandInto.connectingRelationships(
                      fromNode.id(),
                      fromCursor,
                      n.id(),
                      toCursor,
                      traversalCursor,
                      types.types(query)
                    )
                  val relationships = if (selectionCursor != null) {
                    traceRelationshipSelectionCursor(query.resources, selectionCursor, traversalCursor)
                    new RelationshipCursorIterator(selectionCursor, traversalCursor)
                  } else {
                    traversalCursor.close()
                    emptyClosingRelationshipIterator
                  }

                  val filteredRows = ListBuffer.empty[CypherRow]
                  // This is exhausting relationships directly, thus we do not need to return
                  // a ClosingIterator in this flatMap.
                  while (relationships.hasNext) {
                    val nextRel = relationships.next()
                    val candidateRow = maybeRelName.map(relName =>
                      rowFactory.copyWith(
                        row,
                        relName,
                        VirtualValues.relationship(
                          nextRel,
                          relationships.startNodeId(),
                          relationships.endNodeId(),
                          relationships.typeId()
                        )
                      )
                    ).getOrElse(rowFactory.copyWith(row))

                    if (predicate.forall(p => p(candidateRow, state) eq Values.TRUE)) {
                      filteredRows += candidateRow
                    }
                  }
                  if (filteredRows.isEmpty) {
                    maybeRelName.foreach(relName => row.set(relName, Values.NO_VALUE))
                    ClosingIterator.single(row)
                  } else filteredRows.asClosingIterator
                } finally {
                  fromCursor.close()
                  toCursor.close()
                }

              case x =>
                throw InternalException.internalError(getClass.getSimpleName, s"Unexpected value $x")
            }

          case IsNoValue() =>
            maybeRelName.foreach(relName => row.set(relName, Values.NO_VALUE))
            ClosingIterator.single(row)

          case x =>
            throw InternalException.internalError(getClass.getSimpleName, s"Unexpected value $x")
        }
    }.closing(expandInto)
  }
}
