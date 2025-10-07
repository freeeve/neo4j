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
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.CTList
import org.neo4j.cypher.internal.util.symbols.CTVector
import org.neo4j.cypher.internal.util.symbols.ClosedDynamicUnionType

case object ToIntegerList extends Function {
  override def name = "toIntegerList"

  override val signatures = Vector(
    FunctionTypeSignature(
      this,
      names = Vector("input"),
      argumentTypes = Vector(CTList(CTAny)),
      outputType = CTList(CTInteger),
      description =
        "Converts a `LIST<ANY>` to a `LIST<INTEGER>` values. If any values are not convertible to `INTEGER` they will be null in the `LIST<INTEGER>` returned.",
      category = Category.LIST,
      argumentDescriptions = Map("input" -> "A list of values to be converted into a list of integers.")
    ),
    FunctionTypeSignature(
      this,
      names = Vector("input"),
      argumentTypes = Vector(ClosedDynamicUnionType(Set(CTList(CTAny), CTVector))(InputPosition.NONE)),
      outputType = CTList(CTInteger),
      description =
        "Converts a `LIST<ANY> | VECTOR` to a `LIST<INTEGER>` values. If any values are not convertible to `INTEGER` they will be null in the `LIST<INTEGER>` returned.",
      category = Category.LIST,
      argumentDescriptions = Map("input" -> "A list of values or vector to be converted into a list of integers."),
      scopes = Set(CypherVersion.Cypher25),
      semanticFeature = Set("VectorType") // Consider to scope the signature above to Cypher 5 when this is removed
    )
  )
}
