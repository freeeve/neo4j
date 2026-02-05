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
package org.neo4j.cypher.internal.rewriting

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.ShowConstraintsClause
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.rewriting.rewriters.Anonymizer
import org.neo4j.cypher.internal.rewriting.rewriters.anonymizeQuery
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp

class AnonymizeQueryTest extends AnonymizerTestBase {

  val anonymizer: Anonymizer = new Anonymizer {
    override def label(name: String): String = "x" + name
    override def relationshipType(name: String): String = "x" + name
    override def labelOrRelationshipType(name: String): String = "x" + name
    override def propertyKey(name: String): String = "X" + name
    override def variable(name: String): String = "X" + name

    override def unaliasedReturnItemName(anonymizedExpression: Expression, input: String): String =
      prettifier.expr(anonymizedExpression)
    override def parameter(name: String): String = "X" + name
    override def literal(value: String): String = s"string[$value]"
    override def indexName(name: String): String = "X" + name
    override def constraintName(name: String): String = "X" + name
    override def identifierAsString(name: String): String = "X" + name
  }

  val rewriterUnderTest: Rewriter = anonymizeQuery(anonymizer)

  test("variable") {
    assertRewrite("RETURN 1 AS r", "RETURN 1 AS Xr")
    assertRewrite("WITH 1 AS k RETURN (k + k) - k AS r", "WITH 1 AS Xk RETURN (Xk + Xk) - Xk AS Xr")
  }

  test("return item") {
    assertRewrite("WITH 1 AS k RETURN k + k", "WITH 1 AS Xk RETURN Xk + Xk")
  }

  test("label") {
    assertRewrite("MATCH (:LABEL) RETURN count(*)", "MATCH (:xLABEL) RETURN count(*)")
    assertRewrite("MATCH (:A:B) RETURN count(*)", "MATCH (:xA:xB) RETURN count(*)")
    assertRewrite("MATCH (:A)--(:B) RETURN count(*)", "MATCH (:xA)--(:xB) RETURN count(*)")
    assertRewrite("MATCH (:A)-[r*1..5]-(:B) RETURN count(*)", "MATCH (:xA)-[Xr*1..5]-(:xB) RETURN count(*)")
    assertRewrite("MATCH (n) SET n:LABEL", "MATCH (Xn) SET Xn:xLABEL")
  }

  test("relationship type") {
    assertRewrite("MATCH ()-[:R]-() RETURN count(*)", "MATCH ()-[:xR]-() RETURN count(*)")
    assertRewrite("MATCH ()-[:R*2..4]-() RETURN count(*)", "MATCH ()-[:xR*2..4]-() RETURN count(*)")
    assertRewrite(
      "MATCH shortestPath(()-[:R*2..4]-()) RETURN count(*)",
      "MATCH shortestPath(()-[:xR*2..4]-()) RETURN count(*)"
    )
    assertRewrite("MATCH ()-[r]-() SET r:TYPE", "MATCH ()-[Xr]-() SET Xr:xTYPE")
  }

  test("label or relationship type") {
    assertRewrite(
      "MATCH (n)-[r]->() UNWIND [n, r] AS x WITH x WHERE x:A RETURN n",
      "MATCH (Xn)-[Xr]->() UNWIND [Xn, Xr] AS Xx WITH Xx WHERE Xx:xA RETURN Xn"
    )
    assertRewrite(
      "MATCH (n)-[r]->() UNWIND [n, r] AS x WITH x WHERE x:A:B RETURN n",
      "MATCH (Xn)-[Xr]->() UNWIND [Xn, Xr] AS Xx WITH Xx WHERE Xx:xA:xB RETURN Xn"
    )
  }

  test("label expression") {
    assertRewrite("MATCH (n:A&(B|!C)) RETURN n", "MATCH (Xn:xA&(xB|!xC)) RETURN Xn")
    assertRewrite("MATCH (n) WHERE n:A&(B|!C) RETURN n", "MATCH (Xn) WHERE Xn:xA&(xB|!xC) RETURN Xn")
  }

