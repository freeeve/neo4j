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

import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.util.symbols.CTFloat
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.CTString
import org.neo4j.cypher.internal.util.symbols.CTStringNotNull
import org.neo4j.cypher.internal.util.symbols.ClosedDynamicUnionType

class DefineLocalFunctionTest extends AstParsingTestBase {

  test(
    """DEFINE FUNCTION bar() {
      |  RETURN 1 AS one
      |    LIMIT 1
      |}
      |
      |RETURN bar() AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(Seq(), "bar").body(
          return_(
            limit(1),
            aliasedReturnItem(literalInt(1), "one")
          )
        )
      )(
        return_(
          aliasedReturnItem(function("bar"), "one")
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION bar() = 1
      |
      |RETURN bar() AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(Seq(), "bar").body(
          literalInt(1)
        )
      )(
        return_(
          aliasedReturnItem(function("bar"), "one")
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar() {
      |  RETURN 1 AS one
      |    LIMIT 1
      |}
      |
      |RETURN foo.bar() AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(Seq("foo"), "bar").body(
          return_(
            limit(1),
            aliasedReturnItem(literalInt(1), "one")
          )
        )
      )(
        return_(
          aliasedReturnItem(function(Seq("foo"), "bar"), "one")
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar() = 1
      |
      |RETURN foo.bar() AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(Seq("foo"), "bar").body(
          literalInt(1)
        )
      )(
        return_(
          aliasedReturnItem(function(Seq("foo"), "bar"), "one")
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar(x) {
      |  RETURN x + 1 AS one
      |    LIMIT 1
      |}
      |
      |RETURN foo.bar(1) AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(Seq("foo"), "bar", localFieldSignature("x")).body(
          return_(
            limit(1),
            aliasedReturnItem(add(varFor("x"), literalInt(1)), "one")
          )
        )
      )(
        return_(
          aliasedReturnItem(function(Seq("foo"), "bar", literalInt(1)), "one")
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar(x) = x + 1
      |
      |RETURN foo.bar(1) AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(Seq("foo"), "bar", localFieldSignature("x")).body(
          add(varFor("x"), literalInt(1))
        )
      )(
        return_(
          aliasedReturnItem(function(Seq("foo"), "bar", literalInt(1)), "one")
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar(x, y) {
      |  RETURN x + 1 * y AS one
      |    LIMIT 1
      |}
      |
      |RETURN foo.bar(1, 2) AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(Seq("foo"), "bar", localFieldSignature("x"), localFieldSignature("y")).body(
          return_(
            limit(1),
            aliasedReturnItem(add(varFor("x"), multiply(literalInt(1), varFor("y"))), "one")
          )
        )
      )(
        return_(
          aliasedReturnItem(function(Seq("foo"), "bar", literalInt(1), literalInt(2)), "one")
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar(x, y) = x + 1 * y
      |
      |RETURN foo.bar(1, 2) AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(Seq("foo"), "bar", localFieldSignature("x"), localFieldSignature("y")).body(
          add(varFor("x"), multiply(literalInt(1), varFor("y")))
        )
      )(
        return_(
          aliasedReturnItem(function(Seq("foo"), "bar", literalInt(1), literalInt(2)), "one")
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar(x, y) = x = y
      |
      |RETURN foo.bar(1, 2) AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(Seq("foo"), "bar", localFieldSignature("x"), localFieldSignature("y")).body(
          equals(varFor("x"), varFor("y"))
        )
      )(
        return_(
          aliasedReturnItem(function(Seq("foo"), "bar", literalInt(1), literalInt(2)), "one")
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar(a1, b2, c3, d4, e5, f6, g7, h8, i9, j10, k11, l12, m13, n14, o15, p16, q17, r18, s19, t20, u21, v22, w23, x24, y25, z26) {
      |  RETURN 1 AS one
      |    LIMIT 1
      |}
      |
      |RETURN foo.bar(1, "two", 3.14, true, [5, 5, 5], null, {a: 7}, 4 + 4, (9), 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26) AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(
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
            limit(1),
            aliasedReturnItem(literalInt(1), "one")
          )
        )
      )(
        return_(aliasedReturnItem(
          function(
            Seq("foo"),
            "bar",
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
          ),
          "one"
        ))
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar(a1, b2, c3, d4, e5, f6, g7, h8, i9, j10, k11, l12, m13, n14, o15, p16, q17, r18, s19, t20, u21, v22, w23, x24, y25, z26) = 1
      |
      |RETURN foo.bar(1, "two", 3.14, true, [5, 5, 5], null, {a: 7}, 4 + 4, (9), 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26) AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(
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
          literalInt(1)
        )
      )(
        return_(aliasedReturnItem(
          function(
            Seq("foo"),
            "bar",
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
          ),
          "one"
        ))
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar(x, y = 1) = x + 1 * y
      |
      |RETURN foo.bar(0) AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(
          Seq("foo"),
          "bar",
          localFieldSignature("x"),
          localFieldSignature("y", literalInt(1))
        ).body(
          add(varFor("x"), multiply(literalInt(1), varFor("y")))
        )
      )(
        return_(aliasedReturnItem(function(Seq("foo"), "bar", literalInt(0)), "one"))
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar(x = 0, y = 1) = x + 1 * y
      |
      |RETURN foo.bar() AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(
          Seq("foo"),
          "bar",
          localFieldSignature("x", literalInt(0)),
          localFieldSignature("y", literalInt(1))
        ).body(
          add(varFor("x"), multiply(literalInt(1), varFor("y")))
        )
      )(
        return_(aliasedReturnItem(function(Seq("foo"), "bar"), "one"))
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar(x = 0, y) = x + 1 * y
      |
      |RETURN foo.bar(1) AS one""".stripMargin
  ) {
    // NOTE: This query is invalid and needs to filtered out in semantic checking
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(
          Seq("foo"),
          "bar",
          localFieldSignature("x", literalInt(0)),
          localFieldSignature("y")
        ).body(
          add(varFor("x"), multiply(literalInt(1), varFor("y")))
        )
      )(
        return_(aliasedReturnItem(function(Seq("foo"), "bar", literalInt(1)), "one"))
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar(x TYPED INT | FLOAT , y :: INT = 1) TYPED INT = x + 1 * y
      |
      |RETURN foo.bar(0) AS one""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(
          "foo.bar",
          localFieldSignature("x", ClosedDynamicUnionType(Set(CTInteger, CTFloat))(pos)),
          localFieldSignature("y", CTInteger, literalInt(1))
        ).typ(CTInteger).body(
          add(varFor("x"), multiply(literalInt(1), varFor("y")))
        )
      )(
        return_(aliasedReturnItem(function(Seq("foo"), "bar", literalInt(0)), "one"))
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.bar(x STRING , y :: STRING = ["d", "e", "f"]) :: STRING = reduce(acc = "", z IN y | acc + x + z )
      |
      |RETURN foo.bar("abc") AS abc""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(
          "foo.bar",
          localFieldSignature("x", CTString),
          localFieldSignature("y", CTString, listOfString("d", "e", "f"))
        ).typ(CTString).body(
          reduce(
            varFor("acc"),
            literalString(""),
            varFor("z"),
            varFor("y"),
            add(add(varFor("acc"), varFor("x")), varFor("z"))
          )
        )
      )(
        return_(aliasedReturnItem(function(Seq("foo"), "bar", literalString("abc")), "abc"))
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.one() {
      |  RETURN 1 AS one
      |    LIMIT 1
      |}
      |DEFINE FUNCTION foo.two() {
      |  RETURN 2 AS two
      |    LIMIT 1
      |}
      |
      |RETURN foo.one() AS one,  foo.two() AS two""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition(Seq("foo"), "one").body(
          return_(
            limit(1),
            aliasedReturnItem(literalInt(1), "one")
          )
        ),
        localFunctionDefinition(Seq("foo"), "two").body(
          return_(
            limit(1),
            aliasedReturnItem(literalInt(2), "two")
          )
        )
      )(
        return_(
          aliasedReturnItem(function(Seq("foo"), "one"), "one"),
          aliasedReturnItem(function(Seq("foo"), "two"), "two")
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION oneTwo() {
      |  DEFINE FUNCTION foo.one() = 1
      |  DEFINE FUNCTION foo.two() {
      |    RETURN 2 AS two
      |      LIMIT 1
      |  }
      |
      |  RETURN foo.one() + foo.two() AS three
      |}
      |
      |RETURN oneTwo() AS three""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition("oneTwo").body(
          singleQueryWithLocalDefinitions(
            localFunctionDefinition(Seq("foo"), "one").body(literalInt(1)),
            localFunctionDefinition(Seq("foo"), "two").body(
              return_(
                limit(1),
                aliasedReturnItem(literalInt(2), "two")
              )
            )
          )(
            return_(aliasedReturnItem(add(function(Seq("foo"), "one"), function(Seq("foo"), "two")), "three"))
          )
        )
      )(
        return_(aliasedReturnItem(function("oneTwo"), "three"))
      )
    )
  }

  test(
    """{
      |  DEFINE FUNCTION foo.one() {
      |    RETURN 1 AS one
      |      LIMIT 1
      |  }
      |  DEFINE FUNCTION foo.two() {
      |    RETURN 2 AS two
      |      LIMIT 1
      |  }
      |  RETURN foo.one() AS one,  foo.two() AS two
      |}""".stripMargin
  ) {
    parsesToStatement(
      topLevelBraces(
        singleQueryWithLocalDefinitions(
          localFunctionDefinition(Seq("foo"), "one").body(
            return_(
              limit(1),
              aliasedReturnItem(literalInt(1), "one")
            )
          ),
          localFunctionDefinition(Seq("foo"), "two").body(
            return_(
              limit(1),
              aliasedReturnItem(literalInt(2), "two")
            )
          )
        )(
          return_(
            aliasedReturnItem(function(Seq("foo"), "one"), "one"),
            aliasedReturnItem(function(Seq("foo"), "two"), "two")
          )
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.one() {
      |  RETURN 1 AS one
      |    LIMIT 1
      |}
      |DEFINE FUNCTION foo.two() {
      |  RETURN 2 AS two
      |    LIMIT 1
      |}
      |{
      |  RETURN foo.one() AS one,  foo.two() AS two
      |}""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localFunctionDefinition(Seq("foo"), "one").body(
          return_(
            limit(1),
            aliasedReturnItem(literalInt(1), "one")
          )
        ),
        localFunctionDefinition(Seq("foo"), "two").body(
          return_(
            limit(1),
            aliasedReturnItem(literalInt(2), "two")
          )
        )
      )(
        topLevelBraces(
          return_(
            aliasedReturnItem(function(Seq("foo"), "one"), "one"),
            aliasedReturnItem(function(Seq("foo"), "two"), "two")
          )
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.one() {
      |  RETURN 1 AS one
      |    LIMIT 1
      |}
      |{
      |  DEFINE FUNCTION foo.two() {
      |    RETURN 2 AS two
      |      LIMIT 1
      |  }
      |
      |  RETURN foo.one() AS one, foo.two() AS two
      |}""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localFunctionDefinition(Seq("foo"), "one").body(
          return_(
            limit(1),
            aliasedReturnItem(literalInt(1), "one")
          )
        )
      )(
        topLevelBraces(
          singleQueryWithLocalDefinitions(
            localFunctionDefinition(Seq("foo"), "two").body(
              return_(
                limit(1),
                aliasedReturnItem(literalInt(2), "two")
              )
            )
          )(
            return_(
              aliasedReturnItem(function(Seq("foo"), "one"), "one"),
              aliasedReturnItem(function(Seq("foo"), "two"), "two")
            )
          )
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION three() {
      |  DEFINE FUNCTION foo.one() {
      |    RETURN 1 AS one
      |      LIMIT 1
      |  }
      |  DEFINE FUNCTION foo.two() = 2
      |
      |  RETURN foo.one() + foo.two() AS three
      |}
      |
      |RETURN three() AS three""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition("three").body(
          singleQueryWithLocalDefinitions(
            localFunctionDefinition("foo.one").body(
              return_(
                limit(1),
                aliasedReturnItem(literalInt(1), "one")
              )
            ),
            localFunctionDefinition("foo.two").body(
              literalInt(2)
            )
          )(
            return_(
              aliasedReturnItem(add(function(Seq("foo"), "one"), function(Seq("foo"), "two")), "three")
            )
          )
        )
      )(
        return_(
          aliasedReturnItem(function("three"), "three")
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.one() = 1
      |DEFINE FUNCTION foo.two() = 2
      |
      |RETURN foo.one() AS x
      |UNION
      |RETURN foo.two() AS x""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localFunctionDefinition("foo.one").body(
          literalInt(1)
        ),
        localFunctionDefinition("foo.two").body(
          literalInt(2)
        )
      )(
        union(
          singleQuery(
            return_(aliasedReturnItem(function(Seq("foo"), "one"), "x"))
          ),
          singleQuery(
            return_(aliasedReturnItem(function(Seq("foo"), "two"), "x"))
          )
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.one() = 1
      |DEFINE FUNCTION foo.two() = 2
      |{
      |  RETURN foo.one() AS x
      |  UNION
      |  RETURN foo.two() AS x
      |}""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localFunctionDefinition("foo.one").body(
          literalInt(1)
        ),
        localFunctionDefinition("foo.two").body(
          literalInt(2)
        )
      )(
        topLevelBraces(
          union(
            singleQuery(
              return_(aliasedReturnItem(function(Seq("foo"), "one"), "x"))
            ),
            singleQuery(
              return_(aliasedReturnItem(function(Seq("foo"), "two"), "x"))
            )
          )
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.two() = 2
      |
      |{
      |  DEFINE FUNCTION foo.one() = 1
      |
      |  RETURN foo.one() AS x
      |  UNION
      |  RETURN foo.two() AS x
      |}""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localFunctionDefinition("foo.two").body(
          literalInt(2)
        )
      )(
        topLevelBraces(
          queryWithLocalDefinitions(
            localFunctionDefinition("foo.one").body(
              literalInt(1)
            )
          )(
            union(
              singleQuery(
                return_(aliasedReturnItem(function(Seq("foo"), "one"), "x"))
              ),
              singleQuery(
                return_(aliasedReturnItem(function(Seq("foo"), "two"), "x"))
              )
            )
          )
        )
      )
    )
  }

  test(
    """{
      |  DEFINE FUNCTION foo.one() = 1
      |
      |  RETURN foo.one() AS x
      |}
      |UNION
      |{
      |  DEFINE FUNCTION foo.two() = 2
      |
      |  RETURN foo.two() AS x
      |}""".stripMargin
  ) {
    parsesToStatement(
      union(
        topLevelBraces(
          singleQueryWithLocalDefinitions(
            localFunctionDefinition("foo.one").body(
              literalInt(1)
            )
          )(
            return_(aliasedReturnItem(function(Seq("foo"), "one"), "x"))
          )
        ),
        topLevelBraces(
          singleQueryWithLocalDefinitions(
            localFunctionDefinition("foo.two").body(
              literalInt(2)
            )
          )(
            return_(aliasedReturnItem(function(Seq("foo"), "two"), "x"))
          )
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.one() = 1
      |DEFINE FUNCTION foo.two() = 2
      |
      |RETURN foo.one() AS one
      |
      |NEXT
      |
      |RETURN one, foo.two() AS two""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localFunctionDefinition("foo.one").body(
          literalInt(1)
        ),
        localFunctionDefinition("foo.two").body(
          literalInt(2)
        )
      )(
        nextStatement(
          singleQuery(
            return_(aliasedReturnItem(function(Seq("foo"), "one"), "one"))
          ),
          singleQuery(
            return_(returnItem(varFor("one"), "one"), aliasedReturnItem(function(Seq("foo"), "two"), "two"))
          )
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.one() = 1
      |DEFINE FUNCTION foo.two() = 2
      |{
      |  RETURN foo.one() AS one
      |}
      |NEXT
      |{
      |  RETURN one, foo.two() AS two
      |}""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localFunctionDefinition("foo.one").body(
          literalInt(1)
        ),
        localFunctionDefinition("foo.two").body(
          literalInt(2)
        )
      )(
        nextStatement(
          topLevelBraces(singleQuery(
            return_(aliasedReturnItem(function(Seq("foo"), "one"), "one"))
          )),
          topLevelBraces(singleQuery(
            return_(returnItem(varFor("one"), "one"), aliasedReturnItem(function(Seq("foo"), "two"), "two"))
          ))
        )
      )
    )
  }

  test(
    """{
      |  DEFINE FUNCTION foo.one() = 1
      |  RETURN foo.one() AS one
      |}
      |NEXT
      |{
      |  DEFINE FUNCTION foo.two() = 2
      |  RETURN one, foo.two() AS two
      |}""".stripMargin
  ) {
    parsesToStatement(
      nextStatement(
        topLevelBraces(
          singleQueryWithLocalDefinitions(
            localFunctionDefinition("foo.one").body(
              literalInt(1)
            )
          )(
            return_(aliasedReturnItem(function(Seq("foo"), "one"), "one"))
          )
        ),
        topLevelBraces(
          singleQueryWithLocalDefinitions(
            localFunctionDefinition("foo.two").body(
              literalInt(2)
            )
          )(
            return_(returnItem(varFor("one"), "one"), aliasedReturnItem(function(Seq("foo"), "two"), "two"))
          )
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION pick() = toInteger(rand() * 10)
      |DEFINE FUNCTION one() = "one"
      |DEFINE FUNCTION two() = "two"
      |DEFINE FUNCTION more() = "more"
      |
      |WHEN pick() = 1 THEN RETURN one() AS x
      |WHEN pick() = 2 THEN RETURN two() AS x
      |ELSE RETURN more() AS x""".stripMargin
  ) {
    parsesToStatement(
      queryWithLocalDefinitions(
        localFunctionDefinition("pick").body(
          function("toInteger", multiply(function("rand"), literalInt(10)))
        ),
        localFunctionDefinition("one").body(
          literalString("one")
        ),
        localFunctionDefinition("two").body(
          literalString("two")
        ),
        localFunctionDefinition("more").body(
          literalString("more")
        )
      )(
        conditionalQueryWhen(
          conditionalQueryDefault(singleQuery(return_(aliasedReturnItem(function("more"), "x")))),
          conditionalQueryBranch(
            equals(function("pick"), literalInt(1)),
            singleQuery(return_(aliasedReturnItem(function("one"), "x")))
          ),
          conditionalQueryBranch(
            equals(function("pick"), literalInt(2)),
            singleQuery(return_(aliasedReturnItem(function("two"), "x")))
          )
        )
      )
    )
  }

  test(
    """DEFINE FUNCTION pick() = toInteger(rand() * 10)
      |DEFINE FUNCTION one() = "one"
      |DEFINE FUNCTION two() = "two"
      |DEFINE FUNCTION more() = "more"
      |DEFINE FUNCTION result() :: STRING NOT NULL {
      |  WHEN pick() = 1 THEN RETURN one() AS x LIMIT 1
      |  WHEN pick() = 2 THEN RETURN two() AS x LIMIT 1
      |  ELSE RETURN more() AS x LIMIT 1
      |}
      |
      |RETURN result()""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition("pick").body(
          function("toInteger", multiply(function("rand"), literalInt(10)))
        ),
        localFunctionDefinition("one").body(
          literalString("one")
        ),
        localFunctionDefinition("two").body(
          literalString("two")
        ),
        localFunctionDefinition("more").body(
          literalString("more")
        ),
        localFunctionDefinition("result").typ(CTStringNotNull).body(
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
        return_(returnItem(function("result"), "result()"))
      )
    )
  }

  test(
    """DEFINE FUNCTION pick() = toInteger(rand() * 10)
      |DEFINE FUNCTION one() :: STRING NOT NULL = "one"
      |DEFINE FUNCTION two() :: STRING NOT NULL = "two"
      |DEFINE FUNCTION more() :: STRING NOT NULL = "more"
      |DEFINE FUNCTION result() :: STRING NOT NULL =
      |  CASE
      |  WHEN pick() = 1 THEN one()
      |  WHEN pick() = 2 THEN two()
      |  ELSE more()
      |  END
      |
      |RETURN result()""".stripMargin
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
        localFunctionDefinition("result").typ(CTStringNotNull).body(
          caseExpression(
            None,
            Some(function("more")),
            equals(function("pick"), literalInt(1)) -> function("one"),
            equals(function("pick"), literalInt(2)) -> function("two")
          )
        )
      )(
        return_(returnItem(function("result"), "result()"))
      )
    )
  }

  test(
    """CALL {
      |  DEFINE FUNCTION foo.one() = 1
      |  DEFINE FUNCTION foo.two() = 2
      |
      |  RETURN foo.one() AS one, foo.two() AS two
      |}
      |RETURN one, two
      |""".stripMargin
  ) {
    parsesToStatement(
      singleQuery(
        importingWithSubqueryCall(
          singleQueryWithLocalDefinitions(
            localFunctionDefinition("foo.one").body(
              literalInt(1)
            ),
            localFunctionDefinition("foo.two").body(
              literalInt(2)
            )
          )(
            return_(
              aliasedReturnItem(function(Seq("foo"), "one"), "one"),
              aliasedReturnItem(function(Seq("foo"), "two"), "two")
            )
          )
        ),
        return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
      )
    )
  }

  test(
    """CALL () {
      |  DEFINE FUNCTION foo.one() = 1
      |  DEFINE FUNCTION foo.two() = 2
      |
      |  RETURN foo.one() AS one, foo.two() AS two
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
            localFunctionDefinition("foo.one").body(
              literalInt(1)
            ),
            localFunctionDefinition("foo.two").body(
              literalInt(2)
            )
          )(
            return_(
              aliasedReturnItem(function(Seq("foo"), "one"), "one"),
              aliasedReturnItem(function(Seq("foo"), "two"), "two")
            )
          )
        ),
        return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
      )
    )
  }

  test(
    """DEFINE FUNCTION foo.two() = 2
      |CALL () {
      |  DEFINE FUNCTION foo.one() = 1
      |
      |  RETURN foo.one() AS one, foo.two() AS two
      |}
      |RETURN one, two
      |""".stripMargin
  ) {
    parsesToStatement(
      singleQueryWithLocalDefinitions(
        localFunctionDefinition("foo.two").body(
          literalInt(2)
        )
      )(
        scopeClauseSubqueryCall(
          isImportingAll = false,
          Seq(),
          singleQueryWithLocalDefinitions(
            localFunctionDefinition("foo.one").body(
              literalInt(1)
            )
          )(
            return_(
              aliasedReturnItem(function(Seq("foo"), "one"), "one"),
              aliasedReturnItem(function(Seq("foo"), "two"), "two")
            )
          )
        ),
        return_(returnItem(varFor("one"), "one"), returnItem(varFor("two"), "two"))
      )
    )
  }

  test(
    """UNWIND [1, 2, 3] AS x
      |CALL (x) {
      |  DEFINE FUNCTION foo.one(x) = x * 1
      |  DEFINE FUNCTION foo.two(x) = x * 2
      |
      |  RETURN foo.one() AS one, foo.two() AS two
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
            localFunctionDefinition("foo.one", localFieldSignature("x")).body(
              multiply(varFor("x"), literalInt(1))
            ),
            localFunctionDefinition("foo.two", localFieldSignature("x")).body(
              multiply(varFor("x"), literalInt(2))
            )
          )(
            return_(
              aliasedReturnItem(function(Seq("foo"), "one"), "one"),
              aliasedReturnItem(function(Seq("foo"), "two"), "two")
            )
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
