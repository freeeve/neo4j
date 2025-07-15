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
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.frontend.SemanticAnalysisTestSuite.Pipeline
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.If
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.AmbiguousAggregationAnalysis
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.PreparatoryRewriting
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.ResolveAllReduceGroupVariable
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.SemanticAnalysis
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.SemanticTypeCheck
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class AllReducePredicateSemanticAnalysisTest extends CypherFunSuite with SemanticAnalysisTestSuite {

  private def pipelineWithResolveGroupVariableRewriter(rewriterEnabled: Boolean): Pipeline =
    PreparatoryRewriting andThen
      SemanticAnalysis(warn = Some(true), SemanticFeature.AllReduceFunctionAvailable) andThen
      If((_: BaseState) => rewriterEnabled)(ResolveAllReduceGroupVariable) andThen
      SemanticAnalysis(warn = Some(false), SemanticFeature.AllReduceFunctionAvailable) andThen
      SemanticTypeCheck andThen
      AmbiguousAggregationAnalysis

  private def run25(query: String): AnalysisAssertions = {
    run(
      query,
      pipeline = pipelineWithResolveGroupVariableRewriter(rewriterEnabled = true),
      disabledVersions = Set(CypherVersion.Cypher5)
    )
  }

  // Don't run the rewriter if we expect to fail during the first pass of semantic analysis
  private def run25WithoutRewriter(query: String): AnalysisAssertions = {
    run(
      query,
      pipeline = pipelineWithResolveGroupVariableRewriter(rewriterEnabled = false),
      disabledVersions = Set(CypherVersion.Cypher5)
    )
  }

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
      "MATCH (a)-[r]->+(b) RETURN allReduce(acc = [], acc + r, size(acc) <= 5) AS result",
      pipeline = pipelineWithSemanticFeatures(SemanticFeature.AllReduceFunctionAvailable)
    )
      .hasErrorMessagesIn {
        case CypherVersion.Cypher5 => Seq(
            "Variable `acc` not defined"
          )
        case CypherVersion.Cypher25 => Seq.empty
      }
  }

  test("should not allow undefined variables in init") {
    run25WithoutRewriter(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = zzz, acc + r.prop, acc <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Variable `zzz` not defined")
  }

  test("should not allow accumulator variable in init") {
    run25WithoutRewriter(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = acc, acc + r.prop, acc <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Variable `acc` not defined")
  }

  test("should not allow aggregation in init in WHERE") {
    run25WithoutRewriter(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = count(*), acc + r.prop, acc <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Invalid use of aggregating function count(...) in this context")
  }

  test("should allow aggregation in init in RETURN") {
    run25(
      """MATCH (a)-[r]->+(b)
        |RETURN r, allReduce(acc = count(*), acc + r.prop, acc <= 5) AS result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should find implicit grouping expression in allReduce()") {
    run25(
      """MATCH (a)-[r]->+(b)
        |RETURN allReduce(acc = count(*), acc + r.prop, acc <= 5) AS result
        |""".stripMargin
    ).hasErrorMessages(SemanticError.implicitGroupingExpressionInAggregationColumnErrorMessage(Seq("r")))
  }

  test("accumulator variable does not shadow existing variables in init") {
    run25(
      """WITH 1 AS acc
        |MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = acc, acc + r.prop, acc <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow subquery expression in init") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = COUNT { (a)-->(acc) WHERE a <> acc }, acc + r.prop, acc <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow nested allReduce() in init") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = allReduce(acc = [], acc + r, size(acc) < 5),
        |                toInteger(acc) + r.prop,
        |                toInteger(acc) <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("return type of allReduce() is Boolean") {
    run25WithoutRewriter(
      """MATCH (a)-[r]->+(b)
        |WHERE isEmpty(allReduce(acc = 0, acc + r.prop, acc <= 5))
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Type mismatch: expected Map, Node, Relationship, String or List<T> but was Boolean")
  }

  test("type of projected allReduce() is Boolean") {
    run25WithoutRewriter(
      """MATCH (a)-[r]->+(b)
        |WITH allReduce(acc = 0, acc + r.prop, acc <= 5) AS arResult
        |RETURN isEmpty(arResult)
        |""".stripMargin
    ).hasErrorMessages("Type mismatch: expected Map, Node, Relationship, String or List<T> but was Boolean")
  }

  test("predicate should be a boolean expression") {
    run25WithoutRewriter(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = 0, acc + r.prop, 123)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Type mismatch: expected Boolean but was Integer")
  }

  test("should accept possible boolean expressions as predicate") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = [], acc + r.prop, acc[2])
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should treat group variable in reduction step as singleton") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = [], acc + r.prop, size(acc) <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should not allow to use group variable in reduction step as a list") {
    run25WithoutRewriter(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = 0, acc + size(r), acc <= 10)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Type mismatch: expected String, Vector or List<T> but was Relationship")
  }

  test("should not allow multiple group variables in reduction step") {
    run25WithoutRewriter(
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = 0, acc + r.prop + m.prop, acc <= 10)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Wrong number of group variables: 2")
  }

  test("should require at least one group variable in reduction step") {
    run25WithoutRewriter(
      """MATCH (a)((n)-[r]->(m))+(b)
        |WHERE allReduce(acc = 0, acc + 1, acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Wrong number of group variables: 0")
  }

  test("should allow to reference additional non-group variables in reduction step") {
    run25(
      """WITH 123 AS threshold
        |MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = 0, acc + a.prop + b.prop + r.prop, acc <= threshold)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should not allow aggregation in reduction step") {
    run25WithoutRewriter(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = [], acc + r.prop + count(*), size(acc) <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Invalid use of aggregating function count(...) in this context")
  }

  test("should not allow nested allReduce() in reduction step") {
    run25WithoutRewriter(
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = [],
        |                acc + m.prop + allReduce(nacc = 0, nacc + r.prop, nacc < 10),
        |                size(acc) <= 123)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Wrong number of group variables: 0")
  }

  test("should allow subquery expression in reduction step") {
    run25(
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = 0,
        |                acc + COUNT { (n)-[:FRIEND_OF]->() },
        |                acc <= 123)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should not allow multiple group variables in subquery expression in reduction step") {
    run25WithoutRewriter(
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = 0,
        |                acc + COUNT { (n)-[:FRIEND_OF]->(m) },
        |                acc < 5)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Wrong number of group variables: 2")
  }

  test("should correctly find group variable through CALL (*) in reduction step") {
    run25(
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = 0,
        |                acc + COUNT {
        |                        CALL (*) { MATCH (n)-[:FRIEND_OF]->(x) RETURN x }
        |                      },
        |                acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should correctly find extra group variables through CALL (*) in reduction step") {
    run25WithoutRewriter(
      """MATCH (a)((n)-[r]->(m))+(b)
        |WHERE allReduce(acc = 0,
        |                acc + COUNT {
        |                        CALL (*) { MATCH (n)-[:FRIEND_OF]->(m) RETURN n, m }
        |                      },
        |                acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Wrong number of group variables: 2")
  }

  test("should correctly identify shadowed variables in CALL in reduction step") {
    run25WithoutRewriter(
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = 0,
        |                acc + COUNT { WITH 1 AS n RETURN n },
        |                acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages(
      "The variable `n` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      "Wrong number of group variables: 0"
    )
  }

  test("should carry group variables through WITH *") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WITH *
        |RETURN allReduce(acc = 0, acc + r.prop, acc <= 5) AS result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should carry group variables through NEXT") {
    run25(
      """MATCH (a)-[r]->+(b)
        |RETURN r
        |NEXT
        |RETURN allReduce(acc = 0, acc + r.prop, acc <= 5) AS result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should carry group variables through explicit WITH") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WITH r
        |RETURN allReduce(acc = 0, acc + r.prop, acc <= 5) AS result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should carry group variables through WITH aliasing") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WITH r AS alias
        |RETURN allReduce(acc = 0, acc + alias.prop, acc <= 5) AS result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should not mark output of coalesce() as group variable") {
    run25WithoutRewriter(
      """MATCH (a)-[r]->+(b)
        |WITH coalesce(r, 123) AS alias
        |RETURN allReduce(acc = 0, acc + alias.prop, acc <= 5) AS result
        |""".stripMargin
    ).hasErrorMessages(
      "Type mismatch: expected Map, Node, Relationship, Point, Duration, Date, Time, LocalTime, LocalDateTime or DateTime but was Integer or List<Relationship>",
      "Wrong number of group variables: 0"
    )
  }

  test("should carry group variables through CALL subquery import") {
    run25(
      """MATCH (a)-[r]->+(b)
        |CALL (r) {
        |  RETURN allReduce(acc = 0, acc + r.prop, acc <= 5) AS result
        |}
        |RETURN result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should carry group variables through CALL (*) subquery import") {
    run25(
      """MATCH (a)-[r]->+(b)
        |CALL (*) {
        |  RETURN allReduce(acc = 0, acc + r.prop, acc <= 5) AS result
        |}
        |RETURN result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should carry group variables through CALL subquery import into UNION") {
    run25(
      """MATCH (a)-[r]->+(b)
        |CALL (r) {
        |  RETURN 123 AS result
        |  UNION
        |  RETURN allReduce(acc = 0, acc + r.prop, acc <= 5) AS result
        |}
        |RETURN result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should carry group variables through CALL (*) subquery import into UNION") {
    run25(
      """MATCH (a)-[r]->+(b)
        |CALL (*) {
        |  RETURN 123 AS result
        |  UNION
        |  RETURN allReduce(acc = 0, acc + r.prop, acc <= 5) AS result
        |}
        |RETURN result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow shadowed variable name for accumulator") {
    run25WithoutRewriter(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(a = 0, a + r.prop, a <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow predicate without accumulator") {
    run25WithoutRewriter(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = 0, acc + r.prop, a.prop = 5)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }
}
