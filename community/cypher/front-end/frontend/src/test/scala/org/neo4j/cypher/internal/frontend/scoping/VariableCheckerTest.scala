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
         |CALL () {
         |  UNWIND [1, 2, 3] AS x
         |  RETURN b * x AS x
         |}
         |RETURN a, x""".stripMargin) {
    error("42N62", "Variable `b` not defined.")
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL {
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
         |  RETURN b * x AS x
         |}
         |RETURN a, x""".stripMargin) {
    error("42N62", "Variable `b` not defined.")
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL {
         |  WITH a
         |  UNWIND [1, 2, 3] AS x
         |  RETURN b * x AS x
         |}
         |RETURN a, x""".stripMargin) {
    error("42N62", "Variable `b` not defined.")
  }

  test("""LET a = 10
         |CALL {
         |  WITH a
         |  WITH 2 AS b
         |  RETURN a AS x
         |}
         |RETURN a, x""".stripMargin) {
    error("42N62", "Variable `a` not defined.")
  }

  test("""LET a = 10
         |CALL {
         |  WITH a
         |  WITH 2 AS a
         |  RETURN a AS y
         |}
         |RETURN *""".stripMargin) {
    passes()
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL (a) {
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |} IN TRANSACTIONS OF 2 + a ROWS ON ERROR RETRY FOR 2.5 SECONDS
         |RETURN a, x""".stripMargin) {
    error("42N62", "Variable `a` not defined.")
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL {
         |  WITH a
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |} IN TRANSACTIONS OF 2 + a ROWS ON ERROR RETRY FOR 2.5 SECONDS
         |RETURN a, x""".stripMargin) {
    error("42N62", "Variable `a` not defined.")
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL (a) {
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY FOR a + 1 SECONDS
         |RETURN a, x""".stripMargin) {
    error("42N62", "Variable `a` not defined.")
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL {
         |  WITH a
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY FOR a + 1 SECONDS
         |RETURN a, x""".stripMargin) {
    error("42N62", "Variable `a` not defined.")
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

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL {
         |  WITH a
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

  test("""MATCH (n {p: n.p})
         |RETURN n""".stripMargin) {
    passes()
  }

  test("""CREATE (n {p: 1})-[:R]->({p: n.p})
         |RETURN n""".stripMargin) {
    error("42N62", "Variable `n` not defined.")
  }

  test("""MATCH (n {p: 1})-[:R]->({p: n.p})
         |RETURN n""".stripMargin) {
    passes()
  }

  test("""MATCH (n {p: 1})-[r:R WHERE r.p > 1]->()
         |RETURN n""".stripMargin) {
    passes()
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

  test("""LET x = 1
         |CALL (x) {
         |  RETURN x AS a
         |
         |  NEXT
         |
         |  RETURN x + 1 AS b, c AS a, a AS c
         |}
         |RETURN b, a, c""".stripMargin) {
    error("42N62", "Variable `c` not defined.")
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

  test("""CALL (x) { RETURN 1 AS x } RETURN x""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test(
    """UNWIND [1, 2, 3] AS a
      |CALL (a) {
      |  CALL (a, b) {
      |    RETURN 1 AS x
      |  }
      |  RETURN x
      |}
      |RETURN x""".stripMargin
  ) {
    error("42N62", "Variable `b` not defined.")
  }

  test("""MATCH (a)-[r]->+(b)
         |WHERE allReduce(acc = 0, rel IN r | acc + rel.prop, rel.prop = 5)
         |RETURN a, b""".stripMargin) {
    error("42N62", "Variable `rel` not defined.")
  }

  test(
    """MATCH (n)-[:REL]->(d)
      |WHERE any(prefix in b WHERE n.name = prefix)
      |RETURN n.name""".stripMargin
  ) {
    error("42N62", "Variable `b` not defined.")
  }

  test(
    """MATCH (n)-[:REL]->(d)
      |WHERE any(prefix in ["a", "b", "c"] WHERE b.name = prefix)
      |RETURN n.name""".stripMargin
  ) {
    error("42N62", "Variable `b` not defined.")
  }

  test(
    """MATCH (n)-[:REL]->(d)
      |WHERE any(d in ["a", "b", "c"] WHERE n.name = d)
      |RETURN n.name""".stripMargin
  ) {
    passes()
  }

  test(
    """MATCH (n)-[:REL]->(d)
      |WHERE any(prefix in ["a", "b", "c"] WHERE n.name = prefix)
      |RETURN n.name""".stripMargin
  ) {
    passes()
  }

  test("""MATCH (n)
         |RETURN [x=(n)-->() | head(nodes(x))] AS p""".stripMargin) {
    passes()
  }

  test("""MATCH (n)
         |RETURN [(n)-->() | n.name] AS p""".stripMargin) {
    passes()
  }

  test("""WITH 1 AS x
         |MATCH (a:A WHERE a.prop > x)-[r]-(b:B)
         |RETURN a, r, b""".stripMargin) {
    passes()
  }

  test("""WITH 1 AS x
         |RETURN [(a:A WHERE a.prop > x)-[r]-(b:B) | a.prop] AS result""".stripMargin) {
    passes()
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
         |CALL {
         |  WITH a
         |  LET a = a
         |  RETURN a AS y
         |}
         |RETURN *""".stripMargin) {
    error("42N59", "Variable `a` already declared.")
  }

  test("""LET a = 10
         |CALL {
         |  WITH a
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
         |CALL {
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

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL (a) {
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |} IN TRANSACTIONS REPORT STATUS AS b
         |RETURN a, x""".stripMargin) {
    error("42N59", "Variable `b` already declared.")
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL {
         |  WITH a
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |} IN TRANSACTIONS REPORT STATUS AS b
         |RETURN a, x""".stripMargin) {
    error("42N59", "Variable `b` already declared.")
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
   * Shadowing variable in outer scope
   */

  test("""LET a = 1
         |CALL (a) {
         |  RETURN x + 1 AS a
         |  UNION
         |  RETURN a + 2 AS a
         |}""".stripMargin) {
    error(
      "42N07",
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed."
    )
  }

  test("""LET a = 1
         |CALL (a) {
         |  RETURN a + 1 AS a
         |  UNION
         |  RETURN x + 2 AS a
         |}""".stripMargin) {
    error(
      "42N07",
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed."
    )
  }

  test("""LET a = 10
         |CALL (a) {
         |  LET a = a
         |  RETURN a AS y
         |}
         |RETURN *""".stripMargin) {
    error(
      "42N07",
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed."
    )
  }

  test("""LET a = 10
         |CALL (a) {
         |  LET a = a + 0
         |  RETURN a AS y
         |}
         |RETURN *""".stripMargin) {
    error(
      "42N07",
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed."
    )
  }

  test("""LET a = 10
         |CALL (a) {
         |  LET b = a
         |  RETURN b AS a
         |}
         |RETURN *""".stripMargin) {
    error(
      "42N07",
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed."
    )
  }

  test("""LET a = 10
         |CALL {
         |  WITH a
         |  LET b = a
         |  RETURN b AS a
         |}
         |RETURN *""".stripMargin) {
    error(
      "42N07",
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed."
    )
  }

  test("""LET a = 10
         |CALL () {
         |  RETURN 20 AS a
         |}
         |RETURN *""".stripMargin) {
    error(
      "42N07",
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed."
    )
  }

  test("""LET a = 10
         |CALL {
         |  RETURN 20 AS a
         |}
         |RETURN *""".stripMargin) {
    error(
      "42N07",
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed."
    )
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

  /**
   * Incompatible return columns
   */
  test("""RETURN 1 AS x
         |UNION
         |RETURN 2 AS y""".stripMargin) {
    error("42N39", "incompatible return column names.")
  }

  test("""WHEN true THEN RETURN 1 AS x
         |WHEN false THEN RETURN 2 AS x
         |ELSE RETURN 3 AS y""".stripMargin) {
    error("42N39", "incompatible return column names.")
  }

  test("""WHEN true THEN RETURN 1 AS x
         |WHEN false THEN RETURN 2 AS y
         |ELSE RETURN 3 AS z""".stripMargin) {
    error("42N39", "incompatible return column names.")
  }

  test("""WHEN true THEN RETURN 1 AS x, 2 AS y
         |WHEN false THEN RETURN 2 AS x
         |ELSE RETURN 2 AS x, 3 AS y""".stripMargin) {
    error("42N3B", "incompatible number of return columns.")
  }

  test("""WHEN true THEN RETURN 1 AS x
         |WHEN false THEN RETURN 2 AS x
         |ELSE FINISH""".stripMargin) {
    error("42N3A", "incompatible conditional query.")
  }

  /**
   * Invalid use of RETURN *
   */
  test("""RETURN *, 1 AS x""".stripMargin) {
    error("42I37", "'RETURN *' is not allowed when there are no variables in scope.")
  }

  test("""MATCH () RETURN *, 1 AS x""".stripMargin) {
    error("42I37", "'RETURN *' is not allowed when there are no variables in scope.")
  }

  test("""WITH *
         |RETURN *""".stripMargin) {
    error("42I37", "'RETURN *' is not allowed when there are no variables in scope.")
  }

  /**
   * Relationship already bound
   */
  test("""WITH [] AS r LIMIT 1
         |MATCH p = shortestPath((src)-[r*]->(dst))
         |RETURN src, dst""".stripMargin) {
    error("42N66", "relationship variable already bound")
  }

  /**
   * Pattern checks
   */

  test("""CREATE (a:A {name: 'a'}),
         |       (b1:X {name: 'b1'})
         |CREATE (a)-[:KNOWS]->(b1)""".stripMargin) {
    passes()
  }

  test(
    """CREATE (:A)-[m:R {p:'hello'}]->(:B)
      |CREATE (c:C {t:type(m), p:m.p}) RETURN m, c""".stripMargin
  ) {
    passes()
  }

  test("""UNWIND [1, 2] as i
         |CREATE (:A)
         |CREATE (n:B {id: i, count: COUNT { MATCH (:A) } })
         |RETURN n""".stripMargin) {
    passes()
  }

  test("""UNWIND range(1, 10) AS i
         |CALL {
         |  WITH i
         |  UNWIND [1, 2] AS j
         |  CREATE (n:N {i: i, j: j})
         |} IN TRANSACTIONS
         |  OF 10 ROWS
         |  ON ERROR FAIL""".stripMargin) {
    passes()
  }

  test(
    """CREATE ()-[:R]->()""".stripMargin
  ) {
    passes()
  }

  test("""MATCH ()-[r]->()
         |CREATE ()-[r]->()""".stripMargin) {
    error("42N59", "Variable `r` already declared.")
  }

  test("""MATCH ()-[r:R]->()
         |CREATE ()-[:R]->()-[r]->()""".stripMargin) {
    error("42N59", "Variable `r` already declared.")
  }

  test("""MATCH (n)
         |CREATE (n)-[r:R]->(n)""".stripMargin) {
    passes()
  }

  test("""MATCH (n)
         |CREATE (n)""".stripMargin) {
    error("42N59", "Variable `n` already declared.")
  }

  test("""MATCH (n)
         |CREATE ({p:n.p})""".stripMargin) {
    passes()
  }

  test("""MATCH (n)
         |MERGE ({p:n.p})""".stripMargin) {
    passes()
  }

  test("""MATCH (n)
         |MERGE (n {p:n.p})""".stripMargin) {
    error("42N59", "Variable `n` already declared.")
  }

  test("""FOREACH(v IN [null] | CREATE ({property: v}))""".stripMargin) {
    passes()
  }

  test("""FOREACH(x IN [1, 2, 3] | MERGE ({property: x}))""".stripMargin) {
    passes()
  }

  test("""UNWIND [1, 2, 3] AS i
         |WITH i ORDER BY i DESC
         |CALL (i) {
         |  MATCH (n {value: i})
         |  CREATE (m {value: i - 1})
         |  FINISH
         |}
         |RETURN count(*) as count""".stripMargin) {
    passes()
  }

}
