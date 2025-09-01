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
package org.neo4j.cypher.internal.frontend.scoping

class VariableCheckerTest extends VariableCheckingTestSuite {

  /**
   * Variable not defined
   */
  test("""UNWIND [1, 2, 3] AS a
         |RETURN x""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""UNWIND [1, 2, 3] AS a
         |CREATE (n:A {p: x})""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL (a) {
         |  UNWIND [1, 2, 3] AS x
         |  RETURN b * x AS x
         |}
         |RETURN a, x""".stripMargin) {
    error("42N62", "Variable `b` not defined.")
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL (a) {
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS y
         |}
         |RETURN a, x""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""FOREACH (a IN [1, 2, 3] | CREATE (n) )
         |RETURN a""".stripMargin) {
    error("42N62", "Variable `a` not defined.")
  }

  test("""FOREACH (a IN [1, 2, 3] | CREATE (n) )
         |RETURN n""".stripMargin) {
    error("42N62", "Variable `n` not defined.")
  }

  test("""FOREACH (a IN [1, 2, 3] | CREATE (n) SET x.p = a )""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""FOREACH (a IN [1, 2, 3] | SET x.p = a )""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""LET a = 10
         |FOREACH (b IN [1, 2, 3] | CREATE (n) SET n.p = x )
         |RETURN a""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""CREATE (n {p: x})
         |RETURN n""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""CREATE (n)-[:R {p: x}]->()
         |RETURN n""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""CREATE (n)-[:R]->(), ({p: x})
         |RETURN n""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""CREATE (n {p: n.p})
         |RETURN n""".stripMargin) {
    error("42N62", "Variable `n` not defined.")
  }

  test("""CREATE (n {p: 1})-[:R]->({p: n.p})
         |RETURN n""".stripMargin) {
    error("42N62", "Variable `n` not defined.")
  }

  test("""CREATE (n {p: n.p})-[:R]->({p: 1})
         |RETURN n""".stripMargin) {
    error("42N62", "Variable `n` not defined.")
  }

  test("""CREATE (n {p: 1}), ({p: n.p})
         |RETURN n""".stripMargin) {
    error("42N62", "Variable `n` not defined.")
  }

  test("""CREATE (n)-[r:R {p: 1}]->({p: r.p})
         |RETURN n""".stripMargin) {
    error("42N62", "Variable `r` not defined.")
  }

  test("""CREATE (n)-[r:R {p: 1}]->(), ({p: r.p})
         |RETURN n""".stripMargin) {
    error("42N62", "Variable `r` not defined.")
  }

  test("""CREATE p = (n)-[r:R {p: 1}]->(), ({p: length(p)})
         |RETURN n""".stripMargin) {
    error("42N62", "Variable `p` not defined.")
  }

  test("""CREATE (n:$all("A" + x) {p: 1})""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""CREATE (n {p: 1})-[:R]->(:$all("A" + n.p) {p: 1})""".stripMargin) {
    error("42N62", "Variable `n` not defined.")
  }

  test("""RETURN 1 AS b
         |
         |NEXT
         |
         |RETURN x + 1 AS b""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""LET x = 1
         |RETURN x AS a
         |
         |NEXT
         |
         |RETURN x + 1 AS b""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""RETURN 1 AS x
         |
         |NEXT
         |
         |RETURN x AS a
         |
         |NEXT
         |
         |RETURN x + 1 AS b""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""RETURN 1 AS a
         |
         |NEXT
         |{
         |  RETURN x + 1 AS a
         |  UNION
         |  RETURN a + 2 AS a
         |}""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""RETURN 1 AS a
         |
         |NEXT
         |{
         |  RETURN a + 1 AS a
         |  UNION
         |  RETURN x + 2 AS a
         |}""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""LET a = 1
         |CALL (a) {
         |  RETURN x + 1 AS a
         |  UNION
         |  RETURN a + 2 AS a
         |}""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""LET a = 1
         |CALL (a) {
         |  RETURN a + 1 AS a
         |  UNION
         |  RETURN x + 2 AS a
         |}""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN x AS y
         |NEXT
         |WHEN y % 2 = 0 THEN RETURN x * -1 AS x
         |ELSE RETURN y AS x""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN x AS y
         |NEXT
         |WHEN y % 2 = 0 THEN {
         |  LET a = -1
         |  RETURN y * a AS x
         |}
         |ELSE RETURN y * x AS x""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN x AS y
         |NEXT
         |WHEN y % 2 = 0 THEN {
         |  LET x = -1
         |  RETURN y * x AS x
         |}
         |ELSE RETURN y * x AS x""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""LET query = "bob"
         |CALL db.index.fulltext.queryNodes("myIndex", x) YIELD node, score
         |RETURN node ORDER BY score ASCENDING LIMIT 3""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""LET query = "bob"
         |CALL db.index.fulltext.queryNodes("myIndex", query) YIELD node, score
         |RETURN x ORDER BY score ASCENDING LIMIT 3""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN SUM(x) AS s
         |  ORDER BY x ASCENDING""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN SUM(x) AS x
         |  ORDER BY x ASCENDING""".stripMargin) {
    passes()
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN a, SUM(x / a) + x * 5 AS s""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN a AS g, SUM(x / a) + g * 5 AS s""".stripMargin) {
    error("42N62", "Variable `g` not defined.")
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN a AS x, SUM(x / a) + x * 5 AS s""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  /**
   * Variable already declared
   */
  test("""LET a = 10
         |LET a = [1, a, 3]
         |RETURN a""".stripMargin) {
    error("42N59", "Variable `a` already declared.")
  }

  test("""LET a = 10
         |UNWIND [1, a, 3] AS a
         |RETURN a""".stripMargin) {
    error("42N59", "Variable `a` already declared.")
  }

  test("""RETURN 1 AS a
         |
         |NEXT
         |
         |LET a = 2
         |RETURN a + 1 AS b""".stripMargin) {
    error("42N59", "Variable `a` already declared.")
  }

  test("""LET a = 10
         |CALL (a) {
         |  LET a = a
         |  RETURN a AS y
         |}
         |RETURN *""".stripMargin) {
    error("42N59", "Variable `a` already declared.")
  }

  test("""LET a = 10
         |CALL (a) {
         |  LET a = a + 0
         |  RETURN a AS y
         |}
         |RETURN *""".stripMargin) {
    error("42N59", "Variable `a` already declared.")
  }

  test("""LET a = 10
         |CALL (a) {
         |  WITH a
         |  RETURN a AS y
         |}
         |RETURN *""".stripMargin) {
    passes()
  }

  test("""LET a = 10
         |CALL (a) {
         |  WITH a AS a
         |  RETURN a AS y
         |}
         |RETURN *""".stripMargin) {
    passes()
  }

  test("""LET n = 1
         |LET x = COUNT {
         |  LET a = 1
         |  CALL (a) {
         |    LET n = 3
         |    RETURN a + n AS result
         |  }
         |  RETURN result
         |}
         |RETURN *""".stripMargin) {
    passes()
  }

  test("""LET query = "bob"
         |CALL db.index.fulltext.queryNodes("myIndex", query) YIELD node AS n, score
         |LET n = 1
         |RETURN n ORDER BY score ASCENDING LIMIT 3""".stripMargin) {
    error("42N59", "Variable `n` already declared.")
  }

  test("""LET query = "bob"
         |CALL db.index.fulltext.queryNodes("myIndex", query) YIELD node AS n, score
         |LET node = 1
         |RETURN node ORDER BY score ASCENDING LIMIT 3""".stripMargin) {
    passes()
  }

  /**
   * Multiple return columns
   */
  test("""RETURN 1 AS a, 10 AS a""".stripMargin) {
    error("42N38", "duplicate return item name.")
  }

  test("""LET a = 10
         |RETURN a, 10 AS a""".stripMargin) {
    error("42N38", "duplicate return item name.")
  }

  test("""LET a = 10
         |WITH a, 20 AS b, 10 AS a
         |RETURN *""".stripMargin) {
    error("42N38", "duplicate return item name.")
  }
}
