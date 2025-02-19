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
package org.neo4j.cypher.internal.frontend

import org.neo4j.cypher.internal.ast.Ast.p
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.VectorType
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.gqlstatus.GqlHelper

class VectorValueSemanticAnalysisTest extends CypherFunSuite with NameBasedSemanticAnalysisTestSuite {

  test("RETURN VECTOR([1, 2, 3])") {
    // running without semantic feature should fail
    run().hasError(
      GqlHelper.getGql42001_51N26("The vector value constructor", "Vector types", 7, 1, 8),
      "The vector type is not supported.",
      p(7, 1, 8)
    )
  }

  // Function resolution is done after semantic analysis, this will fail in Cypher 5 as non-existent
  test("RETURN VECTOR([1, 2, 3, 4])") {
    runWith(VectorType).hasNoErrors
  }

}
