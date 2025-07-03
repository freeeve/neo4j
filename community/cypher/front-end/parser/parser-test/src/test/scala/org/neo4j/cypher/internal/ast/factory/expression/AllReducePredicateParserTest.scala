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
package org.neo4j.cypher.internal.ast.factory.expression

import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.VariableStringInterpolator
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher25
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.expressions.AllReducePredicate
import org.neo4j.cypher.internal.expressions.AllReducePredicateUnchecked
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.cypher.internal.util.symbols.CTAny

class AllReducePredicateParserTest extends AstParsingTestBase {

  test("allReduce(acc = 0, 1, 2)") {
    parsesToAllReducePredicate {
      AllReducePredicate.unchecked(
        accumulator = v"acc",
        init = literalInt(0),
        reductionStep = literalInt(1),
        predicate = literalInt(2)
      )(pos)
    }
  }

  test("allReduce(acc = [], acc + r, size(acc) <= $hops)") {
    parsesToAllReducePredicate {
      AllReducePredicate.unchecked(
        accumulator = v"acc",
        init = listOf(),
        reductionStep = add(v"acc", v"r"),
        predicate = lessThanOrEqual(size(v"acc"), parameter("hops", CTAny))
      )(pos)
    }
  }

  test("allReduce(acc = 0, acc + 1, allReduce(nestedAcc = acc, nestedAcc + 2, acc < nestedAcc))") {
    parsesToAllReducePredicate {
      AllReducePredicate.unchecked(
        accumulator = v"acc",
        init = literalInt(0),
        reductionStep = add(v"acc", literalInt(1)),
        predicate = AllReducePredicate.unchecked(
          accumulator = v"nestedAcc",
          init = v"acc",
          reductionStep = add(v"nestedAcc", literalInt(2)),
          predicate = lessThan(v"acc", v"nestedAcc")
        )(pos)
      )(pos)
    }
  }

  test("allReduce(0, acc + 1, acc < 123)") {
    parsesTo[Expression] {
      function("allReduce", literalInt(0), add(v"acc", literalInt(1)), lessThan(v"acc", literalInt(123)))
    }
  }

  private def parsesToAllReducePredicate(arp: AllReducePredicateUnchecked): Unit = {
    parsesIn[Expression] {
      case Cypher25 => _.toAst(arp)
      case Cypher5 => _.toAst {
          arp.rewrite(allReducePredicateToFunctionInvocationRewriter).asInstanceOf[Expression]
        }
    }
  }

  private val allReducePredicateToFunctionInvocationRewriter: Rewriter = bottomUp {
    Rewriter.lift {
      case arp: AllReducePredicateUnchecked =>
        function("allReduce", equals(arp.accumulator, arp.init), arp.reductionStep, arp.predicate)
    }
  }
}
