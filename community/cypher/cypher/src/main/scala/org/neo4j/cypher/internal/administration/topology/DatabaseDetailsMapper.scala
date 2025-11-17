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
package org.neo4j.cypher.internal.administration.topology

import org.neo4j.cypher.internal.ast.ShowDatabase.ACCESS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.ADDRESS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.ALIASES_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.CONSTITUENTS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.CURRENT_PRIMARIES_COUNT_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.CURRENT_SECONDARIES_COUNT_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.CURRENT_STATUS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.DATABASE_ID_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.DEFAULT_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.GRAPH_SHARDS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.HOME_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.LAST_COMMITTED_TX_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.NAME_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.OPTIONS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.PROPERTY_SHARDS_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.REPLICATION_LAG_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.ROLE_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.SERVER_ID_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.SHARD_TX_LAG_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.STATUS_MSG_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.STORE_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.TYPE_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.WRITER_COL
import org.neo4j.values.AnyValue
import org.neo4j.values.storable.Values
import org.neo4j.values.virtual.ListValue
import org.neo4j.values.virtual.ListValueBuilder
import org.neo4j.values.virtual.VirtualValues

import scala.jdk.CollectionConverters.MapHasAsJava
import scala.jdk.CollectionConverters.MapHasAsScala

object DatabaseDetailsMapper {

  def toMapValue(
    showDatabaseResult: ShowDatabaseResult,
    defaultDatabase: String,
    homeDatabase: String
  ): AnyValue = {
    val databaseDetails = showDatabaseResult.details
    val constituentValue = buildStringListValue(showDatabaseResult.constituents)
    val aliasesValue = buildStringListValue(showDatabaseResult.aliases.sorted)
    val graphShardValue = showDatabaseResult.graphShards.map(buildStringListValue).getOrElse(Values.NO_VALUE)
    val propertyShardValue = showDatabaseResult.propertyShards.map(buildStringListValue).getOrElse(Values.NO_VALUE)

    VirtualValues.map(
      Array(
        NAME_COL,
        TYPE_COL,
        ACCESS_COL,
        ADDRESS_COL,
        ROLE_COL,
        WRITER_COL,
        DEFAULT_COL,
        HOME_COL,
        CURRENT_STATUS_COL,
        STATUS_MSG_COL,
        DATABASE_ID_COL,
        SERVER_ID_COL,
        CURRENT_PRIMARIES_COUNT_COL,
        CURRENT_SECONDARIES_COUNT_COL,
        STORE_COL,
        LAST_COMMITTED_TX_COL,
        REPLICATION_LAG_COL,
        SHARD_TX_LAG_COL,
        OPTIONS_COL,
        CONSTITUENTS_COL,
        ALIASES_COL,
        GRAPH_SHARDS_COL,
        PROPERTY_SHARDS_COL
      ),
      Array(
        Values.stringValue(databaseDetails.namedDatabaseId().name()),
        Values.stringValue(databaseDetails.databaseType()),
        Values.stringValue(databaseDetails.databaseAccess().getStringRepr),
        databaseDetails.boltAddress().map[AnyValue](s => Values.stringValue(s.toString)).orElse(Values.NO_VALUE),
        databaseDetails.role().map[AnyValue](s => Values.stringValue(s)).orElse(Values.NO_VALUE),
        Values.booleanValue(databaseDetails.writer()),
        Values.booleanValue(databaseDetails.namedDatabaseId().name().equals(defaultDatabase)),
        Values.booleanValue(databaseDetails.namedDatabaseId().name().equals(homeDatabase)),
        Values.stringValue(databaseDetails.actualStatus()),
        Values.stringValue(databaseDetails.statusMessage()),
        databaseDetails.readableExternalStoreId().map[AnyValue](s => Values.stringValue(s)).orElse(Values.NO_VALUE),
        databaseDetails.serverId().map[AnyValue](s => Values.stringValue(s.uuid().toString)).orElse(Values.NO_VALUE),
        if (databaseDetails.actualPrimariesCount() == null) Values.NO_VALUE
        else Values.longValue(databaseDetails.actualPrimariesCount().intValue()),
        if (databaseDetails.actualSecondariesCount() == null) Values.NO_VALUE
        else Values.longValue(databaseDetails.actualSecondariesCount().intValue()),
        databaseDetails.readableStoreId().map[AnyValue](s => Values.stringValue(s)).orElse(Values.NO_VALUE),
        databaseDetails.lastCommittedTxId().map[AnyValue](s => Values.longValue(s)).orElse(Values.NO_VALUE),
        databaseDetails.txCommitLag().map[AnyValue](s => Values.longValue(s)).orElse(Values.NO_VALUE),
        databaseDetails.shardCommitLag().map[AnyValue](s => Values.longValue(s)).orElse(Values.NO_VALUE), {
          val valueOptions =
            databaseDetails.options().asScala.view.mapValues(v => Values.stringValue(v)).toMap[String, AnyValue].asJava
          VirtualValues.fromMap(valueOptions, valueOptions.size, 0)
        },
        constituentValue,
        aliasesValue,
        graphShardValue,
        propertyShardValue
      )
    )
  }

  def buildStringListValue(strings: Seq[String]): ListValue = {
    val lvb = ListValueBuilder.newListBuilder()
    strings.foreach(const => lvb.add(Values.stringValue(const)))
    lvb.build()
  }
}
