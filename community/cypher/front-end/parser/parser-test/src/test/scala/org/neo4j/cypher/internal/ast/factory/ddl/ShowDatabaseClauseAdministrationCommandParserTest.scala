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
import org.neo4j.cypher.internal.ast.ParsedAsYield
import org.neo4j.cypher.internal.ast.ShowDatabasesClause
import org.neo4j.cypher.internal.ast.SingleNamedDatabaseScope
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.Where
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.ShowDatabaseInterpretedRuntime
import org.neo4j.cypher.internal.ast.test.util.AstParsing
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.Parsers
import org.neo4j.cypher.internal.ast.test.util.Parsers.Cypher25Factory
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.test_helpers.GqlExceptionMatchers.gqlStatus
import org.neo4j.gqlstatus.GqlStatusInfoCodes

class ShowDatabaseClauseAdministrationCommandParserTest extends AdministrationAndSchemaCommandParserTestBase {

  // We want to run Cypher 25 only, and we want to configure the parser with the ShowDatabaseInterpretedRuntime feature
  implicit val parsers: Parsers[Statement] = Parsers(
    Map(AstParsing.Cypher25 -> new Cypher25Factory(Seq(ShowDatabaseInterpretedRuntime)).statement())
  )

  Seq[(
    String,
    (Option[Where], List[CommandResultItem], Boolean, Option[With]) => InputPosition => ShowDatabasesClause
  )](
    (
      "DATABASE",
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
      ShowDatabasesClause.apply(
        SingleNamedDatabaseScope(literal("where"))(pos),
        _: Option[Where],
        _: List[CommandResultItem],
        _: Boolean,
        _: Option[With]
      )
    )
  ).foreach { case (dbType, command) =>
    test(s"SHOW $dbType") {
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ =>
          _.toAstWith(singleQuery(command(None, List.empty, false, None)(pos)), prettifierRoundTrip = false)
      }
    }

    test(s"USE system SHOW $dbType") {
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ => _.toAstWith(
            singleQuery(use(List("system"), resolveStrictly = true), command(None, List.empty, false, None)(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType WHERE access = 'GRANTED'") {
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ => _.toAstWith(
            singleQuery(command(Some(where(equals(accessVar, grantedString))), List.empty, false, None)(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType WHERE access = 'GRANTED' AND action = 'match'") {
      val accessPredicate = equals(accessVar, grantedString)
      val matchPredicate = equals(varFor(actionString), literalString("match"))
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ => _.toAstWith(
            singleQuery(command(
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
      val withClause = With(
        distinct = false,
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause),
        None,
        None,
        None,
        ParsedAsYield
      )(pos)
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access WHERE access ='none'") {
      val orderByClause = orderBy(sortItem(accessVar))
      val withClause = With(
        distinct = false,
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause),
        None,
        None,
        Some(where(equals(accessVar, noneString))),
        ParsedAsYield
      )(pos)
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access SKIP 1 LIMIT 10 WHERE access ='none'") {
      val orderByClause = orderBy(sortItem(accessVar))
      val withClause = With(
        distinct = false,
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause),
        Some(skip(1)),
        Some(limit(10)),
        Some(where(equals(accessVar, noneString))),
        ParsedAsYield
      )(pos)
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access OFFSET 1 LIMIT 10 WHERE access ='none'") {
      val orderByClause = orderBy(sortItem(accessVar))
      val withClause = With(
        distinct = false,
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause),
        Some(skip(1)),
        Some(limit(10)),
        Some(where(equals(accessVar, noneString))),
        ParsedAsYield
      )(pos)
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access SKIP -1") {
      val withClause = With(
        distinct = false,
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        None,
        Some(skip(-1)),
        None,
        None,
        ParsedAsYield
      )(pos)
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access OFFSET -1") {
      val withClause = With(
        distinct = false,
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        None,
        Some(skip(-1)),
        None,
        None,
        ParsedAsYield
      )(pos)
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access ORDER BY access RETURN access") {
      val orderByClause = orderBy(sortItem(accessVar))
      val withClause = With(
        distinct = false,
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        Some(orderByClause),
        None,
        None,
        None,
        ParsedAsYield
      )(pos)
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ => _.toAstWith(
            singleQuery(
              command(None, List(commandResultItem("access")), false, Some(withClause))(pos),
              return_(variableReturnItem("access"))
            ),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD * RETURN *") {
      val withClause = With(
        distinct = false,
        returnAllItems(),
        None,
        None,
        None,
        None,
        ParsedAsYield
      )(pos)
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ => _.toAstWith(
            singleQuery(command(None, List.empty, true, Some(withClause))(pos), returnAll),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access") {
      val withClause = With(
        distinct = false,
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        None,
        None,
        None,
        None,
        ParsedAsYield
      )(pos)
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ => _.toAstWith(
            singleQuery(command(None, List(commandResultItem("access")), false, Some(withClause))(pos)),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType YIELD access RETURN access") {
      val withClause = With(
        distinct = false,
        returnAllItems().withDefaultOrderOnColumns(List("access")),
        None,
        None,
        None,
        None,
        ParsedAsYield
      )(pos)
      parsesIn[Statement] {
        case Cypher5 => _.ignored
        case _ => _.toAstWith(
            singleQuery(
              command(None, List(commandResultItem("access")), false, Some(withClause))(pos),
              return_(variableReturnItem("access"))
            ),
            prettifierRoundTrip = false
          )
      }
    }

    test(s"SHOW $dbType WHERE access = 'GRANTED' RETURN action") {
      failsParsing[Statements]
    }

    // don't allow combining commands at this time
    test(s"SHOW $dbType YIELD name SHOW TRANSACTIONS YIELD database RETURN name, database") {
      failsParsing[Statements]
    }
  }

  test("SHOW DATABASE `foo.bar`") {
    parsesIn[Statement] {
      case Cypher5 => _.ignored
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
      case Cypher5 => _.ignored
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
      case Cypher5 => _.ignored
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
      case Cypher5 => _.ignored
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
