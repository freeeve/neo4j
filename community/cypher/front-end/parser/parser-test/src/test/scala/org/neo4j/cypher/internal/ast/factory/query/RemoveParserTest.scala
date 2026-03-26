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
import org.neo4j.cypher.internal.ast.RemoveDynamicPropertyItem
import org.neo4j.cypher.internal.ast.RemovePropertyItem
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.util.symbols.CTAny

class RemoveParserTest extends AstParsingTestBase {

  test("REMOVE n:A") {
    parsesTo[Clause](
      remove(
        Seq(removeLabelItem("n", Seq("A")))
      )
    )
  }

  test("REMOVE n IS A") {
    parsesTo[Clause](
      remove(
        Seq(removeLabelItem("n", Seq("A"), containsIs = true))
      )
    )
  }

  test("REMOVE n:A:B:C") {
    parsesTo[Clause](
      remove(
        Seq(removeLabelItem("n", Seq("A", "B", "C")))
      )
    )
  }

  test("REMOVE n:A, n:B") {
    parsesTo[Clause](
      remove(
        Seq(removeLabelItem("n", Seq("A")), removeLabelItem("n", Seq("B")))
      )
    )
  }

  test("REMOVE n IS A, n IS B") {
    parsesTo[Clause](
      remove(
        Seq(removeLabelItem("n", Seq("A"), containsIs = true), removeLabelItem("n", Seq("B"), containsIs = true))
      )
    )
  }

  test("REMOVE n IS A, n:B") {
    parsesTo[Clause](
      remove(
        Seq(removeLabelItem("n", Seq("A"), containsIs = true), removeLabelItem("n", Seq("B")))
      )
    )
  }

