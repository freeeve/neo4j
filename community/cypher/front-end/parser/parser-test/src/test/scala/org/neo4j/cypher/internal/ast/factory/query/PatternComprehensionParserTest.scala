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

import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase

/**
 * This test class was created due to a bug in Javacc code generation and does not cover general pattern comprehensions
 */
class PatternComprehensionParserTest extends AstParsingTestBase { //
  private val variable = Seq("", "x")
  private val labelExpressions = Seq("", ":A", "IS A")
  private val properties = Seq("", "{prop:1}")
  private val pathLength = Seq("", "*1..5")
  private val where = Seq("", "WHERE x.prop = 1")

  for {
    maybeVariable <- variable
    maybeLabelExpr <- labelExpressions
    maybeProperties <- properties
    maybeWhere <- where
  } yield {
    val nodeReturnText = s"[($maybeVariable $maybeLabelExpr $maybeProperties $maybeWhere)-->() | 1]"
    test(s"RETURN $nodeReturnText") {
      parsesTo[Statements](
        singleQuery(
          return_(
            returnItem(
              patternComprehension(
                relationshipChain(
                  nodePat(
                    name = if (maybeVariable.equals("")) None else Some(maybeVariable),
                    labelExpression =
                      if (maybeLabelExpr.equals("")) None
                      else if (maybeLabelExpr.equals(":A")) Some(labelLeaf("A"))
                      else Some(labelLeaf("A", containsIs = true)),
                    properties = if (maybeProperties.equals("")) None else Some(mapOf(("prop", literalInt(1)))),
                    predicates = if (maybeWhere.equals("")) None else Some(equals(prop("x", "prop"), literalInt(1)))
                  ),
                  relPat(),
                  nodePat()
                ),
                literalInt(1)
              ),
              nodeReturnText
            )
          )
        )
      )
    }

    for {
      maybePathLength <- pathLength
    } yield {
      val relReturnText = s"[()-[$maybeVariable $maybeLabelExpr $maybePathLength $maybeProperties $maybeWhere]->() | 1]"
      test(s"RETURN $relReturnText") {
        parsesTo[Statements](
          singleQuery(
            return_(
              returnItem(
                patternComprehension(
                  relationshipChain(
                    nodePat(),
                    relPat(
                      name = if (maybeVariable.equals("")) None else Some(maybeVariable),
                      labelExpression =
                        if (maybeLabelExpr.equals("")) None
                        else if (maybeLabelExpr.equals(":A")) Some(labelRelTypeLeaf("A"))
                        else Some(labelRelTypeLeaf("A", containsIs = true)),
                      length = if (maybePathLength.equals("")) None else Some(Some(range(Some(1), Some(5)))),
                      properties = if (maybeProperties.equals("")) None else Some(mapOf(("prop", literalInt(1)))),
                      predicates = if (maybeWhere.equals("")) None else Some(equals(prop("x", "prop"), literalInt(1)))
                    ),
                    nodePat()
                  ),
                  literalInt(1)
                ),
                relReturnText
              )
            )
          )
        )
      }
    }
  }

  test(s"RETURN [(WHERE {prop: 123})-->() | WHERE.prop]") {
    parsesIn[Statements] {
      case Cypher5 => _.toAst(
          Statements(Seq(singleQuery(
            return_(
              returnItem(
                patternComprehension(
                  relationshipChain(
                    nodePat(
                      name = Some("WHERE"),
                      properties = Some(mapOf("prop" -> literal(123))),
                      predicates = None
                    ),
                    relPat(),
                    nodePat()
                  ),
                  prop("WHERE", "prop")
                ),
                s"[(WHERE {prop: 123})-->() | WHERE.prop]"
              )
            )
          )))
        )
      // ≥ Cypher25
      case _ => _.toAst(
          Statements(Seq(singleQuery(
            return_(
              returnItem(
                patternComprehension(
                  relationshipChain(
                    nodePat(
                      name = None,
                      properties = None,
                      predicates = Some(mapOf("prop" -> literal(123)))
                    ),
                    relPat(),
                    nodePat()
                  ),
                  prop("WHERE", "prop")
                ),
                s"[(WHERE {prop: 123})-->() | WHERE.prop]"
              )
            )
          )))
        )
    }
  }

  test(s"RETURN [()-[WHERE {prop: 123}]->() | WHERE.prop]") {
    parsesIn[Statements] {
      case Cypher5 => _.toAst(
          Statements(Seq(singleQuery(
            return_(
              returnItem(
                patternComprehension(
                  relationshipChain(
                    nodePat(),
                    relPat(
                      name = Some("WHERE"),
                      properties = Some(mapOf("prop" -> literal(123))),
                      predicates = None
                    ),
                    nodePat()
                  ),
                  prop("WHERE", "prop")
                ),
                s"[()-[WHERE {prop: 123}]->() | WHERE.prop]"
              )
            )
          )))
        )
      // ≥ Cypher25
      case _ => _.toAst(
          Statements(Seq(singleQuery(
            return_(
              returnItem(
                patternComprehension(
                  relationshipChain(
                    nodePat(),
                    relPat(
                      name = None,
                      properties = None,
                      predicates = Some(mapOf("prop" -> literal(123)))
                    ),
                    nodePat()
                  ),
                  prop("WHERE", "prop")
                ),
                s"[()-[WHERE {prop: 123}]->() | WHERE.prop]"
              )
            )
          )))
        )
    }
  }
}
