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
package org.neo4j.cypher.internal.frontend.scoping

class ScopeSurveyorTest extends VariableCheckingTestSuite {

  test("RETURN 1") {
    hasScope(
      ExpectedWorkingScope(
        Ast("RETURN 1"), // query level
        Outgoing(variables = Set("1")),
        ExpectedResult.TableResult("1"),
        ExpectedWorkingScope(
          Ast("RETURN 1"),
          Outgoing(variables = Set("1")),
          ExpectedResult.TableResult("1"),
          ExpectedWorkingScope.constExp("1")
        )
      )
    )
  }

  test("FINISH") {
    hasScope(
      ExpectedWorkingScope(
        Ast("FINISH"), // query level
        ExpectedResult.OmittedResult,
        ExpectedWorkingScope(
          Ast("FINISH"),
          ExpectedResult.OmittedResult
        )
      )
    )
  }

  test("""{
         |  RETURN 1
         |}""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("RETURN 1"), // query level
        Outgoing(variables = Set("1")),
        ExpectedResult.TableResult("1"),
        ExpectedWorkingScope(
          Ast("RETURN 1"),
          Outgoing(variables = Set("1")),
          ExpectedResult.TableResult("1"),
          ExpectedWorkingScope.constExp("1")
        )
      )
    )
  }

  test("""RETURN 1 AS a
         |
         |NEXT
         |
         |RETURN a + 1 AS b""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""RETURN 1 AS a
              |
              |NEXT
              |
              |RETURN a + 1 AS b""".stripMargin),
        Outgoing(variables = Set("b")),
        ExpectedResult.TableResult("b"),
        ExpectedWorkingScope(
          Ast("RETURN 1 AS a"), // query level
          Outgoing(variables = Set("a")),
          ExpectedResult.TableResult("a"),
          ExpectedWorkingScope(
            Ast("RETURN 1 AS a"),
            Outgoing(variables = Set("a")),
            ExpectedResult.TableResult("a"),
            ExpectedWorkingScope.constExp("1")
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN a + 1 AS b"), // query level
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Outgoing(variables = Set("b")),
          ExpectedResult.TableResult("b"),
          ExpectedWorkingScope(
            Ast("RETURN a + 1 AS b"),
            Incoming(variables = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("b")),
            ExpectedResult.TableResult("b"),
            ExpectedWorkingScope(
              Ast("a + 1"),
              Incoming(constants = Set("a")),
              Referenced(Set("a")),
              ExpectedResult.ExpressionResult,
              ExpectedWorkingScope.varExp("a", Set("a"))
            )
          )
        )
      )
    )
  }

  test("""RETURN 1 AS a
         |
         |NEXT
         |
         |RETURN a + 1 AS b
         |
         |NEXT
         |
         |RETURN 1 * b AS b""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""RETURN 1 AS a
              |
              |NEXT
              |
              |RETURN a + 1 AS b
              |
              |NEXT
              |
              |RETURN 1 * b AS b""".stripMargin),
        Outgoing(variables = Set("b")),
        ExpectedResult.TableResult("b"),
        ExpectedWorkingScope(
          Ast("RETURN 1 AS a"), // query level
          Outgoing(variables = Set("a")),
          ExpectedResult.TableResult("a"),
          ExpectedWorkingScope(
            Ast("RETURN 1 AS a"),
            Outgoing(variables = Set("a")),
            ExpectedResult.TableResult("a"),
            ExpectedWorkingScope.constExp("1")
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN a + 1 AS b"), // query level
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Outgoing(variables = Set("b")),
          ExpectedResult.TableResult("b"),
          ExpectedWorkingScope(
            Ast("RETURN a + 1 AS b"),
            Incoming(variables = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("b")),
            ExpectedResult.TableResult("b"),
            ExpectedWorkingScope(
              Ast("a + 1"),
              Incoming(constants = Set("a")),
              Referenced(Set("a")),
              ExpectedResult.ExpressionResult,
              ExpectedWorkingScope.varExp("a", Set("a"))
            )
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN 1 * b AS b"), // query level
          Incoming(variables = Set("b")),
          Referenced(Set("b")),
          Outgoing(variables = Set("b")),
          ExpectedResult.TableResult("b"),
          ExpectedWorkingScope(
            Ast("RETURN 1 * b AS b"),
            Incoming(variables = Set("b")),
            Referenced(Set("b")),
            Outgoing(variables = Set("b")),
            ExpectedResult.TableResult("b"),
            ExpectedWorkingScope(
              Ast("1 * b"),
              Incoming(constants = Set("b")),
              Referenced(Set("b")),
              ExpectedResult.ExpressionResult,
              ExpectedWorkingScope.varExp("b", Set("b"))
            )
          )
        )
      )
    )
  }

  test("""LET x = 1
         |CALL (x) {
         |  RETURN x AS a
         |
         |  NEXT
         |
         |  RETURN x + 1 AS b,  a AS c
         |}
         |RETURN b, c""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET x = 1
              |CALL (x) {
              |  RETURN x AS a
              |
              |  NEXT
              |
              |  RETURN x + 1 AS b, a AS c
              |}
              |RETURN b, c""".stripMargin),
        Outgoing(variables = Set("b", "c")),
        ExpectedResult.TableResult("b", "c"),
        ExpectedWorkingScope(
          Ast("LET x = 1"),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("x")),
          ExpectedWorkingScope.constExp("1")
        ),
        ExpectedWorkingScope(
          Ast("""CALL (x) {
                |  RETURN x AS a
                |
                |  NEXT
                |
                |  RETURN x + 1 AS b, a AS c
                |}""".stripMargin),
          Incoming(variables = Set("x")),
          Referenced(Set("x")),
          Declared(variables = Seq("b", "c")),
          Outgoing(variables = Set("x", "b", "c")),
          ExpectedWorkingScope(
            Ast("""RETURN x AS a
                  |
                  |NEXT
                  |
                  |RETURN x + 1 AS b, a AS c""".stripMargin),
            Incoming(constants = Set("x")),
            Referenced(Set("x")),
            Outgoing(variables = Set("b", "c")),
            ExpectedResult.TableResult("b", "c"),
            ExpectedWorkingScope(
              Ast("RETURN x AS a"), // query level
              Incoming(constants = Set("x")),
              Referenced(Set("x")),
              Outgoing(variables = Set("a")),
              ExpectedResult.TableResult("a"),
              ExpectedWorkingScope(
                Ast("RETURN x AS a"),
                Incoming(constants = Set("x")),
                Referenced(Set("x")),
                Outgoing(variables = Set("a")),
                ExpectedResult.TableResult("a"),
                ExpectedWorkingScope.varExp("x", Set("x"))
              )
            ),
            ExpectedWorkingScope(
              Ast("RETURN x + 1 AS b, a AS c"), // query level
              Incoming(constants = Set("x"), variables = Set("a")),
              Referenced(Set("x", "a")),
              Outgoing(variables = Set("b", "c")),
              ExpectedResult.TableResult("b", "c"),
              ExpectedWorkingScope(
                Ast("RETURN x + 1 AS b, a AS c"),
                Incoming(constants = Set("x"), variables = Set("a")),
                Referenced(Set("x", "a")),
                Outgoing(variables = Set("b", "c")),
                ExpectedResult.TableResult("b", "c"),
                ExpectedWorkingScope(
                  Ast("x + 1"),
                  Incoming(constants = Set("x", "a")),
                  Referenced(Set("x")),
                  ExpectedResult.ExpressionResult,
                  ExpectedWorkingScope.varExp("x", Set("x", "a"))
                ),
                ExpectedWorkingScope.varExp("a", Set("x", "a"))
              )
            )
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN b, c"),
          Incoming(variables = Set("x", "b", "c")),
          Referenced(Set("b", "c")),
          Outgoing(variables = Set("b", "c")),
          ExpectedResult.TableResult("b", "c"),
          ExpectedWorkingScope.varExp("b", Set("x", "b", "c")),
          ExpectedWorkingScope.varExp("c", Set("x", "b", "c"))
        )
      )
    )
  }

  test("""RETURN 1 AS a
         |UNION
         |RETURN 2 AS a""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""RETURN 1 AS a
              |UNION
              |RETURN 2 AS a""".stripMargin),
        Outgoing(variables = Set("a")),
        ExpectedResult.TableResult("a"),
        ExpectedWorkingScope(
          Ast("RETURN 1 AS a"), // query level
          Outgoing(variables = Set("a")),
          ExpectedResult.TableResult("a"),
          ExpectedWorkingScope(
            Ast("RETURN 1 AS a"),
            Outgoing(variables = Set("a")),
            ExpectedResult.TableResult("a"),
            ExpectedWorkingScope.constExp("1")
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN 2 AS a"), // query level
          Outgoing(variables = Set("a")),
          ExpectedResult.TableResult("a"),
          ExpectedWorkingScope(
            Ast("RETURN 2 AS a"),
            Outgoing(variables = Set("a")),
            ExpectedResult.TableResult("a"),
            ExpectedWorkingScope.constExp("2")
          )
        )
      )
    )
  }

  test("""RETURN 1 AS a
         |UNION
         |RETURN 2 AS b""".stripMargin) {

    /**
     * This is an example of deliberately invalid Cypher to document how variable scoping deals with it.
     * Currently the scope of union adopts outgoing and result from the last child.
     * Alternative treatments are imaginable.
     * If an alternative is picked up, this test will probably need adjustment.
     */
    hasScope(
      ExpectedWorkingScope(
        Ast("""RETURN 1 AS a
              |UNION
              |RETURN 2 AS b""".stripMargin),
        Outgoing(variables = Set("a")),
        ExpectedResult.TableResult("a"),
        ExpectedWorkingScope(
          Ast("RETURN 1 AS a"), // query level
          Outgoing(variables = Set("a")),
          ExpectedResult.TableResult("a"),
          ExpectedWorkingScope(
            Ast("RETURN 1 AS a"),
            Outgoing(variables = Set("a")),
            ExpectedResult.TableResult("a"),
            ExpectedWorkingScope.constExp("1")
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN 2 AS b"), // query level
          Outgoing(variables = Set("b")),
          ExpectedResult.TableResult("b"),
          ExpectedWorkingScope(
            Ast("RETURN 2 AS b"),
            Outgoing(variables = Set("b")),
            ExpectedResult.TableResult("b"),
            ExpectedWorkingScope.constExp("2")
          )
        )
      ),
      skipVariableChecker = true
    )
  }

  test("""RETURN 1 AS a
         |
         |NEXT
         |{
         |  RETURN a + 1 AS a
         |  UNION
         |  RETURN a + 2 AS a
         |}""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""RETURN 1 AS a
              |
              |NEXT
              |{
              |  RETURN a + 1 AS a
              |  UNION
              |  RETURN a + 2 AS a
              |}""".stripMargin),
        Outgoing(variables = Set("a")),
        ExpectedResult.TableResult("a"),
        ExpectedWorkingScope(
          Ast("RETURN 1 AS a"), // query level
          Outgoing(variables = Set("a")),
          ExpectedResult.TableResult("a"),
          ExpectedWorkingScope(
            Ast("RETURN 1 AS a"),
            Outgoing(variables = Set("a")),
            ExpectedResult.TableResult("a"),
            ExpectedWorkingScope.constExp("1")
          )
        ),
        ExpectedWorkingScope(
          Ast("""RETURN a + 1 AS a
                |UNION
                |RETURN a + 2 AS a""".stripMargin),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Outgoing(variables = Set("a")),
          ExpectedResult.TableResult("a"),
          ExpectedWorkingScope(
            Ast("RETURN a + 1 AS a"), // query level
            Incoming(variables = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("a")),
            ExpectedResult.TableResult("a"),
            ExpectedWorkingScope(
              Ast("RETURN a + 1 AS a"),
              Incoming(variables = Set("a")),
              Referenced(Set("a")),
              Outgoing(variables = Set("a")),
              ExpectedResult.TableResult("a"),
              ExpectedWorkingScope(
                Ast("a + 1"),
                Incoming(constants = Set("a")),
                Referenced(Set("a")),
                ExpectedResult.ExpressionResult,
                ExpectedWorkingScope.varExp("a", Set("a"))
              )
            )
          ),
          ExpectedWorkingScope(
            Ast("RETURN a + 2 AS a"), // query level
            Incoming(variables = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("a")),
            ExpectedResult.TableResult("a"),
            ExpectedWorkingScope(
              Ast("RETURN a + 2 AS a"),
              Incoming(variables = Set("a")),
              Referenced(Set("a")),
              Outgoing(variables = Set("a")),
              ExpectedResult.TableResult("a"),
              ExpectedWorkingScope(
                Ast("a + 2"),
                Incoming(constants = Set("a")),
                Referenced(Set("a")),
                ExpectedResult.ExpressionResult,
                ExpectedWorkingScope.varExp("a", Set("a"))
              )
            )
          )
        )
      )
    )
  }

  test("""WHEN 1 = $p THEN RETURN 1 AS x
         |ELSE RETURN 2 AS x""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""WHEN 1 = $p THEN RETURN 1 AS x
              |ELSE RETURN 2 AS x""".stripMargin),
        Outgoing(variables = Set("x")),
        ExpectedResult.TableResult("x"),
        ExpectedWorkingScope(
          Ast("WHEN 1 = $p THEN RETURN 1 AS x"),
          Outgoing(variables = Set("x")),
          ExpectedResult.TableResult("x"),
          ExpectedWorkingScope.constExp("1 = $p"),
          ExpectedWorkingScope(
            Ast("RETURN 1 AS x"), // query level
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("RETURN 1 AS x"),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope.constExp("1")
            )
          )
        ),
        ExpectedWorkingScope(
          Ast("ELSE RETURN 2 AS x"),
          Outgoing(variables = Set("x")),
          ExpectedResult.TableResult("x"),
          ExpectedWorkingScope(
            Ast("RETURN 2 AS x"), // query level
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("RETURN 2 AS x"),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope.constExp("2")
            )
          )
        )
      )
    )
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN x AS y
         |NEXT
         |WHEN y % 2 = 0 THEN RETURN y * -1 AS x
         |ELSE RETURN y AS x""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""UNWIND [1, 2, 3] AS x
              |RETURN x AS y
              |NEXT
              |WHEN y % 2 = 0 THEN RETURN y * -1 AS x
              |ELSE RETURN y AS x""".stripMargin),
        Outgoing(variables = Set("x")),
        ExpectedResult.TableResult("x"),
        ExpectedWorkingScope(
          Ast("""UNWIND [1, 2, 3] AS x
                |RETURN x AS y""".stripMargin),
          Outgoing(variables = Set("y")),
          ExpectedResult.TableResult("y"),
          ExpectedWorkingScope(
            Ast("UNWIND [1, 2, 3] AS x"),
            Declared(variables = Seq("x")),
            Outgoing(variables = Set("x")),
            ExpectedWorkingScope.constExp("[1, 2, 3]")
          ),
          ExpectedWorkingScope(
            Ast("RETURN x AS y"),
            Incoming(variables = Set("x")),
            Referenced(Set("x")),
            Outgoing(variables = Set("y")),
            ExpectedResult.TableResult("y"),
            ExpectedWorkingScope.varExp("x", Set("x"))
          )
        ),
        ExpectedWorkingScope(
          Ast(
            """WHEN y % 2 = 0 THEN RETURN y * -1 AS x
              |ELSE RETURN y AS x""".stripMargin
          ),
          Incoming(variables = Set("y")),
          Referenced(Set("y")),
          Outgoing(variables = Set("x")),
          ExpectedResult.TableResult("x"),
          ExpectedWorkingScope(
            Ast("WHEN y % 2 = 0 THEN RETURN y * -1 AS x"),
            Incoming(constants = Set("y")),
            Referenced(Set("y")),
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("y % 2 = 0"),
              Incoming(constants = Set("y")),
              Referenced(Set("y")),
              ExpectedWorkingScope.varExp("y", Set("y"))
            ),
            ExpectedWorkingScope(
              Ast("RETURN y * -1 AS x"), // query level
              Incoming(constants = Set("y")),
              Referenced(Set("y")),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope(
                Ast("RETURN y * -1 AS x"),
                Incoming(constants = Set("y")),
                Referenced(Set("y")),
                Outgoing(variables = Set("x")),
                ExpectedResult.TableResult("x"),
                ExpectedWorkingScope(
                  Ast("y * -1"),
                  Incoming(constants = Set("y")),
                  Referenced(Set("y")),
                  ExpectedWorkingScope.varExp("y", Set("y"))
                )
              )
            )
          ),
          ExpectedWorkingScope(
            Ast("ELSE RETURN y AS x"),
            Incoming(constants = Set("y")),
            Referenced(Set("y")),
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("RETURN y AS x"), // query level
              Incoming(constants = Set("y")),
              Referenced(Set("y")),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope(
                Ast("RETURN y AS x"),
                Incoming(constants = Set("y")),
                Referenced(Set("y")),
                Outgoing(variables = Set("x")),
                ExpectedResult.TableResult("x"),
                ExpectedWorkingScope.varExp("y", Set("y"))
              )
            )
          )
        )
      )
    )
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN x AS y
         |NEXT
         |WHEN y % 2 = 0 THEN {
         |  LET x = -1
         |  RETURN y * x AS x
         |}
         |ELSE RETURN y AS x""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""UNWIND [1, 2, 3] AS x
              |RETURN x AS y
              |NEXT
              |WHEN y % 2 = 0 THEN {
              |  LET x = -1
              |  RETURN y * x AS x
              |}
              |ELSE RETURN y AS x""".stripMargin),
        Outgoing(variables = Set("x")),
        ExpectedResult.TableResult("x"),
        ExpectedWorkingScope(
          Ast("""UNWIND [1, 2, 3] AS x
                |RETURN x AS y""".stripMargin),
          Outgoing(variables = Set("y")),
          ExpectedResult.TableResult("y"),
          ExpectedWorkingScope(
            Ast("UNWIND [1, 2, 3] AS x"),
            Declared(variables = Seq("x")),
            Outgoing(variables = Set("x")),
            ExpectedWorkingScope.constExp("[1, 2, 3]")
          ),
          ExpectedWorkingScope(
            Ast("RETURN x AS y"),
            Incoming(variables = Set("x")),
            Referenced(Set("x")),
            Outgoing(variables = Set("y")),
            ExpectedResult.TableResult("y"),
            ExpectedWorkingScope.varExp("x", Set("x"))
          )
        ),
        ExpectedWorkingScope(
          Ast(
            """WHEN y % 2 = 0 THEN {
              |  LET x = -1
              |  RETURN y * x AS x
              |}
              |ELSE RETURN y AS x""".stripMargin
          ),
          Incoming(variables = Set("y")),
          Referenced(Set("y")),
          Outgoing(variables = Set("x")),
          ExpectedResult.TableResult("x"),
          ExpectedWorkingScope(
            Ast("""WHEN y % 2 = 0 THEN {
                  |  LET x = -1
                  |  RETURN y * x AS x
                  |}""".stripMargin),
            Incoming(constants = Set("y")),
            Referenced(Set("y")),
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("y % 2 = 0"),
              Incoming(constants = Set("y")),
              Referenced(Set("y")),
              ExpectedWorkingScope.varExp("y", Set("y"))
            ),
            ExpectedWorkingScope(
              Ast("""LET x = -1
                    |RETURN y * x AS x""".stripMargin), // query level
              Incoming(constants = Set("y")),
              Referenced(Set("y")),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope(
                Ast("LET x = -1"),
                Incoming(constants = Set("y")),
                Declared(variables = Seq("x")),
                Outgoing(constants = Set("y"), variables = Set("x")),
                ExpectedWorkingScope.constExp("-1", Set("y"))
              ),
              ExpectedWorkingScope(
                Ast("RETURN y * x AS x"),
                Incoming(constants = Set("y"), variables = Set("x")),
                Referenced(Set("y", "x")),
                Outgoing(variables = Set("x")),
                ExpectedResult.TableResult("x"),
                ExpectedWorkingScope(
                  Ast("y * x"),
                  Incoming(constants = Set("y", "x")),
                  Referenced(Set("y", "x")),
                  ExpectedWorkingScope.varExp("y", Set("y", "x")),
                  ExpectedWorkingScope.varExp("x", Set("y", "x"))
                )
              )
            )
          ),
          ExpectedWorkingScope(
            Ast("ELSE RETURN y AS x"),
            Incoming(constants = Set("y")),
            Referenced(Set("y")),
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("RETURN y AS x"), // query level
              Incoming(constants = Set("y")),
              Referenced(Set("y")),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope(
                Ast("RETURN y AS x"),
                Incoming(constants = Set("y")),
                Referenced(Set("y")),
                Outgoing(variables = Set("x")),
                ExpectedResult.TableResult("x"),
                ExpectedWorkingScope.varExp("y", Set("y"))
              )
            )
          )
        )
      )
    )
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN x""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""UNWIND [1, 2, 3] AS x
              |RETURN x""".stripMargin),
        Outgoing(variables = Set("x")),
        ExpectedResult.TableResult("x"),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS x"),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("x")),
          ExpectedWorkingScope.constExp("[1, 2, 3]")
        ),
        ExpectedWorkingScope(
          Ast("RETURN x"),
          Incoming(variables = Set("x")),
          Referenced(Set("x")),
          Outgoing(variables = Set("x")),
          ExpectedResult.TableResult("x"),
          ExpectedWorkingScope.varExp("x", Set("x"))
        )
      )
    )
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN x * x AS x2 ORDER BY x2 ASCENDING""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""UNWIND [1, 2, 3] AS x
              |RETURN x * x AS x2 ORDER BY x2 ASCENDING""".stripMargin),
        Outgoing(variables = Set("x2")),
        ExpectedResult.TableResult("x2"),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS x"),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("x")),
          ExpectedWorkingScope.constExp("[1, 2, 3]")
        ),
        ExpectedWorkingScope(
          Ast("RETURN x * x AS x2 ORDER BY x2 ASCENDING"),
          Incoming(variables = Set("x")),
          Referenced(Set("x")),
          Outgoing(variables = Set("x2")),
          ExpectedResult.TableResult("x2"),
          ExpectedWorkingScope(
            Ast("x * x"),
            Incoming(constants = Set("x")),
            Referenced(Set("x")),
            ExpectedWorkingScope.varExp("x", Set("x")),
            ExpectedWorkingScope.varExp("x", Set("x"))
          ),
          ExpectedWorkingScope.varExp("x2", Set("x", "x2"))
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN x * -1 AS a ORDER BY a ASCENDING""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [1, 2, 3] AS x
              |RETURN x * -1 AS a ORDER BY a ASCENDING""".stripMargin),
        Outgoing(variables = Set("a")),
        ExpectedResult.TableResult("a"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS x"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast("RETURN x * -1 AS a ORDER BY a ASCENDING"),
          Incoming(variables = Set("a", "x")),
          Referenced(Set("x")),
          Outgoing(variables = Set("a")),
          ExpectedResult.TableResult("a"),
          ExpectedWorkingScope(
            Ast("x * -1"),
            Incoming(constants = Set("a", "x")),
            Referenced(Set("x")),
            ExpectedWorkingScope.varExp("x", Set("a", "x"))
          ),
          ExpectedWorkingScope.varExp("a", Set("a", "x"))
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |RETURN a * b AS x""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [1, 2, 3] AS b
              |RETURN a * b AS x""".stripMargin),
        Outgoing(variables = Set("x")),
        ExpectedResult.TableResult("x"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS b"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("b")),
          Outgoing(variables = Set("a", "b")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast("RETURN a * b AS x"),
          Incoming(variables = Set("a", "b")),
          Referenced(Set("a", "b")),
          Outgoing(variables = Set("x")),
          ExpectedResult.TableResult("x"),
          ExpectedWorkingScope(
            Ast("a * b"),
            Incoming(constants = Set("a", "b")),
            Referenced(Set("a", "b")),
            ExpectedWorkingScope.varExp("a", Set("a", "b")),
            ExpectedWorkingScope.varExp("b", Set("a", "b"))
          )
        )
      )
    )
  }

  test("""UNWIND [1, 2, 3] AS x
         |LET a = 10, b = x * 2
         |RETURN round(toFloat(a) / b, x, "UP") AS r""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""UNWIND [1, 2, 3] AS x
              |LET a = 10, b = x * 2
              |RETURN round(toFloat(a) / b, x, "UP") AS r""".stripMargin),
        Outgoing(variables = Set("r")),
        ExpectedResult.TableResult("r"),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS x"),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("x")),
          ExpectedWorkingScope.constExp("[1, 2, 3]")
        ),
        ExpectedWorkingScope(
          Ast("LET a = 10, b = x * 2"),
          Incoming(variables = Set("x")),
          Referenced(Set("x")),
          Declared(variables = Seq("a", "b")),
          Outgoing(variables = Set("x", "a", "b")),
          ExpectedWorkingScope.constExp("10", Set("x")),
          ExpectedWorkingScope(
            Ast("x * 2"),
            Incoming(constants = Set("x")),
            Referenced(Set("x")),
            ExpectedWorkingScope.varExp("x", Set("x"))
          )
        ),
        ExpectedWorkingScope(
          Ast("""RETURN round(toFloat(a) / b, x, "UP") AS r"""),
          Incoming(variables = Set("x", "a", "b")),
          Referenced(Set("x", "a", "b")),
          Outgoing(variables = Set("r")),
          ExpectedResult.TableResult("r"),
          ExpectedWorkingScope(
            Ast("""round(toFloat(a) / b, x, "UP")"""),
            Incoming(constants = Set("x", "a", "b")),
            Referenced(Set("x", "a", "b")),
            ExpectedWorkingScope.varExp("a", Set("x", "a", "b")),
            ExpectedWorkingScope.varExp("b", Set("x", "a", "b")),
            ExpectedWorkingScope.varExp("x", Set("x", "a", "b"))
          )
        )
      )
    )
  }

  test("""LET x = 10, y = 20
         |LET m = {a: 10 + x, b: {y: 1, x: y + x}}
         |RETURN m{.a, by: (m.b).y, bx: y + (m.b).x * m.a} AS r""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET x = 10, y = 20
              |LET m = {a: 10 + x, b: {y: 1, x: y + x}}
              |RETURN m{.a, by: (m.b).y, bx: y + (m.b).x * m.a} AS r""".stripMargin),
        Outgoing(variables = Set("r")),
        ExpectedResult.TableResult("r"),
        ExpectedWorkingScope(
          Ast("LET x = 10, y = 20"),
          Declared(variables = Seq("x", "y")),
          Outgoing(variables = Set("x", "y")),
          ExpectedWorkingScope.constExp("10"),
          ExpectedWorkingScope.constExp("20")
        ),
        ExpectedWorkingScope(
          Ast("LET m = {a: 10 + x, b: {y: 1, x: y + x}}"),
          Incoming(variables = Set("x", "y")),
          Referenced(Set("x", "y")),
          Declared(variables = Seq("m")),
          Outgoing(variables = Set("x", "y", "m")),
          ExpectedWorkingScope(
            Ast("{a: 10 + x, b: {y: 1, x: y + x}}"),
            Incoming(constants = Set("x", "y")),
            Referenced(Set("x", "y")),
            ExpectedWorkingScope.varExp("x", Set("x", "y")),
            ExpectedWorkingScope.varExp("y", Set("x", "y")),
            ExpectedWorkingScope.varExp("x", Set("x", "y"))
          )
        ),
        ExpectedWorkingScope(
          Ast("""RETURN m{.a, by: (m.b).y, bx: y + (m.b).x * m.a} AS r"""),
          Incoming(variables = Set("x", "y", "m")),
          Referenced(Set("y", "m")),
          Outgoing(variables = Set("r")),
          ExpectedResult.TableResult("r"),
          ExpectedWorkingScope(
            Ast("""m{.a, by: (m.b).y, bx: y + (m.b).x * m.a}"""),
            Incoming(constants = Set("x", "y", "m")),
            Referenced(Set("y", "m")),
            ExpectedWorkingScope.varExp("m", Set("x", "y", "m")),
            ExpectedWorkingScope.varExp("m", Set("x", "y", "m")),
            ExpectedWorkingScope.varExp("y", Set("x", "y", "m")),
            ExpectedWorkingScope.varExp("m", Set("x", "y", "m")),
            ExpectedWorkingScope.varExp("m", Set("x", "y", "m"))
          )
        )
      )
    )
  }

  test("""LET a = 2
         |UNWIND [1, 2, 3] AS b
         |FILTER WHERE b % a = 1
         |RETURN a * b AS x""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 2
              |UNWIND [1, 2, 3] AS b
              |FILTER WHERE b % a = 1
              |RETURN a * b AS x""".stripMargin),
        Outgoing(variables = Set("x")),
        ExpectedResult.TableResult("x"),
        ExpectedWorkingScope(
          Ast("LET a = 2"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("2")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS b"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("b")),
          Outgoing(variables = Set("a", "b")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast("FILTER WHERE b % a = 1"),
          Incoming(variables = Set("a", "b")),
          Referenced(Set("b", "a")),
          Outgoing(variables = Set("a", "b")),
          ExpectedWorkingScope(
            Ast("b % a = 1"),
            Incoming(constants = Set("a", "b")),
            Referenced(Set("b", "a")),
            ExpectedWorkingScope.varExp("b", Set("a", "b")),
            ExpectedWorkingScope.varExp("a", Set("a", "b"))
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN a * b AS x"),
          Incoming(variables = Set("a", "b")),
          Referenced(Set("a", "b")),
          Outgoing(variables = Set("x")),
          ExpectedResult.TableResult("x"),
          ExpectedWorkingScope(
            Ast("a * b"),
            Incoming(constants = Set("a", "b")),
            Referenced(Set("a", "b")),
            ExpectedWorkingScope.varExp("a", Set("a", "b")),
            ExpectedWorkingScope.varExp("b", Set("a", "b"))
          )
        )
      )
    )
  }

  test("""LET a = 10
         |WITH *, 1 AS b
         |RETURN *""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |WITH *, 1 AS b
              |RETURN *""".stripMargin),
        Outgoing(variables = Set("a", "b")),
        ExpectedResult.TableResult("a", "b"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("WITH *, 1 AS b"),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Declared(variables = Seq("b")),
          Outgoing(variables = Set("a", "b")),
          ExpectedWorkingScope.constExp("1", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast("RETURN *"),
          Incoming(variables = Set("a", "b")),
          Referenced(Set("a", "b")),
          Outgoing(variables = Set("a", "b")),
          ExpectedResult.TableResult("a", "b")
        )
      )
    )
  }

  test("""LET a = 10
         |WITH a + 10 AS a
         |RETURN *""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |WITH a + 10 AS a
              |RETURN *""".stripMargin),
        Outgoing(variables = Set("a")),
        ExpectedResult.TableResult("a"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("WITH a + 10 AS a"),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope(
            Ast("a + 10"),
            Incoming(constants = Set("a")),
            Referenced(Set("a")),
            ExpectedWorkingScope.varExp("a", Set("a"))
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN *"),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Outgoing(variables = Set("a")),
          ExpectedResult.TableResult("a")
        )
      )
    )
  }

  test("""LET a = 10
         |RETURN COLLECT { RETURN a }""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |RETURN COLLECT { RETURN a }""".stripMargin),
        Outgoing(variables = Set("COLLECT { RETURN a }")),
        ExpectedResult.TableResult("COLLECT { RETURN a }"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("RETURN COLLECT { RETURN a }"),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Outgoing(variables = Set("COLLECT { RETURN a }")),
          ExpectedResult.TableResult("COLLECT { RETURN a }"),
          ExpectedWorkingScope(
            Ast("COLLECT { RETURN a }"),
            Incoming(constants = Set("a")),
            Referenced(Set("a")),
            ExpectedWorkingScope(
              Ast("RETURN a"), // this is the query level
              Incoming(constants = Set("a")),
              Referenced(Set("a")),
              Outgoing(variables = Set("a")),
              ExpectedResult.TableResult("a"),
              ExpectedWorkingScope(
                Ast("RETURN a"),
                Incoming(constants = Set("a")),
                Referenced(Set("a")),
                Outgoing(variables = Set("a")),
                ExpectedResult.TableResult("a"),
                ExpectedWorkingScope.varExp("a", Set("a"))
              )
            )
          )
        )
      )
    )
  }

  test("""LET a = 10
         |LET l = [1, a, 3]
         |UNWIND [x IN l + a WHERE x > 2 | a * x] AS b
         |RETURN a * b AS x, l""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |LET l = [1, a, 3]
              |UNWIND [x IN l + a WHERE x > 2 | a * x] AS b
              |RETURN a * b AS x, l""".stripMargin),
        Outgoing(variables = Set("x", "l")),
        ExpectedResult.TableResult("x", "l"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("LET l = [1, a, 3]"),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Declared(variables = Seq("l")),
          Outgoing(variables = Set("a", "l")),
          ExpectedWorkingScope(
            Ast("[1, a, 3]"),
            Incoming(constants = Set("a")),
            Referenced(Set("a")),
            ExpectedWorkingScope.varExp("a", Set("a"))
          )
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [x IN l + a WHERE x > 2 | a * x] AS b"),
          Incoming(variables = Set("a", "l")),
          Referenced(Set("a", "l")),
          Declared(variables = Seq("b")),
          Outgoing(variables = Set("a", "l", "b")),
          ExpectedWorkingScope(
            Ast("[x IN l + a WHERE x > 2 | a * x]"),
            Incoming(constants = Set("a", "l")),
            Declared(constants = Seq("x")),
            Referenced(Set("a", "l")),
            ExpectedWorkingScope(
              Ast("l + a"),
              Incoming(constants = Set("a", "l")),
              Referenced(Set("a", "l")),
              ExpectedWorkingScope.varExp("l", Set("a", "l")),
              ExpectedWorkingScope.varExp("a", Set("a", "l"))
            ),
            ExpectedWorkingScope(
              Ast("x > 2"),
              Incoming(constants = Set("a", "l", "x")),
              Referenced(Set("x")),
              ExpectedWorkingScope.varExp("x", Set("a", "l", "x"))
            ),
            ExpectedWorkingScope(
              Ast("a * x"),
              Incoming(constants = Set("a", "l", "x")),
              Referenced(Set("a", "x")),
              ExpectedWorkingScope.varExp("a", Set("a", "l", "x")),
              ExpectedWorkingScope.varExp("x", Set("a", "l", "x"))
            )
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN a * b AS x, l"),
          Incoming(variables = Set("a", "l", "b")),
          Referenced(Set("a", "b", "l")),
          Outgoing(variables = Set("x", "l")),
          ExpectedResult.TableResult("x", "l"),
          ExpectedWorkingScope(
            Ast("a * b"),
            Incoming(constants = Set("a", "l", "b")),
            Referenced(Set("a", "b")),
            ExpectedWorkingScope.varExp("a", Set("a", "l", "b")),
            ExpectedWorkingScope.varExp("b", Set("a", "l", "b"))
          ),
          ExpectedWorkingScope.varExp("l", Set("a", "l", "b"))
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [a IN [1, a, 3] WHERE a > 2 | a] AS b
         |RETURN b""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [a IN [1, a, 3] WHERE a > 2 | a] AS b
              |RETURN b""".stripMargin),
        Outgoing(variables = Set("b")),
        ExpectedResult.TableResult("b"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [a IN [1, a, 3] WHERE a > 2 | a] AS b"),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Declared(variables = Seq("b")),
          Outgoing(variables = Set("a", "b")),
          ExpectedWorkingScope(
            Ast("[a IN [1, a, 3] WHERE a > 2 | a]"),
            Incoming(constants = Set("a")),
            Declared(constants = Seq("a")),
            Referenced(Set("a")),
            ExpectedWorkingScope(
              Ast("[1, a, 3]"),
              Incoming(constants = Set("a")),
              Referenced(Set("a")),
              ExpectedWorkingScope.varExp("a", Set("a"))
            ),
            ExpectedWorkingScope(
              Ast("a > 2"),
              Incoming(constants = Set("a")),
              Referenced(Set("a")),
              ExpectedWorkingScope.varExp("a", Set("a"))
            ),
            ExpectedWorkingScope.varExp("a", Set("a"))
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN b"),
          Incoming(variables = Set("a", "b")),
          Referenced(Set("b")),
          Outgoing(variables = Set("b")),
          ExpectedResult.TableResult("b"),
          ExpectedWorkingScope.varExp("b", Set("a", "b"))
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [a IN [1, a, 3] | 1] AS b
         |RETURN b""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [a IN [1, a, 3] | 1] AS b
              |RETURN b""".stripMargin),
        Outgoing(variables = Set("b")),
        ExpectedResult.TableResult("b"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [a IN [1, a, 3] | 1] AS b"),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Declared(variables = Seq("b")),
          Outgoing(variables = Set("a", "b")),
          ExpectedWorkingScope(
            Ast("[a IN [1, a, 3] | 1]"),
            Incoming(constants = Set("a")),
            Declared(constants = Seq("a")),
            Referenced(Set("a")),
            ExpectedWorkingScope(
              Ast("[1, a, 3]"),
              Incoming(constants = Set("a")),
              Referenced(Set("a")),
              ExpectedWorkingScope.varExp("a", Set("a"))
            ),
            ExpectedWorkingScope.constExp("1", Set("a"))
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN b"),
          Incoming(variables = Set("a", "b")),
          Referenced(Set("b")),
          Outgoing(variables = Set("b")),
          ExpectedResult.TableResult("b"),
          ExpectedWorkingScope.varExp("b", Set("a", "b"))
        )
      )
    )
  }

  test("""LET a = 10
         |FOREACH ( a IN [1, a, 3] |
         |  CREATE (n)
         |  SET n.p = a
         |)
         |RETURN a""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |FOREACH ( a IN [1, a, 3] |
              |  CREATE (n)
              |  SET n.p = a
              |)
              |RETURN a""".stripMargin),
        Outgoing(variables = Set("a")),
        ExpectedResult.TableResult("a"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("""FOREACH ( a IN [1, a, 3] |
                |  CREATE (n)
                |  SET n.p = a
                |)""".stripMargin),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Declared(constants = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedResult.OmittedResult,
          ExpectedWorkingScope(
            Ast("[1, a, 3]"),
            Incoming(constants = Set("a")),
            Referenced(Set("a")),
            ExpectedWorkingScope.varExp("a", Set("a"))
          ),
          ExpectedWorkingScope(
            Ast("""CREATE (n)
                  |SET n.p = a""".stripMargin),
            Incoming(constants = Set("a")),
            Referenced(Set("a")),
            Outgoing(constants = Set("a"), variables = Set("n")),
            ExpectedResult.OmittedResult,
            ExpectedWorkingScope(
              Ast("CREATE (n)"),
              Incoming(constants = Set("a")),
              Declared(variables = Seq("n")),
              Outgoing(constants = Set("a"), variables = Set("n")),
              ExpectedResult.OmittedResult,
              ExpectedWorkingScope(
                Ast("(n)"),
                PatternIncoming(topology = Set("a"), predicate = Set("a")),
                Declared(variables = Seq("n")),
                Outgoing(variables = Set("n")),
                ExpectedResult.TableResult("n")
              )
            ),
            ExpectedWorkingScope(
              Ast("SET n.p = a"),
              Incoming(constants = Set("a"), variables = Set("n")),
              Referenced(Set("a", "n")),
              Outgoing(constants = Set("a"), variables = Set("n")),
              ExpectedResult.OmittedResult,
              ExpectedWorkingScope(
                Ast("n.p"),
                Incoming(constants = Set("a", "n")),
                Referenced(Set("n")),
                ExpectedWorkingScope.varExp("n", Set("a", "n"))
              ),
              ExpectedWorkingScope.varExp("a", Set("a", "n"))
            )
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN a"),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Outgoing(variables = Set("a")),
          ExpectedResult.TableResult("a"),
          ExpectedWorkingScope.varExp("a", Set("a"))
        )
      )
    )
  }

  test("""LET a = 10
         |RETURN reduce(acc = a, x IN [1, a, 3] | acc * x + 5) AS red""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |RETURN reduce(acc = a, x IN [1, a, 3] | acc * x + 5) AS red""".stripMargin),
        Outgoing(variables = Set("red")),
        ExpectedResult.TableResult("red"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("RETURN reduce(acc = a, x IN [1, a, 3] | acc * x + 5) AS red"),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Outgoing(variables = Set("red")),
          ExpectedResult.TableResult("red"),
          ExpectedWorkingScope(
            Ast("reduce(acc = a, x IN [1, a, 3] | acc * x + 5)"),
            Incoming(constants = Set("a")),
            Referenced(Set("a")),
            Declared(constants = Seq("acc", "x")),
            ExpectedWorkingScope.varExp("a", Set("a")),
            ExpectedWorkingScope(
              Ast("[1, a, 3]"),
              Incoming(constants = Set("a")),
              Referenced(Set("a")),
              ExpectedWorkingScope.varExp("a", Set("a"))
            ),
            ExpectedWorkingScope(
              Ast("acc * x + 5"),
              Incoming(constants = Set("a", "acc", "x")),
              Referenced(Set("acc", "x")),
              ExpectedWorkingScope.varExp("acc", Set("a", "acc", "x")),
              ExpectedWorkingScope.varExp("x", Set("a", "acc", "x"))
            )
          )
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL (a) {
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |}
         |RETURN a, x""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [1, 2, 3] AS b
              |CALL (a) {
              |  UNWIND [1, 2, 3] AS x
              |  RETURN a * x AS x
              |}
              |RETURN a, x""".stripMargin),
        Outgoing(variables = Set("a", "x")),
        ExpectedResult.TableResult("a", "x"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS b"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("b")),
          Outgoing(variables = Set("a", "b")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast("""CALL (a) {
                |  UNWIND [1, 2, 3] AS x
                |  RETURN a * x AS x
                |}""".stripMargin),
          Incoming(variables = Set("a", "b")),
          Referenced(Set("a")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("a", "b", "x")),
          ExpectedWorkingScope(
            Ast("""UNWIND [1, 2, 3] AS x
                  |RETURN a * x AS x""".stripMargin),
            Incoming(constants = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("UNWIND [1, 2, 3] AS x"),
              Incoming(constants = Set("a")),
              Declared(variables = Seq("x")),
              Outgoing(constants = Set("a"), variables = Set("x")),
              ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
            ),
            ExpectedWorkingScope(
              Ast("RETURN a * x AS x"),
              Incoming(constants = Set("a"), variables = Set("x")),
              Referenced(Set("a", "x")),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope(
                Ast("a * x"),
                Incoming(constants = Set("a", "x")),
                Referenced(Set("a", "x")),
                ExpectedWorkingScope.varExp("a", Set("a", "x")),
                ExpectedWorkingScope.varExp("x", Set("a", "x"))
              )
            )
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN a, x"),
          Incoming(variables = Set("a", "b", "x")),
          Referenced(Set("a", "x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedResult.TableResult("a", "x"),
          ExpectedWorkingScope.varExp("a", Set("a", "b", "x")),
          ExpectedWorkingScope.varExp("x", Set("a", "b", "x"))
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL {
         |  WITH a
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |}
         |RETURN a, x""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [1, 2, 3] AS b
              |CALL {
              |  WITH a
              |  UNWIND [1, 2, 3] AS x
              |  RETURN a * x AS x
              |}
              |RETURN a, x""".stripMargin),
        Outgoing(variables = Set("a", "x")),
        ExpectedResult.TableResult("a", "x"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS b"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("b")),
          Outgoing(variables = Set("a", "b")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast("""CALL {
                |  WITH a
                |  UNWIND [1, 2, 3] AS x
                |  RETURN a * x AS x
                |}""".stripMargin),
          Incoming(variables = Set("a", "b")),
          Referenced(Set("a")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("a", "b", "x")),
          ExpectedWorkingScope(
            Ast("""UNWIND [1, 2, 3] AS x
                  |RETURN a * x AS x""".stripMargin),
            Incoming(variables = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("UNWIND [1, 2, 3] AS x"),
              Incoming(variables = Set("a")),
              Declared(variables = Seq("x")),
              Outgoing(variables = Set("a", "x")),
              ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
            ),
            ExpectedWorkingScope(
              Ast("RETURN a * x AS x"),
              Incoming(variables = Set("a", "x")),
              Referenced(Set("a", "x")),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope(
                Ast("a * x"),
                Incoming(constants = Set("a", "x")),
                Referenced(Set("a", "x")),
                ExpectedWorkingScope.varExp("a", Set("a", "x")),
                ExpectedWorkingScope.varExp("x", Set("a", "x"))
              )
            )
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN a, x"),
          Incoming(variables = Set("a", "b", "x")),
          Referenced(Set("a", "x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedResult.TableResult("a", "x"),
          ExpectedWorkingScope.varExp("a", Set("a", "b", "x")),
          ExpectedWorkingScope.varExp("x", Set("a", "b", "x"))
        )
      )
    )
  }

  test("""LET a = 10
         |CALL (a) {
         |  LET b = a
         |  WITH b, a
         |  RETURN a * b AS x
         |}
         |RETURN a, x""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |CALL (a) {
              |  LET b = a
              |  WITH b, a
              |  RETURN a * b AS x
              |}
              |RETURN a, x""".stripMargin),
        Outgoing(variables = Set("a", "x")),
        ExpectedResult.TableResult("a", "x"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("""CALL (a) {
                |  LET b = a
                |  WITH b, a
                |  RETURN a * b AS x
                |}""".stripMargin),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedWorkingScope(
            Ast("""LET b = a
                  |WITH b, a
                  |RETURN a * b AS x""".stripMargin),
            Incoming(constants = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("LET b = a"),
              Incoming(constants = Set("a")),
              Referenced(Set("a")),
              Declared(variables = Seq("b")),
              Outgoing(constants = Set("a"), variables = Set("b")),
              ExpectedWorkingScope.varExp("a", Set("a"))
            ),
            ExpectedWorkingScope(
              Ast("WITH b, a"),
              Incoming(constants = Set("a"), variables = Set("b")),
              Referenced(Set("a", "b")),
              Declared(variables = Seq("b")),
              Outgoing(constants = Set("a"), variables = Set("b")),
              ExpectedWorkingScope.varExp("b", Set("a", "b")),
              ExpectedWorkingScope.varExp("a", Set("a", "b"))
            ),
            ExpectedWorkingScope(
              Ast("RETURN a * b AS x"),
              Incoming(constants = Set("a"), variables = Set("b")),
              Referenced(Set("a", "b")),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope(
                Ast("a * b"),
                Incoming(constants = Set("a", "b")),
                Referenced(Set("a", "b")),
                ExpectedWorkingScope.varExp("a", Set("a", "b")),
                ExpectedWorkingScope.varExp("b", Set("a", "b"))
              )
            )
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN a, x"),
          Incoming(variables = Set("a", "x")),
          Referenced(Set("a", "x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedResult.TableResult("a", "x"),
          ExpectedWorkingScope.varExp("a", Set("a", "x")),
          ExpectedWorkingScope.varExp("x", Set("a", "x"))
        )
      )
    )
  }

  test("""LET a = 10
         |CALL {
         |  WITH a
         |  LET b = a
         |  WITH b, a
         |  RETURN a * b AS x
         |}
         |RETURN a, x""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |CALL {
              |  WITH a
              |  LET b = a
              |  WITH b, a
              |  RETURN a * b AS x
              |}
              |RETURN a, x""".stripMargin),
        Outgoing(variables = Set("a", "x")),
        ExpectedResult.TableResult("a", "x"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("""CALL {
                |  WITH a
                |  LET b = a
                |  WITH b, a
                |  RETURN a * b AS x
                |}""".stripMargin),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedWorkingScope(
            Ast("""LET b = a
                  |WITH b, a
                  |RETURN a * b AS x""".stripMargin),
            Incoming(variables = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("LET b = a"),
              Incoming(variables = Set("a")),
              Referenced(Set("a")),
              Declared(variables = Seq("b")),
              Outgoing(variables = Set("a", "b")),
              ExpectedWorkingScope.varExp("a", Set("a"))
            ),
            ExpectedWorkingScope(
              Ast("WITH b, a"),
              Incoming(variables = Set("a", "b")),
              Referenced(Set("a", "b")),
              Declared(variables = Seq("b", "a")),
              Outgoing(variables = Set("a", "b")),
              ExpectedWorkingScope.varExp("b", Set("a", "b")),
              ExpectedWorkingScope.varExp("a", Set("a", "b"))
            ),
            ExpectedWorkingScope(
              Ast("RETURN a * b AS x"),
              Incoming(variables = Set("a", "b")),
              Referenced(Set("a", "b")),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope(
                Ast("a * b"),
                Incoming(constants = Set("a", "b")),
                Referenced(Set("a", "b")),
                ExpectedWorkingScope.varExp("a", Set("a", "b")),
                ExpectedWorkingScope.varExp("b", Set("a", "b"))
              )
            )
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN a, x"),
          Incoming(variables = Set("a", "x")),
          Referenced(Set("a", "x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedResult.TableResult("a", "x"),
          ExpectedWorkingScope.varExp("a", Set("a", "x")),
          ExpectedWorkingScope.varExp("x", Set("a", "x"))
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL (a) {
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY 2.5 SECONDS THEN FAIL
         |RETURN a, x""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [1, 2, 3] AS b
              |CALL (a) {
              |  UNWIND [1, 2, 3] AS x
              |  RETURN a * x AS x
              |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY 2.5 SECONDS THEN FAIL
              |RETURN a, x""".stripMargin),
        Outgoing(variables = Set("a", "x")),
        ExpectedResult.TableResult("a", "x"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS b"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("b")),
          Outgoing(variables = Set("a", "b")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast("""CALL (a) {
                |  UNWIND [1, 2, 3] AS x
                |  RETURN a * x AS x
                |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY 2.5 SECONDS THEN FAIL""".stripMargin),
          Incoming(variables = Set("a", "b")),
          Referenced(Set("a")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("a", "b", "x")),
          ExpectedWorkingScope(
            Ast("""UNWIND [1, 2, 3] AS x
                  |RETURN a * x AS x""".stripMargin),
            Incoming(constants = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("UNWIND [1, 2, 3] AS x"),
              Incoming(constants = Set("a")),
              Declared(variables = Seq("x")),
              Outgoing(constants = Set("a"), variables = Set("x")),
              ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
            ),
            ExpectedWorkingScope(
              Ast("RETURN a * x AS x"),
              Incoming(constants = Set("a"), variables = Set("x")),
              Referenced(Set("a", "x")),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope(
                Ast("a * x"),
                Incoming(constants = Set("a", "x")),
                Referenced(Set("a", "x")),
                ExpectedWorkingScope.varExp("a", Set("a", "x")),
                ExpectedWorkingScope.varExp("x", Set("a", "x"))
              )
            )
          ),
          ExpectedWorkingScope.constExp("2 + 3"),
          ExpectedWorkingScope.constExp("2.5")
        ),
        ExpectedWorkingScope(
          Ast("RETURN a, x"),
          Incoming(variables = Set("a", "b", "x")),
          Referenced(Set("a", "x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedResult.TableResult("a", "x"),
          ExpectedWorkingScope.varExp("a", Set("a", "b", "x")),
          ExpectedWorkingScope.varExp("x", Set("a", "b", "x"))
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL {
         |  WITH a
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY 2.5 SECONDS THEN FAIL
         |RETURN a, x""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [1, 2, 3] AS b
              |CALL {
              |  WITH a
              |  UNWIND [1, 2, 3] AS x
              |  RETURN a * x AS x
              |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY 2.5 SECONDS THEN FAIL
              |RETURN a, x""".stripMargin),
        Outgoing(variables = Set("a", "x")),
        ExpectedResult.TableResult("a", "x"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS b"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("b")),
          Outgoing(variables = Set("a", "b")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast("""CALL {
                |  WITH a
                |  UNWIND [1, 2, 3] AS x
                |  RETURN a * x AS x
                |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY 2.5 SECONDS THEN FAIL""".stripMargin),
          Incoming(variables = Set("a", "b")),
          Referenced(Set("a")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("a", "b", "x")),
          ExpectedWorkingScope(
            Ast("""UNWIND [1, 2, 3] AS x
                  |RETURN a * x AS x""".stripMargin),
            Incoming(variables = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("UNWIND [1, 2, 3] AS x"),
              Incoming(variables = Set("a")),
              Declared(variables = Seq("x")),
              Outgoing(variables = Set("a", "x")),
              ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
            ),
            ExpectedWorkingScope(
              Ast("RETURN a * x AS x"),
              Incoming(variables = Set("a", "x")),
              Referenced(Set("a", "x")),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope(
                Ast("a * x"),
                Incoming(constants = Set("a", "x")),
                Referenced(Set("a", "x")),
                ExpectedWorkingScope.varExp("a", Set("a", "x")),
                ExpectedWorkingScope.varExp("x", Set("a", "x"))
              )
            )
          ),
          ExpectedWorkingScope.constExp("2 + 3"),
          ExpectedWorkingScope.constExp("2.5")
        ),
        ExpectedWorkingScope(
          Ast("RETURN a, x"),
          Incoming(variables = Set("a", "b", "x")),
          Referenced(Set("a", "x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedResult.TableResult("a", "x"),
          ExpectedWorkingScope.varExp("a", Set("a", "b", "x")),
          ExpectedWorkingScope.varExp("x", Set("a", "b", "x"))
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL (a) {
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY 2.5 SECONDS THEN FAIL REPORT STATUS AS r
         |RETURN a, r""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [1, 2, 3] AS b
              |CALL (a) {
              |  UNWIND [1, 2, 3] AS x
              |  RETURN a * x AS x
              |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY 2.5 SECONDS THEN FAIL REPORT STATUS AS r
              |RETURN a, r""".stripMargin),
        Outgoing(variables = Set("a", "r")),
        ExpectedResult.TableResult("a", "r"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS b"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("b")),
          Outgoing(variables = Set("a", "b")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast(
            """CALL (a) {
              |  UNWIND [1, 2, 3] AS x
              |  RETURN a * x AS x
              |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY 2.5 SECONDS THEN FAIL REPORT STATUS AS r""".stripMargin
          ),
          Incoming(variables = Set("a", "b")),
          Referenced(Set("a")),
          Declared(variables = Seq("x", "r")),
          Outgoing(variables = Set("a", "b", "x", "r")),
          ExpectedWorkingScope(
            Ast("""UNWIND [1, 2, 3] AS x
                  |RETURN a * x AS x""".stripMargin),
            Incoming(constants = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("UNWIND [1, 2, 3] AS x"),
              Incoming(constants = Set("a")),
              Declared(variables = Seq("x")),
              Outgoing(constants = Set("a"), variables = Set("x")),
              ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
            ),
            ExpectedWorkingScope(
              Ast("RETURN a * x AS x"),
              Incoming(constants = Set("a"), variables = Set("x")),
              Referenced(Set("a", "x")),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope(
                Ast("a * x"),
                Incoming(constants = Set("a", "x")),
                Referenced(Set("a", "x")),
                ExpectedWorkingScope.varExp("a", Set("a", "x")),
                ExpectedWorkingScope.varExp("x", Set("a", "x"))
              )
            )
          ),
          ExpectedWorkingScope.constExp("2 + 3"),
          ExpectedWorkingScope.constExp("2.5")
        ),
        ExpectedWorkingScope(
          Ast("RETURN a, r"),
          Incoming(variables = Set("a", "b", "x", "r")),
          Referenced(Set("a", "r")),
          Outgoing(variables = Set("a", "r")),
          ExpectedResult.TableResult("a", "r"),
          ExpectedWorkingScope.varExp("a", Set("a", "b", "x", "r")),
          ExpectedWorkingScope.varExp("r", Set("a", "b", "x", "r"))
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL {
         |  WITH a
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY 2.5 SECONDS THEN FAIL REPORT STATUS AS r
         |RETURN a, r""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [1, 2, 3] AS b
              |CALL {
              |  WITH a
              |  UNWIND [1, 2, 3] AS x
              |  RETURN a * x AS x
              |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY 2.5 SECONDS THEN FAIL REPORT STATUS AS r
              |RETURN a, r""".stripMargin),
        Outgoing(variables = Set("a", "r")),
        ExpectedResult.TableResult("a", "r"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS b"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("b")),
          Outgoing(variables = Set("a", "b")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast(
            """CALL {
              |  WITH a
              |  UNWIND [1, 2, 3] AS x
              |  RETURN a * x AS x
              |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY 2.5 SECONDS THEN FAIL REPORT STATUS AS r""".stripMargin
          ),
          Incoming(variables = Set("a", "b")),
          Referenced(Set("a")),
          Declared(variables = Seq("x", "r")),
          Outgoing(variables = Set("a", "b", "x", "r")),
          ExpectedWorkingScope(
            Ast("""UNWIND [1, 2, 3] AS x
                  |RETURN a * x AS x""".stripMargin),
            Incoming(variables = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("UNWIND [1, 2, 3] AS x"),
              Incoming(variables = Set("a")),
              Declared(variables = Seq("x")),
              Outgoing(variables = Set("a", "x")),
              ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
            ),
            ExpectedWorkingScope(
              Ast("RETURN a * x AS x"),
              Incoming(variables = Set("a", "x")),
              Referenced(Set("a", "x")),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope(
                Ast("a * x"),
                Incoming(constants = Set("a", "x")),
                Referenced(Set("a", "x")),
                ExpectedWorkingScope.varExp("a", Set("a", "x")),
                ExpectedWorkingScope.varExp("x", Set("a", "x"))
              )
            )
          ),
          ExpectedWorkingScope.constExp("2 + 3"),
          ExpectedWorkingScope.constExp("2.5")
        ),
        ExpectedWorkingScope(
          Ast("RETURN a, r"),
          Incoming(variables = Set("a", "b", "x", "r")),
          Referenced(Set("a", "r")),
          Outgoing(variables = Set("a", "r")),
          ExpectedResult.TableResult("a", "r"),
          ExpectedWorkingScope.varExp("a", Set("a", "b", "x", "r")),
          ExpectedWorkingScope.varExp("r", Set("a", "b", "x", "r"))
        )
      )
    )
  }

  test("""LET n = 1
         |LET x = COUNT {
         |  LET a = 1
         |  CALL (a) {
         |    LET n = 3
         |    RETURN a + n AS result
         |  }
         |  RETURN result
         |}
         |RETURN *""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET n = 1
              |LET x = COUNT {
              |  LET a = 1
              |  CALL (a) {
              |    LET n = 3
              |    RETURN a + n AS result
              |  }
              |  RETURN result
              |}
              |RETURN *""".stripMargin),
        Outgoing(variables = Set("n", "x")),
        ExpectedResult.TableResult("n", "x"),
        ExpectedWorkingScope(
          Ast("LET n = 1"),
          Declared(variables = Seq("n")),
          Outgoing(variables = Set("n")),
          ExpectedWorkingScope.constExp("1")
        ),
        ExpectedWorkingScope(
          Ast("""LET x = COUNT { LET a = 1
                |  CALL (a) {
                |    LET n = 3
                |    RETURN a + n AS result
                |  }
                |  RETURN result
                |}""".stripMargin),
          Incoming(variables = Set("n")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("n", "x")),
          ExpectedWorkingScope(
            Ast("""COUNT {
                  |  LET a = 1
                  |  CALL (a) {
                  |    LET n = 3
                  |    RETURN a + n AS result
                  |  }
                  |  RETURN result
                  |}""".stripMargin),
            Incoming(constants = Set("n")),
            ExpectedResult.ExpressionResult,
            ExpectedWorkingScope(
              Ast("""LET a = 1
                    |CALL (a) {
                    |  LET n = 3
                    |  RETURN a + n AS result
                    |}
                    |RETURN result""".stripMargin),
              Incoming(constants = Set("n")),
              Outgoing(variables = Set("result")),
              ExpectedResult.TableResult("result"),
              ExpectedWorkingScope(
                Ast("LET a = 1"),
                Incoming(constants = Set("n")),
                Declared(variables = Seq("a")),
                Outgoing(constants = Set("n"), variables = Set("a")),
                ExpectedWorkingScope.constExp("1", Set("n"))
              ),
              ExpectedWorkingScope(
                Ast("""CALL (a) {
                      |  LET n = 3
                      |  RETURN a + n AS result
                      |}""".stripMargin),
                Incoming(constants = Set("n"), variables = Set("a")),
                Referenced(Set("a")),
                Declared(variables = Seq("result")),
                Outgoing(constants = Set("n"), variables = Set("a", "result")),
                ExpectedWorkingScope(
                  Ast("""LET n = 3
                        |RETURN a + n AS result""".stripMargin),
                  Incoming(constants = Set("a")),
                  Referenced(Set("a")),
                  Outgoing(variables = Set("result")),
                  ExpectedResult.TableResult("result"),
                  ExpectedWorkingScope(
                    Ast("LET n = 3"),
                    Incoming(constants = Set("a")),
                    Declared(variables = Seq("n")),
                    Outgoing(constants = Set("a"), variables = Set("n")),
                    ExpectedWorkingScope.constExp("3", Set("a"))
                  ),
                  ExpectedWorkingScope(
                    Ast("RETURN a + n AS result"),
                    Incoming(constants = Set("a"), variables = Set("n")),
                    Referenced(Set("a", "n")),
                    Outgoing(variables = Set("result")),
                    ExpectedResult.TableResult("result"),
                    ExpectedWorkingScope(
                      Ast("a + n"),
                      Incoming(constants = Set("a", "n")),
                      Referenced(Set("a", "n")),
                      ExpectedWorkingScope.varExp("a", Set("a", "n")),
                      ExpectedWorkingScope.varExp("n", Set("a", "n"))
                    )
                  )
                )
              ),
              ExpectedWorkingScope(
                Ast("RETURN result"),
                Incoming(constants = Set("n"), variables = Set("a", "result")),
                Referenced(Set("result")),
                Outgoing(variables = Set("result")),
                ExpectedResult.TableResult("result"),
                ExpectedWorkingScope.varExp("result", Set("n", "a", "result"))
              )
            )
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN *"),
          Incoming(variables = Set("n", "x")),
          Referenced(Set("n", "x")),
          Outgoing(variables = Set("n", "x")),
          ExpectedResult.TableResult("n", "x")
        )
      )
    )
  }

  test("CALL db.info()") {
    hasScope(
      ExpectedWorkingScope(
        Ast("CALL db.info()"), // query level
        ExpectedResult.TableResultWithNotYetKnownColumns,
        ExpectedWorkingScope(
          Ast("CALL db.info()"),
          ExpectedResult.TableResultWithNotYetKnownColumns
        )
      )
    )
  }

  test("""LET a = 10
         |CALL {
         |  WITH a
         |  LET b = a
         |  WITH b
         |  RETURN b AS x
         |}
         |RETURN a, x""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |CALL {
              |  WITH a
              |  LET b = a
              |  WITH b
              |  RETURN b AS x
              |}
              |RETURN a, x""".stripMargin),
        Outgoing(variables = Set("a", "x")),
        ExpectedResult.TableResult("a", "x"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("""CALL {
                |  WITH a
                |  LET b = a
                |  WITH b
                |  RETURN b AS x
                |}""".stripMargin),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedWorkingScope(
            Ast("""LET b = a
                  |WITH b
                  |RETURN b AS x""".stripMargin),
            Incoming(variables = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("x")),
            ExpectedResult.TableResult("x"),
            ExpectedWorkingScope(
              Ast("LET b = a"),
              Incoming(variables = Set("a")),
              Referenced(Set("a")),
              Declared(variables = Seq("b")),
              Outgoing(variables = Set("a", "b")),
              ExpectedWorkingScope.varExp("a", Set("a"))
            ),
            ExpectedWorkingScope(
              Ast("WITH b"),
              Incoming(variables = Set("a", "b")),
              Referenced(Set("b")),
              Declared(variables = Seq("b")),
              Outgoing(variables = Set("b")),
              ExpectedWorkingScope.varExp("b", Set("a", "b"))
            ),
            ExpectedWorkingScope(
              Ast("RETURN b AS x"),
              Incoming(variables = Set("b")),
              Referenced(Set("b")),
              Outgoing(variables = Set("x")),
              ExpectedResult.TableResult("x"),
              ExpectedWorkingScope.varExp("b", Set("b"))
            )
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN a, x"),
          Incoming(variables = Set("a", "x")),
          Referenced(Set("a", "x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedResult.TableResult("a", "x"),
          ExpectedWorkingScope.varExp("a", Set("a", "x")),
          ExpectedWorkingScope.varExp("x", Set("a", "x"))
        )
      )
    )
  }

  test("CALL db.info() YIELD *") {
    hasScope(
      ExpectedWorkingScope(
        Ast("CALL db.info() YIELD *"), // query level
        ExpectedResult.TableResultWithNotYetKnownColumns,
        ExpectedWorkingScope(
          Ast("CALL db.info() YIELD *"),
          ExpectedResult.TableResultWithNotYetKnownColumns
        )
      )
    )
  }

  test("CALL db.info() YIELD name, id") {
    hasScope(
      ExpectedWorkingScope(
        Ast("CALL db.info() YIELD name, id"), // query level
        Outgoing(variables = Set("name", "id")),
        ExpectedResult.TableResult("name", "id"),
        ExpectedWorkingScope(
          Ast("CALL db.info() YIELD name, id"),
          Declared(variables = Seq("name", "id")),
          Outgoing(variables = Set("name", "id")),
          ExpectedResult.TableResult("name", "id")
        )
      )
    )
  }

  test("""CALL db.info() YIELD name, id
         |RETURN name""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""CALL db.info() YIELD name, id
              |RETURN name""".stripMargin),
        Outgoing(variables = Set("name")),
        ExpectedResult.TableResult("name"),
        ExpectedWorkingScope(
          Ast("CALL db.info() YIELD name, id"),
          Declared(variables = Seq("name", "id")),
          Outgoing(variables = Set("name", "id")),
          ExpectedResult.NoResult
        ),
        ExpectedWorkingScope(
          Ast("RETURN name"),
          Incoming(variables = Set("name", "id")),
          Referenced(Set("name")),
          Outgoing(variables = Set("name")),
          ExpectedResult.TableResult("name"),
          ExpectedWorkingScope.varExp("name", Set("name", "id"))
        )
      )
    )
  }

  test("""LET query = "bob"
         |CALL db.index.fulltext.queryNodes("myIndex", query) YIELD node AS n, score
         |RETURN n ORDER BY score ASCENDING LIMIT 3""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET query = "bob"
              |CALL db.index.fulltext.queryNodes("myIndex", query) YIELD node AS n, score
              |RETURN n ORDER BY score ASCENDING LIMIT 3""".stripMargin),
        Outgoing(variables = Set("n")),
        ExpectedResult.TableResult("n"),
        ExpectedWorkingScope(
          Ast("""LET query = "bob" """),
          Declared(variables = Seq("query")),
          Outgoing(variables = Set("query")),
          ExpectedWorkingScope.constExp(""" "bob" """)
        ),
        ExpectedWorkingScope(
          Ast("""CALL db.index.fulltext.queryNodes("myIndex", query) YIELD node AS n, score"""),
          Incoming(variables = Set("query")),
          Referenced(Set("query")),
          Declared(variables = Seq("n", "score")),
          Outgoing(variables = Set("query", "n", "score")),
          ExpectedResult.NoResult,
          ExpectedWorkingScope.constExp(""" "myIndex" """, Set("query")),
          ExpectedWorkingScope.varExp("query", Set("query"))
        ),
        ExpectedWorkingScope(
          Ast("RETURN n ORDER BY score ASCENDING LIMIT 3"),
          Incoming(variables = Set("query", "n", "score")),
          Referenced(Set("n", "score")),
          Outgoing(variables = Set("n")),
          ExpectedResult.TableResult("n"),
          ExpectedWorkingScope.varExp("n", Set("query", "n", "score")),
          ExpectedWorkingScope.varExp("score", Set("query", "n", "score"))
        )
      )
    )
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN SUM(x) AS s""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""UNWIND [1, 2, 3] AS x
              |RETURN SUM(x) AS s""".stripMargin),
        Outgoing(variables = Set("s")),
        ExpectedResult.TableResult("s"),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS x"),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("x")),
          ExpectedWorkingScope.constExp("[1, 2, 3]")
        ),
        ExpectedWorkingScope(
          Ast("RETURN SUM(x) AS s"),
          Incoming(variables = Set("x")),
          Referenced(Set("x")),
          Outgoing(variables = Set("s")),
          ExpectedResult.TableResult("s"),
          ExpectedWorkingScope(
            Ast("SUM(x)"),
            Incoming(variables = Set("x")),
            Referenced(Set("x")),
            ExpectedWorkingScope.varExp("x", Set("x"))
          )
        )
      )
    )
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN SUM(x) AS s
         |  ORDER BY COUNT(1) ASCENDING""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""UNWIND [1, 2, 3] AS x
              |RETURN SUM(x) AS s
              |  ORDER BY COUNT(1) ASCENDING""".stripMargin),
        Outgoing(variables = Set("s")),
        ExpectedResult.TableResult("s"),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS x"),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("x")),
          ExpectedWorkingScope.constExp("[1, 2, 3]")
        ),
        ExpectedWorkingScope(
          Ast("""RETURN SUM(x) AS s
                |  ORDER BY COUNT(1) ASCENDING""".stripMargin),
          Incoming(variables = Set("x")),
          Referenced(Set("x")),
          Outgoing(variables = Set("s")),
          ExpectedResult.TableResult("s"),
          ExpectedWorkingScope(
            Ast("SUM(x)"),
            Incoming(variables = Set("x")),
            Referenced(Set("x")),
            ExpectedWorkingScope.varExp("x", Set("x"))
          ),
          ExpectedWorkingScope(
            Ast("COUNT(1)"),
            Incoming(constants = Set("s"), variables = Set("x")),
            ExpectedWorkingScope.constExp("1", Set("s", "x"))
          )
        )
      )
    )
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN SUM(x) AS s
         |  ORDER BY s ASCENDING""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""UNWIND [1, 2, 3] AS x
              |RETURN SUM(x) AS s
              |  ORDER BY s ASCENDING""".stripMargin),
        Outgoing(variables = Set("s")),
        ExpectedResult.TableResult("s"),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS x"),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("x")),
          ExpectedWorkingScope.constExp("[1, 2, 3]")
        ),
        ExpectedWorkingScope(
          Ast("""RETURN SUM(x) AS s
                |  ORDER BY s ASCENDING""".stripMargin),
          Incoming(variables = Set("x")),
          Referenced(Set("x")),
          Outgoing(variables = Set("s")),
          ExpectedResult.TableResult("s"),
          ExpectedWorkingScope(
            Ast("SUM(x)"),
            Incoming(variables = Set("x")),
            Referenced(Set("x")),
            ExpectedWorkingScope.varExp("x", Set("x"))
          ),
          ExpectedWorkingScope.varExp("s", Set("s"), Set("x"))
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN a, SUM(x / a) + a * 5 AS s""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [1, 2, 3] AS x
              |RETURN a, SUM(x / a) + a * 5 AS s""".stripMargin),
        Outgoing(variables = Set("a", "s")),
        ExpectedResult.TableResult("a", "s"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS x"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast("RETURN a, SUM(x / a) + a * 5 AS s"),
          Incoming(variables = Set("a", "x")),
          Referenced(Set("a", "x")),
          Outgoing(variables = Set("a", "s")),
          ExpectedResult.TableResult("a", "s"),
          ExpectedWorkingScope.varExp("a", Set("a", "x")),
          ExpectedWorkingScope(
            Ast("SUM(x / a) + a * 5"),
            Incoming(constants = Set("a"), variables = Set("x")),
            Referenced(Set("a", "x")),
            ExpectedWorkingScope(
              Ast("SUM(x / a)"),
              Incoming(constants = Set("a"), variables = Set("x")),
              Referenced(Set("a", "x")),
              ExpectedWorkingScope(
                Ast("x / a"),
                Incoming(constants = Set("a", "x")),
                Referenced(Set("a", "x")),
                ExpectedWorkingScope.varExp("x", Set("a", "x")),
                ExpectedWorkingScope.varExp("a", Set("a", "x"))
              )
            ),
            ExpectedWorkingScope.varExp("a", Set("a"), Set("x"))
          )
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN *, SUM(x) + a AS s""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [1, 2, 3] AS x
              |RETURN *, SUM(x) + a AS s""".stripMargin),
        Outgoing(variables = Set("a", "x", "s")),
        ExpectedResult.TableResult("a", "x", "s"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS x"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast("RETURN *, SUM(x) + a AS s"),
          Incoming(variables = Set("a", "x")),
          Referenced(Set("a", "x")),
          Outgoing(variables = Set("a", "x", "s")),
          ExpectedResult.TableResult("a", "x", "s"),
          ExpectedWorkingScope(
            Ast("SUM(x) + a"),
            Incoming(constants = Set("a", "x")),
            Referenced(Set("a", "x")),
            ExpectedWorkingScope(
              Ast("SUM(x)"),
              Incoming(constants = Set("a", "x")),
              Referenced(Set("x")),
              ExpectedWorkingScope.varExp("x", Set("a", "x"))
            ),
            ExpectedWorkingScope.varExp("a", Set("a", "x"))
          )
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN a, SUM(x / a) + a * 5 AS s
         |  ORDER BY -1 * MAX(a * x) - a ASCENDING""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [1, 2, 3] AS x
              |RETURN a, SUM(x / a) + a * 5 AS s
              |  ORDER BY -1 * MAX(a * x) - a ASCENDING""".stripMargin),
        Outgoing(variables = Set("a", "s")),
        ExpectedResult.TableResult("a", "s"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS x"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast("""RETURN a, SUM(x / a) + a * 5 AS s
                |  ORDER BY -1 * MAX(a * x) - a ASCENDING""".stripMargin),
          Incoming(variables = Set("a", "x")),
          Referenced(Set("a", "x")),
          Outgoing(variables = Set("a", "s")),
          ExpectedResult.TableResult("a", "s"),
          ExpectedWorkingScope.varExp("a", Set("a", "x")),
          ExpectedWorkingScope(
            Ast("SUM(x / a) + a * 5"),
            Incoming(constants = Set("a"), variables = Set("x")),
            Referenced(Set("a", "x")),
            ExpectedWorkingScope(
              Ast("SUM(x / a)"),
              Incoming(constants = Set("a"), variables = Set("x")),
              Referenced(Set("a", "x")),
              ExpectedWorkingScope(
                Ast("x / a"),
                Incoming(constants = Set("a", "x")),
                Referenced(Set("a", "x")),
                ExpectedWorkingScope.varExp("x", Set("a", "x")),
                ExpectedWorkingScope.varExp("a", Set("a", "x"))
              )
            ),
            ExpectedWorkingScope.varExp("a", Set("a"), Set("x"))
          ),
          ExpectedWorkingScope(
            Ast("-1 * MAX(a * x) - a"),
            Incoming(constants = Set("a", "s"), variables = Set("x")),
            Referenced(Set("a", "x")),
            ExpectedWorkingScope(
              Ast("MAX(a * x)"),
              Incoming(constants = Set("a", "s"), variables = Set("x")),
              Referenced(Set("a", "x")),
              ExpectedWorkingScope(
                Ast("a * x"),
                Incoming(constants = Set("a", "x", "s")),
                Referenced(Set("a", "x")),
                ExpectedWorkingScope.varExp("a", Set("a", "x", "s")),
                ExpectedWorkingScope.varExp("x", Set("a", "x", "s"))
              )
            ),
            ExpectedWorkingScope.varExp("a", Set("a", "s"), Set("x"))
          )
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN a, SUM(x / a) + a * 5 AS s
         |  ORDER BY s * MAX(a * x) - a ASCENDING""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [1, 2, 3] AS x
              |RETURN a, SUM(x / a) + a * 5 AS s
              |  ORDER BY s * MAX(a * x) - a ASCENDING""".stripMargin),
        Outgoing(variables = Set("a", "s")),
        ExpectedResult.TableResult("a", "s"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS x"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast("""RETURN a, SUM(x / a) + a * 5 AS s
                |  ORDER BY s * MAX(a * x) - a ASCENDING""".stripMargin),
          Incoming(variables = Set("a", "x")),
          Referenced(Set("a", "x")),
          Outgoing(variables = Set("a", "s")),
          ExpectedResult.TableResult("a", "s"),
          ExpectedWorkingScope.varExp("a", Set("a", "x")),
          ExpectedWorkingScope(
            Ast("SUM(x / a) + a * 5"),
            Incoming(constants = Set("a"), variables = Set("x")),
            Referenced(Set("a", "x")),
            ExpectedWorkingScope(
              Ast("SUM(x / a)"),
              Incoming(constants = Set("a"), variables = Set("x")),
              Referenced(Set("a", "x")),
              ExpectedWorkingScope(
                Ast("x / a"),
                Incoming(constants = Set("a", "x")),
                Referenced(Set("a", "x")),
                ExpectedWorkingScope.varExp("x", Set("a", "x")),
                ExpectedWorkingScope.varExp("a", Set("a", "x"))
              )
            ),
            ExpectedWorkingScope.varExp("a", Set("a"), Set("x"))
          ),
          ExpectedWorkingScope(
            Ast("s * MAX(a * x) - a"),
            Incoming(constants = Set("a", "s"), variables = Set("x")),
            Referenced(Set("a", "s", "x")),
            ExpectedWorkingScope.varExp("s", Set("a", "s"), Set("x")),
            ExpectedWorkingScope(
              Ast("MAX(a * x)"),
              Incoming(constants = Set("a", "s"), variables = Set("x")),
              Referenced(Set("a", "x")),
              ExpectedWorkingScope(
                Ast("a * x"),
                Incoming(constants = Set("a", "x", "s")),
                Referenced(Set("a", "x")),
                ExpectedWorkingScope.varExp("a", Set("a", "x", "s")),
                ExpectedWorkingScope.varExp("x", Set("a", "x", "s"))
              )
            ),
            ExpectedWorkingScope.varExp("a", Set("a", "s"), Set("x"))
          )
        )
      )
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN a AS g, SUM(x / a) + a * 5 AS s
         |  ORDER BY s * MAX(g * x) - a ASCENDING""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |UNWIND [1, 2, 3] AS x
              |RETURN a AS g, SUM(x / a) + a * 5 AS s
              |  ORDER BY s * MAX(g * x) - a ASCENDING""".stripMargin),
        Outgoing(variables = Set("g", "s")),
        ExpectedResult.TableResult("g", "s"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS x"),
          Incoming(variables = Set("a")),
          Declared(variables = Seq("x")),
          Outgoing(variables = Set("a", "x")),
          ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
        ),
        ExpectedWorkingScope(
          Ast("""RETURN a AS g, SUM(x / a) + a * 5 AS s
                |  ORDER BY s * MAX(g * x) - a ASCENDING""".stripMargin),
          Incoming(variables = Set("a", "x")),
          Referenced(Set("a", "x")),
          Outgoing(variables = Set("g", "s")),
          ExpectedResult.TableResult("g", "s"),
          ExpectedWorkingScope.varExp("a", Set("a", "x")),
          ExpectedWorkingScope(
            Ast("SUM(x / a) + a * 5"),
            Incoming(constants = Set("a"), variables = Set("x")),
            Referenced(Set("a", "x")),
            ExpectedWorkingScope(
              Ast("SUM(x / a)"),
              Incoming(constants = Set("a"), variables = Set("x")),
              Referenced(Set("a", "x")),
              ExpectedWorkingScope(
                Ast("x / a"),
                Incoming(constants = Set("a", "x")),
                Referenced(Set("a", "x")),
                ExpectedWorkingScope.varExp("x", Set("a", "x")),
                ExpectedWorkingScope.varExp("a", Set("a", "x"))
              )
            ),
            ExpectedWorkingScope.varExp("a", Set("a"), Set("x"))
          ),
          ExpectedWorkingScope(
            Ast("s * MAX(g * x) - a"),
            Incoming(constants = Set("a", "g", "s"), variables = Set("x")),
            Referenced(Set("a", "g", "s", "x")),
            ExpectedWorkingScope.varExp("s", Set("a", "g", "s"), Set("x")),
            ExpectedWorkingScope(
              Ast("MAX(g * x)"),
              Incoming(constants = Set("a", "g", "s"), variables = Set("x")),
              Referenced(Set("g", "x")),
              ExpectedWorkingScope(
                Ast("g * x"),
                Incoming(constants = Set("a", "g", "x", "s")),
                Referenced(Set("g", "x")),
                ExpectedWorkingScope.varExp("g", Set("a", "g", "x", "s")),
                ExpectedWorkingScope.varExp("x", Set("a", "g", "x", "s"))
              )
            ),
            ExpectedWorkingScope.varExp("a", Set("a", "g", "s"), Set("x"))
          )
        )
      )
    )
  }

  test("""UNWIND [1, 2, 3] AS a
         |CALL (a) {
         |  UNWIND [1, 2, 3] AS x
         |  RETURN COUNT(x) * a AS s
         |}
         |RETURN a, AVG(s) AS s""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""UNWIND [1, 2, 3] AS a
              |CALL (a) {
              |  UNWIND [1, 2, 3] AS x
              |  RETURN COUNT(x) * a AS s
              |}
              |RETURN a, AVG(s) AS s""".stripMargin),
        Outgoing(variables = Set("a", "s")),
        ExpectedResult.TableResult("a", "s"),
        ExpectedWorkingScope(
          Ast("UNWIND [1, 2, 3] AS a"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("[1, 2, 3]")
        ),
        ExpectedWorkingScope(
          Ast("""CALL (a) {
                |  UNWIND [1, 2, 3] AS x
                |  RETURN COUNT(x) * a AS s
                |}""".stripMargin),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Declared(variables = Seq("s")),
          Outgoing(variables = Set("a", "s")),
          ExpectedWorkingScope(
            Ast("""UNWIND [1, 2, 3] AS x
                  |RETURN COUNT(x) * a AS s""".stripMargin),
            Incoming(constants = Set("a")),
            Referenced(Set("a")),
            Outgoing(variables = Set("s")),
            ExpectedResult.TableResult("s"),
            ExpectedWorkingScope(
              Ast("UNWIND [1, 2, 3] AS x"),
              Incoming(constants = Set("a")),
              Declared(variables = Seq("x")),
              Outgoing(constants = Set("a"), variables = Set("x")),
              ExpectedWorkingScope.constExp("[1, 2, 3]", Set("a"))
            ),
            ExpectedWorkingScope(
              Ast("RETURN COUNT(x) * a AS s"),
              Incoming(constants = Set("a"), variables = Set("x")),
              Referenced(Set("a", "x")),
              Outgoing(variables = Set("s")),
              ExpectedResult.TableResult("s"),
              ExpectedWorkingScope(
                Ast("COUNT(x) * a"),
                Incoming(constants = Set("a"), variables = Set("x")),
                Referenced(Set("a", "x")),
                ExpectedWorkingScope(
                  Ast("COUNT(x)"),
                  Incoming(constants = Set("a"), variables = Set("x")),
                  Referenced(Set("x")),
                  ExpectedWorkingScope.varExp("x", Set("a", "x"))
                ),
                ExpectedWorkingScope.varExp("a", Set("a"), Set("x"))
              )
            )
          )
        ),
        ExpectedWorkingScope(
          Ast("RETURN a, AVG(s) AS s"),
          Incoming(variables = Set("a", "s")),
          Referenced(Set("a", "s")),
          Outgoing(variables = Set("a", "s")),
          ExpectedResult.TableResult("a", "s"),
          ExpectedWorkingScope.varExp("a", Set("a", "s")),
          ExpectedWorkingScope(
            Ast("AVG(s)"),
            Incoming(constants = Set("a"), variables = Set("s")),
            Referenced(Set("s")),
            ExpectedWorkingScope.varExp("s", Set("a", "s"))
          )
        )
      )
    )
  }

  test(
    """WITH "d" AS word
      |RETURN any(prefix IN ["a", "b", "c", word] WHERE word = prefix) AS check""".stripMargin
  ) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""WITH "d" AS word
              |RETURN any(prefix IN ["a", "b", "c", word] WHERE word = prefix) AS check""".stripMargin), // query level
        Outgoing(variables = Set("check")),
        ExpectedResult.TableResult("check"),
        ExpectedWorkingScope(
          Ast("""WITH "d" AS word"""),
          Declared(variables = Seq("word")),
          Outgoing(variables = Set("word")),
          ExpectedWorkingScope.constExp("\"d\"")
        ),
        ExpectedWorkingScope(
          Ast("""RETURN any(prefix IN ["a", "b", "c", word] WHERE word = prefix) AS check""".stripMargin),
          Incoming(variables = Set("word")),
          Referenced(Set("word")),
          Outgoing(variables = Set("check")),
          ExpectedResult.TableResult("check"),
          ExpectedWorkingScope(
            Ast("""any(prefix IN ["a", "b", "c", word] WHERE word = prefix)""".stripMargin),
            Incoming(constants = Set("word")),
            Referenced(Set("word")),
            Declared(Seq("prefix")),
            ExpectedWorkingScope(
              Ast("""["a", "b", "c", word]"""),
              Incoming(constants = Set("word")),
              Referenced(Set("word")),
              ExpectedWorkingScope.varExp("word", Set("word"))
            ),
            ExpectedWorkingScope(
              Ast("word = prefix"),
              Incoming(constants = Set("prefix", "word")),
              Referenced(Set("prefix", "word")),
              ExpectedWorkingScope.varExp("word", incomingConstants = Set("prefix", "word")),
              ExpectedWorkingScope.varExp("prefix", incomingConstants = Set("prefix", "word"))
            )
          )
        )
      )
    )
  }

  test("""LET a = 10
         |RETURN allReduce(acc = 0, x IN [1, 3, a] | acc + x, acc < 10) AS red""".stripMargin) {
    hasScope(
      ExpectedWorkingScope(
        Ast("""LET a = 10
              |RETURN allReduce(acc = 0, x IN [1, 3, a] | acc + x, acc < 10) AS red""".stripMargin),
        Outgoing(variables = Set("red")),
        ExpectedResult.TableResult("red"),
        ExpectedWorkingScope(
          Ast("LET a = 10"),
          Declared(variables = Seq("a")),
          Outgoing(variables = Set("a")),
          ExpectedWorkingScope.constExp("10")
        ),
        ExpectedWorkingScope(
          Ast("RETURN allReduce(acc = 0, x IN [1, 3, a] | acc + x, acc < 10) AS red"),
          Incoming(variables = Set("a")),
          Referenced(Set("a")),
          Outgoing(variables = Set("red")),
          ExpectedResult.TableResult("red"),
          ExpectedWorkingScope(
            Ast("allReduce(acc = 0, x IN [1, 3, a] | acc + x, acc < 10)"),
            Incoming(constants = Set("a")),
            Referenced(Set("a")),
            Declared(constants = Seq("acc", "x")),
            ExpectedWorkingScope.constExp("0", Set("a")),
            ExpectedWorkingScope(
              Ast("[1, 3, a]"),
              Incoming(constants = Set("a")),
              Referenced(Set("a")),
              ExpectedWorkingScope.varExp("a", Set("a"))
            ),
            ExpectedWorkingScope(
              Ast("acc + x"),
              Incoming(constants = Set("a", "acc", "x")),
              Referenced(Set("acc", "x")),
              ExpectedWorkingScope.varExp("acc", Set("a", "acc", "x")),
              ExpectedWorkingScope.varExp("x", Set("a", "acc", "x"))
            ),
            ExpectedWorkingScope(
              Ast("acc < 10"),
              Incoming(constants = Set("a", "acc")),
              Referenced(Set("acc")),
              ExpectedWorkingScope.varExp("acc", Set("a", "acc"))
            )
          )
        )
      )
    )
  }
}
