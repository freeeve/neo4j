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
import org.neo4j.cypher.internal.ast.NamespacedName
import org.neo4j.cypher.internal.ast.ShowDatabase
import org.neo4j.cypher.internal.ast.ShowDatabasesClause
import org.neo4j.cypher.internal.ast.SingleNamedDatabaseScope
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.Where
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.YieldOrWhere
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.test_helpers.GqlExceptionMatchers.gqlStatus
import org.neo4j.gqlstatus.GqlStatusInfoCodes

class ShowDatabaseCommandParserTest extends AdministrationAndSchemaCommandParserTestBase {

  Seq[(
    String,
    (YieldOrWhere, Boolean) => InputPosition => ShowDatabase,
    (Option[Where], List[CommandResultItem], Boolean, Option[With]) => InputPosition => ShowDatabasesClause
  )](
    (
      "DATABASE",
      ShowDatabase.apply(AllDatabasesScope()(pos), _: YieldOrWhere, _: Boolean),
      ShowDatabasesClause.apply(
        AllDatabasesScope()(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With]
      )
    ),
    (
      "DATABASES",
      ShowDatabase.apply(AllDatabasesScope()(pos), _: YieldOrWhere, _: Boolean),
      ShowDatabasesClause.apply(
        AllDatabasesScope()(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With]
      )
    ),
    (
      "DEFAULT DATABASE",
      ShowDatabase.apply(DefaultDatabaseScope()(pos), _: YieldOrWhere, _: Boolean),
      ShowDatabasesClause.apply(
        DefaultDatabaseScope()(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With]
      )
    ),
    (
      "HOME DATABASE",
      ShowDatabase.apply(HomeDatabaseScope()(pos), _: YieldOrWhere, _: Boolean),
      ShowDatabasesClause.apply(
        HomeDatabaseScope()(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With]
      )
    ),
    (
      "DATABASE $db",
      ShowDatabase.apply(
        SingleNamedDatabaseScope(stringParamName("db"))(pos),
        _: YieldOrWhere,
        _: Boolean
      ),
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(stringParamName("db"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With]
      )
    ),
    (
      "DATABASES $db",
      ShowDatabase.apply(
        SingleNamedDatabaseScope(stringParamName("db"))(pos),
        _: YieldOrWhere,
        _: Boolean
      ),
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(stringParamName("db"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With]
      )
    ),
    (
      "DATABASE neo4j",
      ShowDatabase.apply(SingleNamedDatabaseScope(literal("neo4j"))(pos), _: YieldOrWhere, _: Boolean),
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(literal("neo4j"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With]
      )
    ),
    (
      "DATABASES neo4j",
      ShowDatabase.apply(SingleNamedDatabaseScope(literal("neo4j"))(pos), _: YieldOrWhere, _: Boolean),
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(literal("neo4j"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With]
      )
    ),
    // vvv naming the database yield/where should not fail either vvv
    (
      "DATABASE yield",
      ShowDatabase.apply(SingleNamedDatabaseScope(literal("yield"))(pos), _: YieldOrWhere, _: Boolean),
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(literal("yield"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With]
      )
    ),
    (
      "DATABASES yield",
      ShowDatabase.apply(SingleNamedDatabaseScope(literal("yield"))(pos), _: YieldOrWhere, _: Boolean),
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(literal("yield"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With]
      )
    ),
    (
      "DATABASE where",
      ShowDatabase.apply(SingleNamedDatabaseScope(literal("where"))(pos), _: YieldOrWhere, _: Boolean),
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(literal("where"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With]
      )
    ),
    (
      "DATABASES where",
      ShowDatabase.apply(SingleNamedDatabaseScope(literal("where"))(pos), _: YieldOrWhere, _: Boolean),
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(literal("where"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With]
      )
    )
  ).foreach { case (dbType, commandCypher5, commandCypher25) =>
    test(s"SHOW $dbType") {
      parsesIn[Statement] {
        case Cypher5 => _.toAst(commandCypher5(None, true)(pos))
        case _ =>
          _.toAstWith(singleQuery(commandCypher25(None, List.empty, false, None)(pos)), prettifierRoundTrip = false)
      }
    }

    test(s"USE system SHOW $dbType") {
      parsesIn[Statement] {
        case Cypher5 =>
          _.toAst(commandCypher5(None, true)(pos).withGraph(Some(use(List("system"), resolveStrictly = false))))
        case _ => _.toAstWith(
            singleQuery(
              use(List("system"), resolveStrictly = true),
              commandCypher25(None, List.empty, false, None)(pos)
            ),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType WHERE access = 'GRANTED'") {
      parsesIn[Statement] {
        case Cypher5 => _.toAst(commandCypher5(Some(Right(where(equals(accessVar, grantedString)))), true)(pos))
        case _ => _.toAstWith(
            singleQuery(commandCypher25(Some(where(equals(accessVar, grantedString))), List.empty, false, None)(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType WHERE access = 'GRANTED' AND action = 'match'") {
      val accessPredicate = equals(accessVar, grantedString)
      val matchPredicate = equals(varFor(actionString), literalString("match"))
      parsesIn[Statement] {
        case Cypher5 => _.toAst(commandCypher5(Some(Right(where(and(accessPredicate, matchPredicate)))), true)(pos))
        case _ => _.toAstWith(
            singleQuery(commandCypher25(
              Some(where(and(accessPredicate, matchPredicate))),
              List.empty,
              false,
              None
            )(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access") {
      val orderByClause = orderBy(sortItem(accessVar))
      val columns = yieldClause(returnItems(variableReturnItem(accessString)), Some(orderByClause))
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause)
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAst(commandCypher5(Some(Left((columns, None))), true)(pos))
        case _ => _.toAstWith(
            singleQuery(commandCypher25(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access WHERE access ='none'") {
      val orderByClause = orderBy(sortItem(accessVar))
      val whereClause = where(equals(accessVar, noneString))
      val columns =
        yieldClause(returnItems(variableReturnItem(accessString)), Some(orderByClause), where = Some(whereClause))
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause),
        where = Some(where(equals(accessVar, noneString)))
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAst(commandCypher5(Some(Left((columns, None))), true)(pos))
        case _ => _.toAstWith(
            singleQuery(commandCypher25(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
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
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause),
        Some(skip(1)),
        Some(limit(10)),
        Some(where(equals(accessVar, noneString)))
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAst(commandCypher5(Some(Left((columns, None))), true)(pos))
        case _ => _.toAstWith(
            singleQuery(commandCypher25(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
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
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause),
        Some(skip(1)),
        Some(limit(10)),
        Some(where(equals(accessVar, noneString)))
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAst(commandCypher5(Some(Left((columns, None))), true)(pos))
        case _ => _.toAstWith(
            singleQuery(commandCypher25(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access SKIP -1") {
      val columns = yieldClause(returnItems(variableReturnItem(accessString)), skip = Some(skip(-1)))
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        skip = Some(skip(-1))
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAst(commandCypher5(Some(Left((columns, None))), true)(pos))
        case _ => _.toAstWith(
            singleQuery(commandCypher25(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access OFFSET -1") {
      val columns = yieldClause(returnItems(variableReturnItem(accessString)), skip = Some(skip(-1)))
      val yieldOrWhere = Some(Left((columns, None)))
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        skip = Some(skip(-1))
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAst(commandCypher5(yieldOrWhere, true)(pos))
        case _ => _.toAstWith(
            singleQuery(commandCypher25(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access RETURN access") {
      val yieldOrWhere = Some(Left((
        yieldClause(returnItems(variableReturnItem(accessString)), Some(orderBy(sortItem(accessVar)))),
        Some(returnClause(returnItems(variableReturnItem(accessString))))
      )))
      val orderByClause = orderBy(sortItem(accessVar))
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause)
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAst(commandCypher5(yieldOrWhere, true)(pos))
        case _ => _.toAstWith(
            singleQuery(
              commandCypher25(None, List(commandResultItem("access")), false, Some(withClause))(pos),
              return_(variableReturnItem("access"))
            ),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD * RETURN *") {
      val yieldOrWhere = Some(Left((yieldClause(returnAllItems), Some(returnClause(returnAllItems)))))
      val withClause = withFromYield(returnAllItems())
      parsesIn[Statement] {
        case Cypher5 => _.toAst(commandCypher5(yieldOrWhere, true)(pos))
        case _ => _.toAstWith(
            singleQuery(commandCypher25(None, List.empty, true, Some(withClause))(pos), returnAll),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access") {
      val columns = yieldClause(returnItems(variableReturnItem(accessString)))
      val yieldOrWhere = Some(Left((columns, None)))
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access"))
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAst(commandCypher5(yieldOrWhere, true)(pos))
        case _ => _.toAstWith(
            singleQuery(commandCypher25(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access RETURN access") {
      val columns = yieldClause(returnItems(variableReturnItem(accessString)))
      val yieldOrWhere = Some(Left((columns, Some(returnClause(returnItems(variableReturnItem(accessString)))))))
      val withClause = withFromYield(
        returnAllItems().withDefaultOrderOnColumns(List("access"))
      )
      parsesIn[Statement] {
        case Cypher5 => _.toAst(commandCypher5(yieldOrWhere, true)(pos))
        case _ => _.toAstWith(
            singleQuery(
              commandCypher25(None, List(commandResultItem("access")), false, Some(withClause))(pos),
              return_(variableReturnItem("access"))
            ),
            prettifierRoundTrip = false
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
              commandCypher25(Some(where(equals(accessVar, grantedString))), List.empty, false, None)(pos),
              return_(variableReturnItem("action"))
            ),
            prettifierRoundTrip = false
          )
      }
    }
  }

  test("SHOW DATABASE `foo.bar`") {
    parsesIn[Statement] {
      case Cypher5 => _.toAst(ShowDatabase(
          SingleNamedDatabaseScope(namespacedName("foo.bar"))(pos),
          None,
          cypher5ColumnsOnly = true
        )(pos))
      case _ => _.toAstWith(
          singleQuery(ShowDatabasesClause(
            SingleNamedDatabaseScope(namespacedName("foo.bar"))(pos),
            None,
            List.empty,
            yieldAll = false,
            None
          )(pos)),
          prettifierRoundTrip = false
        )
    }
  }

  test("SHOW DATABASE foo.bar") {
    parsesIn[Statement] {
      case Cypher5 => _.toAst(ShowDatabase(
          SingleNamedDatabaseScope(NamespacedName(List("bar"), Some("foo"))(pos))(pos),
          None,
          cypher5ColumnsOnly = true
        )(pos))
      case _ => _.toAstWith(
          singleQuery(ShowDatabasesClause(
            SingleNamedDatabaseScope(namespacedName("foo.bar"))(pos),
            None,
            List.empty,
            yieldAll = false,
            None
          )(pos)),
          prettifierRoundTrip = false
        )
    }
  }

  test("SHOW DATABASES YIELD") {
    parsesIn[Statement] {
      case Cypher5 => _.toAst(ShowDatabase(
          SingleNamedDatabaseScope(namespacedName("YIELD"))(pos),
          None,
          cypher5ColumnsOnly = true
        )(pos))
      case _ => _.toAstWith(
          singleQuery(ShowDatabasesClause(
            SingleNamedDatabaseScope(namespacedName("YIELD"))(pos),
            None,
            List.empty,
            yieldAll = false,
            None
          )(pos)),
          prettifierRoundTrip = false
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
