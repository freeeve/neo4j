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
package org.neo4j.cypher.internal.ast.factory.expression

import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsing.ParserInTest
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.expressions.CaseExpression
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.InvalidNotEquals
import org.neo4j.cypher.internal.expressions.NFCNormalForm
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.IntegerType

class CaseExpressionParserTest extends AstParsingTestBase {

  test("CASE WHEN (e) THEN e ELSE null END") {
    parsesTo[Expression] {
      CaseExpression(
        None,
        List(varFor("e") -> varFor("e")),
        Some(nullLiteral)
      )(pos)
    }
  }

  test("CASE WHEN (e) THEN e END") {
    parsesTo[Expression] {
      CaseExpression(
        None,
        List(varFor("e") -> varFor("e")),
        None
      )(pos)
    }
  }

  test("CASE WHEN e=1 THEN 4 WHEN e=2 THEN 6 ELSE 7 END") {
    parsesTo[Expression] {
      CaseExpression(
        None,
        List(equals(varFor("e"), literalInt(1)) -> literalInt(4), equals(varFor("e"), literalInt(2)) -> literalInt(6)),
        Some(literalInt(7))
      )(pos)
    }
  }

  test("CASE a WHEN b THEN c WHEN d THEN e WHEN f THEN g ELSE h END") {
    parsesTo[Expression] {
      extendedCase(
        varFor("a"),
        Seq(
          equals(_, varFor("b")) -> varFor("c"),
          equals(_, varFor("d")) -> varFor("e"),
          equals(_, varFor("f")) -> varFor("g")
        ),
        Some(varFor("h"))
      )(pos)
    }
  }

  test("CASE when(e) WHEN (e) THEN e ELSE null END") {
    parsesTo[Expression] {
      extendedCase(
        function("when", varFor("e")),
        Seq(equals(_, varFor("e")) -> varFor("e")),
        Some(nullLiteral)
      )(pos)
    }
  }

  // Copied from legacy test
  test("some more case expressions") {
    "CASE 1 WHEN 1 THEN 'ONE' END" should parseTo[Expression](
      extendedCase(literal(1), Seq(equals(_, literal(1)) -> literal("ONE")))()
    )
    """CASE 1
         WHEN 1 THEN 'ONE'
         WHEN 2 THEN 'TWO'
       END""" should parseTo[Expression](
      extendedCase(
        literal(1),
        Seq(
          equals(_, literal(1)) -> literal("ONE"),
          equals(_, literal(2)) -> literal("TWO")
        )
      )()
    )
    """CASE 1
           WHEN 1 THEN 'ONE'
           WHEN 2 THEN 'TWO'
           ELSE 'DEFAULT'
       END""" should parseTo[Expression](
      extendedCase(
        literal(1),
        Seq(
          equals(_, literal(1)) -> literal("ONE"),
          equals(_, literal(2)) -> literal("TWO")
        ),
        Some(literal("DEFAULT"))
      )()
    )
    "CASE WHEN true THEN 'ONE' END" should parseTo[Expression](
      caseExpression(None, None, literal(true) -> literal("ONE"))
    )
    """CASE
           WHEN 1=2     THEN 'ONE'
           WHEN 2='apa' THEN 'TWO'
         END""" should parseTo[Expression](
      caseExpression(
        None,
        None,
        (equals(literal(1), literal(2)), literal("ONE")),
        (equals(literal(2), literal("apa")), literal("TWO"))
      )
    )
    """CASE
           WHEN 1=2     THEN 'ONE'
           WHEN 2='apa' THEN 'TWO'
                        ELSE 'OTHER'
         END""" should parseTo[Expression](caseExpression(
      None,
      Some(literal("OTHER")),
      (equals(literal(1), literal(2)), literal("ONE")),
      (equals(literal(2), literal("apa")), literal("TWO"))
    ))
  }

  test("CASE n.eyes WHEN \"blue\" THEN 1 WHEN \"brown\" THEN 2 ELSE 3 END") {
    parsesTo[Expression] {
      extendedCase(
        prop("n", "eyes"),
        Seq(
          equals(_, literalString("blue")) -> literalInt(1),
          equals(_, literalString("brown")) -> literalInt(2)
        ),
        Some(literalInt(3))
      )(pos)
    }
  }

