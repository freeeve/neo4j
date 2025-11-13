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
import org.neo4j.cypher.internal.ast.PartQuery
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.util.symbols.CTFloat
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.CTStringNotNull
import org.neo4j.cypher.internal.util.symbols.ClosedDynamicUnionType

class DefineLocalProcedureTest extends AstParsingTestBase {

  test(
    """DEFINE PROCEDURE bar() {
      |  RETURN 1 AS one
      |}
      |
      |CALL bar()
      |RETURN one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localProcedureDefinition(Seq(), "bar").body(
          return_(
            aliasedReturnItem(literalInt(1), "one")
          )
        )
      )(
        call(Seq(), "bar"),
        return_(returnItem(varFor("one"), "one"))
      )
    )
  }

  test(
    """DEFINE PROCEDURE wrong() = 1
      |
      |CALL wrong()
      |RETURN one""".stripMargin
  ) {
    failsParsing[Statement]
  }

  test(
    """DEFINE PROCEDURE foo.bar() {
      |  RETURN 1 AS one
      |}
      |
      |CALL foo.bar()
      |RETURN one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localProcedureDefinition(Seq("foo"), "bar").body(
          return_(
            aliasedReturnItem(literalInt(1), "one")
          )
        )
      )(
        call(Seq("foo"), "bar"),
        return_(returnItem(varFor("one"), "one"))
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.bar(x) {
      |  RETURN x + 1 AS one
      |}
      |
      |CALL foo.bar(1)
      |RETURN one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localProcedureDefinition(Seq("foo"), "bar", localFieldSignature("x")).body(
          return_(
            aliasedReturnItem(add(varFor("x"), literalInt(1)), "one")
          )
        )
      )(
        call(Seq("foo"), "bar", Some(Seq(literalInt(1)))),
        return_(returnItem(varFor("one"), "one"))
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.bar(x, y) {
      |  RETURN x + 1 * y AS one
      |}
      |
      |CALL foo.bar(1, 2)
      |RETURN one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localProcedureDefinition(Seq("foo"), "bar", localFieldSignature("x"), localFieldSignature("y")).body(
          return_(
            aliasedReturnItem(add(varFor("x"), multiply(literalInt(1), varFor("y"))), "one")
          )
        )
      )(
        call(Seq("foo"), "bar", Some(Seq(literalInt(1), literalInt(2)))),
        return_(returnItem(varFor("one"), "one"))
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.bar(a1, b2, c3, d4, e5, f6, g7, h8, i9, j10, k11, l12, m13, n14, o15, p16, q17, r18, s19, t20, u21, v22, w23, x24, y25, z26) {
      |  RETURN 1 AS one
      |}
      |
      |CALL foo.bar(1, "two", 3.14, true, [5, 5, 5], null, {a: 7}, 4 + 4, (9), 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26)
      |RETURN one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localProcedureDefinition(
          Seq("foo"),
          "bar",
          localFieldSignature("a1"),
          localFieldSignature("b2"),
          localFieldSignature("c3"),
          localFieldSignature("d4"),
          localFieldSignature("e5"),
          localFieldSignature("f6"),
          localFieldSignature("g7"),
          localFieldSignature("h8"),
          localFieldSignature("i9"),
          localFieldSignature("j10"),
          localFieldSignature("k11"),
          localFieldSignature("l12"),
          localFieldSignature("m13"),
          localFieldSignature("n14"),
          localFieldSignature("o15"),
          localFieldSignature("p16"),
          localFieldSignature("q17"),
          localFieldSignature("r18"),
          localFieldSignature("s19"),
          localFieldSignature("t20"),
          localFieldSignature("u21"),
          localFieldSignature("v22"),
          localFieldSignature("w23"),
          localFieldSignature("x24"),
          localFieldSignature("y25"),
          localFieldSignature("z26")
        ).body(
          return_(
            aliasedReturnItem(literalInt(1), "one")
          )
        )
      )(
        call(
          Seq("foo"),
          "bar",
          Some(Seq(
            literalInt(1),
            literalString("two"),
            literalFloat(3.14),
            trueLiteral,
            listOfInt(5, 5, 5),
            nullLiteral,
            mapOfInt("a" -> 7),
            add(literalInt(4), literalInt(4)),
            literalInt(9),
            literalInt(10),
            literalInt(11),
            literalInt(12),
            literalInt(13),
            literalInt(14),
            literalInt(15),
            literalInt(16),
            literalInt(17),
            literalInt(18),
            literalInt(19),
            literalInt(20),
            literalInt(21),
            literalInt(22),
            literalInt(23),
            literalInt(24),
            literalInt(25),
            literalInt(26)
          ))
        ),
        return_(returnItem(varFor("one"), "one"))
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.bar(x, y = 1) {
      |  RETURN x + 1 * y AS one
      |}
      |
      |CALL foo.bar(1)
      |RETURN one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localProcedureDefinition(
          Seq("foo"),
          "bar",
          localFieldSignature("x"),
          localFieldSignature("y", literalInt(1))
        ).body(
          return_(
            aliasedReturnItem(add(varFor("x"), multiply(literalInt(1), varFor("y"))), "one")
          )
        )
      )(
        call(Seq("foo"), "bar", Some(Seq(literalInt(1)))),
        return_(returnItem(varFor("one"), "one"))
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.bar(x = 0, y = 1) {
      |  RETURN x + 1 * y AS one
      |}
      |
      |CALL foo.bar()
      |RETURN one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localProcedureDefinition(
          Seq("foo"),
          "bar",
          localFieldSignature("x", literalInt(0)),
          localFieldSignature("y", literalInt(1))
        ).body(
          return_(
            aliasedReturnItem(add(varFor("x"), multiply(literalInt(1), varFor("y"))), "one")
          )
        )
      )(
        call(Seq("foo"), "bar"),
        return_(returnItem(varFor("one"), "one"))
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.bar(x = 0, y) {
      |  RETURN x + 1 * y AS one
      |}
      |
      |CALL foo.bar(1)
      |RETURN one""".stripMargin
  ) {
    // NOTE: This query is invalid and needs to filtered out in semantic checking
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localProcedureDefinition(
          Seq("foo"),
          "bar",
          localFieldSignature("x", literalInt(0)),
          localFieldSignature("y")
        ).body(
          return_(
            aliasedReturnItem(add(varFor("x"), multiply(literalInt(1), varFor("y"))), "one")
          )
        )
      )(
        call(Seq("foo"), "bar", Some(Seq(literalInt(1)))),
        return_(returnItem(varFor("one"), "one"))
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.bar(x :: INT, y :: INT | FLOAT = 1) :: (one :: INT | FLOAT) {
      |  RETURN x + 1 * y AS one
      |}
      |
      |CALL foo.bar(1)
      |RETURN one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localProcedureDefinition(
          Seq("foo"),
          "bar",
          localFieldSignature("x", CTInteger),
          localFieldSignature("y", ClosedDynamicUnionType(Set(CTInteger, CTFloat))(pos), literalInt(1))
        ).out(
          localFieldSignature("one", ClosedDynamicUnionType(Set(CTInteger, CTFloat))(pos))
        ).body(
          return_(
            aliasedReturnItem(add(varFor("x"), multiply(literalInt(1), varFor("y"))), "one")
          )
        )
      )(
        call(Seq("foo"), "bar", Some(Seq(literalInt(1)))),
        return_(returnItem(varFor("one"), "one"))
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.one() {
      |  RETURN 1 AS one
      |}
      |DEFINE PROCEDURE foo.two() {
      |  RETURN 2 AS two
      |}
      |
      |CALL foo.one()
      |CALL foo.two()
      |RETURN one, two""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localProcedureDefinition(Seq("foo"), "one").body(
          return_(
            aliasedReturnItem(literalInt(1), "one")
          )
        ),
        localProcedureDefinition(Seq("foo"), "two").body(
          return_(
            aliasedReturnItem(literalInt(2), "two")
          )
        )
      )(
        call(Seq("foo"), "one"),
        call(Seq("foo"), "two"),
        return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
      )
    )
  }

  test(
    """DEFINE PROCEDURE oneTwo() {
      |  DEFINE PROCEDURE foo.one() {
      |    RETURN 1 AS one
      |  }
      |  DEFINE PROCEDURE foo.two() {
      |    RETURN 2 AS two
      |  }
      |
      |  CALL foo.one()
      |  CALL foo.two()
      |  RETURN one, two
      |}
      |
      |CALL oneTwo() YIELD one, two
      |RETURN one, two""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localProcedureDefinition("oneTwo").body(
          singleQueryWithLocalDefinitions(
            localProcedureDefinition("foo.one").body(
              return_(
                aliasedReturnItem(literalInt(1), "one")
              )
            ),
            localProcedureDefinition("foo.two").body(
              return_(
                aliasedReturnItem(literalInt(2), "two")
              )
            )
          )(
            call(Seq("foo"), "one"),
            call(Seq("foo"), "two"),
            return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
          )
        )
      )(
        call(Seq(), "oneTwo", Some(Seq()), Some(Seq(varFor("one"), varFor("two")))),
        return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
      )
    )
  }

  test(
    """{
      |  DEFINE PROCEDURE foo.one() {
      |    RETURN 1 AS one
      |  }
      |  DEFINE PROCEDURE foo.two() {
      |    RETURN 2 AS two
      |  }
      |
      |  CALL foo.one()
      |  CALL foo.two()
      |  RETURN one, two
      |}""".stripMargin
  ) {
    parsesToStatement(
      topLevelBraces(
        singleQueryWithLocalDefinitions(
          localProcedureDefinition(Seq("foo"), "one").body(
            return_(
              aliasedReturnItem(literalInt(1), "one")
            )
          ),
          localProcedureDefinition(Seq("foo"), "two").body(
            return_(
              aliasedReturnItem(literalInt(2), "two")
            )
          )
        )(
          call(Seq("foo"), "one"),
          call(Seq("foo"), "two"),
          return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
        )
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.one() {
      |  RETURN 1 AS one
      |}
      |DEFINE PROCEDURE foo.two() {
      |  RETURN 2 AS two
      |}
      |{
      |  CALL foo.one()
      |  CALL foo.two()
      |  RETURN one, two
      |}""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localProcedureDefinition(Seq("foo"), "one").body(
          return_(
            aliasedReturnItem(literalInt(1), "one")
          )
        ),
        localProcedureDefinition(Seq("foo"), "two").body(
          return_(
            aliasedReturnItem(literalInt(2), "two")
          )
        )
      )(
        topLevelBraces(
          call(Seq("foo"), "one"),
          call(Seq("foo"), "two"),
          return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
        )
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.one() {
      |  RETURN 1 AS one
      |}
      |{
      |  DEFINE PROCEDURE foo.two() {
      |    RETURN 2 AS two
      |  }
      |
      |  CALL foo.one()
      |  CALL foo.two()
      |  RETURN one, two
      |}""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localProcedureDefinition(Seq("foo"), "one").body(
          return_(
            aliasedReturnItem(literalInt(1), "one")
          )
        )
      )(
        topLevelBraces(
          singleQueryWithLocalDefinitions(
            localProcedureDefinition(Seq("foo"), "two").body(
              return_(
                aliasedReturnItem(literalInt(2), "two")
              )
            )
          )(
            call(Seq("foo"), "one"),
            call(Seq("foo"), "two"),
            return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
          )
        )
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.one() {
      |  RETURN 1 AS one
      |}
      |DEFINE PROCEDURE foo.two() {
      |  RETURN 2 AS two
      |}
      |
      |CALL foo.one()
      |RETURN one AS x
      |UNION
      |CALL foo.two()
      |RETURN two AS x""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localProcedureDefinition("foo.one").body(
          return_(
            aliasedReturnItem(literalInt(1), "one")
          )
        ),
        localProcedureDefinition("foo.two").body(
          return_(
            aliasedReturnItem(literalInt(2), "two")
          )
        )
      )(
        union(
          singleQuery(
            call(Seq("foo"), "one"),
            return_(aliasedReturnItem(varFor("one"), "x"))
          ),
          singleQuery(
            call(Seq("foo"), "two"),
            return_(aliasedReturnItem(varFor("two"), "x"))
          )
        )
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.one() {
      |  RETURN 1 AS one
      |}
      |DEFINE PROCEDURE foo.two() {
      |  RETURN 2 AS two
      |}
      |{
      |  CALL foo.one()
      |  RETURN one AS x
      |  UNION
      |  CALL foo.two()
      |  RETURN two AS x
      |}""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localProcedureDefinition("foo.one").body(
          return_(
            aliasedReturnItem(literalInt(1), "one")
          )
        ),
        localProcedureDefinition("foo.two").body(
          return_(
            aliasedReturnItem(literalInt(2), "two")
          )
        )
      )(
        topLevelBraces(
          union(
            singleQuery(
              call(Seq("foo"), "one"),
              return_(aliasedReturnItem(varFor("one"), "x"))
            ),
            singleQuery(
              call(Seq("foo"), "two"),
              return_(aliasedReturnItem(varFor("two"), "x"))
            )
          )
        )
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.two() {
      |  RETURN 2 AS two
      |}
      |{
      |  DEFINE PROCEDURE foo.one() {
      |    RETURN 1 AS one
      |  }
      |
      |  CALL foo.one()
      |  RETURN one AS x
      |  UNION
      |  CALL foo.two()
      |  RETURN two AS x
      |}""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localProcedureDefinition("foo.two").body(
          return_(
            aliasedReturnItem(literalInt(2), "two")
          )
        )
      )(
        topLevelBraces(
          queryWithLocalDefinitions(
            localProcedureDefinition("foo.one").body(
              return_(
                aliasedReturnItem(literalInt(1), "one")
              )
            )
          )(
            union(
              singleQuery(
                call(Seq("foo"), "one"),
                return_(aliasedReturnItem(varFor("one"), "x"))
              ),
              singleQuery(
                call(Seq("foo"), "two"),
                return_(aliasedReturnItem(varFor("two"), "x"))
              )
            )
          )
        )
      )
    )
  }

  test(
    """{
      |  DEFINE PROCEDURE foo.one() {
      |    RETURN 1 AS one
      |  }
      |
      |  CALL foo.one()
      |  RETURN one AS x
      |}
      |UNION
      |{
      |  DEFINE PROCEDURE foo.two() {
      |    RETURN 2 AS two
      |  }
      |
      |  CALL foo.two()
      |  RETURN two AS x
      |}""".stripMargin
  ) {
    parsesToStatement(
      union(
        topLevelBraces(
          singleQueryWithLocalDefinitions(
            localProcedureDefinition("foo.one").body(
              return_(
                aliasedReturnItem(literalInt(1), "one")
              )
            )
          )(
            call(Seq("foo"), "one"),
            return_(aliasedReturnItem(varFor("one"), "x"))
          )
        ),
        topLevelBraces(
          singleQueryWithLocalDefinitions(
            localProcedureDefinition("foo.two").body(
              return_(
                aliasedReturnItem(literalInt(2), "two")
              )
            )
          )(
            call(Seq("foo"), "two"),
            return_(aliasedReturnItem(varFor("two"), "x"))
          )
        )
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.one() {
      |  RETURN 1 AS one
      |}
      |DEFINE PROCEDURE foo.two() {
      |  RETURN 2 AS two
      |}
      |
      |CALL foo.one()
      |RETURN one
      |
      |NEXT
      |
      |CALL foo.two()
      |RETURN one, two""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localProcedureDefinition("foo.one").body(
          return_(
            aliasedReturnItem(literalInt(1), "one")
          )
        ),
        localProcedureDefinition("foo.two").body(
          return_(
            aliasedReturnItem(literalInt(2), "two")
          )
        )
      )(
        nextStatement(
          singleQuery(
            call(Seq("foo"), "one"),
            return_(returnItem(varFor("one"), "one"))
          ),
          singleQuery(
            call(Seq("foo"), "two"),
            return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
          )
        )
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.one() {
      |  RETURN 1 AS one
      |}
      |DEFINE PROCEDURE foo.two() {
      |  RETURN 2 AS two
      |}
      |{
      |  CALL foo.one()
      |  RETURN one
      |}
      |NEXT
      |{
      |  CALL foo.two()
      |  RETURN one, two
      |}""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localProcedureDefinition("foo.one").body(
          return_(
            aliasedReturnItem(literalInt(1), "one")
          )
        ),
        localProcedureDefinition("foo.two").body(
          return_(
            aliasedReturnItem(literalInt(2), "two")
          )
        )
      )(
        nextStatement(
          topLevelBraces(singleQuery(
            call(Seq("foo"), "one"),
            return_(returnItem(varFor("one"), "one"))
          )),
          topLevelBraces(singleQuery(
            call(Seq("foo"), "two"),
            return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
          ))
        )
      )
    )
  }

  test(
    """{
      |  DEFINE PROCEDURE foo.one() {
      |    RETURN 1 AS one
      |  }
      |
      |  CALL foo.one()
      |  RETURN one
      |}
      |NEXT
      |{
      |  DEFINE PROCEDURE foo.two() {
      |    RETURN 2 AS two
      |  }
      |
      |  CALL foo.two()
      |  RETURN one, two
      |}""".stripMargin
  ) {
    parsesToStatement(
      nextStatement(
        topLevelBraces(
          singleQueryWithLocalDefinitions(
            localProcedureDefinition("foo.one").body(
              return_(
                aliasedReturnItem(literalInt(1), "one")
              )
            )
          )(
            call(Seq("foo"), "one"),
            return_(returnItem(varFor("one"), "one"))
          )
        ),
        topLevelBraces(
          singleQueryWithLocalDefinitions(
            localProcedureDefinition("foo.two").body(
              return_(
                aliasedReturnItem(literalInt(2), "two")
              )
            )
          )(
            call(Seq("foo"), "two"),
            return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
          )
        )
      )
    )
  }

  test(
    """DEFINE PROCEDURE pick() {
      |  RETURN toInteger(rand() * 10) AS pick
      |}
      |DEFINE PROCEDURE one() {
      |  RETURN "one" AS x
      |}
      |DEFINE PROCEDURE two() {
      |  RETURN "two" AS x
      |}
      |DEFINE PROCEDURE more() {
      |  RETURN "more" AS x
      |}
      |
      |WHEN head(COLLECT {
      |  CALL pick()
      |  RETURN pick = 1 AS c
      |}) THEN
      |{
      |  CALL one()
      |  RETURN x
      |}
      |WHEN head(COLLECT {
      |  CALL pick()
      |  RETURN pick = 2 AS c
      |}) THEN
      |{
      |  CALL two()
      |  RETURN x
      |}
      |ELSE
      |{
      |  CALL more()
      |  RETURN x
      |}""".stripMargin
  ) {
    def pickCompare(equalTo: Expression): Expression =
      function(
        "head",
        CollectExpression(singleQuery(
          call(Seq.empty, "pick"),
          return_(aliasedReturnItem(equals(varFor("pick"), equalTo), "c"))
        ))(pos, Some(Set.empty), Some(Set.empty))
      )
    def thenCallProc(name: String): PartQuery =
      topLevelBraces(singleQuery(
        call(Seq.empty, name),
        return_(returnItem(varFor("x"), "x"))
      ))
    parsesToStatement(
      queryWithLocalDefinitions(
        localProcedureDefinition("pick").body(
          return_(aliasedReturnItem(function("toInteger", multiply(function("rand"), literalInt(10))), "pick"))
        ),
        localProcedureDefinition("one").body(
          return_(aliasedReturnItem(literalString("one"), "x"))
        ),
        localProcedureDefinition("two").body(
          return_(aliasedReturnItem(literalString("two"), "x"))
        ),
        localProcedureDefinition("more").body(
          return_(aliasedReturnItem(literalString("more"), "x"))
        )
      )(
        conditionalQueryWhen(
          conditionalQueryDefault(thenCallProc("more")),
          conditionalQueryBranch(
            pickCompare(literalInt(1)),
            thenCallProc("one")
          ),
          conditionalQueryBranch(
            pickCompare(literalInt(2)),
            thenCallProc("two")
          )
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION pick() = toInteger(rand() * 10)
      |DEFINE FUNCTION one() :: STRING NOT NULL = "one"
      |DEFINE FUNCTION two() :: STRING NOT NULL = "two"
      |DEFINE FUNCTION more() :: STRING NOT NULL = "more"
      |DEFINE PROCEDURE result() :: (res :: STRING NOT NULL) {
      |  WHEN pick() = 1 THEN RETURN one() AS x LIMIT 1
      |  WHEN pick() = 2 THEN RETURN two() AS x LIMIT 1
      |  ELSE RETURN more() AS x LIMIT 1
      |}
      |
      |CALL result()
      |RETURN res""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition("pick").body(
          function("toInteger", multiply(function("rand"), literalInt(10)))
        ),
        localFunctionDefinition("one").typ(CTStringNotNull).body(
          literalString("one")
        ),
        localFunctionDefinition("two").typ(CTStringNotNull).body(
          literalString("two")
        ),
        localFunctionDefinition("more").typ(CTStringNotNull).body(
          literalString("more")
        ),
        localProcedureDefinition("result").out(localFieldSignature("res", CTStringNotNull)).body(
          conditionalQueryWhen(
            conditionalQueryDefault(singleQuery(return_(limit(1), aliasedReturnItem(function("more"), "x")))),
            conditionalQueryBranch(
              equals(function("pick"), literalInt(1)),
              singleQuery(return_(limit(1), aliasedReturnItem(function("one"), "x")))
            ),
            conditionalQueryBranch(
              equals(function("pick"), literalInt(2)),
              singleQuery(return_(limit(1), aliasedReturnItem(function("two"), "x")))
            )
          )
        )
      )(
        call(Seq.empty, "result"),
        return_(returnItem(varFor("res"), "res"))
      )
    )
  }

  test(
    """CALL {
      |  DEFINE PROCEDURE foo.one() {
      |    RETURN 1 AS one
      |  }
      |  DEFINE PROCEDURE foo.two() {
      |    RETURN 2 AS two
      |  }
      |
      |  CALL foo.one()
      |  CALL foo.two()
      |  RETURN one, two
      |}
      |RETURN one, two
      |""".stripMargin
  ) {
    parsesToStatement(
      singleQuery(
        importingWithSubqueryCall(
          singleQueryWithLocalDefinitions(
            localProcedureDefinition("foo.one").body(
              return_(
                aliasedReturnItem(literalInt(1), "one")
              )
            ),
            localProcedureDefinition("foo.two").body(
              return_(
                aliasedReturnItem(literalInt(2), "two")
              )
            )
          )(
            call(Seq("foo"), "one"),
            call(Seq("foo"), "two"),
            return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
          )
        ),
        return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
      )
    )
  }

  test(
    """CALL () {
      |  DEFINE PROCEDURE foo.one() {
      |    RETURN 1 AS one
      |  }
      |  DEFINE PROCEDURE foo.two() {
      |    RETURN 2 AS two
      |  }
      |
      |  CALL foo.one()
      |  CALL foo.two()
      |  RETURN one, two
      |}
      |RETURN one, two
      |""".stripMargin
  ) {
    parsesToStatement(
      singleQuery(
        scopeClauseSubqueryCall(
          isImportingAll = false,
          Seq(),
          singleQueryWithLocalDefinitions(
            localProcedureDefinition("foo.one").body(
              return_(
                aliasedReturnItem(literalInt(1), "one")
              )
            ),
            localProcedureDefinition("foo.two").body(
              return_(
                aliasedReturnItem(literalInt(2), "two")
              )
            )
          )(
            call(Seq("foo"), "one"),
            call(Seq("foo"), "two"),
            return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
          )
        ),
        return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
      )
    )
  }

  test(
    """DEFINE PROCEDURE foo.two() {
      |  RETURN 2 AS two
      |}
      |CALL () {
      |  DEFINE PROCEDURE foo.one() {
      |    RETURN 1 AS one
      |  }
      |
      |  CALL foo.one()
      |  CALL foo.two()
      |  RETURN one, two
      |}
      |RETURN one, two
      |""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localProcedureDefinition("foo.two").body(
          return_(
            aliasedReturnItem(literalInt(2), "two")
          )
        )
      )(
        scopeClauseSubqueryCall(
          isImportingAll = false,
          Seq(),
          singleQueryWithLocalDefinitions(
            localProcedureDefinition("foo.one").body(
              return_(
                aliasedReturnItem(literalInt(1), "one")
              )
            )
          )(
            call(Seq("foo"), "one"),
            call(Seq("foo"), "two"),
            return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
          )
        ),
        return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
      )
    )
  }

  test(
    """UNWIND [1, 2, 3] AS x
      |CALL (x) {
      |  DEFINE PROCEDURE foo.one(x) {
      |    RETURN x * 1 AS one
      |  }
      |  DEFINE PROCEDURE foo.two(x) {
      |    RETURN x * 2 AS two
      |  }
      |
      |  CALL foo.one(x)
      |  CALL foo.two(x)
      |  RETURN one, two
      |}
      |RETURN one, two
      |""".stripMargin
  ) {
    parsesToStatement(
      singleQuery(
        unwind(listOfInt(1, 2, 3), varFor("x")),
        scopeClauseSubqueryCall(
          isImportingAll = false,
          Seq(varFor("x")),
          singleQueryWithLocalDefinitions(
            localProcedureDefinition("foo.one", localFieldSignature("x")).body(
              return_(
                aliasedReturnItem(multiply(varFor("x"), literalInt(1)), "one")
              )
            ),
            localProcedureDefinition("foo.two", localFieldSignature("x")).body(
              return_(
                aliasedReturnItem(multiply(varFor("x"), literalInt(2)), "two")
              )
            )
          )(
            call(Seq("foo"), "one", Some(Seq(varFor("x")))),
            call(Seq("foo"), "two", Some(Seq(varFor("x")))),
            return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
          )
        ),
        return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
      )
    )
  }

  private def parsesToStatement(
    expected: Statement,
    cypher5SyntaxErrorContains: Option[String] = None
  ): Unit = {
    parsesIn[Statement] {
      case Cypher5 => cypher5SyntaxErrorContains match {
          case None                      => _.withSyntaxErrorContaining("Invalid input")
          case Some(syntaxErrorContains) => _.withSyntaxErrorContaining(syntaxErrorContains)
        }
      case _ => _.toAst(expected)
    }
  }
}
