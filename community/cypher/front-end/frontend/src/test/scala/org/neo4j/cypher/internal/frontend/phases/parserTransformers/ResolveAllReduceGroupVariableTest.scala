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
package org.neo4j.cypher.internal.frontend.phases.parserTransformers

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.VariableStringInterpolator
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.AllReduceFunctionAvailable
import org.neo4j.cypher.internal.expressions.AllReducePredicate
import org.neo4j.cypher.internal.expressions.AllReducePredicate.AllReduceScope
import org.neo4j.cypher.internal.expressions.AllReducePredicate.ReductionStepScope
import org.neo4j.cypher.internal.expressions.AllReducePredicateUnchecked
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.RewritePhaseTest
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class ResolveAllReduceGroupVariableTest extends CypherFunSuite with RewritePhaseTest with AstConstructionTestSupport {
  override def rewriterPhaseUnderTest: Transformer[BaseContext, BaseState, BaseState] = ResolveAllReduceGroupVariable

  override def semanticFeatures: Seq[SemanticFeature] = Seq(AllReduceFunctionAvailable)

  test("should rewrite unresolved allReduce()") {
    val q =
      """MATCH (a)-[r]->+(b)
        |WHERE allReduce(acc = 0, acc + r.prop, acc <= 10)
        |RETURN a, b""".stripMargin

    rewriteAndAssert(q) { statement =>
      statement.folder.findAllByClass[AllReducePredicateUnchecked] shouldBe empty
      statement.folder.findAllByClass[AllReducePredicate] shouldEqual Seq(
        AllReducePredicate(
          scope = AllReduceScope(
            accumulator = v"acc",
            reductionStepScope = ReductionStepScope(
              singletonVariable = v"r",
              reductionStep = add(v"acc", prop(v"r", "prop"))
            )(pos),
            predicate = lessThanOrEqual(v"acc", literalInt(10))
          )(pos),
          groupVariable = v"r",
          init = literalInt(0)
        )(pos)
      )
    }
  }

  test("should rewrite multiple unresolved allReduce()") {
    val q =
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE
        |  allReduce(acc = 0, acc + n.prop, acc <= 10) AND
        |  allReduce(acc = 1, acc * m.prop, acc < 123)
        |
        |RETURN a, b""".stripMargin

    rewriteAndAssert(q) { statement =>
      statement.folder.findAllByClass[AllReducePredicateUnchecked] shouldBe empty
      statement.folder.findAllByClass[AllReducePredicate] should contain theSameElementsAs Seq(
        AllReducePredicate(
          scope = AllReduceScope(
            accumulator = v"acc",
            reductionStepScope = ReductionStepScope(
              singletonVariable = v"n",
              reductionStep = add(v"acc", prop(v"n", "prop"))
            )(pos),
            predicate = lessThanOrEqual(v"acc", literalInt(10))
          )(pos),
          groupVariable = v"n",
          init = literalInt(0)
        )(pos),
        AllReducePredicate(
          scope = AllReduceScope(
            accumulator = v"acc",
            reductionStepScope = ReductionStepScope(
              singletonVariable = v"m",
              reductionStep = multiply(v"acc", prop(v"m", "prop"))
            )(pos),
            predicate = lessThan(v"acc", literalInt(123))
          )(pos),
          groupVariable = v"m",
          init = literalInt(1)
        )(pos)
      )
    }
  }

  test("should rewrite nested unresolved allReduce()") {
    val q =
      """MATCH (a) ((n)-[r]->(m))+ (b)
        |WHERE allReduce(sum = toInteger(allReduce(prod = 1, prod * m.prop, prod < 123)),
        |                sum + n.prop,
        |                sum <= 10)
        |RETURN a, b""".stripMargin

    rewriteAndAssert(q) { statement =>
      statement.folder.findAllByClass[AllReducePredicateUnchecked] shouldBe empty

      val prod = AllReducePredicate(
        scope = AllReduceScope(
          accumulator = v"prod",
          reductionStepScope = ReductionStepScope(
            singletonVariable = v"m",
            reductionStep = multiply(v"prod", prop(v"m", "prop"))
          )(pos),
          predicate = lessThan(v"prod", literalInt(123))
        )(pos),
        groupVariable = v"m",
        init = literalInt(1)
      )(pos)

      val sum = AllReducePredicate(
        scope = AllReduceScope(
          accumulator = v"sum",
          reductionStepScope = ReductionStepScope(
            singletonVariable = v"n",
            reductionStep = add(v"sum", prop(v"n", "prop"))
          )(pos),
          predicate = lessThanOrEqual(v"sum", literalInt(10))
        )(pos),
        groupVariable = v"n",
        init = function("toInteger", prod)
      )(pos)

      statement.folder.findAllByClass[AllReducePredicate] should contain theSameElementsAs Seq(sum, prod)
    }
  }

  private def rewriteAndAssert(q: String)(verify: Statement => Unit): Unit = {
    rewriteAndAssert(q, verify, disabledVersions = Set(CypherVersion.Cypher5))
  }

}
