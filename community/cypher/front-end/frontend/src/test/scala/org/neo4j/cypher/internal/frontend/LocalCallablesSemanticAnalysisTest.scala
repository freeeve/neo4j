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
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.semantics.FeatureError
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.LocalCallables
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.VariableChecking
import org.neo4j.cypher.internal.frontend.phases.FieldSignature
import org.neo4j.cypher.internal.frontend.phases.ProcedureReadOnlyAccess
import org.neo4j.cypher.internal.frontend.phases.ProcedureSignature
import org.neo4j.cypher.internal.frontend.phases.QueryLanguage
import org.neo4j.cypher.internal.frontend.phases.QueryLanguage.Cypher25
import org.neo4j.cypher.internal.frontend.phases.ScopedProcedureSignatureResolver
import org.neo4j.cypher.internal.frontend.phases.TryRewriteProcedureCalls
import org.neo4j.cypher.internal.frontend.phases.UserFunctionSignature
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.ExtractLocalDefinitions
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.ScopeSurveyor
import org.neo4j.cypher.internal.notification.SubqueryVariableShadowing
import org.neo4j.cypher.internal.util.FunctionName
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.ProcedureName
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.CTList
import org.neo4j.cypher.internal.util.symbols.CTNode
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.gqlstatus.GqlHelper

import scala.jdk.CollectionConverters.SeqHasAsJava

