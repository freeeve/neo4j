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
package org.neo4j.cypher.internal.expressions

import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.collection.immutable.ListSet
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.CTString
import org.neo4j.cypher.internal.util.symbols.invariantTypeSpec
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper

class PredicateExpressionsTest extends CypherFunSuite {

  test("Ands.apply should leave ListSet untouched") {
    // GIVEN
    val expr1 = DummyExpression(CTString)
    val expr2 = DummyExpression(CTInteger)
    val ls: Set[Expression] = ListSet(expr1, expr2)

    // WHEN
    val ands = Ands.apply(ls)(InputPosition.NONE)

    // THEN
    ands.exprs must be theSameInstanceAs ls
  }

  test("Ands.create should leave ListSet untouched") {
    // GIVEN
    val expr1 = DummyExpression(CTString)
    val expr2 = DummyExpression(CTInteger)
    val ls: Set[Expression] = ListSet(expr1, expr2)

    // WHEN
    val ands = Ands.create(ls)

    // THEN
    ands mustBe a[Ands]
    ands.asInstanceOf[Ands].exprs must be theSameInstanceAs ls
  }

  test("Ands.unwrap should unwrap ListSet untouched") {
    // GIVEN
    val expr1 = DummyExpression(CTString)
    val expr2 = DummyExpression(CTInteger)
    val ls: Set[Expression] = ListSet(expr1, expr2)
    val ands = Ands.apply(ls)(InputPosition.NONE)

    // WHEN
    val exprs = Ands.unwrap(ands)

    // THEN
    exprs must be theSameInstanceAs ls
  }

  test("Ands.unwrap should unwrap single element untouched") {
    // GIVEN
    val expr = DummyExpression(CTString)

    // WHEN
    val exprs = Ands.unwrap(expr)

    // THEN
    exprs must have size 1
    exprs.head must be theSameInstanceAs expr
  }

  test("Ands.unwrap should unwrap empty ListSet untouched") {
    // GIVEN
    val ls: Set[Expression] = ListSet.empty
    val ands = Ands.apply(ls)(InputPosition.NONE)

    // WHEN
    val exprs = Ands.unwrap(ands)

    // THEN
    exprs must be theSameInstanceAs ls
  }
}
