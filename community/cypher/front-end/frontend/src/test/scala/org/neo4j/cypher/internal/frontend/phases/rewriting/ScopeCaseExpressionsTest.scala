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

import org.neo4j.cypher.internal.ast.SingleQuery
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.expressions.AnonymousScopeExpression
import org.neo4j.cypher.internal.frontend.helpers.TestState
import org.neo4j.cypher.internal.frontend.phases.Monitors
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.ScopeCaseExpressions
import org.neo4j.cypher.internal.frontend.phases.rewriting.cnf.TestContext
import org.neo4j.cypher.internal.rewriting.RewriteTest
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class ScopeCaseExpressionsTest extends CypherFunSuite with RewriteTest {

  test("scope simple case") {
    rewriteQuery(
      """
        |RETURN
        |  CASE rand()
        |    WHEN < 0.5 THEN a
        |    WHEN >= 0.4 THEN b
        |    ELSE c
        |  END AS r
        |""".stripMargin
    ) shouldBe SingleQuery(
      Seq(
        return_(
          aliasedReturnItem(
            scopedCaseExpression(
              varFor("  UNNAMED0"),
              function("rand"),
              Some(varFor("c")),
              lessThan(_, literal(0.5)) -> varFor("a"),
              greaterThanOrEqual(_, literal(0.4)) -> varFor("b")
            ),
            "r"
          )
        )
      )
    )(pos)
  }

  test("scope nested case") {
    val query =
      """RETURN
        |  CASE CASE rand(1) WHEN < 0.5 THEN a WHEN >= 0.5 THEN b END
        |    WHEN < CASE rand(2) WHEN < 0.4 THEN c WHEN >= 0.6 THEN d END THEN e
        |    ELSE f
        |  END AS r
        |""".stripMargin
    rewriteQuery(query).endoRewrite(bottomUp(Rewriter.lift {
      case e: AnonymousScopeExpression => e.deAnonymisedInnerExpression
    })) shouldBe parseForRewriting(query).endoRewrite(unScopedRewriter)
  }

  test("do not scope variables") {
    assertRewrite(
      "RETURN CASE x WHEN < 1 THEN a WHEN > 10 THEN b ELSE c END AS r",
      "RETURN CASE x WHEN < 1 THEN a WHEN > 10 THEN b ELSE c END AS r",
      additionalExpectedAstUpdates = _.endoRewrite(unScopedRewriter)
    )
  }

  test("do not scope parameters") {
    assertRewrite(
      "RETURN CASE $x WHEN < 1 THEN a WHEN > 10 THEN b ELSE c END AS r",
      "RETURN CASE $x WHEN < 1 THEN a WHEN > 10 THEN b ELSE c END AS r",
      additionalExpectedAstUpdates = _.endoRewrite(unScopedRewriter)
    )
  }

  test("do not scope literals") {
    assertRewrite(
      "RETURN CASE 1 WHEN < 1 THEN a WHEN > 10 THEN b ELSE c END AS r",
      "RETURN CASE 1 WHEN < 1 THEN a WHEN > 10 THEN b ELSE c END AS r",
      additionalExpectedAstUpdates = _.endoRewrite(unScopedRewriter)
    )
    assertRewrite(
      "RETURN CASE '1' WHEN < 1 THEN a WHEN > 10 THEN b ELSE c END AS r",
      "RETURN CASE '1' WHEN < 1 THEN a WHEN > 10 THEN b ELSE c END AS r",
      additionalExpectedAstUpdates = _.endoRewrite(unScopedRewriter)
    )
  }

  private def rewriteQuery(query: String): Statement = parseForRewriting(query).endoRewrite(rewriterUnderTest)

  override def rewriterUnderTest: Rewriter = ScopeCaseExpressions()
    .instance(TestState(None), new TestContext(mock[Monitors]))

  def unScopedRewriter: Rewriter = ScopeCaseExpressions(false)
    .instance(TestState(None), new TestContext(mock[Monitors]))
}
