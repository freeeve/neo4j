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
package org.neo4j.cypher.internal.ast.factory.query

import org.neo4j.cypher.internal.ast.AdditiveProjection
import org.neo4j.cypher.internal.ast.AliasedReturnItem
import org.neo4j.cypher.internal.ast.AscSortItem
import org.neo4j.cypher.internal.ast.ExistsExpression
import org.neo4j.cypher.internal.ast.FreeProjection
import org.neo4j.cypher.internal.ast.OrderBy
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.ReturnItems
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.UnaliasedReturnItem
import org.neo4j.cypher.internal.ast.Where
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase

class GroupByClauseParserTest extends AstParsingTestBase {

  // ─────────────────────────────────────────────────────────────────────
  // Basic forms — single exact error for Cypher 5
  // ─────────────────────────────────────────────────────────────────────

  test("RETURN 1 AS x GROUP BY x") {
    parsesIn[Statement] {
      case Cypher5 =>
        _.withSyntaxError(
          """Invalid input 'GROUP': expected ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 15 (offset: 14))
            |"RETURN 1 AS x GROUP BY x"
            |               ^""".stripMargin
        )
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = false,
              ReturnItems(FreeProjection, Seq(AliasedReturnItem(literalInt(1), varFor("x"))(pos)))(pos),
              groupBy = Some(groupBy(varFor("x"))),
              orderBy = None,
              skip = None,
              limit = None
            )(pos)
          )
        )
    }
  }

  test("RETURN a GROUP BY ALL, b") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = false,
              ReturnItems(FreeProjection, Seq(UnaliasedReturnItem(varFor("a"), "a")(pos)))(pos),
              groupBy = Some(groupBy(varFor("ALL"), varFor("b"))),
              orderBy = None,
              skip = None,
              limit = None
            )(pos)
          )
        )
    }
  }

  test("RETURN count(*) AS n GROUP BY ()") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = false,
              ReturnItems(FreeProjection, Seq(AliasedReturnItem(countStar(), varFor("n"))(pos)))(pos),
              groupBy = Some(groupByNone),
              orderBy = None,
              skip = None,
              limit = None
            )(pos)
          )
        )
    }
  }

  test("RETURN count(*) AS n GROUP BY ALL") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = false,
              ReturnItems(FreeProjection, Seq(AliasedReturnItem(countStar(), varFor("n"))(pos)))(pos),
              groupBy = Some(groupByAll),
              orderBy = None,
              skip = None,
              limit = None
            )(pos)
          )
        )
    }
  }

  test("RETURN a, count(*) AS n GROUP BY a") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = false,
              ReturnItems(
                FreeProjection,
                Seq(
                  UnaliasedReturnItem(varFor("a"), "a")(pos),
                  AliasedReturnItem(countStar(), varFor("n"))(pos)
                )
              )(pos),
              groupBy = Some(groupBy(varFor("a"))),
              orderBy = None,
              skip = None,
              limit = None
            )(pos)
          )
        )
    }
  }

  test("RETURN a.p, count(*) AS n GROUP BY a.p") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = false,
              ReturnItems(
                FreeProjection,
                Seq(
                  UnaliasedReturnItem(prop("a", "p"), "a.p")(pos),
                  AliasedReturnItem(countStar(), varFor("n"))(pos)
                )
              )(pos),
              groupBy = Some(groupBy(prop("a", "p"))),
              orderBy = None,
              skip = None,
              limit = None
            )(pos)
          )
        )
    }
  }

  test("RETURN count(*) AS n GROUP BY a.p + 1") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = false,
              ReturnItems(FreeProjection, Seq(AliasedReturnItem(countStar(), varFor("n"))(pos)))(pos),
              groupBy = Some(groupBy(add(prop("a", "p"), literalInt(1)))),
              orderBy = None,
              skip = None,
              limit = None
            )(pos)
          )
        )
    }
  }

  test("RETURN a, b, count(*) AS n GROUP BY a, b") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = false,
              ReturnItems(
                FreeProjection,
                Seq(
                  UnaliasedReturnItem(varFor("a"), "a")(pos),
                  UnaliasedReturnItem(varFor("b"), "b")(pos),
                  AliasedReturnItem(countStar(), varFor("n"))(pos)
                )
              )(pos),
              groupBy = Some(groupBy(varFor("a"), varFor("b"))),
              orderBy = None,
              skip = None,
              limit = None
            )(pos)
          )
        )
    }
  }

  test("MATCH (a) RETURN *, count(*) AS n GROUP BY ALL") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            match_(nodePat(Some("a"))),
            Return(
              distinct = false,
              ReturnItems(AdditiveProjection, Seq(AliasedReturnItem(countStar(), varFor("n"))(pos)))(pos),
              groupBy = Some(groupByAll),
              orderBy = None,
              skip = None,
              limit = None
            )(pos)
          )
        )
    }
  }

  // ─────────────────────────────────────────────────────────────────────
  // Combinations with ORDER BY / SKIP / LIMIT / OFFSET / DISTINCT
  // ─────────────────────────────────────────────────────────────────────

  test("RETURN a, count(*) AS n GROUP BY a ORDER BY a") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = false,
              ReturnItems(
                FreeProjection,
                Seq(
                  UnaliasedReturnItem(varFor("a"), "a")(pos),
                  AliasedReturnItem(countStar(), varFor("n"))(pos)
                )
              )(pos),
              groupBy = Some(groupBy(varFor("a"))),
              orderBy = Some(OrderBy(List(AscSortItem(varFor("a"))(pos)))(pos)),
              skip = None,
              limit = None
            )(pos)
          )
        )
    }
  }

  test("RETURN a, count(*) AS n GROUP BY a ORDER BY a SKIP 1 LIMIT 1") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = false,
              ReturnItems(
                FreeProjection,
                Seq(
                  UnaliasedReturnItem(varFor("a"), "a")(pos),
                  AliasedReturnItem(countStar(), varFor("n"))(pos)
                )
              )(pos),
              groupBy = Some(groupBy(varFor("a"))),
              orderBy = Some(OrderBy(List(AscSortItem(varFor("a"))(pos)))(pos)),
              skip = Some(skip(1)),
              limit = Some(limit(1))
            )(pos)
          )
        )
    }
  }

  test("RETURN a, count(*) AS n GROUP BY a OFFSET 1 LIMIT 1") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = false,
              ReturnItems(
                FreeProjection,
                Seq(
                  UnaliasedReturnItem(varFor("a"), "a")(pos),
                  AliasedReturnItem(countStar(), varFor("n"))(pos)
                )
              )(pos),
              groupBy = Some(groupBy(varFor("a"))),
              orderBy = None,
              skip = Some(offset(1)),
              limit = Some(limit(1))
            )(pos)
          )
        )
    }
  }

  test("RETURN DISTINCT a, count(*) AS n GROUP BY a") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = true,
              ReturnItems(
                FreeProjection,
                Seq(
                  UnaliasedReturnItem(varFor("a"), "a")(pos),
                  AliasedReturnItem(countStar(), varFor("n"))(pos)
                )
              )(pos),
              groupBy = Some(groupBy(varFor("a"))),
              orderBy = None,
              skip = None,
              limit = None
            )(pos)
          )
        )
    }
  }

  // ─────────────────────────────────────────────────────────────────────
  // GROUP BY in WITH (with WHERE)
  // ─────────────────────────────────────────────────────────────────────

  test("WITH a, count(*) AS n GROUP BY a RETURN *") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            With(
              distinct = false,
              ReturnItems(
                FreeProjection,
                Seq(
                  UnaliasedReturnItem(varFor("a"), "a")(pos),
                  AliasedReturnItem(countStar(), varFor("n"))(pos)
                )
              )(pos),
              groupBy = Some(groupBy(varFor("a"))),
              orderBy = None,
              skip = None,
              limit = None,
              where = None
            )(pos),
            returnAll
          )
        )
    }
  }

  test("WITH a, count(*) AS n GROUP BY a WHERE n > 1 RETURN *") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            With(
              distinct = false,
              ReturnItems(
                FreeProjection,
                Seq(
                  UnaliasedReturnItem(varFor("a"), "a")(pos),
                  AliasedReturnItem(countStar(), varFor("n"))(pos)
                )
              )(pos),
              groupBy = Some(groupBy(varFor("a"))),
              orderBy = None,
              skip = None,
              limit = None,
              where = Some(Where(greaterThan(varFor("n"), literalInt(1)))(pos))
            )(pos),
            returnAll
          )
        )
    }
  }

  test("WITH count(*) AS n GROUP BY () WHERE n > 1 RETURN *") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            With(
              distinct = false,
              ReturnItems(FreeProjection, Seq(AliasedReturnItem(countStar(), varFor("n"))(pos)))(pos),
              groupBy = Some(groupByNone),
              orderBy = None,
              skip = None,
              limit = None,
              where = Some(Where(greaterThan(varFor("n"), literalInt(1)))(pos))
            )(pos),
            returnAll
          )
        )
    }
  }

  test("WITH a, count(*) AS n GROUP BY ALL ORDER BY a WHERE a > 0 RETURN *") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            With(
              distinct = false,
              ReturnItems(
                FreeProjection,
                Seq(
                  UnaliasedReturnItem(varFor("a"), "a")(pos),
                  AliasedReturnItem(countStar(), varFor("n"))(pos)
                )
              )(pos),
              groupBy = Some(groupByAll),
              orderBy = Some(OrderBy(List(AscSortItem(varFor("a"))(pos)))(pos)),
              skip = None,
              limit = None,
              where = Some(Where(greaterThan(varFor("a"), literalInt(0)))(pos))
            )(pos),
            returnAll
          )
        )
    }
  }

  // ─────────────────────────────────────────────────────────────────────
  // Compositional contexts: CALL subquery / EXISTS / NEXT / nested WITH
  // ─────────────────────────────────────────────────────────────────────

  test("GROUP BY inside CALL subquery") {
    val query =
      """
        |MATCH (n)
        |CALL (n) {
        |   RETURN n AS x, count(*) AS c GROUP BY x
        |}
        |RETURN *
        |""".stripMargin

    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            match_(nodePat(Some("n"))),
            scopeClauseSubqueryCall(
              false,
              Seq(varFor("n")),
              Return(
                distinct = false,
                ReturnItems(
                  FreeProjection,
                  Seq(
                    AliasedReturnItem(varFor("n"), varFor("x"))(pos),
                    AliasedReturnItem(countStar(), varFor("c"))(pos)
                  )
                )(pos),
                groupBy = Some(groupBy(varFor("x"))),
                orderBy = None,
                skip = None,
                limit = None
              )(pos)
            ),
            returnAll
          )
        )
    }
  }

  test("GROUP BY inside EXISTS subquery expression") {
    val query =
      """
        |RETURN EXISTS { RETURN 1 AS x, count(*) AS c GROUP BY x } AS r
        |""".stripMargin

    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = false,
              ReturnItems(
                FreeProjection,
                Seq(AliasedReturnItem(
                  ExistsExpression(
                    singleQuery(
                      Return(
                        distinct = false,
                        ReturnItems(
                          FreeProjection,
                          Seq(
                            AliasedReturnItem(literalInt(1), varFor("x"))(pos),
                            AliasedReturnItem(countStar(), varFor("c"))(pos)
                          )
                        )(pos),
                        groupBy = Some(groupBy(varFor("x"))),
                        orderBy = None,
                        skip = None,
                        limit = None
                      )(pos)
                    )
                  )(pos, None, None),
                  varFor("r")
                )(pos))
              )(pos),
              groupBy = None,
              orderBy = None,
              skip = None,
              limit = None
            )(pos)
          )
        )
    }
  }

  test("GROUP BY followed by NEXT statement") {
    val query =
      """
        |RETURN 1 AS a, count(*) AS c GROUP BY a
        |NEXT
        |RETURN a + 1 AS b
        |""".stripMargin

    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          nextStatement(
            singleQuery(
              Return(
                distinct = false,
                ReturnItems(
                  FreeProjection,
                  Seq(
                    AliasedReturnItem(literalInt(1), varFor("a"))(pos),
                    AliasedReturnItem(countStar(), varFor("c"))(pos)
                  )
                )(pos),
                groupBy = Some(groupBy(varFor("a"))),
                orderBy = None,
                skip = None,
                limit = None
              )(pos)
            ),
            singleQuery(return_(add(varFor("a"), literalInt(1)).as("b")))
          )
        )
    }
  }

  test("Nested WITH with GROUP BY at multiple levels") {
    val query =
      """
        |WITH 1 AS a, 2 AS b
        |WITH a, count(b) AS cb GROUP BY a
        |WITH cb, count(a) AS ca GROUP BY cb
        |RETURN *
        |""".stripMargin

    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            with_(literalInt(1).as("a"), literalInt(2).as("b")),
            With(
              distinct = false,
              ReturnItems(
                FreeProjection,
                Seq(
                  UnaliasedReturnItem(varFor("a"), "a")(pos),
                  AliasedReturnItem(count(varFor("b")), varFor("cb"))(pos)
                )
              )(pos),
              groupBy = Some(groupBy(varFor("a"))),
              orderBy = None,
              skip = None,
              limit = None,
              where = None
            )(pos),
            With(
              distinct = false,
              ReturnItems(
                FreeProjection,
                Seq(
                  UnaliasedReturnItem(varFor("cb"), "cb")(pos),
                  AliasedReturnItem(count(varFor("a")), varFor("ca"))(pos)
                )
              )(pos),
              groupBy = Some(groupBy(varFor("cb"))),
              orderBy = None,
              skip = None,
              limit = None,
              where = None
            )(pos),
            returnAll
          )
        )
    }
  }

  // ─────────────────────────────────────────────────────────────────────
  // Parenthesised single expression
  // `(a)` must match `expression`, not `LPAREN RPAREN` (which is empty),
  // so it parses as ExplicitGroupingElements(Seq(Variable("a")))
  // where the variable carries isIsolated = true.
  // ─────────────────────────────────────────────────────────────────────

  test("RETURN count(*) AS n GROUP BY (a)") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            Return(
              distinct = false,
              ReturnItems(FreeProjection, Seq(AliasedReturnItem(countStar(), varFor("n"))(pos)))(pos),
              groupBy = Some(groupBy(varFor("a", isIsolated = true))),
              orderBy = None,
              skip = None,
              limit = None
            )(pos)
          )
        )
    }
  }

  // ─────────────────────────────────────────────────────────────────────
  // Negative tests — malformed GROUP BY in Cypher 25
  // ─────────────────────────────────────────────────────────────────────

  test("RETURN a GROUP BY") {
    failsParsing[Statement].in {
      case Cypher5 => _.withAnyFailure
      case _ => _.withSyntaxError(
          """Invalid input '': expected an expression, '(' or 'ALL' (line 1, column 18 (offset: 17))
            |"RETURN a GROUP BY"
            |                  ^""".stripMargin
        )
    }
  }

  test("RETURN a GROUP a") {
    failsParsing[Statement].in {
      case Cypher5 => _.withAnyFailure
      case _ => _.withSyntaxError(
          """Invalid input 'a': expected 'BY' (line 1, column 16 (offset: 15))
            |"RETURN a GROUP a"
            |                ^""".stripMargin
        )
    }
  }

  test("RETURN a GROUP BY (), b") {
    failsParsing[Statement].in {
      case Cypher5 => _.withAnyFailure
      case _ => _.withSyntaxError(
          """Invalid input ',': expected an expression, 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOR', 'FOREACH', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NEXT', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 21 (offset: 20))
            |"RETURN a GROUP BY (), b"
            |                     ^""".stripMargin
        )
    }
  }

  test("RETURN a GROUP BY a,") {
    failsParsing[Statement].in {
      case Cypher5 => _.withAnyFailure
      case _ => _.withSyntaxError(
          """Invalid input '': expected an expression (line 1, column 21 (offset: 20))
            |"RETURN a GROUP BY a,"
            |                     ^""".stripMargin
        )
    }
  }

  test("RETURN a ORDER BY a GROUP BY a") {
    failsParsing[Statement].in {
      case Cypher5 => _.withAnyFailure
      case _ => _.withSyntaxError(
          """Invalid input 'GROUP': expected an expression, ',', 'ASC', 'ASCENDING', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DESC', 'DESCENDING', 'DETACH', 'FILTER', 'FINISH', 'FOR', 'FOREACH', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NEXT', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 21 (offset: 20))
            |"RETURN a ORDER BY a GROUP BY a"
            |                     ^""".stripMargin
        )
    }
  }

  test("RETURN a GROUP BY a GROUP BY b") {
    failsParsing[Statement].in {
      case Cypher5 => _.withAnyFailure
      case _ => _.withSyntaxError(
          """Invalid input 'GROUP': expected an expression, ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOR', 'FOREACH', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NEXT', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 21 (offset: 20))
            |"RETURN a GROUP BY a GROUP BY b"
            |                     ^""".stripMargin
        )
    }
  }

}
