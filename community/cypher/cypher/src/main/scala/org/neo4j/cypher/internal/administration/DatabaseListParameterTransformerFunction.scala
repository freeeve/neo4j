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
import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.administration.DatabaseListParameterTransformerFunction.detailLevels
import org.neo4j.cypher.internal.administration.ShowDatabaseExecutionPlanner.accessibleDbsKey
import org.neo4j.cypher.internal.ast.DatabaseScope
import org.neo4j.cypher.internal.ast.DefaultDatabaseScope
import org.neo4j.cypher.internal.ast.HomeDatabaseScope
import org.neo4j.cypher.internal.ast.NamespacedName
import org.neo4j.cypher.internal.ast.ParameterName
import org.neo4j.cypher.internal.ast.ShowDatabase.DATABASE_ID_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.LAST_COMMITTED_TX_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.REPLICATION_LAG_COL
import org.neo4j.cypher.internal.ast.ShowDatabase.STORE_COL
import org.neo4j.cypher.internal.ast.SingleNamedDatabaseScope
import org.neo4j.cypher.internal.ast.Yield
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.procs.ParameterTransformer.ParameterTransformerOutput
import org.neo4j.cypher.internal.procs.ParameterTransformerFunction
import org.neo4j.cypher.internal.util.AssertionRunner
import org.neo4j.cypher.internal.util.InternalNotification
import org.neo4j.dbms.database.DatabaseDetails
import org.neo4j.dbms.database.DatabaseDetails.STATUS_MIXED
import org.neo4j.dbms.database.TopologyInfoService
import org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DEFAULT_NAMESPACE
import org.neo4j.graphdb.Transaction
import org.neo4j.internal.kernel.api.security.SecurityContext
import org.neo4j.kernel.database.DatabaseIdFactory
import org.neo4j.kernel.database.DatabaseReference
import org.neo4j.kernel.database.DatabaseReferenceImpl
import org.neo4j.kernel.database.DatabaseReferenceRepository
import org.neo4j.kernel.database.DefaultDatabaseResolver
import org.neo4j.kernel.database.NamedDatabaseId
import org.neo4j.kernel.database.NormalizedDatabaseName
import org.neo4j.values.AnyValue
import org.neo4j.values.storable.Values
import org.neo4j.values.virtual.MapValue
import org.neo4j.values.virtual.VirtualValues

import java.util

import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.jdk.CollectionConverters.SetHasAsJava
import scala.jdk.OptionConverters.RichOption