  test("CASE n.eyes WHEN \"blue\" THEN 1 WHEN \"brown\" THEN 2 END") {
    parsesTo[Expression] {
      extendedCase(
        prop("n", "eyes"),
        Seq(
          equals(_, literalString("blue")) -> literalInt(1),
          equals(_, literalString("brown")) -> literalInt(2)
        )
      )(pos)
    }
  }

  test("CASE n.eyes WHEN \"blue\", \"green\", \"brown\" THEN 1 WHEN \"red\" THEN 2 END") {
    parsesTo[Expression] {
      extendedCase(
        prop("n", "eyes"),
        Seq(
          equals(_, literalString("blue")) -> literalInt(1),
          equals(_, literalString("green")) -> literalInt(1),
          equals(_, literalString("brown")) -> literalInt(1),
          equals(_, literalString("red")) -> literalInt(2)
        )
      )(pos)
    }
  }

  test(
    """
      |CASE n.eyes
      | WHEN IS NULL THEN 1
      | WHEN IS TYPED INTEGER THEN 2
      | WHEN IS NORMALIZED THEN 3
      | ELSE 4
      |END""".stripMargin
  ) {
    parsesTo[Expression] {
      extendedCase(
        prop("n", "eyes"),
        Seq(
          isNull(_) -> literalInt(1),
          isTyped(_, IntegerType(isNullable = true)(pos)) -> literalInt(2),
          isNormalized(_, NFCNormalForm) -> literalInt(3)
        ),
        Some(literalInt(4))
      )(pos)
    }
  }

  test(
    """
      |CASE n.eyes
      | WHEN > 2, = 1, 5 THEN 1
      | WHEN STARTS WITH "gre", ENDS WITH "en" THEN 3
      | ELSE 4
      |END""".stripMargin
  ) {
    parsesTo[Expression] {
      extendedCase(
        prop("n", "eyes"),
        Seq(
          greaterThan(_, literalInt(2)) -> literalInt(1),
          equals(_, literalInt(1)) -> literalInt(1),
          equals(_, literalInt(5)) -> literalInt(1),
          startsWith(_, literalString("gre")) -> literalInt(3),
          endsWith(_, literalString("en")) -> literalInt(3)
        ),
        Some(literalInt(4))
      )(pos)
    }
  }

  test(
    """
      |CASE n.eyes
      | WHEN n.eyes > 2 THEN 1
      | ELSE 4
      |END""".stripMargin
  ) {
    parsesTo[Expression] {
      extendedCase(
        prop("n", "eyes"),
        Seq(equals(_, greaterThan(prop("n", "eyes"), literalInt(2))) -> literalInt(1)),
        Some(literalInt(4))
      )(pos)
    }
  }

  test(
    """CASE n.eyes
      | WHEN in[0] THEN 1
      | ELSE 4
      |END""".stripMargin
  ) {
    parsesIn[Expression] { parser =>
      _.toAst(
        extendedCase(
          prop("n", "eyes"),
          Seq(parser match {
            case Cypher5 => equals(_, containerIndex(varFor("in"), literalInt(0))) -> literalInt(1)
            case _       => in(_, listOfInt(0)) -> literalInt(1)
          }),
          Some(literalInt(4))
        )(pos)
      )
    }
  }

  test(
    """CASE 2
      |  WHEN contains + 1 THEN 'contains'
      |  ELSE 'else'
      |END""".stripMargin
  ) {
    parsesIn[Expression] { parser =>
      _.toAst(
        extendedCase(
          literalInt(2),
          Seq(parser match {
            case Cypher5 => equals(_, add(varFor("contains"), literalInt(1))) -> literalString("contains")
            case _       => contains(_, unaryAdd(literalInt(1))) -> literalString("contains")
          }),
          Some(literalString("else"))
        )(pos)
      )
    }
  }

