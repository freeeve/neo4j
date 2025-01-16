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
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.VariableStringInterpolator
import org.neo4j.cypher.internal.compiler.ExecutionModel
import org.neo4j.cypher.internal.compiler.ExecutionModel.BatchedParallel
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningIntegrationTestSupport
import org.neo4j.cypher.internal.expressions.ExplicitParameter
import org.neo4j.cypher.internal.expressions.HasDegreeGreaterThan
import org.neo4j.cypher.internal.expressions.SemanticDirection.INCOMING
import org.neo4j.cypher.internal.expressions.SemanticDirection.OUTGOING
import org.neo4j.cypher.internal.ir.SelectivePathPattern.CountInteger
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.Predicate
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createNode
import org.neo4j.cypher.internal.logical.builder.TestNFABuilder
import org.neo4j.cypher.internal.logical.plans.DoNotGetValue
import org.neo4j.cypher.internal.logical.plans.Expand.ExpandAll
import org.neo4j.cypher.internal.logical.plans.Expand.ExpandInto
import org.neo4j.cypher.internal.logical.plans.GetValue
import org.neo4j.cypher.internal.logical.plans.IndexOrderAscending
import org.neo4j.cypher.internal.logical.plans.IndexOrderDescending
import org.neo4j.cypher.internal.logical.plans.StatefulShortestPath
import org.neo4j.cypher.internal.planner.spi.DatabaseMode
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class RemoteBatchPropertiesUsingPlannerPlanningIntegrationTest
    extends AbstractRemoteBatchPropertiesUsingPlannerPlanningIntegrationTest(ExecutionModel.default) {

  test("should batch properties for ordered aggregations") {
    val query =
      """MATCH (person)
        |WITH person ORDER BY person.age
        |RETURN person.name, person.age, count(person.age)""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("`person.name`", "`person.age`", "`count(person.age)`")
      .orderedAggregation(
        Seq("cacheN[person.name] AS `person.name`", "cacheN[person.age] AS `person.age`"),
        Seq("count(cacheN[person.age]) AS `count(person.age)`"),
        Seq("cacheN[person.age]")
      )
      .remoteBatchProperties("cacheNFromStore[person.name]")
      .sort("`person.age` ASC")
      .projection("cacheN[person.age] AS `person.age`")
      .remoteBatchProperties("cacheNFromStore[person.age]")
      .allNodeScan("person")
      .build()
  }

  test("should propagate cached properties for an ordered distinct projection") {
    val query =
      """
        |MATCH (n:Person)
        |WHERE n.firstName = 'foo'
        |RETURN DISTINCT n.firstName""".stripMargin
    val plan = planner.plan(query).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .orderedDistinct(Seq("cacheN[n.firstName]"), "cacheN[n.firstName] AS `n.firstName`")
      .nodeIndexOperator(
        "n:Person(firstName = 'foo')",
        indexOrder = IndexOrderAscending,
        getValue = Map("firstName" -> GetValue)
      )
      .build())
  }

  test("should batch properties when finding triadic selections") {
    val query =
      """
        |MATCH (n:Person {firstName: "Dave"})-[:KNOWS]-(friend:Person)-[:KNOWS]-(friendOfFriend:Person)
        |WHERE NOT (n)-[:KNOWS]-(friendOfFriend)
        |RETURN n. firstName, n.lastName, friendOfFriend.firstName, friendOfFriend.lastName""".stripMargin
    val plan = planner.plan(query).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .projection(
        "cacheN[n.firstName] AS `n. firstName`",
        "cacheN[n.lastName] AS `n.lastName`",
        "cacheN[friendOfFriend.firstName] AS `friendOfFriend.firstName`",
        "cacheN[friendOfFriend.lastName] AS `friendOfFriend.lastName`"
      )
      .remoteBatchProperties(
        "cacheNFromStore[friendOfFriend.firstName]",
        "cacheNFromStore[friendOfFriend.lastName]"
      )
      .filter("NOT anon_1 = anon_0", "friendOfFriend:Person")
      .triadicSelection(positivePredicate = false, "n", "friend", "friendOfFriend")
      .|.expandAll("(friend)-[anon_1:KNOWS]-(friendOfFriend)")
      .|.argument("friend", "anon_0")
      .filter("friend:Person")
      .expandAll("(n)-[anon_0:KNOWS]-(friend)")
      .remoteBatchProperties("cacheNFromStore[n.lastName]")
      .nodeIndexOperator("n:Person(firstName = 'Dave')", getValue = Map("firstName" -> GetValue))
      .build())
  }

  test("should leverage order for max queries instead of using aggregation") { // index_backed_order_by:q23
    val query =
      """
        |MATCH (msg:Message)
        |WHERE msg.creationDate > $min_creation_date
        |RETURN max(msg.creationDate)
        |""".stripMargin

    planner.plan(query).stripProduceResults shouldEqual planner
      .subPlanBuilder()
      .optional()
      .limit(1)
      .projection("cacheN[msg.creationDate] AS `max(msg.creationDate)`")
      .nodeIndexOperator(
        "msg:Message(creationDate > ???)",
        paramExpr = Some(parameter("min_creation_date", CTAny)),
        getValue = Map("creationDate" -> GetValue),
        indexOrder = IndexOrderDescending
      )
      .build()
  }

  test("should leverage order for min queries instead of using aggregation") {
    val query =
      """
        |MATCH (msg:Message)
        |WHERE msg.creationDate > $min_creation_date
        |RETURN min(msg.creationDate)
        |""".stripMargin

    planner.plan(query).stripProduceResults shouldEqual planner
      .subPlanBuilder()
      .optional()
      .limit(1)
      .projection("cacheN[msg.creationDate] AS `min(msg.creationDate)`")
      .nodeIndexOperator(
        "msg:Message(creationDate > ???)",
        paramExpr = Some(parameter("min_creation_date", CTAny)),
        getValue = Map("creationDate" -> GetValue),
        indexOrder = IndexOrderAscending
      )
      .build()
  }
}

class ParallelRuntimeRemoteBatchPropertiesPlanningIntegrationTest
    extends AbstractRemoteBatchPropertiesUsingPlannerPlanningIntegrationTest(BatchedParallel(1, 2))

abstract class AbstractRemoteBatchPropertiesUsingPlannerPlanningIntegrationTest(executionModel: ExecutionModel)
    extends CypherFunSuite
    with LogicalPlanningIntegrationTestSupport with AstConstructionTestSupport {

  protected val spdPlanner = plannerBuilder()
    .setDatabaseMode(DatabaseMode.SHARDED)
    .withSetting(
      GraphDatabaseInternalSettings.cypher_remote_batch_properties_implementation,
      RemoteBatchPropertiesImplementation.PLANNER
    )

  // Graph counts based on a subset of LDBC SF 1
  final protected val planner = spdPlanner
    .setAllNodesCardinality(3181725)
    .setLabelCardinality("Person", 9892)
    .setLabelCardinality("Message", 3055774)
    .setLabelCardinality("Post", 1003605)
    .setLabelCardinality("Comment", 2052169)
    .setLabelCardinality("Country", 111)
    .setLabelCardinality("City", 1343)
    .setLabelCardinality("Tag", 16080)
    .setLabelCardinality("TagClass", 71)
    .setRelationshipCardinality("()-[:KNOWS]->()", 180623)
    .setRelationshipCardinality("(:Person)-[:KNOWS]->()", 180623)
    .setRelationshipCardinality("()-[:KNOWS]->(:Person)", 180623)
    .setRelationshipCardinality("(:Person)-[:KNOWS]->(:Person)", 180623)
    .setRelationshipCardinality("()-[:POST_HAS_CREATOR]->()", 1003605)
    .setRelationshipCardinality("(:Message)-[:POST_HAS_CREATOR]->()", 1003605)
    .setRelationshipCardinality("(:Post)-[:POST_HAS_CREATOR]->()", 1003605)
    .setRelationshipCardinality("()-[:POST_HAS_CREATOR]->(:Person)", 1003605)
    .setRelationshipCardinality("(:Message)-[:POST_HAS_CREATOR]->(:Person)", 1003605)
    .setRelationshipCardinality("(:Post)-[:POST_HAS_CREATOR]->(:Person)", 1003605)
    .setRelationshipCardinality("(:Person)-[:POST_HAS_CREATOR]->()", 0)
    .setRelationshipCardinality("()-[:POST_HAS_CREATOR]->(:Message)", 0)
    .setRelationshipCardinality("()-[:POST_HAS_CREATOR]->(:Post)", 0)
    .setRelationshipCardinality("(:Person)-[:POST_HAS_CREATOR]->(:Person)", 0)
    .setRelationshipCardinality("()-[:COMMENT_HAS_CREATOR]->()", 2052169)
    .setRelationshipCardinality("(:Message)-[:COMMENT_HAS_CREATOR]->()", 2052169)
    .setRelationshipCardinality("(:Comment)-[:COMMENT_HAS_CREATOR]->()", 2052169)
    .setRelationshipCardinality("()-[:REPLY_OF]->()", 2052169)
    .setRelationshipCardinality("()-[:REPLY_OF]->(:Message)", 2052169)
    .setRelationshipCardinality("(:Message)-[:REPLY_OF]->()", 0)
    .setRelationshipCardinality("(:Message)-[:REPLY_OF]->(:Message)", 0)
    .setRelationshipCardinality("()-[:COMMENT_HAS_CREATOR]->(:Person)", 2052169)
    .setRelationshipCardinality("(:Message)-[:COMMENT_HAS_CREATOR]->(:Person)", 2052169)
    .setRelationshipCardinality("(:Comment)-[:COMMENT_HAS_CREATOR]->(:Person)", 2052169)
    .setRelationshipCardinality("(:Person)-[:COMMENT_HAS_CREATOR]->()", 0)
    .setRelationshipCardinality("()-[:COMMENT_HAS_CREATOR]->(:Message)", 0)
    .setRelationshipCardinality("()-[:COMMENT_HAS_CREATOR]->(:Comment)", 0)
    .setRelationshipCardinality("()-[:MESSAGE_HAS_TAG]->()", 2928064)
    .setRelationshipCardinality("(:Message)-[:MESSAGE_HAS_TAG]->(:Tag)", 2928064)
    .setRelationshipCardinality("(:Message)-[:MESSAGE_HAS_TAG]->()", 2928064)
    .setRelationshipCardinality("()-[:MESSAGE_HAS_TAG]->(:Tag)", 2928064)
    .setRelationshipCardinality("(:Tag)-[:MESSAGE_HAS_TAG]->()", 0)
    .setRelationshipCardinality("(:Tag)-[:MESSAGE_HAS_TAG]->(:Message)", 0)
    .setRelationshipCardinality("()-[:MESSAGE_HAS_TAG]->(:Message)", 0)
    .setRelationshipCardinality("(:Post)-[:MESSAGE_HAS_TAG]->(:Tag)", 0)
    .setRelationshipCardinality("()-[:IS_LOCATED_IN]->()", 9892)
    .setRelationshipCardinality("(:Person)-[:IS_LOCATED_IN]->()", 9892)
    .setRelationshipCardinality("()-[:IS_LOCATED_IN]->(:City)", 9892)
    .setRelationshipCardinality("()-[:IS_LOCATED_IN]->(:Person)", 0)
    .setRelationshipCardinality("(:City)-[:IS_LOCATED_IN]->()", 0)
    .setRelationshipCardinality("(:Person)-[:IS_LOCATED_IN]->(:City)", 9892)
    .setRelationshipCardinality("()-[:IS_PART_OF]->()", 1454)
    .setRelationshipCardinality("(:City)-[:IS_PART_OF]->()", 1454)
    .setRelationshipCardinality("()-[:IS_PART_OF]->(:Country)", 1454)
    .setRelationshipCardinality("(:City)-[:IS_PART_OF]->(:Country)", 1454)
    .setRelationshipCardinality("()-[:IS_PART_OF]->(:City)", 0)
    .setRelationshipCardinality("(:Country)-[:IS_PART_OF]->()", 0)
    .setRelationshipCardinality("()-[:HAS_TYPE]->()", 16080)
    .setRelationshipCardinality("(:Tag)-[:HAS_TYPE]->(:TagClass)", 16080)
    .setRelationshipCardinality("(:Tag)-[:HAS_TYPE]->()", 16080)
    .setRelationshipCardinality("()-[:HAS_TYPE]->(:TagClass)", 16080)
    .setRelationshipCardinality("()-[:HAS_TYPE]->(:Tag)", 0)
    .setRelationshipCardinality("(:TagClass)-[:HAS_TYPE]->()", 0)
    .setRelationshipCardinality("(:Message)-[]->(:Person)", 2052169 + 1003605)
    .setRelationshipCardinality("(:Message)-[]->()", 2052169 + 1003605)
    .setRelationshipCardinality("()-[]->()", 2052169 + 1003605 + 180623)
    .setRelationshipCardinality("()-[]->(:Person)", 2052169 + 1003605 + 180623)
    .addNodeIndex("Person", List("id"), existsSelectivity = 1.0, uniqueSelectivity = 1.0 / 9892.0, isUnique = true)
    .addNodeIndex("Person", List("firstName"), existsSelectivity = 1.0, uniqueSelectivity = 1 / 1323.0)
    .addNodeIndex("Message", List("creationDate"), existsSelectivity = 1.0, uniqueSelectivity = 10 / 3055774.0)
    .addNodeIndex("City", List("name"), existsSelectivity = 1.0, uniqueSelectivity = 1.0 / 1343.0)
    .addNodeIndex("Country", List("name"), existsSelectivity = 1.0, uniqueSelectivity = 1.0 / 111.0)
    .addNodeIndex("Country", List("id"), existsSelectivity = 1.0, uniqueSelectivity = 1.0 / 111.0)
    .addNodeIndex("Tag", List("id"), existsSelectivity = 1.0, uniqueSelectivity = 1.0 / 16080.0)
    .addNodeIndex("Tag", List("name"), existsSelectivity = 1.0, uniqueSelectivity = 1.0 / 16080.0)
    .addNodeIndex("TagClass", List("name"), existsSelectivity = 1.0, uniqueSelectivity = 1.0 / 71.0)
    .addRelationshipIndex("COMMENT_HAS_CREATOR", List("location"), existsSelectivity = 1.0, uniqueSelectivity = 0.1)
    .addRelationshipIndex(
      "COMMENT_HAS_CREATOR",
      List("id"),
      existsSelectivity = 1.0,
      uniqueSelectivity = 1.0 / 2052169,
      isUnique = true
    )
    .setExecutionModel(executionModel)
    .build()

  test("should batch node properties") {
    val query =
      """MATCH (person:Person)
        |RETURN person.firstName AS personFirstName,
        |       person.lastName AS personLastName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("personFirstName", "personLastName")
      .projection("cacheN[person.firstName] AS personFirstName", "cacheN[person.lastName] AS personLastName")
      .remoteBatchProperties("cacheNFromStore[person.firstName]", "cacheNFromStore[person.lastName]")
      .nodeByLabelScan("person", "Person")
      .build()
  }

  test("should handle renamed variables from within an apply") {
    val query =
      """MATCH (person:Person {id:$Person}) WHERE person.firstName IS NOT NULL
        |CALL {
        |  WITH person
        |  RETURN person AS x
        |}
        |RETURN x.firstName AS personFirstName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("personFirstName")
      .projection(Map(
        "personFirstName" -> cachedNodeProp("person", "firstName", "x")
      ))
      .projection("person AS x")
      .filter("cacheN[person.firstName] IS NOT NULL")
      .remoteBatchProperties("cacheNFromStore[person.firstName]")
      .nodeIndexOperator(
        "person:Person(id = ???)",
        paramExpr = Some(parameter("Person", CTAny)),
        getValue = Map("id" -> DoNotGetValue),
        unique = true
      )
      .build()
  }

  test("should not batch node properties for write queries") {
    val query =
      """MATCH (person:Person)
        |CREATE ()
        |RETURN person.firstName AS personFirstName,
        |       person.lastName AS personLastName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("personFirstName", "personLastName")
      .projection("cacheN[person.firstName] AS personFirstName", "cacheN[person.lastName] AS personLastName")
      .cacheProperties("cacheNFromStore[person.firstName]", "cacheNFromStore[person.lastName]")
      .create(createNode("anon_0"))
      .nodeByLabelScan("person", "Person")
      .build()
  }

  test("should batch relationship properties") {
    val query =
      """MATCH (person:Person)-[knows:KNOWS]->(friend:Person)
        |RETURN knows.creationDate AS knowsSince""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("knowsSince")
      .projection("cacheR[knows.creationDate] AS knowsSince")
      .remoteBatchProperties("cacheRFromStore[knows.creationDate]")
      .filter("person:Person")
      .expandAll("(friend)<-[knows:KNOWS]-(person)")
      .nodeByLabelScan("friend", "Person")
      .build()
  }

  test("should batch on variables from an unwind") {
    val query = """
      MATCH (n)
      UNWIND [null, n] AS x
      MATCH (x)
      RETURN x.name AS name
      """

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("name")
      .projection("cacheN[x.name] AS name")
      .remoteBatchProperties("cacheNFromStore[x.name]")
      .filterExpression(assertIsNode("x"))
      .unwind("[NULL, n] AS x")
      .allNodeScan("n")
      .build()
  }

  test("should batch on standalone pattern arguments") {
    val planner = spdPlanner
      .setAllNodesCardinality(30)
      .setLabelCardinality("L0", 10)
      .setRelationshipCardinality("()-[]->()", 10000)
      .setRelationshipCardinality("(:L0)-[]->()", 20)
      .addNodeIndex("L0", List("prop"), existsSelectivity = 1.0, uniqueSelectivity = 1.0 / 10.0)
      .build()

    val query = """
                  |OPTIONAL MATCH (n0)-[r1]->(n1) WHERE n0.prop = 42
                  |MATCH (a:L0 {prop:42})-[r2]->(n1), (n0)
                  |RETURN a.prop AS expandIntoProp, n0.prop AS standaloneProp
                  |""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("expandIntoProp", "standaloneProp")
      .projection("cacheN[a.prop] AS expandIntoProp", "cacheN[n0.prop] AS standaloneProp")
      .expandInto("(a)-[r2]->(n1)")
      .filterExpression(assertIsNode("n0"))
      .apply()
      .|.nodeIndexOperator(
        "a:L0(prop = 42)",
        argumentIds = Set("n0", "n1", "r1"),
        getValue = Map("prop" -> GetValue)
      )
      .optional()
      .expandAll("(n0)-[r1]->(n1)")
      .filter("cacheN[n0.prop] = 42")
      .remoteBatchProperties("cacheNFromStore[n0.prop]")
      .allNodeScan("n0")
      .build()
  }

  test("should also batch properties used in filters, even if just once") {
    val query =
      """MATCH (person:Person)-[knows:KNOWS]->(friend:Person)
        |  WHERE person.lastName = friend.lastName AND knows.creationDate < $max_creation_date
        |RETURN person.firstName AS personFirstName,
        |       friend.firstName AS friendFirstName,
        |       knows.creationDate AS knowsSince""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("personFirstName", "friendFirstName", "knowsSince")
      .projection(
        "cacheN[person.firstName] AS personFirstName",
        "cacheN[friend.firstName] AS friendFirstName",
        "cacheR[knows.creationDate] AS knowsSince"
      )
      .filter(
        "cacheN[person.lastName] = cacheN[friend.lastName]",
        "cacheR[knows.creationDate] < $max_creation_date",
        "person:Person"
      )
      .remoteBatchProperties(
        "cacheNFromStore[person.lastName]",
        "cacheNFromStore[person.firstName]",
        "cacheRFromStore[knows.creationDate]"
      )
      .expandAll("(friend)<-[knows:KNOWS]-(person)")
      .remoteBatchProperties("cacheNFromStore[friend.lastName]", "cacheNFromStore[friend.firstName]")
      .nodeByLabelScan("friend", "Person")
      .build()
  }

  test("Should not get value from index if the property isn't reused") {
    val query =
      """MATCH (person:Person {id:$Person})
        |RETURN person.firstName AS personFirstName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("personFirstName")
      .projection("cacheN[person.firstName] AS personFirstName")
      .remoteBatchProperties("cacheNFromStore[person.firstName]")
      .nodeIndexOperator(
        "person:Person(id = ???)",
        paramExpr = Some(parameter("Person", CTAny)),
        getValue = Map("id" -> DoNotGetValue),
        unique = true
      )
      .build()
  }

  test("Should get value from index if the property used in another predicate") {
    val query =
      """MATCH (person:Person {id:$Person}) WHERE person.id <> 42
        |RETURN person.firstName AS personFirstName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("personFirstName")
      .projection("cacheN[person.firstName] AS personFirstName")
      .remoteBatchProperties("cacheNFromStore[person.firstName]")
      .filter("NOT cacheN[person.id] = 42")
      .nodeIndexOperator(
        "person:Person(id = ???)",
        paramExpr = Some(parameter("Person", CTAny)),
        getValue = Map("id" -> GetValue),
        unique = true
      )
      .build()
  }

  test("should retrieve properties from indexes where applicable") {
    val query =
      """MATCH (person:Person {id:$Person})
        |RETURN person.id AS personId,
        |       person.firstName AS personFirstName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("personId", "personFirstName")
      .projection("cacheN[person.id] AS personId", "cacheN[person.firstName] AS personFirstName")
      .remoteBatchProperties("cacheNFromStore[person.firstName]")
      .nodeIndexOperator(
        "person:Person(id = ???)",
        paramExpr = Some(parameter("Person", CTAny)),
        getValue = Map("id" -> GetValue),
        unique = true
      )
      .build()
  }

  test("should cache properties with optional match") {
    val query =
      """MATCH (person:Person {id:$Person})-[knows:KNOWS*1..2]-(friend)
        |  WHERE person.firstName = friend.firstName
        |OPTIONAL MATCH (friend)<-[has_creator:POST_HAS_CREATOR|COMMENT_HAS_CREATOR]-(message)
        |  WHERE message.creationDate IS NOT NULL
        |RETURN message.id AS messageId,
        |       message.creationDate AS messageCreationDate,
        |       friend.firstName AS friendFirstName,
        |       friend.lastName AS friendLastName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner
        .planBuilder()
        .produceResults("messageId", "messageCreationDate", "friendFirstName", "friendLastName")
        .projection(
          "cacheN[message.id] AS messageId",
          "cacheN[message.creationDate] AS messageCreationDate",
          "cacheN[friend.firstName] AS friendFirstName",
          "cacheN[friend.lastName] AS friendLastName"
        )
        .apply()
        .|.optional("person", "knows", "friend")
        .|.filter("cacheN[message.creationDate] IS NOT NULL")
        .|.remoteBatchProperties("cacheNFromStore[message.creationDate]", "cacheNFromStore[message.id]")
        .|.expandAll("(friend)<-[has_creator:POST_HAS_CREATOR|COMMENT_HAS_CREATOR]-(message)")
        .|.argument("friend", "person", "knows")
        .filter("cacheN[person.firstName] = cacheN[friend.firstName]")
        .remoteBatchProperties("cacheNFromStore[friend.lastName]", "cacheNFromStore[friend.firstName]")
        .expand("(person)-[knows:KNOWS*1..2]-(friend)", expandMode = ExpandAll, projectedDir = OUTGOING)
        .remoteBatchProperties("cacheNFromStore[person.firstName]")
        .nodeIndexOperator(
          "person:Person(id = ???)",
          paramExpr = Some(parameter("Person", CTAny)),
          getValue =
            Map("id" -> DoNotGetValue), // context.plannerState.accessedProperties contains `person.id`, we need to list projected values in a different way
          unique = true
        )
        .build()
  }

  test("should cache properties with unions from the or-leaf planner") {
    val query =
      """MATCH (person:Person)-[knows:KNOWS*1..2]-(friend)
        |  WHERE person.id = $Person  OR person.firstName = 'Dave'
        |RETURN
        |       person.firstName AS firstName,
        |       person.lastName AS lastName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner
        .planBuilder()
        .produceResults("firstName", "lastName")
        .projection("cacheN[person.firstName] AS firstName", "cacheN[person.lastName] AS lastName")
        .expand("(person)-[knows:KNOWS*1..2]-(friend)")
        .remoteBatchProperties(
          "cacheNFromStore[person.firstName]",
          "cacheNFromStore[person.lastName]"
        ) // note how we retrieve firstName once again
        .distinct("person AS person")
        .union()
        .|.nodeIndexOperator(
          "person:Person(id = ???)",
          paramExpr = Some(parameter("Person", CTAny)),
          getValue = Map("id" -> DoNotGetValue),
          unique = true
        ) // here we get the person id, even though we don't use it later on
        .nodeIndexOperator(
          "person:Person(firstName = 'Dave')",
          getValue = Map("firstName" -> GetValue)
        ) // TODO: we shouldn't get the value only on one side of the union, we need some additional logic
        .build()
  }

  test("should batch properties only on non-conflicting variables on either side of the union") {
    val query =
      """
        |CALL {
        | MATCH (p: Person)
        | WHERE p.firstName = "Smith" AND p.creationDate > $max_creation_date
        | RETURN p
        |UNION ALL
        | MATCH (p: Message)-[:POST_HAS_CREATOR]->(:Person{firstName:"Smith"})
        | WHERE p.creationDate < $max_creation_date
        | RETURN p
        |}
        |RETURN p.creationDate
        |""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("`p.creationDate`")
      .projection("cacheN[p.creationDate] AS `p.creationDate`")
      .remoteBatchProperties("cacheNFromStore[p.creationDate]")
      .union()
      .|.projection("p AS p")
      .|.filter("cacheN[p.creationDate] < $max_creation_date", "p:Message")
      .|.remoteBatchProperties("cacheNFromStore[p.creationDate]")
      .|.expandAll("(anon_1)<-[anon_0:POST_HAS_CREATOR]-(p)")
      .|.nodeIndexOperator("anon_1:Person(firstName = 'Smith')", getValue = Map("firstName" -> DoNotGetValue))
      .projection("p AS p")
      .filter("cacheN[p.creationDate] > $max_creation_date")
      .remoteBatchProperties("cacheNFromStore[p.creationDate]")
      .nodeIndexOperator("p:Person(firstName = 'Smith')", getValue = Map("firstName" -> DoNotGetValue))
      .build()
  }

  test("should batch properties of renamed entities") {
    val query =
      """MATCH (person:Person)
        |  WHERE person.creationDate < $max_creation_date AND person.lastName IS NOT NULL
        |WITH person AS earlyAdopter, person.creationDate AS earlyAdopterSince ORDER BY earlyAdopterSince LIMIT 10
        |MATCH (earlyAdopter)-[knows:KNOWS]->(friend:Person)
        |RETURN earlyAdopter.lastName AS personLastName,
        |       friend.lastName AS friendLastName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("personLastName", "friendLastName")
      .projection(Map(
        "personLastName" -> cachedNodeProp(
          // notice how `originalEntity` and `entityVariable` differ here
          variable = "person",
          propKey = "lastName",
          currentVarName = "earlyAdopter"
        ),
        "friendLastName" -> cachedNodeProp(variable = "friend", propKey = "lastName", currentVarName = "friend")
      ))
      .remoteBatchProperties("cacheNFromStore[friend.lastName]")
      .filter("friend:Person")
      .expandAll("(earlyAdopter)-[knows:KNOWS]->(friend)")
      .projection("person AS earlyAdopter")
      .top(10, "earlyAdopterSince ASC")
      .projection("cacheN[person.creationDate] AS earlyAdopterSince")
      .filter("cacheN[person.creationDate] < $max_creation_date", "cacheN[person.lastName] IS NOT NULL")
      .remoteBatchProperties("cacheNFromStore[person.lastName]", "cacheNFromStore[person.creationDate]")
      .nodeByLabelScan("person", "Person")
      .build()
  }

  test("probably should but currently does not batch properties when returning entire entities") {
    val query =
      """MATCH (person:Person)-[knows:KNOWS]->(friend:Person)
        |RETURN person, friend""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("person", "friend")
      .filter("person:Person")
      .expandAll("(friend)<-[knows:KNOWS]-(person)")
      .nodeByLabelScan("friend", "Person")
      .build()
  }

  test("should batch accessed properties before a selection even if there are no predicates on it") {
    val query =
      """MATCH (person:Person {id:$Person})-[:KNOWS*1..2]-(friend)
        |WHERE NOT person.id = friend.id
        |RETURN friend.id AS personId,
        |       friend.firstName AS personFirstName,
        |       friend.lastName AS personLastName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("personId", "personFirstName", "personLastName")
      .projection(
        "cacheN[friend.id] AS personId",
        "cacheN[friend.firstName] AS personFirstName",
        "cacheN[friend.lastName] AS personLastName"
      )
      .filter("NOT cacheN[person.id] = cacheN[friend.id]")
      .remoteBatchProperties(
        "cacheNFromStore[friend.id]",
        "cacheNFromStore[friend.firstName]",
        "cacheNFromStore[friend.lastName]"
      )
      .expand("(person)-[anon_0:KNOWS*1..2]-(friend)")
      .nodeIndexOperator(
        "person:Person(id = ???)",
        paramExpr = Some(ExplicitParameter("Person", CTAny)(InputPosition.NONE)),
        getValue = Map("id" -> GetValue),
        unique = true
      )
      .build()
  }

  test("should batch properties in complex enough queries (Query 9 in LDBC SF 1)") {
    val query =
      """MATCH (person:Person {id:$Person})-[:KNOWS*1..2]-(friend)
        |WHERE NOT person=friend
        |WITH DISTINCT friend
        |MATCH (friend)<-[:POST_HAS_CREATOR|COMMENT_HAS_CREATOR]-(message)
        |WHERE message.creationDate < $Date0
        |WITH friend, message
        |ORDER BY message.creationDate DESC, message.id ASC
        |LIMIT 20
        |RETURN message.id AS messageId,
        |       coalesce(message.content,message.imageFile) AS messageContent,
        |       message.creationDate AS messageCreationDate,
        |       friend.id AS personId,
        |       friend.firstName AS personFirstName,
        |       friend.lastName AS personLastName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults(
        "messageId",
        "messageContent",
        "messageCreationDate",
        "personId",
        "personFirstName",
        "personLastName"
      )
      .projection(
        "cacheN[message.id] AS messageId",
        "cacheN[friend.lastName] AS personLastName",
        "cacheN[friend.id] AS personId",
        "cacheN[message.creationDate] AS messageCreationDate",
        "coalesce(cacheN[message.content], cacheN[message.imageFile]) AS messageContent",
        "cacheN[friend.firstName] AS personFirstName"
      )
      .remoteBatchProperties(
        "cacheNFromStore[friend.lastName]",
        "cacheNFromStore[friend.id]",
        "cacheNFromStore[friend.firstName]"
      )
      .top(20, "`message.creationDate` DESC", "`message.id` ASC")
      .projection("cacheN[message.creationDate] AS `message.creationDate`", "cacheN[message.id] AS `message.id`")
      .filter("cacheN[message.creationDate] < $Date0")
      .remoteBatchProperties(
        "cacheNFromStore[message.imageFile]",
        "cacheNFromStore[message.creationDate]",
        "cacheNFromStore[message.content]",
        "cacheNFromStore[message.id]"
      )
      .expandAll("(friend)<-[anon_0:POST_HAS_CREATOR|COMMENT_HAS_CREATOR]-(message)")
      .projection("friend AS friend")
      .filter("NOT person = friend")
      .bfsPruningVarExpand(
        "(person)-[:KNOWS*1..2]-(friend)"
      )
      .nodeIndexOperator(
        "person:Person(id = ???)",
        paramExpr = Some(parameter("Person", CTAny)),
        getValue = Map("id" -> DoNotGetValue),
        unique = true
      )
      .build()
  }

  test("should not batch properties if the index determines that values are not required - LDBC BI SF001-Read_9") {
    val query =
      """
        |MATCH (person:Person)<-[:POST_HAS_CREATOR]-(post:Message)<-[:REPLY_OF*0..]-(reply)
        |WHERE post.creationDate <= $endDate AND
        |      reply.creationDate <= $endDate
        |WITH person, count(post) AS threadCount, count(reply) AS messageCount
        |RETURN
        |  person.id,
        |  person.firstName,
        |  person.lastName,
        |  threadCount,
        |  messageCount
        |ORDER BY messageCount DESC, person.id ASC
        |LIMIT 100
        |""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("`person.id`", "`person.firstName`", "`person.lastName`", "threadCount", "messageCount")
      .projection(
        "cacheN[person.firstName] AS `person.firstName`",
        "cacheN[person.lastName] AS `person.lastName`",
        "cacheN[person.id] AS `person.id`"
      )
      .remoteBatchProperties("cacheNFromStore[person.firstName]", "cacheNFromStore[person.lastName]")
      .top(100, "messageCount DESC", "`person.id` ASC")
      .projection("cacheN[person.id] AS `person.id`")
      .remoteBatchProperties("cacheNFromStore[person.id]")
      .aggregation(Seq("person AS person"), Seq("count(post) AS threadCount", "count(reply) AS messageCount"))
      .filter("cacheN[reply.creationDate] <= $endDate")
      .remoteBatchProperties("cacheNFromStore[reply.creationDate]")
      .expand("(post)<-[anon_1:REPLY_OF*0..]-(reply)", expandMode = ExpandAll, projectedDir = INCOMING)
      .filter("person:Person")
      .expandAll("(post)-[anon_0:POST_HAS_CREATOR]->(person)")
      .nodeIndexOperator(
        "post:Message(creationDate <= ???)",
        paramExpr = Some(parameter("endDate", CTAny)),
        getValue = Map("creationDate" -> DoNotGetValue)
      )
      .build()
  }

  test("should batch properties in relationship indexes") {
    val query =
      """MATCH (person:Person)<-[r:COMMENT_HAS_CREATOR]-(message)
        |WHERE r.location = 'London' AND person.firstName IS NOT NULL
        |RETURN r.location AS posterLocation,
        |       coalesce(message.content,message.imageFile) AS messageContent,
        |       person.firstName AS personFirstName,
        |       person.lastName AS personLastName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("posterLocation", "messageContent", "personFirstName", "personLastName")
      .projection(
        "cacheR[r.location] AS posterLocation",
        "coalesce(cacheN[message.content], cacheN[message.imageFile]) AS messageContent",
        "cacheN[person.firstName] AS personFirstName",
        "cacheN[person.lastName] AS personLastName"
      )
      .remoteBatchProperties("cacheNFromStore[message.content]", "cacheNFromStore[message.imageFile]")
      .filterExpressionOrString(
        "cacheN[person.firstName] IS NOT NULL",
        hasLabels("person", "Person")
      )
      .remoteBatchProperties("cacheNFromStore[person.lastName]", "cacheNFromStore[person.firstName]")
      .relationshipIndexOperator(
        "(message)-[r:COMMENT_HAS_CREATOR(location = 'London')]->(person)",
        getValue = Map("location" -> GetValue)
      )
      .build()
  }

  test("should batch properties in relationship indexes by id seek") {
    val query =
      """MATCH (person:Person)<-[r:COMMENT_HAS_CREATOR {id:$CommentCreatorId}]-(message)
        |WHERE person.firstName IS NOT NULL
        |RETURN r.id AS commentorId,
        |       coalesce(message.content,message.imageFile) AS messageContent,
        |       person.firstName AS personFirstName,
        |       person.lastName AS personLastName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("commentorId", "messageContent", "personFirstName", "personLastName")
      .projection(
        "cacheR[r.id] AS commentorId",
        "coalesce(cacheN[message.content], cacheN[message.imageFile]) AS messageContent",
        "cacheN[person.firstName] AS personFirstName",
        "cacheN[person.lastName] AS personLastName"
      )
      .remoteBatchProperties("cacheNFromStore[message.content]", "cacheNFromStore[message.imageFile]")
      .filterExpressionOrString(
        "cacheN[person.firstName] IS NOT NULL",
        hasLabels("person", "Person")
      )
      .remoteBatchProperties("cacheNFromStore[person.lastName]", "cacheNFromStore[person.firstName]")
      .relationshipIndexOperator(
        "(message)-[r:COMMENT_HAS_CREATOR(id = ???)]->(person)",
        paramExpr = Some(ExplicitParameter("CommentCreatorId", CTAny)(InputPosition.NONE)),
        getValue = Map("id" -> GetValue),
        unique = true
      )
      .build()
  }

  test("should batch properties for aggregating functions like max") {
    val query =
      """MATCH (person)
        |WITH MAX(person.age) as maxAge, person
        |WHERE person.age = maxAge
        |RETURN person.name""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("`person.name`")
      .projection("cacheN[person.name] AS `person.name`")
      .filter("cacheN[person.age] = maxAge")
      .remoteBatchProperties("cacheNFromStore[person.age]", "cacheNFromStore[person.name]")
      .aggregation(Seq("person AS person"), Seq("MAX(cacheN[person.age]) AS maxAge"))
      .remoteBatchProperties("cacheNFromStore[person.age]")
      .allNodeScan("person")
      .build()
  }

  test("should batch properties for aggregating functions with grouping keys") {
    val query =
      """MATCH (person)
        |WITH person, max(person.age) - person.age AS ageDifference
        |RETURN person.name, ageDifference""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("`person.name`", "ageDifference")
      .projection("cacheN[person.name] AS `person.name`")
      .remoteBatchProperties("cacheNFromStore[person.name]")
      .projection("anon_0 - anon_1 AS ageDifference")
      .aggregation(Seq("cacheN[person.age] AS anon_1", "person AS person"), Seq("max(cacheN[person.age]) AS anon_0"))
      .remoteBatchProperties("cacheNFromStore[person.age]")
      .allNodeScan("person")
      .build()
  }

  test("should batch properties for collect function") {
    val query =
      """MATCH (person)
        |RETURN collect(person.age) as ages""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("ages")
      .aggregation(Seq(), Seq("collect(cacheN[person.age]) AS ages"))
      .remoteBatchProperties("cacheNFromStore[person.age]")
      .allNodeScan("person")
      .build()
  }

  test("should batch properties for rollup apply") {
    val query =
      """
        |MATCH (p:Person)
        |RETURN p.name, [(p)<-[r:COMMENT_HAS_CREATOR]-(message) | message.name] AS title
        |""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("`p.name`", "title")
      .projection("cacheN[p.name] AS `p.name`")
      .remoteBatchProperties("cacheNFromStore[p.name]")
      .rollUpApply("title", "anon_0")
      .|.projection("cacheN[message.name] AS anon_0")
      .|.remoteBatchProperties("cacheNFromStore[message.name]")
      .|.expandAll("(p)<-[r:COMMENT_HAS_CREATOR]-(message)")
      .|.argument("p")
      .nodeByLabelScan("p", "Person")
      .build()
  }

  test("should propagate cached properties for an unwind projection") {
    val query =
      """
        |MATCH (n:Person)
        |UNWIND [n.firstName] AS foo
        |MATCH (n)-[:KNOWS]-(friend:Person {lastName: foo})
        |RETURN n.firstName, friend.firstName""".stripMargin
    val plan = planner.plan(query).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .projection("cacheN[n.firstName] AS `n.firstName`", "cacheN[friend.firstName] AS `friend.firstName`")
      .filter("cacheN[friend.lastName] = foo", "friend:Person")
      .remoteBatchProperties("cacheNFromStore[friend.lastName]", "cacheNFromStore[friend.firstName]")
      .expandAll("(n)-[anon_0:KNOWS]-(friend)")
      .unwind("[cacheN[n.firstName]] AS foo")
      .remoteBatchProperties("cacheNFromStore[n.firstName]")
      .nodeByLabelScan("n", "Person")
      .build())
  }

  test("should propagate cached properties for a distinct projection") {
    val query =
      """
        |MATCH (n:Person)
        |WHERE n.firstName = 'foo'
        |RETURN DISTINCT n.lastName""".stripMargin
    val plan = planner.plan(query).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .distinct("cacheN[n.lastName] AS `n.lastName`")
      .remoteBatchProperties("cacheNFromStore[n.lastName]")
      .nodeIndexOperator("n:Person(firstName = 'foo')", getValue = Map("firstName" -> DoNotGetValue))
      .build())
  }

  test("should batch properties for letSelectOrAntiSemiApply and selectOrSemiApply") {
    val query =
      """
        |MATCH (a:Person)
        |WHERE  NOT EXISTS { (:Post)-[:POST_HAS_CREATOR]->(n) } OR (a)-[:KNOWS]-(:Person{lastName:"Smith"}) OR a.age > 30
        |RETURN a.firstName, a.age""".stripMargin
    val plan = planner.plan(query).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .projection("cacheN[a.firstName] AS `a.firstName`", "cacheN[a.age] AS `a.age`")
      .selectOrSemiApply("anon_4")
      .|.filter("cacheN[anon_3.lastName] = 'Smith'", "anon_3:Person")
      .|.remoteBatchProperties("cacheNFromStore[anon_3.lastName]")
      .|.expandAll("(a)-[anon_2:KNOWS]-(anon_3)")
      .|.argument("a")
      .letSelectOrAntiSemiApply("anon_4", "cacheN[a.age] > 30")
      .|.expandAll("(anon_0)-[anon_1:POST_HAS_CREATOR]->(n)")
      .|.nodeByLabelScan("anon_0", "Post")
      .remoteBatchProperties("cacheNFromStore[a.firstName]", "cacheNFromStore[a.age]")
      .nodeByLabelScan("a", "Person")
      .build())
  }

  test(
    "should propagate batch properties with LetSemiApply and SelectOrAntiSemiApply"
  ) {
    val query =
      """
        |MATCH (a: Person)
        |WHERE (a {lastName:"Smith"})-[:KNOWS]-(:Person{lastName:"Smyth"}) OR NOT (a {lastName:"Smyth"})-[:KNOWS]-(:Person{lastName:"Smith"})
        |RETURN a.firstName, a.lastName""".stripMargin
    val plan = planner.plan(query).stripProduceResults
    plan should equal(
      planner.subPlanBuilder()
        .projection("cacheN[a.firstName] AS `a.firstName`", "cacheN[a.lastName] AS `a.lastName`")
        .remoteBatchProperties("cacheNFromStore[a.firstName]")
        .selectOrAntiSemiApply("anon_4")
        .|.filter("cacheN[anon_3.lastName] = 'Smith'", "anon_3:Person")
        .|.remoteBatchProperties("cacheNFromStore[anon_3.lastName]")
        .|.expandAll("(a)-[anon_2:KNOWS]-(anon_3)")
        .|.filter("cacheN[a.lastName] = 'Smyth'")
        .|.argument("a")
        .letSemiApply("anon_4")
        .|.filter("cacheN[anon_1.lastName] = 'Smyth'", "anon_1:Person")
        .|.remoteBatchProperties("cacheNFromStore[anon_1.lastName]")
        .|.expandAll("(a)-[anon_0:KNOWS]-(anon_1)")
        .|.filter("cacheN[a.lastName] = 'Smith'")
        .|.remoteBatchProperties("cacheNFromStore[a.lastName]")
        .|.argument("a")
        .nodeByLabelScan("a", "Person")
        .build()
    )
  }

  test("should propagate cached properties for cartesian products, apply and anti-semi-apply") {
    val query =
      """MATCH (a:Person )
        |MATCH (b:Person {firstName: "John"})
        |WHERE NOT (b)-[:KNOWS]->(:Person {firstName: "Jon"})
        |RETURN a.name, b.firstName
        |""".stripMargin
    val plan = planner.plan(query).stripProduceResults
    plan should equal(
      planner.subPlanBuilder()
        .projection("cacheN[a.name] AS `a.name`", "cacheN[b.firstName] AS `b.firstName`")
        .remoteBatchProperties("cacheNFromStore[a.name]")
        .cartesianProduct()
        .|.nodeByLabelScan("a", "Person")
        .antiSemiApply()
        .|.expandInto("(b)-[anon_0:KNOWS]->(anon_1)")
        .|.nodeIndexOperator(
          "anon_1:Person(firstName = 'Jon')",
          argumentIds = Set("b"),
          getValue = Map("firstName" -> DoNotGetValue)
        )
        .nodeIndexOperator("b:Person(firstName = 'John')", getValue = Map("firstName" -> GetValue))
        .build()
    )
  }

  test("optional match with properties from outer matches should not fetch properties again") {
    val query =
      """
        |MATCH (p:Person {firstName: 'foo'})
        |OPTIONAL MATCH (p)-[:KNOWS]-(s:Person) WHERE s.firstName <> p.firstName
        |RETURN p.firstName,s.firstName
        |""".stripMargin

    val plan = planner.plan(query).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .projection("cacheN[p.firstName] AS `p.firstName`", "cacheN[s.firstName] AS `s.firstName`")
      .apply()
      .|.optional("p")
      .|.filter("NOT cacheN[s.firstName] = cacheN[p.firstName]", "s:Person")
      .|.remoteBatchProperties("cacheNFromStore[s.firstName]")
      .|.expandAll("(p)-[anon_0:KNOWS]-(s)")
      .|.argument("p")
      .nodeIndexOperator("p:Person(firstName = 'foo')", getValue = Map("firstName" -> GetValue))
      .build())
  }

  test("should propagate cached properties from a nodeindex scan ") {
    val query =
      """
        |MATCH (p)-[:KNOWS]-(s:Person) WHERE s.firstName <> p.firstName
        |RETURN p.firstName,s.firstName
        |""".stripMargin

    val plan = planner.plan(query).stripProduceResults

    plan should equal(planner.subPlanBuilder()
      .projection("cacheN[p.firstName] AS `p.firstName`", "cacheN[s.firstName] AS `s.firstName`")
      .filter("NOT cacheN[s.firstName] = cacheN[p.firstName]")
      .remoteBatchProperties("cacheNFromStore[p.firstName]")
      .expandAll("(s)-[anon_0:KNOWS]-(p)")
      .nodeIndexOperator("s:Person(firstName)", getValue = Map("firstName" -> GetValue))
      .build())
  }

  test("should batch properties for anti-conditional apply and shortestpath") {
    val query =
      """
        |MATCH p=shortestPath((a:Person{lastName:"Smith"})-[:KNOWS*]-(b:Person{lastName:"Smith"}))
        |WHERE all(r IN relationships(p) WHERE r.creationDate < $max_creation_date)
        |AND all(n IN nodes(p) WHERE n.firstName = "John")
        |AND length(p) > 5
        |RETURN a.lastName, [n in nodes(p) | n.lastName] as lastNames, b.lastName
        |""".stripMargin

    planner.plan(query).stripProduceResults should equal(
      planner.subPlanBuilder()
        .projection(
          "cacheN[a.lastName] AS `a.lastName`",
          "[n IN nodes(p) | n.lastName] AS lastNames",
          "cacheN[b.lastName] AS `b.lastName`"
        )
        .antiConditionalApply("p")
        .|.top(1, "anon_1 ASC")
        .|.projection("length(p) AS anon_1")
        .|.filter("length(p) > 5")
        .|.projection(Map("p" -> varLengthPathExpression(v"a", v"anon_0", v"b")))
        .|.expand(
          "(a)-[anon_0:KNOWS*1..]-(b)",
          expandMode = ExpandInto,
          projectedDir = OUTGOING,
          nodePredicates = Seq(Predicate("n", "n.firstName = 'John'")),
          relationshipPredicates = Seq(Predicate("r", "r.creationDate < $max_creation_date"))
        )
        .|.argument("a", "b")
        .apply()
        .|.optional("a", "b")
        .|.shortestPath(
          "(a)-[anon_0:KNOWS*1..]-(b)",
          pathName = Some("p"),
          nodePredicates = Seq(Predicate("n", "n.firstName = 'John'")),
          relationshipPredicates = Seq(Predicate("r", "r.creationDate < $max_creation_date")),
          pathPredicates = Seq("length(p) > 5"),
          withFallback = true
        ) // TODO: this shortestPath should eventually be rewritten into a stateful shortest path to allow batching of properties.
        .|.argument("a", "b")
        .cartesianProduct()
        .|.filter("cacheN[b.lastName] = 'Smith'")
        .|.remoteBatchProperties("cacheNFromStore[b.lastName]")
        .|.nodeByLabelScan("b", "Person")
        .filter("cacheN[a.lastName] = 'Smith'")
        .remoteBatchProperties("cacheNFromStore[a.lastName]")
        .nodeByLabelScan("a", "Person")
        .build()
    )
  }

  test("should propagate batched properties after a statefulshortestpath") {
    val query =
      """
        |MATCH p=ANY SHORTEST ((a)((:Person{lastName:"Smith"})-[:KNOWS]-(:Person{firstName:"John"}))*(b{lastName:"Smith"}))
        |WHERE length(p) > 5
        |RETURN a.lastName, [n in nodes(p) | n.lastName] as lastNames, b.firstName
        |""".stripMargin
    planner.plan(query).stripProduceResults should equal(planner.subPlanBuilder()
      .projection(Map(
        "a.lastName" -> cachedNodeProp("a", "lastName"),
        "lastNames" -> listComprehension(
          v"n",
          nodes(varLengthPathExpression(v"a", v"anon_3", v"b")),
          None,
          Some(prop("n", "lastName"))
        ),
        "b.firstName" -> cachedNodeProp("b", "firstName")
      ))
      .remoteBatchProperties("cacheNFromStore[a.lastName]")
      .filterExpression(greaterThan(
        length(varLengthPathExpression(v"a", v"anon_3", v"b")),
        literalInt(5)
      ))
      .statefulShortestPath(
        "b",
        "a",
        "SHORTEST 1 (a) ((`anon_0`)-[`anon_1`]-(`anon_2`)){0, } (b)",
        None,
        Set(),
        Set(("anon_1", "anon_3")),
        Set(("a", "a")),
        Set(),
        StatefulShortestPath.Selector.Shortest(CountInteger(1)),
        new TestNFABuilder(0, "b")
          .addTransition(0, 1, "(b) (anon_2 WHERE anon_2.firstName = 'John' AND anon_2:Person)")
          .addTransition(0, 3, "(b) (a)")
          .addTransition(1, 2, "(anon_2)-[anon_1:KNOWS]-(anon_0 WHERE anon_0.lastName = 'Smith' AND anon_0:Person)")
          .addTransition(2, 1, "(anon_0) (anon_2 WHERE anon_2.firstName = 'John' AND anon_2:Person)")
          .addTransition(2, 3, "(anon_0) (a)")
          .setFinalState(3)
          .build(),
        ExpandAll,
        reverseGroupVariableProjections = true
      )
      .filter("cacheN[b.lastName] = 'Smith'")
      .remoteBatchProperties("cacheNFromStore[b.lastName]", "cacheNFromStore[b.firstName]")
      .allNodeScan("b")
      .build())
  }

  test("should batch properties for a value hash join") {
    val query =
      """
        | MATCH (al:Message), (a:Person)
        |    WHERE al.title = a.name
        |    RETURN al.title, a.name
        |""".stripMargin

    planner.plan(query).stripProduceResults should equal(planner.subPlanBuilder()
      .projection("cacheN[al.title] AS `al.title`", "cacheN[a.name] AS `a.name`")
      .valueHashJoin("cacheN[a.name] = cacheN[al.title]")
      .|.remoteBatchProperties("cacheNFromStore[al.title]")
      .|.nodeByLabelScan("al", "Message")
      .remoteBatchProperties("cacheNFromStore[a.name]")
      .nodeByLabelScan("a", "Person")
      .build())
  }

  test("should not throw an error when fetching remote batch properties between two ordered operators") {
    val query =
      """
        |MATCH (a:Person)
        |CALL {
        |  WITH a
        |  MATCH (a)-[:KNOWS]->(b{name: a.firstName})
        |  RETURN b
        |  ORDER BY b.name
        |  LIMIT 1
        |}
        |RETURN a.lastName, b.name
        |ORDER BY b.name
        |LIMIT 100
        |""".stripMargin

    planner.plan(query) should equal(planner
      .planBuilder()
      .produceResults("`a.lastName`", "`b.name`")
      .projection("cacheN[a.lastName] AS `a.lastName`", "cacheN[b.name] AS `b.name`")
      .remoteBatchProperties("cacheNFromStore[a.lastName]")
      .top(100, "`b.name` ASC")
      .projection("cacheN[b.name] AS `b.name`")
      .apply()
      .|.top(1, "`b.name` ASC")
      .|.projection("cacheN[b.name] AS `b.name`")
      .|.filter("cacheN[b.name] = cacheN[a.firstName]")
      .|.remoteBatchProperties("cacheNFromStore[b.name]")
      .|.expandAll("(a)-[anon_0:KNOWS]->(b)")
      .|.remoteBatchProperties("cacheNFromStore[a.firstName]")
      .|.argument("a")
      .nodeByLabelScan("a", "Person")
      .build())
  }

  test("should plan pruning var expand if it has remoteBatchProperties") {
    val query =
      """
        |MATCH path=(:Person {id:$Person})-[:KNOWS*1..3]-(friend)
        |WHERE friend.firstName=$Name
        |RETURN friend.firstName, min(length(path)) AS distance
        |""".stripMargin

    planner.plan(query).stripProduceResults shouldEqual
      planner.subPlanBuilder()
        .aggregation(Seq("cacheN[friend.firstName] AS `friend.firstName`"), Seq("min(anon_1) AS distance"))
        .filter("cacheN[friend.firstName] = $Name")
        .remoteBatchProperties("cacheNFromStore[friend.firstName]")
        .bfsPruningVarExpand("(anon_0)-[:KNOWS*1..3]-(friend)", depthName = Some("anon_1"), mode = ExpandAll)
        .nodeIndexOperator(
          "anon_0:Person(id = ???)",
          paramExpr = Some(parameter("Person", CTAny)),
          getValue = Map("id" -> DoNotGetValue),
          unique = true
        )
        .build()
  }

  test("should plan node hash join instead of expandAlls") { // offshore leaks: Q6_no_hint
    val query =
      """
        |MATCH (o1:Person)<-[r1]-(m:Message)-[r2]->(o2:Person)
        |WHERE id(o1) < id(o2)
        |  AND NOT o1.firstName CONTAINS "John"
        |  AND NOT o2.firstName CONTAINS "John"
        |  AND size([p = (o1)-->() | p]) > 10
        |  AND size([p = (o2)-->() | p]) > 10
        |WITH o1,o2,count(*) as freq, collect(m)[0..10] as messages
        |WHERE freq > 10
        |WITH *
        |ORDER BY freq DESC
        |LIMIT 10
        |RETURN o1.firstName, o2.firstName, freq, [m IN messages | m.title]
        |""".stripMargin

    planner.plan(query).stripProduceResults shouldEqual planner.subPlanBuilder()
      .projection(
        "cacheN[o1.firstName] AS `o1.firstName`",
        "cacheN[o2.firstName] AS `o2.firstName`",
        "[m IN messages | m.title] AS `[m IN messages | m.title]`"
      )
      .remoteBatchProperties("cacheNFromStore[o1.firstName]", "cacheNFromStore[o2.firstName]")
      .top(10, "freq DESC")
      .filter("freq > 10")
      .projection("anon_0[0..10] AS messages")
      .aggregation(Seq("o1 AS o1", "o2 AS o2"), Seq("collect(m) AS anon_0", "count(*) AS freq"))
      .filter("id(o1) < id(o2)", "NOT r2 = r1")
      .nodeHashJoin("m")
      .|.expandAll("(o2)<-[r2]-(m)")
      .|.filterExpressionOrString(
        not(contains(cachedNodeProp("o2", "firstName"), literalString("John"))),
        HasDegreeGreaterThan(v"o2", None, OUTGOING, literalInt(10))(pos)
      )
      .|.nodeIndexOperator("o2:Person(firstName)", getValue = Map("firstName" -> GetValue))
      .filterExpression(hasLabels("m", "Message"))
      .expandAll("(o1)<-[r1]-(m)")
      .filterExpressionOrString(
        not(contains(cachedNodeProp("o1", "firstName"), literalString("John"))),
        HasDegreeGreaterThan(v"o1", None, OUTGOING, literalInt(10))(pos)
      )
      .nodeIndexOperator("o1:Person(firstName)", getValue = Map("firstName" -> GetValue))
      .build()
  }

  test("should insert remoteBatchProperties between an aggregation and projection on the same property") {
    val query =
      """
        |MATCH (subject:Person { firstName: $firstName })
        |MATCH p=(subject)<-[:POST_HAS_CREATOR]-()-[:KNOWS*0..2]-()<-[:POST_HAS_CREATOR]-(person)-[:KNOWS]->(friend)
        |WHERE person<>subject AND friend.firstName IN $interests
        |WITH person, friend, min(length(p)) AS pathLength
        |ORDER BY friend.firstName
        |RETURN person.name AS name, count(friend) AS score, collect(friend.firstName) AS interests,((pathLength - 1)/2) AS distance
        |ORDER BY score DESC LIMIT 20
        |""".stripMargin

    planner.plan(query).stripProduceResults shouldEqual planner
      .subPlanBuilder()
      .top(20, "score DESC")
      .aggregation(
        Seq("cacheN[person.name] AS name", "(pathLength - 1) / 2 AS distance"),
        Seq("count(friend) AS score", "collect(cacheN[friend.firstName]) AS interests")
      )
      .remoteBatchProperties("cacheNFromStore[person.name]")
      .sort("`friend.firstName` ASC")
      .projection("cacheN[friend.firstName] AS `friend.firstName`")
      .remoteBatchProperties(
        "cacheNFromStore[friend.firstName]"
      ) // we need to plan this friend.firstName again, otherwise projection is very slow.
      .aggregation(
        Map("person" -> v"person", "friend" -> v"friend"),
        Map("pathLength" -> min(length(PathExpressionBuilder.node("subject").inTo("anon_0", "anon_1").bothToVarLength(
          "anon_2",
          "anon_3"
        ).inTo("anon_4", "person").outTo("anon_5", "friend").build())))
      )
      .filter("NOT anon_5 IN anon_2", "cacheN[friend.firstName] IN $interests")
      .remoteBatchProperties("cacheNFromStore[friend.firstName]")
      .expandAll("(person)-[anon_5:KNOWS]->(friend)")
      .filter("NOT person = subject", "NOT anon_4 = anon_0")
      .expandAll("(anon_3)<-[anon_4:POST_HAS_CREATOR]-(person)")
      .expand("(anon_1)-[anon_2:KNOWS*0..2]-(anon_3)", expandMode = ExpandAll, projectedDir = OUTGOING)
      .expandAll("(subject)<-[anon_0:POST_HAS_CREATOR]-(anon_1)")
      .nodeIndexOperator(
        "subject:Person(firstName = ???)",
        paramExpr = Some(parameter("firstName", CTAny)),
        getValue = Map("firstName" -> DoNotGetValue)
      )
      .build()
  }

  test("should cache index values if they are used in other index seeks: index_backed_order_by-q15") {
    val query =
      """
        |MATCH (a:PROFILES), (b:PROFILES)
        |WHERE
        |    a.pets STARTS WITH "x" AND
        |    b.children STARTS WITH "x" AND
        |    a.pets = b.children
        |RETURN id(a), id(b)
        |""".stripMargin

    val planner = spdPlanner
      .setAllNodesCardinality(1632803)
      .setLabelCardinality("PROFILES", 1632803)
      .addNodeIndex(
        "PROFILES",
        Seq("gender", "pets", "children"),
        existsSelectivity = 371135.0 / 1632803,
        uniqueSelectivity = 1.0 / 253204.0
      )
      .addNodeIndex(
        "PROFILES",
        Seq("gender", "hair_color", "eye_color", "pets", "children"),
        existsSelectivity = 337392.0 / 1632803,
        uniqueSelectivity = 1.0 / 273680.0
      )
      .addNodeIndex(
        "PROFILES",
        Seq("pets", "children"),
        existsSelectivity = 371135.0 / 1632803,
        uniqueSelectivity = 1.0 / 247714.0
      )
      .addNodeIndex(
        "PROFILES",
        Seq("children"),
        existsSelectivity = 475697.0 / 1632803,
        uniqueSelectivity = 1.0 / 176353
      )
      .addNodeIndex("PROFILES", Seq("pets"), existsSelectivity = 700681.0 / 1632803, uniqueSelectivity = 1.0 / 268273.0)
      .build()

    planner.plan(query) shouldEqual planner.planBuilder()
      .produceResults("`id(a)`", "`id(b)`")
      .projection("id(a) AS `id(a)`", "id(b) AS `id(b)`")
      .filter("cacheN[a.pets] STARTS WITH 'x'")
      .apply()
      .|.nodeIndexOperator(
        "a:PROFILES(pets = cacheN[b.children])",
        argumentIds = Set("b"),
        getValue = Map("pets" -> GetValue)
      )
      .nodeIndexOperator("b:PROFILES(children STARTS WITH 'x')", getValue = Map("children" -> GetValue))
      .build()
  }

  test("should fetch batched properties before an expandAll") {
    val query =
      """
        |  MATCH (p)-[:KNOWS]-(s:Person) WHERE s.lastName <> p.lastName
        |  RETURN p.firstName,s.firstName
        |""".stripMargin

    val plan = planner.plan(query).stripProduceResults
    plan should equal(planner.subPlanBuilder()
      .projection("cacheN[p.firstName] AS `p.firstName`", "cacheN[s.firstName] AS `s.firstName`")
      .filter("NOT cacheN[s.lastName] = cacheN[p.lastName]")
      .remoteBatchProperties("cacheNFromStore[p.lastName]", "cacheNFromStore[p.firstName]")
      .expandAll("(s)-[anon_0:KNOWS]-(p)")
      .remoteBatchProperties("cacheNFromStore[s.lastName]", "cacheNFromStore[s.firstName]")
      .nodeByLabelScan("s", "Person")
      .build())
  }

  test(
    "should plan node hashjoin and pruning var expands: ldbc_bi_sf001: Read_10a"
  ) { // Test to ensure that cost estimate for remote batch properties helps
    val query =
      """
        |MATCH path=(startPerson:Person {id: $personId})-[:KNOWS*1..4]-(expertCandidatePerson:Person)
        |MATCH (expertCandidatePerson)-[:IS_LOCATED_IN]->(:City)-[:IS_PART_OF]->(:Country {name: $country})
        |WITH expertCandidatePerson, min(length(path)) AS shortestPathDistance
        |WHERE shortestPathDistance >=3
        |MATCH (expertCandidatePerson)<-[:POST_HAS_CREATOR]-(message:Message)
        |WHERE (message)-[:MESSAGE_HAS_TAG]->(:Tag)-[:HAS_TYPE]->(:TagClass {name: $tagClass})
        |MATCH (message)-[:MESSAGE_HAS_TAG]-(tag:Tag)
        |RETURN expertCandidatePerson.id, tag.name, count(DISTINCT message) AS messageCount
        |ORDER BY messageCount DESC, tag.name ASC, expertCandidatePerson.id ASC
        |LIMIT 100
        |""".stripMargin

    planner.plan(query).stripProduceResults shouldEqual planner.subPlanBuilder()
      .top(100, "messageCount DESC", "`tag.name` ASC", "`expertCandidatePerson.id` ASC")
      .aggregation(
        Seq("cacheN[expertCandidatePerson.id] AS `expertCandidatePerson.id`", "cacheN[tag.name] AS `tag.name`"),
        Seq("count(DISTINCT message) AS messageCount")
      )
      .remoteBatchProperties("cacheNFromStore[expertCandidatePerson.id]", "cacheNFromStore[tag.name]")
      .filter("tag:Tag")
      .expandAll("(message)-[anon_9:MESSAGE_HAS_TAG]-(tag)")
      .filter("message:Message")
      .semiApply()
      .|.filter("cacheN[anon_8.name] = $tagClass", "anon_8:TagClass")
      .|.remoteBatchProperties("cacheNFromStore[anon_8.name]")
      .|.expandAll("(anon_6)-[anon_7:HAS_TYPE]->(anon_8)")
      .|.filter("anon_6:Tag")
      .|.expandAll("(message)-[anon_5:MESSAGE_HAS_TAG]->(anon_6)")
      .|.argument("message")
      .expandAll("(expertCandidatePerson)<-[anon_4:POST_HAS_CREATOR]-(message)")
      .filter("shortestPathDistance >= 3")
      .aggregation(Seq("expertCandidatePerson AS expertCandidatePerson"), Seq("min(anon_10) AS shortestPathDistance"))
      .nodeHashJoin("expertCandidatePerson")
      // we are testing that we run this operator
      .|.bfsPruningVarExpand(
        "(startPerson)-[:KNOWS*1..4]-(expertCandidatePerson)",
        depthName = Some("anon_10"),
        mode = ExpandAll
      ) // we are testing that we run this operator
      .|.nodeIndexOperator(
        "startPerson:Person(id = ???)",
        paramExpr = Some(parameter("personId", CTAny)),
        getValue = Map("id" -> DoNotGetValue),
        unique = true
      )
      .filter("expertCandidatePerson:Person")
      .expandAll("(anon_1)<-[anon_0:IS_LOCATED_IN]-(expertCandidatePerson)")
      .filter("anon_1:City")
      .expandAll("(anon_3)<-[anon_2:IS_PART_OF]-(anon_1)")
      .nodeIndexOperator(
        "anon_3:Country(name = ???)",
        paramExpr = Some(parameter("country", CTAny)),
        getValue = Map("name" -> DoNotGetValue)
      )
      .build()
  }

  test("should plan join when using join hints") { // simplified ldbc_bi_sf001: Read_2a_join_hint to only test joins
    val query =
      """
        |MATCH (tag:Tag)-[:HAS_TYPE]->(:TagClass {name: $tagClass})
        |OPTIONAL MATCH (message:Message)-[:MESSAGE_HAS_TAG]->(tag)
        |
        |// makes 100-1000x faster
        |USING JOIN ON tag
        |
        |WHERE $date <= message.creationDate
        |WITH tag, message.creationDate < $date /*secondWindowStart*/ AS isInFirstWindow
        |RETURN tag.name
        |""".stripMargin

    planner.plan(query).stripProduceResults shouldEqual planner.subPlanBuilder()
      .projection("cacheN[tag.name] AS `tag.name`")
      .remoteBatchProperties("cacheNFromStore[tag.name]")
      .projection("cacheN[message.creationDate] < $date AS isInFirstWindow")
      .remoteBatchProperties("cacheNFromStore[message.creationDate]")
      .leftOuterHashJoin("tag")
      .|.expandAll("(message)-[anon_2:MESSAGE_HAS_TAG]->(tag)")
      .|.nodeIndexOperator(
        "message:Message(creationDate >= ???)",
        paramExpr = Some(parameter("date", CTAny)),
        getValue = Map("creationDate" -> GetValue)
      )
      .filter("tag:Tag")
      .expandAll("(anon_1)<-[anon_0:HAS_TYPE]-(tag)")
      .nodeIndexOperator(
        "anon_1:TagClass(name = ???)",
        paramExpr = Some(parameter("tagClass", CTAny)),
        getValue = Map("name" -> DoNotGetValue)
      )
      .build()
  }

  test("should plan use plans with pre-fetched properties even when planning multiple cartesian products") {
    val query =
      """
        |MATCH (tag:Tag)-[:HAS_TYPE]->(:TagClass {name: $tagClass})
        |MATCH (message:Message)-[:MESSAGE_HAS_TAG]->(:Tag {name: $tagClass})
        |MATCH (person: Person {name: $tagClass})
        |WHERE $date <= message.creationDate
        |WITH tag, message.creationDate < $date /*secondWindowStart*/ AS isInFirstWindow, message.title AS messageTitle, person.name AS matchingPersonTag
        |RETURN tag.name, messageTitle, matchingPersonTag
        |""".stripMargin
    planner.plan(query).stripProduceResults should equal(planner.subPlanBuilder()
      .projection("cacheN[tag.name] AS `tag.name`")
      .projection(
        "cacheN[message.creationDate] < $date AS isInFirstWindow",
        "cacheN[message.title] AS messageTitle",
        "cacheN[person.name] AS matchingPersonTag"
      )
      .cartesianProduct()
      .|.cartesianProduct()
      .|.|.remoteBatchProperties("cacheNFromStore[tag.name]")
      .|.|.filter("tag:Tag")
      .|.|.expandAll("(anon_1)<-[anon_0:HAS_TYPE]-(tag)")
      .|.|.nodeIndexOperator(
        "anon_1:TagClass(name = ???)",
        paramExpr = Some(parameter("tagClass", CTAny)),
        getValue = Map("name" -> DoNotGetValue)
      )
      .|.filter("cacheN[message.creationDate] >= $date", "message:Message")
      .|.remoteBatchProperties("cacheNFromStore[message.creationDate]", "cacheNFromStore[message.title]")
      .|.expandAll("(anon_3)<-[anon_2:MESSAGE_HAS_TAG]-(message)")
      .|.nodeIndexOperator(
        "anon_3:Tag(name = ???)",
        paramExpr = Some(parameter("tagClass", CTAny)),
        getValue = Map("name" -> DoNotGetValue)
      )
      .filter("cacheN[person.name] = $tagClass")
      .remoteBatchProperties("cacheNFromStore[person.name]")
      .nodeByLabelScan("person", "Person")
      .build())(SymmetricalLogicalPlanEquality)
  }
}