  test("REMOVE n:A, r.prop, m IS B") {
    parsesTo[Clause](
      remove(
        Seq(
          removeLabelItem("n", Seq("A")),
          removePropertyItem("r", "prop"),
          removeLabelItem("m", Seq("B"), containsIs = true)
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
    test(s"REMOVE n:$labelNameReserved, n IS $labelNameReserved") {
      parsesTo[Clause](
        remove(
          Seq(
            removeLabelItem("n", Seq(labelNameReserved)),
            removeLabelItem("n", Seq(labelNameReserved), containsIs = true)
          )
        )
      )
    }
  }

  // Invalid mix of colon conjunction and IS, this will be disallowed in semantic checking

  test("REMOVE n IS A:B") {
    parsesTo[Clause](
      remove(
        Seq(removeLabelItem("n", Seq("A", "B"), containsIs = true))
      )
    )
  }

  test("REMOVE n IS A, m:A:B") {
    parsesTo[Clause](
      remove(
        Seq(
          removeLabelItem("n", Seq("A"), containsIs = true),
          removeLabelItem("m", Seq("A", "B"))
        )
      )
    )
  }

  test("REMOVE map.n.prop") {
    parsesTo[Clause](
      remove(
        Seq(
          RemovePropertyItem(prop(prop(varFor("map"), "n"), "prop"))(pos)
        )
      )
    )
  }

  test("REMOVE list[0].prop") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '.': expected ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 15 (offset: 14))
            |"REMOVE list[0].prop"
            |               ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          remove(
            Seq(
              RemovePropertyItem(prop(index(varFor("list"), 0), "prop"))(pos)
            )
          )
        )
    }
  }

  test("REMOVE list[0].n[1].prop") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '.': expected ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 15 (offset: 14))
            |"REMOVE list[0].n[1].prop"
            |               ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          remove(
            Seq(
              RemovePropertyItem(prop(index(prop(index(varFor("list"), 0), "n"), 1), "prop"))(pos)
            )
          )
        )
    }
  }

  test("REMOVE map[prop]") {
    parsesToWith[Clause](
      remove(
        Seq(
          RemoveDynamicPropertyItem(containerIndex(varFor("map"), varFor("prop")))(pos)
        )
      ),
      comparePositions = false
    )
  }

  test("REMOVE map.field[\"prop\"]") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '[': expected an expression, ',', '.', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 17 (offset: 16))
            |"REMOVE map.field["prop"]"
            |                 ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          remove(
            Seq(
              RemoveDynamicPropertyItem(containerIndex(prop(varFor("map"), "field"), literalString("prop")))(pos)
            )
          )
        )
    }
  }

  test("REMOVE list[0][\"prop\"]") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '[': expected ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 15 (offset: 14))
            |"REMOVE list[0]["prop"]"
            |               ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          remove(
            Seq(
              RemoveDynamicPropertyItem(containerIndex(index(varFor("list"), 0), literalString("prop")))(pos)
            )
          )
        )
    }
  }

  test("REMOVE list[0][\"n\"][1][\"prop\"]") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '[': expected ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 15 (offset: 14))
            |"REMOVE list[0]["n"][1]["prop"]"
            |               ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          remove(
            Seq(
              RemoveDynamicPropertyItem(
                containerIndex(
                  index(containerIndex(index(varFor("list"), 0), literalString("n")), 1),
                  literalString("prop")
                )
              )(pos)
            )
          )
        )
    }
  }

  test("REMOVE list[0..1][\"prop\"]") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '..': expected an expression or ']' (line 1, column 14 (offset: 13))
            |"REMOVE list[0..1]["prop"]"
            |              ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          remove(
            Seq(
              RemoveDynamicPropertyItem(
                containerIndex(sliceFull(varFor("list"), literalInt(0), literalInt(1)), literalString("prop"))
              )(pos)
            )
          )
        )
    }
  }

  test("REMOVE list[0..1]") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '..': expected an expression or ']' (line 1, column 14 (offset: 13))
            |"REMOVE list[0..1]"
            |              ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.withSyntaxError(
          """Invalid input none property access expression, expected: static property access or dynamic property access. (line 1, column 12 (offset: 11))
            |"REMOVE list[0..1]"
            |            ^""".stripMargin
        )
    }
  }

  test("REMOVE 123") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '': expected '.' or '[' (line 1, column 11 (offset: 10))
            |"REMOVE 123"
            |           ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.withSyntaxError(
          """Invalid input none property access expression, expected: static property access or dynamic property access. (line 1, column 8 (offset: 7))
            |"REMOVE 123"
            |        ^""".stripMargin
        )
    }
  }

  test("REMOVE (CASE WHEN true THEN r END).name") {
    parsesTo[Clause](
      remove(
        Seq(
          RemovePropertyItem(prop(caseExpression((literalBoolean(true), varFor("r"))), "name"))(pos)
        )
      )
    )
  }

  test("REMOVE (CASE WHEN true THEN r END)[toUpper(\"prop\")]") {
    parsesToWith[Clause](
      remove(
        Seq(
          RemoveDynamicPropertyItem(
            containerIndex(
              caseExpression((literalBoolean(true), varFor("r"))),
              function("toUpper", literalString("prop"))
            )
          )(pos)
        )
      ),
      comparePositions = false
    )
  }

  test("REMOVE (listOfNodes[0])[toUpper(\"prop\")]") {
    parsesToWith[Clause](
      remove(
        Seq(
          RemoveDynamicPropertyItem(
            containerIndex(
              containerIndex(varFor("listOfNodes"), 0),
              function("toUpper", literalString("prop"))
            )
          )(pos)
        )
      ),
      comparePositions = false
    )
  }

  test("REMOVE listOfNodes[0].prop") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '.': expected ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 22 (offset: 21))
            |"REMOVE listOfNodes[0].prop"
            |                      ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          remove(
            Seq(
              RemovePropertyItem(
                prop(
                  containerIndex(varFor("listOfNodes"), 0),
                  "prop"
                )
              )(pos)
            )
          )
        )
    }
  }

  test("REMOVE listOfNodes[0][toUpper(\"prop\")]") {
    parsesIn[Clause] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input '[': expected ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 22 (offset: 21))
            |"REMOVE listOfNodes[0][toUpper("prop")]"
            |                      ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.toAst(
          remove(
            Seq(
              RemoveDynamicPropertyItem(
                containerIndex(
                  containerIndex(varFor("listOfNodes"), 0),
                  function("toUpper", literalString("prop"))
                )
              )(pos)
            )
          )
        )
    }
  }

  //  Invalid use of other label expression symbols than :

  test("REMOVE n:A|B") {
    parseIn[Statements] {
      case Cypher5 => _.withMessage(
          """Invalid input '|': expected 'FOREACH', ',', ':', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 11 (offset: 10))
            |"REMOVE n:A|B"
            |           ^""".stripMargin
        )
      case _ => _.withMessage(
          """Invalid input '|': expected 'FOREACH', ',', ':', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOR', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 11 (offset: 10))
            |"REMOVE n:A|B"
            |           ^""".stripMargin
        )
    }
  }

  test("REMOVE n:!A") {
    failsParsing[Statements].withMessage(
      """Invalid input '!': expected an identifier or '$' (line 1, column 10 (offset: 9))
        |"REMOVE n:!A"
        |          ^""".stripMargin
    )
  }

  test("REMOVE n:%") {
    failsParsing[Statements].withMessage(
      """Invalid input '%': expected an identifier or '$' (line 1, column 10 (offset: 9))
        |"REMOVE n:%"
        |          ^""".stripMargin
    )
  }

  test("REMOVE n:A&B") {
    parseIn[Statements] {
      case Cypher5 => _.withMessage(
          """Invalid input '&': expected 'FOREACH', ',', ':', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 11 (offset: 10))
            |"REMOVE n:A&B"
            |           ^""".stripMargin
        )
      case _ => _.withMessage(
          """Invalid input '&': expected 'FOREACH', ',', ':', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOR', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 11 (offset: 10))
            |"REMOVE n:A&B"
            |           ^""".stripMargin
        )
    }
  }

  test("REMOVE n IS A&B") {
    parseIn[Statements] {
      case Cypher5 => _.withMessage(
          """Invalid input '&': expected 'FOREACH', ',', ':', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 14 (offset: 13))
            |"REMOVE n IS A&B"
            |              ^""".stripMargin
        )
      case _ => _.withMessage(
          """Invalid input '&': expected 'FOREACH', ',', ':', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOR', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 14 (offset: 13))
            |"REMOVE n IS A&B"
            |              ^""".stripMargin
        )
    }
  }

  test("REMOVE :A") {
    parsesIn[Clause] {
      case Cypher5 => _.withMessage(
          """Invalid input ':': expected an expression (line 1, column 8 (offset: 7))
            |"REMOVE :A"
            |        ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.withMessage(
          """Invalid input ':': expected a variable name or an expression (line 1, column 8 (offset: 7))
            |"REMOVE :A"
            |        ^""".stripMargin
        )
    }
  }

  test("REMOVE IS A") {
    parsesIn[Clause] {
      case Cypher5 => _.withMessage(
          """Invalid input 'A': expected an expression, '.', ':', 'IS' or '[' (line 1, column 11 (offset: 10))
            |"REMOVE IS A"
            |           ^""".stripMargin
        )
      // ≥ Cypher 25
      case _ => _.withMessage(
          """Invalid input none property access expression, expected: static property access or dynamic property access. (line 1, column 8 (offset: 7))
            |"REMOVE IS A"
            |        ^""".stripMargin
        )
    }
  }

  // Dynamic Labels

  test("REMOVE n:$(A)") {
    parsesTo[Clause](
      remove(
        Seq(removeLabelItem("n", Seq.empty, dynamicLabels = Seq(varFor("A"))))
      )
    )
  }

  test("REMOVE n IS $(A)") {
    parsesTo[Clause](
      remove(
        Seq(removeLabelItem("n", Seq.empty, dynamicLabels = Seq(varFor("A")), containsIs = true))
      )
    )
  }

  test("REMOVE n:$(A):B:$(C)") {
    parsesTo[Clause](
      remove(
        Seq(removeLabelItem("n", Seq("B"), dynamicLabels = Seq(varFor("A"), varFor("C"))))
      )
    )
  }

  test("REMOVE n:$($param), n:$(B)") {
    parsesTo[Clause](
      remove(
        Seq(
          removeLabelItem("n", Seq.empty, dynamicLabels = Seq(parameter("param", CTAny))),
          removeLabelItem("n", Seq.empty, dynamicLabels = Seq(varFor("B")))
        )
      )
    )
  }

  test("REMOVE n IS $(\"A\"), n IS B") {
    parsesTo[Clause](
      remove(
        Seq(
          removeLabelItem("n", Seq.empty, Seq(literalString("A")), containsIs = true),
          removeLabelItem("n", Seq("B"), containsIs = true)
        )
      )
    )
  }

  test("REMOVE n IS $(a || b), n:$(b || c)") {
    parsesTo[Clause](
      remove(
        Seq(
          removeLabelItem("n", Seq.empty, Seq(concatenate(varFor("a"), varFor("b"))), containsIs = true),
          removeLabelItem("n", Seq.empty, Seq(concatenate(varFor("b"), varFor("c"))))
        )
      )
    )
  }

  test("REMOVE n:$(CASE WHEN x THEN \"Label1\" ELSE \"Label2\" END), r.prop, m IS $($param)") {
    parsesTo[Clause](
      remove(
        Seq(
          removeLabelItem(
            "n",
            Seq.empty,
            Seq(caseExpression(None, Some(literalString("Label2")), (varFor("x"), literalString("Label1"))))
          ),
          removePropertyItem("r", "prop"),
          removeLabelItem("m", Seq.empty, Seq(parameter("param", CTAny)), containsIs = true)
        )
      )
    )
  }
}
