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
package org.neo4j.cypher.internal.frontend.scoping.checker

import org.neo4j.cypher.internal.frontend.scoping.VariableCheckingTestSuite

/**
 * Test for 42I58 - Invalid Entity Reference
 */
class GQL_42I18_invalidReferenceToImplicitlyGroupedExpressions extends VariableCheckingTestSuite {

  test(
    """LET a = 10, b = 2, c = 5
      |UNWIND [1, 2, 3] AS x
      |RETURN a, SUM(x / a) + b + c * 5 AS s""".stripMargin
  ) {
    error(
      "42I18",
      "The aggregation column contains implicit grouping expressions referenced by the variables `b`, `c`."
    )
  }

  test(
    """LET a = 10, b = 2, c = 5
      |UNWIND [1, 2, 3] AS x
      |RETURN a, SUM(x/a) + x, SUM(x / a) + b + c * 5 AS s""".stripMargin
  ) {
    error(
      "42I18",
      "The aggregation column contains implicit grouping expressions referenced by the variables `x`, `b`, `c`."
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN a, SUM(x / a) + x * 5 AS s""".stripMargin) {
    error(
      "42I18",
      "The aggregation column contains implicit grouping expressions referenced by the variables `x`."
    )
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN a AS x, SUM(x / a) + x * 5 AS s""".stripMargin) {
    error(
      "42I18",
      "The aggregation column contains implicit grouping expressions referenced by the variables `x`."
    )
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |RETURN a.name, {foo: a.partner='Andres', kids: collect(child.name)}""".stripMargin) {
    error(
      "42I18",
      "The aggregation column contains implicit grouping expressions referenced by the variables `a`."
    )
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |RETURN a.name, {foo: a.name=child.name, kids: collect(child.name)}""".stripMargin) {
    error(
      "42I18",
      "The aggregation column contains implicit grouping expressions referenced by the variables `child`."
    )
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |RETURN {h:a.name}, {foo: {h:a.name}.h, kids: collect(child.name)}""".stripMargin) {
    error(
      "42I18",
      "The aggregation column contains implicit grouping expressions referenced by the variables `a`."
    )
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |RETURN {h:a.name}, {foo: {h:a.name}.h, kids: collect(child.name)} ORDER BY {h:a.name}""".stripMargin) {
    error(
      "42I18",
      "The aggregation column contains implicit grouping expressions referenced by the variables `a`."
    )
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |RETURN child.name, {foo: a.name='Andres', kids: collect(child.name)}""".stripMargin) {
    error(
      "42I18",
      "The aggregation column contains implicit grouping expressions referenced by the variables `a`."
    )
  }

  // Positive tests

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |RETURN a.name, {foo: a.name='Andres', kids: collect(child.name)}""".stripMargin) {
    passes()
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |RETURN a.name, {foo: a.name=a.name, kids: collect(child.name)}""".stripMargin) {
    passes()
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |RETURN a, {foo: a=a, kids: collect(child.name)}""".stripMargin) {
    passes()
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |RETURN {h:1}, {foo: {h:1}.h, kids: collect(child.name)}""".stripMargin) {
    passes()
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |RETURN {h:a.name}, {foo: {h:1}.h, kids: collect(child.name)}""".stripMargin) {
    passes()
  }

  test("""MATCH (n)
         |WITH DISTINCT n.prop AS prop ORDER BY n.prop DESC
         |RETURN prop""".stripMargin) {
    passes()
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |RETURN a.name, {foo: a.name='Andres', kids: collect(child.name)} ORDER BY a.name""".stripMargin) {
    passes()
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |WITH a.name AS name, {foo: a.name='Andres', kids: collect(child.name)} AS map ORDER BY a.name
         |RETURN name, map""".stripMargin) {
    passes()
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |WITH a.name AS name, {foo: a.name='Andres', kids: collect(child.name)} AS map WHERE a.name
         |RETURN name, map""".stripMargin) {
    passes()
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |WITH a.name AS name, {foo: a.name='Andres', kids: collect(child.name)} AS map ORDER BY name, map
         |RETURN name, map""".stripMargin) {
    passes()
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |WITH a.name AS name, {foo: a.name='Andres', kids: collect(child.name)} AS map WHERE name AND map.foo
         |RETURN name, map""".stripMargin) {
    passes()
  }

  test("""WITH 1 AS m
         |MATCH (n)
         |RETURN m AS w, n, {f: m, q: SUM(n.age)} ORDER BY {f: m, q:SUM(n.age)}""".stripMargin) {
    passes()
  }

  // This test fail semanticAnalysis but should pass
  test("""WITH 1 AS g
         |RETURN COLLECT {
         |    UNWIND [1,2,3] AS x
         |    WITH * WHERE x < 0
         |    RETURN COUNT(*)+g AS agg
         |} AS x""".stripMargin) {
    passes()
  }
}
