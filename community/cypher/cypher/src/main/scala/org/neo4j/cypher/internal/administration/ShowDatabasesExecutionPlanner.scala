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

import org.neo4j.common.DependencyResolver
import org.neo4j.cypher.internal.AdministrationCommandRuntime.internalKey
import org.neo4j.cypher.internal.AdministrationCommandRuntime.translateDefaultLanguagePropertyToShowOutput
import org.neo4j.cypher.internal.AdministrationCommandRuntimeContext
import org.neo4j.cypher.internal.AdministrationShowCommandUtils
import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ExecutionEngine
import org.neo4j.cypher.internal.ExecutionPlan
import org.neo4j.cypher.internal.administration.ShowDatabaseExecutionPlanner.accessibleDbsKey
import org.neo4j.cypher.internal.administration.topology.ShowDatabaseService
import org.neo4j.cypher.internal.ast.DatabaseScope
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.ShowDatabase.ACCESS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.ADDRESS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.ALIASES_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.CONSTITUENTS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.CREATION_TIME_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.CURRENT_PRIMARIES_COUNT_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.CURRENT_PROPERTY_SHARD_REPLICA_COUNT_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.CURRENT_SECONDARIES_COUNT_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.CURRENT_STATUS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.DATABASE_ID_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.DEFAULT_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.DEFAULT_LANGUAGE_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.GRAPH_SHARDS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.HOME_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.LAST_COMMITTED_TX_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.LAST_START_TIME_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.LAST_STOP_TIME_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.NAME_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.OPTIONS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.PROPERTY_SHARDS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.PROPERTY_SHARD_REPLICA_ROLE
import org.neo4j.cypher.internal.ast.ShowDatabase.REPLICATION_LAG_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.REQUESTED_PRIMARIES_COUNT_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.REQUESTED_PROPERTY_SHARDS_REPLICA_COUNT_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.REQUESTED_SECONDARIES_COUNT_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.REQUESTED_STATUS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.ROLE_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.SERVER_ID_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.SHARD_TX_LAG_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.STATUS_MSG_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.STORE_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.WRITER_COL
import org.neo4j.cypher.internal.ast.Yield
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.procs.SystemCommandExecutionPlan
import org.neo4j.dbms.database.TopologyInfoService
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.COMPOSITE_DATABASE
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_CREATED_AT_PROPERTY
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_NAME
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_PRIMARIES_PROPERTY
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_SECONDARIES_PROPERTY
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_STARTED_AT_PROPERTY
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_STATUS_PROPERTY
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_STOPPED_AT_PROPERTY
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DEFAULT_NAMESPACE
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DISPLAY_NAME_PROPERTY
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.GRAPH_SHARD
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.HAS_GRAPH_SHARD
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.HAS_PROPERTY_SHARD
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.NAMESPACE_PROPERTY
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.NAME_PROPERTY
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.PROPERTY_SHARD
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.SPD
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.TARGETS
import org.neo4j.internal.kernel.api.security.SecurityAuthorizationHandler
import org.neo4j.kernel.database.DatabaseReferenceRepository
import org.neo4j.kernel.database.DefaultDatabaseResolver
import org.neo4j.kernel.database.NamedDatabaseId.SYSTEM_DATABASE_NAME
import org.neo4j.values.virtual.VirtualValues

