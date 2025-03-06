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
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.FunctionTypeSignature
import org.neo4j.cypher.internal.expressions.StringLiteral
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.CTList
import org.neo4j.cypher.internal.util.symbols.CTNumber
import org.neo4j.cypher.internal.util.symbols.CTString
import org.neo4j.cypher.internal.util.symbols.CTVector
import org.neo4j.cypher.internal.util.symbols.ClosedDynamicUnionType

import java.util.Locale

// Remove internal annotation when the feature flag is removed.
case object VectorValueConstructor extends Function {
  override def name = "vector"

  sealed trait VectorElementType
  case object Int8VectorElementType extends VectorElementType
  case object Int16VectorElementType extends VectorElementType
  case object Int32VectorElementType extends VectorElementType
  case object Int64VectorElementType extends VectorElementType
  case object Float32VectorElementType extends VectorElementType
  case object Float64VectorElementType extends VectorElementType

  def vectorElementType(invocation: FunctionInvocation): VectorElementType = {
    if (!invocation.name.equalsIgnoreCase(name) || invocation.arguments.size != 3) {
      throw new IllegalStateException(s"$invocation does not have an element type")
    }
    invocation.arguments(2) match {
      case s: StringLiteral => s.value.toUpperCase(Locale.ROOT) match {
          case "INTEGER8" | "INT8"                              => Int8VectorElementType
          case "INTEGER16" | "INT16"                            => Int16VectorElementType
          case "INTEGER32" | "INT32"                            => Int32VectorElementType
          case "INTEGER64" | "INT64" | "INT" | "SIGNED INTEGER" => Int64VectorElementType
          case "FLOAT32"                                        => Float32VectorElementType
          case "FLOAT64" | "FLOAT"                              => Float64VectorElementType
          case n => throw new IllegalStateException(
              s"$name is not a valid vector type and should have failed in semantic checking"
            )
        }
      case n =>
        throw new IllegalStateException(s"$n is not a valid vector type and should have failed in semantic checking")
    }
  }

  override val signatures = Vector(
    FunctionTypeSignature(
      this,
      names = Vector("vectorValue", "dimension", "coordinateType"),
      argumentTypes =
        Vector(ClosedDynamicUnionType(Set(CTList(CTNumber), CTString))(InputPosition.NONE), CTInteger, CTString),
      outputType = CTVector,
      description =
        "Converts a `STRING` or `LIST<INTEGER | FLOAT>` to a `VECTOR`.",
      overrideDefaultAsString = Some(
        name + "(vectorValue :: STRING | LIST<INTEGER | FLOAT>, dimension :: INTEGER, coordinateType :: [INTEGER64, INTEGER32, INTEGER16, INTEGER8, FLOAT64, FLOAT32]) :: VECTOR"
      ),
      overriddenArgumentTypeName =
        Some(Map("coordinateType" -> "[INTEGER64, INTEGER32, INTEGER16, INTEGER8, FLOAT64, FLOAT32]")),
      category = Category.SCALAR,
      argumentDescriptions = Map(
        "vectorValue" -> "A value to convert to a `VECTOR`.",
        "dimension" -> "The dimension of the resulting `VECTOR`.",
        "coordinateType" -> "The inner type of the resulting `VECTOR`, one of [`INTEGER64`, `INTEGER32`, `INTEGER16`, `INTEGER8`, `FLOAT64`, `FLOAT32`]."
      ),
      scopes = Set(CypherVersion.Cypher25),
      internal = true
    ),
    FunctionTypeSignature(
      this,
      names = Vector("vectorValue", "dimension"),
      argumentTypes = Vector(ClosedDynamicUnionType(Set(CTList(CTNumber), CTString))(InputPosition.NONE), CTInteger),
      outputType = CTVector,
      description =
        "Converts a `STRING` or `LIST<INTEGER | FLOAT>` to a `VECTOR`.",
      category = Category.SCALAR,
      argumentDescriptions = Map(
        "vectorValue" -> "A value to convert to a `VECTOR`.",
        "dimension" -> "The dimension of the resulting `VECTOR`."
      ),
      scopes = Set(CypherVersion.Cypher25),
      internal = true
    ),
    FunctionTypeSignature(
      this,
      names = Vector("vectorValue"),
      argumentTypes = Vector(ClosedDynamicUnionType(Set(CTList(CTNumber), CTString))(InputPosition.NONE)),
      outputType = CTVector,
      description =
        "Converts a `STRING` or `LIST<INTEGER | FLOAT>` to a `VECTOR`.",
      category = Category.SCALAR,
      argumentDescriptions = Map("vectorValue" -> "A value to convert to a `VECTOR`."),
      scopes = Set(CypherVersion.Cypher25),
      internal = true
    )
  )
}
