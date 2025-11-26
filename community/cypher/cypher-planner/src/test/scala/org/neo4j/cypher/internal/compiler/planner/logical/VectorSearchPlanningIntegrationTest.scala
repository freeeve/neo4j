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
package org.neo4j.cypher.internal.compiler.planner.logical

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.VectorSearch
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningIntegrationTestSupport
import org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.column
import org.neo4j.cypher.internal.logical.plans.DoNotGetValue
import org.neo4j.cypher.internal.logical.plans.GetValue
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.exceptions.SyntaxException
import org.neo4j.exceptions.VectorIndexSearchException

class VectorSearchPlanningIntegrationTest extends CypherFunSuite
    with LogicalPlanningIntegrationTestSupport
    with AstConstructionTestSupport {

  override protected def plannerBuilder(): StatisticsBackedLogicalPlanningConfigurationBuilder =
    super.plannerBuilder()
      .addSemanticFeature(VectorSearch)
      .setAllNodesCardinality(120)
      .setLabelCardinality("Movie", 120)
      .setRelationshipCardinality("()-[]->()", 100)
      .setRelationshipCardinality("()-[:ACTS_IN]->()", 50)
      .setRelationshipCardinality("(:Movie)-[]->()", 20)
      .addNodeVectorIndex("moviePlots", "Movie", "plot")
      .addRelationshipVectorIndex("actsInScript", "ACTS_IN", "script")
      .addNodeIndex("Movie", List("title"), 1.0, 1.0 / 120.0, isUnique = true)

  test("plan node vector index search") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH (movie:Movie) WHERE movie.plot IS NOT NULL
        |  SEARCH movie IN (
        |    VECTOR INDEX moviePlots
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN movie.plot""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("`movie.plot`")
        .projection("cacheN[movie.plot] AS `movie.plot`")
        .filter("cacheN[movie.plot] IS NOT NULL", "movie:Movie") // TODO: unnecessary filters. See PLAN-3052
        .nodeVectorIndexSearch(
          node = "movie",
          labelNames = Seq("Movie"),
          properties = Seq("plot"),
          indexName = "moviePlots",
          vector = "$embedding",
          limit = "10",
          score = "",
          argumentIds = Set(),
          getValueFromIndex = Map("plot" -> GetValue)
        )
        .build()
  }

  // TODO: this incorrectly gets rejected by semantic analysis, probably because of NameAllPatternElements
  //  See SURF-283
  ignore("plan node vector index search as part of a longer path pattern") {
    val planner = plannerBuilder().enablePrintCostComparisons().build()

    val query =
      """MATCH (movie:Movie)-->()
        |  SEARCH movie IN (
        |    VECTOR INDEX moviePlots
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN movie""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults(column("movie", "cacheN[movie.plot]"))
        .expandAll("(movie)-[`  UNNAMED0`]->(`  UNNAMED1`)")
        .filter("movie:Movie")
        .nodeVectorIndexSearch(
          "movie",
          Seq("Movie"),
          Seq("plot"),
          "moviePlots",
          "$embedding",
          "10",
          getValueFromIndex = Map("plot" -> GetValue)
        )
        .build()
  }

  test("plan node vector index search taking the embedding as an argument") {
    val planner = plannerBuilder().build()

    val query =
      """WITH [1,2,3,4,5] AS embedding
        |MATCH (movie:Movie)
        |  SEARCH movie IN (
        |    VECTOR INDEX moviePlots
        |    FOR embedding
        |    LIMIT 10
        |  )
        |RETURN movie""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults(column("movie", "cacheN[movie.plot]"))
        .filter("movie:Movie")
        .apply()
        .|.nodeVectorIndexSearch(
          node = "movie",
          labelNames = Seq("Movie"),
          properties = Seq("plot"),
          indexName = "moviePlots",
          vector = "embedding",
          limit = "10",
          argumentIds = Set("embedding"),
          getValueFromIndex = Map("plot" -> GetValue)
        )
        .projection("[1, 2, 3, 4, 5] AS embedding")
        .argument()
        .build()
  }

  test("plan node vector index search where the embedding contains a collect sub-query") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH (movie:Movie)
        |  SEARCH movie IN (
        |    VECTOR INDEX moviePlots
        |    FOR COLLECT { MATCH (m:Movie) RETURN m.releaseYear ORDER BY m.releaseYear }
        |    LIMIT 10
        |  )
        |RETURN movie""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults(column("movie", "cacheN[movie.plot]"))
        .filter("movie:Movie")
        .apply()
        .|.nodeVectorIndexSearch(
          "movie",
          Seq("Movie"),
          Seq("plot"),
          "moviePlots",
          "anon_0",
          "10",
          argumentIds = Set("anon_0"),
          getValueFromIndex = Map("plot" -> GetValue)
        )
        .rollUpApply("anon_0", "m.releaseYear")
        .|.sort("`m.releaseYear` ASC")
        .|.projection("m.releaseYear AS `m.releaseYear`")
        .|.nodeByLabelScan("m", "Movie")
        .argument()
        .build()
  }

  test("plan node vector index search, taking limit as an argument") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH (movie:Movie)
        |  SEARCH movie IN (
        |    VECTOR INDEX moviePlots
        |    FOR $embedding
        |    LIMIT $limit
        |  )
        |RETURN movie""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults(column("movie", "cacheN[movie.plot]"))
        .filter("movie:Movie")
        .nodeVectorIndexSearch(
          node = "movie",
          labelNames = Seq("Movie"),
          properties = Seq("plot"),
          indexName = "moviePlots",
          vector = "$embedding",
          limit = "$limit",
          getValueFromIndex = Map("plot" -> GetValue)
        )
        .build()
  }

  // TODO: see Example SQ2 in CIP-224
  //  See PLAN-2843
  ignore("plan node vector index search, where the embedding refers to the binding variable") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH (movie:Movie)
        |  SEARCH movie IN (
        |    VECTOR INDEX moviePlots
        |    FOR movie.titleEmbedding
        |    LIMIT 10
        |  )
        |  WHERE movie.title IN $titles
        |RETURN movie""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults(column("movie", "cacheN[movie.plot]"))
        .apply()
        .|.filter("anon_0 = movie")
        .|.nodeVectorIndexSearch(
          node = "anon_0",
          labelNames = Seq("Movie"),
          properties = Seq("plot"),
          indexName = "moviePlots",
          vector = "movie.titleEmbedding",
          limit = "10",
          argumentIds = Set("movie"),
          getValueFromIndex = Map("plot" -> GetValue)
        )
        .filter("movie.title IN $titles")
        .nodeByLabelScan("movie", "Movie")
        .build()
  }

  test("should not get the property value from the index when it is not needed afterwards") {
    val planner = plannerBuilder().build()
    // Vector index moviePlots is on (label: "Movie", property: "plot")
    // The property "plot" is not needed in the rest of the query
    val query =
      """MATCH (movie:Movie) WHERE movie.p1 IS NOT NULL
        |  SEARCH movie IN (
        |    VECTOR INDEX moviePlots
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN movie.p1""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("`movie.p1`")
        .projection("cacheN[movie.p1] AS `movie.p1`")
        .filter(
          "cacheNFromStore[movie.p1] IS NOT NULL",
          "movie:Movie"
        ) // TODO: unnecessary filter 'movie:Movie'. See PLAN-3052
        .nodeVectorIndexSearch(
          "movie",
          Seq("Movie"),
          Seq("plot"),
          "moviePlots",
          "$embedding",
          "10",
          getValueFromIndex = Map("plot" -> DoNotGetValue)
        )
        .build()
  }

  test(
    "plan node vector index search using a relationship variable as binding variable should give GQL error 22G03 with cause 22N01"
  ) {
    val planner = plannerBuilder().build()

    val query =
      """MATCH ()-[r:ACTS_IN]->()
        |  SEARCH r IN (
        |    VECTOR INDEX moviePlots
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN r""".stripMargin

    val caughtException = intercept[VectorIndexSearchException] {
      planner.plan(CypherVersion.Cypher25, query)
    }
    caughtException.gqlStatus() should be("22G03")
    caughtException.statusDescription() should be("error: data exception - invalid value type")
    caughtException.legacyMessage() should be(
      "22N01: Expected the value `r` to be of type NODE, but was of type RELATIONSHIP."
    )

    val caughtExceptionCause = caughtException.cause()
    caughtExceptionCause.isEmpty should be(false)
    caughtExceptionCause.get.gqlStatus() should be("22N01")
    caughtExceptionCause.get.statusDescription() should be(
      "error: data exception - invalid type. Expected the value `r` to be of type NODE, but was of type RELATIONSHIP."
    )
    caughtExceptionCause.get.cause().isEmpty should be(true)
  }

  test(
    "plan relationship vector index search using a node variable as binding variable should give GQL error 22G03 with cause 22N01"
  ) {
    val planner = plannerBuilder().build()

    val query =
      """MATCH (r:Movie)
        |  SEARCH r IN (
        |    VECTOR INDEX actsInScript
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN r""".stripMargin

    val caughtException = intercept[VectorIndexSearchException] {
      planner.plan(CypherVersion.Cypher25, query)
    }
    caughtException.gqlStatus() should be("22G03")
    caughtException.statusDescription() should be("error: data exception - invalid value type")
    caughtException.legacyMessage() should be(
      "22N01: Expected the value `r` to be of type RELATIONSHIP, but was of type NODE."
    )

    val caughtExceptionCause = caughtException.cause()
    caughtExceptionCause.isEmpty should be(false)
    caughtExceptionCause.get.gqlStatus() should be("22N01")
    caughtExceptionCause.get.statusDescription() should be(
      "error: data exception - invalid type. Expected the value `r` to be of type RELATIONSHIP, but was of type NODE."
    )
    caughtExceptionCause.get.cause().isEmpty should be(true)
  }

  test(
    "plan node vector index search using a path variable as binding variable should give GQL error 22G03 with cause 22N01"
  ) {
    val planner = plannerBuilder().build()

    val query =
      """MATCH p=()-[r:ACTS_IN]->()
        |  SEARCH p IN (
        |    VECTOR INDEX actsInScript
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN p""".stripMargin

    val caughtException = intercept[SyntaxException] {
      planner.plan(CypherVersion.Cypher25, query)
    }
    caughtException.gqlStatus() should be("22G03")
    caughtException.statusDescription() should be("error: data exception - invalid value type")
    caughtException.legacyMessage() should be(
      """Type mismatch: expected Node or Relationship but was Path (line 2, column 10 (offset: 36))
        |"  SEARCH p IN ("
        |          ^""".stripMargin
    )

    val caughtExceptionCause = caughtException.cause()
    caughtExceptionCause.isEmpty should be(false)
    caughtExceptionCause.get.gqlStatus() should be("22N27")
    caughtExceptionCause.get.statusDescription() should be(
      "error: data exception - invalid entity type. Invalid input 'PATH' for `p`. Expected to be NODE or RELATIONSHIP."
    )
    caughtExceptionCause.get.cause().isEmpty should be(true)
  }

  test(
    "plan node vector index search using a node variable (hidden from the planner) passed as a relationship variable should give GQL error 22G03 with cause 22N01"
  ) {
    val planner = plannerBuilder().build()

    val query =
      """MATCH (n)-[r]->()
        |WITH coalesce(n, r) as x
        |MATCH ()-[x]->()
        | SEARCH x IN (
        |    VECTOR INDEX moviePlots
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN x.prop""".stripMargin

    val caughtException = intercept[VectorIndexSearchException] {
      planner.plan(CypherVersion.Cypher25, query)
    }
    caughtException.gqlStatus() should be("22G03")
    caughtException.statusDescription() should be("error: data exception - invalid value type")
    caughtException.legacyMessage() should be(
      "22N01: Expected the value `x` to be of type NODE, but was of type RELATIONSHIP."
    )

    val caughtExceptionCause = caughtException.cause()
    caughtExceptionCause.isEmpty should be(false)
    caughtExceptionCause.get.gqlStatus() should be("22N01")
    caughtExceptionCause.get.statusDescription() should be(
      "error: data exception - invalid type. Expected the value `x` to be of type NODE, but was of type RELATIONSHIP."
    )
    caughtExceptionCause.get.cause().isEmpty should be(true)
  }
}
