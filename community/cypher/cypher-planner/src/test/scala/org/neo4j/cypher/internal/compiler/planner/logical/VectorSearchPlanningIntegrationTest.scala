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
import org.neo4j.cypher.internal.expressions.SemanticDirection.OUTGOING
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.column
import org.neo4j.cypher.internal.logical.plans.DoNotGetValue
import org.neo4j.cypher.internal.logical.plans.GetValue
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.exceptions.InternalException
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
      .setRelationshipCardinality("()-[:CONTRIBUTED]->()", 8)
      .setRelationshipCardinality("()-[:CONTRIBUTED]->(:Movie)", 7)
      .setRelationshipCardinality("(:Movie)-[]->()", 20)
      .addNodeVectorIndex("moviePlots", "Movie", "plot")
      .addRelationshipVectorIndex("actsInScript", "ACTS_IN", "script")
      .addRelationshipVectorIndex("actsInScript", "ACTS_IN", "script")
      .addRelationshipVectorIndex("contributed", "CONTRIBUTED", "embedding")
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
          argumentIds = Set(),
          getValueFromIndex = Map("plot" -> GetValue)
        )
        .build()
  }

  test("plan node vector index search as part of a longer path pattern") {
    val planner = plannerBuilder().build()

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
        .expandAll("(movie)-[]->()")
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

  test("plan node vector index search projecting the score variable") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH (movie:Movie)
        |  SEARCH movie IN (
        |    VECTOR INDEX moviePlots
        |    FOR $embedding
        |    LIMIT 10
        |  ) SCORE as similarity
        |RETURN movie, similarity""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults(column("movie", "cacheN[movie.plot]"), column("similarity"))
        .filter("movie:Movie")
        .nodeVectorIndexSearch(
          node = "movie",
          labelNames = Seq("Movie"),
          properties = Seq("plot"),
          indexName = "moviePlots",
          vector = "$embedding",
          limit = "10",
          score = "similarity",
          argumentIds = Set(),
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

  test("plan relationship vector index search") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH ()-[r]->()
        |  SEARCH r IN (
        |    VECTOR INDEX actsInScript
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN r.plot""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("`r.plot`")
        .projection("r.plot AS `r.plot`")
        .relationshipVectorIndexSearch(
          "()-[r]->()",
          Seq("ACTS_IN"),
          Seq("script"),
          "actsInScript",
          "$embedding",
          "10"
        )
        .build()
  }

  test("plan relationship vector index search and get and cache property value from index") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH ()-[r]->()
        |  SEARCH r IN (
        |    VECTOR INDEX actsInScript
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN r""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults(column("r", "cacheR[r.script]"))
        .relationshipVectorIndexSearch(
          "()-[r]->()",
          Seq("ACTS_IN"),
          Seq("script"),
          "actsInScript",
          "$embedding",
          "10",
          "",
          Set(),
          Map("script" -> GetValue)
        )
        .build()
  }

  test("plan undirected relationship vector index search and get and cache property value from index") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH ()-[r]-()
        |  SEARCH r IN (
        |    VECTOR INDEX actsInScript
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN r""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults(column("r", "cacheR[r.script]"))
        .relationshipVectorIndexSearch(
          "()-[r]-()",
          Seq("ACTS_IN"),
          Seq("script"),
          "actsInScript",
          "$embedding",
          "10",
          "",
          Set(),
          Map("script" -> GetValue)
        )
        .build()
  }

  test("plan incoming directed relationship vector index search") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH ()<-[r]-()
        |  SEARCH r IN (
        |    VECTOR INDEX actsInScript
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN r.plot""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("`r.plot`")
        .projection("r.plot AS `r.plot`")
        .relationshipVectorIndexSearch(
          "()<-[r]-()",
          Seq("ACTS_IN"),
          Seq("script"),
          "actsInScript",
          "$embedding",
          "10"
        )
        .build()
  }

  test("plan undirected relationship vector index search") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH ()-[r]-()
        |  SEARCH r IN (
        |    VECTOR INDEX actsInScript
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN r.plot""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("`r.plot`")
        .projection("r.plot AS `r.plot`")
        .relationshipVectorIndexSearch(
          "()-[r]-()",
          Seq("ACTS_IN"),
          Seq("script"),
          "actsInScript",
          "$embedding",
          "10"
        )
        .build()
  }

  test("plan relationship vector index on RHS of apply - using WITH r SKIP 0") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH (:Movie {title: "Matrix"})<-[r:CONTRIBUTED]-()
        |WITH r SKIP 0
        |MATCH ()-[c]->()
        |  SEARCH c IN (
        |    VECTOR INDEX contributed
        |    FOR r.embedding
        |    LIMIT 5
        |  )
        |RETURN c.description AS contributionDesc""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("contributionDesc")
        .projection("c.description AS contributionDesc")
        .apply()
        .|.relationshipVectorIndexSearch(
          "()-[c]->()",
          Seq("CONTRIBUTED"),
          Seq("embedding"),
          "contributed",
          "r.embedding",
          "5",
          "",
          Set("r")
        )
        .skip(0)
        .expandAll("(anon_0)<-[r:CONTRIBUTED]-()")
        .nodeIndexOperator("anon_0:Movie(title = 'Matrix')", unique = true)
        .build()
  }

  // TODO see PLAN-2843
  test("plan relationship vector index on RHS of apply") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH (:Movie {title: "Matrix"})<-[r:CONTRIBUTED]-()
        |MATCH ()-[c]->()
        |  SEARCH c IN (
        |    VECTOR INDEX contributed
        |    FOR r.embedding
        |    LIMIT 5
        |  )
        |RETURN c.description AS contributionDesc""".stripMargin

    // Currently this gives an assertion error in VerifyBestPlan
    intercept[InternalException] {
      planner.plan(CypherVersion.Cypher25, query)
    }

    // TODO: It should be something like the following, instead of the AssertionError
//    val plan = planner.plan(CypherVersion.Cypher25, query)
//
//    plan shouldEqual
//      planner.planBuilder()
//        .produceResults("contributionDesc")
//        .projection("c.description AS contributionDesc")
//        .apply()
//        .|.relationshipVectorIndexSearch(
//          "()-[c]->()",
//          Seq("CONTRIBUTED"),
//          Seq("embedding"),
//          "contributed",
//          "r.embedding",
//          "5",
//          "",
//          Set("r")
//        )
//        .expandAll("(anon_0)<-[r:CONTRIBUTED]-()")
//        .nodeIndexOperator("anon_0:Movie(title = 'Matrix')", unique = true)
//        .build()
  }

  test("plan relationship vector index search with predicates on relationship") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH ()-[r]->() WHERE r.prop > 50 and r.prop2 CONTAINS "running"
        |  SEARCH r IN (
        |    VECTOR INDEX actsInScript
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN r.plot""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("`r.plot`")
        .projection("r.plot AS `r.plot`")
        .filter("r.prop2 CONTAINS 'running'", "r.prop > 50")
        .relationshipVectorIndexSearch(
          "()-[r]->()",
          Seq("ACTS_IN"),
          Seq("script"),
          "actsInScript",
          "$embedding",
          "10"
        )
        .build()
  }

  ignore("plan vector index using another variable in the embedding") {
    // Currently cartesian products do not allow vector search to be planned later
    val planner = plannerBuilder()
      .setLabelCardinality("Person", 120)
      .build()

    val query =
      """MATCH (person:Person)
        |MATCH (movie:Movie)
        |  SEARCH movie IN (
        |    VECTOR INDEX moviePlots
        |    FOR person.embedding
        |    LIMIT 10
        |  )
        |RETURN movie, person""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults(column("movie", "cacheN[movie.plot]"), column("person"))
        .apply()
        .|.filter("movie:Movie")
        .|.nodeVectorIndexSearch(
          "movie",
          Seq("Movie"),
          Seq("plot"),
          "moviePlots",
          "$embedding",
          "person.limit",
          argumentIds = Set("person"),
          getValueFromIndex = Map("plot" -> GetValue)
        )
        .allNodeScan("person")
        .build()
  }

  test("should support using a returned score in subsequent predicates") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH (movie:Movie) WHERE movie.plot IS NOT NULL
        |  SEARCH movie IN (
        |    VECTOR INDEX moviePlots
        |    FOR $embedding
        |    LIMIT 10
        |  ) SCORE AS similarity
        |WITH movie, similarity
        |WHERE similarity > 0.8
        |RETURN movie.plot, similarity""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("`movie.plot`", "similarity")
        .projection("cacheN[movie.plot] AS `movie.plot`")
        .filter("similarity > 0.8", "cacheN[movie.plot] IS NOT NULL", "movie:Movie")
        .nodeVectorIndexSearch(
          node = "movie",
          labelNames = Seq("Movie"),
          properties = Seq("plot"),
          indexName = "moviePlots",
          vector = "$embedding",
          limit = "10",
          score = "similarity",
          argumentIds = Set(),
          getValueFromIndex = Map("plot" -> GetValue)
        )
        .build()
  }

  test("should not use the countStore to count when using vector search") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH (movie:Movie)
        |  SEARCH movie IN (
        |    VECTOR INDEX moviePlots
        |    FOR $embedding
        |    LIMIT 10
        |  ) SCORE AS similarity
        |RETURN count(*)""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("`count(*)`")
        .aggregation(Seq(), Seq("count(*) AS `count(*)`"))
        .filter("movie:Movie")
        .nodeVectorIndexSearch(
          "movie",
          Seq("Movie"),
          Seq("plot"),
          "moviePlots",
          "$embedding",
          "10",
          "similarity",
          Set(),
          Map("plot" -> DoNotGetValue)
        )
        .build()
  }

  test("should use getDegree when using vector search") {
    val planner = plannerBuilder().build()

    val query =
      """MATCH (movie:Movie)
        |  WHERE NOT (movie)-[]->()
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
        .filter(not(hasDegreeGreater("movie", OUTGOING, literalInt(0))), "movie:Movie")
        .nodeVectorIndexSearch(
          "movie",
          Seq("Movie"),
          Seq("plot"),
          "moviePlots",
          "$embedding",
          "10",
          "",
          Set(),
          Map("plot" -> GetValue)
        )
        .build()
  }
}
