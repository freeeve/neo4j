/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher

import org.neo4j.configuration.Config
import org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME
import org.neo4j.configuration.GraphDatabaseSettings.SYSTEM_DATABASE_NAME
import org.neo4j.configuration.GraphDatabaseSettings.cypher_hints_error
import org.neo4j.configuration.GraphDatabaseSettings.initial_default_database
import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.javacompat.GraphDatabaseCypherService
import org.neo4j.cypher.internal.util.test_helpers.GqlExceptionMatchers.InvalidReferenceStatus
import org.neo4j.cypher.internal.util.test_helpers.GqlExceptionMatchers.gqlException
import org.neo4j.cypher.internal.util.test_helpers.GqlExceptionMatchers.gqlStatus
import org.neo4j.exceptions.Neo4jException
import org.neo4j.gqlstatus.GqlStatusInfoCodes
import org.neo4j.graphdb.config.Setting
import org.scalatest.OptionValues
import org.scalatest.enablers.Messaging.messagingNatureOfThrowable
import org.scalatest.matchers.BeMatcher
import org.scalatest.prop.TableDrivenPropertyChecks.forEvery
import org.scalatest.prop.Tables.Table

import java.lang.Boolean.TRUE
import java.nio.file.Path

import scala.jdk.CollectionConverters.MapHasAsJava

