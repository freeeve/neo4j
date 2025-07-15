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
package org.neo4j.cypher.internal.ast.factory.ddl

import org.neo4j.cypher.internal.ast.IndefiniteWait
import org.neo4j.cypher.internal.ast.NamespacedName
import org.neo4j.cypher.internal.ast.NoWait
import org.neo4j.cypher.internal.ast.StartDatabase
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.StopDatabase
import org.neo4j.cypher.internal.ast.TimeoutAfter
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.util.test_helpers.GqlExceptionMatchers.gqlStatus
import org.neo4j.gqlstatus.GqlStatusInfoCodes

class StartAndStopDatabaseAdministrationCommandParserTest extends AdministrationAndSchemaCommandParserTestBase {

  // START DATABASE

  test("START DATABASE foo") {
    parsesTo[Statements](StartDatabase(literalFoo, NoWait()(defaultPos))(pos))
  }

  test("START DATABASE $foo") {
    parsesTo[Statements](StartDatabase(stringParamName("foo"), NoWait()(defaultPos))(pos))
  }

  test("START DATABASE foo WAIT") {
    parsesTo[Statements](StartDatabase(literalFoo, IndefiniteWait()(defaultPos))(pos))
  }

  test("START DATABASE foo WAIT 5") {
    parsesTo[Statements](StartDatabase(literal("foo"), TimeoutAfter("5")(defaultPos))(pos))
  }

  test("START DATABASE foo WAIT 5 SEC") {
    parsesTo[Statements](StartDatabase(literal("foo"), TimeoutAfter("5")(defaultPos))(pos))
  }

  test("START DATABASE foo WAIT 5 SECOND") {
    parsesTo[Statements](StartDatabase(literal("foo"), TimeoutAfter("5")(defaultPos))(pos))
  }

  test("START DATABASE foo WAIT 5 SECONDS") {
    parsesTo[Statements](StartDatabase(literal("foo"), TimeoutAfter("5")(defaultPos))(pos))
  }

  test("START DATABASE foo NOWAIT") {
    parsesTo[Statements](StartDatabase(literalFoo, NoWait()(defaultPos))(pos))
  }

  test("START DATABASE `foo.bar`") {
    parsesTo[Statements](StartDatabase(literal("foo.bar"), NoWait()(defaultPos))(pos))
  }

  test("START DATABASE foo.bar") {
    parsesIn[Statements] {
      case Cypher5 => _.toAstPositioned(
          StartDatabase(
            NamespacedName(List("bar"), Some("foo"))((1, 16, 15)),
            NoWait()(pos)
          )(pos)
        )
      case _ => _.toAstPositioned(
          StartDatabase(
            NamespacedName(List("foo.bar"), None)((1, 16, 15)),
            NoWait()(pos)
          )(pos)
        )
    }
  }

