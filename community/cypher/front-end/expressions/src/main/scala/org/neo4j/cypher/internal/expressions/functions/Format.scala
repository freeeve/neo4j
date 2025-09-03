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
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.CTDate
import org.neo4j.cypher.internal.util.symbols.CTDateTime
import org.neo4j.cypher.internal.util.symbols.CTDuration
import org.neo4j.cypher.internal.util.symbols.CTLocalDateTime
import org.neo4j.cypher.internal.util.symbols.CTLocalTime
import org.neo4j.cypher.internal.util.symbols.CTString
import org.neo4j.cypher.internal.util.symbols.CTTime
import org.neo4j.cypher.internal.util.symbols.ClosedDynamicUnionType

case object Format extends Function {
  def name = "format"

  override val signatures = Vector(
    FunctionTypeSignature(
      function = this,
      names = Vector("value"),
      argumentTypes = Vector(
        ClosedDynamicUnionType(Set(
          CTDateTime,
          CTLocalDateTime,
          CTDate,
          CTTime,
          CTLocalTime,
          CTDuration
        ))(InputPosition.NONE)
      ),
      outputType = CTString,
      description =
        "Returns the temporal value as an ISO-formatted string or as a string formatted by the provided pattern.",
      category = Category.TEMPORAL,
      argumentDescriptions =
        Map("value" -> "A temporal value to be formatted."),
      scopes = Set(CypherVersion.Cypher25)
    ),
    FunctionTypeSignature(
      function = this,
      names = Vector("value", "pattern"),
      argumentTypes = Vector(
        ClosedDynamicUnionType(Set(
          CTDateTime,
          CTLocalDateTime,
          CTDate,
          CTTime,
          CTLocalTime,
          CTDuration
        ))(InputPosition.NONE),
        CTString
      ),
      outputType = CTString,
      description =
        "Returns the temporal value as an ISO-formatted string or as a string formatted by the provided pattern.",
      category = Category.TEMPORAL,
      argumentDescriptions =
        Map(
          "value" -> "A temporal value to be formatted.",
          "pattern" -> "A pattern used to format the temporal value. If the pattern is not provided the value will be formatted according to ISO 8601."
        ),
      scopes = Set(CypherVersion.Cypher25)
    )
  )
}
