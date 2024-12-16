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
package cypher.features

import org.neo4j.cypher.internal.CypherVersion

case class QueryPrefix(commonPrefix: String, executionPrefix: String)

object QueryPrefix {
  def empty: QueryPrefix = QueryPrefix("", "")
}
case class TestConfig(denylist: Option[String], executionPrefix: QueryPrefix, experimental: Boolean)

object TestConfig {

  def apply(
    testClass: Class[_],
    denyListFilename: String,
    prefix: QueryPrefix,
    experimental: Boolean = false
  ): TestConfig =
    TestConfig(Some(testClass.getPackageName.replace('.', '/') + "/denylist/" + denyListFilename), prefix, experimental)

  def default(testClass: Class[_], deny: String = denyFile("default")): TestConfig =
    TestConfig(testClass, deny, QueryPrefix.empty)

  def slotted(testClass: Class[_], denyList: String = denyFile("slotted")): TestConfig =
    TestConfig(testClass, denyList, QueryPrefix("", "CYPHER planner=cost runtime=slotted"))

  def slottedWithCompiledExpressions(testClass: Class[_]): TestConfig = TestConfig(
    testClass,
    denyFile("slotted-with-compiled-expressions"),
    QueryPrefix("", "CYPHER planner=cost runtime=slotted expressionEngine=COMPILED")
  )

  def pipelined(testClass: Class[_]): TestConfig =
    TestConfig(
      testClass,
      denyFile("pipelined-single-threaded"),
      QueryPrefix("", "CYPHER planner=cost runtime=pipelined")
    )

  def pipelinedFull(testClass: Class[_]): TestConfig = TestConfig(
    testClass,
    denyFile("pipelined-single-threaded-full"),
    QueryPrefix("", "CYPHER planner=cost runtime=pipelined interpretedPipesFallback=all"),
    experimental = true
  )

  def parallel(testClass: Class[_]): TestConfig =
    TestConfig(testClass, denyFile("parallel"), QueryPrefix("", "CYPHER planner=cost runtime=parallel"))

  def interpreted(testClass: Class[_]): TestConfig =
    TestConfig(testClass, denyFile("interpreted"), QueryPrefix("", "CYPHER planner=cost runtime=legacy"))

  def defaultSpd(testClass: Class[_]): TestConfig = TestConfig(testClass, denyFile("default-spd"), QueryPrefix.empty)

  private def denyFile(name: String): String = s"$name-cypher${CypherVersion.Default.versionName}.txt"
}
