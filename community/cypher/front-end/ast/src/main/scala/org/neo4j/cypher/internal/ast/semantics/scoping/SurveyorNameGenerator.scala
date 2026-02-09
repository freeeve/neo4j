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
package org.neo4j.cypher.internal.ast.semantics.scoping

import org.neo4j.cypher.internal.ast.semantics.scoping.SurveyorNameGenerator.prefix
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator

case class SurveyorNameGenerator() extends AnonymousVariableNameGenerator {
  private var counter = 0
  private val inc = 1

  def anonymousVarName(counter: Int) =
    s"$prefix$counter"

  override def nextName: String = {
    val result = anonymousVarName(counter)
    counter += inc
    result
  }
}

object SurveyorNameGenerator {
  val generatorName = "SURVEYOR"
  private val prefix = s"  $generatorName"

  def anonymousVarName(counter: Int) =
    s"$prefix$counter"

  def named(x: String): Boolean = !s""" {2}($generatorName)(-?\\d+)""".r.matches(x)
}
