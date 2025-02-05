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
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.gqlstatus.GqlStatusInfoCodes

class NormalizeFunctionParserTest extends AstParsingTestBase {

  // Normalize defaults to NFC
  test("normalize(\"hello\")") {
    parsesTo[Expression](function("normalize", literalString("hello"), literalString("NFC")))
  }

  // All normal form keywords parse as expected
  Seq("NFC", "NFD", "NFKC", "NFKD").foreach { normalForm =>
    test(s"normalize(foo, $normalForm)") {
      parsesTo[Expression](function("normalize", varFor("foo"), literalString(normalForm)))
    }
    test(s"return my.own.normalize(foo, $normalForm)") {
      parsesTo[Statements] {
        Statements(Seq(singleQuery(return_(returnItem(
          function(Seq("my", "own"), "normalize", varFor("foo"), varFor(normalForm)),
          s"my.own.normalize(foo, $normalForm)"
        )))))
      }
    }
  }

  test("normalize with namespace") {
    "return my.own.normalize('1')" should parseTo[Statements] {
      Statements(Seq(singleQuery(return_(returnItem(
        function(Seq("my", "own"), "normalize", literal("1")),
        "my.own.normalize('1')"
      )))))
    }
    "return my.own.normalize('1',2)" should parseTo[Statements] {
      Statements(Seq(singleQuery(return_(returnItem(
        function(Seq("my", "own"), "normalize", literal("1"), literal(2)),
        "my.own.normalize('1',2)"
      )))))
    }
    "return my.own.normalize('1', notANormalForm)" should parseTo[Statements] {
      Statements(Seq(singleQuery(return_(returnItem(
        function(Seq("my", "own"), "normalize", literal("1"), varFor("notANormalForm")),
        "my.own.normalize('1', notANormalForm)"
      )))))
    }
  }

  // Failing tests
  test("RETURN normalize(\"hello\", \"NFC\")") {
    failsParsing[Statements].withSyntaxErrorContaining(
      """Invalid normal form, expected NFC, NFD, NFKC, NFKD (line 1, column 27 (offset: 26))
        |"RETURN normalize("hello", "NFC")"
        |                           ^""".stripMargin,
      GqlStatusInfoCodes.STATUS_42N49,
      "error: syntax error or access rule violation - unsupported normal form. Unknown Normal Form: 'NFC'.",
      position = Some(InputPosition(26, 1, 27))
    )
  }

  test("RETURN normalize(\"hello\", null)") {
    failsParsing[Statements].withSyntaxErrorContaining(
      """Invalid normal form, expected NFC, NFD, NFKC, NFKD (line 1, column 27 (offset: 26))
        |"RETURN normalize("hello", null)"
        |                           ^""".stripMargin,
      GqlStatusInfoCodes.STATUS_42N49,
      "error: syntax error or access rule violation - unsupported normal form. Unknown Normal Form: 'NULL'.",
      position = Some(InputPosition(26, 1, 27))
    )
  }

  test("RETURN normalize(\"hello\", NFF)") {
    failsParsing[Statements].withSyntaxErrorContaining(
      """Invalid normal form, expected NFC, NFD, NFKC, NFKD (line 1, column 27 (offset: 26))
        |"RETURN normalize("hello", NFF)"
        |                           ^""".stripMargin,
      GqlStatusInfoCodes.STATUS_42N49,
      "error: syntax error or access rule violation - unsupported normal form. Unknown Normal Form: 'NFF'.",
      position = Some(InputPosition(26, 1, 27))
    )
  }

  test("normalize(\"hello\", NFC, anotherVar)") {
    parsesIn[Expression] {
      case _ => _.withoutErrors
    }
  }

  test("normalize()") {
    parsesIn[Expression] {
      case _ => _.withoutErrors
    }
  }
}
