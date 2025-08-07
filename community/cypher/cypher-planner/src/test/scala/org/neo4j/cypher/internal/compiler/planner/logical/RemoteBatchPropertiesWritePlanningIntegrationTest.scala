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
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningIntegrationTestSupport
import org.neo4j.cypher.internal.expressions.LabelName
import org.neo4j.cypher.internal.expressions.PropertyKeyName
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.SemanticDirection.OUTGOING
import org.neo4j.cypher.internal.ir.EagernessReason
import org.neo4j.cypher.internal.ir.HasHeaders
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createNodeFull
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.createRelationship
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder.setNodeProperty
import org.neo4j.cypher.internal.logical.plans.DoNotGetValue
import org.neo4j.cypher.internal.planner.spi.DatabaseMode
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.internal.util.collection.immutable.ListSet
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class RemoteBatchPropertiesWritePlanningIntegrationTest extends CypherFunSuite
    with LogicalPlanningIntegrationTestSupport with AstConstructionTestSupport {

  // Graph counts based on a subset of LDBC SF 1
  final protected val planner = plannerBuilder()
    .setDatabaseMode(DatabaseMode.SHARDED)
    .withSetting(
      GraphDatabaseInternalSettings.cypher_remote_batch_properties_implementation,
      RemoteBatchPropertiesImplementation.PLANNER
    )
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
    .setLabelCardinality("Dog", 10)
    .setRelationshipCardinality("()-[:HAS_DOG]->()", 10)
    .setRelationshipCardinality("(:Person)-[:HAS_DOG]->()", 10)
    .setRelationshipCardinality("()-[:HAS_DOG]->(:Person)", 10)
    .setRelationshipCardinality("(:Person)-[:HAS_DOG]->(:Person)", 10)
    .setRelationshipCardinality("(:Person)-[:HAS_DOG]->(:Dog)", 10)
    .setRelationshipCardinality("()-[:FRIEND]->()", 10)
    .setRelationshipCardinality("(:Person)-[:FRIEND]->()", 10)
    .setRelationshipCardinality("()-[:FRIEND]->(:Person)", 10)
    .setRelationshipCardinality("(:Person)-[:FRIEND]->(:Person)", 10)
    .build()

  test("should fetch properties again after properties have been set") {
    val query =
      """
        |MATCH (n:Person {name: 'Alice', age: 30})
        |SET n.age = 31
        |RETURN n.name, n.age
        |""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("`n.name`", "`n.age`")
      .projection("cacheN[n.name] AS `n.name`", "cacheN[n.age] AS `n.age`")
      .remoteBatchProperties("cacheNFromStore[n.name]", "cacheNFromStore[n.age]")
      .setNodeProperty("n", "age", "31")
      .filter("cacheN[n.name] = 'Alice'", "cacheN[n.age] = 30")
      .remoteBatchProperties("cacheNFromStore[n.name]", "cacheNFromStore[n.age]")
      .nodeByLabelScan("n", "Person")
      .build()
  }

  test("should set remote batch properties after a create") {
    val query =
      """
        |CREATE
        |  (max:Person {name: 'Max'}),
        |  (chris:Person {name: 'Chris'})
        |CREATE (max)-[:KNOWS {prop: 42}]->(chris)
        |WITH *
        |MATCH (a)-[r:KNOWS]->(b)
        |WHERE r.prop = 42
        |RETURN a.name, b.name
        |""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner.planBuilder()
      .produceResults("`a.name`", "`b.name`")
      .projection("cacheN[a.name] AS `a.name`", "cacheN[b.name] AS `b.name`")
      .remoteBatchProperties("cacheNFromStore[a.name]", "cacheNFromStore[b.name]")
      .filter("cacheR[r.prop] = 42")
      .remoteBatchProperties("cacheRFromStore[r.prop]")
      .apply()
      .|.relationshipTypeScan("(a)-[r:KNOWS]->(b)")
      .eager(ListSet(
        EagernessReason.TypeReadSetConflict(RelTypeName("KNOWS")(InputPosition.NONE)).withConflict(
          EagernessReason.Conflict(Id(8), Id(6))
        ),
        EagernessReason.PropertyReadSetConflict(PropertyKeyName("prop")(InputPosition.NONE)).withConflict(
          EagernessReason.Conflict(Id(8), Id(6))
        ),
        EagernessReason.LabelReadSetConflict(LabelName("Person")(InputPosition.NONE)).withConflict(
          EagernessReason.Conflict(Id(8), Id(6))
        )
      ))
      .create(
        createNodeFull("max", labels = Seq("Person"), properties = Some("{name: 'Max'}")),
        createNodeFull("chris", labels = Seq("Person"), properties = Some("{name: 'Chris'}")),
        createRelationship("anon_0", "max", "KNOWS", "chris", OUTGOING, Some("{prop: 42}"))
      )
      .argument()
      .build()
  }

  test("should plan remoteBatchProperties on sort after transaction") {
    val plan = planner.plan(
      """LOAD CSV WITH HEADERS from $param AS row
        |CALL {
        |  WITH row
        |  CREATE (n {name: row.name, age: toInteger(row.age)})
        |  RETURN n
        |} IN TRANSACTIONS
        |RETURN n.name, n.age ORDER BY n.age ASC""".stripMargin
    )

    plan shouldEqual
      planner.planBuilder()
        .produceResults("`n.name`", "`n.age`")
        .projection("cacheN[n.name] AS `n.name`")
        .remoteBatchProperties("cacheNFromStore[n.name]")
        .sort("`n.age` ASC")
        .projection("cacheN[n.age] AS `n.age`")
        .remoteBatchProperties("cacheNFromStore[n.age]")
        .transactionApply()
        .|.create(createNodeFull("n", properties = Some("{name: row.name, age: toInteger(row.age)}")))
        .|.argument("row")
        .loadCSV("$param", "row", HasHeaders, None)
        .argument()
        .build()
  }

  test("should fetch properties when using dynamic labels") {
    val query = """
                  |LOAD CSV WITH HEADERS FROM $param AS line
                  |CREATE (n {name: line.name})
                  |SET n:$(line.role)
                  |RETURN n.name""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("`n.name`")
        .projection("cacheN[n.name] AS `n.name`")
        .remoteBatchProperties("cacheNFromStore[n.name]")
        .setLabels("n", Seq(), Seq("line.role"))
        .create(createNodeFull("n", properties = Some("{name: line.name}")))
        .loadCSV("$param", "line", HasHeaders, None)
        .argument()
        .build()
  }

  test("should fetch properties on merge queries") {
    val query =
      """
        |MERGE (p:Person {name: 'Andy'})
        |ON MATCH SET p.existed = true
        |ON CREATE SET p.existed = false
        |RETURN p.name, p.existed
        |""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("`p.name`", "`p.existed`")
        .projection("cacheN[p.name] AS `p.name`", "cacheN[p.existed] AS `p.existed`")
        .remoteBatchProperties("cacheNFromStore[p.name]", "cacheNFromStore[p.existed]")
        .merge(
          Seq(createNodeFull("p", labels = Seq("Person"), properties = Some("{name: 'Andy'}"))),
          Seq.empty,
          Seq(setNodeProperty("p", "existed", "true")),
          Seq(setNodeProperty("p", "existed", "false")),
          Set.empty
        )
        .filter("cacheN[p.name] = 'Andy'")
        .remoteBatchProperties("cacheNFromStore[p.name]", "cacheNFromStore[p.existed]")
        .nodeByLabelScan("p", "Person")
        .build()
  }

  test("should not fetch properties on locking merge queries") { // locking merge is not supported yet
    val query =
      """
        |MATCH  (andy:Person {name: 'Andy'}),
        |       (lou:Person {name: 'Lou'})
        |MERGE (andy)-[r:KNOWS {from: 'Factory'}]->(lou)
        |RETURN r.name, r.existed
        |""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual
      planner.planBuilder()
        .produceResults("`r.name`", "`r.existed`")
        .projection("cacheR[r.name] AS `r.name`", "cacheR[r.existed] AS `r.existed`")
        .apply()
        .|.merge(
          Seq.empty,
          Seq(createRelationship("r", "andy", "KNOWS", "lou", OUTGOING, Some("{from: 'Factory'}"))),
          Seq.empty,
          Seq.empty,
          Set("andy", "lou")
        )
        .|.cacheProperties(
          "cacheRFromStore[r.name]",
          "cacheRFromStore[r.existed]"
        ) // insert cache properties invoked because RemoteBatchProperties is not supported for locking merge
        .|.filter("r.from = 'Factory'")
        .|.expandInto("(andy)-[r:KNOWS]->(lou)")
        .|.argument("andy", "lou")
        .cartesianProduct()
        .|.filter("lou.name = 'Lou'")
        .|.nodeByLabelScan("lou", "Person")
        .filter("andy.name = 'Andy'")
        .nodeByLabelScan("andy", "Person")
        .build()
  }

  test("should set eager when filters use cached properties") {
    val query =
      """MATCH (person:Person {id:$Person}) WHERE person.firstName IS NOT NULL
        |CALL {
        |  WITH person
        |  SET person.firstName = 'Unknown'
        |  RETURN person AS x
        |}
        |RETURN x.firstName AS personFirstName""".stripMargin

    val plan = planner.plan(query)

    plan shouldEqual planner
      .planBuilder()
      .produceResults("personFirstName")
      .projection("cacheN[x.firstName] AS personFirstName")
      .remoteBatchProperties("cacheNFromStore[x.firstName]")
      .eager(ListSet(
        EagernessReason
          .PropertyReadSetConflict(PropertyKeyName("firstName")(InputPosition.NONE))
          .withConflict(EagernessReason.Conflict(Id(6), Id(1))),
        EagernessReason
          .PropertyReadSetConflict(PropertyKeyName("firstName")(InputPosition.NONE))
          .withConflict(EagernessReason.Conflict(Id(6), Id(2)))
      ))
      .apply()
      .|.projection("person AS x")
      .|.setNodeProperty("person", "firstName", "'Unknown'")
      .|.argument("person")
      .eager(ListSet(
        EagernessReason
          .PropertyReadSetConflict(PropertyKeyName("firstName")(InputPosition.NONE))
          .withConflict(EagernessReason.Conflict(Id(6), Id(9))),
        EagernessReason
          .PropertyReadSetConflict(PropertyKeyName("firstName")(InputPosition.NONE))
          .withConflict(EagernessReason.Conflict(Id(6), Id(10)))
      ))
      .filter("cacheN[person.firstName] IS NOT NULL")
      .remoteBatchProperties("cacheNFromStore[person.firstName]")
      .nodeIndexOperator(
        "person:Person(id = ???)",
        paramExpr = Seq(parameter("Person", CTAny)),
        getValue = Map("id" -> DoNotGetValue),
        unique = true
      )
      .build()
  }
}