  test("relationship type expression") {
    assertRewrite("MATCH ()-[r:A&(B|!C)]->() RETURN r", "MATCH ()-[Xr:xA&(xB|!xC)]->() RETURN Xr")
    assertRewrite("MATCH ()-[r]->() WHERE r:A&(B|!C) RETURN r", "MATCH ()-[Xr]->() WHERE Xr:xA&(xB|!xC) RETURN Xr")
  }

  test("property key") {
    assertRewrite("MATCH ({p: 2}) RETURN count(*)", "MATCH ({Xp: 2}) RETURN count(*)")
    assertRewrite("MATCH (n) SET p.n = 2", "MATCH (Xn) SET Xp.Xn = 2")
    assertRewrite("MATCH (n) WHERE p.n > 2 RETURN 1", "MATCH (Xn) WHERE Xp.Xn > 2 RETURN 1")
  }

  test("search clause") {
    assertRewrite(
      CypherVersion.Cypher25,
      "MATCH (n) SEARCH n IN ( VECTOR INDEX name FOR [1,2,3] LIMIT 1 ) SCORE AS score",
      "MATCH (Xn) SEARCH Xn IN ( VECTOR INDEX `Xstring[name]` FOR [1,2,3] LIMIT 1 ) SCORE AS Xscore"
    )
  }

  test("parameter") {
    assertRewrite("RETURN $param1 AS p1, $param2 AS p2", "RETURN $Xparam1 AS Xp1, $Xparam2 AS Xp2")
  }

  test("literals") {
    assertRewrite("RETURN \"hello\"", "RETURN \"string[hello]\"")

  }

  test("index commands") {
    // create range
    assertRewrite(
      "CREATE INDEX name FOR (n:Label) ON (n.prop1, n.prop2)",
      "CREATE INDEX `Xstring[name]` FOR (Xn:xLabel) ON (Xn.Xprop1, Xn.Xprop2)"
    )
    assertRewrite(
      "CREATE INDEX name FOR ()-[r:TYPE]-() ON (r.prop)",
      "CREATE INDEX `Xstring[name]` FOR ()-[Xr:xTYPE]-() ON (Xr.Xprop)"
    )
    assertRewrite(
      "CREATE INDEX $name FOR (n:Label) ON (n.prop1, n.prop2)",
      "CREATE INDEX $Xname FOR (Xn:xLabel) ON (Xn.Xprop1, Xn.Xprop2)"
    )
    assertRewrite(
      CypherVersion.Cypher5,
      "CREATE INDEX name FOR (n:Label) ON n.prop OPTIONS {indexProvider: 'range-1.0'}",
      "CREATE INDEX `Xstring[name]` FOR (Xn:xLabel) ON Xn.Xprop OPTIONS {indexProvider: 'string[range-1.0]'}"
    )

    // create text
    assertRewrite(
      "CREATE TEXT INDEX name FOR (n:Label) ON (n.prop)",
      "CREATE TEXT INDEX `Xstring[name]` FOR (Xn:xLabel) ON (Xn.Xprop)"
    )
    assertRewrite(
      "CREATE TEXT INDEX name FOR ()-[r:TYPE]-() ON (r.prop)",
      "CREATE TEXT INDEX `Xstring[name]` FOR ()-[Xr:xTYPE]-() ON (Xr.Xprop)"
    )

    // create point
    assertRewrite(
      "CREATE POINT INDEX name FOR (n:Label) ON (n.prop)",
      "CREATE POINT INDEX `Xstring[name]` FOR (Xn:xLabel) ON (Xn.Xprop)"
    )
    assertRewrite(
      "CREATE POINT INDEX name FOR ()-[r:TYPE]-() ON (r.prop)",
      "CREATE POINT INDEX `Xstring[name]` FOR ()-[Xr:xTYPE]-() ON (Xr.Xprop)"
    )
    assertRewrite(
      "CREATE POINT INDEX name FOR ()-[r:TYPE]-() ON (r.prop) OPTIONS {indexConfig: {`spatial.cartesian.min`: [-5.0, 5.0]}}",
      "CREATE POINT INDEX `Xstring[name]` FOR ()-[Xr:xTYPE]-() ON (Xr.Xprop) OPTIONS {indexConfig: {`Xspatial.cartesian.min`: [-5.0, 5.0]}}"
    )

    // create vector
    assertRewrite(
      "CREATE VECTOR INDEX name FOR (n:Label) ON (n.prop)",
      "CREATE VECTOR INDEX `Xstring[name]` FOR (Xn:xLabel) ON (Xn.Xprop)"
    )
    assertRewrite(
      CypherVersion.Cypher25,
      "CREATE VECTOR INDEX name FOR ()-[r:TYPE|TYPE2]-() ON (r.prop) WITH [r.prop2]",
      "CREATE VECTOR INDEX `Xstring[name]` FOR ()-[Xr:xTYPE|xTYPE2]-() ON (Xr.Xprop) WITH [Xr.Xprop2]"
    )

    // create fulltext
    assertRewrite(
      "CREATE FULLTEXT INDEX name FOR (n:Label1|Label2) ON EACH [n.prop]",
      "CREATE FULLTEXT INDEX `Xstring[name]` FOR (Xn:xLabel1|xLabel2) ON EACH [Xn.Xprop]"
    )
    assertRewrite(
      "CREATE FULLTEXT INDEX name FOR ()-[r:TYPE]-() ON EACH [r.prop]",
      "CREATE FULLTEXT INDEX `Xstring[name]` FOR ()-[Xr:xTYPE]-() ON EACH [Xr.Xprop]"
    )
    assertRewrite(
      "CREATE FULLTEXT INDEX name FOR (n:Label1|Label2) ON EACH [n.prop] OPTIONS {indexConfig: {`fulltext.analyzer`: 'english'}}",
      "CREATE FULLTEXT INDEX `Xstring[name]` FOR (Xn:xLabel1|xLabel2) ON EACH [Xn.Xprop] OPTIONS {indexConfig: {`Xfulltext.analyzer`: 'string[english]'}}"
    )

    // create token lookup
    assertRewrite(
      "CREATE LOOKUP INDEX name FOR (n) ON EACH labels(n)",
      "CREATE LOOKUP INDEX `Xstring[name]` FOR (Xn) ON EACH labels(Xn)"
    )
    assertRewrite(
      "CREATE LOOKUP INDEX name FOR ()-[r]-() ON EACH type(r)",
      "CREATE LOOKUP INDEX `Xstring[name]` FOR ()-[Xr]-() ON EACH type(Xr)"
    )

    // drop
    assertRewrite("DROP INDEX name", "DROP INDEX `Xstring[name]`")
    assertRewrite("DROP INDEX $name", "DROP INDEX $Xname")
  }

