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
package org.neo4j.cypher.internal.frontend

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.semantics.FeatureError
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.LocalCallables
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.gqlstatus.GqlHelper

class LocalCallablesSemanticAnalysisTest
    extends CypherFunSuite
    with NameBasedSemanticAnalysisTestSuite {

  def errorFeatureRequired(offset: Int, line: Int, column: Int): FeatureError =
    FeatureError.notAvailableInThisImplementation(
      LocalCallables,
      "The DEFINE keyword",
      InputPosition(offset, line, column)
    )

  def runWithoutLC(): AnalysisAssertions = {
    runWith(disabledCypherVersions = Set(CypherVersion.Cypher5))
  }

  def runWithLC(): AnalysisAssertions = {
    runWith(disabledCypherVersions = Set(CypherVersion.Cypher5), LocalCallables)
  }

  /*
   * BEGIN: Test feature flag
   * vvvvvvvvvvvvvvvvvvvvvvvv
   */
  test("""DEFINE PROCEDURE foo.bar() { FINISH }
         |
         |CALL foo.bar()
         |FINISH
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(0, 1, 1))
  }

  test("""DEFINE FUNCTION foo.bar() = 1
         |
         |RETURN foo.bar() AS x
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(0, 1, 1))
  }

  test("""DEFINE FUNCTION hej(n) = EXISTS { MATCH (n)-->(:B) }
         |
         |MATCH (a:A) WHERE hej(a)
         |RETURN *
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(0, 1, 1))
  }

  test("""DEFINE FUNCTION hej(n :: NODE) = EXISTS { MATCH (n)-->(:B) }
         |
         |MATCH (a:A) WHERE hej(a)
         |RETURN *
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(0, 1, 1))
  }

  test("""DEFINE FUNCTION hej() =
         |  EXISTS {
         |    DEFINE FUNCTION nested() = 1
         |    MATCH (:A)-->(:B {p: nested() })
         |  }
         |
         |MATCH (a:A) WHERE hej()
         |RETURN *
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(0, 1, 1))
  }

  test("""RETURN head(COLLECT {
         |  DEFINE FUNCTION foo() = "foo"
         |
         |  RETURN foo() AS x
         |}) AS foo
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(34 - 1 * 10, 2, 3))
  }

  test("""DEFINE PROCEDURE unused() {
         |  DEFINE PROCEDURE foo.bar(x :: INT) { FINISH }
         |  DEFINE FUNCTION foo.one() = 1
         |
         |  CALL foo.bar(foo.one())
         |  FINISH
         |}
         |
         |FINISH
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(0, 1, 1))
  }

  test("""DEFINE PROCEDURE foo.bar(x :: INT) { FINISH }
         |
         |{
         |  DEFINE FUNCTION foo.one() = 1
         |
         |  CALL foo.bar(foo.one())
         |  FINISH
         |}
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(0, 1, 1), errorFeatureRequired(81 - 3 * 10, 4, 3))
  }

  test("""{
         |  DEFINE PROCEDURE foo.bar() { FINISH }
         |
         |  CALL foo.bar()
         |  FINISH
         |}
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(14 - 1 * 10, 2, 3))
  }

  test("""{
         |  DEFINE FUNCTION one() = 1
         |
         |  RETURN one() AS x
         |}
         |UNION
         |{
         |  RETURN 2 AS x
         |}
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(14 - 1 * 10, 2, 3))
  }

  test("""{
         |  RETURN 1 AS x
         |}
         |UNION
         |{
         |  DEFINE PROCEDURE two() { RETURN 2 AS foo }
         |
         |  CALL two() YIELD foo AS x
         |  RETURN x
         |}
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(80 - 5 * 10, 6, 3))
  }

  test("""{
         |  DEFINE FUNCTION one() = 1
         |
         |  RETURN one() AS x
         |}
         |
         |NEXT
         |
         |{
         |  RETURN 2 AS x
         |}
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(14 - 1 * 10, 2, 3))
  }

  test("""{
         |  RETURN 1 AS x
         |}
         |
         |NEXT
         |
         |{
         |  DEFINE PROCEDURE two() { RETURN 2 AS foo }
         |
         |  CALL two() YIELD foo AS x
         |  RETURN x
         |}
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(101 - 7 * 10, 8, 3))
  }

  test("""WHEN 1 = 1 THEN {
         |  DEFINE FUNCTION one() = 1
         |
         |  RETURN one() AS x
         |}
         |ELSE RETURN 2 AS x
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(30 - 1 * 10, 2, 3))
  }

  test("""WHEN 1 = 1 THEN RETURN 1 AS x
         |WHEN 1 = head(COLLECT {
         |  DEFINE FUNCTION two() = 2
         |
         |  RETURN two() AS x
         |}) THEN RETURN 2 AS x
         |ELSE RETURN 3 AS x
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(76 - 2 * 10, 3, 3))
  }

  test("""WHEN 1 = 1 THEN RETURN 1 AS x
         |WHEN 1 = 2 THEN {
         |  DEFINE FUNCTION two() = 2
         |
         |  RETURN two() AS x
         |}
         |ELSE RETURN 3 AS x
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(70 - 2 * 10, 3, 3))
  }

  test("""WHEN 1 = 1 THEN RETURN 1 AS x
         |WHEN 1 = 2 THEN RETURN 2 AS x
         |ELSE {
         |  DEFINE FUNCTION thr33() = 3
         |
         |  RETURN thr33() AS x
         |}
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(99 - 3 * 10, 4, 3))
  }

  test("""CALL {
         |  DEFINE FUNCTION foo.bar() = 1
         |
         |  RETURN foo.bar() AS error
         |}
         |
         |FINISH
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(19 - 1 * 10, 2, 3))
  }

  test("""CALL () {
         |  DEFINE FUNCTION foo.bar() = 1
         |
         |  RETURN foo.bar() AS error
         |}
         |
         |FINISH
         |""".stripMargin) {
    runWithoutLC().hasErrors(errorFeatureRequired(22 - 1 * 10, 2, 3))
  }

  /* ^^^^^^^^^^^^^^^^^^^^^^
   * END: Test feature flag
   */

  test("""DEFINE PROCEDURE foo.baz() { FINISH }
         |
         |CALL foo.baz()
         |FINISH
         |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  for {
    (kind, gqlError) <- Seq("procedure" -> GqlHelper.getGql42001_42NAF(), "function" -> GqlHelper.getGql42001_42NAG())
    keyword = kind.toUpperCase
    msg = s"USE clause is not supported in local $kind definitions."
    offDiff = kind.length - "$keyword".length
  } {
    test(s"""DEFINE $keyword foo.baz() {
            |  USE graph
            |  FINISH
            |}
            |
            |CALL foo.baz()
            |FINISH
            |""".stripMargin) {
      runWithLC().hasError(gqlError, msg, InputPosition(43 - 1 * 13 + 1 * offDiff, 2, 3))
    }

    test(s"""DEFINE $keyword foo.baz() {
            |  DEFINE $keyword foo.bar() {
            |    USE graph
            |    FINISH
            |  }
            |
            |  FINISH
            |}
            |
            |CALL foo.baz()
            |FINISH
            |""".stripMargin) {
      runWithLC().hasError(gqlError, msg, InputPosition(88 - 2 * 13 + 2 * offDiff, 3, 5))
    }

    /*
    // This test is also an invalid query, but it runs into a different error on embedded testing
    test(s"""DEFINE $keyword foo.baz() {
            |  CALL () {
            |    USE graph
            |    FINISH
            |  }
            |
            |  FINISH
            |}
            |
            |CALL foo.baz()
            |FINISH
            |""".stripMargin) {
      runWithLC().hasError(gqlError, msg, InputPosition(70 - 2*13 + 1*offDiff, 3, 5))
    }
     */
    test(s"""DEFINE $keyword foo.baz() {
            |  USE graph
            |  FINISH
            |  UNION
            |  USE graph
            |  FINISH
            |}
            |
            |CALL foo.baz()
            |FINISH
            |""".stripMargin) {
      runWithLC().hasError(gqlError, msg, InputPosition(43 - 1 * 13 + 1 * offDiff, 2, 3))
    }

    test(s"""DEFINE $keyword foo.baz() {
            |  DEFINE $keyword foo.bar() {
            |    RETURN 1 AS x
            |
            |    NEXT
            |
            |    USE graph
            |    FINISH
            |  }
            |
            |  FINISH
            |}
            |
            |CALL foo.baz()
            |FINISH
            |""".stripMargin) {
      runWithLC().hasError(gqlError, msg, InputPosition(169 - 6 * 13 + 2 * offDiff, 7, 5))
    }

    test(s"""DEFINE $keyword foo.baz(x) {
            |  WHEN x = 1 THEN RETURN 1 AS y
            |  WHEN x = 2 THEN {
            |    DEFINE $keyword foo.bar() {
            |      RETURN 1 AS z
            |
            |      NEXT
            |
            |      USE graph
            |      RETURN 2 AS z
            |    }
            |
            |    RETURN 2 AS y
            |  }
            |  ELSE RETURN 3 AS y
            |}
            |
            |CALL foo.baz(1)
            |FINISH
            |""".stripMargin) {
      runWithLC().hasError(gqlError, msg, InputPosition(256 - 8 * 13 + 2 * offDiff, 9, 7))
    }
  }
}
