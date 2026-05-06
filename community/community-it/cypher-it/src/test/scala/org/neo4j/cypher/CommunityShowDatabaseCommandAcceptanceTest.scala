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

import org.neo4j.configuration.GraphDatabaseSettings
import org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME
import org.neo4j.configuration.GraphDatabaseSettings.SYSTEM_DATABASE_NAME
import org.neo4j.configuration.GraphDatabaseSettings.initial_default_database
import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.DatabaseStatus
import org.neo4j.cypher.internal.javacompat.GraphDatabaseCypherService
import org.neo4j.cypher.internal.util.test_helpers.GqlExceptionMatchers.InvalidReferenceStatus
import org.neo4j.cypher.internal.util.test_helpers.GqlExceptionMatchers.gqlException
import org.neo4j.exceptions.SyntaxException
import org.neo4j.gqlstatus.GqlStatusInfoCodes
import org.neo4j.graphdb.config.Setting
import org.neo4j.kernel.internal.GraphDatabaseAPI
import org.neo4j.storageengine.api.MetadataProvider
import org.scalatest.OptionValues
import org.scalatest.matchers.BeMatcher
import org.scalatest.prop.TableDrivenPropertyChecks.forEvery
import org.scalatest.prop.Tables.Table

import java.nio.file.Path
import java.time.ZonedDateTime

import scala.jdk.CollectionConverters.MapHasAsJava

