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

import org.neo4j.cypher.internal.ast.ExistsExpression
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.expressions.RelationshipChain
import org.neo4j.cypher.internal.expressions.RelationshipPattern
import org.neo4j.cypher.internal.expressions.SemanticDirection.OUTGOING

class ConditionalQueryWhenParserTest extends AstParsingTestBase {

  test("WHEN true THEN RETURN 1 AS x") {
    parsesIn[Statement] {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'WHEN': expected 'ALTER', 'ORDER BY', 'CALL', 'USING PERIODIC COMMIT', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FINISH', 'FOREACH', 'GRANT', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE' or 'WITH' (line 1, column 1 (offset: 0))
                            |"WHEN true THEN RETURN 1 AS x"
                            | ^""".stripMargin)
      case _ => _.toAst(
          conditionalQueryWhen(conditionalQueryBranch(trueLiteral, singleQuery(return_(literalInt(1).as("x")))))
        )
    }
  }

  test("WHEN when THEN RETURN then ELSE RETURN else") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstWith(
          conditionalQueryWhen(
            conditionalQueryDefault(singleQuery(return_(returnItem(varFor("else"), "else", pos)))),
            conditionalQueryBranch(varFor("when"), singleQuery(return_(returnItem(varFor("then"), "then", pos))))
          )
        )
    }
  }

  test("WHEN true THEN RETURN 1 AS x WHEN true THEN RETURN 1 AS x") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstWith(
          conditionalQueryWhen(
            conditionalQueryBranch(trueLiteral, singleQuery(return_(literalInt(1).as("x")))),
            conditionalQueryBranch(trueLiteral, singleQuery(return_(literalInt(1).as("x"))))
          )
        )
    }
  }

  test("WHEN true THEN RETURN 1 AS x WHEN true THEN RETURN 1 AS x ELSE RETURN 1 AS x") {
    parsesIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstWith(
          conditionalQueryWhen(
            conditionalQueryDefault(singleQuery(return_(literalInt(1).as("x")))),
            conditionalQueryBranch(trueLiteral, singleQuery(return_(literalInt(1).as("x")))),
            conditionalQueryBranch(trueLiteral, singleQuery(return_(literalInt(1).as("x"))))
          )
        )
    }
  }

  test("When enclosing UNION and WHEN") {
    val query =
      """WHEN true THEN {
        |   RETURN 1 AS x
        |   UNION
        |   RETURN 1 AS x
        |}
        |WHEN true THEN {
        |   WHEN true THEN {
        |      WHEN true THEN RETURN 1 AS x
        |   }
        |}
        |ELSE RETURN 1 AS x
        |""".stripMargin

    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstWith(
          conditionalQueryWhen(
            conditionalQueryDefault(singleQuery(return_(literalInt(1).as("x")))),
            conditionalQueryBranch(
              trueLiteral,
              topLevelBraces(unionDistinct(
                singleQuery(return_(literalInt(1).as("x"))),
                singleQuery(return_(literalInt(1).as("x")))
              ))
            ),
            conditionalQueryBranch(
              trueLiteral,
              topLevelBraces(conditionalQueryWhen(conditionalQueryBranch(
                trueLiteral,
                topLevelBraces(conditionalQueryWhen(conditionalQueryBranch(
                  trueLiteral,
                  singleQuery(return_(literalInt(1).as("x")))
                )))
              )))
            )
          )
        )
    }
  }

  test("When with complex predicates") {
    val query =
      """
        |WHEN NOT 1 AND NOT 3 AND 5 THEN RETURN 1 AS x
        |WHEN EXISTS { MATCH (m)-[r]->(p) RETURN p } THEN RETURN 1 AS x
        |ELSE RETURN 1 AS x
        |""".stripMargin

    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstWith(
          conditionalQueryWhen(
            conditionalQueryDefault(singleQuery(return_(literalInt(1).as("x")))),
            conditionalQueryBranch(
              and(and(not(literalInt(1)), not(literalInt(3))), literalInt(5)),
              singleQuery(return_(literalInt(1).as("x")))
            ),
            conditionalQueryBranch(
              ExistsExpression(
                singleQuery(
                  match_(
                    RelationshipChain(
                      nodePat(Some("m")),
                      RelationshipPattern(
                        Some(varFor("r", pos)),
                        None,
                        None,
                        None,
                        None,
                        OUTGOING
                      )(pos),
                      nodePat(Some("p"))
                    )(pos)
                  ),
                  return_(variableReturnItem("p"))
                )
              )(pos, None, None),
              singleQuery(return_(literalInt(1).as("x")))
            )
          ),
          obfuscator = false // Bug in obfuscation
        )
    }
  }

  test("UNION with WHEN as arguments") {
    val query =
      """
        |{
        |   WHEN true THEN RETURN 1 AS x
        |   WHEN true THEN RETURN 1 AS x
        |   ELSE RETURN 1 AS x
        |}
        |UNION
        |{
        |   WHEN true THEN RETURN 1 AS x
        |   WHEN true THEN RETURN 1 AS x
        |   ELSE RETURN 1 AS x
        |}
        |""".stripMargin

    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstWith(
          union(
            topLevelBraces(
              conditionalQueryWhen(
                conditionalQueryDefault(singleQuery(return_(literalInt(1).as("x")))),
                conditionalQueryBranch(trueLiteral, singleQuery(return_(literalInt(1).as("x")))),
                conditionalQueryBranch(trueLiteral, singleQuery(return_(literalInt(1).as("x"))))
              )
            ),
            topLevelBraces(
              conditionalQueryWhen(
                conditionalQueryDefault(singleQuery(return_(literalInt(1).as("x")))),
                conditionalQueryBranch(trueLiteral, singleQuery(return_(literalInt(1).as("x")))),
                conditionalQueryBranch(trueLiteral, singleQuery(return_(literalInt(1).as("x"))))
              )
            )
          )
        )
    }
  }

  test("CALL subquery with enclosed When") {
    val query =
      """
        |MATCH (n)
        |CALL (n) {
        |   WHEN n.prop > 0 THEN RETURN 1 AS x
        |   WHEN n.prop < 0 THEN RETURN 1 AS x
        |   ELSE RETURN 1 AS x
        |}
        |RETURN *
        |""".stripMargin

    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstWith(
          singleQuery(
            match_(nodePat(Some("n"))),
            scopeClauseSubqueryCall(
              false,
              Seq(varFor("n")),
              conditionalQueryWhen(
                conditionalQueryDefault(singleQuery(return_(literalInt(1).as("x")))),
                conditionalQueryBranch(
                  greaterThan(prop("n", "prop"), literalInt(0)),
                  singleQuery(return_(literalInt(1).as("x")))
                ),
                conditionalQueryBranch(
                  lessThan(prop("n", "prop"), literalInt(0)),
                  singleQuery(return_(literalInt(1).as("x")))
                )
              )
            ),
            returnAll
          )
        )
    }
  }

  test("Exists subquery enclosing WHEN") {
    val query =
      """
        |RETURN EXISTS { WHEN true THEN RETURN 1 AS x } AS x
        |""".stripMargin
    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAst(
          singleQuery(
            return_(aliasedReturnItem(
              ExistsExpression(
                conditionalQueryWhen(conditionalQueryBranch(trueLiteral, singleQuery(return_(literalInt(1).as("x")))))
              )(pos, None, None),
              "x"
            ))
          )
        )
    }
  }

  test("WHEN with CASE expressions as predicates") {
    val query =
      """
        |WHEN CASE WHEN true THEN false WHEN false THEN true ELSE false END THEN RETURN 1 AS x
        |WHEN CASE false WHEN true THEN false WHEN false THEN true ELSE false END THEN RETURN 1 AS x
        |ELSE RETURN 1 AS x""".stripMargin

    query should parseIn[Statement] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstWith(
          conditionalQueryWhen(
            conditionalQueryDefault(singleQuery(return_(literalInt(1).as("x")))),
            conditionalQueryBranch(
              caseExpression(
                None,
                Some(falseLiteral),
                trueLiteral -> falseLiteral,
                falseLiteral -> trueLiteral
              ),
              singleQuery(return_(literalInt(1).as("x")))
            ),
            conditionalQueryBranch(
              caseExpression(
                Some(falseLiteral),
                Some(falseLiteral),
                equals(falseLiteral, trueLiteral) -> falseLiteral,
                equals(falseLiteral, falseLiteral) -> trueLiteral
              ),
              singleQuery(return_(literalInt(1).as("x")))
            )
          )
        )
    }
  }

  // Negative tests
  test("""ELSE RETURN 1 AS x""") {
    failsParsing[Statement].in {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'ELSE': expected 'ALTER', 'ORDER BY', 'CALL', 'USING PERIODIC COMMIT', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FINISH', 'FOREACH', 'GRANT', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE' or 'WITH' (line 1, column 1 (offset: 0))
                            |"ELSE RETURN 1 AS x"
                            | ^""".stripMargin)
      case _ =>
        _.withSyntaxError("""Invalid input 'ELSE': expected 'ALTER', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FILTER', 'FINISH', 'FOREACH', 'GRANT', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE', 'WHEN', 'WITH' or '{' (line 1, column 1 (offset: 0))
                            |"ELSE RETURN 1 AS x"
                            | ^""".stripMargin)
    }
  }

  test("""THEN RETURN 1 AS x""") {
    failsParsing[Statement].in {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'THEN': expected 'ALTER', 'ORDER BY', 'CALL', 'USING PERIODIC COMMIT', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FINISH', 'FOREACH', 'GRANT', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE' or 'WITH' (line 1, column 1 (offset: 0))
                            |"THEN RETURN 1 AS x"
                            | ^""".stripMargin)
      case _ =>
        _.withSyntaxError("""Invalid input 'THEN': expected 'ALTER', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FILTER', 'FINISH', 'FOREACH', 'GRANT', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE', 'WHEN', 'WITH' or '{' (line 1, column 1 (offset: 0))
                            |"THEN RETURN 1 AS x"
                            | ^""".stripMargin)
    }
  }

  test("""WHEN true RETURN 1 AS x""") {
    failsParsing[Statement].in {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'WHEN': expected 'ALTER', 'ORDER BY', 'CALL', 'USING PERIODIC COMMIT', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FINISH', 'FOREACH', 'GRANT', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE' or 'WITH' (line 1, column 1 (offset: 0))
                            |"WHEN true RETURN 1 AS x"
                            | ^""".stripMargin)
      case _ =>
        _.withSyntaxError("""Invalid input 'RETURN': expected an expression (line 1, column 11 (offset: 10))
                            |"WHEN true RETURN 1 AS x"
                            |           ^""".stripMargin)
    }
  }

  test("""WHEN true THEN""") {
    failsParsing[Statement].in {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'WHEN': expected 'ALTER', 'ORDER BY', 'CALL', 'USING PERIODIC COMMIT', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FINISH', 'FOREACH', 'GRANT', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE' or 'WITH' (line 1, column 1 (offset: 0))
                            |"WHEN true THEN"
                            | ^""".stripMargin)
      case _ =>
        _.withSyntaxError("""Invalid input '': expected 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOREACH', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE', 'WITH' or '{' (line 1, column 15 (offset: 14))
                            |"WHEN true THEN"
                            |               ^""".stripMargin)
    }
  }

  test("Starting else followed by WHEN") {
    val query =
      """ELSE RETURN 1 AS x
        |WHEN true THEN RETURN 1 AS x
        |""".stripMargin

    query should notParse[Statement].in {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'ELSE': expected 'ALTER', 'ORDER BY', 'CALL', 'USING PERIODIC COMMIT', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FINISH', 'FOREACH', 'GRANT', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE' or 'WITH' (line 1, column 1 (offset: 0))
                            |"ELSE RETURN 1 AS x"
                            | ^""".stripMargin)
      case _ =>
        _.withSyntaxError("""Invalid input 'ELSE': expected 'ALTER', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FILTER', 'FINISH', 'FOREACH', 'GRANT', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE', 'WHEN', 'WITH' or '{' (line 1, column 1 (offset: 0))
                            |"ELSE RETURN 1 AS x"
                            | ^""".stripMargin)
    }
  }

  test("Additional WHEN branch after ELSE") {
    val query =
      """WHEN true THEN RETURN 1 AS x
        |ELSE RETURN 1 AS x
        |WHEN true THEN RETURN 1 AS x
        |""".stripMargin
    query should notParse[Statement].in {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'WHEN': expected 'ALTER', 'ORDER BY', 'CALL', 'USING PERIODIC COMMIT', 'CREATE', 'LOAD CSV', 'START DATABASE', 'STOP DATABASE', 'DEALLOCATE', 'DELETE', 'DENY', 'DETACH', 'DROP', 'DRYRUN', 'FINISH', 'FOREACH', 'GRANT', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REALLOCATE', 'REMOVE', 'RENAME', 'RETURN', 'REVOKE', 'ENABLE SERVER', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE' or 'WITH' (line 1, column 1 (offset: 0))
                            |"WHEN true THEN RETURN 1 AS x"
                            | ^""".stripMargin)
      case _ =>
        _.withSyntaxError("""Invalid input 'WHEN': expected ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOREACH', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NEXT', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNWIND', 'USE', 'WITH' or <EOF> (line 3, column 1 (offset: 48))
                            |"WHEN true THEN RETURN 1 AS x"
                            | ^""".stripMargin)
    }
  }

  test("WHEN in singleQuery") {
    val query =
      """MATCH(n)
        |WHEN true THEN RETURN 1 AS x
        |ELSE RETURN 1 AS x
        |RETURN 1 AS x
        |""".stripMargin

    query should notParse[Statement].in {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'WHEN': expected a graph pattern, ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'USING', 'WHERE', 'WITH' or <EOF> (line 2, column 1 (offset: 9))
                            |"WHEN true THEN RETURN 1 AS x"
                            | ^""".stripMargin)
      case _ =>
        _.withSyntaxError("""Invalid input 'WHEN': expected a graph pattern, ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOREACH', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NEXT', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SEARCH', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNION', 'UNWIND', 'USE', 'USING', 'WHERE', 'WITH' or <EOF> (line 2, column 1 (offset: 9))
                            |"WHEN true THEN RETURN 1 AS x"
                            | ^""".stripMargin)
    }
  }

  test("When in the middle of query") {
    val query =
      """MATCH(n)
        |WITH n
        |WHEN true THEN RETURN 1 AS x
        |ELSE RETURN 1 AS x
        |WITH x
        |RETURN 1 AS x
        |""".stripMargin

    query should notParse[Statement].in {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'WHEN': expected an expression, ',', 'AS', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WHERE', 'WITH' or <EOF> (line 3, column 1 (offset: 16))
                            |"WHEN true THEN RETURN 1 AS x"
                            | ^""".stripMargin)
      case _ =>
        _.withSyntaxError("""Invalid input 'WHEN': expected an expression, ',', 'AS', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOREACH', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NEXT', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SHOW', 'SKIP', 'TERMINATE', 'UNION', 'UNWIND', 'USE', 'WHERE', 'WITH' or <EOF> (line 3, column 1 (offset: 16))
                            |"WHEN true THEN RETURN 1 AS x"
                            | ^""".stripMargin)
    }
  }

}