  test("START DATABASE") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input '': expected a database name or a parameter (line 1, column 15 (offset: 14))
        |"START DATABASE"
        |               ^""".stripMargin
    )
  }

  test("START DATABASE `foo`.bar.`baz`") {
    failsParsing[Statements].in {
      case Cypher5 => _.withMessageStart(
          "Invalid input ``foo`.bar.`baz`` for name. Expected name to contain at most two components separated by `.`."
        )
          .withSyntaxErrorGqlStatus(
            gqlStatus(
              GqlStatusInfoCodes.STATUS_22N05,
              "error: data exception - input failed validation. Invalid input '`foo`.bar.`baz`' for name."
            )
              .withCause(
                GqlStatusInfoCodes.STATUS_22N83,
                "error: data exception - input consists of too many components. Expected name to contain at most 2 components separated by '.'."
              )
          )
      case _ => _.withMessageStart(
          "Incorrectly formatted graph reference '`foo`.bar.`baz`'. Expected a single quoted or unquoted identifier. Separate name parts should not be quoted individually."
        )
          .withSyntaxErrorGqlStatus(
            gqlStatus(
              GqlStatusInfoCodes.STATUS_42NAA,
              "error: syntax error or access rule violation - incorrectly formatted graph reference. Incorrectly formatted graph reference '`foo`.bar.`baz`'. Expected a single quoted or unquoted identifier. Separate name parts should not be quoted individually."
            )
          )
    }
  }

  // STOP DATABASE

  test("STOP DATABASE foo") {
    parsesTo[Statements](StopDatabase(literalFoo, NoWait()(defaultPos))(pos))
  }

  test("STOP DATABASE $foo") {
    parsesTo[Statements](StopDatabase(stringParamName("foo"), NoWait()(defaultPos))(pos))
  }

  test("STOP DATABASE foo WAIT") {
    parsesTo[Statements](StopDatabase(literalFoo, IndefiniteWait()(defaultPos))(pos))
  }

  test("STOP DATABASE foo WAIT 99") {
    parsesTo[Statements](StopDatabase(literal("foo"), TimeoutAfter("99")(defaultPos))(pos))
  }

  test("STOP DATABASE foo WAIT 99 SEC") {
    parsesTo[Statements](StopDatabase(literal("foo"), TimeoutAfter("99")(defaultPos))(pos))
  }

  test("STOP DATABASE foo WAIT 99 SECOND") {
    parsesTo[Statements](StopDatabase(literal("foo"), TimeoutAfter("99")(defaultPos))(pos))
  }

  test("STOP DATABASE foo WAIT 99 SECONDS") {
    parsesTo[Statements](StopDatabase(literal("foo"), TimeoutAfter("99")(defaultPos))(pos))
  }

  test("STOP DATABASE foo NOWAIT") {
    parsesTo[Statements](StopDatabase(literalFoo, NoWait()(defaultPos))(pos))
  }

  test("STOP DATABASE `foo.bar`") {
    parsesTo[Statements](StopDatabase(literal("foo.bar"), NoWait()(defaultPos))(pos))
  }

  test("STOP DATABASE foo.bar") {
    parsesIn[Statements] {
      case Cypher5 => _.toAstPositioned(
          StopDatabase(NamespacedName(List("bar"), Some("foo"))((1, 15, 14)), NoWait()(pos))(pos)
        )
      case _ => _.toAstPositioned(
          StopDatabase(NamespacedName(List("foo.bar"), None)((1, 15, 14)), NoWait()(pos))(pos)
        )
    }
  }

  test("STOP DATABASE `foo`.bar.`baz`") {
    failsParsing[Statements].in {
      case Cypher5 => _.withMessageStart(
          "Invalid input ``foo`.bar.`baz`` for name. Expected name to contain at most two components separated by `.`."
        )
          .withSyntaxErrorGqlStatus(
            gqlStatus(
              GqlStatusInfoCodes.STATUS_22N05,
              "error: data exception - input failed validation. Invalid input '`foo`.bar.`baz`' for name."
            )
              .withCause(
                GqlStatusInfoCodes.STATUS_22N83,
                "error: data exception - input consists of too many components. Expected name to contain at most 2 components separated by '.'."
              )
          )
      case _ => _.withMessageStart(
          "Incorrectly formatted graph reference '`foo`.bar.`baz`'. Expected a single quoted or unquoted identifier. Separate name parts should not be quoted individually."
        )
          .withSyntaxErrorGqlStatus(
            gqlStatus(
              GqlStatusInfoCodes.STATUS_42NAA,
              "error: syntax error or access rule violation - incorrectly formatted graph reference. Incorrectly formatted graph reference '`foo`.bar.`baz`'. Expected a single quoted or unquoted identifier. Separate name parts should not be quoted individually."
            )
          )
    }
  }

  test("STOP DATABASE") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input '': expected a database name or a parameter (line 1, column 14 (offset: 13))
        |"STOP DATABASE"
        |              ^""".stripMargin
    )
  }
}
