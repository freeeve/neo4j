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

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.DatabaseName
import org.neo4j.cypher.internal.notification.InternalNotification
import org.neo4j.dbms.database.DatabaseDetails
import org.neo4j.dbms.database.DatabaseDetails.STATUS_MIXED
import org.neo4j.dbms.database.TopologyInfoService
import org.neo4j.graphdb.Transaction
import org.neo4j.internal.kernel.api.security.SecurityContext
import org.neo4j.kernel.database.DatabaseId
import org.neo4j.kernel.database.DatabaseIdFactory
import org.neo4j.kernel.database.DatabaseReference
import org.neo4j.kernel.database.DatabaseReferenceImpl
import org.neo4j.kernel.database.DatabaseReferenceImpl.Composite
import org.neo4j.kernel.database.DatabaseReferenceImpl.GraphShard
import org.neo4j.kernel.database.DatabaseReferenceRepository
import org.neo4j.kernel.database.DefaultDatabaseResolver
import org.neo4j.kernel.database.NamedDatabaseId
import org.neo4j.values.virtual.MapValue

import java.util.UUID

import scala.collection.MapView
import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.jdk.CollectionConverters.SetHasAsJava
import scala.jdk.CollectionConverters.SetHasAsScala
import scala.jdk.OptionConverters.RichOption

case class ShowDatabaseServiceContext(
  transaction: Transaction,
  securityContext: SecurityContext,
  cypherVersion: CypherVersion,
  detailLevel: TopologyInfoService.RequestedExtras
)

case class ShowDatabaseResult(
  details: DatabaseDetails,
  constituents: Seq[String],
  aliases: Seq[String],
  graphShards: Option[Seq[String]],
  propertyShards: Option[Seq[String]] = None
)

