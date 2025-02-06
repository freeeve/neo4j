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
package org.neo4j.cypher.internal

import org.antlr.v4.runtime.BailErrorStrategy
import org.antlr.v4.runtime.CommonTokenStream
import org.neo4j.cypher.internal.PreParser.queryOptions
import org.neo4j.cypher.internal.cache.LFUCache
import org.neo4j.cypher.internal.config.CypherConfiguration
import org.neo4j.cypher.internal.options.CypherConnectComponentsPlannerOption
import org.neo4j.cypher.internal.options.CypherEagerAnalyzerOption
import org.neo4j.cypher.internal.options.CypherExecutionMode
import org.neo4j.cypher.internal.options.CypherExpressionEngineOption
import org.neo4j.cypher.internal.options.CypherQueryOptions
import org.neo4j.cypher.internal.options.CypherRuntimeOption
import org.neo4j.cypher.internal.options.CypherVersionOption
import org.neo4j.cypher.internal.parser.SyntaxErrorListener
import org.neo4j.cypher.internal.parser.lexer.UnicodeEscapeReplacementReader.InvalidUnicodeLiteral
import org.neo4j.cypher.internal.preparser.CypherPreparserParser
import org.neo4j.cypher.internal.preparser.PreParsedQuery
import org.neo4j.cypher.internal.preparser.PreParsedStatement
import org.neo4j.cypher.internal.preparser.PreparserCypherLexer
import org.neo4j.cypher.internal.preparser.PreparserUtil
import org.neo4j.cypher.internal.preparser.QueryOptions
import org.neo4j.cypher.internal.preparser.StatefulPreparserListener
import org.neo4j.cypher.internal.preparser.javacc.CypherPreParser
import org.neo4j.cypher.internal.preparser.javacc.PreParserCharStream
import org.neo4j.cypher.internal.preparser.javacc.PreParserResult
import org.neo4j.cypher.internal.util.DeprecatedConnectComponentsPlannerPreParserOption
import org.neo4j.cypher.internal.util.DeprecatedEagerAnalyzerPreParserOption
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.InternalNotification
import org.neo4j.cypher.internal.util.InternalNotificationLogger
import org.neo4j.cypher.internal.util.Neo4jCypherExceptionFactory
import org.neo4j.exceptions.InvalidCypherOption
import org.neo4j.exceptions.SyntaxException
import org.neo4j.gqlstatus.GqlHelper

import java.util.Locale

import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Preparses Cypher queries.
 *
 * The PreParser converts queries like
 *
 * 'CYPHER planner=cost runtime=slotted MATCH (n) RETURN n'
 *
 * into
 *
 * PreParsedQuery(
 *   statement: 'MATCH (n) RETURN n'
 *   options: QueryOptions(
 *     planner: 'cost'
 *     runtime: 'slotted'
 *   )
 * )
 */
class CachingPreParser(
  configuration: CypherConfiguration,
  preParserCache: LFUCache[PreParsedQuery.CacheKey, PreParsedQuery]
) extends PreParser(configuration) {

  /**
   * Clear the pre-parser query cache.
   *
   * @return the number of entries cleared
   */
  def clearCache(): Long = {
    PreparserUtil.clearDFACache()
    preParserCache.clear()
  }

  def insertIntoCache(preParsedQuery: PreParsedQuery): Unit = {
    preParserCache.put(preParsedQuery.preParserCacheKey, preParsedQuery)
  }

  /**
   * Pre-parse a user-specified cypher query.
   *
   * @param queryText                   the query
   * @param notificationLogger          records notifications during pre parsing
   * @param defaultLanguage             the database specific default query language
   * @param profile                     true if the query should be profiled even if profile is not given as a pre-parser option
   * @param couldContainSensitiveFields true if the query might contain passwords, like some administrative commands can
   * @param targetsComposite            true if the query targets a composite database
   * @throws SyntaxException if there are syntactic errors in the pre-parser options
   * @return the pre-parsed query
   */
  @throws(classOf[SyntaxException])
  def preParseQuery(
    queryText: String,
    notificationLogger: InternalNotificationLogger,
    defaultLanguage: CypherVersion,
    profile: Boolean = false,
    couldContainSensitiveFields: Boolean = false,
    targetsComposite: Boolean = false
  ): PreParsedQuery = {
    val preParsedQuery =
      if (couldContainSensitiveFields) { // This is potentially any outer query running on the system database
        preParse(queryText, defaultLanguage)
      } else {
        val key = PreParsedQuery.CacheKey(queryText, defaultLanguage)
        preParserCache.computeIfAbsent(key, preParse(queryText, defaultLanguage))
      }
    preParsedQuery.notifications.foreach(notificationLogger.log)
    if (profile) {
      preParsedQuery.copy(options = preParsedQuery.options.withExecutionMode(CypherExecutionMode.profile))
    } else if (targetsComposite) {
      preParsedQuery.copy(options =
        preParsedQuery.options.copy(
          queryOptions = preParsedQuery.options.queryOptions.copy(
            runtime = CypherRuntimeOption.slotted,
            expressionEngine = CypherExpressionEngineOption.interpreted
          ),
          materializedEntitiesMode = true
        )
      )
    } else {
      preParsedQuery
    }
  }
}