case class ShowDatabasesExecutionPlanner(
  resolver: DependencyResolver,
  normalExecutionEngine: ExecutionEngine,
  securityAuthorizationHandler: SecurityAuthorizationHandler
) {

  private val defaultDatabaseResolver = resolver.resolveDependency(classOf[DefaultDatabaseResolver])
  private val infoService = resolver.resolveDependency(classOf[TopologyInfoService])
  private val referenceResolver = resolver.resolveDependency(classOf[DatabaseReferenceRepository])

  private val showDatabaseService = new ShowDatabaseService(
    referenceResolver,
    defaultDatabaseResolver,
    infoService
  )

  def planShowDatabases(
    scope: DatabaseScope,
    verbose: Boolean,
    symbols: List[LogicalVariable],
    yields: Option[Yield],
    returns: Option[Return],
    context: AdministrationCommandRuntimeContext
  ): ExecutionPlan = {
    val isCompositeKey = internalKey("isComposite")

    val verboseColumns =
      if (verbose) {
        val defaultLanguage = translateDefaultLanguagePropertyToShowOutput("d")
        s""", props.$DATABASE_ID_COL as $DATABASE_ID_COL,
           |CASE WHEN d:$PROPERTY_SHARD THEN null ELSE props.$CURRENT_PRIMARIES_COUNT_COL END as $CURRENT_PRIMARIES_COUNT_COL,
           |CASE WHEN d:$PROPERTY_SHARD THEN null ELSE props.$CURRENT_SECONDARIES_COUNT_COL END as $CURRENT_SECONDARIES_COUNT_COL,
           |CASE WHEN d:$PROPERTY_SHARD THEN props.$CURRENT_SECONDARIES_COUNT_COL ELSE null END as $CURRENT_PROPERTY_SHARD_REPLICA_COUNT_COL,
           |CASE WHEN d:$PROPERTY_SHARD THEN null ELSE d.$DATABASE_PRIMARIES_PROPERTY END as $REQUESTED_PRIMARIES_COUNT_COL,
           |CASE WHEN d:$PROPERTY_SHARD THEN null ELSE d.$DATABASE_SECONDARIES_PROPERTY  END as $REQUESTED_SECONDARIES_COUNT_COL,
           |CASE WHEN d:$PROPERTY_SHARD THEN d.$DATABASE_SECONDARIES_PROPERTY ELSE null END as $REQUESTED_PROPERTY_SHARDS_REPLICA_COUNT_COL,
           |props.$LAST_COMMITTED_TX_COL as $LAST_COMMITTED_TX_COL,
           |props.$REPLICATION_LAG_COL as $REPLICATION_LAG_COL,
           |props.$SHARD_TX_LAG_COL as $SHARD_TX_LAG_COL,
           |d.$DATABASE_CREATED_AT_PROPERTY as $CREATION_TIME_COL,
           |d.$DATABASE_STARTED_AT_PROPERTY as $LAST_START_TIME_COL,
           |d.$DATABASE_STOPPED_AT_PROPERTY as $LAST_STOP_TIME_COL,
           |props.$STORE_COL as $STORE_COL,
           |props.$OPTIONS_COL as $OPTIONS_COL,
           |d:$COMPOSITE_DATABASE as $isCompositeKey,
           |$defaultLanguage as $DEFAULT_LANGUAGE_COL,
           |
           |CASE
           |  WHEN d:$COMPOSITE_DATABASE|$GRAPH_SHARD|$PROPERTY_SHARD OR d.$NAME_PROPERTY = '$SYSTEM_DATABASE_NAME' THEN NULL 
           |  WHEN d:$SPD THEN COLLECT {
           |    MATCH (d)-[:$HAS_GRAPH_SHARD]->(graphShardName:$GRAPH_SHARD)
           |    RETURN graphShardName.$NAME_PROPERTY
           |  }
           |  ELSE [d.$NAME_PROPERTY]
           |END as $GRAPH_SHARDS_COL,
           |
           |CASE
           |  WHEN d:$COMPOSITE_DATABASE|$GRAPH_SHARD|$PROPERTY_SHARD OR d.$NAME_PROPERTY = '$SYSTEM_DATABASE_NAME' THEN NULL
           |  ELSE COLLECT {
           |    MATCH (d)-[:$HAS_GRAPH_SHARD]->($GRAPH_SHARD)-[:$HAS_PROPERTY_SHARD]-(propertyShard:$PROPERTY_SHARD)
           |    RETURN propertyShard.$NAME_PROPERTY
           |    ORDER BY propertyShard.$NAME_PROPERTY
           |  }
           |END as $PROPERTY_SHARDS_COL
           |
           |with *,
           |CASE WHEN $isCompositeKey THEN NULL ELSE $OPTIONS_COL END as $OPTIONS_COL,
           |CASE WHEN $isCompositeKey THEN NULL ELSE $STORE_COL END as $STORE_COL,
           |CASE WHEN $isCompositeKey THEN NULL ELSE $CURRENT_PRIMARIES_COUNT_COL END as $CURRENT_PRIMARIES_COUNT_COL,
           |CASE WHEN $isCompositeKey THEN NULL ELSE $CURRENT_SECONDARIES_COUNT_COL END as $CURRENT_SECONDARIES_COUNT_COL
           |""".stripMargin
      } else {
        ""
      }
    val verboseNames =
      if (verbose) {
        s", $DATABASE_ID_COL, $SERVER_ID_COL, $REQUESTED_PRIMARIES_COUNT_COL, $REQUESTED_SECONDARIES_COUNT_COL, $REQUESTED_PROPERTY_SHARDS_REPLICA_COUNT_COL, $CURRENT_PRIMARIES_COUNT_COL, " +
          s"$CURRENT_SECONDARIES_COUNT_COL, $CURRENT_PROPERTY_SHARD_REPLICA_COUNT_COL, $CREATION_TIME_COL, $LAST_START_TIME_COL, $LAST_STOP_TIME_COL, $STORE_COL, $LAST_COMMITTED_TX_COL, $REPLICATION_LAG_COL, " +
          s"$SHARD_TX_LAG_COL, $GRAPH_SHARDS_COL, $PROPERTY_SHARDS_COL, $DEFAULT_LANGUAGE_COL, $OPTIONS_COL"
      } else {
        ""
      }
    val returnClause = AdministrationShowCommandUtils.generateReturnClause(symbols, yields, returns, Seq("name"))

    val nameFilter = context.runtimeContext.cypherVersion match {
      case CypherVersion.Cypher5 => s"{$NAME_PROPERTY: props.name, $NAMESPACE_PROPERTY: '$DEFAULT_NAMESPACE'}"
      case _                     => s"{$DISPLAY_NAME_PROPERTY: props.name}"
    }

    val query = Predef.augmentString(
      s"""UNWIND $$`$accessibleDbsKey` AS props
           |MATCH (d:$DATABASE)<-[:$TARGETS]-(dn:$DATABASE_NAME $nameFilter)
           |WITH d, dn, props
           |WITH dn.$DISPLAY_NAME_PROPERTY as name,
           |props.$ALIASES_COL as $ALIASES_COL,
           |props.$CONSTITUENTS_COL as $CONSTITUENTS_COL,
           |props.$ACCESS_COL as $ACCESS_COL,
           |props.$ADDRESS_COL as $ADDRESS_COL,
           |CASE WHEN d:$PROPERTY_SHARD THEN '$PROPERTY_SHARD_REPLICA_ROLE' ELSE props.$ROLE_COL END as $ROLE_COL,
           |props.$WRITER_COL as $WRITER_COL,
           | // serverID needs to be part of the grouping key here as it is guaranteed to be different on different servers
           |props.$SERVER_ID_COL as $SERVER_ID_COL,
           |d.$DATABASE_STATUS_PROPERTY as requestedStatus,
           |props.$CURRENT_STATUS_COL as $CURRENT_STATUS_COL,
           |props.$STATUS_MSG_COL as $STATUS_MSG_COL,
           |props.$DEFAULT_COL as $DEFAULT_COL,
           |props.$HOME_COL as $HOME_COL,
           |props.type as type,
           |d.name as dbNameProperty
           |$verboseColumns
           |
           |WITH name AS $NAME_COL,
           |type,
           |$ALIASES_COL,
           |$ACCESS_COL,
           |$ADDRESS_COL,
           |$ROLE_COL,
           |$WRITER_COL,
           |requestedStatus AS $REQUESTED_STATUS_COL,
           |$CURRENT_STATUS_COL,
           |$STATUS_MSG_COL,
           |$DEFAULT_COL,
           |$HOME_COL,
           |constituents as $CONSTITUENTS_COL
           |$verboseNames
           |$returnClause
           |"""
    ).stripMargin
    SystemCommandExecutionPlan(
      scope.showCommandName,
      normalExecutionEngine,
      securityAuthorizationHandler,
      query,
      VirtualValues.EMPTY_MAP,
      parameterTransformer = new DatabaseListParameterTransformerFunction(
        showDatabaseService,
        defaultDatabaseResolver,
        yields,
        verbose,
        scope,
        context
      ),
      cypherVersion = Some(context.runtimeContext.cypherVersion)
    )
  }
}

object ShowDatabaseExecutionPlanner {
  protected[administration] val accessibleDbsKey: String = internalKey("accessibleDbs")
}
