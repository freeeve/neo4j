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

import org.neo4j.cypher.internal.physicalplanning.Slot
import org.neo4j.cypher.internal.physicalplanning.SlotConfigurationUtils.makeGetPrimitiveNodeFromSlotFunctionFor
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.ReadableRow
import org.neo4j.cypher.internal.runtime.interpreted.pipes.Pipe
import org.neo4j.cypher.internal.runtime.interpreted.pipes.PipeWithSource
import org.neo4j.cypher.internal.runtime.interpreted.pipes.QueryState
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.kernel.api.StatementConstants

import java.util.function.ToLongFunction

import scala.collection.mutable

class LockNodesSlottedPipe(
  src: Pipe,
  nodesToLock: Array[Slot]
)(val id: Id = Id.INVALID_ID) extends PipeWithSource(src) {

  private val nodeGetters: Array[ToLongFunction[ReadableRow]] =
    nodesToLock.map(s => makeGetPrimitiveNodeFromSlotFunctionFor(s))

  override protected def internalCreateResults(
    input: ClosingIterator[CypherRow],
    state: QueryState
  ): ClosingIterator[CypherRow] = {
    input.map(row => {
      state.query.lockNodes(getNodes(row): _*)
      row
    })
  }

  private def getNodes(ctx: CypherRow): Array[Long] = {
    val nodes = mutable.ArrayBuilder.make[Long]
    nodeGetters.foreach(getter => {
      val node = getter.applyAsLong(ctx)
      if (node != StatementConstants.NO_SUCH_NODE) {
        nodes += node
      }
    })
    nodes.result()
  }

}