class PreParser(
  configuration: CypherConfiguration
) {

  private val EMPTY_QUERY_PARSER_EXCEPTION_MSG = "Unexpected end of input: expected CYPHER, EXPLAIN, PROFILE or Query"

  @Deprecated // To be removed soon
  def preParse(query: String, notifications: InternalNotificationLogger): PreParsedQuery =
    preParse(query, configuration.systemDefaultLanguage)

  @Deprecated // To be removed soon
  def preParse(query: String): PreParsedQuery = preParse(query, configuration.systemDefaultLanguage)

  /**
   * Pre-parse query.
   *
   * @param query the query
   * @param defaultLanguage the database specific default query language
   * @return
   */
  def preParse(query: String, defaultLanguage: CypherVersion): PreParsedQuery = {
    val preParsedStatement =
      if (configuration.antlrPreparserEnabled) preParseQuery(query)
      else preParseJavaCc(query)
    val notifications = preParserOptionsNotifications(preParsedStatement)
    val options = queryOptions(preParsedStatement, configuration, defaultLanguage)
    val preparsed = PreParsedQuery(preParsedStatement.statement, query, options, notifications)
    if (preparsed.resolvedLanguage.experimental && !configuration.enableExperimentalCypherVersions) {
      throw InvalidCypherOption.invalidOption(
        preparsed.resolvedLanguage.versionName,
        CypherVersionOption.name,
        CypherVersionOption.supportedValues.map(_.name): _*
      )
    }
    preparsed
  }

  private def preParseJavaCc(queryText: String): PreParsedStatement = {
    val exceptionFactory = new Neo4jASTExceptionFactory(Neo4jCypherExceptionFactory(queryText, None))
    if (queryText.isEmpty) {
      val gql = GqlHelper.getGql42001_42N45(0, 1, 1)
      throw exceptionFactory.syntaxException(
        gql,
        new IllegalStateException(PreParserResult.getEmptyQueryExceptionMsg),
        0,
        1,
        1
      )
    }
    val preParserResult = new CypherPreParser(exceptionFactory, new PreParserCharStream(queryText)).parse()
    PreParsedStatement(
      queryText.substring(preParserResult.position.offset),
      preParserResult.options.asScala.toList,
      preParserResult.position
    )
  }

  private def preParseQuery(queryText: String): PreParsedStatement = {
    val exceptionFactory = Neo4jCypherExceptionFactory(queryText, None)

    val tokenStream =
      try {
        val lexer =
          PreparserCypherLexer.fromString(queryText, false)
        lexer.removeErrorListeners()
        lexer.addErrorListener(new SyntaxErrorListener(exceptionFactory))
        new CommonTokenStream(lexer)
      } catch {
        case e: InvalidUnicodeLiteral =>
          throw exceptionFactory.syntaxException(
            GqlHelper.getGql42001_42I47(e.getMessage, e.offset, e.line, e.column),
            e.getMessage,
            InputPosition(e.offset, e.line, e.column)
          )
      }

    val (queryStart, settings) = if (hasPreparserOptions(tokenStream.LA(1))) {
      val preparser = new CypherPreparserParser(tokenStream)
      val statefulPreparserListener = new StatefulPreparserListener()
      preparser.addParseListener(statefulPreparserListener)
      preparser.setErrorHandler(new BailErrorStrategy)
      preparser.removeErrorListeners()

      preparser.preparserOptions()

      if (statefulPreparserListener.queryPosition.isEmpty) {
        throw exceptionFactory.syntaxException(
          GqlHelper.getGql42001_42N45(0, 1, 1),
          EMPTY_QUERY_PARSER_EXCEPTION_MSG,
          InputPosition(0, 1, 1)
        )
      }

      (statefulPreparserListener.queryPosition.get, statefulPreparserListener.settings.toList)
    } else if (queryText.isBlank) {
      throw exceptionFactory.syntaxException(
        GqlHelper.getGql42001_42N45(0, 1, 1),
        EMPTY_QUERY_PARSER_EXCEPTION_MSG,
        InputPosition(0, 1, 1)
      )
    } else {
      (InputPosition(0, 1, 1), List.empty)
    }

    PreParsedStatement(
      queryText.substring(queryStart.offset),
      settings,
      queryStart
    )

  }

  private def preParserOptionsNotifications(preParsedStatement: PreParsedStatement): List[InternalNotification] =
    preParsedStatement.options.flatMap { option =>
      option.key.toLowerCase(Locale.ROOT) match {
        case CypherConnectComponentsPlannerOption.key =>
          Some(DeprecatedConnectComponentsPlannerPreParserOption(option.position))
        case CypherEagerAnalyzerOption.key =>
          Some(DeprecatedEagerAnalyzerPreParserOption(option.position))
        case _ =>
          None
      }
    }

  // Used as an optimisation to shortcut preparsing.
  private def hasPreparserOptions(token: Int): Boolean = {
    token == CypherPreparserParser.CYPHER ||
    token == CypherPreparserParser.EXPLAIN ||
    token == CypherPreparserParser.PROFILE
  }
}

object PreParser {

  /**
   *
   * @param preparsed pre-parsed statement
   * @param configuration dbms configuration
   * @param defaultVersion database specific default query language version
   * @return
   */
  def queryOptions(
    preparsed: PreParsedStatement,
    configuration: CypherConfiguration,
    defaultVersion: CypherVersion
  ): QueryOptions = QueryOptions(
    offset = preparsed.offset,
    queryOptions = CypherQueryOptions.fromValues(configuration, preparsed.options.map(o => (o.key, o.value)).toSet),
    defaultLanguage = defaultVersion
  )
}
