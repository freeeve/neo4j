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

import org.neo4j.configuration.GraphDatabaseInternalSettings.attribute_based_access_control
import org.neo4j.configuration.GraphDatabaseSettings.CypherVersion
import org.neo4j.configuration.GraphDatabaseSettings.default_language
import org.neo4j.graphdb.config.Setting

class CommunityAuthRuleAdministrationCommandAcceptanceTest extends CommunityAdministrationCommandAcceptanceTestBase {

  override def databaseConfig(): Map[Setting[_], Object] =
    super.databaseConfig() ++ Map(
      attribute_based_access_control -> java.lang.Boolean.TRUE,
      default_language -> CypherVersion.Cypher25
    )

  test("should fail on showing auth rules from community") {
    assertFailure("SHOW AUTH RULES", "Unsupported administration command: SHOW AUTH RULES")
    assertFailure(
      "SHOW AUTH RULES AS COMMANDS",
      "Unsupported administration command: SHOW AUTH RULES AS COMMANDS"
    )
  }

  test("should fail on creating auth rule from community") {
    assertFailure(
      "CREATE AUTH RULE foo SET CONDITION 1=1",
      "Unsupported administration command: CREATE AUTH RULE foo SET CONDITION 1=1"
    )
    assertFailure(
      "CREATE AUTH RULE $foo SET CONDITION 1=1",
      "Unsupported administration command: CREATE AUTH RULE $foo SET CONDITION 1=1"
    )
    assertFailure(
      "CREATE AUTH RULE foo IF NOT EXISTS SET CONDITION 1=1",
      "Unsupported administration command: CREATE AUTH RULE foo IF NOT EXISTS SET CONDITION 1=1"
    )
  }

  test("should fail on dropping auth rule from community") {
    assertFailure("DROP AUTH RULE foo", "Unsupported administration command: DROP AUTH RULE foo")
    assertFailure("DROP AUTH RULE $foo", "Unsupported administration command: DROP AUTH RULE $foo")
    assertFailure("DROP AUTH RULE foo IF EXISTS", "Unsupported administration command: DROP AUTH RULE foo IF EXISTS")
  }

  test("should fail on granting role to auth rule from community") {
    assertFailure(
      "GRANT ROLE reader TO AUTH RULE rule",
      "Unsupported administration command: GRANT ROLE reader TO AUTH RULE rule"
    )
    assertFailure("GRANT ROLE $r TO AUTH RULE $u", "Unsupported administration command: GRANT ROLE $r TO AUTH RULE $u")
  }

  test("should fail on revoking role from auth rule from community") {
    assertFailure(
      "REVOKE ROLE custom FROM AUTH RULE rule",
      "Unsupported administration command: REVOKE ROLE custom FROM AUTH RULE rule"
    )
    assertFailure(
      "REVOKE ROLE $r FROM AUTH RULE $u",
      "Unsupported administration command: REVOKE ROLE $r FROM AUTH RULE $u"
    )
  }
}
