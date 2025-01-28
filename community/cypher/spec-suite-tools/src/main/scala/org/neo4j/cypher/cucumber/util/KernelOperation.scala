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
package org.neo4j.cypher.cucumber.util

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.kernel.api.KernelTransaction
import org.neo4j.kernel.impl.coreapi.InternalTransaction

import scala.util.Using

/**
 * Contains some kernel operations.
 * Kernel operations are preferred over Cypher for performance.
 */
object KernelOperation {

  def detachDeleteAllNodes(db: GraphDatabaseService): Unit = withKernelTx(db)(detachDeleteAllNodes)

  def withKernelTx[T](db: GraphDatabaseService)(f: KernelTransaction => T): T = Using.resource(db.beginTx()) {
    case tx: InternalTransaction =>
      val result = f(tx.kernelTransaction())
      tx.commit()
      result
    case tx => throw new IllegalArgumentException(s"Expected an InternalTransaction: " + tx.getClass)
  }

  private def detachDeleteAllNodes(tx: KernelTransaction): Unit = {
    Using.resource(tx.cursors().allocateNodeCursor(tx.cursorContext())) { nodeCur =>
      tx.dataRead().allNodesScan(nodeCur)
      val write = tx.dataWrite()
      while (nodeCur.next()) write.nodeDetachDelete(nodeCur.nodeReference())
    }
  }
}