  test("constraint commands") {
    // create
    assertRewrite(
      "CREATE CONSTRAINT name FOR (n:Label) REQUIRE (n.prop1, n.prop2) IS NODE KEY",
      "CREATE CONSTRAINT `Xstring[name]` FOR (Xn:xLabel) REQUIRE (Xn.Xprop1, Xn.Xprop2) IS NODE KEY"
    )
    assertRewrite(
      "CREATE CONSTRAINT name FOR (n:Label) REQUIRE (n.prop) IS UNIQUE",
      "CREATE CONSTRAINT `Xstring[name]` FOR (Xn:xLabel) REQUIRE (Xn.Xprop) IS UNIQUE"
    )
    assertRewrite(
      "CREATE CONSTRAINT name FOR (n:Label) REQUIRE (n.prop) IS NOT NULL",
      "CREATE CONSTRAINT `Xstring[name]` FOR (Xn:xLabel) REQUIRE (Xn.Xprop) IS NOT NULL"
    )
    assertRewrite(
      "CREATE CONSTRAINT name FOR ()-[r:TYPE]-() REQUIRE (r.prop) IS NOT NULL",
      "CREATE CONSTRAINT `Xstring[name]` FOR ()-[Xr:xTYPE]-() REQUIRE (Xr.Xprop) IS NOT NULL"
    )
    assertRewrite(
      "CREATE CONSTRAINT $name FOR ()-[r:TYPE]-() REQUIRE (r.prop) IS :: STRING",
      "CREATE CONSTRAINT $Xname FOR ()-[Xr:xTYPE]-() REQUIRE (Xr.Xprop) IS :: STRING"
    )

    // drop
    assertRewrite("DROP CONSTRAINT name", "DROP CONSTRAINT `Xstring[name]`")
    assertRewrite("DROP CONSTRAINT $name", "DROP CONSTRAINT $Xname")
  }

