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

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class AllReducePredicateSemanticAnalysisTest extends CypherFunSuite with SemanticAnalysisTestSuite {

  test("allReduce() not available without semantic feature") {
    run("RETURN allReduce(acc = 0, acc + 1, acc <= 5) AS result")
      .hasErrorMessagesIn {
        case CypherVersion.Cypher5 => Seq(
            "Variable `acc` not defined"
          )
        case CypherVersion.Cypher25 => Seq(
            "allReduce() function is not available in this implementation of Cypher due to lack of support for allReduce() function."
          )
      }
  }

  test("allReduce() available with semantic feature") {
    run(
      "RETURN allReduce(acc = 0, acc + 1, acc <= 5) AS result",
      pipeline = pipelineWithSemanticFeatures(SemanticFeature.AllReduceFunctionAvailable)
    )
      .hasErrorMessagesIn {
        case CypherVersion.Cypher5 => Seq(
            "Variable `acc` not defined"
          )
        case CypherVersion.Cypher25 => Seq.empty
      }
  }
}
