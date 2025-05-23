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
import org.neo4j.cypher.internal.ast.AddedInRewriteGeneral
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.DefaultWith
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.ExpandNext
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.SemanticAnalysis
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.kernel.database.NormalizedDatabaseName

class ExpandNextTest extends CypherFunSuite with RewritePhaseTest with AstConstructionTestSupport {

  override def rewriterPhaseUnderTest: Transformer[BaseContext, BaseState, BaseState] =
    SemanticAnalysis(Some(false), semanticFeatures: _*) andThen
      ExpandNext

  override def astRewriteAndAnalyze: Boolean = false
  val sessionDatabaseName: String = NormalizedDatabaseName.normalize("sessionDb");
  override def sessionDatabase: String = sessionDatabaseName
  override def targetsComposite: Boolean = true
  override def semanticFeatures: Seq[SemanticFeature] = Seq(SemanticFeature.UseAsMultipleGraphsSelector)

  private def withUpdate(exclude: Set[String] = Set.empty) = (expectedStatement: Statement) => {
    val transformed = expectedStatement.endoRewrite(bottomUp(Rewriter.lift {
      // The original/rewritten statement will have AddedInRewriteGeneral,
      // the explicit WITH in the expected will have DefaultWith
      // so let's update that before checking the equality
      case w: With if w.withType == DefaultWith =>
        w.copy(withType = AddedInRewriteGeneral)(w.position)
    }))
    transformed match {
      case q: Query if q.isReturning =>
        q.mapEachSingleQuery(sq =>
          sq.copy(
            sq.clauses.dropRight(1) ++ sq.getReturns.map(r => r.copy(excludedNames = exclude)(r.position))
          )(sq.position)
        )
      case x => x
    }
  }