class DatabaseListParameterTransformerFunction(
  referenceResolver: DatabaseReferenceRepository,
  defaultDatabaseResolver: DefaultDatabaseResolver,
  infoService: TopologyInfoService,
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

    val allReferences = referenceResolver.getAllDatabaseReferences.asScala.toSet
    val (filteredReferences, notifications): (Set[DatabaseReference], Set[InternalNotification]) = scope match {
      case _: DefaultDatabaseScope =>
        (allReferences.filter(ref => ref.isPrimary && ref.alias().name().equals(defaultDatabase)), Set.empty)
      case _: HomeDatabaseScope =>
        (allReferences.filter(ref => ref.isPrimary && ref.alias().name().equals(homeDatabase)), Set.empty)
      case namedDatabaseScope: SingleNamedDatabaseScope =>
        filterReferencesByName(allReferences, namedDatabaseScope, userParams)
      case _ =>
        (allReferences, Set.empty)
    }

    val filteredReferencesWithShards: Set[DatabaseReference] = filteredReferences.flatMap {
      case db: DatabaseReferenceImpl.VirtualSPD =>
        val graphShard = db.graphShard()
        val propertyShards = graphShard.propertyShards().values()
        Set(db) ++ Set(graphShard) ++ propertyShards.asScala
      case db => Set(db)
    }

    val allDbInfos: util.Set[DatabaseDetails] =
      infoService.databases(
        transaction,
        filteredReferencesWithShards.collect(db => db.namedDatabaseId()).asJava,
        detailLevels(verbose, maybeYield)
      )

    val accessibleDatabases = filteredReferences
      .collect {
        case db
          if db.isPrimary && !db.isInstanceOf[
            DatabaseReferenceImpl.VirtualSPD
          ] && securityContext.databaseAccessMode().canSeeDatabase(db) =>
          DatabaseIdFactory.from(db.alias().name(), db.id())
      }

    val dbMetadata: List[AnyValue] = {
      val dbInfos: util.Set[DatabaseDetails] =
        allDbInfos.asScala.filter(info => accessibleDatabases.contains(info.namedDatabaseId())).toSet.asJava
      dbInfos.asScala.map(info => DatabaseDetailsMapper.toMapValue(info, defaultDatabase, homeDatabase)).toList
    }

    val spdMetadata: List[AnyValue] = filteredReferences.collect {
      case ref: DatabaseReferenceImpl.VirtualSPD
        if ref.isPrimary && securityContext.databaseAccessMode().canSeeDatabase(ref) =>
        val spd = DatabaseIdFactory.from(ref.alias().name(), ref.id())
        val graphShardInfos =
          allDbInfos.asScala.filter(info => ref.graphShard().namedDatabaseId().equals(info.namedDatabaseId()))
        val propertyShardDatabaseIds = ref.graphShard().propertyShards().values().asScala.map(_.namedDatabaseId()).toSet
        val propertyShardInfos =
          allDbInfos.asScala.filter(info => propertyShardDatabaseIds.contains(info.namedDatabaseId()))
        val groupedStatus = (graphShardInfos ++ propertyShardInfos).map(databaseDetail =>
          databaseDetail.status()
        ).groupBy(identity).view.mapValues(_.size)
        val (status, statusMessage) = if (groupedStatus.size == 1) {
          (groupedStatus.head._1, "")
        } else {
          val statusMessage = groupedStatus.map(group => s"${group._1}(${group._2})").mkString(", ")
          (STATUS_MIXED, statusMessage)
        }

        graphShardInfos.map(databaseDetails =>
          new DatabaseDetails(
            databaseDetails.serverId(),
            databaseDetails.databaseAccess(),
            databaseDetails.boltAddress(),
            databaseDetails.role,
            databaseDetails.writer(),
            status,
            statusMessage,
            Option.empty.toJava,
            Option.empty.toJava,
            // database level values - will be the same for all members
            spd, // replace with spd
            DatabaseDetails.TYPE_STANDARD,
            databaseDetails.options,
            Option.empty.toJava,
            databaseDetails.externalStoreId(),
            null,
            null
          )
        ).map(info =>
          DatabaseDetailsMapper.toMapValue(info, defaultDatabase, homeDatabase)
        )
          .toList

    }.toList.flatten

    (
      safeMergeParameters(
        systemParams,
        userParams,
        VirtualValues.map(
          Array(accessibleDbsKey),
          Array(VirtualValues.fromList((dbMetadata ++ spdMetadata).asJava))
        ).updatedWith(generateUsernameParameter(securityContext))
      ),
      notifications
    )
  }

  private def filterReferencesByName(
    databaseReferences: Set[DatabaseReference],
    namedDatabaseScope: SingleNamedDatabaseScope,
    params: MapValue
  ): (Set[DatabaseReference], Set[InternalNotification]) = {
    val (name, namespace, notifications)
      : (NormalizedDatabaseName, Option[NormalizedDatabaseName], Set[InternalNotification]) =
      namedDatabaseScope.database match {
        case nn @ NamespacedName(_, namespace) =>
          val normalizedNamespace = namespace.map(new NormalizedDatabaseName(_))
          if (context.runtimeContext.cypherVersion == CypherVersion.Cypher5) {
            normalizedNamespace match {
              case Some(ns) =>
                val deprecatedName = ns.name() + "." + nn.name
                databaseReferences.find(dr => dr.isComposite && dr.alias().equals(ns))
                  .map(_ => (new NormalizedDatabaseName(nn.name), normalizedNamespace, Set.empty[InternalNotification]))
                  // This is the deprecated case of "SHOW DATABASE a.b" with no composite. Should really be `a.b`, so warn
                  .getOrElse((
                    new NormalizedDatabaseName(deprecatedName),
                    None,
                    Set.empty
                  ))
              case None => (new NormalizedDatabaseName(nn.name), normalizedNamespace, Set.empty[InternalNotification])
            }
          } else {
            (new NormalizedDatabaseName(nn.name), normalizedNamespace, Set.empty[InternalNotification])
          }
        case pn: ParameterName =>
          val (namespace, name, _, _) = pn.getNameParts(params, DEFAULT_NAMESPACE)
          val normalizedNamespace = namespace.map(new NormalizedDatabaseName(_))
          normalizedNamespace match {
            case None => (new NormalizedDatabaseName(name), None, Set.empty[InternalNotification])
            case Some(ns) =>
              databaseReferences.find(dr => dr.isComposite && dr.alias().equals(ns))
                .map(_ => (new NormalizedDatabaseName(name), normalizedNamespace, Set.empty[InternalNotification]))
                .getOrElse((new NormalizedDatabaseName(ns.name() + "." + name), None, Set.empty[InternalNotification]))
          }
      }

    def assertAtMostOne(seq: Iterable[DatabaseReference]): Unit = {
      if (AssertionRunner.isAssertionsEnabled && seq.size > 1) {
        throw new IllegalStateException("SHOW DATABASE by name should only return 0 or 1 databases")
      }
    }

    def displayName(namespace: Option[NormalizedDatabaseName], name: NormalizedDatabaseName): String = {
      (namespace, name) match {
        case (Some(ns), name) => s"${ns.name()}.${name.name()}"
        case (None, name)     => name.name()
      }
    }

    def primaryByNamedDatabaseId(namedDatabaseId: NamedDatabaseId): Option[DatabaseReference] = {
      val matching = databaseReferences.filter(_.isPrimary).filter(_.namedDatabaseId() == namedDatabaseId)
      assertAtMostOne(matching)
      matching.headOption
    }

    val filteredReferences: Set[DatabaseReference] = context.runtimeContext.cypherVersion match {
      case CypherVersion.Cypher5 =>
        // Cypher 5: find reference by namespace/name split
        namespace match {
          case None => databaseReferences.collect {
              // database
              case ref if ref.isPrimary && ref.alias().equals(name) => Set(ref)
              // alias
              case ref: DatabaseReferenceImpl.Internal if ref.alias().equals(name) =>
                primaryByNamedDatabaseId(ref.namedDatabaseId())
            }.flatten
          case Some(namespace) => databaseReferences.collect {
              // composite constituent
              case c: DatabaseReferenceImpl.Composite if c.alias().equals(namespace) =>
                c.constituents().asScala
                  .filter(r => r.alias().equals(name))
                  .flatMap(dr => primaryByNamedDatabaseId(dr.namedDatabaseId()))
            }.flatten
        }
      case _ =>
        // Cypher 25+: find reference by full/display name
        databaseReferences.collect {
          // database
          case ref if ref.isPrimary && ref.fullName().name().equals(displayName(namespace, name)) => Set(ref)
          // composite constituent
          case c: DatabaseReferenceImpl.Composite =>
            c.constituents().asScala
              .filter(constituent => constituent.fullName().name().equals(displayName(namespace, name)))
              .flatMap(dr => primaryByNamedDatabaseId(dr.namedDatabaseId()))
          // alias
          case ref: DatabaseReferenceImpl.Internal if ref.fullName().name().equals(displayName(namespace, name)) =>
            primaryByNamedDatabaseId(ref.namedDatabaseId())
        }.flatten
    }

    assertAtMostOne(filteredReferences)
    (filteredReferences, notifications)
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
    REPLICATION_LAG_COL
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
