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
package org.neo4j.cypher.internal.frontend.scoping

import org.neo4j.cypher.internal.CypherVersion

sealed trait Outcome

case class Versioned(default: Outcome, map: (CypherVersion, Outcome)*) extends Outcome

object Versioned {
  def ignoreBeforeCypher25(outcome: Outcome): Outcome = Versioned(outcome, CypherVersion.Cypher5 -> Ignore)
  def passesBeforeCypher25(outcome: Outcome): Outcome = Versioned(outcome, CypherVersion.Cypher5 -> Passes)

  def passesCypher25Onwards(beforeCypher25: Outcome): Outcome =
    Versioned(Passes, CypherVersion.Cypher5 -> beforeCypher25)
}

sealed trait Unversioned extends Outcome

case object Ignore extends Unversioned

case object Passes extends Unversioned

trait GqlError extends Unversioned {
  val num: String
  val msg: String
}

case class E42N07(variable: String) extends GqlError {
  override val num: String = "42N07"

  override val msg: String =
    s"The variable `$variable` is shadowing a variable with the same name from the outer scope and needs to be renamed."
}

case class E42N29(variable: String) extends GqlError {
  override val num: String = "42N29"

  override val msg: String = s"Pattern expressions are not allowed to introduce new variables: `$variable`."
}

case object E42N38 extends GqlError {
  override val num: String = "42N38"

  override val msg: String = "Return items must have unique names."
}

case object E42N39 extends GqlError {
  override val num: String = "42N39"

  override val msg: String = "incompatible return column names."
}

case object E42N66 extends GqlError {
  override val num: String = "42N66"

  override val msg: String = "relationship variable already bound"
}

case object E42N3A extends GqlError {
  override val num: String = "42N3A"

  override val msg: String = "incompatible conditional query."
}

case object E42N3B extends GqlError {
  override val num: String = "42N3B"

  override val msg: String = "incompatible number of return columns."
}

case class E42N44(variable: String, clause: String) extends GqlError {
  override val num: String = "42N44"

  override val msg: String =
    s"It is not possible to access the variable `$variable` declared before the $clause clause when using `DISTINCT` or an aggregation."
}

case class E42N59(variable: String) extends GqlError {
  override val num: String = "42N59"

  override val msg: String = s"Variable `$variable` already declared."
}

case class E42N62(variable: String) extends GqlError {
  override val num: String = "42N62"

  override val msg: String = s"Variable `$variable` not defined."
}

case class E42I18(variables: String*) extends GqlError {
  override val num: String = "42I18"

  override val msg: String =
    s"The aggregation column contains implicit grouping expressions referenced by the variables ${variables.map(v =>
        s"`$v`"
      ).mkString(", ")}."
}

case object E42I37 extends GqlError {
  override val num: String = "42I37"

  override val msg: String = "'RETURN *' is not allowed when there are no variables in scope."
}

case class E42I58(variable: String) extends GqlError {
  override val num: String = "42I58"

  override val msg: String =
    s"Entity, '$variable', cannot be created and referenced in the same clause."
}
