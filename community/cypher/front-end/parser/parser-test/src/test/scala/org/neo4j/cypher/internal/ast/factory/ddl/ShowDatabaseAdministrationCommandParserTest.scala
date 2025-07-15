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

import org.neo4j.cypher.internal.ast.AdministrationCommand
import org.neo4j.cypher.internal.ast.AllDatabasesScope
import org.neo4j.cypher.internal.ast.DefaultDatabaseScope
import org.neo4j.cypher.internal.ast.HomeDatabaseScope
import org.neo4j.cypher.internal.ast.NamespacedName
import org.neo4j.cypher.internal.ast.ShowDatabase
import org.neo4j.cypher.internal.ast.SingleNamedDatabaseScope
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.YieldOrWhere
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.util.test_helpers.GqlExceptionMatchers.gqlStatus
import org.neo4j.gqlstatus.GqlStatusInfoCodes

class ShowDatabaseAdministrationCommandParserTest extends AdministrationAndSchemaCommandParserTestBase {

  Seq(
    ("DATABASE", ShowDatabase.apply(AllDatabasesScope()(pos), _: YieldOrWhere, _: Boolean, _: Boolean) _),
    ("DATABASES", ShowDatabase.apply(AllDatabasesScope()(pos), _: YieldOrWhere, _: Boolean, _: Boolean) _),
    ("DEFAULT DATABASE", ShowDatabase.apply(DefaultDatabaseScope()(pos), _: YieldOrWhere, _: Boolean, _: Boolean) _),
    ("HOME DATABASE", ShowDatabase.apply(HomeDatabaseScope()(pos), _: YieldOrWhere, _: Boolean, _: Boolean) _),
    (
      "DATABASE $db",
      ShowDatabase.apply(
        SingleNamedDatabaseScope(stringParamName("db"))(pos),
        _: YieldOrWhere,
        _: Boolean,
        _: Boolean
      ) _
    ),
    (
      "DATABASES $db",
      ShowDatabase.apply(
        SingleNamedDatabaseScope(stringParamName("db"))(pos),
        _: YieldOrWhere,
        _: Boolean,
        _: Boolean
      ) _
    ),
    (
      "DATABASE neo4j",
      ShowDatabase.apply(SingleNamedDatabaseScope(literal("neo4j"))(pos), _: YieldOrWhere, _: Boolean, _: Boolean) _
    ),
    (
      "DATABASES neo4j",
      ShowDatabase.apply(SingleNamedDatabaseScope(literal("neo4j"))(pos), _: YieldOrWhere, _: Boolean, _: Boolean) _
    ),
    // vvv naming the database yield/where should not fail either vvv
    (
      "DATABASE yield",
      ShowDatabase.apply(SingleNamedDatabaseScope(literal("yield"))(pos), _: YieldOrWhere, _: Boolean, _: Boolean) _
    ),
    (
      "DATABASES yield",
      ShowDatabase.apply(SingleNamedDatabaseScope(literal("yield"))(pos), _: YieldOrWhere, _: Boolean, _: Boolean) _
    ),
    (
      "DATABASE where",
      ShowDatabase.apply(SingleNamedDatabaseScope(literal("where"))(pos), _: YieldOrWhere, _: Boolean, _: Boolean) _
    ),
    (
      "DATABASES where",
      ShowDatabase.apply(SingleNamedDatabaseScope(literal("where"))(pos), _: YieldOrWhere, _: Boolean, _: Boolean) _
    )
  ).foreach { case (dbType, command) =>
    test(s"SHOW $dbType") {
      parsesIn[Statement] {
        case Cypher5 => _.toAst(command(None, true, false)(pos))
        case _       => _.toAst(command(None, false, false)(pos))
      }
    }

    test(s"USE system SHOW $dbType") {
      def expected(resolveStrictly: Boolean, showCypher5ColumnsOnly: Boolean): AdministrationCommand = {
        command(None, showCypher5ColumnsOnly, false)(pos)
          .withGraph(Some(use(List("system"), resolveStrictly)))
      }

      parsesIn[Statement] {
        case Cypher5 => _.toAst(expected(resolveStrictly = false, showCypher5ColumnsOnly = true))
        case _       => _.toAst(expected(resolveStrictly = true, showCypher5ColumnsOnly = false))
      }
    }

    test(s"SHOW $dbType WHERE access = 'GRANTED'") {
      parsesIn[Statement] {
        case Cypher5 => _.toAst(command(Some(Right(where(equals(accessVar, grantedString)))), true, false)(pos))
        case _       => _.toAst(command(Some(Right(where(equals(accessVar, grantedString)))), false, false)(pos))
      }
    }

    test(s"SHOW $dbType WHERE access = 'GRANTED' AND action = 'match'") {
      val accessPredicate = equals(accessVar, grantedString)
      val matchPredicate = equals(varFor(actionString), literalString("match"))
      parsesIn[Statement] {
        case Cypher5 => _.toAst(command(Some(Right(where(and(accessPredicate, matchPredicate)))), true, false)(pos))
        case _       => _.toAst(command(Some(Right(where(and(accessPredicate, matchPredicate)))), false, false)(pos))
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access") {
      val orderByClause = orderBy(sortItem(accessVar))
      val columns = yieldClause(returnItems(variableReturnItem(accessString)), Some(orderByClause))
      parsesIn[Statement] {
        case Cypher5 => _.toAst(command(Some(Left((columns, None))), true, false)(pos))
        case _       => _.toAst(command(Some(Left((columns, None))), false, false)(pos))
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access WHERE access ='none'") {
      val orderByClause = orderBy(sortItem(accessVar))
      val whereClause = where(equals(accessVar, noneString))
      val columns =
        yieldClause(returnItems(variableReturnItem(accessString)), Some(orderByClause), where = Some(whereClause))

      parsesIn[Statement] {
        case Cypher5 => _.toAst(command(Some(Left((columns, None))), true, false)(pos))
        case _       => _.toAst(command(Some(Left((columns, None))), false, false)(pos))
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access SKIP 1 LIMIT 10 WHERE access ='none'") {
      val orderByClause = orderBy(sortItem(accessVar))
      val whereClause = where(equals(accessVar, noneString))
      val columns = yieldClause(
        returnItems(variableReturnItem(accessString)),
        Some(orderByClause),
        Some(skip(1)),
        Some(limit(10)),
        Some(whereClause)
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAst(command(Some(Left((columns, None))), true, false)(pos))
        case _       => _.toAst(command(Some(Left((columns, None))), false, false)(pos))
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access OFFSET 1 LIMIT 10 WHERE access ='none'") {
      val orderByClause = orderBy(sortItem(accessVar))
      val whereClause = where(equals(accessVar, noneString))
      val columns = yieldClause(
        returnItems(variableReturnItem(accessString)),
        Some(orderByClause),
        Some(skip(1)),
        Some(limit(10)),
        Some(whereClause)
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAst(command(Some(Left((columns, None))), true, false)(pos))
        case _       => _.toAst(command(Some(Left((columns, None))), false, false)(pos))
      }
    }

    test(s"SHOW $dbType YIELD access SKIP -1") {
      val columns = yieldClause(returnItems(variableReturnItem(accessString)), skip = Some(skip(-1)))
      parsesIn[Statement] {
        case Cypher5 => _.toAst(command(Some(Left((columns, None))), true, false)(pos))
        case _       => _.toAst(command(Some(Left((columns, None))), false, false)(pos))
      }
    }

    test(s"SHOW $dbType YIELD access OFFSET -1") {
      val columns = yieldClause(returnItems(variableReturnItem(accessString)), skip = Some(skip(-1)))
      val yieldOrWhere = Some(Left((columns, None)))
      parsesIn[Statement] {
        case Cypher5 => _.toAst(command(yieldOrWhere, true, false)(pos))
        case _       => _.toAst(command(yieldOrWhere, false, false)(pos))
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access RETURN access") {
      val yieldOrWhere = Some(Left((
        yieldClause(returnItems(variableReturnItem(accessString)), Some(orderBy(sortItem(accessVar)))),
        Some(returnClause(returnItems(variableReturnItem(accessString))))
      )))
      parsesIn[Statement] {
        case Cypher5 => _.toAst(command(yieldOrWhere, true, false)(pos))
        case _       => _.toAst(command(yieldOrWhere, false, false)(pos))
      }
    }

    test(s"SHOW $dbType YIELD * RETURN *") {
      val yieldOrWhere = Some(Left((yieldClause(returnAllItems), Some(returnClause(returnAllItems)))))
      parsesIn[Statement] {
        case Cypher5 => _.toAst(command(yieldOrWhere, true, false)(pos))
        case _       => _.toAst(command(yieldOrWhere, false, false)(pos))
      }
    }

    test(s"SHOW $dbType WHERE access = 'GRANTED' RETURN action") {
      failsParsing[Statements]
    }
  }

  test("SHOW DATABASE `foo.bar`") {
    parsesIn[Statement] {
      case Cypher5 => _.toAst(ShowDatabase(
          SingleNamedDatabaseScope(namespacedName("foo.bar"))(pos),
          None,
          cypher5ColumnsOnly = true,
          spdEnabled = false
        )(pos))
      case _ => _.toAst(ShowDatabase(
          SingleNamedDatabaseScope(namespacedName("foo.bar"))(pos),
          None,
          cypher5ColumnsOnly = false,
          spdEnabled = false
        )(pos))
    }
  }

  test("SHOW DATABASE foo.bar") {
    parsesIn[Statement] {
      case Cypher5 => _.toAst(ShowDatabase(
          SingleNamedDatabaseScope(NamespacedName(List("bar"), Some("foo"))(pos))(pos),
          None,
          cypher5ColumnsOnly = true,
          spdEnabled = false
        )(pos))
      case _ => _.toAst(ShowDatabase(
          SingleNamedDatabaseScope(NamespacedName(List("foo.bar"), None)(pos))(pos),
          None,
          cypher5ColumnsOnly = false,
          spdEnabled = false
        )(pos))
    }
  }

  test("SHOW DATABASE `foo`.`bar`.`baz`") {
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

  test("SHOW DATABASE blah YIELD *,blah RETURN user") {
    failsParsing[Statements]
  }

  test("SHOW DATABASE YIELD (123 + xyz)") {
    failsParsing[Statements]
  }

  test("SHOW DATABASES YIELD (123 + xyz) AS foo") {
    failsParsing[Statements]
  }

  test("SHOW DEFAULT DATABASES") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'DATABASES': expected 'DATABASE' (line 1, column 14 (offset: 13))
        |"SHOW DEFAULT DATABASES"
        |              ^""".stripMargin
    )
  }

  test("SHOW DEFAULT DATABASES YIELD *") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'DATABASES': expected 'DATABASE' (line 1, column 14 (offset: 13))
        |"SHOW DEFAULT DATABASES YIELD *"
        |              ^""".stripMargin
    )
  }

  test("SHOW DEFAULT DATABASES WHERE name STARTS WITH 'foo'") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'DATABASES': expected 'DATABASE' (line 1, column 14 (offset: 13))
        |"SHOW DEFAULT DATABASES WHERE name STARTS WITH 'foo'"
        |              ^""".stripMargin
    )
  }

  test("SHOW HOME DATABASES") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'DATABASES': expected 'DATABASE' (line 1, column 11 (offset: 10))
        |"SHOW HOME DATABASES"
        |           ^""".stripMargin
    )
  }

  test("SHOW HOME DATABASES YIELD *") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'DATABASES': expected 'DATABASE' (line 1, column 11 (offset: 10))
        |"SHOW HOME DATABASES YIELD *"
        |           ^""".stripMargin
    )
  }

  test("SHOW HOME DATABASES WHERE name STARTS WITH 'foo'") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'DATABASES': expected 'DATABASE' (line 1, column 11 (offset: 10))
        |"SHOW HOME DATABASES WHERE name STARTS WITH 'foo'"
        |           ^""".stripMargin
    )
  }
}
