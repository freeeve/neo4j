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
package org.neo4j.fabric.planning

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.CatalogName
import org.neo4j.cypher.internal.cache.CacheSize
import org.neo4j.cypher.internal.cache.CaffeineCacheFactory
import org.neo4j.cypher.internal.cache.CypherQueryCaches.CacheStrategy
import org.neo4j.cypher.internal.config.CypherConfiguration
import org.neo4j.cypher.internal.frontend.phases.InternalUsageStats
import org.neo4j.cypher.internal.frontend.phases.InternalUsageStatsNoOp
import org.neo4j.cypher.internal.frontend.phases.ProcedureSignatureResolver
import org.neo4j.cypher.internal.frontend.phases.QueryLanguage
import org.neo4j.cypher.internal.frontend.phases.ScopedProcedureSignatureResolver
import org.neo4j.cypher.internal.notification.InternalNotificationLogger
import org.neo4j.cypher.internal.notification.RecordingNotificationLogger
import org.neo4j.cypher.internal.options.CypherExpressionEngineOption
import org.neo4j.cypher.internal.options.CypherRuntimeOption
import org.neo4j.cypher.internal.options.CypherVersionOption
import org.neo4j.cypher.internal.preparser.FullyParsedQuery
import org.neo4j.cypher.internal.preparser.PreParsedQuery
import org.neo4j.cypher.internal.preparser.QueryOptions
import org.neo4j.cypher.internal.util.CancellationChecker
import org.neo4j.cypher.rendering.QueryOptionsRenderer
import org.neo4j.fabric.cache.FabricQueryCache
import org.neo4j.fabric.config.FabricConfig
import org.neo4j.fabric.eval.Catalog
import org.neo4j.fabric.eval.UseEvaluation
import org.neo4j.fabric.pipeline.FabricFrontEnd
import org.neo4j.fabric.planning.FabricPlan.DebugOptions
import org.neo4j.fabric.planning.FabricQuery.LocalQuery
import org.neo4j.fabric.planning.FabricQuery.RemoteQuery
import org.neo4j.kernel.database.DatabaseReference
import org.neo4j.monitoring.Monitors
import org.neo4j.values.virtual.MapValue

