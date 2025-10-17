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
package org.neo4j.cypher.internal.administration

import org.neo4j.cypher.internal.AdministrationCommandRuntime.internalKey
import org.neo4j.cypher.internal.AdministrationCommandRuntimeContext
import org.neo4j.cypher.internal.administration.DatabaseListParameterTransformerFunction.detailLevels
import org.neo4j.cypher.internal.administration.ShowDatabaseExecutionPlanner.accessibleDbsKey
import org.neo4j.cypher.internal.administration.topology.DatabaseDetailsMapper
import org.neo4j.cypher.internal.administration.topology.ShowDatabaseResult
import org.neo4j.cypher.internal.administration.topology.ShowDatabaseService
import org.neo4j.cypher.internal.administration.topology.ShowDatabaseServiceContext
import org.neo4j.cypher.internal.ast.DatabaseScope
import org.neo4j.cypher.internal.ast.DefaultDatabaseScope
import org.neo4j.cypher.internal.ast.HomeDatabaseScope
import org.neo4j.cypher.internal.ast.ShowDatabase.DATABASE_ID_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.LAST_COMMITTED_TX_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.REPLICATION_LAG_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.SHARD_TX_LAG_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.STORE_COL
import org.neo4j.cypher.internal.ast.SingleNamedDatabaseScope
import org.neo4j.cypher.internal.ast.Yield
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.notification.InternalNotification
import org.neo4j.cypher.internal.procs.ParameterTransformer.ParameterTransformerOutput
import org.neo4j.cypher.internal.procs.ParameterTransformerFunction
import org.neo4j.dbms.database.TopologyInfoService
import org.neo4j.graphdb.Transaction
import org.neo4j.internal.kernel.api.security.SecurityContext
import org.neo4j.kernel.database.DefaultDatabaseResolver
import org.neo4j.values.storable.Values
import org.neo4j.values.virtual.MapValue
import org.neo4j.values.virtual.VirtualValues

import scala.jdk.CollectionConverters.SeqHasAsJava

class DatabaseListParameterTransformerFunction(
  showDatabaseService: ShowDatabaseService,
  defaultDatabaseResolver: DefaultDatabaseResolver,
  maybeYield: Option[Yield],
  verbose: Boolean,
  scope: DatabaseScope,
  context: AdministrationCommandRuntimeContext
) extends ParameterTransformerFunction {

  override def transform(
    transaction: Transaction,
    securityContext: SecurityContext,
    systemParams: MapValue,
    userParams: MapValue
  ): ParameterTransformerOutput = {
    val defaultDatabase = defaultDatabaseResolver.defaultDatabase(null)
    val homeDatabase = defaultDatabaseResolver.defaultDatabase(securityContext.subject().executingUser())
    val showDatabaseServiceContext = ShowDatabaseServiceContext(
      transaction,
      securityContext,
      context.runtimeContext.cypherVersion,
      detailLevels(verbose, maybeYield)
    )

    val (databaseDetails, notifications): (Seq[ShowDatabaseResult], Set[InternalNotification]) = scope match {
      case _: DefaultDatabaseScope => (showDatabaseService.getDefaultDatabase(showDatabaseServiceContext), Set.empty)
      case _: HomeDatabaseScope    => (showDatabaseService.getHomeDatabase(showDatabaseServiceContext), Set.empty)
      case namedDatabaseScope: SingleNamedDatabaseScope =>
        showDatabaseService.getSingleNamedDatabase(namedDatabaseScope.database, userParams, showDatabaseServiceContext)
      case _ => (showDatabaseService.getAllDatabases(showDatabaseServiceContext), Set.empty)
    }

    val dbMetadata = databaseDetails.map(DatabaseDetailsMapper.toMapValue(_, defaultDatabase, homeDatabase))
    (
      safeMergeParameters(
        systemParams,
        userParams,
        VirtualValues.map(
          Array(accessibleDbsKey),
          Array(VirtualValues.fromList(dbMetadata.asJava))
        ).updatedWith(generateUsernameParameter(securityContext))
      ),
      notifications
    )
  }

  private def generateUsernameParameter(securityContext: SecurityContext): MapValue = {
    val username = Option(securityContext.subject().executingUser()) match {
      case None       => Values.NO_VALUE
      case Some("")   => Values.NO_VALUE
      case Some(user) => Values.stringValue(user)
    }

    VirtualValues.map(
      Array(internalKey("username")),
      Array(username)
    )
  }
}

object DatabaseListParameterTransformerFunction {

  private val txCols = Set(
    LAST_COMMITTED_TX_COL,
    REPLICATION_LAG_COL,
    SHARD_TX_LAG_COL
  )

  private val storeIdCols = Set(
    STORE_COL,
    DATABASE_ID_COL
  )

  private def detailLevels(verbose: Boolean, maybeYield: Option[Yield]): TopologyInfoService.RequestedExtras = {
    if (verbose && maybeYield.isDefined) {
      if (maybeYield.get.returnItems.includeExisting) {
        TopologyInfoService.RequestedExtras.ALL
      } else {
        val (lastTxSpecified, storeIdSpecified) =
          maybeYield.get.returnItems.items.map(_.expression).foldLeft((false, false))((acc, expr) => {
            expr match {
              case Variable(name) => (acc._1 || txCols.contains(name), acc._2 || storeIdCols.contains(name))
              case _              => acc
            }
          })
        new TopologyInfoService.RequestedExtras(lastTxSpecified, storeIdSpecified)
      }
    } else {
      TopologyInfoService.RequestedExtras.NONE
    }
  }
}