  // Test all type predicate expressions parse as expected
  test(
    """CASE 1
      |  WHEN IS TYPED INT THEN 1
      |  WHEN IS NOT TYPED INT THEN 2
      |  WHEN IS :: INT THEN 3
      |  WHEN :: INT THEN 4
      |  ELSE 'else'
      |END""".stripMargin
  ) {
    def expected(isNotTypedExp: Expression => Expression) =
      extendedCase(
        literalInt(1),
        Seq(
          isTyped(_, IntegerType(isNullable = true)(pos)) -> literalInt(1),
          isNotTyped(_, IntegerType(isNullable = true)(pos)) -> literalInt(2),
          isNotTypedExp(_) -> literalInt(3),
          isTyped(_, IntegerType(isNullable = true)(pos), withDoubleColonOnly = true) -> literalInt(4)
        ),
        Some(literalString("else"))
      )(pos)
    parsesIn[Expression] {
      case Cypher5 => _.toAst(expected(equals(
          _,
          isTyped(varFor("IS"), IntegerType(isNullable = true)(pos), withDoubleColonOnly = true)
        )))
      case _ => _.toAst(expected(isTyped(_, IntegerType(isNullable = true)(pos), withDoubleColonOnly = false)))
    }
  }

  test("RETURN CASE when(v1) + 1 WHEN THEN v2 ELSE null END") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'v2': expected an expression, ',' or 'THEN' (line 1, column 36 (offset: 35))
        |"RETURN CASE when(v1) + 1 WHEN THEN v2 ELSE null END"
        |                                    ^""".stripMargin
    )
  }

  test("RETURN CASE ELSE null END") {
    parseIn[Statements] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input 'ELSE': expected an expression, 'FOREACH', ',', 'AS', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 13 (offset: 12))
            |"RETURN CASE ELSE null END"
            |             ^""".stripMargin
        )
      case _ => _.withSyntaxError(
          """Invalid input 'ELSE': expected an expression, 'FOREACH', ',', 'AS', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOR', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 13 (offset: 12))
            |"RETURN CASE ELSE null END"
            |             ^""".stripMargin
        )
    }
  }

  test("RETURN CASE WHEN THEN v2 ELSE null END") {
    parseIn[Statements] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input 'WHEN': expected an expression, 'FOREACH', ',', 'AS', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 13 (offset: 12))
            |"RETURN CASE WHEN THEN v2 ELSE null END"
            |             ^""".stripMargin
        )
      case _ => _.withSyntaxError(
          """Invalid input 'WHEN': expected an expression, 'FOREACH', ',', 'AS', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOR', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 13 (offset: 12))
            |"RETURN CASE WHEN THEN v2 ELSE null END"
            |             ^""".stripMargin
        )
    }
  }

  test("RETURN CASE WHEN true, false THEN v2 ELSE null END") {
    parseIn[Statements] {
      case Cypher5 => _.withSyntaxError(
          """Invalid input 'WHEN': expected an expression, 'FOREACH', ',', 'AS', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 13 (offset: 12))
            |"RETURN CASE WHEN true, false THEN v2 ELSE null END"
            |             ^""".stripMargin
        )
      case _ => _.withSyntaxError(
          """Invalid input 'WHEN': expected an expression, 'FOREACH', ',', 'AS', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOR', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF> (line 1, column 13 (offset: 12))
            |"RETURN CASE WHEN true, false THEN v2 ELSE null END"
            |             ^""".stripMargin
        )
    }
  }

  test("RETURN CASE n WHEN true, false, THEN 1 ELSE null END") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input '1': expected an expression, ',' or 'THEN' (line 1, column 38 (offset: 37))
        |"RETURN CASE n WHEN true, false, THEN 1 ELSE null END"
        |                                      ^""".stripMargin
    )
  }

  test("case expression combinations") {
    val in = varFor("input")
    val altsPart2 = Seq[(String, (ParserInTest, Expression) => Expression)](
      "=~ '.*'" -> ((_, lhs) => regex(lhs, literal(".*"))),
      "starts with 'zzz'" -> ((_, lhs) => startsWith(lhs, literal("zzz"))),
      "ends with 'zzz'" -> ((_, lhs) => endsWith(lhs, literal("zzz"))),
      "is null" -> ((_, lhs) => isNull(lhs)),
      "is not null" -> ((_, lhs) => isNotNull(lhs)),
      "is typed Int" -> ((_, lhs) => isTyped(lhs, CTInteger)),
      "is not typed Int" -> ((_, lhs) => isNotTyped(lhs, CTInteger)),
      ":: Int" -> ((parserInTest, lhs) =>
        parserInTest match {
          case Cypher5 => isTyped(lhs, CTInteger, withDoubleColonOnly = true)
          case _       => isTyped(lhs, CTInteger, withDoubleColonOnly = false)
        }
      ),
      "= 'x'" -> ((_, lhs) => equals(lhs, literal("x"))),
      "<> 'x'" -> ((_, lhs) => notEquals(lhs, literal("x"))),
      "!= 'x'" -> ((_, lhs) => InvalidNotEquals(lhs, literal("x"))(pos)),
      "< 0" -> ((_, lhs) => lessThan(lhs, literal(0))),
      "> 0" -> ((_, lhs) => greaterThan(lhs, literal(0))),
      "<= 0" -> ((_, lhs) => lessThanOrEqual(lhs, literal(0))),
      ">= 0" -> ((_, lhs) => greaterThanOrEqual(lhs, literal(0)))
    )
    val altsExpression = altsPart2 ++ Seq[(String, (ParserInTest, Expression) => Expression)](
      "a" -> ((_, lhs) => equals(lhs, varFor("a"))),
      "a.p" -> ((_, lhs) => equals(lhs, prop("a", "p"))),
      "a[0]" -> ((_, lhs) => equals(lhs, containerIndex(varFor("a"), 0))),
      "false" -> ((_, lhs) => equals(lhs, literal(false)))
    )

    altsExpression.foreach {
      case (cypherWhen, expF) =>
        s"case ${in.name} when $cypherWhen then true end" should parseIn[Expression](parserInTest =>
          _.toAst(extendedCase(in, Seq(expF(parserInTest, _) -> trueLiteral))())
        )
    }

    altsPart2.foreach {
      case (cypherWhen, expF) =>
        s"case when ${in.name} $cypherWhen then true end" should parseIn[Expression](parserInTest =>
          _.toAst(caseExpression(None, None, expF(parserInTest, in) -> trueLiteral))
        )
    }

    for {
      (aCypher, aExpF) <- altsExpression
      (bCypher, bExpF) <- altsExpression
    } {
      s"""case ${in.name}
         |when $aCypher then 'a'
         |when $bCypher then 'b'
         |else 'c'
         |end
         |""".stripMargin should parseIn[Expression](parserInTest =>
        _.toAst(
          extendedCase(
            in,
            Seq(
              aExpF(parserInTest, _) -> literal("a"),
              bExpF(parserInTest, _) -> literal("b")
            ),
            Some(literal("c"))
          )()
        )
      )
      s"""case ${in.name}
         |when $aCypher, $bCypher then 'yes'
         |end
         |""".stripMargin should parseIn[Expression](parserInTest =>
        _.toAst(
          extendedCase(
            in,
            Seq(aExpF(parserInTest, _) -> literal("yes"), bExpF(parserInTest, _) -> literal("yes"))
          )()
        )
      )
    }

    s"""case ${in.name}
       |when
       |  ${altsExpression.map(_._1).mkString(",\n  ")}
       |then
       |  true
       |end
       |""".stripMargin should parseIn[Expression](parserInTest =>
      _.toAst(
        extendedCase(in, altsExpression.map { case (_, f) => f(parserInTest, _) -> trueLiteral })()
      )
    )
  }

  private def extendedCase(
    candidate: Expression,
    alts: Seq[Expression => (Expression, Expression)],
    default: Option[Expression] = None
  )(p: InputPosition = pos) =
    CaseExpression(
      Some(candidate),
      alts.map(_.apply(candidate)).toIndexedSeq,
      default
    )(p)
}
