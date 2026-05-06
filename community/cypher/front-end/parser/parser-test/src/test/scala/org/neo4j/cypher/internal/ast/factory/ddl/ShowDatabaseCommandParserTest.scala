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

import org.neo4j.cypher.internal.ast.AllDatabasesScope
import org.neo4j.cypher.internal.ast.CommandResultItem
import org.neo4j.cypher.internal.ast.DefaultDatabaseScope
import org.neo4j.cypher.internal.ast.HomeDatabaseScope
import org.neo4j.cypher.internal.ast.ShowDatabasesClause
import org.neo4j.cypher.internal.ast.SingleNamedDatabaseScope
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.Where
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.test_helpers.GqlExceptionMatchers.gqlStatus
import org.neo4j.gqlstatus.GqlStatusInfoCodes

class ShowDatabaseCommandParserTest extends AdministrationAndSchemaCommandParserTestBase {

  Seq[(
    String,
    (Option[Where], List[CommandResultItem], Boolean, Option[With], Boolean) => InputPosition => ShowDatabasesClause
  )](
    (
      "DATABASE",
      ShowDatabasesClause.apply(
        AllDatabasesScope()(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With],
        _: Boolean
      )
    ),
    (
      "DATABASES",
      ShowDatabasesClause.apply(
        AllDatabasesScope()(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With],
        _: Boolean
      )
    ),
    (
      "DEFAULT DATABASE",
      ShowDatabasesClause.apply(
        DefaultDatabaseScope()(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With],
        _: Boolean
      )
    ),
    (
      "HOME DATABASE",
      ShowDatabasesClause.apply(
        HomeDatabaseScope()(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With],
        _: Boolean
      )
    ),
    (
      "DATABASE $db",
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(stringParamName("db"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With],
        _: Boolean
      )
    ),
    (
      "DATABASES $db",
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(stringParamName("db"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With],
        _: Boolean
      )
    ),
    (
      "DATABASE neo4j",
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(literal("neo4j"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With],
        _: Boolean
      )
    ),
    (
      "DATABASES neo4j",
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(literal("neo4j"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With],
        _: Boolean
      )
    ),
    // vvv naming the database yield/where should not fail either vvv
    (
      "DATABASE yield",
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(literal("yield"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With],
        _: Boolean
      )
    ),
    (
      "DATABASES yield",
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(literal("yield"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With],
        _: Boolean
      )
    ),
    (
      "DATABASE where",
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(literal("where"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With],
        _: Boolean
      )
    ),
    (
      "DATABASES where",
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(literal("where"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With],
        _: Boolean
      )
    )
  ).foreach { case (dbType, command) =>
    test(s"SHOW $dbType") {
      parsesIn[Statement] {
        case Cypher5 =>
          _.toAstWith(singleQuery(command(None, List.empty, false, None, true)(pos)))
        case _ =>
          _.toAstWith(singleQuery(command(None, List.empty, false, None, false)(pos)))
      }
    }

    test(s"USE system SHOW $dbType") {
      parsesIn[Statement] {
        case Cypher5 =>
          _.toAstWith(
            singleQuery(
              use(List("system"), resolveStrictly = false),
              command(None, List.empty, false, None, true)(pos)
            )
          )
        case _ => _.toAstWith(
            singleQuery(
              use(List("system"), resolveStrictly = true),
              command(None, List.empty, false, None, false)(pos)
            )
          )
      }
    }

    test(s"SHOW $dbType WHERE access = 'GRANTED'") {
      parsesIn[Statement] {
        case Cypher5 => _.toAstWith(
            singleQuery(command(Some(where(equals(accessVar, grantedString))), List.empty, false, None, true)(pos))
          )
        case _ => _.toAstWith(
            singleQuery(command(Some(where(equals(accessVar, grantedString))), List.empty, false, None, false)(pos))
          )
      }
    }

    test(s"SHOW $dbType WHERE access = 'GRANTED' AND action = 'match'") {
      val accessPredicate = equals(accessVar, grantedString)
      val matchPredicate = equals(varFor(actionString), literalString("match"))
      parsesIn[Statement] {
        case Cypher5 => _.toAstWith(
            singleQuery(command(
              Some(where(and(accessPredicate, matchPredicate))),
              List.empty,
              false,
              None,
              true
            )(pos))
          )
        case _ => _.toAstWith(
            singleQuery(command(
              Some(where(and(accessPredicate, matchPredicate))),
              List.empty,
              false,
              None,
              false
            )(pos))
          )
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access") {
      val orderByClause = orderBy(sortItem(accessVar))
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause)
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), true)(pos))
          )
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), false)(pos))
          )
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access WHERE access ='none'") {
      val orderByClause = orderBy(sortItem(accessVar))
      val whereClause = where(equals(accessVar, noneString))
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause),
        where = Some(whereClause)
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), true)(pos))
          )
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), false)(pos))
          )
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access SKIP 1 LIMIT 10 WHERE access ='none'") {
      val orderByClause = orderBy(sortItem(accessVar))
      val whereClause = where(equals(accessVar, noneString))
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause),
        Some(skip(1)),
        Some(limit(10)),
        Some(whereClause)
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), true)(pos))
          )
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), false)(pos))
          )
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access OFFSET 1 LIMIT 10 WHERE access ='none'") {
      val orderByClause = orderBy(sortItem(accessVar))
      val whereClause = where(equals(accessVar, noneString))
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause),
        Some(skip(1)),
        Some(limit(10)),
        Some(whereClause)
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), true)(pos))
          )
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), false)(pos))
          )
      }
    }

    test(s"SHOW $dbType YIELD access SKIP -1") {
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        skip = Some(skip(-1))
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), true)(pos))
          )
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), false)(pos))
          )
      }
    }

    test(s"SHOW $dbType YIELD access OFFSET -1") {
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        skip = Some(skip(-1))
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), true)(pos))
          )
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), false)(pos))
          )
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access RETURN access") {
      val orderByClause = orderBy(sortItem(accessVar))
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause)
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAstWith(
            singleQuery(
              command(None, List(commandResultItem("access")), false, Some(withClause), true)(pos),
              return_(variableReturnItem("access"))
            )
          )
        case _ => _.toAstWith(
            singleQuery(
              command(None, List(commandResultItem("access")), false, Some(withClause), false)(pos),
              return_(variableReturnItem("access"))
            )
          )
      }
    }

    test(s"SHOW $dbType YIELD * RETURN *") {
      val withClause = withFromYield(returnAllItems())
      parsesIn[Statement] {
        case Cypher5 => _.toAstWith(
            singleQuery(command(None, List.empty, true, Some(withClause), true)(pos), returnAll)
          )
        case _ => _.toAstWith(
            singleQuery(command(None, List.empty, true, Some(withClause), false)(pos), returnAll)
          )
      }
    }

    test(s"SHOW $dbType YIELD access") {
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access"))
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), true)(pos))
          )
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause), false)(pos))
          )
      }
    }

    test(s"SHOW $dbType YIELD access RETURN access") {
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access"))
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAstWith(
            singleQuery(
              command(None, List(commandResultItem("access")), false, Some(withClause), true)(pos),
              return_(variableReturnItem("access"))
            )
          )
        case _ => _.toAstWith(
            singleQuery(
              command(None, List(commandResultItem("access")), false, Some(withClause), false)(pos),
              return_(variableReturnItem("access"))
            )
          )
      }
    }

    test(s"SHOW $dbType WHERE access = 'GRANTED' RETURN action") {
      parsesIn[Statement] {
        case Cypher5 => _.withMessageStart(
            "Invalid input 'RETURN': expected an expression or <EOF>"
          )
            .withGqlStatus(
              gqlStatus(
                GqlStatusInfoCodes.STATUS_42I06,
                "error: syntax error or access rule violation - invalid input. Invalid input 'RETURN', expected: an expression or <EOF>."
              )
            )
        case _ => _.toAstWith(
            singleQuery(
              command(Some(where(equals(accessVar, grantedString))), List.empty, false, None, false)(pos),
              return_(variableReturnItem("action"))
            )
          )
      }
    }
  }

  test("SHOW DATABASE `foo.bar`") {
    parsesIn[Statement] {
      case Cypher5 => _.toAstWith(
          singleQuery(ShowDatabasesClause(
            SingleNamedDatabaseScope(namespacedName("foo.bar"))(pos),
            None,
            List.empty,
            yieldAll = false,
            None,
            cypher5ColumnsOnly = true
          )(pos))
        )
      case _ => _.toAstWith(
          singleQuery(ShowDatabasesClause(
            SingleNamedDatabaseScope(namespacedName("foo.bar"))(pos),
            None,
            List.empty,
            yieldAll = false,
            None,
            cypher5ColumnsOnly = false
          )(pos))
        )
    }
  }

  test("SHOW DATABASE foo.bar") {
    parsesIn[Statement] {
      case Cypher5 => _.toAstWith(
          singleQuery(ShowDatabasesClause(
            SingleNamedDatabaseScope(namespacedName("foo", "bar"))(pos),
            None,
            List.empty,
            yieldAll = false,
            None,
            cypher5ColumnsOnly = true
          )(pos))
        )
      case _ => _.toAstWith(
          singleQuery(ShowDatabasesClause(
            SingleNamedDatabaseScope(namespacedName("foo.bar"))(pos),
            None,
            List.empty,
            yieldAll = false,
            None,
            cypher5ColumnsOnly = false
          )(pos))
        )
    }
  }

  test("SHOW DATABASES YIELD") {
    parsesIn[Statement] {
      case Cypher5 => _.toAstWith(
          singleQuery(ShowDatabasesClause(
            SingleNamedDatabaseScope(namespacedName("YIELD"))(pos),
            None,
            List.empty,
            yieldAll = false,
            None,
            cypher5ColumnsOnly = true
          )(pos))
        )
      case _ => _.toAstWith(
          singleQuery(ShowDatabasesClause(
            SingleNamedDatabaseScope(namespacedName("YIELD"))(pos),
            None,
            List.empty,
            yieldAll = false,
            None,
            cypher5ColumnsOnly = false
          )(pos))
        )
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

  test("SHOW DATABASE db1 YIELD") {
    failsParsing[Statements]
  }

  test("SHOW DATABASES YIELD * RETURN") {
    failsParsing[Statements]
  }

  test("SHOW DATABASE db1 YIELD * RETURN") {
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
