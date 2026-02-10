/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.frontend.phases

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.CypherVersionTestSupport
import org.neo4j.cypher.internal.frontend.helpers.ErrorCollectingContext
import org.neo4j.cypher.internal.frontend.helpers.NoPlannerName
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.CollectSyntaxUsageMetrics
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.Parse
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class CollectSyntaxUsageMetricsTest extends CypherFunSuite with CypherVersionTestSupport {

  private def pipeline =
    Parse andThen CollectSyntaxUsageMetrics

  testVersions("should find multiple things in one query") { version =>
    val stats = runPipeline(
      version,
      """
        |MATCH ANY SHORTEST (a)-->*(b)
        |MATCH ANY SHORTEST (c)-->*(d)
        |WITH shortestPath( (a)-[*]->(d) ) AS p
        |RETURN *
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.GPM_SHORTEST) should be(2)
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.LEGACY_SHORTEST) should be(1)
  }

  testVersions("should find GPM SHORTEST") { version =>
    val stats = runPipeline(
      version,
      """
        |MATCH ANY SHORTEST (a)-->*(b)
        |RETURN *
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.GPM_SHORTEST) should be(1)
  }

  testVersions("should find LEGACY SHORTEST in MATCH") { version =>
    val stats = runPipeline(
      version,
      """
        |MATCH shortestPath( (a)-[*]->(b) )
        |RETURN *
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.LEGACY_SHORTEST) should be(1)
  }

  testVersions("should find LEGACY SHORTEST in WITH") { version =>
    val stats = runPipeline(
      version,
      """
        |MATCH (a), (b) WITH shortestPath( (a)-[*]->(b) ) AS p
        |RETURN *
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.LEGACY_SHORTEST) should be(1)
  }

  testVersions("should find COLLECT subquery") { version =>
    val stats = runPipeline(
      version,
      """
        |RETURN COLLECT { MATCH (a) RETURN a } AS as
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.COLLECT_SUBQUERY) should be(1)
  }

  testVersions("should find COUNT subquery") { version =>
    val stats = runPipeline(
      version,
      """
        |RETURN COUNT { MATCH (a) RETURN a } AS as
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.COUNT_SUBQUERY) should be(1)
  }

  testVersions("should find EXISTS subquery") { version =>
    val stats = runPipeline(
      version,
      """
        |RETURN EXISTS { MATCH (a) RETURN a } AS as
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.EXISTS_SUBQUERY) should be(1)
  }

  testVersions("should find QPP") { version =>
    val stats = runPipeline(
      version,
      """
        |MATCH ANY SHORTEST (a)( ()-->() )*(b)
        |RETURN *
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.QUANTIFIED_PATH_PATTERN) should be(1)
  }

  testVersions("should find QPP syntactic sugar") { version =>
    val stats = runPipeline(
      version,
      """
        |MATCH ANY SHORTEST (a)-->*(b)
        |RETURN *
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.QUANTIFIED_PATH_PATTERN) should be(1)
  }

  testVersions("should find Repeatable Elements") { version =>
    val stats = runPipeline(
      version,
      """
        |MATCH REPEATABLE ELEMENTS (a)( ()-->() )*(b)
        |MATCH REPEATABLE ELEMENTS ANY SHORTEST (a)( ()-->() )*(b)
        |RETURN *
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.REPEATABLE_ELEMENTS) should be(2)
  }

  testVersionsExcept5("should find LET clause") { version =>
    val stats = runPipeline(
      version,
      """
        |LET x = 1
        |RETURN x
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.LET_CLAUSE) should be(1)
  }

  testVersionsExcept5("should find FILTER clause") { version =>
    val stats = runPipeline(
      version,
      """
        |LET x = 1
        |FILTER x > 0
        |RETURN x
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.LET_CLAUSE) should be(1)
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.FILTER_CLAUSE) should be(1)
  }

  testVersionsExcept5("should find Importing with correlated") { version =>
    val stats = runPipeline(
      version,
      """
        |
        |LET x = 1
        |CALL {
        |   WITH x
        |   RETURN x + 1 AS y
        |}
        |RETURN y
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.LET_CLAUSE) should be(1)
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.IMPORTING_WITH_SUBQUERY) should be(1)
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.IMPORTING_WITH_SUBQUERY_CORRELATED) should be(1)
  }

  testVersionsExcept5("should find Importing with uncorrelated") { version =>
    val stats = runPipeline(
      version,
      """
        |
        |LET x = 1
        |CALL {
        |   RETURN 1 AS y
        |}
        |RETURN y
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.LET_CLAUSE) should be(1)
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.IMPORTING_WITH_SUBQUERY) should be(1)
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.IMPORTING_WITH_SUBQUERY_UNCORRELATED) should be(1)
  }

  testVersionsExcept5("should find Scope clause") { version =>
    val stats = runPipeline(
      version,
      """
        |LET x = 1
        |CALL (x) {
        |   RETURN x + 1 AS y
        |}
        |RETURN y
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.LET_CLAUSE) should be(1)
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.SCOPE_CLAUSE_SUBQUERY) should be(1)
  }

  testVersionsExcept5("should find Conditional Query") { version =>
    val stats = runPipeline(
      version,
      """
        |WHEN true THEN RETURN 1 AS y
        |WHEN false THEN RETURN 2 AS y
        |ELSE RETURN 3 AS y
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.CONDITIONAL_QUERY) should be(1)
  }

  testVersionsExcept5("should find NEXT statement") { version =>
    val stats = runPipeline(
      version,
      """
        |RETURN 1 AS x
        |
        |NEXT
        |
        |RETURN x + 1 AS y
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.NEXT_STATEMENT) should be(1)
  }

  testVersionsExcept5("should find SEARCH clause without filters") { version =>
    val stats = runPipeline(
      version,
      """
        |MATCH (movie)
        |  WHERE movie.rating > 7
        |  SEARCH movie IN (
        |    VECTOR INDEX idx
        |    FOR $embedding
        |    LIMIT 5
        |  )
        |RETURN movie.title AS title
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.SEARCH_WITHOUT_FILTERS) should be(1)
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.SEARCH_WITH_FILTERS) should be(0)
  }

  testVersionsExcept5("should find SEARCH clause with filters") { version =>
    val stats = runPipeline(
      version,
      """
        |MATCH (movie)
        |  WHERE movie.rating > 7
        |  SEARCH movie IN (
        |    VECTOR INDEX idx
        |    FOR $embedding
        |    WHERE movie.rating > 8
        |    LIMIT 5
        |  )
        |RETURN movie.title AS title
        |""".stripMargin
    )
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.SEARCH_WITHOUT_FILTERS) should be(0)
    stats.getSyntaxUsageCount(SyntaxUsageMetricKey.SEARCH_WITH_FILTERS) should be(1)
  }

  private def runPipeline(version: CypherVersion, query: String): InternalUsageStats = {
    val startState = InitialState(query, NoPlannerName, new AnonymousVariableNameGenerator)
    val context = new ErrorCollectingContext(version, query = query) {
      override val internalUsageStats: InternalUsageStats = InternalUsageStats.newImpl()
    }
    pipeline.transform(startState, context)

    context.internalUsageStats
  }
}
