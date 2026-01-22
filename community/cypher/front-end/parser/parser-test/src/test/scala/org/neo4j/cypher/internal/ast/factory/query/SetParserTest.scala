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

import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.SetDynamicPropertyItem
import org.neo4j.cypher.internal.ast.SetPropertyItem
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.util.symbols.CTAny

class SetParserTest extends AstParsingTestBase {

  test("SET n:A") {
    parsesTo[Clause](
      set_(
        Seq(setLabelItem("n", Seq("A")))
      )
    )
  }

  test("SET n IS A") {
    parsesTo[Clause](
      set_(
        Seq(setLabelItem("n", Seq("A"), containsIs = true))
      )
    )
  }

  test("SET n:A:B:C") {
    parsesTo[Clause](
      set_(
        Seq(setLabelItem("n", Seq("A", "B", "C")))
      )
    )
  }

  test("SET n:A, n:B") {
    parsesTo[Clause](
      set_(
        Seq(setLabelItem("n", Seq("A")), setLabelItem("n", Seq("B")))
      )
    )
  }

  test("SET n IS A, n IS B") {
    parsesTo[Clause](
      set_(
        Seq(setLabelItem("n", Seq("A"), containsIs = true), setLabelItem("n", Seq("B"), containsIs = true))
      )
    )
  }

  test("SET n IS A, n:B") {
    parsesTo[Clause](
      set_(
        Seq(setLabelItem("n", Seq("A"), containsIs = true), setLabelItem("n", Seq("B")))
      )
    )
  }

  test("SET n:A, r.prop = 1, m IS B") {
    parsesTo[Clause](
      set_(
        Seq(
          setLabelItem("n", Seq("A")),
          setPropertyItem("r", "prop", literalInt(1)),
          setLabelItem("m", Seq("B"), containsIs = true)
        )
      )
    )
  }

  test("SET n._1 = 1") {
    parsesTo[Clause](
      set_(
        Seq(
          setPropertyItem("n", "_1", literalInt(1))
        )
      )
    )
  }

  // use label name reserved keywords
  for {
    labelNameReserved <- Seq(
      "NOT",
      "NULL",
      "TYPED",
      "NORMALIZED",
      "NFC",
      "NFD",
      "NFKC",
      "NFKD"
    )
  } yield {
    test(s"SET n:$labelNameReserved, n IS $labelNameReserved") {
      parsesTo[Clause](
        set_(
          Seq(
            setLabelItem("n", Seq(labelNameReserved)),
            setLabelItem("n", Seq(labelNameReserved), containsIs = true)
          )
        )
      )
    }
  }

  // Dynamic Labels

  test("SET n:$(A)") {
    parsesTo[Clause](
      set_(
        Seq(setLabelItem("n", Seq.empty, dynamicLabels = Seq(varFor("A"))))
      )
    )
  }

  test("SET n IS $(A)") {
    parsesTo[Clause](
      set_(
        Seq(setLabelItem("n", Seq.empty, dynamicLabels = Seq(varFor("A")), containsIs = true))
      )
    )
  }

  test("SET n:$(A):B:$(C)") {
    parsesTo[Clause](
      set_(
        Seq(setLabelItem("n", Seq("B"), dynamicLabels = Seq(varFor("A"), varFor("C"))))
      )
    )
  }

  test("SET n:$($param), n:$(B)") {
    parsesTo[Clause](
      set_(
        Seq(
          setLabelItem("n", Seq.empty, dynamicLabels = Seq(parameter("param", CTAny))),
          setLabelItem("n", Seq.empty, dynamicLabels = Seq(varFor("B")))
        )
      )
    )
  }

  test("SET n IS $(\"A\"), n IS B") {
    parsesTo[Clause](
      set_(
        Seq(
          setLabelItem("n", Seq.empty, Seq(literalString("A")), containsIs = true),
          setLabelItem("n", Seq("B"), containsIs = true)
        )
      )
    )
  }

  test("SET n IS $(a || b), n:$(b || c)") {
    parsesTo[Clause](
      set_(
        Seq(
          setLabelItem("n", Seq.empty, Seq(concatenate(varFor("a"), varFor("b"))), containsIs = true),
          setLabelItem("n", Seq.empty, Seq(concatenate(varFor("b"), varFor("c"))))
        )
      )
    )
  }

