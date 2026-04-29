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
import org.neo4j.cypher.internal.util.symbols.CTString

case object StringRegexReplace extends Function {
  def name = "string.regexReplace"

  override val signatures = Vector(
    FunctionTypeSignature(
      function = this,
      names = Vector("original", "regex", "replacement"),
      argumentTypes = Vector(CTString, CTString, CTString),
      outputType = CTString,
      description =
        "Returns a `STRING` where all matches of `regex` in the `original` `STRING` are replaced with the `replacement` `STRING`.",
      category = Category.STRING,
      argumentDescriptions = Map(
        "original" -> "The string to be modified.",
        "regex" -> "The regex pattern to replace in the original `STRING`.",
        "replacement" -> "The value to be inserted in the original string."
      ),
      scopes = Set(CypherVersion.Cypher25)
    )
  )
}
