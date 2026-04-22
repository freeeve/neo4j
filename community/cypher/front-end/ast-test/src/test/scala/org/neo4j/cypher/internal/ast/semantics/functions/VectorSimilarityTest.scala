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
package org.neo4j.cypher.internal.ast.semantics.functions

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.util.symbols.CTBoolean
import org.neo4j.cypher.internal.util.symbols.CTFloat
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.CTList
import org.neo4j.cypher.internal.util.symbols.CTMap
import org.neo4j.cypher.internal.util.symbols.CTNumber
import org.neo4j.cypher.internal.util.symbols.CTPoint
import org.neo4j.cypher.internal.util.symbols.CTString
import org.neo4j.cypher.internal.util.symbols.invariantTypeSpec

abstract class VectorSimilarityTest(functionName: String) extends FunctionTestBase(s"vector.similarity.$functionName") {

  test("should accept correct types") {
    testValidTypesInVersion(CTList(CTFloat), CTList(CTFloat)) {
      case CypherVersion.Cypher5 => CTFloat
      case _                     => CTFloat
    }
    testValidTypesInVersion(CTList(CTFloat), CTList(CTInteger)) {
      case CypherVersion.Cypher5 => CTFloat
      case _                     => CTFloat
    }
    testValidTypesInVersion(CTList(CTInteger), CTList(CTFloat)) {
      case CypherVersion.Cypher5 => CTFloat
      case _                     => CTFloat
    }
    testValidTypesInVersion(CTList(CTInteger), CTList(CTInteger)) {
      case CypherVersion.Cypher5 => CTFloat
      case _                     => CTFloat
    }
  }

  test("should fail if wrong types") {
    testInvalidApplicationInVersion(CTList(CTNumber), CTList(CTString)) {
      case CypherVersion.Cypher5 =>
        "Type mismatch: expected List<Float>, List<Integer> or List<Number> but was List<String>"
      case _ => "Type mismatch: expected Vector, List<Float>, List<Integer> or List<Number> but was List<String>"
    }
    testInvalidApplicationInVersion(CTList(CTInteger), CTList(CTBoolean)) {
      case CypherVersion.Cypher5 =>
        "Type mismatch: expected List<Float>, List<Integer> or List<Number> but was List<Boolean>"
      case _ => "Type mismatch: expected Vector, List<Float>, List<Integer> or List<Number> but was List<Boolean>"
    }
    testInvalidApplicationInVersion(CTList(CTFloat), CTPoint) {
      case CypherVersion.Cypher5 => "Type mismatch: expected List<Float>, List<Integer> or List<Number> but was Point"
      case _ => "Type mismatch: expected Vector, List<Float>, List<Integer> or List<Number> but was Point"
    }
    testInvalidApplicationInVersion(CTBoolean, CTInteger) {
      case CypherVersion.Cypher5 => "Type mismatch: expected List<Float>, List<Integer> or List<Number> but was Boolean"
      case _ => "Type mismatch: expected Vector, List<Float>, List<Integer> or List<Number> but was Boolean"
    }
    testInvalidApplicationInVersion(CTList(CTFloat), CTList(CTString)) {
      case CypherVersion.Cypher5 =>
        "Type mismatch: expected List<Float>, List<Integer> or List<Number> but was List<String>"
      case _ => "Type mismatch: expected Vector, List<Float>, List<Integer> or List<Number> but was List<String>"
    }
  }

  test("should fail if wrong number of arguments") {
    testInvalidApplication()(
      s"Insufficient parameters for function 'vector.similarity.$functionName'"
    )
    testInvalidApplication(CTMap)(
      s"Insufficient parameters for function 'vector.similarity.$functionName'"
    )
    testInvalidApplication(CTList(CTFloat), CTList(CTFloat), CTList(CTFloat))(
      s"Too many parameters for function 'vector.similarity.$functionName'"
    )
  }
}

class VectorSimilarityEuclideanTest extends VectorSimilarityTest("euclidean")
class VectorSimilarityCosineTest extends VectorSimilarityTest("cosine")
