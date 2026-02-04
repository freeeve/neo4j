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
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.DisableReworkedRewriters
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.ExpandNext
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.SemanticAnalysis
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class ExpandNextTest extends CypherFunSuite with RewritePhaseTest with AstConstructionTestSupport {

  override def rewriterPhaseUnderTest: Transformer[BaseContext, BaseState, BaseState] =
    SemanticAnalysis(Some(false)) andThen
      ExpandNext

  override val phaseTestConfig = PhaseTestConfig(
    excludedVersions = Set(CypherVersion.Cypher5),
    semanticFeatures = Seq(SemanticFeature.UseAsMultipleGraphsSelector, DisableReworkedRewriters)
  )

  private def withUpdate(exclude: Set[String] = Set.empty) = (expectedStatement: Statement) => {
    val transformed = expectedStatement.endoRewrite(bottomUp(Rewriter.lift {
      // The original/rewritten statement will have AddedInRewriteGeneral,
      // the explicit WITH in the expected will have DefaultWith
      // so let's update that before checking the equality
      case w: With =>
        w.copy(withType = AddedInRewriteGeneral())(w.position)
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

  test("NEXT query rewritten 1") {
    assertRewritten(
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
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }

  test("NEXT query rewritten 2") {
    assertRewritten(
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
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }

  test("NEXT query rewritten 3") {
    assertRewritten(
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
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }

  test("NEXT query rewritten 4") {
    assertRewritten(
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
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }

  test("NEXT query rewritten 5") {
    assertRewritten(
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
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }

  test("NEXT query rewritten 6") {
    assertRewritten(
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
        |WITH count(*) AS `  UNNAMED1`, collect([`  UNNAMED0`]) AS `  UNNAMED0`
        |CALL (*) {
        |  UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED3`
        |  WITH (`  UNNAMED0`[`  UNNAMED3`])[0] AS a
        |  RETURN a + 1 AS `  UNNAMED2`
        |  UNION
        |  UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED4`
        |  WITH (`  UNNAMED0`[`  UNNAMED4`])[0] AS a
        |  RETURN a + 2 AS `  UNNAMED2`
        |}
        |RETURN `  UNNAMED2` AS b""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("NEXT query rewritten 7") {
    assertRewritten(
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
      """LET x = 1, y = 2
        |WITH x AS `  UNNAMED0`
        |WITH count(*) AS `  UNNAMED1`, collect([`  UNNAMED0`]) AS `  UNNAMED0`
        |CALL (*) {
        |  UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED4`
        |  WITH (`  UNNAMED0`[`  UNNAMED4`])[0] AS x
        |  RETURN 1 + x AS `  UNNAMED2`
        |  UNION
        |  UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED5`
        |  WITH (`  UNNAMED0`[`  UNNAMED5`])[0] AS x
        |  LET y = 3
        |  RETURN 2 + y AS `  UNNAMED2`
        |}
        |WITH count(*) AS `  UNNAMED3`, collect([`  UNNAMED2`]) AS `  UNNAMED2`
        |CALL (*) {
        |  UNWIND range(0, `  UNNAMED3` - 1) AS `  UNNAMED7`
        |  WITH (`  UNNAMED2`[`  UNNAMED7`])[0] AS a
        |  RETURN a + 1 AS `  UNNAMED6`
        |  UNION
        |  UNWIND range(0, `  UNNAMED3` - 1) AS `  UNNAMED8`
        |  WITH (`  UNNAMED2`[`  UNNAMED8`])[0] AS a
        |  RETURN a + 2 AS `  UNNAMED6`
        |}
        |RETURN `  UNNAMED6` AS b""".stripMargin,
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }

  test("NEXT query rewritten 8") {
    assertRewritten(
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
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }

  test("NEXT query rewritten 9") {
    assertRewritten(
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
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }

  test("NEXT query rewritten 10") {
    assertRewritten(
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
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }

  test("NEXT query rewritten 11") {
    assertRewritten(
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
        |WITH x AS `  UNNAMED0`, y AS `  UNNAMED1`
        |WITH count(*) AS `  UNNAMED2`, collect([`  UNNAMED0`]) AS `  UNNAMED0`, collect([`  UNNAMED1`]) AS `  UNNAMED1`
        |CALL (*) {
        |  UNWIND range(0, `  UNNAMED2` - 1) AS `  UNNAMED5`
        |  WITH (`  UNNAMED0`[`  UNNAMED5`])[0] AS x, (`  UNNAMED1`[`  UNNAMED5`])[0] AS y
        |  RETURN 1 + x AS `  UNNAMED3`
        |  UNION
        |  UNWIND range(0, `  UNNAMED2` - 1) AS `  UNNAMED6`
        |  WITH (`  UNNAMED0`[`  UNNAMED6`])[0] AS x, (`  UNNAMED1`[`  UNNAMED6`])[0] AS y
        |  RETURN 2 + y AS `  UNNAMED3`
        |}
        |WITH count(*) AS `  UNNAMED4`, collect([`  UNNAMED3`]) AS `  UNNAMED3`
        |CALL (*) {
        |  UNWIND range(0, `  UNNAMED4` - 1) AS `  UNNAMED8`
        |  WITH (`  UNNAMED3`[`  UNNAMED8`])[0] AS a
        |  RETURN a + 1 AS `  UNNAMED7`
        |  UNION
        |  UNWIND range(0, `  UNNAMED4` - 1) AS `  UNNAMED9`
        |  WITH (`  UNNAMED3`[`  UNNAMED9`])[0] AS a
        |  RETURN a + 2 AS `  UNNAMED7`
        |}
        |WITH `  UNNAMED7` AS b
        |RETURN *""".stripMargin,
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }

  test("NEXT query rewritten 12") {
    assertRewritten(
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
        |WITH x AS `  UNNAMED0`, y AS `  UNNAMED1`
        |WITH count(*) AS `  UNNAMED2`, collect([`  UNNAMED0`]) AS `  UNNAMED0`, collect([`  UNNAMED1`]) AS `  UNNAMED1`
        |CALL (*) {
        |  UNWIND range(0, `  UNNAMED2` - 1) AS `  UNNAMED5`
        |  WITH (`  UNNAMED0`[`  UNNAMED5`])[0] AS x, (`  UNNAMED1`[`  UNNAMED5`])[0] AS y
        |  RETURN 1 + x AS `  UNNAMED3`
        |  UNION
        |  UNWIND range(0, `  UNNAMED2` - 1) AS `  UNNAMED6`
        |  WITH (`  UNNAMED0`[`  UNNAMED6`])[0] AS x, (`  UNNAMED1`[`  UNNAMED6`])[0] AS y
        |  RETURN 2 + y AS `  UNNAMED3`
        |}
        |WITH count(*) AS `  UNNAMED4`, collect([`  UNNAMED3`]) AS `  UNNAMED3`
        |CALL (*) {
        |  UNWIND range(0, `  UNNAMED4` - 1) AS `  UNNAMED9`
        |  WITH (`  UNNAMED3`[`  UNNAMED9`])[0] AS a
        |  LET b = a + 1
        |  RETURN a AS `  UNNAMED7`, b AS `  UNNAMED8`
        |  UNION
        |  UNWIND range(0, `  UNNAMED4` - 1) AS `  UNNAMED10`
        |  WITH (`  UNNAMED3`[`  UNNAMED10`])[0] AS a
        |  LET b = a + 2
        |  RETURN a AS `  UNNAMED7`, b AS `  UNNAMED8`
        |}
        |WITH `  UNNAMED7` AS a, `  UNNAMED8` AS b
        |RETURN *""".stripMargin,
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }

  test("NEXT query rewritten 13") {
    assertRewritten(
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
        |WITH a AS `  UNNAMED0`
        |WITH count(*) AS `  UNNAMED1`, collect([`  UNNAMED0`]) AS `  UNNAMED0`
        |CALL (*) {
        |  UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED4`
        |  WITH (`  UNNAMED0`[`  UNNAMED4`])[0] AS a
        |  LET b = a + 1
        |  RETURN a AS `  UNNAMED2`, b AS `  UNNAMED3`
        |  UNION
        |  UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED5`
        |  WITH (`  UNNAMED0`[`  UNNAMED5`])[0] AS a
        |  LET b = a + 2
        |  RETURN a AS `  UNNAMED2`, b AS `  UNNAMED3`
        |}
        |WITH `  UNNAMED2` AS a, `  UNNAMED3` AS b
        |LET c = b + 1
        |RETURN a AS a, b AS b, c AS c""".stripMargin,
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }

  test("NEXT query rewritten 14") {
    assertRewritten(
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
        |  RETURN n AS `  UNNAMED0`
        |}
        |WITH `  UNNAMED0` AS n
        |MATCH (m:L1)
        |WITH *
        |MATCH (o:L2)
        |RETURN (n.x + m.x) + o.x AS `n.x + m.x + o.x`""".stripMargin,
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }

  test("NEXT query rewritten 15") {
    assertRewritten(
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
        |  RETURN n AS `  UNNAMED0`
        |}
        |WITH `  UNNAMED0` AS n
        |CALL (*) {
        |  USE `neo2`
        |  MATCH (m:L1)
        |  RETURN n AS `  UNNAMED1`, m AS `  UNNAMED2`
        |}
        |WITH `  UNNAMED1` AS n, `  UNNAMED2` AS m
        |MATCH (o:L2)
        |RETURN (n.x + m.x) + o.x AS `n.x + m.x + o.x`""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("NEXT query rewritten 16") {
    assertRewritten(
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
        |  RETURN n AS `  UNNAMED0`
        |}
        |WITH `  UNNAMED0` AS n
        |CALL (*) {
        |  USE `neo2`
        |  MATCH (m:L1)
        |  RETURN n AS `  UNNAMED1`, m AS `  UNNAMED2`
        |}
        |WITH `  UNNAMED1` AS n, `  UNNAMED2` AS m
        |CALL (*) {
        |  USE `neo1`
        |  MATCH (o:L2)
        |  RETURN (n.x + m.x) + o.x AS `  UNNAMED3`
        |}
        |RETURN `  UNNAMED3` AS `n.x + m.x + o.x`""".stripMargin,
      additionalExpectedAstUpdates = withUpdate()
    )
  }

  test("NEXT query with aggregation rewritten 1") {
    assertRewritten(
      """
      UNWIND [1,2,3] AS a
      RETURN a

      NEXT

      {
        UNWIND [1,2,3] AS x
        RETURN COUNT(x) AS x
        UNION ALL
        UNWIND [1,2,3] AS x
        RETURN SUM(x) AS x

        NEXT

        RETURN x
      }
      """.stripMargin,
      """UNWIND [1, 2, 3] AS a
        |WITH a AS `  UNNAMED0`
        |WITH count(*) AS `  UNNAMED1`, collect([`  UNNAMED0`]) AS `  UNNAMED0`
        |CALL (*) {
        |  {
        |    CALL (*) {
        |      UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED3`
        |      WITH (`  UNNAMED0`[`  UNNAMED3`])[0] AS a
        |      UNWIND [1, 2, 3] AS x
        |      RETURN COUNT(x) AS `  UNNAMED5`
        |      UNION ALL
        |      UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED4`
        |      WITH (`  UNNAMED0`[`  UNNAMED4`])[0] AS a
        |      UNWIND [1, 2, 3] AS x
        |      RETURN SUM(x) AS `  UNNAMED5`
        |    }
        |    WITH `  UNNAMED5` AS x
        |    RETURN x AS `  UNNAMED2`
        |  }
        |}
        |RETURN `  UNNAMED2` AS x""".stripMargin,
      additionalExpectedAstUpdates = withUpdate(),
      withUpdate()
    )
  }

  test("NEXT query with aggregation rewritten 2") {
    assertRewritten(
      """
      UNWIND [1,2,3] AS x
      RETURN x

      NEXT

      RETURN COUNT(x) AS x
      UNION ALL
      RETURN SUM(x) AS x

      NEXT

      RETURN COUNT(x) AS x
      UNION ALL
      RETURN SUM(x) AS x

      NEXT

      RETURN x
      """.stripMargin,
      """UNWIND [1, 2, 3] AS x
        |WITH x AS `  UNNAMED0`
        |WITH count(*) AS `  UNNAMED1`, collect([`  UNNAMED0`]) AS `  UNNAMED0`
        |CALL (*) {
        |  UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED4`
        |  WITH (`  UNNAMED0`[`  UNNAMED4`])[0] AS x
        |  RETURN COUNT(x) AS `  UNNAMED2`
        |  UNION ALL
        |  UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED5`
        |  WITH (`  UNNAMED0`[`  UNNAMED5`])[0] AS x
        |  RETURN SUM(x) AS `  UNNAMED2`
        |}
        |WITH count(*) AS `  UNNAMED3`, collect([`  UNNAMED2`]) AS `  UNNAMED2`
        |CALL (*) {
        |  UNWIND range(0, `  UNNAMED3` - 1) AS `  UNNAMED7`
        |  WITH (`  UNNAMED2`[`  UNNAMED7`])[0] AS x
        |  RETURN COUNT(x) AS `  UNNAMED6`
        |  UNION ALL
        |  UNWIND range(0, `  UNNAMED3` - 1) AS `  UNNAMED8`
        |  WITH (`  UNNAMED2`[`  UNNAMED8`])[0] AS x
        |  RETURN SUM(x) AS `  UNNAMED6`
        |}
        |WITH `  UNNAMED6` AS x
        |RETURN x AS x""".stripMargin,
      additionalExpectedAstUpdates = withUpdate(),
      withUpdate()
    )
  }

  test("Should not wrap on inner aggregation in subquery expression") {
    assertRewritten(
      """
      UNWIND [1,2,3] AS x
      RETURN x

      NEXT

      RETURN x
      UNION ALL
      RETURN COUNT{ RETURN sum(x) } AS x""".stripMargin,
      """UNWIND [1, 2, 3] AS x
        |WITH x AS `  UNNAMED0`
        |WITH count(*) AS `  UNNAMED1`, collect([`  UNNAMED0`]) AS `  UNNAMED0`
        |CALL (*) {
        |  UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED3`
        |  WITH (`  UNNAMED0`[`  UNNAMED3`])[0] AS x
        |  RETURN x AS `  UNNAMED2`
        |  UNION ALL
        |  UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED4`
        |  WITH (`  UNNAMED0`[`  UNNAMED4`])[0] AS x
        |  RETURN COUNT { RETURN sum(x) AS `sum(x)` } AS `  UNNAMED2`
        |}
        |RETURN `  UNNAMED2` AS x""".stripMargin,
      additionalExpectedAstUpdates = withUpdate(),
      withUpdate()
    )
  }

  test("NEXT query rewritten with USE in UNION") {
    assertRewritten(
      """USE mega
        |MATCH (p0:Person) ORDER BY p0.name ASC
        |RETURN head(collect(p0.name)) AS name0 ORDER BY name0
        |
        |NEXT
        |
        |USE mega
        |MATCH (p1:Person) WHERE name0 = p1.name
        |RETURN name0, p1.name
        |UNION
        |USE mega
        |MATCH (p1:Person) WHERE name0 = p1.name
        |RETURN name0, p1.name""".stripMargin,
      """CALL (*) {
        |  USE `mega`
        |  MATCH (p0:Person)
        |  ORDER BY p0.name ASCENDING
        |  RETURN head(collect(p0.name)) AS `  UNNAMED0`
        |    ORDER BY `  UNNAMED0` ASCENDING
        |}
        |WITH count(*) AS `  UNNAMED1`, collect([`  UNNAMED0`]) AS `  UNNAMED0`
        |CALL (*) {
        |  USE `mega`
        |  UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED4`
        |  WITH (`  UNNAMED0`[`  UNNAMED4`])[0] AS name0
        |  MATCH (p1:Person)
        |    WHERE name0 = p1.name
        |  RETURN name0 AS `  UNNAMED2`, p1.name AS `  UNNAMED3`
        |  UNION
        |  USE `mega`
        |  UNWIND range(0, `  UNNAMED1` - 1) AS `  UNNAMED5`
        |  WITH (`  UNNAMED0`[`  UNNAMED5`])[0] AS name0
        |  MATCH (p1:Person)
        |    WHERE name0 = p1.name
        |  RETURN name0 AS `  UNNAMED2`, p1.name AS `  UNNAMED3`
        |}
        |RETURN `  UNNAMED2` AS name0, `  UNNAMED3` AS `p1.name`""".stripMargin,
      additionalExpectedAstUpdates = withUpdate(),
      additionalActualAstCleanup = withUpdate()
    )
  }
}
