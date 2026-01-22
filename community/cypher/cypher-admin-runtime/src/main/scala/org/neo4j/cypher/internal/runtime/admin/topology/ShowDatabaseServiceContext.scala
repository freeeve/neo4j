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
package org.neo4j.cypher.internal.runtime.admin.topology

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.ShowDatabase.DATABASE_ID_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.LAST_COMMITTED_TX_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.REPLICATION_LAG_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.SHARD_TX_LAG_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.STORE_COL
import org.neo4j.dbms.database.TopologyInfoService
import org.neo4j.internal.kernel.api.security.SecurityContext

case class ShowDatabaseServiceContext(
  securityContext: SecurityContext,
  cypherVersion: CypherVersion,
  detailLevel: TopologyInfoService.RequestedExtras
)

object ShowDatabaseServiceContext {

  private val txCols = Set(
    LAST_COMMITTED_TX_COL,
    REPLICATION_LAG_COL,
    SHARD_TX_LAG_COL
  )

  private val storeIdCols = Set(
    STORE_COL,
    DATABASE_ID_COL
  )

  def apply(
    securityContext: SecurityContext,
    cypherVersion: CypherVersion,
    columns: Set[String]
  ): ShowDatabaseServiceContext = {
    ShowDatabaseServiceContext(
      securityContext,
      cypherVersion,
      new TopologyInfoService.RequestedExtras(
        columns.intersect(txCols).nonEmpty,
        columns.intersect(storeIdCols).nonEmpty
      )
    )
  }
}
