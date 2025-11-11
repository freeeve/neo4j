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

import org.neo4j.cypher.internal.physicalplanning.SlottedIndexedProperty
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.MergePropertySets
import org.neo4j.cypher.internal.runtime.interpreted.pipes.MergeUniqueNodePipe.computePredicates
import org.neo4j.cypher.internal.runtime.interpreted.pipes.MergeUniqueNodePipe.mergeUniqueNodeId
import org.neo4j.cypher.internal.runtime.interpreted.pipes.Pipe
import org.neo4j.cypher.internal.runtime.interpreted.pipes.QueryState
import org.neo4j.cypher.internal.runtime.slotted.pipes.MergeUniqueNodeSlottedPipe.cacheProperties
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.internal.kernel.api.PropertyIndexQuery

class MergeUniqueNodeSlottedPipe(
  nodeOffset: Int,
  labelName: String,
  queryIndexId: Int,
  indexedProperties: Array[SlottedIndexedProperty],
  seekExpressions: Array[Expression],
  onMatchProperties: MergePropertySets,
  onCreateProperties: MergePropertySets
)(val id: Id = Id.INVALID_ID) extends Pipe {
  private[this] val properties = indexedProperties.map(_.propertyKeyId)

  private[this] val cachedPropsKeys: Array[Int] =
    indexedProperties.map(p => p.maybeCachedEntityPropertySlot.getOrElse(-1))
  private[this] val shouldCacheProperties: Boolean = indexedProperties.exists(_.getValueFromIndex)

  protected def internalCreateResults(state: QueryState): ClosingIterator[CypherRow] = {
    val row = state.newRowWithArgument(rowFactory)
    val predicates = computePredicates(labelName, row, state, properties, seekExpressions)
    row.setLongAt(
      nodeOffset,
      mergeUniqueNodeId(
        row,
        state,
        queryIndexId,
        predicates,
        onMatchProperties,
        onCreateProperties
      )
    )
    if (shouldCacheProperties) {
      cacheProperties(row, cachedPropsKeys, predicates)
    }
    ClosingIterator.single(row)
  }
}

object MergeUniqueNodeSlottedPipe {

  def cacheProperties(
    row: CypherRow,
    cachedPropsKeys: Array[Int],
    predicates: Array[PropertyIndexQuery.ExactPredicate]
  ): Unit = {
    var i = 0
    while (i < cachedPropsKeys.length) {
      val key = cachedPropsKeys(i)
      if (key != -1) {
        row.setCachedPropertyAt(key, predicates(i).value())
      }
      i += 1
    }
  }
}
