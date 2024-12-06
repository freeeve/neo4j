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
import org.neo4j.cypher.internal.cache.LFUCache
import org.neo4j.cypher.internal.config.CypherConfiguration
import org.neo4j.cypher.internal.options.CypherConnectComponentsPlannerOption
import org.neo4j.cypher.internal.options.CypherExecutionMode
import org.neo4j.cypher.internal.options.CypherExpressionEngineOption
import org.neo4j.cypher.internal.options.CypherQueryOptions
import org.neo4j.cypher.internal.options.CypherRuntimeOption
import org.neo4j.cypher.internal.parser.SyntaxErrorListener
import org.neo4j.cypher.internal.parser.lexer.UnicodeEscapeReplacementReader.InvalidUnicodeLiteral
import org.neo4j.cypher.internal.preparser.CypherPreparserParser
import org.neo4j.cypher.internal.preparser.PreParsedQuery
import org.neo4j.cypher.internal.preparser.PreParsedStatement
import org.neo4j.cypher.internal.preparser.PreParserOption
import org.neo4j.cypher.internal.preparser.PreparserCypherLexer
import org.neo4j.cypher.internal.preparser.PreparserUtil
import org.neo4j.cypher.internal.preparser.QueryOptions
import org.neo4j.cypher.internal.preparser.StatefulPreparserListener
import org.neo4j.cypher.internal.preparser.javacc.CypherPreParser
import org.neo4j.cypher.internal.preparser.javacc.PreParserCharStream
import org.neo4j.cypher.internal.preparser.javacc.PreParserResult
import org.neo4j.cypher.internal.util.DeprecatedConnectComponentsPlannerPreParserOption
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.InternalNotificationLogger
import org.neo4j.cypher.internal.util.Neo4jCypherExceptionFactory
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
  preParserCache: LFUCache[String, PreParsedQuery]
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

  def insertIntoCache(queryText: String, preParsedQuery: PreParsedQuery): Unit = {
    preParserCache.put(queryText, preParsedQuery)
  }

  /**
   * Pre-parse a user-specified cypher query.
   *
   * @param queryText                   the query
   * @param notificationLogger          records notifications during pre parsing
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
    profile: Boolean = false,
    couldContainSensitiveFields: Boolean = false,
    targetsComposite: Boolean = false
  ): PreParsedQuery = {
    val preParsedQuery =
      if (couldContainSensitiveFields) { // This is potentially any outer query running on the system database
        preParse(queryText)
      } else {
        preParserCache.computeIfAbsent(queryText, preParse(queryText))
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

  @Deprecated
  def preParse(query: String, notifications: InternalNotificationLogger): PreParsedQuery = preParse(query)

  def preParse(query: String): PreParsedQuery =
    if (configuration.antlrPreparserEnabled) preParseAntlr(query)
    else preParseJavaCc(query)

  def preParseJavaCc(queryText: String): PreParsedQuery = {
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
    val preParsedStatement = PreParsedStatement(
      queryText.substring(preParserResult.position.offset),
      preParserResult.options.asScala.toList,
      preParserResult.position
    )

    val notifications = preParsedStatement.options.collect {
      case PreParserOption(key, _, pos) if key.toLowerCase(Locale.ROOT) == CypherConnectComponentsPlannerOption.key =>
        DeprecatedConnectComponentsPlannerPreParserOption(pos)
    }

    val options = PreParser.queryOptions(
      preParsedStatement.options,
      preParsedStatement.offset,
      configuration
    )

    PreParsedQuery(preParsedStatement.statement, queryText, options, notifications)
  }

  def preParseAntlr(queryText: String): PreParsedQuery = {
    val preParsedStatement = preParseQuery(queryText)
    val notifications = preParsedStatement.options.collect {
      case PreParserOption(key, _, pos) if key.toLowerCase(Locale.ROOT) == CypherConnectComponentsPlannerOption.key =>
        DeprecatedConnectComponentsPlannerPreParserOption(pos)
    }

    PreParsedQuery(
      preParsedStatement.statement,
      queryText,
      PreParser.queryOptions(preParsedStatement.options, preParsedStatement.offset, configuration),
      notifications
    )
  }

  def preParseQuery(queryText: String): PreParsedStatement = {
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

  // Used as an optimisation to shortcut preparsing.
  private def hasPreparserOptions(token: Int): Boolean = {
    token == CypherPreparserParser.CYPHER ||
    token == CypherPreparserParser.EXPLAIN ||
    token == CypherPreparserParser.PROFILE
  }
}

object PreParser {

  def queryOptions(
    preParsedOptions: List[PreParserOption],
    offset: InputPosition,
    configuration: CypherConfiguration
  ): QueryOptions = {

    val preParsedOptionsSet = preParsedOptions.map(o => (o.key, o.value)).toSet

    val options = CypherQueryOptions.fromValues(configuration, preParsedOptionsSet)

    QueryOptions(
      offset,
      options
    )
  }
}