class CommunityShowDatabaseCommandAcceptanceTest extends CommunityAdministrationCommandAcceptanceTestBase
    with OptionValues {

  private val onlineStatus: String = DatabaseStatus.Online.stringValue()
  private val accessString: String = "read-write"
  private val typeString: String = "standard"
  private val localHostString: String = "localhost:0"
  protected val dbDefaultMap: Map[String, String] = Map("db" -> DEFAULT_DATABASE_NAME)
  private val nameDefaultMap: Map[String, String] = Map("name" -> DEFAULT_DATABASE_NAME)
  private val nameSystemMap: Map[String, String] = Map("name" -> SYSTEM_DATABASE_NAME)

  override def databaseConfig(): Map[Setting[?], Object] = {
    super.databaseConfig() ++ Map(
      GraphDatabaseSettings.default_language -> GraphDatabaseSettings.CypherVersion.Cypher25
    )
  }

  test(s"should show database $DEFAULT_DATABASE_NAME") {
    // GIVEN
    setup()

    // WHEN
    val result = execute(s"SHOW DATABASE $DEFAULT_DATABASE_NAME")

    // THEN
    result.toList should be(List(db(DEFAULT_DATABASE_NAME, home = true, default = true)))
  }

  test(s"should show database $DEFAULT_DATABASE_NAME with params") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASE $db", dbDefaultMap)

    // THEN
    result.toList should be(List(db(DEFAULT_DATABASE_NAME, home = true, default = true)))
  }

  test("should give nothing when showing a non-existing database") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASE foo")

    // THEN
    result.toList should be(List.empty)

    // and an invalid (non-existing) one
    // WHEN
    val result2 = execute("SHOW DATABASE ``")

    // THEN
    result2.toList should be(List.empty)
  }

  test("should fail when showing a database when not on system database") {
    // GIVEN
    setup()

    // THEN
    assertFailWhenNotOnSystem(s"SHOW DATABASE $DEFAULT_DATABASE_NAME", "SHOW DATABASE")
  }

  test("should show default databases") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASES")

    // THEN
    result.toSet should be(Set(
      db(DEFAULT_DATABASE_NAME, home = true, default = true),
      db(SYSTEM_DATABASE_NAME, dbType = SYSTEM_DATABASE_NAME)
    ))
  }

  test("should fail when showing databases when not on system database") {
    // GIVEN
    setup()

    // THEN
    assertFailWhenNotOnSystem("SHOW DATABASES", "SHOW DATABASES")
  }

  test("should show default database") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DEFAULT DATABASE")

    // THEN
    result.toList should be(List(homeOrDefaultDb(DEFAULT_DATABASE_NAME)))
  }

  test("should show custom default database using show default database command") {
    // GIVEN
    setup(Map(initial_default_database -> "foo"))

    // WHEN
    val result = execute("SHOW DEFAULT DATABASE")

    // THEN
    result.toList should be(List(homeOrDefaultDb("foo")))
  }

  test("should show custom default database with unusual name using show default database command") {
    // GIVEN
    setup(Map(initial_default_database -> "123abc"))

    // WHEN
    val result = execute("SHOW DEFAULT DATABASE")

    // THEN
    result.toList should be(List(
      homeOrDefaultDb("123abc")
    ))
  }

  test("should show database quoting variations") {
    val invalidReferenceSyntax = (name: String) =>
      gqlException(
        s"Incorrectly formatted graph reference '$name'. Expected a single quoted or unquoted identifier. Separate name parts should not be quoted individually.",
        InvalidReferenceStatus.withCause(
          GqlStatusInfoCodes.STATUS_42NAA,
          s"error: syntax error or access rule violation - incorrectly formatted graph reference. Incorrectly formatted graph reference '$name'. Expected a single quoted or unquoted identifier. Separate name parts should not be quoted individually."
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
      ("id",  "name literal", "params",                 "cypher version",       "expected result",                              "notifications"),
      ("0",   "`aaa`.`bbb`",  Map.empty,                CypherVersion.Cypher5,  Succeed(),                                      Seq(deprecatedGraphRef("`aaa`.`bbb`", "`aaa.bbb`"))),
      ("1",   "`aaa`.`bbb`",  Map.empty,                CypherVersion.Cypher25, Throw(invalidReferenceSyntax("`aaa`.`bbb`")),   Seq()),
      ("2",   "aaa.bbb",      Map.empty,                CypherVersion.Cypher5,  Succeed(),                                      Seq()),
      ("3",   "aaa.bbb",      Map.empty,                CypherVersion.Cypher25, Succeed(),                                      Seq()),
      ("4",   "`aaa.bbb`",    Map.empty,                CypherVersion.Cypher5,  Succeed(),                                      Seq()),
      ("5",   "`aaa.bbb`",    Map.empty,                CypherVersion.Cypher25, Succeed(),                                      Seq()),
      ("6",   "$p",           Map("p"->"`aaa`.`bbb`"),  CypherVersion.Cypher5,  ReturnEmpty(),                                  Seq()),
      ("7",   "$p",           Map("p"->"`aaa`.`bbb`"),  CypherVersion.Cypher25, ReturnEmpty(),                                  Seq()),
      ("8",   "$p",           Map("p"->"aaa.bbb"),      CypherVersion.Cypher5,  Succeed(),                                      Seq()),
      ("9",   "$p",           Map("p"->"aaa.bbb"),      CypherVersion.Cypher25, Succeed(),                                      Seq()),
      ("10",  "$p",           Map("p"->"`aaa.bbb`"),    CypherVersion.Cypher5,  ReturnEmpty(),                                  Seq()),
      ("11",  "$p",           Map("p"->"`aaa.bbb`"),    CypherVersion.Cypher25, ReturnEmpty(),                                  Seq()),
    )
    // format: on
    forEvery(scenarios) { (_, nameLiteral, params, cypherVersion, expectedResult, notifications) =>
      try {
        // GIVEN
        // Might need to be enabled when the next experimental version appear: config.set(GraphDatabaseSettings.enable_experimental_cypher_versions, TRUE)
        setup(Map(initial_default_database -> "aaa.bbb"))

        val showQuery = s"CYPHER ${cypherVersion.versionName} SHOW DATABASE $nameLiteral YIELD name"

        expectedResult match {
          case Succeed() =>
            // WHEN
            val showRes = execute(showQuery, params)
            // THEN
            showRes.toList should be(List(Map("name" -> "aaa.bbb")))
            showRes.notifications.map(_.getDescription) should be(notifications)
          case ReturnEmpty() =>
            // WHEN
            val showRes = execute(showQuery, params)
            // THEN
            showRes.toList should be(empty)
            showRes.notifications.map(_.getDescription) should be(notifications)
          case Throw(error) =>
            // WHEN ... THEN
            the[Exception] thrownBy execute(showQuery, params) should be(error)
        }

      } finally {
        afterEach()
      }
    }

  }

  test("should show correct default database for switch of default database") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DEFAULT DATABASE")

    // THEN
    result.toSet should be(Set(homeOrDefaultDb(DEFAULT_DATABASE_NAME)))

    // GIVEN
    managementService.shutdown()
    setup(Map(initial_default_database -> "foo"))

    // WHEN
    val result2 = execute("SHOW DEFAULT DATABASE")

    // THEN
    val expectedRow = homeOrDefaultDb("foo")
    result2.toSet should be(Set(expectedRow))
  }

  test("should fail when showing default database when not on system database") {
    // GIVEN
    setup()

    // THEN
    assertFailWhenNotOnSystem("SHOW DEFAULT DATABASE", "SHOW DEFAULT DATABASE")
  }

  test("should show default database as home database when executing as anonymous user") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW HOME DATABASE")

    // THEN
    result.toList should be(List(homeOrDefaultDb(DEFAULT_DATABASE_NAME)))
  }

  // yield / skip / limit / order by / where

  test("should show database with yield") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASE $db YIELD name, address, role", dbDefaultMap)

    // THEN
    result.toList should be(List(Map(
      "name" -> DEFAULT_DATABASE_NAME,
      "address" -> localHostString,
      "role" -> "primary"
    )))
  }

  test("should show database with yield *") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASE $db YIELD *", dbDefaultMap).toList.head

    // THEN
    val db = managementService.database(DEFAULT_DATABASE_NAME).asInstanceOf[GraphDatabaseAPI]
    val format =
      db.getDependencyResolver.resolveDependency(classOf[MetadataProvider]).getStoreId.getStoreVersionUserString

    if (dbmsDefaultQueryLanguage `equals` CypherVersion.Cypher25) {
      result should have size 32
      result should contain.allOf(
        "currentPropertyShardReplicas" -> null,
        "requestedPropertyShardReplicas" -> null,
        "shardTxnLag" -> null,
        "graphShards" -> Seq("neo4j"),
        "propertyShards" -> Seq.empty
      )
    } else {
      result should have size 27
    }
    result should contain.allOf(
      "name" -> DEFAULT_DATABASE_NAME,
      "type" -> "standard",
      "access" -> "read-write",
      "aliases" -> Seq(),
      "address" -> localHostString,
      "role" -> "primary",
      "writer" -> true,
      "requestedStatus" -> "online",
      "currentStatus" -> "online",
      "currentPrimariesCount" -> 1,
      "currentSecondariesCount" -> 0,
      "requestedPrimariesCount" -> null,
      "requestedSecondariesCount" -> null,
      "store" -> format,
      "lastCommittedTxn" -> null,
      "replicationLag" -> 0,
      "constituents" -> Seq.empty,
      "defaultLanguage" -> dbmsDefaultQueryLanguage.description,
      "options" -> Map()
    )
  }

  test("should show database with yield and where") {
    // GIVEN
    setup()

    // WHEN
    val result =
      execute(s"SHOW DATABASE $$db YIELD name, address, role WHERE name = '$DEFAULT_DATABASE_NAME'", dbDefaultMap)

    // THEN
    result.toList should be(List(Map(
      "name" -> DEFAULT_DATABASE_NAME,
      "address" -> localHostString,
      "role" -> "primary"
    )))
  }

  test("should show databases with yield and where") {
    // GIVEN
    setup()

    // WHEN
    val result = execute(s"SHOW DATABASES YIELD name, address, role WHERE name = '$DEFAULT_DATABASE_NAME'")

    // THEN
    result.toList should be(List(Map(
      "name" -> DEFAULT_DATABASE_NAME,
      "address" -> localHostString,
      "role" -> "primary"
    )))
  }

  test("should show databases with yield and skip") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASES YIELD name ORDER BY name SKIP 1")

    // THEN
    result.toList should be(List(nameSystemMap))
  }

  test("should show databases with yield and limit") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASES YIELD name ORDER BY name LIMIT 1")

    // THEN
    result.toList should be(List(nameDefaultMap))
  }

  test("should show databases with yield and order by asc") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASES YIELD name ORDER BY name ASC")

    // THEN
    result.toList should be(List(nameDefaultMap, nameSystemMap))
  }

  test("should show databases with yield and order by desc") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASES YIELD name ORDER BY name DESC")

    // THEN
    result.toList should be(List(nameSystemMap, nameDefaultMap))
  }

  test("should show databases with yield and return") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASES YIELD name RETURN name")

    // THEN
    result.toSet should be(Set(nameSystemMap, nameDefaultMap))
  }

  test("should count default database with yield and return") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DEFAULT DATABASE YIELD name RETURN count(name) as count, name")

    // THEN
    result.toSet should be(Set(Map[String, Any]("count" -> 1, "name" -> DEFAULT_DATABASE_NAME)))
  }

  test("should show databases with yield, return and skip") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASES YIELD * RETURN name ORDER BY name SKIP 1")

    // THEN
    result.toList should be(List(nameSystemMap))
  }

  test("should show databases with yield, return and limit") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASES YIELD * RETURN name ORDER BY name LIMIT 1")

    // THEN
    result.toList should be(List(nameDefaultMap))
  }

  test(s"should show database $DEFAULT_DATABASE_NAME with yield and aliasing") {
    // GIVEN
    setup()

    // WHEN
    val result =
      execute(s"SHOW DATABASE $DEFAULT_DATABASE_NAME YIELD name AS foo WHERE foo = '$DEFAULT_DATABASE_NAME' RETURN foo")

    // THEN
    result.toList should be(List(Map("foo" -> DEFAULT_DATABASE_NAME)))
  }

  test("should show default database with all verbose columns") {
    // GIVEN
    setup()
    selectDatabase(DEFAULT_DATABASE_NAME)
    val dbId = execute(s"CALL db.info() YIELD id").toList.head("id")

    // WHEN
    selectDatabase(SYSTEM_DATABASE_NAME)
    val result = execute(s"SHOW DATABASE $DEFAULT_DATABASE_NAME YIELD *").toList.head

    // THEN
    result should contain allElementsOf homeOrDefaultDb(DEFAULT_DATABASE_NAME)
    result("replicationLag") shouldEqual 0
    result("lastCommittedTxn") shouldEqual null
    result("serverID") should beAValidUUID()
    result("databaseID") shouldEqual dbId
    result("creationTime") shouldBe a[ZonedDateTime]
    (ZonedDateTime.now().toEpochSecond - result("creationTime").asInstanceOf[
      ZonedDateTime
    ].toEpochSecond) should be < 300L
    result("lastStartTime") shouldBe a[ZonedDateTime]
    (ZonedDateTime.now().toEpochSecond - result("lastStartTime").asInstanceOf[
      ZonedDateTime
    ].toEpochSecond) should be < 300L
    result("lastStopTime") shouldEqual null
    result("currentPrimariesCount") shouldEqual 1
    result("currentSecondariesCount") shouldEqual 0
    result("requestedPrimariesCount") shouldEqual null
    result("requestedSecondariesCount") shouldEqual null
  }

  test("should show database with yield verbose columns should produce verbose but not polled columns") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASES YIELD name, databaseID, serverID")

    // THEN
    result.toList.foreach { map =>
      map should have size 3
      map.get("name") should contain.oneOf("neo4j", "system")

      // Lookup the real store id from db.info()
      selectDatabase(map("name").asInstanceOf[String])
      val dbId = execute(s"CALL db.info() YIELD id").toList.head("id")

      map.get("databaseID").value shouldBe dbId
      map.get("serverID").value should beAValidUUID()
    }
  }

  test("should show database and yield only verbose columns") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW DATABASES YIELD lastCommittedTxn, serverID").toList

    // THEN
    result.foreach { map =>
      map should have size 2
      map.get("serverID").value should beAValidUUID()
      map.get("lastCommittedTxn").value shouldBe null
    }
  }

  test("should show default database with yield and return with aliasing") {
    // GIVEN
    setup()

    // WHEN
    val result = execute(s"SHOW DEFAULT DATABASE YIELD name WHERE name = '$DEFAULT_DATABASE_NAME' RETURN name as foo")

    // THEN
    result.toList should be(List(Map("foo" -> DEFAULT_DATABASE_NAME)))
  }

  test("should show default database as home database with YIELD") {
    // GIVEN
    setup()

    // WHEN
    val result = execute("SHOW HOME DATABASE YIELD name")

    // THEN
    result.toList should be(List(Map("name" -> DEFAULT_DATABASE_NAME)))
  }

  test("should not show database with invalid yield") {
    // GIVEN
    setup()

    // WHEN
    val exception = the[SyntaxException] thrownBy {
      execute("SHOW DATABASE $db YIELD foo, bar, baz", dbDefaultMap)
    }

    // THEN
    exception.getMessage should startWith("Trying to YIELD non-existing column: `foo`")
    exception.getMessage should include("(line 1, column 25 (offset: 24))")

    // WHEN
    val exceptionCypher5 = the[SyntaxException] thrownBy {
      execute("CYPHER 5 SHOW DATABASE $db YIELD foo, bar, baz", dbDefaultMap)
    }

    // THEN
    exceptionCypher5.getMessage should startWith("Trying to YIELD non-existing column: `foo`")
    exceptionCypher5.getMessage should include("(line 1, column 34 (offset: 33))")
  }

  test("should not show database with invalid where") {
    // GIVEN
    setup()

    // WHEN
    val exception = the[SyntaxException] thrownBy {
      execute("SHOW DATABASE $db WHERE foo = 'bar'", dbDefaultMap)
    }

    // THEN
    exception.getMessage should startWith("Variable `foo` not defined")
    exception.getMessage should include("(line 1, column 25 (offset: 24))")
  }

  test("should not show database with yield and invalid where") {
    // GIVEN
    setup()

    // WHEN
    val exception = the[SyntaxException] thrownBy {
      execute("SHOW DATABASE $db YIELD name, address, role WHERE foo = 'bar'", dbDefaultMap)
    }

    // THEN
    exception.getMessage should startWith("Variable `foo` not defined")
    exception.getMessage should include("(line 1, column 51 (offset: 50))")
  }

  test("should not show databases with yield and invalid skip") {
    // GIVEN
    setup()

    // WHEN
    val exception = the[SyntaxException] thrownBy {
      execute("SHOW DATABASES YIELD name ORDER BY name SKIP -1")
    }

    // THEN
    exception.getMessage should startWith("Invalid input. '-1' is not a valid value. Must be a non-negative integer")
    exception.getMessage should include("(line 1, column 46 (offset: 45))")
  }

  test("should not show databases with yield and invalid limit") {
    // GIVEN
    setup()

    // WHEN
    val exception = the[SyntaxException] thrownBy {
      execute("SHOW DATABASES YIELD name ORDER BY name LIMIT -1")
    }

    // THEN
    exception.getMessage should startWith("Invalid input. '-1' is not a valid value. Must be a non-negative integer")
    exception.getMessage should include("(line 1, column 47 (offset: 46))")
  }

  test("should not show default database with invalid order by") {
    // GIVEN
    setup()

    // WHEN
    val exceptionBar = the[SyntaxException] thrownBy {
      execute("SHOW DEFAULT DATABASE YIELD name ORDER BY bar")
    }

    // THEN
    exceptionBar.getMessage should startWith("Variable `bar` not defined")
    exceptionBar.getMessage should include("(line 1, column 43 (offset: 42))")

    // WHEN
    val exceptionRole = the[SyntaxException] thrownBy {
      // 'role' is a valid column but not yielded
      execute("SHOW DEFAULT DATABASE YIELD name ORDER BY role")
    }

    // THEN
    exceptionRole.getMessage should startWith("Variable `role` not defined")
    exceptionRole.getMessage should include("(line 1, column 43 (offset: 42))")
  }

  // Helper methods

  private def db(
    name: String,
    dbType: String = typeString,
    home: Boolean = false,
    default: Boolean = false
  ): Map[String, Any] =
    Map(
      "name" -> name,
      "type" -> dbType,
      "aliases" -> Seq.empty,
      "access" -> accessString,
      "address" -> localHostString,
      "role" -> "primary",
      "writer" -> true,
      "requestedStatus" -> onlineStatus,
      "currentStatus" -> onlineStatus,
      "statusMessage" -> "",
      "default" -> default,
      "home" -> home,
      "constituents" -> List()
    )

  private def homeOrDefaultDb(name: String): Map[String, Any] =
    Map(
      "name" -> name,
      "type" -> typeString,
      "aliases" -> Seq.empty,
      "access" -> accessString,
      "address" -> localHostString,
      "role" -> "primary",
      "writer" -> true,
      "requestedStatus" -> onlineStatus,
      "currentStatus" -> onlineStatus,
      "statusMessage" -> "",
      "constituents" -> List()
    )

  // Disable normal database creation because we need different settings on each test
  override protected def beforeEach(): Unit = {
    resetLogs() // Don't keep the cumulative logs in memory to avoid OOM
  }

  protected def setup(config: Map[Setting[?], Object] = Map.empty): Unit = {
    managementService = graphDatabaseFactory(Path.of("test")).impermanent().setConfig(
      (databaseConfig() ++ config).asJava
    ).setInternalLogProvider(logProvider).build()
    graphOps = managementService.database(SYSTEM_DATABASE_NAME)
    graph = new GraphDatabaseCypherService(graphOps)
  }
}
