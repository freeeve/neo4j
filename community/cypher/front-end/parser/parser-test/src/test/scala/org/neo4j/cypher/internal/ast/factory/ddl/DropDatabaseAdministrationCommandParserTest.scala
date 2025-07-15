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

import org.neo4j.cypher.internal.ast.CascadeAliases
import org.neo4j.cypher.internal.ast.DestroyData
import org.neo4j.cypher.internal.ast.DropDatabase
import org.neo4j.cypher.internal.ast.DumpData
import org.neo4j.cypher.internal.ast.IndefiniteWait
import org.neo4j.cypher.internal.ast.NamespacedName
import org.neo4j.cypher.internal.ast.NoWait
import org.neo4j.cypher.internal.ast.Restrict
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.TimeoutAfter
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.util.test_helpers.GqlExceptionMatchers.gqlStatus
import org.neo4j.gqlstatus.GqlStatusInfoCodes

class DropDatabaseAdministrationCommandParserTest extends AdministrationAndSchemaCommandParserTestBase {

  test("DROP DATABASE foo") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = false,
      composite = false,
      Restrict,
      DestroyData,
      NoWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE alias") {
    parsesTo[Statements](DropDatabase(
      literal("alias"),
      ifExists = false,
      composite = false,
      Restrict,
      DestroyData,
      NoWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE alias WAIT") {
    parsesTo[Statements](DropDatabase(
      literal("alias"),
      ifExists = false,
      composite = false,
      Restrict,
      DestroyData,
      IndefiniteWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE alias NOWAIT") {
    parsesTo[Statements](DropDatabase(
      literal("alias"),
      ifExists = false,
      composite = false,
      Restrict,
      DestroyData,
      NoWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE $foo") {
    parsesTo[Statements](DropDatabase(
      stringParamName("foo"),
      ifExists = false,
      composite = false,
      Restrict,
      DestroyData,
      NoWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo WAIT") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = false,
      composite = false,
      Restrict,
      DestroyData,
      IndefiniteWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo WAIT 10") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = false,
      composite = false,
      Restrict,
      DestroyData,
      TimeoutAfter("10")(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo WAIT 10 SEC") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = false,
      composite = false,
      Restrict,
      DestroyData,
      TimeoutAfter("10")(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo WAIT 10 SECOND") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = false,
      composite = false,
      Restrict,
      DestroyData,
      TimeoutAfter("10")(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo WAIT 10 SECONDS") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = false,
      composite = false,
      Restrict,
      DestroyData,
      TimeoutAfter("10")(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo NOWAIT") {
    parsesTo[Statements](
      DropDatabase(literalFoo, ifExists = false, composite = false, Restrict, DestroyData, NoWait()(defaultPos))(pos)
    )
  }

  test("DROP DATABASE `foo.bar`") {
    parsesTo[Statements](
      DropDatabase(
        literal("foo.bar"),
        ifExists = false,
        composite = false,
        Restrict,
        DestroyData,
        NoWait()(defaultPos)
      )(pos)
    )
  }

  test("DROP DATABASE foo.bar") {
    parsesIn[Statements] {
      case Cypher5 => _.toAstPositioned(
          DropDatabase(
            NamespacedName(List("bar"), Some("foo"))((1, 15, 14)),
            ifExists = false,
            composite = false,
            Restrict,
            DestroyData,
            NoWait()(pos)
          )(pos)
        )
      case _ => _.toAstPositioned(
          DropDatabase(
            NamespacedName(List("foo.bar"), None)((1, 15, 14)),
            ifExists = false,
            composite = false,
            Restrict,
            DestroyData,
            NoWait()(pos)
          )(pos)
        )
    }
  }

  test("DROP DATABASE foo IF EXISTS") {
    parsesTo[Statements](
      DropDatabase(literalFoo, ifExists = true, composite = false, Restrict, DestroyData, NoWait()(defaultPos))(pos)
    )
  }

  test("DROP DATABASE foo IF EXISTS WAIT") {
    parsesTo[Statements](DropDatabase(
      literalFoo,
      ifExists = true,
      composite = false,
      Restrict,
      DestroyData,
      IndefiniteWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo IF EXISTS NOWAIT") {
    parsesTo[Statements](
      DropDatabase(literalFoo, ifExists = true, composite = false, Restrict, DestroyData, NoWait()(defaultPos))(pos)
    )
  }

  test("DROP DATABASE foo DUMP DATA") {
    parsesTo[Statements](
      DropDatabase(literalFoo, ifExists = false, composite = false, Restrict, DumpData, NoWait()(defaultPos))(pos)
    )
  }

  test("DROP DATABASE foo DESTROY DATA") {
    parsesTo[Statements](
      DropDatabase(literalFoo, ifExists = false, composite = false, Restrict, DestroyData, NoWait()(defaultPos))(pos)
    )
  }

  test("DROP DATABASE foo IF EXISTS DUMP DATA") {
    parsesTo[Statements](
      DropDatabase(literalFoo, ifExists = true, composite = false, Restrict, DumpData, NoWait()(defaultPos))(pos)
    )
  }

  test("DROP DATABASE foo IF EXISTS DESTROY DATA") {
    parsesTo[Statements](
      DropDatabase(literalFoo, ifExists = true, composite = false, Restrict, DestroyData, NoWait()(defaultPos))(pos)
    )
  }

  test("DROP DATABASE foo IF EXISTS DESTROY DATA WAIT") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = true,
      composite = false,
      Restrict,
      DestroyData,
      IndefiniteWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo RESTRICT") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = false,
      composite = false,
      Restrict,
      DestroyData,
      NoWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo CASCADE ALIAS") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = false,
      composite = false,
      CascadeAliases,
      DestroyData,
      NoWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo IF EXISTS RESTRICT") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = true,
      composite = false,
      Restrict,
      DestroyData,
      NoWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo IF EXISTS CASCADE ALIASES") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = true,
      composite = false,
      CascadeAliases,
      DestroyData,
      NoWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo RESTRICT DUMP DATA") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = false,
      composite = false,
      Restrict,
      DumpData,
      NoWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo CASCADE ALIAS DESTROY DATA") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = false,
      composite = false,
      CascadeAliases,
      DestroyData,
      NoWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo RESTRICT NOWAIT") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = false,
      composite = false,
      Restrict,
      DestroyData,
      NoWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE foo CASCADE ALIASES WAIT") {
    parsesTo[Statements](DropDatabase(
      literal("foo"),
      ifExists = false,
      composite = false,
      CascadeAliases,
      DestroyData,
      IndefiniteWait()(defaultPos)
    )(pos))
  }

  test("DROP DATABASE") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input '': expected a database name or a parameter (line 1, column 14 (offset: 13))
        |"DROP DATABASE"
        |              ^""".stripMargin
    )
  }

  test("DROP DATABASE  IF EXISTS") {
    failsParsing[Statements]
  }

  test("DROP DATABASE foo IF NOT EXISTS") {
    failsParsing[Statements]
  }

  test("DROP DATABASE `foo`.`bar`.`baz`") {
    failsParsing[Statements].in {
      case Cypher5 => _.withMessageStart(
          "Invalid input ``foo`.`bar`.`baz`` for name. Expected name to contain at most two components separated by `.`."
        )
          .withSyntaxErrorGqlStatus(
            gqlStatus(
              GqlStatusInfoCodes.STATUS_22N05,
              "error: data exception - input failed validation. Invalid input '`foo`.`bar`.`baz`' for name."
            )
              .withCause(
                GqlStatusInfoCodes.STATUS_22N83,
                "error: data exception - input consists of too many components. Expected name to contain at most 2 components separated by '.'."
              )
          )
      case _ => _.withMessageStart(
          "Incorrectly formatted graph reference '`foo`.`bar`.`baz`'. Expected a single quoted or unquoted identifier. Separate name parts should not be quoted individually."
        )
          .withSyntaxErrorGqlStatus(
            gqlStatus(
              GqlStatusInfoCodes.STATUS_42NAA,
              "error: syntax error or access rule violation - incorrectly formatted graph reference. Incorrectly formatted graph reference '`foo`.`bar`.`baz`'. Expected a single quoted or unquoted identifier. Separate name parts should not be quoted individually."
            )
          )
    }
  }

  test("DROP DATABASE KEEP DATA") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'DATA': expected a database name, 'CASCADE', 'DESTROY', 'DUMP', 'IF EXISTS', 'NOWAIT', 'RESTRICT', 'WAIT' or <EOF> (line 1, column 20 (offset: 19))
        |"DROP DATABASE KEEP DATA"
        |                    ^""".stripMargin
    )
  }

  test("DROP DATABASE db KEEP DATA") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'KEEP': expected a database name, 'CASCADE', 'DESTROY', 'DUMP', 'IF EXISTS', 'NOWAIT', 'RESTRICT', 'WAIT' or <EOF> (line 1, column 18 (offset: 17))
        |"DROP DATABASE db KEEP DATA"
        |                  ^""".stripMargin
    )
  }

  test("DROP DATABASE foo CASCADE") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input '': expected 'ALIAS' or 'ALIASES' (line 1, column 26 (offset: 25))
        |"DROP DATABASE foo CASCADE"
        |                          ^""".stripMargin
    )
  }

  test("DROP DATABASE foo DUMP DATA CASCADE ALIASES") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'CASCADE': expected 'NOWAIT', 'WAIT' or <EOF> (line 1, column 29 (offset: 28))
        |"DROP DATABASE foo DUMP DATA CASCADE ALIASES"
        |                             ^""".stripMargin
    )
  }

  test("DROP DATABASE foo DESTROY DATA RESTRICT") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'RESTRICT': expected 'NOWAIT', 'WAIT' or <EOF> (line 1, column 32 (offset: 31))
        |"DROP DATABASE foo DESTROY DATA RESTRICT"
        |                                ^""".stripMargin
    )
  }
}
