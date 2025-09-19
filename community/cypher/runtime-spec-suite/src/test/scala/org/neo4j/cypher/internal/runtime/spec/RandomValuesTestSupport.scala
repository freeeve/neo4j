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
package org.neo4j.cypher.internal.runtime.spec

import org.neo4j.cypher.internal.RuntimeContext
import org.neo4j.values.storable.RandomValues
import org.neo4j.values.storable.RandomValuesUtils
import org.neo4j.values.storable.Value
import org.neo4j.values.storable.ValueType
import org.scalatest.Outcome
import org.scalatest.TestSuite
import org.scalatest.TestSuiteMixin

import scala.util.Random

trait RandomValuesTestSupport[CONTEXT <: RuntimeContext] extends TestSuiteMixin with TestSuite {
  self: RuntimeTestSuite[CONTEXT] =>

  private val initialSeed = Random.nextLong()

  // Only initialize the test fixtures that are used.
  lazy val random = new Random(initialSeed)

  // Not all tests run with a graphDb available. For these tests, just assume that all values are permitted
  lazy val randomValues: RandomValues =
    RandomValues.create(
      new java.util.Random(initialSeed),
      Option(graphDb).map(
        RandomValuesUtils.selectStorageEngineDependentConfigurationBuilder
      ).getOrElse(RandomValues.newConfigurationBuilder)
        .maxCodePoint(10_000)
        .build
    )

  // Scala compat
  def randomValue(valueType: ValueType): Value = randomValues.nextValueOfType(valueType)

  def randomValues(size: Int, valueTypes: ValueType*): Array[Value] =
    randomValues.nextValuesOfTypes(size, valueTypes: _*)
  def randomAmong[T](values: Seq[T]): T = values(randomValues.nextInt(values.size))
  def shuffle[T](values: Seq[T]): Seq[T] = random.shuffle(values)

  abstract override def withFixture(test: NoArgTest): Outcome = {
    withClue(
      s"""
         |${classOf[RandomValuesTestSupport[CONTEXT]].getSimpleName} test failed with initial seed:
         |private val initialSeed = ${initialSeed}L
         |
         |""".stripMargin
    ) {
      super.withFixture(test)
    }
  }
}
