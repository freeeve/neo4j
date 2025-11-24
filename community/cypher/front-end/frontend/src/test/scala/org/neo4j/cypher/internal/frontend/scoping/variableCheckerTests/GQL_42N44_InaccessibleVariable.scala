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
package org.neo4j.cypher.internal.frontend.scoping.variableCheckerTests

import org.neo4j.cypher.internal.frontend.scoping.VariableCheckingTestSuite

/**
 * Test for 42I58 - Invalid Entity Reference
 *
 */
class GQL_42N44_InaccessibleVariable extends VariableCheckingTestSuite {

  // Aggregation

  test(
    """UNWIND [1, 2, 3] AS x
      |RETURN SUM(x) AS s ORDER BY x ASCENDING""".stripMargin
  ) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `x` declared before the RETURN clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |RETURN n.name, SUM(n.age) ORDER BY n""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `n` declared before the RETURN clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |RETURN n.name, SUM(n.age) ORDER BY n.name, n""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `n` declared before the RETURN clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |RETURN n.name, SUM(n.age) ORDER BY n.name, n.age""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `n` declared before the RETURN clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |CALL (n) {
         |  WITH n AS m
         |  RETURN SUM(n.age) AS ages ORDER BY m.name
         |}
         |RETURN n, ages""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `m` declared before the RETURN clause when using `DISTINCT` or an aggregation."
    )
  }

  test(
    """UNWIND [1, 2, 3] AS x
      |WITH SUM(x) AS s ORDER BY x ASCENDING
      |RETURN s""".stripMargin
  ) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `x` declared before the WITH clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |WITH n.name AS m, SUM(n.age) AS sum ORDER BY n
         |RETURN m, sum""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `n` declared before the WITH clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |CALL (n) {
         |  WITH n AS m
         |  WITH SUM(n.age) AS ages ORDER BY m.name
         |  RETURN ages
         |}
         |RETURN n, ages""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `m` declared before the WITH clause when using `DISTINCT` or an aggregation."
    )
  }

  test(
    """UNWIND [1, 2, 3] AS x
      |WITH SUM(x) AS s WHERE x = 1
      |RETURN s""".stripMargin
  ) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `x` declared before the WITH clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |WITH n.name AS m, SUM(n.age) AS sum WHERE n.rate = 1
         |RETURN m, sum""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `n` declared before the WITH clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |CALL (n) {
         |  WITH n AS m
         |  WITH SUM(n.age) AS ages WHERE m.name = "a"
         |  RETURN ages
         |}
         |RETURN n, ages""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `m` declared before the WITH clause when using `DISTINCT` or an aggregation."
    )
  }

  // Distinct

  test(
    """UNWIND [1, 2, 3] AS x
      |RETURN DISTINCT s ORDER BY x ASCENDING""".stripMargin
  ) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `x` declared before the RETURN clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |RETURN DISTINCT n.name ORDER BY n""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `n` declared before the RETURN clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |CALL (n) {
         |  WITH n AS m
         |  RETURN DISTINCT n.age AS ages ORDER BY m.name
         |}
         |RETURN n, ages""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `m` declared before the RETURN clause when using `DISTINCT` or an aggregation."
    )
  }

  test(
    """UNWIND [1, 2, 3] AS x
      |WITH DISTINCT s ORDER BY x ASCENDING
      |RETURN s""".stripMargin
  ) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `x` declared before the WITH clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |WITH DISTINCT n.name AS m ORDER BY n
         |RETURN m""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `n` declared before the WITH clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |CALL (n) {
         |  WITH n AS m
         |  WITH DISTINCT n.age AS ages ORDER BY m.name
         |  RETURN ages
         |}
         |RETURN n, ages""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `m` declared before the WITH clause when using `DISTINCT` or an aggregation."
    )
  }

  test(
    """UNWIND [1, 2, 3] AS x
      |WITH DISTINCT s WHERE x = 1
      |RETURN s""".stripMargin
  ) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `x` declared before the WITH clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |WITH DISTINCT n.name AS m WHERE n.age = 1
         |RETURN m""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `n` declared before the WITH clause when using `DISTINCT` or an aggregation."
    )
  }

  test("""MATCH (n)
         |CALL (n) {
         |  WITH n AS m
         |  WITH DISTINCT n.age AS ages WHERE m.name = "a"
         |  RETURN ages
         |}
         |RETURN n, ages""".stripMargin) {
    errorAllVersions(
      "42N44",
      "It is not possible to access the variable `m` declared before the WITH clause when using `DISTINCT` or an aggregation."
    )
  }
  // Positive tests

  test("""MATCH (n)
         |RETURN n, SUM(n.age) ORDER BY n""".stripMargin) {
    passes()
  }

  test("""WITH 1 AS m
         |MATCH (n)
         |RETURN m AS w, n, SUM(n.age) ORDER BY m""".stripMargin) {
    passes()
  }

  test("""WITH 1 AS m
         |MATCH (n)
         |RETURN m AS w, n, SUM(n.age) ORDER BY w""".stripMargin) {
    passes()
  }

  test("""MATCH (n)
         |RETURN *, SUM(n.age) ORDER BY n""".stripMargin) {
    passes()
  }

  test("""MATCH (n)
         |RETURN *, 1 AS n, SUM(n.age) ORDER BY n""".stripMargin) {
    passes()
  }

  test("""MATCH (n)
         |RETURN n, SUM(n.age) ORDER BY n.name""".stripMargin) {
    passes()
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN SUM(x) AS x
         |  ORDER BY x ASCENDING""".stripMargin) {
    passes()
  }

  test("""MATCH (n)
         |CALL (n) {
         |  RETURN n AS m, SUM(n.age) AS ages ORDER BY n.name
         |}
         |RETURN n, ages""".stripMargin) {
    passes()
  }

  test("""MATCH (n)
         |CALL (n) {
         |  RETURN SUM(n.age) AS ages ORDER BY n.name
         |}
         |RETURN n, ages""".stripMargin) {
    passes()
  }

  test("WITH 1 AS x WHERE x = 1 RETURN x") {
    passes()
  }

  test("WITH 1 AS x ORDER BY x RETURN x") {
    passes()
  }

  test("RETURN 1 AS x ORDER BY x RETURN x") {
    passes()
  }

  test(
    """UNWIND [1, 2, 3] AS x
      |RETURN SUM(x) AS s ORDER BY s ASCENDING""".stripMargin
  ) {
    passes()
  }

  test("""MATCH (n)
         |RETURN n.name, SUM(n.age) ORDER BY n.name""".stripMargin) {
    passes()
  }

}
