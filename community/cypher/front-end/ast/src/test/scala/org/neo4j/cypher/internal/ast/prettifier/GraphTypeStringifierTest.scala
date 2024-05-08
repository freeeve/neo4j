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
package org.neo4j.cypher.internal.ast.prettifier

import org.neo4j.cypher.internal.ast.AstGraphTypeConstructionTestSupport
import org.neo4j.cypher.internal.ast.GraphType
import org.neo4j.cypher.internal.ast.OptionsMap
import org.neo4j.cypher.internal.graphtype.GraphTypeTestCase
import org.neo4j.cypher.internal.util.symbols.DateType
import org.neo4j.cypher.internal.util.symbols.IntegerType
import org.neo4j.cypher.internal.util.symbols.StringType
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.scalatest.Assertion

import scala.collection.immutable.ArraySeq

class GraphTypeStringifierTest extends CypherFunSuite with AstGraphTypeConstructionTestSupport {

  test("Running example RE-1 canonicalised") {
    graphType(
      Seq(
        nodeType("City", Set("Location"), propertyType("name", StringType(isNullable = true))),
        nodeType("Site", Set("Location"), propertyType("name", StringType(isNullable = true))),
        nodeType(
          "Student",
          Set("Person"),
          propertyType("name", StringType(isNullable = false)),
          propertyType("studId", IntegerType(isNullable = true)),
          propertyType("birthday", DateType(isNullable = true))
        ),
        edgeType(identifyingNodeTypeRef("Student"), "LIVES_IN", identifyingNodeTypeRef("City")),
        edgeType(identifyingNodeTypeRef("Student"), "VISITED", nodeTypeRefByLabel("Location"))
      ),
      Seq(
        keyConstraint(identifyingNodeTypeRef("City", "n"), ArraySeq(prop(varFor("n"), "name"))),
        keyConstraint("mySiteConstraint", identifyingNodeTypeRef("Site", "n"), ArraySeq(prop(varFor("n"), "name"))),
        uniquenessConstraint(
          identifyingNodeTypeRef("Student", "n"),
          ArraySeq(prop(varFor("n"), "name"), prop(varFor("n"), "birthday")),
          OptionsMap(Map("indexProvider" -> literalString("range-1.0")))(pos)
        ),
        keyConstraint(identifyingNodeTypeRef("Student", "n"), ArraySeq(prop(varFor("n"), "studId"))),
        propertyTypeConstraint(
          nodeTypeRefByLabel("Person", "n"),
          ArraySeq(prop(varFor("n"), "age")),
          IntegerType(isNullable = true)(defaultPos)
        ),
        uniquenessConstraint(edgeTypeRefByLabel("LegacyRel", "r"), ArraySeq(prop(varFor("r"), "foo")))
      )
    ) shouldStringifyTo
      """{
        | (:`City` => :`Location` {`name` :: STRING}),
        | (:`Site` => :`Location` {`name` :: STRING}),
        | (:`Student` => :`Person` {`birthday` :: DATE, `name` :: STRING NOT NULL, `studId` :: INTEGER}),
        | (:`Student` =>)-[:`LIVES_IN` =>]->(:`City` =>),
        | (:`Student` =>)-[:`VISITED` =>]->(:`Location`),
        | CONSTRAINT FOR (`n`:`City` =>) REQUIRE (`n`.`name`) IS KEY,
        | CONSTRAINT `mySiteConstraint` FOR (`n`:`Site` =>) REQUIRE (`n`.`name`) IS KEY,
        | CONSTRAINT FOR (`n`:`Student` =>) REQUIRE (`n`.`name`, `n`.`birthday`) IS UNIQUE OPTIONS {`indexProvider`: "range-1.0"},
        | CONSTRAINT FOR (`n`:`Student` =>) REQUIRE (`n`.`studId`) IS KEY,
        | CONSTRAINT FOR (`n`:`Person`) REQUIRE (`n`.`age`) IS :: INTEGER,
        | CONSTRAINT FOR ()-[`r`:`LegacyRel`]->() REQUIRE (`r`.`foo`) IS UNIQUE
        |}""".stripMargin
  }

  test("invalid constraint with no alias var") {
    graphType(
      Seq(),
      Seq(
        uniquenessConstraint(nodeTypeRefByLabel("City"), ArraySeq(prop(varFor("n"), "name"))),
        keyConstraint(identifyingNodeTypeRef("Person"), ArraySeq(prop(varFor("p"), "name"))),
        keyConstraint(identifyingNodeTypeRef("Person"), ArraySeq(prop(varFor("p"), "name"))),
        keyConstraint(edgeTypeRefByLabel("REL1"), ArraySeq(prop(varFor("r"), "name"))),
        uniquenessConstraint(identifyingEdgeTypeRef("REL2"), ArraySeq(prop(varFor("r2"), "name")))
      )
    ) shouldStringifyTo (
      """{
        | CONSTRAINT FOR (:`Person` =>) REQUIRE (`p`.`name`) IS KEY,
        | CONSTRAINT FOR (:`City`) REQUIRE (`n`.`name`) IS UNIQUE,
        | CONSTRAINT FOR ()-[:`REL2` =>]->() REQUIRE (`r2`.`name`) IS UNIQUE,
        | CONSTRAINT FOR ()-[:`REL1`]->() REQUIRE (`r`.`name`) IS KEY
        |}""".stripMargin
    )
  }

  GraphTypeTestCase.testcases.collect { case GraphTypeTestCase(name, _, ast, Some(prettifiedCypher)) =>
    test(name) {
      ast shouldStringifyTo prettifiedCypher
    }
  }

  implicit private class GraphTypeMatchers(graphType: GraphType) {

    def shouldStringifyTo(expected: String): Assertion = {
      GraphTypeStringifier.apply(graphType) should be(expected)
    }
  }

}
