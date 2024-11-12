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

import org.neo4j.cypher.internal.ast.CollectExpression
import org.neo4j.cypher.internal.ast.CountExpression
import org.neo4j.cypher.internal.ast.ExistsExpression
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.TopLevelBraces
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher25
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.util.InputPosition

class TopLevelBracesParserTest extends AstParsingTestBase {

  test("{ RETURN 1 AS x }") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            TopLevelBraces(
              singleQuery(return_(aliasedReturnItem(literal(1), "x"))),
              None
            )(pos)
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  test("{{ RETURN 1 AS x }}") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            TopLevelBraces(
              TopLevelBraces(
                singleQuery(return_(aliasedReturnItem(literal(1), "x"))),
                None
              )(pos),
              None
            )(pos)
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  test("{{{ RETURN 1 AS x }}}") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            TopLevelBraces(
              TopLevelBraces(
                TopLevelBraces(
                  singleQuery(return_(aliasedReturnItem(literal(1), "x"))),
                  None
                )(pos),
                None
              )(pos),
              None
            )(pos)
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  test("{{{{ RETURN 1 AS x }}}}") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            TopLevelBraces(
              TopLevelBraces(
                TopLevelBraces(
                  TopLevelBraces(
                    singleQuery(return_(aliasedReturnItem(literal(1), "x"))),
                    None
                  )(pos),
                  None
                )(pos),
                None
              )(pos),
              None
            )(pos)
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  test("{{{{ MATCH (n) RETURN n.age + 1 AS x }}}}") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            TopLevelBraces(
              TopLevelBraces(
                TopLevelBraces(
                  TopLevelBraces(
                    singleQuery(
                      match_(Seq(nodePat(Some("n"))), None),
                      return_(aliasedReturnItem(add(propExpression(varFor("n"), "age"), literal(1)), "x"))
                    ),
                    None
                  )(pos),
                  None
                )(pos),
                None
              )(pos),
              None
            )(pos)
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  test("{ CREATE (n: Person )} UNION { CREATE (n: Animal) }") {
    val lhs = TopLevelBraces(singleQuery(create(nodePat(Some("n"), Some(labelLeaf("Person"))))), None)(pos)
    val rhs = TopLevelBraces(singleQuery(create(nodePat(Some("n"), Some(labelLeaf("Animal"))))), None)(pos)
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(union(lhs, rhs)))
        )
      case _ => _.withAnyFailure
    }
  }

  test("{ MERGE (n: Person )} UNION { MERGE (n: Animal) }") {
    val lhs = TopLevelBraces(singleQuery(merge(nodePat(Some("n"), Some(labelLeaf("Person"))))), None)(pos)
    val rhs = TopLevelBraces(singleQuery(merge(nodePat(Some("n"), Some(labelLeaf("Animal"))))), None)(pos)
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(union(lhs, rhs)))
        )
      case _ => _.withAnyFailure
    }
  }

  test("USE graph { RETURN 1 AS x }") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            TopLevelBraces(singleQuery(return_(aliasedReturnItem(literal(1), "x"))), Some(use(List("graph"))))(pos)
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  test("USE graph { USE innerGraph { RETURN 1 AS x } }") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            TopLevelBraces(
              TopLevelBraces(
                singleQuery(return_(aliasedReturnItem(literal(1), "x"))),
                Some(use(List("innerGraph")))
              )(pos),
              Some(use(List("graph")))
            )(pos)
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  test("USE graph { USE innerGraph { USE innerInnerGraph { RETURN 1 AS x } } }") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            TopLevelBraces(
              TopLevelBraces(
                TopLevelBraces(
                  singleQuery(return_(aliasedReturnItem(literal(1), "x"))),
                  Some(use(List("innerInnerGraph")))
                )(pos),
                Some(use(List("innerGraph")))
              )(pos),
              Some(use(List("graph")))
            )(pos)
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  test("USE graph { { USE innerGraph { RETURN 1 AS x } } }") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            TopLevelBraces(
              TopLevelBraces(
                TopLevelBraces(
                  singleQuery(return_(aliasedReturnItem(literal(1), "x"))),
                  Some(use(List("innerGraph")))
                )(pos),
                None
              )(pos),
              Some(use(List("graph")))
            )(pos)
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  test("USE graph { USE innerGraph { USE innerInnerGraph { USE innerInnerInnerGraph { RETURN 1 AS x } } } }") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            TopLevelBraces(
              TopLevelBraces(
                TopLevelBraces(
                  TopLevelBraces(
                    singleQuery(return_(aliasedReturnItem(literal(1), "x"))),
                    Some(use(List("innerInnerInnerGraph")))
                  )(pos),
                  Some(use(List("innerInnerGraph")))
                )(pos),
                Some(use(List("innerGraph")))
              )(pos),
              Some(use(List("graph")))
            )(pos)
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  test("USE graph { { USE innerGraph { { RETURN 1 AS x } } } }") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            TopLevelBraces(
              TopLevelBraces(
                TopLevelBraces(
                  TopLevelBraces(
                    singleQuery(return_(aliasedReturnItem(literal(1), "x"))),
                    None
                  )(pos),
                  Some(use(List("innerGraph")))
                )(pos),
                None
              )(pos),
              Some(use(List("graph")))
            )(pos)
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  test("USE graph { { { USE innerGraph { RETURN 1 AS x } } } }") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            TopLevelBraces(
              TopLevelBraces(
                TopLevelBraces(
                  TopLevelBraces(
                    singleQuery(return_(aliasedReturnItem(literal(1), "x"))),
                    Some(use(List("innerGraph")))
                  )(pos),
                  None
                )(pos),
                None
              )(pos),
              Some(use(List("graph")))
            )(pos)
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  // UNION / UNION ALL / COMBINATION

  test("{ RETURN 1 AS x } UNION { RETURN 2 AS x }") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(Statements(Seq(
          union(
            TopLevelBraces(singleQuery(return_(aliasedReturnItem(literal(1), "x"))), None)(pos),
            TopLevelBraces(singleQuery(return_(aliasedReturnItem(literal(2), "x"))), None)(pos),
            differentReturnOrderAllowed = false
          )
        )))
      case _ => _.withAnyFailure
    }
  }

  test("USE graphLeft { RETURN 1 AS x } UNION USE graphRight { RETURN 2 AS x }") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(Statements(Seq(
          union(
            TopLevelBraces(singleQuery(return_(aliasedReturnItem(literal(1), "x"))), Some(use(List("graphLeft"))))(pos),
            TopLevelBraces(singleQuery(return_(aliasedReturnItem(literal(2), "x"))), Some(use(List("graphRight"))))(
              pos
            ),
            differentReturnOrderAllowed = false
          )
        )))
      case _ => _.withAnyFailure
    }
  }

  test(
    "{ USE graphLeft { RETURN 1 AS x } UNION ALL USE graphRight { RETURN 2 AS x } } UNION USE graphRight { RETURN 2 AS x }"
  ) {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(Statements(Seq(
          union(
            TopLevelBraces(
              union(
                TopLevelBraces(
                  singleQuery(return_(aliasedReturnItem(literal(1), "x"))),
                  Some(use(List("graphLeft")))
                )(pos),
                TopLevelBraces(
                  singleQuery(return_(aliasedReturnItem(literal(2), "x"))),
                  Some(use(List("graphRight")))
                )(pos),
                differentReturnOrderAllowed = false
              ).all,
              None
            )(pos),
            TopLevelBraces(singleQuery(return_(aliasedReturnItem(literal(2), "x"))), Some(use(List("graphRight"))))(
              pos
            ),
            differentReturnOrderAllowed = false
          )
        )))

      case _ => _.withAnyFailure
    }
  }

  test(
    "{ { { RETURN 1 AS x } UNION { RETURN 2 AS x } } UNION ALL { RETURN 2 AS x } } UNION { RETURN 2 AS x }"
  ) {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(Statements(Seq(
          union(
            TopLevelBraces(
              union(
                TopLevelBraces(
                  union(
                    TopLevelBraces(singleQuery(return_(aliasedReturnItem(literal(1), "x"))), None)(pos),
                    TopLevelBraces(singleQuery(return_(aliasedReturnItem(literal(2), "x"))), None)(pos),
                    differentReturnOrderAllowed = false
                  ),
                  None
                )(pos),
                TopLevelBraces(singleQuery(return_(aliasedReturnItem(literal(2), "x"))), None)(pos),
                differentReturnOrderAllowed = false
              ).all,
              None
            )(pos),
            TopLevelBraces(singleQuery(return_(aliasedReturnItem(literal(2), "x"))), None)(
              pos
            ),
            differentReturnOrderAllowed = false
          )
        )))
      case _ => _.withAnyFailure
    }
  }

  test(
    "{ { USE graphLeft { RETURN 1 AS x } UNION USE graphRight { RETURN 2 AS x } } UNION ALL USE graphRight { RETURN 2 AS x } } UNION USE graphRight { RETURN 2 AS x }"
  ) {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(Statements(Seq(
          union(
            TopLevelBraces(
              union(
                TopLevelBraces(
                  union(
                    TopLevelBraces(
                      singleQuery(return_(aliasedReturnItem(literal(1), "x"))),
                      Some(use(List("graphLeft")))
                    )(pos),
                    TopLevelBraces(
                      singleQuery(return_(aliasedReturnItem(literal(2), "x"))),
                      Some(use(List("graphRight")))
                    )(pos),
                    differentReturnOrderAllowed = false
                  ),
                  None
                )(pos),
                TopLevelBraces(singleQuery(return_(aliasedReturnItem(literal(2), "x"))), Some(use(List("graphRight"))))(
                  pos
                ),
                differentReturnOrderAllowed = false
              ).all,
              None
            )(pos),
            TopLevelBraces(singleQuery(return_(aliasedReturnItem(literal(2), "x"))), Some(use(List("graphRight"))))(
              pos
            ),
            differentReturnOrderAllowed = false
          )
        )))
      case _ => _.withAnyFailure
    }
  }

  // SUBQUERY CALL

  test("WITH 1 AS x CALL (x) { USE graph USE graph { RETURN 1 + x AS y } } RETURN y") {
    failsParsing[Statements]
  }

  test("WITH 1 AS x CALL (x) { USE graph { RETURN 1 + x AS y } } RETURN y") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            singleQuery(
              with_(aliasedReturnItem(literal(1), "x")),
              scopeClauseSubqueryCall(
                false,
                Seq(varFor("x")),
                TopLevelBraces(
                  singleQuery(return_(aliasedReturnItem(add(literal(1), varFor("x")), "y"))),
                  Some(use(List("graph")))
                )(pos)
              ),
              return_(returnItem(varFor("y"), "y"))
            )
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  test("WITH 1 AS x CALL (x) { { { USE graph { RETURN 1 + x AS y } } } } RETURN y") {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            singleQuery(
              with_(aliasedReturnItem(literal(1), "x")),
              scopeClauseSubqueryCall(
                false,
                Seq(varFor("x")),
                TopLevelBraces(
                  TopLevelBraces(
                    TopLevelBraces(
                      singleQuery(return_(aliasedReturnItem(add(literal(1), varFor("x")), "y"))),
                      Some(use(List("graph")))
                    )(pos),
                    None
                  )(pos),
                  None
                )(pos)
              ),
              return_(returnItem(varFor("y"), "y"))
            )
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  test(
    "WITH 1 AS x CALL (x) { {RETURN 1 + x AS y UNION ALL RETURN 2 + x AS y } UNION { { USE graph { RETURN 1 + x AS y } } } } RETURN y"
  ) {
    parsesIn[Statements] {
      case Cypher25 => _.toAst(
          Statements(Seq(
            singleQuery(
              with_(aliasedReturnItem(literal(1), "x")),
              scopeClauseSubqueryCall(
                false,
                Seq(varFor("x")),
                union(
                  TopLevelBraces(
                    union(
                      singleQuery(return_(aliasedReturnItem(add(literal(1), varFor("x")), "y"))),
                      singleQuery(return_(aliasedReturnItem(add(literal(2), varFor("x")), "y")))
                    ).all,
                    None
                  )(pos),
                  TopLevelBraces(
                    TopLevelBraces(
                      TopLevelBraces(
                        singleQuery(return_(aliasedReturnItem(add(literal(1), varFor("x")), "y"))),
                        Some(use(List("graph")))
                      )(pos),
                      None
                    )(pos),
                    None
                  )(pos)
                )
              ),
              return_(returnItem(varFor("y"), "y"))
            )
          ))
        )
      case _ => _.withAnyFailure
    }
  }

  // EXIST / COLLECT / COUNT

  test(
    """MATCH (m)
      |WHERE EXISTS { USE graph { MATCH (m) RETURN m UNION MATCH (p) RETURN p } }
      |RETURN m""".stripMargin
  ) {
    val lhs = singleQuery(
      match_(nodePat(name = Some("m"), None)),
      return_(variableReturnItem("m"))
    )
    val rhs = singleQuery(
      match_(nodePat(name = Some("p"), None)),
      return_(variableReturnItem("p"))
    )

    parsesIn[Statement] {
      case Cypher25 => _.toAst(
          singleQuery(
            match_(
              nodePat(name = Some("m")),
              where = Some(where(
                ExistsExpression(
                  TopLevelBraces(
                    union(lhs, rhs),
                    Some(use(List("graph")))
                  )(pos)
                )(InputPosition(16, 2, 7), None, None)
              ))
            ),
            return_(variableReturnItem("m"))
          )
        )
      case _ => _.withAnyFailure
    }
  }

  test(
    """MATCH (m)
      |WHERE COLLECT { USE graph { MATCH (m) RETURN m.prop AS a UNION MATCH (p) RETURN p.prop AS a } } = [1, 2, 3]
      |RETURN m""".stripMargin
  ) {
    val lhs = singleQuery(
      match_(nodePat(name = Some("m"), namePos = InputPosition(33, 2, 24), position = InputPosition(32, 2, 23))),
      return_(aliasedReturnItem(prop("m", "prop"), "a"))
    )
    val rhs = singleQuery(
      match_(nodePat(name = Some("p"), namePos = InputPosition(68, 2, 59), position = InputPosition(67, 2, 58))),
      return_(aliasedReturnItem(prop("p", "prop"), "a"))
    )

    parsesIn[Statement] {
      case Cypher25 => _.toAst(
          singleQuery(
            match_(
              nodePat(name = Some("m")),
              where = Some(where(equals(
                CollectExpression(
                  TopLevelBraces(
                    union(lhs, rhs),
                    Some(use(List("graph")))
                  )(pos)
                )(InputPosition(16, 2, 7), None, None),
                listOfInt(1, 2, 3)
              )))
            ),
            return_(variableReturnItem("m"))
          )
        )
      case _ => _.withAnyFailure
    }
  }

  test(
    """MATCH (m)
      |WHERE COUNT { USE graph { MATCH (m) RETURN m UNION MATCH (p) RETURN p } } >= 3
      |RETURN m""".stripMargin
  ) {
    val lhs = singleQuery(
      match_(nodePat(name = Some("m"), namePos = InputPosition(31, 2, 22), position = InputPosition(30, 2, 21))),
      return_(variableReturnItem("m"))
    )
    val rhs = singleQuery(
      match_(nodePat(name = Some("p"), namePos = InputPosition(56, 2, 47), position = InputPosition(55, 2, 46))),
      return_(variableReturnItem("p"))
    )

    parsesIn[Statement] {
      case Cypher25 => _.toAst(
          singleQuery(
            match_(
              nodePat(name = Some("m")),
              where = Some(where(greaterThanOrEqual(
                CountExpression(
                  TopLevelBraces(
                    union(lhs, rhs),
                    Some(use(List("graph")))
                  )(pos)
                )(InputPosition(16, 2, 7), None, None),
                literal(3)
              )))
            ),
            return_(variableReturnItem("m"))
          )
        )
      case _ => _.withAnyFailure
    }
  }

  // NEGATIVE CASES
  test("WITH 1 AS x { RETURN 1 AS y UNION RETURN 2 AS z }") {
    failsParsing[Statements]
  }

  test("MATCH(n) { RETURN 1 AS y UNION RETURN 2 AS z } RETURN n.prop, y, z") {
    failsParsing[Statements]
  }

  test("RETURN { WITH 1 AS x }") {
    failsParsing[Statements]
  }

  test("""LOAD CSV
         |FROM 'file:///artists-fieldterminator.csv' AS line
         |FIELDTERMINATOR ';' {
         |  CREATE (:Artist {name: line[1], year: toInteger(line[2])})
         |}
         |""".stripMargin) {
    failsParsing[Statements]
  }
}
