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
package org.neo4j.cypher.internal.frontend.phases.rewriting

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.frontend.helpers.TestState
import org.neo4j.cypher.internal.frontend.phases.Monitors
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.UnwrapTopLevelBraces
import org.neo4j.cypher.internal.frontend.phases.rewriting.cnf.TestContext
import org.neo4j.cypher.internal.rewriting.RewriteTest
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class UnwrapTopLevelBracesTest extends CypherFunSuite with RewriteTest {

  override val rewriterUnderTest: Rewriter =
    UnwrapTopLevelBraces.instance(TestState(None), new TestContext(mock[Monitors]))

  test("braces") {
    assertRewrite(CypherVersion.Cypher25, "{ RETURN 1 AS x }", "RETURN 1 AS x")
  }

  test("nested braces") {
    assertRewrite(CypherVersion.Cypher25, "{ { { RETURN 1 AS x } } }", "RETURN 1 AS x")
  }

  test("braces with use") {
    assertRewrite(CypherVersion.Cypher25, "USE graph { RETURN 1 AS x }", "USE graph RETURN 1 AS x")
  }

  test("nested braces with use ") {
    assertRewrite(
      CypherVersion.Cypher25,
      "USE graph { { USE innerGraph { RETURN 1 AS x } } }",
      "USE innerGraph RETURN 1 AS x"
    )
  }

  test("nested braces with use on all") {
    assertRewrite(
      CypherVersion.Cypher25,
      "USE graph { USE innerGraph { USE innerInnerGraph { RETURN 1 AS x } } }",
      "USE innerInnerGraph RETURN 1 AS x"
    )
  }

  test("union with top level braces as arguments") {
    assertRewrite(
      CypherVersion.Cypher25,
      """
        | { RETURN 1 AS x }
        |UNION
        | { RETURN 2 AS x }""".stripMargin,
      """
        | CALL (*) {
        |    RETURN 1 AS x
        | }
        | RETURN *
        |UNION
        | CALL (*) {
        |    RETURN 2 AS x
        | }
        | RETURN *""".stripMargin
    )
  }

  test("union all and union") {
    assertRewrite(
      CypherVersion.Cypher25,
      " { { RETURN 1 AS x UNION RETURN 2 AS x } UNION ALL { RETURN 3 AS x UNION RETURN 4 AS x } }",
      " CALL (*) { RETURN 1 AS x UNION RETURN 2 AS x } RETURN * UNION ALL CALL (*) { RETURN 3 AS x UNION RETURN 4 AS x } RETURN * "
    )
  }

  test("union and union all") {
    assertRewrite(
      CypherVersion.Cypher25,
      " { { RETURN 1 AS x UNION ALL RETURN 2 AS x } UNION { RETURN 3 AS x UNION ALL RETURN 4 AS x } }",
      " CALL (*) { RETURN 1 AS x UNION ALL RETURN 2 AS x } RETURN * UNION CALL (*) { RETURN 3 AS x UNION ALL RETURN 4 AS x } RETURN *"
    )
  }

  test("nested union all and union") {
    assertRewrite(
      CypherVersion.Cypher25,
      """ {
        |   {
        |      {
        |         RETURN 1 AS x
        |         UNION ALL
        |         RETURN 2 AS x
        |      }
        |      UNION
        |      RETURN 3 AS x
        |   }
        |   UNION ALL
        |   {
        |      RETURN 4 AS x
        |      UNION
        |      RETURN 5 AS x
        |   }
        | }""".stripMargin,
      """ CALL (*) {
        |   CALL (*) {
        |      RETURN 1 AS x
        |      UNION ALL
        |      RETURN 2 AS x
        |   }
        |   RETURN *
        |   UNION
        |   RETURN 3 AS x
        |}
        |RETURN *
        |UNION ALL
        |CALL (*) {
        |   RETURN 4 AS x
        |   UNION
        |   RETURN 5 AS x
        |}
        |RETURN * """.stripMargin
    )
  }

  test("nested union and union all") {
    assertRewrite(
      CypherVersion.Cypher25,
      " { { RETURN 1 AS x UNION ALL RETURN 2 AS x } UNION { RETURN 3 AS x UNION ALL { RETURN 4 AS x UNION RETURN 5 AS x  } } }",
      " CALL (*) { RETURN 1 AS x UNION ALL RETURN 2 AS x } RETURN * UNION CALL (*) { RETURN 3 AS x UNION ALL CALL (*) { RETURN 4 AS x UNION RETURN 5 AS x } RETURN * } RETURN *"
    )
  }

  test("union multiple arms") {
    assertRewrite(
      CypherVersion.Cypher25,
      """{ RETURN 1 AS x }
        |UNION
        |{ RETURN 2 AS x }
        |UNION
        |{ RETURN 3 AS x }
        |UNION
        |{ RETURN 4 AS x }""".stripMargin,
      """CALL (*) { RETURN 1 AS x } RETURN *
        |UNION
        |CALL (*) { RETURN 2 AS x } RETURN *
        |UNION
        |CALL (*) { RETURN 3 AS x } RETURN *
        |UNION
        |CALL (*) { RETURN 4 AS x } RETURN *""".stripMargin
    )
  }

  test("wrapped union") {
    assertRewrite(
      CypherVersion.Cypher25,
      """{ RETURN 1 AS x
        |UNION
        | RETURN 2 AS x }""".stripMargin,
      """RETURN 1 AS x
        |UNION
        |RETURN 2 AS x""".stripMargin
    )
  }

  test("wrapped union with use") {
    assertRewrite(
      CypherVersion.Cypher25,
      """USE graph { RETURN 1 AS x
        |UNION
        | RETURN 2 AS x }""".stripMargin,
      """USE graph
        |RETURN 1 AS x
        |UNION
        |USE graph
        |RETURN 2 AS x""".stripMargin
    )
  }

  test("wrapped union multiple arms") {
    assertRewrite(
      CypherVersion.Cypher25,
      """{ { RETURN 1 AS x }
        |UNION
        |{ RETURN 2 AS x }
        |UNION
        |{ RETURN 3 AS x }
        |UNION
        |{ RETURN 4 AS x } }""".stripMargin,
      """CALL (*) { RETURN 1 AS x } RETURN *
        |UNION
        |CALL (*) { RETURN 2 AS x } RETURN *
        |UNION
        |CALL (*) { RETURN 3 AS x } RETURN *
        |UNION
        |CALL (*) { RETURN 4 AS x } RETURN *""".stripMargin
    )
  }

  test("union no return") {
    assertRewrite(
      CypherVersion.Cypher25,
      """{ CREATE (n) }
        |UNION
        |{ CREATE (n) }""".stripMargin,
      """CALL (*) { CREATE (n) } FINISH
        |UNION
        |CALL (*) { CREATE (n) } FINISH """.stripMargin
    )
  }

  test("union multiple arms no return") {
    assertRewrite(
      CypherVersion.Cypher25,
      """{ CREATE (n) }
        |UNION
        |{ CREATE (n) }
        |UNION
        |{ CREATE (n) }
        |UNION
        |{ CREATE (n) }""".stripMargin,
      """CALL (*) { CREATE (n) } FINISH
        |UNION
        |CALL (*) { CREATE (n) } FINISH
        |UNION
        |CALL (*) { CREATE (n) } FINISH
        |UNION
        |CALL (*) { CREATE (n) } FINISH """.stripMargin
    )
  }

  test("union with use") {
    assertRewrite(
      CypherVersion.Cypher25,
      """USE graphLeft { RETURN 1 AS x }
        |UNION
        |USE graphRight { RETURN 2 AS x }""".stripMargin,
      """CALL (*) { USE graphLeft RETURN 1 AS x } RETURN *
        |UNION
        |CALL (*) { USE graphRight RETURN 2 AS x } RETURN *""".stripMargin
    )
  }

  test("union multiple arms with some use") {
    assertRewrite(
      CypherVersion.Cypher25,
      """USE graphLeft { RETURN 1 AS x }
        |UNION
        |{ RETURN 2 AS x }
        |UNION
        |USE graphLeft { RETURN 3 AS x }
        |UNION { RETURN 4 AS x }""".stripMargin,
      """CALL (*) { USE graphLeft RETURN 1 AS x } RETURN *
        |UNION
        |CALL (*) { RETURN 2 AS x } RETURN *
        |UNION
        |CALL (*) { USE graphLeft RETURN 3 AS x } RETURN *
        |UNION
        |CALL (*) { RETURN 4 AS x } RETURN *""".stripMargin
    )
  }

  test("wrapped union multiple arms with outerUse") {
    assertRewrite(
      CypherVersion.Cypher25,
      """USE outerGraph {
        |   USE innerGraph { RETURN 1 AS x }
        |   UNION
        |   USE innerGraph { RETURN 2 AS x }
        |   UNION
        |   USE innerGraph { RETURN 3 AS x }
        |   UNION
        |   USE innerGraph { RETURN 4 AS x }
        |}""".stripMargin,
      """CALL (*) { USE innerGraph RETURN 1 AS x } RETURN *
        |UNION
        |CALL (*) { USE innerGraph RETURN 2 AS x } RETURN *
        |UNION
        |CALL (*) { USE innerGraph RETURN 3 AS x } RETURN *
        |UNION
        |CALL (*) { USE innerGraph RETURN 4 AS x } RETURN *""".stripMargin
    )
  }

  test("wrapped union multiple different arms with outerUse") {
    assertRewrite(
      CypherVersion.Cypher25,
      """USE outerGraph {
        |   USE innerGraph { RETURN 1 AS x }
        |   UNION
        |   USE innerGraph { RETURN 2 AS x }
        |   UNION
        |   { RETURN 3 AS x }
        |   UNION
        |   RETURN 4 AS x
        |}""".stripMargin,
      """CALL (*) { USE innerGraph RETURN 1 AS x } RETURN *
        |UNION
        |CALL (*) { USE innerGraph RETURN 2 AS x } RETURN *
        |UNION
        |CALL (*) { USE outerGraph RETURN 3 AS x } RETURN *
        |UNION
        |USE outerGraph RETURN 4 AS x """.stripMargin
    )
  }

  test("union with use and no return") {
    assertRewrite(
      CypherVersion.Cypher25,
      """USE leftGraph { CREATE (n) }
        |UNION
        |USE rightGraph { CREATE (n) }""".stripMargin,
      """CALL (*) { USE leftGraph CREATE (n) } FINISH
        |UNION
        |CALL (*) { USE rightGraph CREATE (n) } FINISH """.stripMargin
    )
  }

  test("union with nested use in single query within union") {
    assertRewriteWithFeatures(
      CypherVersion.Cypher25,
      """USE leftGraph {
        |MATCH (n) RETURN n
        |UNION ALL
        |USE rightGraph MATCH (n) RETURN n}
    """.stripMargin,
      """USE leftGraph
        |MATCH (n) RETURN n
        |UNION ALL
        |USE rightGraph
        |MATCH (n) RETURN n
        |""".stripMargin
    )
  }

  test("nested unions #1") {
    assertRewrite(
      CypherVersion.Cypher25,
      """
        | { RETURN 1 AS x UNION RETURN 2 AS x }
        |UNION
        | { RETURN 3 AS x UNION RETURN 4 AS x }""".stripMargin,
      """
        | CALL (*) {
        |    RETURN 1 AS x
        |    UNION
        |    RETURN 2 AS x
        | }
        | RETURN *
        |UNION
        | CALL (*) {
        |    RETURN 3 AS x
        |    UNION
        |    RETURN 4 AS x
        | }
        | RETURN *""".stripMargin
    )
  }

  test("nested unions #2") {
    assertRewrite(
      CypherVersion.Cypher25,
      """
        | { RETURN 1 AS x UNION RETURN 2 AS x UNION RETURN 3 AS x }
        |UNION
        | { { { { RETURN 3 AS x UNION RETURN 4 AS x } } } }""".stripMargin,
      """
        | CALL (*) {
        |    RETURN 1 AS x
        |    UNION
        |    RETURN 2 AS x
        |    UNION
        |    RETURN 3 AS x
        | }
        | RETURN *
        |UNION
        | CALL (*) {
        |    RETURN 3 AS x
        |    UNION
        |    RETURN 4 AS x
        | }
        | RETURN *""".stripMargin
    )
  }

  test("nested unions #3") {
    assertRewrite(
      CypherVersion.Cypher25,
      """
        | { RETURN 1 AS x UNION { RETURN 10 AS x UNION ALL RETURN 10 AS x } UNION RETURN 3 AS x }
        |UNION
        | { { { { RETURN 3 AS x UNION RETURN 4 AS x } } } }""".stripMargin,
      """
        | CALL (*) {
        |    RETURN 1 AS x
        |    UNION
        |    CALL (*) {
        |       RETURN 10 AS x
        |       UNION ALL
        |       RETURN 10 AS x
        |    }
        |    RETURN *
        |    UNION
        |    RETURN 3 AS x
        | }
        | RETURN *
        |UNION
        | CALL (*) {
        |    RETURN 3 AS x
        |    UNION
        |    RETURN 4 AS x
        | }
        | RETURN *""".stripMargin
    )
  }

  test("nested unions #4") {
    assertRewrite(
      CypherVersion.Cypher25,
      """
        |USE outerGraph {
        | { RETURN 1 AS x UNION USE innerGraph { RETURN 10 AS x UNION ALL RETURN 10 AS x } UNION RETURN 3 AS x }
        |UNION
        | RETURN 3 AS x UNION RETURN 4 AS x
        |}""".stripMargin,
      """
        | CALL (*) {
        |    USE outerGraph
        |    RETURN 1 AS x
        |    UNION
        |    CALL (*) {
        |       USE innerGraph
        |       RETURN 10 AS x
        |       UNION ALL
        |       USE innerGraph
        |       RETURN 10 AS x
        |    }
        |    RETURN *
        |    UNION
        |    USE outerGraph
        |    RETURN 3 AS x
        | }
        | RETURN *
        |UNION
        | USE outerGraph
        | RETURN 3 AS x
        | UNION
        | USE outerGraph
        | RETURN 4 AS x""".stripMargin
    )
  }

  override protected def assertRewrite(version: CypherVersion, originalQuery: String, expectedQuery: String): Unit = {
    val (expected, result) = getRewrite(version, originalQuery, expectedQuery)
    assert(
      normalizeReturn(result.asInstanceOf[Query]) === expected,
      s"\n$originalQuery\nshould be rewritten to:\n${prettifier.asString(expected)}\nbut was rewritten to:\n${prettifier.asString(result.asInstanceOf[Statement])}"
    )
  }

  override protected def assertRewriteWithFeatures(
    version: CypherVersion,
    originalQuery: String,
    expectedQuery: String
  ): Unit = {
    val (expected, result) = getRewriteWithFeatures(version, originalQuery, expectedQuery)
    assert(
      normalizeReturn(result.asInstanceOf[Query]) === expected,
      s"\n$originalQuery\nshould be rewritten to:\n${prettifier.asString(expected)}\nbut was rewritten to:\n${prettifier.asString(result.asInstanceOf[Statement])}"
    )
  }

  private def normalizeReturn(query: Query): Query = {
    // Removes marker to returns allowing them to throw helpful message
    query.endoRewrite(bottomUp(Rewriter.lift {
      case ret: Return =>
        ret.copy()(ret.position)
    }))

  }

}
