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
package org.neo4j.cypher.internal.expressions.functions

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.expressions.FunctionTypeSignature
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.symbols.CTLocalTime
import org.neo4j.cypher.internal.util.symbols.CTString

/**
 * Note: This is a GQL alias to the `localtime` function.
 */
case object LocalTime extends Function {
  def name = "local_time"

  override val signatures: Vector[FunctionTypeSignature] = Vector(
    FunctionTypeSignature(
      function = this,
      names = Vector("input"),
      argumentTypes = Vector(CTAny),
      outputType = CTLocalTime,
      description = "Creates a `LOCAL TIME` instant.",
      category = Category.TEMPORAL,
      overrideDefaultAsString = Some("local_time(input = DEFAULT_TEMPORAL_ARGUMENT :: ANY) :: LOCAL TIME"),
      argumentDescriptions = Map(
        "input" ->
          "Either a string representation of a temporal value, a map containing the single key 'timezone', or a map containing temporal values ('hour, 'minute', 'second', 'millisecond', 'microsecond', 'nanosecond' as components."
      ),
      scopes = Set(CypherVersion.Cypher25)
    ),
    FunctionTypeSignature(
      function = this,
      names = Vector("input", "pattern"),
      argumentTypes = Vector(CTAny, CTString),
      outputType = CTLocalTime,
      description = "Creates a `LOCAL TIME` instant.",
      category = Category.TEMPORAL,
      overrideDefaultAsString = Some(
        "local_time(input = DEFAULT_TEMPORAL_ARGUMENT :: ANY, pattern = DEFAULT_TEMPORAL_ARGUMENT :: STRING) :: LOCAL TIME"
      ),
      argumentDescriptions = Map(
        "input" ->
          "Either a string representation of a temporal value, a map containing the single key 'timezone', or a map containing temporal values ('hour, 'minute', 'second', 'millisecond', 'microsecond', 'nanosecond' as components.",
        "pattern" ->
          "A pattern used to parse a local time value. If the pattern is not provided the value will be parsed according to default patterns."
      ),
      scopes = Set(CypherVersion.Cypher25)
    )
  )
}