  test("when single branch rewritten 1") {
    assertRewritten(
      CypherVersion.Cypher25,
      """LET a = 1
        |RETURN a
        |
        |NEXT
        |
        |LET b = a + 1
        |RETURN a, b
        |
        |NEXT
        |
        |LET c = a + b + 1
        |RETURN b, c
        |
        |NEXT
        |
        |LET d = b + c
        |RETURN d
        |""".stripMargin,
      """LET a = 1
        |WITH a
        |LET b = a + 1
        |WITH a, b
        |LET c = a + b + 1
        |WITH b, c
        |LET d = b + c
        |RETURN d
        |""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("when single branch rewritten 2") {
    assertRewritten(
      CypherVersion.Cypher25,
      """LET a = 1
        |RETURN a
        |
        |NEXT
        |
        |FINISH
        |""".stripMargin,
      """LET a = 1
        |WITH a
        |FINISH
        |""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("when single branch rewritten 3") {
    assertRewritten(
      CypherVersion.Cypher25,
      """LET a = 1
        |RETURN a
        |
        |NEXT
        |
        |FINISH
        |
        |NEXT
        |
        |LET b = 1
        |RETURN b
        |""".stripMargin,
      """LET a = 1
        |WITH a
        |WITH count(NULL) AS `  UNNAMED0`
        |LET b = 1
        |RETURN b
        |""".stripMargin,
      additionalExpectedAstUpdates = withUpdate(Set("  UNNAMED0"))
    )
  }

  test("when single branch rewritten 4") {
    assertRewritten(
      CypherVersion.Cypher25,
      """FINISH
        |
        |NEXT
        |
        |LET a = 1
        |RETURN a
        |
        |NEXT
        |
        |LET b = a + 1
        |RETURN a, b
        |""".stripMargin,
      """WITH count(NULL) AS `  UNNAMED0`
        |LET a = 1
        |WITH a AS a
        |LET b = a + 1
        |RETURN a AS a, b AS b
        |""".stripMargin,
      additionalExpectedAstUpdates = withUpdate(Set("  UNNAMED0"))
    )
  }

  test("when single branch rewritten 5") {
    assertRewritten(
      CypherVersion.Cypher25,
      """LET a = 1
        |CALL (a) {
        |  RETURN 1 as b
        |
        |  NEXT
        |
        |  RETURN b+a as c
        |}
        |RETURN c
        |""".stripMargin,
      """LET a = 1
        |CALL (a) {
        |  WITH 1 as b
        |  RETURN b+a as c
        |}
        |RETURN c
        |""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("when single branch rewritten 6") {
    assertRewritten(
      CypherVersion.Cypher25,
      """RETURN 1 AS a
        |UNION
        |RETURN 2 AS a
        |
        |NEXT
        |
        |RETURN a + 1 AS b
        |UNION
        |RETURN a + 2 AS b
        |""".stripMargin,
      """CALL (*) {
        |  RETURN 1 AS `  UNNAMED0`
        |  UNION
        |  RETURN 2 AS `  UNNAMED0`
        |}
        |WITH `  UNNAMED0` AS a
        |CALL (*) {
        |  RETURN a + 1 AS `  UNNAMED1`
        |  UNION
        |  RETURN a + 2 AS `  UNNAMED1`
        |}
        |RETURN `  UNNAMED1` AS b""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("when single branch rewritten 7") {
    assertRewritten(
      CypherVersion.Cypher25,
      """LET x = 1, y = 2
        |RETURN x
        |
        |NEXT
        |
        |RETURN 1 + x AS a
        |UNION
        |LET y = 3
        |RETURN 2 + y AS a
        |
        |NEXT
        |
        |RETURN a + 1 AS b
        |UNION
        |RETURN a + 2 AS b
        |""".stripMargin,
      """|LET x = 1, y = 2
         |WITH x AS x
         |CALL (*) {
         |  RETURN 1 + x AS `  UNNAMED0`
         |  UNION
         |  LET y = 3
         |  RETURN 2 + y AS `  UNNAMED0`
         |}
         |WITH `  UNNAMED0` AS a
         |CALL (*) {
         |  RETURN a + 1 AS `  UNNAMED1`
         |  UNION
         |  RETURN a + 2 AS `  UNNAMED1`
         |}
         |RETURN `  UNNAMED1` AS b""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("when single branch rewritten 8") {
    assertRewritten(
      CypherVersion.Cypher25,
      """{
        |  RETURN 1 AS a
        |
        |  NEXT
        |
        |  RETURN a + 1 AS b
        |}
        |
        |NEXT
        |
        |RETURN b + 1 AS c
        |""".stripMargin,
      """CALL (*) {
        |  {
        |    WITH 1 AS a
        |    RETURN a + 1 AS `  UNNAMED0`
        |  }
        |}
        |WITH `  UNNAMED0` AS b
        |RETURN b + 1 AS c""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("when single branch rewritten 9") {
    assertRewritten(
      CypherVersion.Cypher25,
      """MATCH (n)
        |RETURN n
        |
        |NEXT
        |
        |WHEN n.x > 2 THEN
        |  RETURN "large number" AS msg
        |WHEN n.x > 1 THEN
        |  RETURN "small number" AS msg
        |ELSE
        |  RETURN "tiny number" AS msg
        |
        |NEXT
        |
        |RETURN collect(msg) AS messages
        |""".stripMargin,
      """MATCH (n)
        |WITH n AS n
        |CALL (*) {
        |  WHEN n.x > 2 THEN RETURN "large number" AS `  UNNAMED0`
        |  WHEN n.x > 1 THEN RETURN "small number" AS `  UNNAMED0`
        |  ELSE RETURN "tiny number" AS `  UNNAMED0`
        |}
        |WITH `  UNNAMED0` AS msg
        |RETURN collect(msg) AS messages""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("when single branch rewritten 10") {
    assertRewritten(
      CypherVersion.Cypher25,
      """LET a = 1
        |LET x = EXISTS {
        |  RETURN a + 1 AS b
        |
        |  NEXT
        |
        |  RETURN a + b AS c
        |
        |  NEXT
        |
        |  RETURN a + c AS d
        |}
        |RETURN a, x
        |""".stripMargin,
      """LET a = 1
        |LET x = EXISTS {
        |  WITH a + 1 AS b
        |  WITH a + b AS c
        |  RETURN a + c AS d
        |}
        |RETURN a, x
        |""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("when single branch rewritten 11") {
    assertRewritten(
      CypherVersion.Cypher25,
      """LET x = 1, y = 2
        |RETURN x, y
        |
        |NEXT
        |
        |RETURN 1 + x AS a
        |UNION
        |RETURN 2 + y AS a
        |
        |NEXT
        |
        |RETURN a + 1 AS b
        |UNION
        |RETURN a + 2 AS b
        |
        |NEXT
        |
        |RETURN *
        |""".stripMargin,
      """LET x = 1, y = 2
        |WITH x AS x, y AS y
        |CALL (*) {
        |  RETURN 1 + x AS `  UNNAMED0`
        |  UNION
        |  RETURN 2 + y AS `  UNNAMED0`
        |}
        |WITH `  UNNAMED0` AS a
        |CALL (*) {
        |  RETURN a + 1 AS `  UNNAMED1`
        |  UNION
        |  RETURN a + 2 AS `  UNNAMED1`
        |}
        |WITH `  UNNAMED1` AS b
        |RETURN *""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("when single branch rewritten 12") {
    assertRewritten(
      CypherVersion.Cypher25,
      """LET x = 1, y = 2
        |RETURN x, y
        |
        |NEXT
        |
        |RETURN 1 + x AS a
        |UNION
        |RETURN 2 + y AS a
        |
        |NEXT
        |
        |LET b = a + 1
        |RETURN *
        |UNION
        |LET b = a + 2
        |RETURN *
        |
        |NEXT
        |
        |RETURN *
        |""".stripMargin,
      """LET x = 1, y = 2
        |WITH x AS x, y AS y
        |CALL (*) {
        |  RETURN 1 + x AS `  UNNAMED0`
        |  UNION
        |  RETURN 2 + y AS `  UNNAMED0`
        |}
        |WITH `  UNNAMED0` AS a
        |CALL (*) {
        |  LET b = a + 1
        |  RETURN *
        |  UNION
        |  LET b = a + 2
        |  RETURN *
        |}
        |WITH *
        |RETURN *""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("when single branch rewritten 13") {
    assertRewritten(
      CypherVersion.Cypher25,
      """LET a = 1
        |RETURN a
        |
        |NEXT
        |
        |LET b = a + 1
        |RETURN a, b
        |UNION
        |LET b = a + 2
        |RETURN a, b
        |
        |NEXT
        |
        |LET c = b + 1
        |RETURN a, b, c
        |""".stripMargin,
      """LET a = 1
        |WITH a AS a
        |CALL (*) {
        |  LET b = a + 1
        |  RETURN a AS `  UNNAMED0`, b AS `  UNNAMED1`
        |  UNION
        |  LET b = a + 2
        |  RETURN a AS `  UNNAMED0`, b AS `  UNNAMED1`
        |}
        |WITH `  UNNAMED0` AS a, `  UNNAMED1` AS b
        |LET c = b + 1
        |RETURN a AS a, b AS b, c AS c""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("when single branch rewritten 14") {
    assertRewritten(
      CypherVersion.Cypher25,
      """USE neo1
        |MATCH (n:L1)
        |RETURN *
        |
        |NEXT
        |
        |MATCH (m:L1)
        |RETURN *
        |
        |NEXT
        |
        |MATCH(o:L2)
        |RETURN n.x + m.x + o.x
        |""".stripMargin,
      """CALL (*) {
        |  USE `neo1`
        |  MATCH (n:L1)
        |  RETURN *
        |}
        |WITH *
        |MATCH (m:L1)
        |WITH *
        |MATCH (o:L2)
        |RETURN (n.x + m.x) + o.x AS `n.x + m.x + o.x`""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("when single branch rewritten 15") {
    assertRewritten(
      CypherVersion.Cypher25,
      """USE neo1
        |MATCH (n:L1)
        |RETURN *
        |
        |NEXT
        |
        |USE neo2
        |MATCH (m:L1)
        |RETURN *
        |
        |NEXT
        |
        |MATCH(o:L2)
        |RETURN n.x + m.x + o.x
        |""".stripMargin,
      """CALL (*) {
        |  USE `neo1`
        |  MATCH (n:L1)
        |  RETURN *
        |}
        |WITH *
        |CALL (*) {
        |  USE `neo2`
        |  MATCH (m:L1)
        |  RETURN *
        |}
        |WITH *
        |MATCH (o:L2)
        |RETURN (n.x + m.x) + o.x AS `n.x + m.x + o.x`""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("when single branch rewritten 16") {
    assertRewritten(
      CypherVersion.Cypher25,
      """USE neo1
        |MATCH (n:L1)
        |RETURN *
        |
        |NEXT
        |
        |USE neo2
        |MATCH (m:L1)
        |RETURN *
        |
        |NEXT
        |
        |USE neo1
        |MATCH(o:L2)
        |RETURN n.x + m.x + o.x
        |""".stripMargin,
      """CALL (*) {
        |  USE `neo1`
        |  MATCH (n:L1)
        |  RETURN *
        |}
        |WITH *
        |CALL (*) {
        |  USE `neo2`
        |  MATCH (m:L1)
        |  RETURN *
        |}
        |WITH *
        |CALL (*) {
        |  USE `neo1`
        |  MATCH (o:L2)
        |  RETURN (n.x + m.x) + o.x AS `  UNNAMED0`
        |}
        |RETURN `  UNNAMED0` AS `n.x + m.x + o.x`""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }
}
