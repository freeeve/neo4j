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

import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase

class ProcedureCallParserTest extends AstParsingTestBase {

  test("CALL foo") {
    parsesTo[Clause](call(Seq.empty, "foo", None))
  }

  test("CALL foo()") {
    parsesTo[Clause](call(Seq.empty, "foo", Some(Seq.empty)))
  }

  test("CALL foo('Test', 1+2)") {
    parsesTo[Clause](call(Seq.empty, "foo", Some(Vector(literalString("Test"), add(literalInt(1), literalInt(2))))))
  }

  test("CALL foo.bar.baz('Test', 1+2)") {
    parsesTo[Clause](call(
      List("foo", "bar"),
      "baz",
      Some(Vector(literalString("Test"), add(literalInt(1), literalInt(2))))
    ))
  }

  test("CALL foo YIELD bar") {
    parsesTo[Clause](call(Seq.empty, "foo", None, Some(Seq(varFor("bar")))))
  }

  test("CALL foo YIELD bar, baz") {
    parsesTo[Clause](call(Seq.empty, "foo", None, Some(Seq(varFor("bar"), varFor("baz")))))
  }

  test("CALL foo() YIELD bar") {
    parsesTo[Clause](call(Seq.empty, "foo", Some(Seq.empty), Some(Seq(varFor("bar")))))
  }

  test("CALL foo() YIELD bar, baz") {
    parsesTo[Clause](call(Seq.empty, "foo", Some(Seq.empty), Some(Seq(varFor("bar"), varFor("baz")))))
  }

  test("CALL foo() YIELD bar, baz WHERE true") {
    parsesTo[Clause](call(
      Seq.empty,
      "foo",
      Some(Seq.empty),
      Some(Seq(varFor("bar"), varFor("baz"))),
      Some(trueLiteral)
    ))
  }

  test("procedure parameters without comma separation should not parse") {
    "CALL foo('test' 42)" should notParse[Clause]
  }

  test("procedure parameters with invalid start comma should not parse") {
    "CALL foo(, 'test', 42)" should notParse[Clause]
  }

  test("Not standalone in UNION") {
    val query =
      """
      CALL db.labels() YIELD label
      UNION
      CALL db.labels() YIELD label
      RETURN label AS label
      """
    query should parseIn[Statement] {
      _ =>
        _.toAst(
          union(
            singleQuery(
              call(Seq("db"), "labels", Some(Seq.empty), Some(Seq(varFor("label"))))
            ),
            singleQuery(
              call(Seq("db"), "labels", Some(Seq.empty), Some(Seq(varFor("label")))),
              return_(varFor("label").as("label"))
            )
          )
        )
    }
  }

  test("Not standalone in NEXT") {
    val query =
      """
      CALL db.labels() YIELD label
      NEXT
      CALL db.labels() YIELD label
      RETURN label AS label
      """
    query should parseIn[Statement] {
      case Cypher5 =>
        _.withSyntaxError("""Invalid input 'NEXT': expected ',', 'AS', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WHERE', 'WITH' or <EOF> (line 3, column 7 (offset: 42))
                            |"      NEXT"
                            |       ^""".stripMargin)
      case _ =>
        _.toAst(
          nextStatement(
            singleQuery(
              call(Seq("db"), "labels", Some(Seq.empty), Some(Seq(varFor("label"))))
            ),
            singleQuery(
              call(Seq("db"), "labels", Some(Seq.empty), Some(Seq(varFor("label")))),
              return_(varFor("label").as("label"))
            )
          )
        )
    }
  }

  test("Not standalone in inline subquery") {
    val query =
      """
      CALL () {
        CALL db.labels() YIELD label
      }
      RETURN *
      """
    query should parseIn[Statement] {
      _ =>
        _.toAst(
          singleQuery(
            scopeClauseSubqueryCall(
              false,
              Seq.empty,
              call(Seq("db"), "labels", Some(Seq.empty), Some(Seq(varFor("label"))))
            ),
            returnAll
          )
        )
    }
  }

  test("Is standalone") {
    val query =
      """
      CALL db.labels() YIELD label
      """
    query should parseIn[Statement] {
      _ =>
        _.toAst(singleQuery(call(Seq("db"), "labels", Some(Seq.empty), Some(Seq(varFor("label"))), standalone = true)))
    }
  }

  // OPTIONAL CALL

  test("OPTIONAL CALL foo") {
    parsesTo[Clause](optCall(Seq.empty, "foo", None))
  }

  test("OPTIONAL CALL foo()") {
    parsesTo[Clause](optCall(Seq.empty, "foo", Some(Seq.empty)))
  }

  test("OPTIONAL CALL foo('Test', 1+2)") {
    parsesTo[Clause](optCall(Seq.empty, "foo", Some(Vector(literalString("Test"), add(literalInt(1), literalInt(2))))))
  }

  test("OPTIONAL CALL foo.bar.baz('Test', 1+2)") {
    parsesTo[Clause](optCall(
      List("foo", "bar"),
      "baz",
      Some(Vector(literalString("Test"), add(literalInt(1), literalInt(2))))
    ))
  }

  test("OPTIONAL CALL foo YIELD bar") {
    parsesTo[Clause](optCall(Seq.empty, "foo", None, Some(Seq(varFor("bar")))))
  }

  test("OPTIONAL CALL foo YIELD bar, baz") {
    parsesTo[Clause](optCall(Seq.empty, "foo", None, Some(Seq(varFor("bar"), varFor("baz")))))
  }

  test("OPTIONAL CALL foo() YIELD bar") {
    parsesTo[Clause](optCall(Seq.empty, "foo", Some(Seq.empty), Some(Seq(varFor("bar")))))
  }

  test("OPTIONAL CALL foo() YIELD bar, baz") {
    parsesTo[Clause](optCall(Seq.empty, "foo", Some(Seq.empty), Some(Seq(varFor("bar"), varFor("baz")))))
  }

  test("optional call procedure parameters without comma separation should not parse") {
    "OPTIONAL CALL foo('test' 42)" should notParse[Clause]
  }

  test("optional call procedure parameters with invalid start comma should not parse") {
    "OPTIONAL CALL foo(, 'test', 42)" should notParse[Clause]
  }
}
