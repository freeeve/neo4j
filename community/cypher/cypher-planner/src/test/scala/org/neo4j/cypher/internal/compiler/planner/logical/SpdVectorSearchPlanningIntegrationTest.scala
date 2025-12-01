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

import org.neo4j.configuration.GraphDatabaseInternalSettings
import org.neo4j.configuration.GraphDatabaseInternalSettings.RemoteBatchPropertiesImplementation
import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.VectorSearch
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanConstructionTestSupport
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningIntegrationTestSupport
import org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder
import org.neo4j.cypher.internal.logical.plans.DoNotGetValue
import org.neo4j.cypher.internal.logical.plans.GetValue
import org.neo4j.cypher.internal.planner.spi.DatabaseMode
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class SpdVectorSearchPlanningIntegrationTest extends CypherFunSuite
    with LogicalPlanningIntegrationTestSupport
    with AstConstructionTestSupport
    with LogicalPlanConstructionTestSupport {

  protected val spdPlanner: StatisticsBackedLogicalPlanningConfigurationBuilder = plannerBuilder()
    .setDatabaseMode(DatabaseMode.SHARDED)
    .withSetting(
      GraphDatabaseInternalSettings.cypher_remote_batch_properties_implementation,
      RemoteBatchPropertiesImplementation.PLANNER
    )

  // Graph counts based on a subset of LDBC SF 1
  final protected val planBuilder = spdPlanner
    .addSemanticFeature(VectorSearch)
    .addNodeVectorIndex("messageContent", "Message", "content")
    .addRelationshipVectorIndex("knowsDescr", "KNOWS", "description")
    .setAllNodesCardinality(3181725)
    .setLabelCardinality("Message", 3055774)
    .setRelationshipCardinality("()-[:KNOWS]->()", 180623)

  test("should get the property value from node vector index when the property is used later in the horizon") {
    val planner = planBuilder.build()
    val query =
      """MATCH (m)
        |  SEARCH m IN (
        |    VECTOR INDEX messageContent
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN m.content
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query).stripProduceResults
    plan shouldEqual planner.subPlanBuilder()
      .projection("cacheN[m.content] AS `m.content`")
      .nodeVectorIndexSearch(
        "m",
        Seq("Message"),
        Seq("content"),
        "messageContent",
        "$embedding",
        "10",
        getValueFromIndex = Map("content" -> GetValue)
      )
      .build()
  }

  test(
    "should get the property value from relationship vector index when the property is used later in the horizon"
  ) {
    val planner = planBuilder.build()
    val query =
      """MATCH ()-[r]->()
        |  SEARCH r IN (
        |    VECTOR INDEX knowsDescr
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN r.description
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query).stripProduceResults
    plan shouldEqual
      planner.subPlanBuilder()
        .projection("cacheR[r.description] AS `r.description`")
        .relationshipVectorIndexSearch(
          "()-[r]->()",
          Seq("KNOWS"),
          Seq("description"),
          "knowsDescr",
          "$embedding",
          "10",
          getValueFromIndex = Map("description" -> GetValue)
        )
        .build()
  }

  test(
    "should NOT get the property value from relationship vector index when the property is NOT used later in the query"
  ) {
    val planner = planBuilder.build()
    val query =
      """MATCH ()-[r]->()
        |  SEARCH r IN (
        |    VECTOR INDEX knowsDescr
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN r.prop
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query).stripProduceResults
    plan shouldEqual
      planner.subPlanBuilder()
        .projection("cacheR[r.prop] AS `r.prop`")
        .remoteBatchProperties("cacheRFromStore[r.prop]")
        .relationshipVectorIndexSearch(
          "()-[r]->()",
          Seq("KNOWS"),
          Seq("description"),
          "knowsDescr",
          "$embedding",
          "10",
          getValueFromIndex = Map("description" -> DoNotGetValue)
        )
        .build()
  }

  test("should get the property value from node vector index when the property is used later in the same queryGraph") {
    val planner = planBuilder.build()
    val query =
      """MATCH (m) WHERE m.content CONTAINS "Malmo"
        |  SEARCH m IN (
        |    VECTOR INDEX messageContent
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN m
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query).stripProduceResults
    plan shouldEqual planner.subPlanBuilder()
      .filter("cacheN[m.content] CONTAINS 'Malmo'")
      .nodeVectorIndexSearch(
        "m",
        Seq("Message"),
        Seq("content"),
        "messageContent",
        "$embedding",
        "10",
        getValueFromIndex = Map("content" -> GetValue)
      )
      .build()
  }

  test(
    "should get the property value from node vector index when the property is used later after renaming the indexed variable"
  ) {
    val planner = planBuilder.build()
    val query =
      """MATCH (m)
        |  SEARCH m IN (
        |    VECTOR INDEX messageContent
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |WITH m as x
        |WHERE x.content CONTAINS "Malmo"
        |RETURN x.size
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query).stripProduceResults
    plan shouldEqual planner.subPlanBuilder()
      .projection(Map("x.size" -> cachedNodeProp("m", "size", "x")))
      .remoteBatchProperties(cachedNodeProp("m", "size", "x", knownToAccessStore = true))
      .filter(contains(cachedNodeProp("m", "content", "x"), literalString("Malmo")))
      .projection("m AS x")
      .nodeVectorIndexSearch(
        "m",
        Seq("Message"),
        Seq("content"),
        "messageContent",
        "$embedding",
        "10",
        getValueFromIndex = Map("content" -> GetValue)
      )
      .build()
  }

  test("should not get the property value from node vector index when the property is not used later in the query") {
    val planner = planBuilder.build()
    val query =
      """MATCH (m)
        |  SEARCH m IN (
        |    VECTOR INDEX messageContent
        |    FOR $embedding
        |    LIMIT 10
        |  )
        |RETURN m.length
        |""".stripMargin

    val plan = planner.plan(CypherVersion.Cypher25, query).stripProduceResults
    plan shouldEqual planner.subPlanBuilder()
      .projection("cacheN[m.length] AS `m.length`")
      .remoteBatchProperties("cacheNFromStore[m.length]")
      .nodeVectorIndexSearch(
        "m",
        Seq("Message"),
        Seq("content"),
        "messageContent",
        "$embedding",
        "10",
        getValueFromIndex = Map("content" -> DoNotGetValue)
      )
      .build()
  }
}