case class FabricPlanner(
  config: FabricConfig,
  cypherConfig: CypherConfiguration,
  monitors: Monitors,
  cacheFactory: CaffeineCacheFactory
) {
  private[planning] val queryCache = new FabricQueryCache(cacheFactory, CacheSize.Dynamic(cypherConfig.queryCacheSize))

  private val frontend = FabricFrontEnd(cypherConfig, monitors, cacheFactory)
  private val initialCacheStrategy = CacheStrategy.default.withConfig(cypherConfig)

  /**
   * Convenience method without cancellation checker or InternalSyntaxUsageStats. Should be used for tests only.
   */
  def instance(
    signatureResolver: ProcedureSignatureResolver,
    queryString: String,
    queryParams: MapValue,
    sessionDatabase: DatabaseReference,
    catalog: Catalog,
    defaultLanguage: CypherVersion,
    shadowedFunctions: Set[String]
  ): PlannerInstance =
    instance(
      signatureResolver,
      queryString,
      queryParams,
      sessionDatabase,
      catalog,
      InternalUsageStatsNoOp,
      CancellationChecker.NeverCancelled,
      defaultLanguage,
      shadowedFunctions
    )

  def instance(
    signatureResolver: ProcedureSignatureResolver,
    queryString: String,
    queryParams: MapValue,
    sessionDatabase: DatabaseReference,
    catalog: Catalog,
    internalSyntaxUsageStats: InternalUsageStats,
    cancellationChecker: CancellationChecker,
    defaultLanguage: CypherVersion,
    shadowedFunctions: Set[String]
  ): PlannerInstance = {
    val notificationLogger = new RecordingNotificationLogger()

    val query = frontend.preParsing.preParse(queryString, notificationLogger, defaultLanguage, initialCacheStrategy)
    val cacheStrategy = initialCacheStrategy
      .updateFromQueryText(query.statement)
      .updateFromQueryOptions(query.options.queryOptions)
    PlannerInstance(
      ScopedProcedureSignatureResolver.from(
        signatureResolver,
        QueryLanguage.from(query.resolvedLanguage)
      ),
      query,
      queryParams,
      sessionDatabase,
      catalog,
      cancellationChecker,
      notificationLogger,
      internalSyntaxUsageStats,
      shadowedFunctions,
      cacheStrategy
    )
  }

  def testInstance(
    signatureResolver: ProcedureSignatureResolver,
    queryString: String,
    queryParams: MapValue,
    sessionDatabase: DatabaseReference,
    catalog: Catalog,
    defaultLanguage: CypherVersion
  ): PlannerInstance = {
    instance(
      signatureResolver,
      queryString,
      queryParams,
      sessionDatabase,
      catalog,
      defaultLanguage,
      Set.empty
    )
  }

  case class PlannerInstance(
    signatureResolver: ScopedProcedureSignatureResolver,
    query: PreParsedQuery,
    queryParams: MapValue,
    sessionDatabase: DatabaseReference,
    catalog: Catalog,
    cancellationChecker: CancellationChecker,
    notificationLogger: InternalNotificationLogger,
    internalSyntaxUsageStats: InternalUsageStats,
    shadowedFunctions: Set[String],
    cacheStrategy: CacheStrategy
  ) {

    private lazy val pipeline =
      frontend.Pipeline(
        signatureResolver,
        query,
        queryParams,
        cancellationChecker,
        notificationLogger,
        internalSyntaxUsageStats,
        sessionDatabase,
        shadowedFunctions
      )

    private val useHelper = new UseHelper(catalog, sessionDatabase.alias().name())

    private val sessionDatabaseAlias: String = sessionDatabase.alias().name()

    lazy val plan: FabricPlan = {
      val plan =
        if (cacheStrategy.astShouldBeCached) {
          queryCache.computeIfAbsent(
            query.cacheKey,
            queryParams,
            sessionDatabaseAlias,
            () => computePlan(),
            shouldCache,
            cypherConfig.useParameterSizeHint
          )
        } else {
          computePlan()
        }

      plan.copy(
        executionType = frontend.preParsing.executionType(query.options, plan.inCompositeContext),
        queryOptionsOffset = query.options.offset
      )
    }

    private def computePlan(): FabricPlan = trace {
      val prepared = pipeline.parseAndPrepare.process()

      val fragmenter =
        new FabricFragmenter(sessionDatabaseAlias, prepared.statement(), prepared.semantics())
      val fragments = fragmenter.fragment

      val compositeContext = useHelper.rootTargetsCompositeContext(fragments)

      val stitcher =
        FabricStitcher(
          query.statement,
          compositeContext,
          query.resolvedLanguage,
          pipeline,
          useHelper
        )
      val stitchedFragments = stitcher.convert(fragments)

      FabricPlan(
        query = stitchedFragments,
        queryType = QueryType.recursive(stitchedFragments),
        executionType = FabricPlan.Execute,
        queryString = query.statement,
        debugOptions = DebugOptions.noLogging(),
        obfuscationMetadata = prepared.obfuscationMetadata(),
        inCompositeContext = compositeContext,
        internalNotifications = pipeline.internalNotifications,
        queryOptionsOffset = query.options.offset,
        maybeResolvedParameters = prepared.maybeResolvedParams
      )
    }

    private def shouldCache(plan: FabricPlan): Boolean =
      !QueryType.sensitive(plan.query) && plan.maybeResolvedParameters.isEmpty

    private def optionsFor(fragment: Fragment) = {
      val languageOption = query.resolvedLanguage match {
        case CypherVersion.Cypher5  => CypherVersionOption.cypher5
        case CypherVersion.Cypher25 => CypherVersionOption.cypher25
      }
      if (useHelper.fragmentTargetsCompositeContext(fragment)) {
        val defaultOptions = QueryOptions.default(cypherConfig, query.resolvedLanguage)
        defaultOptions.copy(
          queryOptions = defaultOptions.queryOptions.copy(
            runtime = CypherRuntimeOption.slotted,
            expressionEngine = CypherExpressionEngineOption.interpreted,
            cypherVersion = languageOption
          ),
          materializedEntitiesMode = true,
          defaultLanguage = query.resolvedLanguage
        )
      } else {
        query.options.copy(
          queryOptions = query.options.queryOptions.copy(cypherVersion = languageOption),
          defaultLanguage = query.resolvedLanguage
        )
      }
    }

    private def trace(compute: => FabricPlan): FabricPlan = {
      val event = pipeline.traceStart()
      try compute
      finally event.close()
    }

    def asLocal(fragment: Fragment.Exec): LocalQuery = LocalQuery(
      FullyParsedQuery(fragment.localQuery, optionsFor(fragment)),
      fragment.queryType
    )

    def asRemote(fragment: Fragment.Exec): RemoteQuery = RemoteQuery(
      QueryOptionsRenderer.addOptions(fragment.remoteQuery.query, optionsFor(fragment)),
      fragment.queryType,
      fragment.remoteQuery.extractedLiterals
    )

    def targetsComposite(fragment: Fragment): Boolean =
      useHelper.fragmentTargetsCompositeContext(fragment)
  }
}

class UseHelper(catalog: Catalog, defaultContextName: String) {

  def rootTargetsCompositeContext(fragment: Fragment): Boolean = {
    // always resolve root databases strictly
    isComposite(CatalogName(true, defaultContextName)) || fragmentTargetsCompositeContext(fragment)
  }

  def fragmentTargetsCompositeContext(fragment: Fragment): Boolean = {
    def check(frag: Fragment): Boolean = frag match {
      case chain: Fragment.Chain     => useTargetsCompositeContext(chain.use)
      case union: Fragment.Union     => check(union.lhs) && check(union.rhs)
      case command: Fragment.Command => useTargetsCompositeContext(command.use)
    }

    check(fragment)
  }

  def useTargetsCompositeContext(use: Use): Boolean =
    UseEvaluation.evaluateStatic(use.graphSelection).exists(isComposite)

  private def isComposite(name: CatalogName): Boolean =
    catalog.resolveGraphOption(name) match {
      case Some(_: Catalog.Composite) => true
      case _                          => false
    }
}
