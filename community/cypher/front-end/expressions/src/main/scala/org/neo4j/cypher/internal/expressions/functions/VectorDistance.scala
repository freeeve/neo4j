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
import org.neo4j.cypher.internal.util.symbols.CTFloat
import org.neo4j.cypher.internal.util.symbols.CTString
import org.neo4j.cypher.internal.util.symbols.CTVector

case object VectorDistance extends Function {
  override def name = "vector_distance"

  override val signatures: Vector[FunctionTypeSignature] = Vector(
    FunctionTypeSignature(
      this,
      names = Vector("vector1", "vector2", "vectorDistanceMetric"),
      argumentTypes =
        Vector(CTVector, CTVector, CTString),
      outputType = CTFloat,
      description =
        "Returns a `FLOAT` representing the distance between the two vector values based on the selected `vectorDistanceMetric` algorithm.",
      overrideDefaultAsString = Some(
        name + "(vector1 :: VECTOR, vector2 :: VECTOR, coordinateType :: [EUCLIDEAN, EUCLIDEAN_SQUARED, MANHATTAN, COSINE, DOT, HAMMING]) :: FLOAT"
      ),
      overriddenArgumentTypeName =
        Some(Map("vectorDistanceMetric" -> "[EUCLIDEAN, EUCLIDEAN_SQUARED, MANHATTAN, COSINE, DOT, HAMMING]")),
      category = Category.VECTOR,
      argumentDescriptions = Map(
        "vector1" -> "The first vector.",
        "vector2" -> "The second vector.",
        "vectorDistanceMetric" -> "The vector distance algorithm to calculate the distance by."
      ),
      scopes = Set(CypherVersion.Cypher25)
    )
  )
}
