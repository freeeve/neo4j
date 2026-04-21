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
package org.neo4j.cypher.internal.ast.semantics

import org.neo4j.cypher.internal.ast.DummyExpression
import org.neo4j.cypher.internal.ast.SemanticCheckInTest.SemanticCheckWithDefaultContext
import org.neo4j.cypher.internal.expressions.ListComprehension
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.util.DummyPosition
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.symbols.CTBoolean
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.CTList
import org.neo4j.cypher.internal.util.symbols.CTNode
import org.neo4j.cypher.internal.util.symbols.CTNumber
import org.neo4j.cypher.internal.util.symbols.CTString

class ListComprehensionTest extends SemanticFunSuite {

  val dummyExpression = DummyExpression(
    CTList(CTNode) | CTBoolean | CTList(CTString)
  )

  test("withoutExtractExpressionShouldHaveCollectionTypesOfInnerExpression") {
    val filter = ListComprehension(
      Variable("x")(DummyPosition(5), Variable.isIsolatedDefault),
      dummyExpression,
      None,
      None
    )(DummyPosition(0))
    val result = SemanticExpressionCheck.simple(filter).run(SemanticState.clean)
    result.errors shouldBe empty
    types(filter)(result.state) should equal(CTList(CTNode) | CTList(CTString))
  }

  test("shouldHaveCollectionWithInnerTypesOfExtractExpression") {
    val extractExpression = DummyExpression(CTNode | CTNumber, DummyPosition(2))

    val filter = ListComprehension(
      Variable("x")(DummyPosition(5), Variable.isIsolatedDefault),
      dummyExpression,
      None,
      Some(extractExpression)
    )(
      DummyPosition(0)
    )
    val result = SemanticExpressionCheck.simple(filter).run(SemanticState.clean)
    result.errors shouldBe empty
    types(filter)(result.state) should equal(CTList(CTNode) | CTList(CTNumber))
  }

  test("shouldSemanticCheckPredicateInStateContainingTypedVariable") {
    val error = SemanticError.internalError(this.getClass.getSimpleName, "dummy error", DummyPosition(8))
    val predicate = ErrorExpression(error, CTAny, DummyPosition(7))

    val filter =
      ListComprehension(
        Variable("x")(DummyPosition(2), Variable.isIsolatedDefault),
        dummyExpression,
        Some(predicate),
        None
      )(DummyPosition(0))
    val result = SemanticExpressionCheck.simple(filter).run(SemanticState.clean)
    result.errors should equal(Seq(error))
    result.state.symbol("x") should equal(None)
  }

  test("should declare variables in list comprehension without predicate") {
    val listComprehension =
      ListComprehension(Variable("x")(DummyPosition(2), Variable.isIsolatedDefault), dummyExpression, None, None)(
        DummyPosition(0)
      )
    val result = SemanticExpressionCheck.simple(listComprehension).run(SemanticState.clean)
    result.errors shouldBe empty
    // x should not be in the outer scope
    result.state.symbol("x") should equal(None)
    // x should be in the inner scope
    result.state.scopeTree.children.head.symbolTable.keys should contain("x")
  }

  test("list comprehension over an integer reports type mismatch") {
    val l = variable("l")
    val lc = ListComprehension(l, literal(123), Some(isNotNull(l)), None)(DummyPosition(0))
    val result = SemanticExpressionCheck.simple(lc).run(SemanticState.clean)
    result.errors should have length 1
    // Prefer substring assert unless you want to lock the full expected string:
    val e42001 = result.errors.head.gqlStatusObject
    e42001.gqlStatus() should equal("42001")
    e42001.cause() should not be empty
    val e22NB1 = e42001.cause().get()
    e22NB1.gqlStatus() should equal("22NB1")
    e22NB1.getMessage should include("Type mismatch: expected to be LIST but was 'INTEGER'.")
  }

  test("list comprehension over non-list parameter reports type mismatch") {
    val l = variable("l")
    val listParam = parameter("list", CTInteger) // static type: $list is an integer, not a list
    val lc = ListComprehension(l, listParam, Some(isNotNull(l)), None)(DummyPosition(0))
    val result = SemanticExpressionCheck.simple(lc).run(SemanticState.clean)
    result.errors should have length 1
    // Prefer substring assert unless you want to lock the full expected string:
    val e22G03 = result.errors.head.gqlStatusObject
    e22G03.gqlStatus() should equal("22G03")
    e22G03.cause() should not be empty
    val e22N27 = e22G03.cause().get()
    e22N27.gqlStatus() should equal("22N27")
    e22N27.getMessage should include("Invalid input 'INTEGER' for  parameter: list. Expected to be LIST.")
  }
}