  test("SET n:$(CASE WHEN x THEN \"Label1\" ELSE \"Label2\" END), r.prop = 1, m IS $($param)") {
    parsesTo[Clause](
      set_(
        Seq(
          setLabelItem(
            "n",
            Seq.empty,
            Seq(caseExpression(None, Some(literalString("Label2")), (varFor("x"), literalString("Label1"))))
          ),
          setPropertyItem("r", "prop", literalInt(1)),
          setLabelItem("m", Seq.empty, Seq(parameter("param", CTAny)), containsIs = true)
        )
      )
    )
  }

  test("SET map.n.prop = 1") {
    parsesTo[Clause](
      set_(
        Seq(
          SetPropertyItem(prop(prop(varFor("map"), "n"), "prop"), literalInt(1))(pos)
        )
      )
    )
  }

  test("SET list[0].prop = 1") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '.': expected '=' (line 1, column 12 (offset: 11))
            |"SET list[0].prop = 1"
            |            ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          set_(
            Seq(
              SetPropertyItem(prop(index(varFor("list"), 0), "prop"), literalInt(1))(pos)
            )
          )
        )
    }
  }

  test("SET list[0].n[1].prop = 1") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '.': expected '=' (line 1, column 12 (offset: 11))
            |"SET list[0].n[1].prop = 1"
            |            ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          set_(
            Seq(
              SetPropertyItem(prop(index(prop(index(varFor("list"), 0), "n"), 1), "prop"), literalInt(1))(pos)
            )
          )
        )
    }
  }

  test("SET map[\"prop\"] = 1") {
    parsesToWith[Clause](
      set_(
        Seq(
          SetDynamicPropertyItem(containerIndex(varFor("map"), literalString("prop")), literalInt(1))(pos)
        )
      ),
      comparePositions = false
    )
  }

  test("SET map.field[\"prop\"] = 1") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '[': expected an expression, '.' or '=' (line 1, column 14 (offset: 13))
            |"SET map.field["prop"] = 1"
            |              ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          set_(
            Seq(
              SetDynamicPropertyItem(
                containerIndex(prop(varFor("map"), "field"), literalString("prop")),
                literalInt(1)
              )(pos)
            )
          )
        )
    }
  }

  test("SET list[0][\"prop\"] = 1") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '[': expected '=' (line 1, column 12 (offset: 11))
            |"SET list[0]["prop"] = 1"
            |            ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          set_(
            Seq(
              SetDynamicPropertyItem(
                containerIndex(index(varFor("list"), 0), literalString("prop")),
                literalInt(1)
              )(pos)
            )
          )
        )
    }
  }

  test("SET list[0][\"n\"][1][\"prop\"] = 1") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '[': expected '=' (line 1, column 12 (offset: 11))
            |"SET list[0]["n"][1]["prop"] = 1"
            |            ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          set_(
            Seq(
              SetDynamicPropertyItem(
                containerIndex(
                  index(containerIndex(index(varFor("list"), 0), literalString("n")), 1),
                  literalString("prop")
                ),
                literalInt(1)
              )(pos)
            )
          )
        )
    }
  }

  test("SET list[0..1][\"prop\"] = 1") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '..': expected an expression or ']' (line 1, column 11 (offset: 10))
            |"SET list[0..1]["prop"] = 1"
            |           ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          set_(
            Seq(
              SetDynamicPropertyItem(
                containerIndex(sliceFull(varFor("list"), literalInt(0), literalInt(1)), literalString("prop")),
                literalInt(1)
              )(pos)
            )
          )
        )
    }
  }

  test("SET list[0..1] = 1") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '..': expected an expression or ']' (line 1, column 11 (offset: 10))
            |"SET list[0..1] = 1"
            |           ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.withSyntaxError(
          """Invalid input none property access expression, expected: static property access or dynamic property access. (line 1, column 9 (offset: 8))
            |"SET list[0..1] = 1"
            |         ^""".stripMargin
        )
    }
  }

  test("SET 123 = 1") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '=': expected '.' or '[' (line 1, column 9 (offset: 8))
            |"SET 123 = 1"
            |         ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.withSyntaxError(
          """Invalid input none property access expression, expected: static property access or dynamic property access. (line 1, column 5 (offset: 4))
            |"SET 123 = 1"
            |     ^""".stripMargin
        )
    }
  }

  test("SET (CASE WHEN true THEN r END).name = 'neo4j'") {
    parsesTo[Clause](
      set_(
        Seq(
          SetPropertyItem(prop(caseExpression((literalBoolean(true), varFor("r"))), "name"), literalString("neo4j"))(
            pos
          )
        )
      )
    )
  }

  test("SET (CASE WHEN true THEN r END)[toUpper(\"prop\")] = 'neo4j'") {
    parsesToWith[Clause](
      set_(
        Seq(
          SetDynamicPropertyItem(
            containerIndex(
              caseExpression((literalBoolean(true), varFor("r"))),
              function("toUpper", literalString("prop"))
            ),
            literalString("neo4j")
          )(
            pos
          )
        )
      ),
      comparePositions = false
    )
  }

  test("SET (listOfNodes[0]).prop = 'neo4j'") {
    parsesTo[Clause](
      set_(
        Seq(
          SetPropertyItem(
            prop(
              containerIndex(varFor("listOfNodes"), 0),
              "prop"
            ),
            literalString("neo4j")
          )(
            pos
          )
        )
      )
    )
  }

  test("SET listOfNodes[0].prop = 'neo4j'") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '.': expected '=' (line 1, column 19 (offset: 18))
            |"SET listOfNodes[0].prop = 'neo4j'"
            |                   ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          set_(
            Seq(
              SetPropertyItem(
                prop(
                  containerIndex(varFor("listOfNodes"), 0),
                  "prop"
                ),
                literalString("neo4j")
              )(
                pos
              )
            )
          )
        )
    }
  }

  test("SET listOfNodes[0][toUpper(\"prop\")] = 'neo4j'") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '[': expected '=' (line 1, column 19 (offset: 18))
            |"SET listOfNodes[0][toUpper("prop")] = 'neo4j'"
            |                   ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          set_(
            Seq(
              SetDynamicPropertyItem(
                containerIndex(
                  containerIndex(varFor("listOfNodes"), 0),
                  function("toUpper", literalString("prop"))
                ),
                literalString("neo4j")
              )(
                pos
              )
            )
          )
        )
    }
  }

  test("SET :A") {
    parsesIn[Clause] {
      case Cypher5 => _.withMessage(
          """Invalid input ':': expected an expression (line 1, column 5 (offset: 4))
            |"SET :A"
            |     ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.withMessage(
          """Invalid input ':': expected a variable name or an expression (line 1, column 5 (offset: 4))
            |"SET :A"
            |     ^""".stripMargin
        )
    }
  }

  test("SET IS A") {
    parsesIn[Clause] {
      case Cypher5 => _.withMessage(
          """Invalid input 'A': expected an expression, '+=', '.', ':', '=', 'IS' or '[' (line 1, column 8 (offset: 7))
            |"SET IS A"
            |        ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.withMessage(
          """Invalid input 'A': expected an expression, '+=', ':', '=' or 'IS' (line 1, column 8 (offset: 7))
            |"SET IS A"
            |        ^""".stripMargin
        )
    }
  }

  // Invalid mix of colon conjunction and IS, this will be disallowed in semantic checking

  test("SET n IS A:B") {
    parsesTo[Clause](
      set_(
        Seq(setLabelItem("n", Seq("A", "B"), containsIs = true))
      )
    )
  }

  test("SET n IS A, m:A:B") {
    parsesTo[Clause](
      set_(
        Seq(
          setLabelItem("n", Seq("A"), containsIs = true),
          setLabelItem("m", Seq("A", "B"))
        )
      )
    )
  }

  //  Invalid use of other label expression symbols than :

  test("SET n:A|B") {
    failsParsing[Statements]
  }

  test("SET n:!A") {
    failsParsing[Statements]
  }

  test("SET n:%") {
    failsParsing[Statements]
  }

  test("SET n:A&B") {
    failsParsing[Statements]
  }

  test("SET n IS A&B") {
    failsParsing[Statements]
  }
}
