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
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.ExpandWhen
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.SemanticAnalysis
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class ExpandWhenTest extends CypherFunSuite with RewritePhaseTest with AstConstructionTestSupport {

  override def rewriterPhaseUnderTest: Transformer[BaseContext, BaseState, BaseState] =
    SemanticAnalysis(Some(false)) andThen
      ExpandWhen

  override val phaseTestConfig = PhaseTestConfig(
    excludedVersions = Set(CypherVersion.Cypher5),
    semanticFeatures = Seq(SemanticFeature.DisableReworkedRewriters)
  )

  private def additionalExpectedUpdates = (expectedStatement: Statement) => {
    expectedStatement.endoRewrite(bottomUp(Rewriter.lift {
      // The original/rewritten statement will have AddedInRewriteGeneral,
      // the explicit WITH in the expected will have DefaultWith
      // so let's update that before checking the equality
      case w: With if w.withType == DefaultWith =>
        w.copy(withType = AddedInRewriteGeneral())(w.position)
    }))
  }

  private def additionalActualCleanup = (actualStatement: Statement) => {
    actualStatement.endoRewrite(bottomUp(Rewriter.lift({ case ret: Return =>
      // Removes set of excludes introduced by the rewriter.
      // These values are difficult to set on expected in this rewriter test framework.
      ret.copy(excludedNames = Set.empty)(ret.position)
    })))
  }

  test("when single branch rewritten") {
    assertRewritten(
      """WHEN true THEN RETURN 1 AS x""",
      """WITH *, CASE WHEN true THEN [true, false] ELSE [false, true] END AS `  UNNAMED0`
        |CALL (*) {
        |  WITH * WHERE `  UNNAMED0`[0]
        |  RETURN 1 AS x
        |}
        |RETURN x AS x""".stripMargin,
      additionalExpectedAstUpdates = additionalExpectedUpdates,
      additionalActualAstCleanup = additionalActualCleanup
    )
  }

  test("when unit single branch rewritten") {
    assertRewritten(
      """WHEN true THEN CREATE ()""",
      """WITH *, CASE WHEN true THEN [true, false] ELSE [false, true] END AS `  UNNAMED0`
        |CALL (*) {
        |  WITH * WHERE `  UNNAMED0`[0]
        |  CREATE ()
        |}
        |FINISH""".stripMargin,
      additionalExpectedAstUpdates = additionalExpectedUpdates,
      additionalActualAstCleanup = additionalActualCleanup
    )
  }

  test("when in subquery and return all rewritten") {
    assertRewritten(
      """
        CALL (*) {
             WHEN true THEN
               CALL (*) {
                 RETURN 4 AS `x`
               }
               RETURN *
         }
         RETURN *""".stripMargin,
      """CALL (*) {
        |  WITH *, CASE
        |  WHEN true THEN [true, false]
        |  ELSE [false, true]
        |END AS `  UNNAMED0`
        |  CALL (*) {
        |    WITH *
        |      WHERE `  UNNAMED0`[0]
        |    CALL (*) {
        |      RETURN 4 AS x
        |    }
        |    RETURN *
        |  }
        |  RETURN x AS x
        |}
        |RETURN *""".stripMargin,
      additionalExpectedAstUpdates = additionalExpectedUpdates,
      additionalActualAstCleanup = additionalActualCleanup
    )
  }

  test("when single branch with else rewritten") {
    assertRewritten(
      """
        |   WHEN false THEN RETURN 1 AS x
        |   ELSE RETURN 2 AS x
        |""".stripMargin,
      """WITH *, CASE
        |  WHEN false THEN [true, false]
        |  ELSE [false, true]
        |END AS `  UNNAMED0`
        |CALL (*) {
        |  WITH *
        |    WHERE `  UNNAMED0`[0]
        |  RETURN 1 AS x
        |  UNION ALL
        |  WITH *
        |    WHERE `  UNNAMED0`[1]
        |  RETURN 2 AS x
        |}
        |RETURN x AS x""".stripMargin,
      additionalExpectedAstUpdates = additionalExpectedUpdates,
      additionalActualAstCleanup = additionalActualCleanup
    )
  }

  test("when with simple subquery expression predicate") {
    assertRewritten(
      """
        |WHEN false THEN RETURN 1 AS x
        |WHEN EXISTS { RETURN 1 } THEN RETURN 2 AS x
        |ELSE RETURN 3 AS x
        |""".stripMargin,
      """
        |WITH *, CASE
        |  WHEN false THEN [true, false, false]
        |  WHEN EXISTS { RETURN 1 AS `1` } THEN [false, true, false]
        |  ELSE [false, false, true]
        |END AS `  UNNAMED0`
        |CALL (*) {
        |  WITH *
        |    WHERE `  UNNAMED0`[0]
        |  RETURN 1 AS x
        |  UNION ALL
        |  WITH *
        |    WHERE `  UNNAMED0`[1]
        |  RETURN 2 AS x
        |  UNION ALL
        |  WITH *
        |    WHERE `  UNNAMED0`[2]
        |  RETURN 3 AS x
        |}
        |RETURN x AS x
        |""".stripMargin,
      additionalExpectedAstUpdates = additionalExpectedUpdates,
      additionalActualAstCleanup = additionalActualCleanup
    )
  }

  test("when multiple branches with else rewritten") {
    assertRewritten(
      """
        |   WHEN false THEN RETURN 1 AS x
        |   WHEN false THEN RETURN 2 AS x
        |   WHEN false THEN RETURN 3 AS x
        |   WHEN false THEN RETURN 4 AS x
        |   ELSE RETURN 5 AS x
        |""".stripMargin,
      """WITH *, CASE
        |  WHEN false THEN [true, false, false, false, false]
        |  WHEN false THEN [false, true, false, false, false]
        |  WHEN false THEN [false, false, true, false, false]
        |  WHEN false THEN [false, false, false, true, false]
        |  ELSE [false, false, false, false, true]
        |END AS `  UNNAMED0`
        |CALL (*) {
        |  WITH *
        |    WHERE `  UNNAMED0`[0]
        |  RETURN 1 AS x
        |  UNION ALL
        |  WITH *
        |    WHERE `  UNNAMED0`[1]
        |  RETURN 2 AS x
        |  UNION ALL
        |  WITH *
        |    WHERE `  UNNAMED0`[2]
        |  RETURN 3 AS x
        |  UNION ALL
        |  WITH *
        |    WHERE `  UNNAMED0`[3]
        |  RETURN 4 AS x
        |  UNION ALL
        |  WITH *
        |    WHERE `  UNNAMED0`[4]
        |  RETURN 5 AS x
        |}
        |RETURN x AS x""".stripMargin,
      additionalExpectedAstUpdates = additionalExpectedUpdates,
      additionalActualAstCleanup = additionalActualCleanup
    )
  }

  test("when enclosed in union in subquery") {
    assertRewritten(
      """
        |   MATCH (n)
        |   CALL (*) {
        |      {
        |         WHEN false THEN RETURN 1 AS x
        |         ELSE RETURN n.prop AS x
        |      }
        |      UNION
        |      {
        |         WHEN false THEN RETURN 1 AS x
        |         ELSE RETURN n.prop AS x
        |      }
        |   }
        |   RETURN *
        |""".stripMargin,
      """MATCH (n)
        |CALL (*) {
        |  {
        |    WITH *, CASE
        |  WHEN false THEN [true, false]
        |  ELSE [false, true]
        |END AS `  UNNAMED0`
        |    CALL (*) {
        |      WITH *
        |        WHERE `  UNNAMED0`[0]
        |      RETURN 1 AS x
        |      UNION ALL
        |      WITH *
        |        WHERE `  UNNAMED0`[1]
        |      RETURN n.prop AS x
        |    }
        |    RETURN x AS x
        |  }
        |  UNION
        |  {
        |    WITH *, CASE
        |  WHEN false THEN [true, false]
        |  ELSE [false, true]
        |END AS `  UNNAMED1`
        |    CALL (*) {
        |      WITH *
        |        WHERE `  UNNAMED1`[0]
        |      RETURN 1 AS x
        |      UNION ALL
        |      WITH *
        |        WHERE `  UNNAMED1`[1]
        |      RETURN n.prop AS x
        |    }
        |    RETURN x AS x
        |  }
        |}
        |RETURN *""".stripMargin,
      additionalExpectedAstUpdates = additionalExpectedUpdates,
      additionalActualAstCleanup = additionalActualCleanup
    )
  }

  test("when in subquery rewritten") {
    assertRewritten(
      """
        |   LET x = 1
        |   CALL (x) {
        |      WHEN x < 0 THEN RETURN 1 + x AS y
        |      WHEN x < 1 THEN RETURN 2 + x AS y
        |      ELSE RETURN 3 + x AS y
        |   }
        |   RETURN y
        |""".stripMargin,
      """LET x = 1
        |CALL (x) {
        |  WITH *, CASE
        |  WHEN x < 0 THEN [true, false, false]
        |  WHEN x < 1 THEN [false, true, false]
        |  ELSE [false, false, true]
        |END AS `  UNNAMED0`
        |  CALL (*) {
        |    WITH *
        |      WHERE `  UNNAMED0`[0]
        |    RETURN 1 + x AS y
        |    UNION ALL
        |    WITH *
        |      WHERE `  UNNAMED0`[1]
        |    RETURN 2 + x AS y
        |    UNION ALL
        |    WITH *
        |      WHERE `  UNNAMED0`[2]
        |    RETURN 3 + x AS y
        |  }
        |  RETURN y AS y
        |}
        |RETURN y AS y""".stripMargin,
      additionalExpectedAstUpdates = additionalExpectedUpdates,
      additionalActualAstCleanup = additionalActualCleanup
    )
  }

  test("when in subquery expression rewritten") {
    assertRewritten(
      """
        |   LET x = 1, b = 2
        |   RETURN EXISTS {
        |      WHEN x < 0 THEN RETURN 1 + x AS y
        |      WHEN b < 1 THEN RETURN 2 AS y
        |      ELSE RETURN 3 + x AS y
        |   } AS res
        |""".stripMargin,
      """LET x = 1, b = 2
        |RETURN EXISTS { WITH *, CASE
        |  WHEN x < 0 THEN [true, false, false]
        |  WHEN b < 1 THEN [false, true, false]
        |  ELSE [false, false, true]
        |END AS `  UNNAMED0`
        |CALL (*) {
        |  WITH *
        |    WHERE `  UNNAMED0`[0]
        |  RETURN 1 + x AS y
        |  UNION ALL
        |  WITH *
        |    WHERE `  UNNAMED0`[1]
        |  RETURN 2 AS y
        |  UNION ALL
        |  WITH *
        |    WHERE `  UNNAMED0`[2]
        |  RETURN 3 + x AS y
        |}
        |RETURN y AS y } AS res""".stripMargin,
      additionalExpectedAstUpdates = additionalExpectedUpdates,
      additionalActualAstCleanup = additionalActualCleanup
    )
  }

  test("when example with params rewritten") {
    assertRewritten(
      """
        |   WHEN $param < 1 THEN RETURN 1 AS res
        |   WHEN $param > 1 THEN RETURN 2 AS res
        |   ELSE RETURN 3 AS res
        |""".stripMargin,
      """
        |   WITH *, CASE WHEN $param < 1 THEN [true, false, false] WHEN $param > 1 THEN [false, true, false] ELSE [false, false, true] END AS `  UNNAMED0`
        |   CALL (*) {
        |      WITH * WHERE `  UNNAMED0`[0]
        |      RETURN 1 AS res
        |      UNION ALL
        |      WITH * WHERE `  UNNAMED0`[1]
        |      RETURN 2 AS res
        |      UNION ALL
        |      WITH * WHERE `  UNNAMED0`[2]
        |      RETURN 3 AS res
        |   }
        |   RETURN res
        |""".stripMargin,
      additionalExpectedAstUpdates = additionalExpectedUpdates,
      additionalActualAstCleanup = additionalActualCleanup
    )
  }

  test("chained subquery when") {
    assertRewritten(
      """CALL () {
        |  WHEN true THEN MATCH (n) RETURN n
        |}
        |CALL (n) {
        |  WHEN n.age > 40 THEN RETURN "old" AS x
        |  ELSE RETURN "young" AS x
        |}
        |RETURN x
        |""".stripMargin,
      """CALL () {
        |  WITH *, CASE
        |  WHEN true THEN [true, false]
        |  ELSE [false, true]
        |END AS `  UNNAMED0`
        |  CALL (*) {
        |    WITH *
        |      WHERE `  UNNAMED0`[0]
        |    MATCH (n)
        |    RETURN n AS n
        |  }
        |  RETURN n AS n
        |}
        |CALL (n) {
        |  WITH *, CASE
        |  WHEN n.age > 40 THEN [true, false]
        |  ELSE [false, true]
        |END AS `  UNNAMED1`
        |  CALL (*) {
        |    WITH *
        |      WHERE `  UNNAMED1`[0]
        |    RETURN "old" AS x
        |    UNION ALL
        |    WITH *
        |      WHERE `  UNNAMED1`[1]
        |    RETURN "young" AS x
        |  }
        |  RETURN x AS x
        |}
        |RETURN x AS x""".stripMargin,
      additionalExpectedAstUpdates = additionalExpectedUpdates,
      additionalActualAstCleanup = additionalActualCleanup
    )
  }
}