  test("graph type commands") {
    // set
    assertRewrite(
      CypherVersion.Cypher25,
      "ALTER CURRENT GRAPH TYPE SET { CONSTRAINT name FOR (n:Label) REQUIRE (n.prop1, n.prop2) IS KEY }",
      "ALTER CURRENT GRAPH TYPE SET { CONSTRAINT Xname FOR (Xn:xLabel) REQUIRE (Xn.Xprop1, Xn.Xprop2) IS KEY }"
    )
    assertRewrite(
      CypherVersion.Cypher25,
      "ALTER CURRENT GRAPH TYPE SET { (n:Label1 => :Label2 {prop :: STRING}) }",
      "ALTER CURRENT GRAPH TYPE SET { (Xn:xLabel1 => :xLabel2 {Xprop :: STRING}) }"
    )
    assertRewrite(
      CypherVersion.Cypher25,
      "ALTER CURRENT GRAPH TYPE SET { (n:Label1)-[r:TYPE => {prop1 :: INTEGER, prop2 :: ANY NOT NULL}]->(m:Label2 =>) }",
      "ALTER CURRENT GRAPH TYPE SET { (Xn:xLabel1)-[Xr:xTYPE => {Xprop1 :: INTEGER, Xprop2 :: ANY NOT NULL}]->(Xm:xLabel2 =>) }"
    )

    // add
    assertRewrite(
      CypherVersion.Cypher25,
      "ALTER CURRENT GRAPH TYPE ADD { CONSTRAINT name FOR ()-[r:TYPE =>]->() REQUIRE (r.prop1, r.prop2) IS UNIQUE }",
      "ALTER CURRENT GRAPH TYPE ADD { CONSTRAINT Xname FOR ()-[Xr:xTYPE =>]->() REQUIRE (Xr.Xprop1, Xr.Xprop2) IS UNIQUE }"
    )
    assertRewrite(
      CypherVersion.Cypher25,
      "ALTER CURRENT GRAPH TYPE ADD { (n:Label1 => :Label2&Label3 {prop :: STRING NOT NULL}) }",
      "ALTER CURRENT GRAPH TYPE ADD { (Xn:xLabel1 => :xLabel2&xLabel3 {Xprop :: STRING NOT NULL}) }"
    )
    assertRewrite(
      CypherVersion.Cypher25,
      "ALTER CURRENT GRAPH TYPE ADD { (n:Label1)-[r:TYPE => {prop1 :: INTEGER, prop2 :: ANY NOT NULL}]->(m:Label2 =>) }",
      "ALTER CURRENT GRAPH TYPE ADD { (Xn:xLabel1)-[Xr:xTYPE => {Xprop1 :: INTEGER, Xprop2 :: ANY NOT NULL}]->(Xm:xLabel2 =>) }"
    )

    // alter
    assertRewrite(
      CypherVersion.Cypher25,
      "ALTER CURRENT GRAPH TYPE ALTER { (n:Label1 => :Label2&Label3 {prop :: STRING NOT NULL}) }",
      "ALTER CURRENT GRAPH TYPE ALTER { (Xn:xLabel1 => :xLabel2&xLabel3 {Xprop :: STRING NOT NULL}) }"
    )
    assertRewrite(
      CypherVersion.Cypher25,
      "ALTER CURRENT GRAPH TYPE ALTER { (n:Label1)-[r:TYPE => {prop1 :: INTEGER, prop2 :: ANY NOT NULL}]->(m:Label2 =>) }",
      "ALTER CURRENT GRAPH TYPE ALTER { (Xn:xLabel1)-[Xr:xTYPE => {Xprop1 :: INTEGER, Xprop2 :: ANY NOT NULL}]->(Xm:xLabel2 =>) }"
    )

    // drop
    assertRewrite(
      CypherVersion.Cypher25,
      "ALTER CURRENT GRAPH TYPE DROP { CONSTRAINT name }",
      "ALTER CURRENT GRAPH TYPE DROP { CONSTRAINT Xname }"
    )
    assertRewrite(
      CypherVersion.Cypher25,
      "ALTER CURRENT GRAPH TYPE DROP { (n:Label1 => :Label2&Label3 {prop :: STRING NOT NULL}) }",
      "ALTER CURRENT GRAPH TYPE DROP { (Xn:xLabel1 => :xLabel2&xLabel3 {Xprop :: STRING NOT NULL}) }"
    )
    assertRewrite(
      CypherVersion.Cypher25,
      "ALTER CURRENT GRAPH TYPE DROP { (n:Label1 =>) }",
      "ALTER CURRENT GRAPH TYPE DROP { (Xn:xLabel1 =>) }"
    )
    assertRewrite(
      CypherVersion.Cypher25,
      "ALTER CURRENT GRAPH TYPE DROP { (n:Label1)-[r:TYPE => {prop1 :: INTEGER, prop2 :: ANY NOT NULL}]->(m:Label2 =>) }",
      "ALTER CURRENT GRAPH TYPE DROP { (Xn:xLabel1)-[Xr:xTYPE => {Xprop1 :: INTEGER, Xprop2 :: ANY NOT NULL}]->(Xm:xLabel2 =>) }"
    )
    assertRewrite(
      CypherVersion.Cypher25,
      "ALTER CURRENT GRAPH TYPE DROP { ()-[r:TYPE =>]->() }",
      "ALTER CURRENT GRAPH TYPE DROP { ()-[Xr:xTYPE =>]->() }"
    )
  }

