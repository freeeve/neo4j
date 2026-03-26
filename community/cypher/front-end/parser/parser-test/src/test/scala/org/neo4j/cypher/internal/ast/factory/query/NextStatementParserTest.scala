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
import org.neo4j.cypher.internal.ast.ParsedAsFilter
import org.neo4j.cypher.internal.ast.ParsedAsLet
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase

class NextStatementParserTest extends AstParsingTestBase {

  test("P1") {
    val query =
      """
      RETURN 1 AS a

      NEXT

      RETURN a + 1 AS b
      """
    query should parseIn[Statement] {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'NEXT': expected ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 4, column 7 (offset: 28))
                            |"      NEXT"
                            |       ^""".stripMargin)
      case _ => _.toAst(
          nextStatement(
            singleQuery(return_(literalInt(1).as("a"))),
            singleQuery(return_(add(varFor("a"), literalInt(1)).as("b")))
          )
        )
    }
  }

  test("P2") {
    val query =
      """
      RETURN 1 AS a, 2 AS b

      NEXT

      RETURN *
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          nextStatement(
            singleQuery(return_(literalInt(1).as("a"), literalInt(2).as("b"))),
            singleQuery(returnAll)
          )
        )
    }
  }

  test("P3") {
    val query =
      """
      LET a = 1, b = 2
      RETURN a, 3 AS c

      NEXT

      RETURN *
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          nextStatement(
            singleQuery(
              withAdditionalItemsTyped(ParsedAsLet, literalInt(1).as("a"), literalInt(2).as("b")),
              return_(returnItem(varFor("a"), "a"), literalInt(3).as("c"))
            ),
            singleQuery(returnAll)
          )
        )
    }
  }

  test("P4") {
    val query =
      """
      LET a = 1, b = 2
      RETURN a, b

      NEXT

      RETURN *, a + b AS c
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          nextStatement(
            singleQuery(
              withAdditionalItemsTyped(ParsedAsLet, literalInt(1).as("a"), literalInt(2).as("b")),
              return_(returnItem(varFor("a"), "a"), returnItem(varFor("b"), "b"))
            ),
            singleQuery(returnAll(add(varFor("a"), varFor("b")).as("c")))
          )
        )
    }
  }

  test("P5") {
    val query =
      """
      LET a = 1, b = 2
      RETURN a, a + b AS c

      NEXT

      LET b = "B"
      RETURN *
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          nextStatement(
            singleQuery(
              withAdditionalItemsTyped(ParsedAsLet, literalInt(1).as("a"), literalInt(2).as("b")),
              return_(returnItem(varFor("a"), "a"), add(varFor("a"), varFor("b")).as("c"))
            ),
            singleQuery(
              withAdditionalItemsTyped(ParsedAsLet, literalString("B").as("b")),
              returnAll
            )
          )
        )
    }
  }

  test("P6") {
    val query =
      """
      LET a = 1
      RETURN *

      NEXT

      LET b = 2
      RETURN *

      NEXT

      LET c = 3
      RETURN *
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          nextStatement(
            singleQuery(
              withAdditionalItemsTyped(ParsedAsLet, literalInt(1).as("a")),
              returnAll
            ),
            singleQuery(
              withAdditionalItemsTyped(ParsedAsLet, literalInt(2).as("b")),
              returnAll
            ),
            singleQuery(
              withAdditionalItemsTyped(ParsedAsLet, literalInt(3).as("c")),
              returnAll
            )
          )
        )
    }
  }

  test("P7") {
    val query =
      """
      FILTER false
      RETURN *
      NEXT
      RETURN 1 AS a
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          nextStatement(
            singleQuery(
              withAllTyped(Some(where(falseLiteral)), ParsedAsFilter),
              returnAll
            ),
            singleQuery(
              return_(literalInt(1).as("a"))
            )
          )
        )
    }
  }

  test("P8") {
    val query =
      """
      FINISH

      NEXT

      FINISH
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(nextStatement(
          singleQuery(finish()),
          singleQuery(finish())
        ))
    }
  }

  test("P9") {
    val query =
      """
      MATCH (n:L1)
      RETURN n

      NEXT

      FINISH

      NEXT

      MATCH (n:L2)
      RETURN *
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          nextStatement(
            singleQuery(match_(nodePat(Some("n"), Some(labelLeaf("L1")))), return_(returnItem(varFor("n"), "n"))),
            singleQuery(finish()),
            singleQuery(match_(nodePat(Some("n"), Some(labelLeaf("L2")))), returnAll)
          )
        )
    }
  }

  test("P10") {
    val query =
      """
      MATCH (n)
      RETURN n

      NEXT

      WHEN n.x > 2 THEN
        RETURN "large number" AS msg
      WHEN n.x > 1 THEN
        RETURN "small number" AS msg
      ELSE
        RETURN "tiny number" AS msg
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstWith(
          nextStatement(
            singleQuery(match_(nodePat(Some("n"))), return_(returnItem(varFor("n"), "n"))),
            conditionalQueryWhen(
              conditionalQueryDefault(singleQuery(return_(literalString("tiny number").as("msg")))),
              conditionalQueryBranch(
                greaterThan(prop("n", "x"), literalInt(2)),
                singleQuery(return_(literalString("large number").as("msg")))
              ),
              conditionalQueryBranch(
                greaterThan(prop("n", "x"), literalInt(1)),
                singleQuery(return_(literalString("small number").as("msg")))
              )
            )
          ),
          obfuscator = false // Disabled because of bug in obfuscation
        )
    }
  }

  test("P11") {
    val query =
      """
      MATCH (n)
      RETURN n

      NEXT

      WHEN n.x % 2 = 0 THEN {
        RETURN n.x + 2 AS x

        NEXT

        RETURN x, "larger even number" AS msg
      }
      ELSE {
        RETURN n.x + 2 AS x

        NEXT

        RETURN x, "larger odd number" AS msg
      }
      """

    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstWith(
          nextStatement(
            singleQuery(match_(nodePat(Some("n"))), return_(returnItem(varFor("n"), "n"))),
            conditionalQueryWhen(
              conditionalQueryDefault(
                topLevelBraces(
                  nextStatement(
                    singleQuery(return_(add(prop("n", "x"), literalInt(2)).as("x"))),
                    singleQuery(return_(returnItem(varFor("x"), "x"), literalString("larger odd number").as("msg")))
                  )
                )
              ),
              conditionalQueryBranch(
                equals(modulo(prop("n", "x"), literalInt(2)), literalInt(0)),
                topLevelBraces(
                  nextStatement(
                    singleQuery(return_(add(prop("n", "x"), literalInt(2)).as("x"))),
                    singleQuery(return_(returnItem(varFor("x"), "x"), literalString("larger even number").as("msg")))
                  )
                )
              )
            )
          ),
          obfuscator = false // Disabled because of bug in obfuscation
        )
    }
  }

  test("P12") {
    val query =
      """
      RETURN 1 AS a
      UNION
      RETURN 2 AS a

      NEXT

      RETURN a + 1 AS b
      UNION
      RETURN a + 2 AS b
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          nextStatement(
            union(
              singleQuery(return_(literalInt(1).as("a"))),
              singleQuery(return_(literalInt(2).as("a")))
            ),
            union(
              singleQuery(return_(add(varFor("a"), literalInt(1)).as("b"))),
              singleQuery(return_(add(varFor("a"), literalInt(2)).as("b")))
            )
          )
        )
    }
  }

  test("P13") {
    val query =
      """
      LET a = 1
      CALL (a) {
        RETURN a + 1 AS b

        NEXT

        RETURN a + b AS c

        NEXT

        RETURN a + c AS d
      }
      RETURN *
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            withAdditionalItemsTyped(ParsedAsLet, literalInt(1).as("a")),
            scopeClauseSubqueryCall(
              false,
              Seq(
                varFor("a")
              ),
              nextStatement(
                singleQuery(
                  return_(add(varFor("a"), literalInt(1)).as("b"))
                ),
                singleQuery(
                  return_(add(varFor("a"), varFor("b")).as("c"))
                ),
                singleQuery(
                  return_(add(varFor("a"), varFor("c")).as("d"))
                )
              )
            ),
            returnAll
          )
        )
    }
  }

  test("P14") {
    val query =
      """
      LET a = 1
      LET x = EXISTS {
        RETURN a + 1 AS b

        NEXT

        RETURN a + b AS c

        NEXT

        RETURN a + c AS d
      }
      RETURN *
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            withAdditionalItemsTyped(ParsedAsLet, literalInt(1).as("a")),
            withAdditionalItemsTyped(
              ParsedAsLet,
              ExistsExpression(
                nextStatement(
                  singleQuery(
                    return_(add(varFor("a"), literalInt(1)).as("b"))
                  ),
                  singleQuery(
                    return_(add(varFor("a"), varFor("b")).as("c"))
                  ),
                  singleQuery(
                    return_(add(varFor("a"), varFor("c")).as("d"))
                  )
                )
              )(pos, None, None).as("x")
            ),
            returnAll
          )
        )
    }
  }

  test("P15") {
    val query =
      """
      LET a = 1
      LET x = COUNT {
        RETURN a + 1 AS b

        NEXT

        RETURN a + b AS c

        NEXT

        RETURN a + c AS d
      }
      RETURN *
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            withAdditionalItemsTyped(ParsedAsLet, literalInt(1).as("a")),
            withAdditionalItemsTyped(
              ParsedAsLet,
              CountExpression(
                nextStatement(
                  singleQuery(
                    return_(add(varFor("a"), literalInt(1)).as("b"))
                  ),
                  singleQuery(
                    return_(add(varFor("a"), varFor("b")).as("c"))
                  ),
                  singleQuery(
                    return_(add(varFor("a"), varFor("c")).as("d"))
                  )
                )
              )(pos, None, None).as("x")
            ),
            returnAll
          )
        )
    }
  }

  test("P16") {
    val query =
      """
      LET a = 1
      LET x = COLLECT {
        RETURN a + 1 AS b

        NEXT

        RETURN a + b AS c

        NEXT

        RETURN a + c AS d
      }
      RETURN *
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            withAdditionalItemsTyped(ParsedAsLet, literalInt(1).as("a")),
            withAdditionalItemsTyped(
              ParsedAsLet,
              CollectExpression(
                nextStatement(
                  singleQuery(
                    return_(add(varFor("a"), literalInt(1)).as("b"))
                  ),
                  singleQuery(
                    return_(add(varFor("a"), varFor("b")).as("c"))
                  ),
                  singleQuery(
                    return_(add(varFor("a"), varFor("c")).as("d"))
                  )
                )
              )(pos, None, None).as("x")
            ),
            returnAll
          )
        )
    }
  }

  test("P17") {
    val query =
      """
      {
        RETURN 1 AS a

        NEXT

        RETURN a + 1 AS b
      }

      NEXT

      RETURN b + 1 AS c
      """
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          nextStatement(
            topLevelBraces(
              nextStatement(
                singleQuery(return_(literalInt(1).as("a"))),
                singleQuery(return_(add(varFor("a"), literalInt(1)).as("b")))
              )
            ),
            singleQuery(return_(add(varFor("b"), literalInt(1)).as("c")))
          )
        )
    }
  }

  test("N1") {
    val query =
      """
      NEXT
      """
    query should parseIn[Statement] {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'NEXT': expected 'ALTER', 'ORDER BY', 'CALL', 'USING PERIODIC COMMIT', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FINISH', 'FOREACH', 'GRANT', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE' or 'WITH' (line 2, column 7 (offset: 7))
                            |"      NEXT"
                            |       ^""".stripMargin)
      case _ =>
        _.withSyntaxError("""Invalid input 'NEXT': expected 'ALTER', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FILTER', 'FINISH', 'FOR', 'FOREACH', 'GRANT', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE', 'WHEN', 'WITH' or '{' (line 2, column 7 (offset: 7))
                            |"      NEXT"
                            |       ^""".stripMargin)
    }
  }

  test("N2") {
    val query =
      """
      LET a = 1
      RETURN a

      NEXT
      """
    query should parseIn[Statement] {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'LET': expected 'ALTER', 'ORDER BY', 'CALL', 'USING PERIODIC COMMIT', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FINISH', 'FOREACH', 'GRANT', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE' or 'WITH' (line 2, column 7 (offset: 7))
                            |"      LET a = 1"
                            |       ^""".stripMargin)
      case _ =>
        _.withSyntaxError("""Invalid input '': expected 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOR', 'FOREACH', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE', 'WHEN', 'WITH' or '{' (line 6, column 7 (offset: 50))
                            |""
                            |       ^""".stripMargin)
    }
  }

  test("N3") {
    val query =
      """
      NEXT

      LET a = 1
      RETURN a
      """
    query should parseIn[Statement] {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'NEXT': expected 'ALTER', 'ORDER BY', 'CALL', 'USING PERIODIC COMMIT', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FINISH', 'FOREACH', 'GRANT', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE' or 'WITH' (line 2, column 7 (offset: 7))
                            |"      NEXT"
                            |       ^""".stripMargin)
      case _ =>
        _.withSyntaxError("""Invalid input 'NEXT': expected 'ALTER', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FILTER', 'FINISH', 'FOR', 'FOREACH', 'GRANT', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE', 'WHEN', 'WITH' or '{' (line 2, column 7 (offset: 7))
                            |"      NEXT"
                            |       ^""".stripMargin)
    }
  }

  test("N4") {
    val query =
      """
      SHOW DATABASES YIELD name
      RETURN count(DISTINCT name) AS count

      NEXT

      RETURN count * 2 AS res
      """
    query should parseIn[Statement] {
      _ =>
        _.withSyntaxError("""Invalid input 'NEXT': expected ',', 'ORDER BY', 'LIMIT', 'OFFSET', 'SKIP' or <EOF> (line 5, column 7 (offset: 83))
                            |"      NEXT"
                            |       ^""".stripMargin)
    }
  }
}