class ShowDatabaseService(
  referenceResolver: DatabaseReferenceRepository,
  defaultDatabaseResolver: DefaultDatabaseResolver,
  infoService: TopologyInfoService
) {
  private val nameResolver = new DatabaseNameResolver(referenceResolver)

  def getAllDatabases(context: ShowDatabaseServiceContext): Seq[ShowDatabaseResult] = {
    getDatabaseDetails(referenceResolver.getAllDatabaseReferences.asScala.toSet, context)
  }

  def getHomeDatabase(context: ShowDatabaseServiceContext): Seq[ShowDatabaseResult] = {
    val homeDatabase = defaultDatabaseResolver.defaultDatabase(context.securityContext.subject().executingUser())
    val references = referenceResolver.getAllDatabaseReferences.asScala.filter(ref =>
      ref.isPrimary && ref.alias().name().equals(homeDatabase)
    ).toSet
    getDatabaseDetails(references, context)
  }

  def getDefaultDatabase(context: ShowDatabaseServiceContext): Seq[ShowDatabaseResult] = {
    val defaultDatabase = defaultDatabaseResolver.defaultDatabase(null)
    val references = referenceResolver.getAllDatabaseReferences.asScala.filter(ref =>
      ref.isPrimary && ref.alias().name().equals(defaultDatabase)
    ).toSet
    getDatabaseDetails(references, context)
  }

  def getSingleNamedDatabase(
    name: DatabaseName,
    params: MapValue,
    context: ShowDatabaseServiceContext
  ): (Seq[ShowDatabaseResult], Set[InternalNotification]) = {
    val (references, notifications) = nameResolver.resolveDatabaseNameToReference(name, params, context.cypherVersion)
    (getDatabaseDetails(references, context), notifications)
  }

  private def getDatabaseDetails(
    filteredReferences: Set[DatabaseReference],
    context: ShowDatabaseServiceContext
  ): Seq[ShowDatabaseResult] = {
    val allReferences = sortAliases(referenceResolver.getAllDatabaseReferences.asScala)

    val filteredReferencesWithShards: Set[DatabaseReference] = filteredReferences.flatMap {
      case db: DatabaseReferenceImpl.VirtualSPD =>
        val graphShard = db.graphShard()
        val propertyShards = graphShard.propertyShards().values()
        Set(db) ++ Set(graphShard) ++ propertyShards.asScala
      case db => Set(db)
    }

    val allDbInfos: Set[ShowDatabaseResult] =
      lookupDbInfos(allReferences.view.filterKeys(filteredReferencesWithShards.contains), context)

    val accessibleDatabases = filteredReferences.collect {
      case db
        if db.isPrimary && !db.isInstanceOf[
          DatabaseReferenceImpl.VirtualSPD
        ] && context.securityContext.databaseAccessMode().canSeeDatabase(db) =>
        DatabaseIdFactory.from(db.alias().name(), db.id())
    }

    val spdMetadata: Seq[ShowDatabaseResult] = filteredReferences.collect {
      case ref: DatabaseReferenceImpl.VirtualSPD
        if ref.isPrimary && context.securityContext.databaseAccessMode().canSeeDatabase(ref) =>
        val spdId = DatabaseIdFactory.from(ref.alias().name(), ref.id())
        val graphShardInfos =
          allDbInfos.toList.filter(info => ref.graphShard().namedDatabaseId().equals(info.details.namedDatabaseId()))
        val propertyShardDatabaseIds = ref.graphShard().propertyShards().values().asScala.map(_.namedDatabaseId()).toSet
        val propertyShardInfos =
          allDbInfos.filter(info => propertyShardDatabaseIds.contains(info.details.namedDatabaseId()))
        val groupedStatus = (graphShardInfos ++ propertyShardInfos).map(databaseDetail =>
          databaseDetail.details.status()
        ).groupBy(identity).view.mapValues(_.size)
        val (status, statusMessage) = if (groupedStatus.size == 1) {
          (groupedStatus.head._1, "")
        } else {
          val statusMessage = groupedStatus.map(group => s"${group._1}(${group._2})").mkString(", ")
          (STATUS_MIXED, statusMessage)
        }

        graphShardInfos.map(databaseInfo =>
          ShowDatabaseResult(
            databaseDetailsForGraphShard(databaseInfo.details, status, statusMessage, spdId),
            Seq.empty,
            allReferences(ref).map(_.name()),
            Some(Seq(ref.graphShard().name())),
            Some(ref.graphShard().propertyShards().asScala.map { case (_, propShard) => propShard.name() }.toSeq)
          )
        )
    }.toList.flatten

    spdMetadata ++ allDbInfos.filter(info => accessibleDatabases.contains(info.details.namedDatabaseId())).toSeq
  }

  private def databaseDetailsForGraphShard(
    databaseDetails: DatabaseDetails,
    status: String,
    statusMessage: String,
    spdId: NamedDatabaseId
  ): DatabaseDetails = new DatabaseDetails(
    databaseDetails.serverId(),
    databaseDetails.databaseAccess(),
    databaseDetails.boltAddress(),
    databaseDetails.role,
    databaseDetails.writer(),
    status,
    statusMessage,
    Option.empty.toJava,
    Option.empty.toJava,
    Option.empty.toJava,
    // database level values - will be the same for all members
    spdId,
    // replace with spd
    DatabaseDetails.TYPE_STANDARD,
    // this is not great as these are the options of the graph shard which is not the same as the options of spd
    databaseDetails.options,
    Option.empty.toJava,
    databaseDetails.externalStoreId(),
    null,
    null
  )

  private def lookupDbInfos(
    dbsToLookup: MapView[DatabaseReference, Seq[DatabaseReference]],
    context: ShowDatabaseServiceContext
  ): Set[ShowDatabaseResult] = {

    case class DatabaseLinks(
      constituents: Seq[String],
      alias: Seq[String],
      graphShards: Option[Seq[String]] = None,
      propertyShards: Option[Seq[String]] = None
    )

    val dbids: Map[NamedDatabaseId, DatabaseLinks] = dbsToLookup.map {
      case (db: DatabaseReferenceImpl.Composite, _) =>
        (db.namedDatabaseId(), DatabaseLinks(db.constituents().asScala.map(_.name()).toSeq, Seq.empty))
      // aliases should go on the virtual db so a GraphShard has no aliases
      case (db: GraphShard, _) => (db.namedDatabaseId(), DatabaseLinks(Seq.empty, Seq.empty))
      // these get filtered anyway and replaced with spdMetadata
      case (db: DatabaseReferenceImpl.VirtualSPD, _) => (db.namedDatabaseId(), DatabaseLinks(Seq.empty, Seq.empty))
      case (db: DatabaseReferenceImpl.PropertyShard, _) =>
        (db.namedDatabaseId(), DatabaseLinks(Seq.empty, Seq.empty))
      case (db, aliases) if db.id() == DatabaseId.SYSTEM_DATABASE_ID.uuid() =>
        (db.namedDatabaseId(), DatabaseLinks(Seq.empty, aliases.map(_.name())))
      case (db, aliases) =>
        (
          db.namedDatabaseId(),
          DatabaseLinks(
            Seq.empty,
            aliases.map(_.name()),
            Some(Seq(db.name())),
            Some(Seq.empty)
          )
        )
    }.toMap[NamedDatabaseId, DatabaseLinks]

    val details = infoService.databases(
      context.transaction,
      dbids.keySet.asJava,
      context.detailLevel
    )

    details.asScala.map(d => {
      val DatabaseLinks(constituents, aliases, graphShards, propertyShards) = dbids(d.namedDatabaseId())
      ShowDatabaseResult(d, constituents, aliases, graphShards, propertyShards)
    })
  }.toSet

  // Sort DatabaseReferences into primary references and aliases.
  private def sortAliases(references: Iterable[DatabaseReference]): Map[DatabaseReference, Seq[DatabaseReference]] = {

    val queue = mutable.Queue[DatabaseReference]()
    queue.addAll(references)
    queue.sortInPlaceBy(!_.isPrimary)

    val sorted = mutable.Map[DatabaseReference, mutable.Buffer[DatabaseReference]]()
    val uuidToKey = mutable.Map[UUID, DatabaseReference]()

    while (queue.nonEmpty) {
      queue.dequeue() match {
        case comp: Composite =>
          sorted += (comp -> mutable.ListBuffer.empty)
          uuidToKey += (comp.id() -> comp)
          queue.enqueueAll(comp.constituents().asScala)
        case _: DatabaseReferenceImpl.External                    => ()
        case db: DatabaseReferenceImpl.GraphShard if db.isPrimary =>
          // Graph shard and SPD virtual have the same UUID. We only want to track the
          // aliases against the SPD virtual
          sorted += (db -> mutable.ListBuffer.empty)
        case db if db.isPrimary =>
          sorted += (db -> mutable.ListBuffer.empty)
          uuidToKey += (db.id -> db)
        case db =>
          val uuid = db.id()
          uuidToKey.get(uuid) match {
            case None             => () // alias which doesn't have a primary
            case Some(primaryRef) => sorted(primaryRef) += db
          }
      }
    }
    sorted.view.mapValues(_.toSeq).toMap
  }
}
