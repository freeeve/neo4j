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
package org.neo4j.cypher.internal.runtime.cursors

import org.neo4j.internal.kernel.api.NodeCursor
import org.neo4j.internal.kernel.api.Read
import org.neo4j.internal.kernel.api.ReferenceCursor

abstract class FilteringNodeCursor(
  inner: ReferenceCursor,
  read: Read,
  nodeCursor: NodeCursor
) extends DelegatingReferenceCursor(inner) {
  def test(nodeCursor: NodeCursor): Boolean

  override def next(): Boolean = {
    while (inner.next()) {
      // TODO: Can we just use inner here instead
      val nodeId = inner.reference()

      // Position the node cursor on the current node
      read.singleNode(nodeId, nodeCursor)

      // The node may have been deleted in this transaction, so check that it has not been
      if (nodeCursor.next() && test(nodeCursor)) {
        return true
      }
    }

    // No more matching nodes found
    false
  }
}