  test("show and terminate commands") {
    assertRewrite(
      "SHOW INDEXES WHERE name = 'my_index'",
      "SHOW INDEXES WHERE Xname = 'string[my_index]'"
    )
    assertRewrite(
      // Default columns differ between Cypher versions so only run one version
      CypherVersion.Cypher25,
      "SHOW CONSTRAINTS YIELD name ORDER BY name WHERE name = $param",
      "SHOW CONSTRAINTS YIELD name AS Xname ORDER BY Xname WHERE Xname = $Xparam",
      additionalExpectedAstUpdates = expectedStatement => {
        expectedStatement.endoRewrite(bottomUp(Rewriter.lift {
          case s: ShowConstraintsClause =>
            // Update WITH clause to 'keep' correct default order
            val updatedWithClause = s.yieldWith.map(w => {
              val updatedReturnItems =
                w.returnItems.copy(defaultOrderOnColumns = Some(List("name")))(w.returnItems.position)
              w.copy(returnItems = updatedReturnItems)(w.position)
            })
            s.copy(yieldWith = updatedWithClause)(s.position)
        }))
      }
    )
    assertRewrite(
      "SHOW PROCEDURES EXECUTABLE BY user",
      "SHOW PROCEDURES EXECUTABLE BY Xuser"
    )
    assertRewrite(
      "SHOW FUNCTIONS EXECUTABLE BY user WHERE name",
      "SHOW FUNCTIONS EXECUTABLE BY Xuser WHERE Xname"
    )
    assertRewrite(
      "SHOW SETTINGS 'setting'",
      "SHOW SETTINGS 'string[setting]'"
    )
    assertRewrite(
      "SHOW SETTINGS 'setting1', 'setting2'",
      "SHOW SETTINGS 'string[setting1]', 'string[setting2]'"
    )
    assertRewrite(
      "SHOW TRANSACTIONS $param",
      "SHOW TRANSACTIONS $Xparam"
    )
    assertRewrite(
      "SHOW TRANSACTIONS [$param, 'txId']",
      "SHOW TRANSACTIONS [$Xparam, 'string[txId]']"
    )
    assertRewrite(
      "TERMINATE TRANSACTIONS ['txId']",
      "TERMINATE TRANSACTIONS ['string[txId]']"
    )
    assertRewrite(
      "TERMINATE TRANSACTIONS 'txId', 'txId2'",
      "TERMINATE TRANSACTIONS 'string[txId]', 'string[txId2]'"
    )
  }
}
