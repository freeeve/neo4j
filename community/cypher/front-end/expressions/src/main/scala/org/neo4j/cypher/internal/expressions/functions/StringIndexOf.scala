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
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.CTString

case object StringIndexOf extends Function {
  def name = "string.indexOf"

  override val signatures = Vector(
    FunctionTypeSignature(
      function = this,
      names = Vector("input", "value"),
      argumentTypes = Vector(CTString, CTString),
      outputType = CTInteger,
      description =
        "Returns the index of the first occurrence of the given `value` within the `input` `STRING`, or -1 if no match is found.",
      category = Category.STRING,
      argumentDescriptions = Map("input" -> "The string to be searched.", "value" -> "A value to search for."),
      scopes = Set(CypherVersion.Cypher25)
    )
  )
}