class CommunityMultiDatabaseAdministrationCommandAcceptanceTest extends CommunityAdministrationCommandAcceptanceTestBase
    with OptionValues {

  test("should fail at startup when config setting for default database name is invalid") {
    // GIVEN
    val startOfError = "Error evaluating value for setting 'initial.dbms.default_database'. "

    // Empty name
    the[IllegalArgumentException] thrownBy {
      // WHEN
      Config.defaults(initial_default_database, "")
      // THEN
    } should have message startOfError + "Failed to validate '' for 'initial.dbms.default_database': The provided database name is empty."

    // Starting on invalid character
    the[IllegalArgumentException] thrownBy {
      // WHEN
      Config.defaults(initial_default_database, "_default")
      // THEN
    } should have message startOfError + "Failed to validate '_default' for 'initial.dbms.default_database': Database name '_default' is not starting with an ASCII alphabetic character or number."

    // Has prefix 'system'
    the[IllegalArgumentException] thrownBy {
      // WHEN
      Config.defaults(initial_default_database, "system-mine")
      // THEN
    } should have message startOfError + "Failed to validate 'system-mine' for 'initial.dbms.default_database': Database name 'system-mine' is invalid, due to the prefix 'system'."

    // Contains invalid characters
    the[IllegalArgumentException] thrownBy {
      // WHEN
      Config.defaults(initial_default_database, "mydbwith_and%")
      // THEN
    } should have message startOfError + "Failed to validate 'mydbwith_and%' for 'initial.dbms.default_database': Database name 'mydbwith_and%' contains illegal characters. Use simple ascii characters, numbers, dots and dashes."

    // Too short name
    the[IllegalArgumentException] thrownBy {
      // WHEN
      Config.defaults(initial_default_database, "me")
      // THEN
    } should have message startOfError + "Failed to validate 'me' for 'initial.dbms.default_database': The provided database name must have a length between 3 and 63 characters."

    // Too long name
    val name = "ihaveallooootoflettersclearlymorethanishould-ihaveallooootoflettersclearlymorethanishould"
    the[IllegalArgumentException] thrownBy {
      // WHEN
      Config.defaults(initial_default_database, name)
      // THEN
    } should have message startOfError + "Failed to validate '" + name + "' for 'initial.dbms.default_database': The provided database name must have a length between 3 and 63 characters."
  }

  // Test for default language

  test("should alter default database to a specific cypher version") {
    // Might need to be enabled when the next experimental version appear: GraphDatabaseInternalSettings.enable_experimental_cypher_versions -> java.lang.Boolean.TRUE
    setup()

    // WHEN
    execute(
      s"ALTER DATABASE $DEFAULT_DATABASE_NAME SET DEFAULT LANGUAGE CYPHER 25"
    ).queryStatistics().systemUpdates shouldBe 1

    // THEN
    assertDefaultCypherVersion(DEFAULT_DATABASE_NAME, CypherVersion.Cypher25)

    // WHEN
    execute(
      s"ALTER DATABASE $DEFAULT_DATABASE_NAME SET DEFAULT LANGUAGE CYPHER 5"
    ).queryStatistics().systemUpdates shouldBe 1

    // THEN
    assertDefaultCypherVersion(DEFAULT_DATABASE_NAME, CypherVersion.Cypher5)

  }

  test("should alter database quoting variations") {
    val invalidReferenceSyntax = (name: String) =>
      gqlException(
        s"Incorrectly formatted graph reference '$name'. Expected a single quoted or unquoted identifier. Separate name parts should not be quoted individually.",
        InvalidReferenceStatus.withCause(
          GqlStatusInfoCodes.STATUS_42NAA,
          s"error: syntax error or access rule violation - incorrectly formatted graph reference. Incorrectly formatted graph reference '$name'. Expected a single quoted or unquoted identifier. Separate name parts should not be quoted individually."
        ),
        fuzzyMsg = true
      )

    val databaseNotFoundParam = (name: String, param: String) =>
      gqlException(
        s"Failed to alter the specified database '$name': Database does not exist.",
        InvalidReferenceStatus.withCause(
          gqlStatus(
            GqlStatusInfoCodes.STATUS_42N51,
            s"error: syntax error or access rule violation - invalid parameter. Invalid parameter $$`$param`."
          ).withCause(
            GqlStatusInfoCodes.STATUS_42N00,
            s"error: syntax error or access rule violation - graph reference not found. A graph reference with the name `$name` was not found. Verify that the spelling is correct."
          )
        ),
        fuzzyMsg = true
      )

    val deprecatedGraphRef = (deprecatedName: String, futureName: String) =>
      s"Graph references with separately backticked name parts ($deprecatedName) are deprecated. In future Cypher versions, use parameters or backtick the entire name ($futureName)."

    sealed trait ExpectedResult
    case class Succeed() extends ExpectedResult
    case class ReturnEmpty() extends ExpectedResult
    case class Throw(error: BeMatcher[Exception]) extends ExpectedResult

    // format: off
    val scenarios = Table[String, String, Map[String, String], CypherVersion, ExpectedResult, Seq[String]](
      ("id",  "name literal", "params",                 "cypher version",       "expected result",                                  "notifications"),
      ("0",   "`aaa`.`bbb`",  Map.empty,                CypherVersion.Cypher5,  Succeed(),                                          Seq(deprecatedGraphRef("`aaa`.`bbb`", "`aaa.bbb`"))),
      ("1",   "`aaa`.`bbb`",  Map.empty,                CypherVersion.Cypher25, Throw(invalidReferenceSyntax("`aaa`.`bbb`")),       Seq()),
      ("2",   "aaa.bbb",      Map.empty,                CypherVersion.Cypher5,  Succeed(),                                          Seq()),
      ("3",   "aaa.bbb",      Map.empty,                CypherVersion.Cypher25, Succeed(),                                          Seq()),
      ("4",   "`aaa.bbb`",    Map.empty,                CypherVersion.Cypher5,  Succeed(),                                          Seq()),
      ("5",   "`aaa.bbb`",    Map.empty,                CypherVersion.Cypher25, Succeed(),                                          Seq()),
      ("6",   "$p",           Map("p"->"`aaa`.`bbb`"),  CypherVersion.Cypher5,  Throw(databaseNotFoundParam("`aaa`.`bbb`", "p")),   Seq()),
      ("7",   "$p",           Map("p"->"`aaa`.`bbb`"),  CypherVersion.Cypher25, Throw(databaseNotFoundParam("`aaa`.`bbb`", "p")),   Seq()),
      ("8",   "$p",           Map("p"->"aaa.bbb"),      CypherVersion.Cypher5,  Succeed(),                                          Seq()),
      ("9",   "$p",           Map("p"->"aaa.bbb"),      CypherVersion.Cypher25, Succeed(),                                          Seq()),
      ("10",  "$p",           Map("p"->"`aaa.bbb`"),    CypherVersion.Cypher5,  Throw(databaseNotFoundParam("`aaa.bbb`", "p")),     Seq()),
      ("11",  "$p",           Map("p"->"`aaa.bbb`"),    CypherVersion.Cypher25, Throw(databaseNotFoundParam("`aaa.bbb`", "p")),     Seq()),
    )
    // format: on
    forEvery(scenarios) { (_, nameLiteral, params, cypherVersion, expectedResult, notifications) =>
      try {
        // GIVEN
        // Might need to be enabled when the next experimental version appear: config.set(GraphDatabaseInternalSettings.enable_experimental_cypher_versions, TRUE)
        setup(Map(initial_default_database -> "aaa.bbb"))

        val alterQuery =
          s"CYPHER ${cypherVersion.versionName} ALTER DATABASE $nameLiteral SET DEFAULT LANGUAGE CYPHER 25"

        expectedResult match {
          case Succeed() =>
            // WHEN
            val alterRes = execute(alterQuery, params)
            // THEN
            alterRes.queryStatistics().systemUpdates should be(1)
            assertDefaultCypherVersion("aaa.bbb", CypherVersion.Cypher25)
            alterRes.notifications.map(_.getDescription) should be(notifications)
          case ReturnEmpty() =>
            // WHEN
            val alterRes = execute(alterQuery, params)
            // THEN
            alterRes.queryStatistics().systemUpdates should be(0)
            assertDefaultCypherVersion("aaa.bbb", CypherVersion.Cypher5)
            alterRes.notifications should be(notifications)
          case Throw(error) =>
            // WHEN ... THEN
            the[Exception] thrownBy execute(alterQuery, params) should be(error)
        }

      } finally {
        afterEach()
      }
    }
  }

  test("should alter system database to a specific cypher version") {
    // Might need to be enabled when the next experimental version appear: GraphDatabaseInternalSettings.enable_experimental_cypher_versions -> java.lang.Boolean.TRUE
    setup()

    // WHEN
    execute(
      s"ALTER DATABASE $SYSTEM_DATABASE_NAME SET DEFAULT LANGUAGE CYPHER 25"
    ).queryStatistics().systemUpdates shouldBe 1

    // THEN
    assertDefaultCypherVersion(SYSTEM_DATABASE_NAME, CypherVersion.Cypher25)

    // WHEN
    execute(
      s"ALTER DATABASE $SYSTEM_DATABASE_NAME SET DEFAULT LANGUAGE CYPHER 5"
    ).queryStatistics().systemUpdates shouldBe 1

    // THEN
    assertDefaultCypherVersion(SYSTEM_DATABASE_NAME, CypherVersion.Cypher5)
  }

  test("should alter default database to a specific cypher version if it exists") {
    // Might need to be enabled when the next experimental version appear: GraphDatabaseInternalSettings.enable_experimental_cypher_versions -> java.lang.Boolean.TRUE
    setup()

    // WHEN
    execute(
      s"ALTER DATABASE $DEFAULT_DATABASE_NAME IF EXISTS SET DEFAULT LANGUAGE CYPHER 25"
    ).queryStatistics().systemUpdates shouldBe 1

    // THEN
    assertDefaultCypherVersion(DEFAULT_DATABASE_NAME, CypherVersion.Cypher25)

    // WHEN database does not exist, no effect
    execute(
      s"ALTER DATABASE doesNotExist IF EXISTS SET DEFAULT LANGUAGE CYPHER 5"
    ).queryStatistics().systemUpdates shouldBe 0
  }

  // Test for non-valid community commands

  test("should fail on creating database from community") {
    // GIVEN
    setup()

    // THEN

    assertFailureWithGQLStatus(
      "CREATE DATABASE foo",
      "Unsupported administration command: CREATE DATABASE foo",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'CREATE DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      "CREATE DATABASE $foo",
      "Unsupported administration command: CREATE DATABASE $foo",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'CREATE DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"CREATE DATABASE $DEFAULT_DATABASE_NAME",
      s"Unsupported administration command: CREATE DATABASE $DEFAULT_DATABASE_NAME",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'CREATE DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"CREATE DATABASE $DEFAULT_DATABASE_NAME IF NOT EXISTS",
      s"Unsupported administration command: CREATE DATABASE $DEFAULT_DATABASE_NAME IF NOT EXISTS",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'CREATE DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"CREATE OR REPLACE DATABASE $DEFAULT_DATABASE_NAME",
      s"Unsupported administration command: CREATE OR REPLACE DATABASE $DEFAULT_DATABASE_NAME",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'CREATE OR REPLACE DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"CREATE DATABASE $DEFAULT_DATABASE_NAME OPTIONS {existingData: 'use', existingDataSeedInstance: '1'}",
      s"Unsupported administration command: CREATE DATABASE $DEFAULT_DATABASE_NAME OPTIONS {existingData: 'use', existingDataSeedInstance: '1'}",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'CREATE DATABASE' is not supported in community edition."
    )
  }

  test("should fail on dropping database from community") {
    // GIVEN
    setup()

    // THEN

    assertFailureWithGQLStatus(
      "DROP DATABASE foo",
      "Unsupported administration command: DROP DATABASE foo",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'DROP DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      "DROP DATABASE $foo",
      "Unsupported administration command: DROP DATABASE $foo",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'DROP DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"DROP DATABASE $DEFAULT_DATABASE_NAME",
      s"Unsupported administration command: DROP DATABASE $DEFAULT_DATABASE_NAME",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'DROP DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"DROP DATABASE $DEFAULT_DATABASE_NAME IF EXISTS",
      s"Unsupported administration command: DROP DATABASE $DEFAULT_DATABASE_NAME IF EXISTS",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'DROP DATABASE' is not supported in community edition."
    )
  }

  test("should fail on altering database from community") {
    // GIVEN
    // have to enable spd setting to get correct error message
    setup()

    // THEN
    assertFailureWithGQLStatus(
      "ALTER DATABASE foo SET ACCESS READ ONLY",
      "Unsupported administration command: ALTER DATABASE foo SET ACCESS READ ONLY",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'ALTER DATABASE SET ACCESS' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"ALTER DATABASE $DEFAULT_DATABASE_NAME SET ACCESS READ WRITE",
      s"Unsupported administration command: ALTER DATABASE $DEFAULT_DATABASE_NAME SET ACCESS READ WRITE",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'ALTER DATABASE SET ACCESS' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"ALTER DATABASE $SYSTEM_DATABASE_NAME SET DEFAULT LANGUAGE CYPHER 5 WAIT",
      s"Unsupported administration command: ALTER DATABASE $SYSTEM_DATABASE_NAME SET DEFAULT LANGUAGE CYPHER 5 WAIT",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'ALTER DATABASE SET DEFAULT LANGUAGE WAIT' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"ALTER DATABASE $SYSTEM_DATABASE_NAME SET DEFAULT LANGUAGE CYPHER 5 SET ACCESS READ ONLY",
      s"Unsupported administration command: ALTER DATABASE $SYSTEM_DATABASE_NAME SET DEFAULT LANGUAGE CYPHER 5 SET ACCESS READ ONLY",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'ALTER DATABASE SET ACCESS SET DEFAULT LANGUAGE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"ALTER DATABASE $SYSTEM_DATABASE_NAME SET DEFAULT LANGUAGE CYPHER 5 SET OPTION txLogEnrichment null",
      s"Unsupported administration command: ALTER DATABASE $SYSTEM_DATABASE_NAME SET DEFAULT LANGUAGE CYPHER 5 SET OPTION txLogEnrichment null",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'ALTER DATABASE SET DEFAULT LANGUAGE SET OPTION' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"ALTER DATABASE $DEFAULT_DATABASE_NAME SET TOPOLOGY 1 PRIMARY SET DEFAULT LANGUAGE CYPHER 5",
      s"Unsupported administration command: ALTER DATABASE $DEFAULT_DATABASE_NAME SET TOPOLOGY 1 PRIMARY SET DEFAULT LANGUAGE CYPHER 5",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'ALTER DATABASE SET TOPOLOGY SET DEFAULT LANGUAGE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"ALTER DATABASE $DEFAULT_DATABASE_NAME REMOVE OPTION txLogEnrichment",
      s"Unsupported administration command: ALTER DATABASE $DEFAULT_DATABASE_NAME REMOVE OPTION txLogEnrichment",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'ALTER DATABASE REMOVE OPTION' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"CYPHER 25 ALTER DATABASE $DEFAULT_DATABASE_NAME SET GRAPH SHARD { SET TOPOLOGY 1 PRIMARY }",
      s"Unsupported administration command: ALTER DATABASE $DEFAULT_DATABASE_NAME SET GRAPH SHARD { SET TOPOLOGY 1 PRIMARY }",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'ALTER DATABASE SET GRAPH SHARD' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"CYPHER 25 ALTER DATABASE $DEFAULT_DATABASE_NAME SET PROPERTY SHARD { SET TOPOLOGY 1 REPLICA }",
      s"Unsupported administration command: ALTER DATABASE $DEFAULT_DATABASE_NAME SET PROPERTY SHARD { SET TOPOLOGY 1 REPLICA }",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'ALTER DATABASE SET PROPERTY SHARD' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"CYPHER 25 ALTER DATABASE $DEFAULT_DATABASE_NAME SET TOPOLOGY 1 REPLICA",
      s"Unsupported administration command: ALTER DATABASE $DEFAULT_DATABASE_NAME SET TOPOLOGY 1 REPLICA",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'ALTER DATABASE SET TOPOLOGY' is not supported in community edition."
    )

  }

  test("should fail on starting database from community") {
    // GIVEN
    setup()

    // THEN

    assertFailureWithGQLStatus(
      "START DATABASE foo",
      "Unsupported administration command: START DATABASE foo",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'START DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      "START DATABASE $foo",
      "Unsupported administration command: START DATABASE $foo",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'START DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"START DATABASE $DEFAULT_DATABASE_NAME",
      s"Unsupported administration command: START DATABASE $DEFAULT_DATABASE_NAME",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'START DATABASE' is not supported in community edition."
    )
  }

  test("should fail on stopping database from community") {
    // GIVEN
    setup()

    // THEN

    assertFailureWithGQLStatus(
      "STOP DATABASE foo",
      "Unsupported administration command: STOP DATABASE foo",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'STOP DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      "STOP DATABASE $foo",
      "Unsupported administration command: STOP DATABASE $foo",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'STOP DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"STOP DATABASE $DEFAULT_DATABASE_NAME",
      s"Unsupported administration command: STOP DATABASE $DEFAULT_DATABASE_NAME",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'STOP DATABASE' is not supported in community edition."
    )
  }

  test("should fail on creating composite database from community") {
    // GIVEN
    setup()

    // THEN

    assertFailureWithGQLStatus(
      "CREATE COMPOSITE DATABASE foo",
      "Unsupported administration command: CREATE COMPOSITE DATABASE foo",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'CREATE COMPOSITE DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      "CREATE COMPOSITE DATABASE $foo",
      "Unsupported administration command: CREATE COMPOSITE DATABASE $foo",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'CREATE COMPOSITE DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"CREATE COMPOSITE DATABASE $DEFAULT_DATABASE_NAME",
      s"Unsupported administration command: CREATE COMPOSITE DATABASE $DEFAULT_DATABASE_NAME",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'CREATE COMPOSITE DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"CREATE COMPOSITE DATABASE $DEFAULT_DATABASE_NAME IF NOT EXISTS",
      s"Unsupported administration command: CREATE COMPOSITE DATABASE $DEFAULT_DATABASE_NAME IF NOT EXISTS",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'CREATE COMPOSITE DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"CREATE OR REPLACE COMPOSITE DATABASE $DEFAULT_DATABASE_NAME",
      s"Unsupported administration command: CREATE OR REPLACE COMPOSITE DATABASE $DEFAULT_DATABASE_NAME",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'CREATE OR REPLACE COMPOSITE DATABASE' is not supported in community edition."
    )
  }

  test("should fail on dropping composite database from community") {
    // GIVEN
    setup()

    // THEN

    assertFailureWithGQLStatus(
      "DROP COMPOSITE DATABASE foo",
      "Unsupported administration command: DROP COMPOSITE DATABASE foo",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'DROP COMPOSITE DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      "DROP COMPOSITE DATABASE $foo",
      "Unsupported administration command: DROP COMPOSITE DATABASE $foo",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'DROP COMPOSITE DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"DROP COMPOSITE DATABASE $DEFAULT_DATABASE_NAME",
      s"Unsupported administration command: DROP COMPOSITE DATABASE $DEFAULT_DATABASE_NAME",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'DROP COMPOSITE DATABASE' is not supported in community edition."
    )

    assertFailureWithGQLStatus(
      s"DROP COMPOSITE DATABASE foo IF EXISTS",
      s"Unsupported administration command: DROP COMPOSITE DATABASE foo IF EXISTS",
      GqlStatusInfoCodes.STATUS_51N27,
      "error: system configuration or operation exception - not supported in this edition. 'DROP COMPOSITE DATABASE' is not supported in community edition."
    )
  }

  test("should fail with enterprise-only runtime") {
    // GIVEN
    setup(Map(cypher_hints_error -> TRUE))

    // WHEN
    selectDatabase(DEFAULT_DATABASE_NAME)
    val exception = the[Neo4jException] thrownBy {
      execute(s"CYPHER runtime=parallel SHOW DATABASE $DEFAULT_DATABASE_NAME")
    }

    // THEN
    exception should have message "This version of Neo4j does not support the requested runtime: `parallel`"
    exception.gqlStatusObject().gqlStatus() should be(GqlStatusInfoCodes.STATUS_22000.getStatusString)
    exception.gqlStatusObject().statusDescription() should be("error: data exception")
    val cause = exception.gqlStatusObject().cause()
    cause should not be empty
    cause.get().gqlStatus() should be(GqlStatusInfoCodes.STATUS_51N27.getStatusString)
    cause.get().statusDescription() should be(
      "error: system configuration or operation exception - not supported in this edition. 'parallel' is not supported in community edition."
    )

  }

  // Helper methods

  private def assertDefaultCypherVersion(dbName: String, version: CypherVersion) = {
    execute(s"SHOW DATABASE $dbName YIELD name, defaultLanguage RETURN *").toList shouldBe List(Map(
      "name" -> dbName,
      "defaultLanguage" -> version.description
    ))
  }

  // Disable normal database creation because we need different settings on each test
  override protected def beforeEach(): Unit = {
    resetLogs() // Don't keep the cumulative logs in memory to avoid OOM
  }

  private def setup(config: Map[Setting[_], Object] = Map.empty): Unit = {
    managementService = graphDatabaseFactory(Path.of("test")).impermanent().setConfig(
      config.asJava
    ).setInternalLogProvider(logProvider).build()
    graphOps = managementService.database(SYSTEM_DATABASE_NAME)
    graph = new GraphDatabaseCypherService(graphOps)
  }
}
