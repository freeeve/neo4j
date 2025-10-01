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
package org.neo4j.cypher.internal.preparser

import org.neo4j.configuration.Config
import org.neo4j.cypher.internal.CachingPreParser
import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.TestExecutorCaffeineCacheFactory
import org.neo4j.cypher.internal.cache.LFUCache
import org.neo4j.cypher.internal.compiler.phases.CompilationPhases
import org.neo4j.cypher.internal.compiler.phases.CompilationPhases.ParsingConfig
import org.neo4j.cypher.internal.compiler.test_helpers.ContextHelper
import org.neo4j.cypher.internal.config.CypherConfiguration
import org.neo4j.cypher.internal.frontend.phases.InitialState
import org.neo4j.cypher.internal.notification.devNullLogger
import org.neo4j.cypher.internal.planner.spi.IDPPlannerName
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class InputQueryTest extends CypherFunSuite {

  private val preParser =
    new CachingPreParser(
      CypherConfiguration.fromConfig(Config.defaults()),
      new LFUCache[PreParsedQuery.CacheKey, PreParsedQuery](TestExecutorCaffeineCacheFactory, 0)
    )

  private def parser =
    CompilationPhases.parsing(ParsingConfig())

  private def toPreParsedQuery(queryString: String, version: CypherVersion) =
    preParser.preParseQuery(queryString, devNullLogger, version)

  // Note, only parse in default version
  private def toFullyParsedQuery(queryString: String, version: CypherVersion) = FullyParsedQuery(
    state = parser.transform(
      from = InitialState(queryString, IDPPlannerName, new AnonymousVariableNameGenerator),
      context = ContextHelper.create(version)
    ),
    options = QueryOptions.default(version)
  )

  test("same input string should have same cache keys (FullyParsedQuery)") {

    val queryString =
      """UNWIND [0, 1] AS x
        |MATCH (a)--(b)
        |RETURN x, a, b
        |""".stripMargin

    val a = toFullyParsedQuery(queryString, CypherVersion.Legacy.legacyVersion())
    val b = toFullyParsedQuery(queryString, CypherVersion.Legacy.legacyVersion())

    a.cacheKey shouldEqual b.cacheKey
    a.cacheKey.hashCode() shouldEqual b.cacheKey.hashCode()
  }

  test("same input string should have same cache keys (PreParsedQuery)") {

    val queryString =
      """UNWIND [0, 1] AS x
        |MATCH (a)--(b)
        |RETURN x, a, b
        |""".stripMargin

    val a = toPreParsedQuery(queryString, CypherVersion.Legacy.legacyVersion())
    val b = toPreParsedQuery(queryString, CypherVersion.Legacy.legacyVersion())

    a.cacheKey shouldEqual b.cacheKey
    a.cacheKey.hashCode() shouldEqual b.cacheKey.hashCode()
  }

}
