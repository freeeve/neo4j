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
package org.neo4j.cypher.internal

import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.scalatest.Assertions.withClue
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import scala.util.Random

object CypherVersionHelpers {

  def randomVersion(): CypherVersion = {
    val values = CypherVersion.values()
    values(Random.nextInt(values.length))
  }

  def equalInVersions[T](versions: CypherVersion*)(f: CypherVersion => T): T = {
    val baselineVersion = versions(Random.nextInt(versions.size))
    versions.foldLeft(f(baselineVersion)) {
      case (baseline, `baselineVersion`) => baseline
      case (baseline, version) =>
        val result = f(version)
        withClue(
          s"""Expected the same value but got:
             |CYPHER $baselineVersion: $baseline
             |CYPHER $version: $result
             |""".stripMargin
        )(result shouldBe baseline)
        baseline
    }
  }

  def equalInAllVersions[T](f: CypherVersion => T): T = equalInVersions(CypherVersion.values(): _*)(f)
}

trait CypherVersionTestSupport {
  self: CypherFunSuite =>

  def testVersions(testName: String)(f: CypherVersion => Any)(implicit pos: org.scalactic.source.Position): Unit =
    test(testName) {
      CypherVersion.values().foreach(v => withClue(s"CYPHER $v\n")(f(v)))
    }

  def testVersionsExcept5(testName: String)(f: CypherVersion => Any)(implicit
    pos: org.scalactic.source.Position): Unit =
    test(testName) {
      CypherVersion.values().filter(version => version != CypherVersion.Cypher5).foreach(v =>
        withClue(s"CYPHER $v\n")(f(v))
      )
    }

  def versionsExcept5(f: CypherVersion => Any): Unit =
    CypherVersion.values().filter(version => version != CypherVersion.Cypher5).foreach(v =>
      withClue(s"CYPHER $v\n")(f(v))
    )
}
