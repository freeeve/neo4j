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
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.expressions.AllReducePredicate
import org.neo4j.cypher.internal.expressions.AllReducePredicate.AllReduceScope
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.FunctionName
import org.neo4j.cypher.internal.expressions.Namespace
import org.neo4j.cypher.internal.util.symbols.CTAny

class AllReducePredicateParserTest extends AstParsingTestBase {

  test("allReduce(acc = 0, 1, 2)") {
    parsesTo[FunctionInvocation](FunctionInvocation(
      functionName = FunctionName(namespace = Namespace(parts = List())(pos), name = "allReduce")(pos),
      distinct = false,
      args = IndexedSeq(
        equals(lhs = v"acc", rhs = literalInt(0)),
        literalInt(1),
        literalInt(2)
      )
    )(pos))
  }

  test("allReduce(acc = [], iter in r | acc + iter, size(acc) <= $hops)") {
    parsesIn[Expression] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input '|': expected an expression, ')' or ','")
      case _ => _.toAstPositioned(AllReducePredicate(
          AllReduceScope(
            accumulator = v"acc",
            reductionStepScope = AllReducePredicate.ReductionStepScope(
              reductionStepVariable = v"iter",
              reductionStep = add(v"acc", v"iter")
            )(pos),
            predicate = lessThanOrEqual(size(v"acc"), parameter("hops", CTAny))
          )(pos),
          init = listOf(),
          list = v"r"
        )(pos))
    }
  }

  test(
    "allReduce(acc = 0, iter IN r | acc + 1, allReduce(nestedAcc = acc, nestedIter in r | nestedAcc + 2, acc < nestedAcc))"
  ) {
    parsesIn[Expression] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input '|': expected an expression, ')' or ','")
      case _ => _.toAstPositioned(AllReducePredicate(
          AllReduceScope(
            accumulator = v"acc",
            reductionStepScope = AllReducePredicate.ReductionStepScope(
              reductionStepVariable = v"iter",
              reductionStep = add(v"acc", literalInt(1))
            )(pos),
            predicate = AllReducePredicate(
              AllReduceScope(
                accumulator = v"nestedAcc",
                reductionStepScope = AllReducePredicate.ReductionStepScope(
                  reductionStepVariable = v"nestedIter",
                  reductionStep = add(v"nestedAcc", literalInt(2))
                )(pos),
                predicate = lessThan(v"acc", v"nestedAcc")
              )(pos),
              init = v"acc",
              list = v"r"
            )(pos)
          )(pos),
          init = literalInt(0),
          list = v"r"
        )(pos))
    }
  }

  test("allReduce(iter IN r | acc + 1, acc < nestedAcc)") {
    parsesIn[Expression](_ => _.withSyntaxErrorContaining("Invalid input '|': expected an expression, ')' or ','"))
  }

  test("allReduce(acc =0, iter IN r | acc + 1)") {
    parsesIn[Expression] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input '|': expected an expression, ')' or ','")
      case _       => _.withSyntaxErrorContaining("Invalid input ')': expected an expression or ','")
    }
  }

  test("allReduce(acc =0, acc + 5, iter IN r | acc + 1)") {
    parsesIn[Expression](_ => _.withSyntaxErrorContaining("Invalid input '|': expected an expression, ')' or ','"))
  }

  test("allReduce(acc =0, iter IN r | acc + 1, acc < 5, acc > 10)") {
    parsesIn[Expression] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input '|': expected an expression, ')' or ','")
      case _       => _.withSyntaxErrorContaining("Invalid input ',': expected an expression or ')'")
    }
  }
}