class LocalCallablesSemanticAnalysisTest
    extends CypherFunSuite
    with NameWithPositionCaretBasedSemanticAnalysisTestSuite with AstConstructionTestSupport {

  def errorFeatureRequired(offset: Int, line: Int, column: Int): FeatureError =
    FeatureError.notAvailableInThisImplementation(
      LocalCallables,
      "The DEFINE keyword",
      InputPosition(offset, line, column)
    )

  def errorFeatureRequired(p: InputPosition): FeatureError =
    FeatureError.notAvailableInThisImplementation(
      LocalCallables,
      "The DEFINE keyword",
      p
    )

  private val makeResolverMock: ScopedProcedureSignatureResolver = {
    val name = procedureName("my", "proc42", "foo")
    val signatureInputs = IndexedSeq(FieldSignature("a", CTInteger))
    val signatureOutputs = Some(IndexedSeq(FieldSignature("x", CTInteger), FieldSignature("y", CTList(CTNode))))

    val signature =
      ProcedureSignature(name, signatureInputs, signatureOutputs, None, ProcedureReadOnlyAccess, id = 42)

    val procSignatureLookup: ProcedureName => ProcedureSignature = _ => signature
    val funcSignatureLookup: FunctionName => Option[UserFunctionSignature] = _ => None

    new ScopedProcedureSignatureResolver {
      override def procedureSignature(n: ProcedureName): ProcedureSignature = procSignatureLookup(n)

      override def functionSignature(n: FunctionName): Option[UserFunctionSignature] = funcSignatureLookup(n)

      override def procedureSignatureVersion: Long = 42

      override def queryLanguage: QueryLanguage = Cypher25
    }
  }

  def runWithoutLC(): AnalysisAssertions = {
    runWith(disabledCypherVersions = Set(CypherVersion.Cypher5), VariableChecking)
  }

  def runWithLC(): AnalysisAssertions = {
    runWith(
      disabledCypherVersions = Set(CypherVersion.Cypher5),
      semanticAnalysisTwice(
        Some(
          ScopeSurveyor andThen ExtractLocalDefinitions
        ),
        Some(TryRewriteProcedureCalls(makeResolverMock))
      ),
      LocalCallables,
      VariableChecking
    )
  }

  def msg22N27(expected: String, actual: String): String =
    s"Type mismatch: expected $expected but was $actual"

  def msg22NB1(expected: String, actual: String): String =
    s"Type mismatch: expected $expected but was $actual"

  def msg42N25(): String =
    s"Procedure call inside a query does not support naming results implicitly (name explicitly using `YIELD` instead)"

  def msg42N50(column: String): String =
    s"Unknown procedure output: `$column`"

  def msg42N59(column: String): String =
    s"Variable `$column` already declared"

  def msg42NAH(column: String, procedureName: String): String =
    s"Return column `$column` does not match output signature of local procedure $procedureName"

  def msg42NAI(column: String, procedureName: String): String =
    s"Return column `$column` is missing to match output signature of local procedure $procedureName"

  def msg42NAJ(typeName: String, column: String, procedureName: String): String =
    s"`$typeName` is not supported as local procedure output type. Adjust the type of output field `$column` of local procedure ${procedureName}"

  def msg42NAL(typeName: String, column: String, procedureName: String): String =
    s"`$typeName` is not supported as local callable parameter type. Adjust the type of parameter `$column` of local callable ${procedureName}"

  def msg42I42(): String =
    s"Cannot yield value from void procedure."

  /*
   * BEGIN: Test feature flag
   * vvvvvvvvvvvvvvvvvvvvvvvv
   */
  test("""DEFINE PROCEDURE foo.bar() { FINISH }
         |^
         |
         |CALL foo.bar()
         |FINISH
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""DEFINE FUNCTION foo.bar() = 1
         |^
         |
         |RETURN foo.bar() AS x
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""DEFINE FUNCTION hej(n) = EXISTS { MATCH (n)-->(:B) }
         |^
         |
         |MATCH (a:A) WHERE hej(a)
         |RETURN *
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""DEFINE FUNCTION hej(n :: NODE) = EXISTS { MATCH (n)-->(:B) }
         |^
         |
         |MATCH (a:A) WHERE hej(a)
         |RETURN *
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""DEFINE FUNCTION hej() =
         |^
         |  EXISTS {
         |    DEFINE FUNCTION nested() = 1
         |    MATCH (:A)-->(:B {p: nested() })
         |  }
         |
         |MATCH (a:A) WHERE hej()
         |RETURN *
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""RETURN head(COLLECT {
         |  DEFINE FUNCTION foo() = "foo"
         |  ^
         |
         |  RETURN foo() AS x
         |}) AS foo
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""DEFINE PROCEDURE unused() {
         |^
         |  DEFINE PROCEDURE foo.bar(x :: INT) { FINISH }
         |  DEFINE FUNCTION foo.one() = 1
         |
         |  CALL foo.bar(foo.one())
         |  FINISH
         |}
         |
         |FINISH
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""DEFINE PROCEDURE foo.bar(x :: INT) { FINISH }
         |^
         |
         |{
         |  DEFINE FUNCTION foo.one() = 1
         |  CALL foo.bar(foo.one())
         |  FINISH
         |}
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""{
         |  DEFINE PROCEDURE foo.bar() { FINISH }
         |  ^
         |
         |  CALL foo.bar()
         |  FINISH
         |}
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""{
         |  DEFINE FUNCTION one() = 1
         |  ^
         |
         |  RETURN one() AS x
         |}
         |UNION
         |{
         |  RETURN 2 AS x
         |}
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""{
         |  RETURN 1 AS x
         |}
         |UNION
         |{
         |  DEFINE PROCEDURE two() { RETURN 2 AS foo }
         |  ^
         |
         |  CALL two() YIELD foo AS x
         |  RETURN x
         |}
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""{
         |  DEFINE FUNCTION one() = 1
         |  ^
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
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""{
         |  RETURN 1 AS x
         |}
         |
         |NEXT
         |
         |{
         |  DEFINE PROCEDURE two() { RETURN 2 AS foo }
         |  ^
         |
         |  CALL two() YIELD foo AS y
         |  RETURN y
         |}
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""WHEN 1 = 1 THEN {
         |  DEFINE FUNCTION one() = 1
         |  ^
         |
         |  RETURN one() AS x
         |}
         |ELSE RETURN 2 AS x
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""WHEN 1 = 1 THEN RETURN 1 AS x
         |WHEN 1 = head(COLLECT {
         |  DEFINE FUNCTION two() = 2
         |  ^
         |
         |  RETURN two() AS x
         |}) THEN RETURN 2 AS x
         |ELSE RETURN 3 AS x
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""WHEN 1 = 1 THEN RETURN 1 AS x
         |WHEN 1 = 2 THEN {
         |  DEFINE FUNCTION two() = 2
         |  ^
         |
         |  RETURN two() AS x
         |}
         |ELSE RETURN 3 AS x
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""WHEN 1 = 1 THEN RETURN 1 AS x
         |WHEN 1 = 2 THEN RETURN 2 AS x
         |ELSE {
         |  DEFINE FUNCTION thr33() = 3
         |  ^
         |
         |  RETURN thr33() AS x
         |}
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""CALL {
         |  DEFINE FUNCTION foo.bar() = 1
         |  ^
         |
         |  RETURN foo.bar() AS error
         |}
         |
         |FINISH
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
  }

  test("""CALL () {
         |  DEFINE FUNCTION foo.bar() = 1
         |  ^
         |
         |  RETURN foo.bar() AS error
         |}
         |
         |FINISH
         |""".stripMargin) {
    runWithoutLC().hasErrorsWithMarkedPosition(p => errorFeatureRequired(p))
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

  test("""{
         |  DEFINE PROCEDURE foo.baz() { FINISH }
         |
         |  CALL foo.baz()
         |  FINISH
         |}""".stripMargin) {
    runWithLC().hasNoErrors
  }

  test(s"""DEFINE PROCEDURE foo.a(x :: INT) {
          |  RETURN x AS z
          |}
          |
          |CALL foo.a(1) YIELD z
          |RETURN z
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  test(s"""DEFINE PROCEDURE foo.a(x, y) {
          |  RETURN x + y AS x
          |}
          |
          |CALL foo.a(1, 2) YIELD x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  test(s"""DEFINE PROCEDURE foo.a(x :: INT, y :: INT) {
          |  RETURN x + y AS x
          |}
          |
          |CALL foo.a(1, 2) YIELD x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  test(s"""DEFINE PROCEDURE foo.a(x) {
          |  RETURN 1 AS a
          |}
          |DEFINE PROCEDURE foo.b(x) {
          |  RETURN 2 AS b
          |}
          |
          |FINISH
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  test(s"""DEFINE PROCEDURE foo.baz(x) {
          |  WHEN x = 1 THEN RETURN 1 AS y
          |  WHEN x = 2 THEN {
          |    DEFINE PROCEDURE foo.bar() {
          |      RETURN 1 AS z
          |
          |      NEXT
          |
          |      RETURN 2 AS z
          |    }
          |
          |    RETURN 2 AS y
          |  }
          |  ELSE RETURN 3 AS y
          |}
          |
          |CALL foo.baz(1) YIELD y
          |FINISH
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  /*
   * USE clause is not supported
   */
  for {
    (kind, gqlError) <- Seq("procedure" -> GqlHelper.getGql42001_42NAF(), "function" -> GqlHelper.getGql42001_42NAG())
    keyword = kind.toUpperCase
    invocation = kind match {
      case "procedure" => "CALL"
      case "function"  => "LET _res ="
    }
    msg = s"USE clause is not supported in local $kind definitions"
  } {

    test(s"""DEFINE $keyword foo.baz() {
            |  USE graph
            |  ^
            |  FINISH
            |}
            |
            |$invocation foo.baz()
            |FINISH
            |""".stripMargin) {
      runWithLC().hasErrorWithMarkedPosition(_ => gqlError, msg)
    }

    test(s"""DEFINE $keyword foo.baz() {
            |  DEFINE $keyword foo.bar() {
            |    USE graph
            |    ^
            |    FINISH
            |  }
            |
            |  FINISH
            |}
            |
            |$invocation foo.baz()
            |FINISH
            |""".stripMargin) {
      runWithLC().hasErrorWithMarkedPosition(_ => gqlError, msg)
    }

    /*
    // This test is also an invalid query, but it runs into a different error on embedded testing
    test(s"""DEFINE $keyword foo.baz() {
            |  CALL () {
            |    USE graph
            |    ^
            |    FINISH
            |  }
            |
            |  FINISH
            |}
            |
            |$invocation foo.baz()
            |FINISH
            |""".stripMargin) {
      runWithLC().hasErrorWithMarkedPosition(_ => gqlError, msg)
    }
     */
    test(s"""DEFINE $keyword foo.baz() {
            |  USE graph
            |  ^
            |  FINISH
            |  UNION
            |  USE graph
            |  FINISH
            |}
            |
            |$invocation foo.baz()
            |FINISH
            |""".stripMargin) {
      runWithLC().hasErrorWithMarkedPosition(_ => gqlError, msg)
    }

    test(s"""DEFINE $keyword foo.baz() {
            |  DEFINE $keyword foo.bar() {
            |    RETURN 1 AS x
            |
            |    NEXT
            |
            |    USE graph
            |    ^
            |    FINISH
            |  }
            |
            |  FINISH
            |}
            |
            |$invocation foo.baz()
            |FINISH
            |""".stripMargin) {
      runWithLC().hasErrorWithMarkedPosition(_ => gqlError, msg)
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
            |      ^
            |      RETURN 2 AS z
            |    }
            |
            |    RETURN 2 AS y
            |  }
            |  ELSE RETURN 3 AS y
            |}
            |
            |$invocation foo.baz(1)
            |FINISH
            |""".stripMargin) {
      runWithLC().hasErrorWithMarkedPosition(_ => gqlError, msg)
    }
  }

  /*
   * Type mismatch
   */
  // inside input signature 1 in procedure
  test(s"""DEFINE PROCEDURE foo.a(x :: INT = true) {
          |                                  ^---
          |  RETURN 1 LIMIT 1
          |}
          |
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("INTEGER").asJava,
          "BOOLEAN",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("Integer", "Boolean")
    )
  }

  // inside input signature 2 in procedure
  test(s"""DEFINE PROCEDURE foo.a(v :: BOOLEAN, x :: STRING = "true", y :: STRING = 123, z :: INT = 123) {
          |                                                                         ^--
          |  RETURN 1 LIMIT 1
          |}
          |
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("STRING").asJava,
          "INTEGER",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("String", "Integer")
    )
  }

  // inside input signature 3 in procedure
  test(s"""DEFINE PROCEDURE foo.a(x :: STRING = 123, y :: INT = "abc") {
          |                                     ^--             ^----
          |  RETURN 1 LIMIT 1
          |}
          |
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("STRING").asJava,
          "INTEGER",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("String", "Integer"),
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("INTEGER").asJava,
          "STRING",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("Integer", "String")
    )
  }

  // inside input signature 4 in procedure
  test(s"""DEFINE PROCEDURE foo.a(x :: ANY<INT|STRING> = true) {
          |                                              ^---
          |  RETURN 1 LIMIT 1
          |}
          |
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("INTEGER", "STRING").asJava,
          "BOOLEAN",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("Integer or String", "Boolean")
    )
  }

  // inside input signature 1 in function
  test(s"""DEFINE FUNCTION foo.a(x :: INT = true) {
          |                                 ^---
          |  RETURN 1 LIMIT 1
          |}
          |
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("INTEGER").asJava,
          "BOOLEAN",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("Integer", "Boolean")
    )
  }

  // inside input signature 2 in function
  test(s"""DEFINE FUNCTION foo.a(v :: BOOLEAN, x :: STRING = "true", y :: STRING = 123, z :: INT = 123) {
          |                                                                        ^--
          |  RETURN 1 LIMIT 1
          |}
          |
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("STRING").asJava,
          "INTEGER",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("String", "Integer")
    )
  }

  // inside input signature 3 in function
  test(s"""DEFINE FUNCTION foo.a(x :: STRING = 123, y :: INT = "abc") {
          |                                    ^--             ^----
          |  RETURN 1 LIMIT 1
          |}
          |
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("STRING").asJava,
          "INTEGER",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("String", "Integer"),
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("INTEGER").asJava,
          "STRING",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("Integer", "String")
    )
  }

  // inside input signature 4 in function
  test(s"""DEFINE FUNCTION foo.a(x :: ANY<INT|STRING> = true) {
          |                                             ^---
          |  RETURN 1 LIMIT 1
          |}
          |
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("INTEGER", "STRING").asJava,
          "BOOLEAN",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("Integer or String", "Boolean")
    )
  }

  // inside body between parameter and constant
  test(s"""DEFINE PROCEDURE foo.a(x :: INT) {
          |  RETURN x || 4 AS z
          |         ^
          |}
          |
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("STRING", "LIST").asJava,
          "INTEGER",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("String or List<T>", "Integer")
    )
  }

  // inside body between parameters
  test(s"""DEFINE PROCEDURE foo.a(x :: STRING, y :: INT) {
          |  RETURN x || y AS z
          |              ^
          |}
          |
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("STRING").asJava,
          "INTEGER",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("String", "Integer")
    )
  }

  // inside nested body between parameter and constant
  test(s"""DEFINE PROCEDURE foo.a(x :: INT) {
          |  DEFINE PROCEDURE foo.b(x :: INT) {
          |    RETURN x || 4 AS z
          |           ^
          |  }
          |
          |  CALL foo.b(x) YIELD z
          |  RETURN z
          |}
          |
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("STRING", "LIST").asJava,
          "INTEGER",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("String or List<T>", "Integer")
    )
  }

  // after call between result column and constant
  test(s"""DEFINE PROCEDURE foo.a(x :: STRING) :: (z :: STRING) {
          |  RETURN x || "def" AS z
          |}
          |
          |CALL foo.a("abc") YIELD z
          |RETURN z / 3
          |       ^
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("FLOAT", "INTEGER", "DURATION").asJava,
          "STRING",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("Float, Integer or Duration", "String")
    )
  }

  // after call between result columns
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b :: STRING) {
          |  RETURN 1 AS a, "abc" AS b
          |}
          |
          |CALL foo.a() YIELD a, b
          |RETURN a * b AS z
          |           ^
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("FLOAT", "INTEGER", "DURATION").asJava,
          "STRING",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("Float, Integer or Duration", "String")
    )
  }

  // after call with YIELD between renamed result columns
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b :: STRING) {
          |  RETURN 1 AS a, "abc" AS b
          |}
          |
          |CALL foo.a() YIELD a AS x, b AS y
          |RETURN x * y AS z
          |           ^
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("FLOAT", "INTEGER", "DURATION").asJava,
          "STRING",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("Float, Integer or Duration", "String")
    )
  }

  // in argument
  test(s"""DEFINE PROCEDURE foo.a(a :: INT) {
          |  FINISH
          |}
          |
          |CALL foo.a(true)
          |           ^---
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("INTEGER").asJava,
          "BOOLEAN",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("Integer", "Boolean")
    )
  }

  // in arguments
  test(s"""DEFINE PROCEDURE foo.a(a :: INT, b :: STRING) {
          |  FINISH
          |}
          |
          |CALL foo.a("a", 10)
          |           ^--  ^-
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("INTEGER").asJava,
          "STRING",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("Integer", "String"),
      pos =>
        GqlHelper.getGql42001_22NB1(
          List("STRING").asJava,
          "INTEGER",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22NB1("String", "Integer")
    )
  }

  // NOT NULL in the input signature
  // Forbidding due to current limitations of NULL type inference
  // Ideally this would be valid query
  test(s"""DEFINE PROCEDURE foo.a(a :: INT, b :: INT NOT NULL) {
          |                                 ^
          |  FINISH
          |}
          |
          |CALL foo.a(1, 2)
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAL(
          "INTEGER NOT NULL",
          "b",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAL("INTEGER NOT NULL", "b", "foo.a")
    )
  }

  test(s"""DEFINE PROCEDURE foo.a(x :: STRING NOT NULL, y :: INT NOT NULL) {
          |                       ^                     ^
          |  RETURN x, y
          |}
          |
          |CALL foo.a(1, 2) YIELD x, y
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAL(
          "STRING NOT NULL",
          "x",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAL("STRING NOT NULL", "x", "foo.a"),
      pos =>
        GqlHelper.getGql42001_42NAL(
          "INTEGER NOT NULL",
          "y",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAL("INTEGER NOT NULL", "y", "foo.a")
    )
  }

  // LIST<... NOT NULL> in the output signature
  test(s"""DEFINE PROCEDURE foo.a(a :: LIST<INT NOT NULL>) {
          |                       ^
          |  FINISH
          |}
          |
          |CALL foo.a([1])
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAL(
          "LIST<INTEGER NOT NULL>",
          "a",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAL("LIST<INTEGER NOT NULL>", "a", "foo.a")
    )
  }

  /*
   * Return column order can be mixed — like in UNION
   */
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b :: INT) {
          |  RETURN 2 AS b, 1 AS a
          |}
          |
          |CALL foo.a() YIELD a AS x, b AS y
          |RETURN x, y
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b) {
          |  RETURN 2 AS b, 1 AS a
          |}
          |
          |CALL foo.a() YIELD a AS x, b AS y
          |RETURN x, y
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b :: INT, c :: INT) {
          |  RETURN 2 AS b, 3 AS c, 1 AS a
          |}
          |
          |CALL foo.a() YIELD c AS z, a AS x, b AS y
          |RETURN x, y, z
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  test(s"""DEFINE PROCEDURE foo.a() :: (a :: STRING, b, c :: INT) {
          |  RETURN [2] AS b, 3 AS c, "1" AS a
          |}
          |
          |CALL foo.a() YIELD c AS z, a AS x, b AS y
          |RETURN x, y, z
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  /*
   * Return column types that match output signature types
   */
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT) {
          |  RETURN 1 AS a
          |}
          |
          |CALL foo.a() YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  test(
    s"""DEFINE PROCEDURE foo.a() :: (a :: FLOAT, b :: ANY<FLOAT|INT>) {
       |  RETURN 1 AS a, 2.0 AS b
       |}
       |
       |CALL foo.a() YIELD a AS x, b AS y
       |RETURN x, y
       |""".stripMargin
  ) {
    runWithLC().hasNoErrors
  }

  test(s"""DEFINE PROCEDURE foo.a() :: (a :: ANY<STRING|INT>, b :: LIST<INT>) {
          |  RETURN 1 AS a, [2] AS b
          |}
          |
          |CALL foo.a() YIELD a AS x, b AS y
          |RETURN x, y
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  test(s"""DEFINE PROCEDURE foo.a() :: (a :: MAP, b :: LIST<LIST<INT>>) {
          |  RETURN {a: 1} AS a, [[2], [2]] AS b
          |}
          |
          |CALL foo.a() YIELD a AS x, b AS y
          |RETURN x, y
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  test(s"""DEFINE PROCEDURE foo.a() :: (a :: ANY, b) {
          |  RETURN 1 AS a, 2 AS b
          |}
          |
          |CALL foo.a() YIELD a AS x, b AS y
          |RETURN x, y
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  /*
   * Return not fitting output signature
   */
  // Extra columns in RETURN
  test(s"""DEFINE PROCEDURE foo.a() :: () {
          |  RETURN 1 AS a
          |              ^
          |}
          |
          |CALL foo.a() YIELD a
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAH(
          "a",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAH("a", "foo.a")
    )
  }

  // Extra columns in RETURN
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT) {
          |  RETURN 1 AS a, 2 AS b
          |                      ^
          |}
          |
          |CALL foo.a() YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAH(
          "b",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAH("b", "foo.a")
    )
  }

  // Extra columns in NEXT
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT) {
          |  RETURN 1 AS a
          |
          |  NEXT
          |
          |  RETURN 2 AS a, 3 AS b
          |                      ^
          |}
          |
          |CALL foo.a() YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAH(
          "b",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAH("b", "foo.a")
    )
  }

  // Extra columns in UNION
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT) {
          |  RETURN 1 AS a, 2 AS b
          |  UNION
          |  ^
          |  RETURN 3 AS a, 4 AS b
          |}
          |
          |CALL foo.a() YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAH(
          "b",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAH("b", "foo.a")
    )
  }

  // Extra columns in conditional query
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT) {
          |  WHEN TRUE THEN RETURN 1 AS a, 2 AS b
          |                                     ^
          |  WHEN FALSE THEN RETURN 2 AS a, 3 AS b
          |  ELSE RETURN 3 AS a, 4 AS b
          |}
          |
          |CALL foo.a() YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAH(
          "b",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAH("b", "foo.a")
    )
  }

  // Extra columns in output signature with FINISH
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT) {
          |                             ^
          |  FINISH
          |}
          |
          |CALL foo.a()
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAI(
          "a",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAI("a", "foo.a")
    )
  }

  // Extra columns in output signature with an update
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT) {
          |                             ^
          |  CREATE (n :N)
          |}
          |
          |CALL foo.a()
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAI(
          "a",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAI("a", "foo.a")
    )
  }

  // Extra columns in output signature with RETURN
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b :: INT) {
          |                                       ^
          |  RETURN 1 AS a
          |}
          |
          |CALL foo.a() YIELD a AS x, b AS y
          |RETURN x, y
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAI(
          "b",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAI("b", "foo.a")
    )
  }

  // Extra columns in output signature with NEXT
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b :: INT) {
          |                                       ^
          |  RETURN 1 AS x
          |
          |  NEXT
          |
          |  RETURN 2 AS a
          |}
          |
          |CALL foo.a() YIELD a AS x, b AS y
          |RETURN x, y
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAI(
          "b",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAI("b", "foo.a")
    )
  }

  // Extra columns in output signature with UNION
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b :: INT) {
          |                                       ^
          |  RETURN 1 AS a
          |  UNION
          |  RETURN 2 AS a
          |}
          |
          |CALL foo.a() YIELD a AS x, b AS y
          |RETURN x, y
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAI(
          "b",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAI("b", "foo.a")
    )
  }

  // Extra columns in output signature with conditional queries
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b :: INT) {
          |                                       ^
          |  WHEN TRUE THEN RETURN 1 AS a
          |  WHEN FALSE THEN RETURN 2 AS a
          |  ELSE RETURN 3 AS a
          |}
          |
          |CALL foo.a() YIELD a AS x, b AS y
          |RETURN x, y
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAI(
          "b",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAI("b", "foo.a")
    )
  }

  // NOT NULL in the output signature
  // Forbidding due to current limitations of NULL type inference
  // Ideally this would be valid query
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b :: INT NOT NULL) {
          |                                       ^
          |  RETURN null AS a, 2 AS b
          |}
          |
          |CALL foo.a() YIELD a AS x, b AS y
          |RETURN x, y
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAJ(
          "INTEGER NOT NULL",
          "b",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAJ("INTEGER NOT NULL", "b", "foo.a")
    )
  }

  // LIST<... NOT NULL> in the output signature
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: LIST<INT NOT NULL>) {
          |                             ^
          |  RETURN [1] AS a
          |}
          |
          |CALL foo.a() YIELD a
          |RETURN a
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAJ(
          "LIST<INTEGER NOT NULL>",
          "a",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAJ("LIST<INTEGER NOT NULL>", "a", "foo.a")
    )
  }

  // type mismatch even not called
  test(s"""DEFINE PROCEDURE foo.a(x :: STRING) :: (z :: STRING) {
          |  RETURN 123 AS z
          |                ^
          |}
          |
          |FINISH
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql22G03_22N27(
          "INTEGER",
          "`z`",
          List("STRING").asJava,
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22N27("String", "Integer")
    )
  }

  // type mismatch 1
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT) {
          |  RETURN "1" AS a
          |                ^
          |}
          |
          |CALL foo.a() YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql22G03_22N27(
          "STRING",
          "`a`",
          List("INTEGER").asJava,
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22N27("Integer", "String")
    )
  }

  // type mismatch 2
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT) {
          |  RETURN 1.0 AS a
          |                ^
          |}
          |
          |CALL foo.a() YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql22G03_22N27(
          "FLOAT",
          "`a`",
          List("INTEGER").asJava,
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22N27("Integer", "Float")
    )
  }

  // type mismatch 3
  test(s"""DEFINE PROCEDURE foo.a(x :: STRING) :: (a :: INT) {
          |  RETURN x AS a
          |              ^
          |}
          |
          |CALL foo.a("abc") YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql22G03_22N27(
          "STRING",
          "`a`",
          List("INTEGER").asJava,
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22N27("Integer", "String")
    )
  }

  // type mismatch 4
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: ANY<INT|STRING>) {
          |  RETURN 1.0 AS a
          |                ^
          |}
          |
          |CALL foo.a() YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql22G03_22N27(
          "FLOAT",
          "`a`",
          List("INTEGER", "STRING").asJava,
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22N27("Integer or String", "Float")
    )
  }

  // type mismatch 5
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT) {
          |  RETURN true AS a
          |  UNION
          |  ^
          |  RETURN 1.0 AS a
          |}
          |
          |CALL foo.a() YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql22G03_22N27(
          "ANY", // it would be better if that would be ANY<BOOLEAN|FLOAT>
          "`a`",
          List("INTEGER").asJava,
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22N27("Integer", "Boolean or Float")
    )
  }

  // type mismatch 6
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: ANY<INT|STRING>) {
          |  RETURN true AS a
          |  UNION
          |  ^
          |  RETURN 1.0 AS a
          |}
          |
          |CALL foo.a() YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql22G03_22N27(
          "ANY", // it would be better if that would be ANY<FLOAT|STRING>
          "`a`",
          List("INTEGER", "STRING").asJava,
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22N27("Integer or String", "Boolean or Float")
    )
  }

  // type fit 1
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: ANY<INT|STRING>) {
          |  RETURN "abc" AS a
          |  UNION
          |  RETURN 123 AS a
          |}
          |
          |CALL foo.a() YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  /* The following two test should work but do not due to shortcomings in the type system

  // type fit 2.1
  test(s"""DEFINE PROCEDURE foo.a(list :: LIST<ANY> = ["abc", 123, true]) :: (a :: ANY<INT|STRING>) {
          |  RETURN list[2] AS a
          |  UNION
          |  RETURN list[1] AS a
          |  UNION
          |  RETURN list[0] AS a
          |}
          |
          |CALL foo.a() YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  // type fit 2.2
  test(s"""DEFINE PROCEDURE foo.a(list :: LIST<BOOLEAN|INT|STRING>) :: (a :: ANY<INT|STRING>) {
          |  RETURN list[2] AS a
          |  UNION
          |  RETURN list[1] AS a
          |  UNION
          |  RETURN list[0] AS a
          |}
          |
          |CALL foo.a(["abc", 123, true]) YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }
   */

  // type fit 2.3
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: ANY<INT|STRING>) {
          |  RETURN ["abc", 123, true][2] AS a
          |  UNION
          |  RETURN ["abc", 123, true][1] AS a
          |  UNION
          |  RETURN ["abc", 123, true][0] AS a
          |}
          |
          |CALL foo.a() YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  /* This test should work but does not due to shortcomings in the type system
  // type fit 4 — type checking of the callable body does not take arguments into account
  test(s"""DEFINE PROCEDURE foo.a(x) :: (a :: INT) {
          |  RETURN x AS a
          |}
          |
          |CALL foo.a("abc") YIELD a AS x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }
   */

  // Extra columns in output signature and type mismatch
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b :: INT) {
          |                                       ^
          |  RETURN 1.0 AS a
          |                ^
          |}
          |
          |CALL foo.a() YIELD a AS x, b AS y
          |RETURN x, y
          |""".stripMargin) {
    runWithLC().hasErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42NAI(
          "b",
          "foo.a",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42NAI("b", "foo.a"),
      pos =>
        GqlHelper.getGql22G03_22N27(
          "FLOAT",
          "`a`",
          List("INTEGER").asJava,
          pos.offset,
          pos.line,
          pos.column
        ),
      msg22N27("Integer", "Float")
    )
  }

  /*
   * Missing YIELD clause
   */
  // not for VOID procedures with FINISH without output signature
  test(s"""DEFINE PROCEDURE foo.a() {
          |  FINISH
          |}
          |
          |CALL foo.a()
          |FINISH
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  // not for VOID procedures with FINISH with output signature
  test(s"""DEFINE PROCEDURE foo.a() :: () {
          |  FINISH
          |}
          |
          |CALL foo.a()
          |FINISH
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  // not for VOID procedures with update without output signature
  test(s"""DEFINE PROCEDURE foo.a() {
          |  CREATE ()
          |}
          |
          |CALL foo.a()
          |FINISH
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  // not for VOID procedures with update with output signature
  test(s"""DEFINE PROCEDURE foo.a() :: () {
          |  CREATE ()
          |}
          |
          |CALL foo.a()
          |FINISH
          |""".stripMargin) {
    runWithLC().hasNoErrors
  }

  // without output signature
  test(s"""DEFINE PROCEDURE foo.a() {
          |  RETURN 1 AS a, 2 AS b
          |}
          |
          |CALL foo.a()
          |^
          |FINISH
          |""".stripMargin) {
    runWithLC().hasAtLeastErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42N25(
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42N25()
    )
  }

  // with output signature
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b :: INT) {
          |  RETURN 1 AS a, 2 AS b
          |}
          |
          |CALL foo.a()
          |^
          |FINISH
          |""".stripMargin) {
    runWithLC().hasAtLeastErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42N25(
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42N25()
    )
  }

  /*
   * YIELD *
   */
  // without output signature
  test(s"""DEFINE PROCEDURE foo.a() {
          |  RETURN 1 AS a, 2 AS b
          |}
          |
          |CALL foo.a() YIELD *
          |^
          |FINISH
          |""".stripMargin) {
    runWithLC().hasAtLeastErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42N25(
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42N25()
    )
  }

  // with output signature
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b :: INT) {
          |  RETURN 1 AS a, 2 AS b
          |}
          |
          |CALL foo.a() YIELD *
          |^
          |FINISH
          |""".stripMargin) {
    runWithLC().hasAtLeastErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42N25(
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42N25()
    )
  }

  /*
   * Yielding from VOID procedure
   */
  // VOID procedures with FINISH without output signature
  test(s"""DEFINE PROCEDURE foo.a() {
          |  FINISH
          |}
          |
          |CALL foo.a() YIELD x
          |^
          |FINISH
          |""".stripMargin) {
    runWithLC().hasAtLeastErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42I42(
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42I42()
    )
  }

  // VOID procedures with FINISH with output signature
  test(s"""DEFINE PROCEDURE foo.a() :: () {
          |  FINISH
          |}
          |
          |CALL foo.a() YIELD x
          |^
          |FINISH
          |""".stripMargin) {
    runWithLC().hasAtLeastErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42I42(
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42I42()
    )
  }

  // VOID procedures with update without output signature
  test(s"""DEFINE PROCEDURE foo.a() {
          |  CREATE ()
          |}
          |
          |CALL foo.a() YIELD x
          |^
          |FINISH
          |""".stripMargin) {
    runWithLC().hasAtLeastErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42I42(
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42I42()
    )
  }

  // VOID procedures with update with output signature
  test(s"""DEFINE PROCEDURE foo.a() :: () {
          |  CREATE ()
          |}
          |
          |CALL foo.a() YIELD x
          |^
          |FINISH
          |""".stripMargin) {
    runWithLC().hasAtLeastErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42I42(
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42I42()
    )
  }

  /*
   * Yielding non-result columns
   */
  // without output signature
  test(s"""DEFINE PROCEDURE foo.a() {
          |  RETURN 1 AS a, 2 AS b
          |}
          |
          |CALL foo.a() YIELD a AS x, c AS y
          |                           ^
          |RETURN x + y AS z
          |""".stripMargin) {
    runWithLC().hasAtLeastErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42N50(
          "c",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42N50("c")
    )
  }

  // with output signature
  test(s"""DEFINE PROCEDURE foo.a() :: (a :: INT, b :: INT) {
          |  RETURN 1 AS a, 2 AS b
          |}
          |
          |CALL foo.a() YIELD a AS x, c AS y
          |                           ^
          |RETURN x + y AS z
          |""".stripMargin) {
    runWithLC().hasAtLeastErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42N50(
          "c",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42N50("c")
    )
  }

  /*
   * Yielding already defined
   */
  // without aliasing
  test(s"""DEFINE PROCEDURE foo.a() {
          |  RETURN 1 AS a, 2 AS b
          |}
          |LET b = 1
          |CALL foo.a() YIELD a, b
          |                      ^
          |RETURN a + b AS z
          |""".stripMargin) {
    runWithLC().hasAtLeastErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42N59(
          "b",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42N59("b")
    )
  }

  // with aliasing
  test(s"""DEFINE PROCEDURE foo.a() {
          |  RETURN 1 AS a, 2 AS b
          |}
          |LET y = 1
          |CALL foo.a() YIELD a AS x, b AS y
          |                                ^
          |RETURN x + y AS z
          |""".stripMargin) {
    runWithLC().hasAtLeastErrorWithMarkedPosition(
      pos =>
        GqlHelper.getGql42001_42N59(
          "y",
          pos.offset,
          pos.line,
          pos.column
        ),
      msg42N59("y")
    )
  }

  /*
   * Yielding already defined
   */
  // inner the notification come out
  test(s"""DEFINE PROCEDURE foo.a() {
          |  MATCH p0 = (n7)-[]->()
          |  RETURN
          |    COLLECT {
          |      WITH p0 AS p0, 1 AS x
          |      CALL () {
          |        MATCH p0 = (n7)-[]->()
          |              ^
          |        RETURN length(p0) AS a
          |      }
          |      RETURN a
          |    } AS x
          |}
          |LET y = 1
          |CALL foo.a() YIELD x
          |RETURN x
          |""".stripMargin) {
    runWithLC().hasNotificationsWithMarkedPosition(pos => SubqueryVariableShadowing(pos, "CALL", "p0"))
  }

  // body does not cause shadowing notification
  test(s"""MATCH (n)
          |CALL (n) {
          |  DEFINE PROCEDURE foo() {
          |    MATCH (n)
          |    RETURN n
          |  }
          |  CALL foo() YIELD n AS m
          |  RETURN m
          |}
          |RETURN n, m""".stripMargin) {
    runWithLC().hasNoErrors.hasNoNotifications
  }
}
