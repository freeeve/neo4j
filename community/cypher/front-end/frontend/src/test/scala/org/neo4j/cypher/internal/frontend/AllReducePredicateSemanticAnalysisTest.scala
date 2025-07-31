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
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.AmbiguousAggregationAnalysis
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class AllReducePredicateSemanticAnalysisTest extends CypherFunSuite with SemanticAnalysisTestSuite {

  private def run25(query: String): AnalysisAssertions = {
    run(
      query,
      disabledVersions = Set(CypherVersion.Cypher5)
    )
  }

  test("allReduce() is available") {
    run25(
      "MATCH (a)-[r]->+(b) RETURN allReduce(acc = [], iter IN r | acc + iter, size(acc) <= 5) AS result"
    ).hasNoErrors
  }

  test("should not allow undefined variables in init") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = zzz, rel IN r | acc + rel.prop, acc <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Variable `zzz` not defined")
  }

  test("should return an error when the iteration expression is not a list") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = 45, rel IN 1 | acc + rel.prop, acc <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages(
      "Type mismatch: expected List<T> but was Integer",
      "Type mismatch: expected Map, Node, Relationship, Point, Duration, Date, Time, LocalTime, LocalDateTime or DateTime but was Any"
    )
  }

  test("should not allow accumulator variable in init") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = acc, rel IN r | acc + rel.prop, acc <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Variable `acc` not defined")
  }

  test("should not allow aggregation in init in WHERE") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = count(*), rel IN r | acc + rel.prop, acc <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Invalid use of aggregating function count(...) in this context")
  }

  test("should allow aggregation in init in RETURN") {
    run25(
      """MATCH (a)-[r]->+(b)
        |RETURN r, allReduce(acc = count(*), rel IN r | acc + rel.prop, acc <= 5) AS result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should find implicit grouping expression in allReduce()") {
    run(
      """MATCH (a)-[r]->+(b)
        |RETURN allReduce(acc = count(*), rel IN r | acc + rel.prop, acc <= 5) AS result
        |""".stripMargin,
      pipeline =
        pipelineWithSemanticFeatures() andThen AmbiguousAggregationAnalysis,
      disabledVersions = Set(CypherVersion.Cypher5)
    ).hasErrorMessages(SemanticError.implicitGroupingExpressionInAggregationColumnErrorMessage(Seq("r")))
  }

  test("accumulator variable does not shadow existing variables in init") {
    run25(
      """WITH 1 AS acc
        |MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = acc, rel IN r | acc + rel.prop, acc <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow subquery expression in init") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = COUNT { (a)-->(acc) WHERE a <> acc }, rel IN r | acc + rel.prop, acc <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow nested allReduce() in init") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = allReduce(acc = [], rel IN r | acc + rel, size(acc) < 5),
        |                rel IN r | toInteger(acc) + rel.prop,
        |                toInteger(acc) <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("return type of allReduce() is Boolean") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE isEmpty(allReduce(acc = 0, rel IN r | acc + rel.prop, acc <= 5))
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Type mismatch: expected Map, Node, Relationship, String or List<T> but was Boolean")
  }

  test("type of projected allReduce() is Boolean") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WITH allReduce(acc = 0, rel IN r | acc + rel.prop, acc <= 5) AS arResult
        |RETURN isEmpty(arResult)
        |""".stripMargin
    ).hasErrorMessages("Type mismatch: expected Map, Node, Relationship, String or List<T> but was Boolean")
  }

  test("predicate should be a boolean expression") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = 0, rel IN r | acc + rel.prop, acc)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Type mismatch: expected Boolean but was Integer")
  }

  test("should accept possible boolean expressions as predicate") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = [], rel IN r | acc + rel.prop, acc[2])
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should treat group variable in reduction step as singleton") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = [], rel IN r | acc + rel.prop, size(acc) <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow using group variable in reduction step as a list") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = 0, rel IN r | acc + size(r), acc <= 10)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should not allow using implicitly imported group variables in reduction step as singletons") {
    run25(
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = 0, rel IN r | acc + rel.prop + m.prop, acc <= 10)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages(
      "Type mismatch: expected Map, Node, Relationship, Point, Duration, Date, Time, LocalTime, LocalDateTime or DateTime but was List<Node>"
    )
  }

  test("can support reduction with no group variables") {
    run25(
      """MATCH (a)((n)-[r]->(m))+(b)
        |WHERE allReduce(acc = 0, rel IN [0,1] | acc + 1, acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow to reference additional non-group variables in reduction step") {
    run25(
      """WITH 123 AS threshold
        |MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = 0, rel IN r | acc + a.prop + b.prop + rel.prop, acc <= threshold)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should not allow aggregation in reduction step") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = [], rel IN r | acc + rel.prop + count(*), size(acc) <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Invalid use of aggregating function count(...) in this context")
  }

  test("should allow nested allReduce() in reduction step") {
    run25(
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = [],
        |                node IN m | acc + node.prop + allReduce(nacc = 0, rel IN r | nacc + rel.prop, nacc < 10),
        |                size(acc) <= 123)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow subquery expression in reduction step") {
    run25(
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = 0,
        |                node IN n | acc + COUNT { (node)-[:FRIEND_OF]->() },
        |                acc <= 123)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow using reductionStepVariable through CALL (*) in reduction step") {
    run25(
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = 0,
        |                node IN n | acc + COUNT {
        |                        CALL (*) { MATCH (node)-[:FRIEND_OF]->(x) RETURN x }
        |                      },
        |                acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow using the accumulator through CALL (*) in reduction step") {
    run25(
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = 0,
        |                node IN n | acc + COUNT {
        |                        CALL (*) {
        |                          MATCH (node)-[:FRIEND_OF]->(x)
        |                          WHERE x.prop < acc
        |                          RETURN x
        |                        }
        |                      },
        |                acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should identify improper usage of group variables through CALL (*) in reduction step") {
    run25(
      """MATCH (a)((n)-[r]->(m))+(b)
        |WHERE allReduce(acc = 0,
        |                node IN n | acc + COUNT {
        |                        CALL (*) { MATCH (node)-[:FRIEND_OF]->(m) RETURN node, m }
        |                      },
        |                acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Type mismatch: m defined with conflicting type List<Node> (expected Node)")
  }

  test("should identify improper usage of group variables through CALL with variable in reduction step") {
    run25(
      """MATCH (a)((n)-[r]->(m))+(b)
        |WHERE allReduce(acc = 0,
        |                node IN n | acc + COUNT {
        |                        CALL (m) { MATCH (node)-[:FRIEND_OF]->(m) RETURN node, m }
        |                      },
        |                acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Type mismatch: m defined with conflicting type List<Node> (expected Node)")
  }

  test("should override group variables through CALL without variables in reduction step") {
    run25(
      """MATCH (a)((n)-[r]->(m))+(b)
        |WHERE allReduce(acc = 0,
        |                node IN n | acc + COUNT {
        |                        CALL () { MATCH (node)-[:FRIEND_OF]->(m) RETURN node, m }
        |                      },
        |                acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should identify improper usage of group variables through CALL (*) in the predicate") {
    run25(
      """MATCH (a)((n)-[r]->(m))+(b)
        |WHERE allReduce(acc = 0,
        |                node IN n | acc + node.prop,
        |                EXISTS {
        |                        CALL (*) { MATCH (a)-[:FRIEND_OF]->(m) WHERE a.prop = acc RETURN a, m }
        |                      })
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Type mismatch: m defined with conflicting type List<Node> (expected Node)")
  }

  test("should identify improper usage of group variables through CALL (m) in the predicate") {
    run25(
      """MATCH (a)((n)-[r]->(m))+(b)
        |WHERE allReduce(acc = 0,
        |                node IN n | acc + node.prop,
        |                EXISTS {
        |                        CALL (m) { MATCH (a)-[:FRIEND_OF]->(m) WHERE a.prop = acc RETURN a, m }
        |                      })
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Type mismatch: m defined with conflicting type List<Node> (expected Node)")
  }

  test("should not identify the usage of the accumulator if using CALL() in the predicate") {
    run25(
      """MATCH (a)((n)-[r]->(m))+(b)
        |WHERE allReduce(acc = 0,
        |                node IN n | acc + node.prop,
        |                EXISTS {
        |                        CALL () { MATCH (a)-[:FRIEND_OF]->(m) WHERE a.prop = acc RETURN a, m }
        |                      })
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages(
      "Variable `acc` not defined"
    ) // Note that the variable m is newly defined here, so you shouldn't see the error from the other tests here.
  }

  test("should not allow variables introduced in predicates in the reduction step") {
    run25(
      """MATCH (a)((n)-[r]->(m))+(b)
        |WHERE allReduce(acc = 0,
        |                node IN n | acc + node.prop + x.prop,
        |                EXISTS {
        |                  MATCH (x)-[:FRIEND_OF]->() WHERE x.prop = acc RETURN x
        |                })
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages(
      "Variable `x` not defined"
    ) // Note that the variable m is newly defined here, so you shouldn't see the error from the other tests here.
  }

  test("should ensure that the accumulator and the reduction step have matching types") {
    run25(
      """MATCH (a)((n)-[r]->(m))+(b)
        |WHERE allReduce(acc = 0,
        |                node IN ["Hello", "World"] | acc + node,
        |                acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Type mismatch: accumulator is Integer but reduction has type String")
  }

  test("should not throw an error when accumulator can be coerced to the reduction type") {
    run25(
      """MATCH (a)((n)-[r]->(m))+(b)
        |WHERE allReduce(acc = "0",
        |                node IN [1,2] | acc + node,
        |                acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should correctly find reduction step type mismatches through CALL (*) in reduction step") {
    run25(
      """MATCH (a)((n)-[r]->(m))+(b)
        |WHERE allReduce(acc = {},
        |                node IN n | acc + COUNT {
        |                        CALL (*) { MATCH (node)-[:FRIEND_OF]->(x) RETURN node, x}
        |                      },
        |                acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages(
      "Type mismatch: expected List<T> but was Integer",
      "Type mismatch: accumulator is Map but reduction has type Any"
    )
  }

  test("should correctly identify shadowed variables in CALL in reduction step") {
    run25(
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(acc = 0,
        |                node IN n | acc + COUNT { WITH 1 AS n RETURN n },
        |                acc < 10)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages(
      "The variable `n` is shadowing a variable with the same name from the outer scope and needs to be renamed"
    )
  }

  test("should carry group variables through WITH *") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WITH *
        |RETURN allReduce(acc = 0, rel IN r | acc + rel.prop, acc <= 5) AS result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should carry group variables through NEXT") {
    run25(
      """MATCH (a)-[r]->+(b)
        |RETURN r
        |NEXT
        |RETURN allReduce(acc = 0, rel IN r | acc + rel.prop, acc <= 5) AS result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should carry group variables through explicit WITH") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WITH r
        |RETURN allReduce(acc = 0, rel IN r | acc + rel.prop, acc <= 5) AS result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should carry group variables through WITH aliasing") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WITH r AS alias
        |RETURN allReduce(acc = 0, rel IN alias | acc + rel.prop, acc <= 5) AS result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow running an allReduce on the output of coalesce()") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WITH coalesce(r, 123) AS alias
        |RETURN allReduce(acc = 0, rel IN alias | acc + rel.prop, acc <= 5) AS result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should carry group variables through CALL subquery import") {
    run25(
      """MATCH (a)-[r]->+(b)
        |CALL (r) {
        |  RETURN allReduce(acc = 0, rel IN r | acc + rel.prop, acc <= 5) AS result
        |}
        |RETURN result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should carry group variables through CALL (*) subquery import") {
    run25(
      """MATCH (a)-[r]->+(b)
        |CALL (*) {
        |  RETURN allReduce(acc = 0, rel IN r | acc + rel.prop, acc <= 5) AS result
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
        |  RETURN allReduce(acc = 0, rel IN r | acc + rel.prop, acc <= 5) AS result
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
        |  RETURN allReduce(acc = 0, rel IN r | acc + rel.prop, acc <= 5) AS result
        |}
        |RETURN result
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow shadowed variable name for accumulator") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(a = 0, rel IN r | a + rel.prop, a <= 5)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should allow predicate without accumulator") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = 0, rel IN r | acc + rel.prop, a.prop = 5)
        |RETURN a, b
        |""".stripMargin
    ).hasNoErrors
  }

  test("should not allow reduction step variable in predicate") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = 0, rel IN r | acc + rel.prop, rel.prop = 5)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Variable `rel` not defined")
  }

  test("should not allow using reduction step variable to initialize accumulator") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = rel.prop, rel IN r | acc + rel.prop, acc = 5)
        |RETURN a, b
        |""".stripMargin
    ).hasErrorMessages("Variable `rel` not defined")
  }

  test("should not allow using reduction step variable outside of reduction step") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = rel.prop, rel IN r | acc + rel.prop, acc = 5)
        |RETURN a, rel
        |""".stripMargin
    ).hasErrorMessages("Variable `rel` not defined")
  }

  test("should fail gracefully when allReduce() is used inside the QPP") {
    run25(
      """MATCH p = (a)-[r WHERE allReduce(acc = 0, rel IN r | acc + rel.p, acc <= 5)]->+(b)
        |RETURN p
        |""".stripMargin
    ).hasErrorMessages(
      "Type mismatch: expected List<T> but was Relationship",
      "Type mismatch: expected Map, Node, Relationship, Point, Duration, Date, Time, LocalTime, LocalDateTime or DateTime but was Any"
    )
  }

  test("reduction variable can shadow any variable outside of the allReduce scope") {
    run25(
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = 0, r IN r | acc + r.prop, acc <= 5)
        |RETURN a, b, r
        |""".stripMargin
    ).hasNoErrors
  }
}
