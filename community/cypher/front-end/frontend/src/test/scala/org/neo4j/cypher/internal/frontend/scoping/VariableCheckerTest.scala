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

import org.neo4j.cypher.internal.CypherVersion

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
    passes()
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL {
         |  WITH a
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |} IN TRANSACTIONS OF 2 + a ROWS ON ERROR RETRY FOR 2.5 SECONDS
         |RETURN a, x""".stripMargin) {
    passes()
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL (a) {
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY FOR a + 1 SECONDS
         |RETURN a, x""".stripMargin) {
    passes()
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS b
         |CALL {
         |  WITH a
         |  UNWIND [1, 2, 3] AS x
         |  RETURN a * x AS x
         |} IN TRANSACTIONS OF 2 + 3 ROWS ON ERROR RETRY FOR a + 1 SECONDS
         |RETURN a, x""".stripMargin) {
    passes()
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

  test("""CREATE (a:A), (a)-[r:R {p: a.t}]->(b:B {p: a.q})
         |RETURN a""".stripMargin) {
    passes(CypherVersion.Cypher5)
  }

  test("""CREATE (a:A {p: 1})-[r:R {p: 1}]->(b:B {p: 1}), (c:C)-[s:S {p: a.p+b.p+r.p}]->(d:D)
         |RETURN a""".stripMargin) {
    passes(CypherVersion.Cypher5)
  }

  test("""MATCH (n {p: n.p})
         |RETURN n""".stripMargin) {
    passes()
  }

  test("""SHOW USERS YIELD user ORDER BY bar ASCENDING""".stripMargin) {
    error("42N62", "Variable `bar` not defined.")
  }

  test("""MATCH (n {p: 1})-[r:R WHERE r.p > 1]->()
         |RETURN n""".stripMargin) {
    passes()
  }

  test("""MATCH (n {p: 1})-[:R]->({p: n.p})
         |RETURN n""".stripMargin) {
    passes()
  }

  test("""CREATE (n:$all("A" + x) {p: 1})""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
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

  test("""WITH "%s" AS g
         |CALL() {
         |    USE graph.byName(g)
         |    UNWIND [1, 2] AS i
         |    CREATE (:Number {value:i})
         |} IN TRANSACTIONS""".stripMargin) {
    error("42N62", "Variable `g` not defined.")
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN SUM(x) AS s
         |  ORDER BY x ASCENDING""".stripMargin) {
    passes()
  }

  test("""MATCH (a {name: 'Andres'})<-[:FATHER]-(child)
         |RETURN a.name, {foo: a.name='Andres', kids: collect(child.name)}""".stripMargin) {
    passes()
  }

  test("""UNWIND [1, 2, 3] AS x
         |RETURN SUM(x) AS x
         |  ORDER BY x ASCENDING""".stripMargin) {
    passes()
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN a, SUM(x / a) + x * 5 AS s""".stripMargin) {
    // Ambiguous aggregation
    passes()
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN a AS g, SUM(x / a) + g * 5 AS s""".stripMargin) {
    error("42N62", "Variable `g` not defined.")
  }

  test("""LET a = 10
         |UNWIND [1, 2, 3] AS x
         |RETURN a AS x, SUM(x / a) + x * 5 AS s""".stripMargin) {
    // Ambiguous aggregation
    passes()
  }

  test("""CALL (x) { RETURN 1 AS x } RETURN x""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""CREATE LOOKUP INDEX FOR (n) ON EACH labels(x)""".stripMargin) {
    error("42N62", "Variable `x` not defined.")
  }

  test("""CREATE FULLTEXT INDEX FOR (n:Label) ON EACH [x.prop]""".stripMargin) {
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
    passes()
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

  test("""MATCH (a:P)
         |RETURN
         |  CASE COLLECT { CALL (a) { RETURN a.p AS p } RETURN p }[0]
         |    WHEN > 2 THEN '> 2'
         |    ELSE 'else'
         |  END AS res
         |ORDER BY res""".stripMargin) {
    passes()
  }

  test(
    """MATCH SHORTEST $one (p = ((start)((a)-[r:R]->(b))+(end))
      |  WHERE length(p) > 3)
      |RETURN p""".stripMargin
  ) {
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

  test("""MATCH (p)-[]-(x)
         |MATCH p = (x)-[]-(y), x = (p)
         |RETURN p, x""".stripMargin) {
    error("42N59", "Variable `p` already declared.")
  }

  test("""MATCH p = (p)--() RETURN p""".stripMargin) {
    error("42N59", "Variable `p` already declared.")
  }

  test("""MATCH ((a)-[r]->(b))+, (b)-[c]->(d)
         |RETURN *""".stripMargin) {
    error("42N59", "Variable `b` already declared.")
  }

  test("""MATCH ((a)--(b))+, (a WHERE size([1,2]) = 2)
         |RETURN *""".stripMargin) {
    error("42N59", "Variable `a` already declared.")
  }

  test("""MATCH ((a)--(b))+, (a) WHERE size([1,2]) = 2
         |RETURN *""".stripMargin) {
    error("42N59", "Variable `a` already declared.")
  }

  test("""WITH "%s" AS g
         |CALL() {
         |  USE graph.byName(g)
         |  WITH 2 AS x
         |  RETURN *
         |}
         |RETURN x""".stripMargin) {
    error("42N62", "Variable `g` not defined.")
  }

  test("""MATCH (a)-[* {propA: a.prop, propB: b.prop}]-(b) RETURN a, b""".stripMargin) {
    passes()
  }

  test("""MATCH (a)-[*..4 {propA: a.prop, propB: b.prop}]-(b) RETURN a, b""".stripMargin) {
    passes()
  }

  test("""MATCH ((a)--(b))+, (x WHERE size(a) = 2)
         |RETURN *""".stripMargin) {
    passes()
  }

  test("""MATCH ((a)--(b))+, (x) WHERE size(a) = 2
         |RETURN *""".stripMargin) {
    passes()
  }

  test("""MATCH ((a)--(b))+, (n:$(a[0].name))
         |RETURN *;""".stripMargin) {
    passes()
  }

  test("""MATCH ((a)--(b))+, (n {prop:size(a)})
         |RETURN *""".stripMargin) {
    passes()
  }

  test("""MATCH ((a)--(b))+, (n:$(a[0].name) {prop:size(a)} WHERE size(a) = 1)
         |RETURN *""".stripMargin) {
    passes()
  }

  test("""MATCH ((a)--(b))+, (a:$(a[0].name) {prop:size(a)} WHERE size(a) = 1)
         |RETURN *""".stripMargin) {
    error("42N59", "Variable `a` already declared.")
  }

  test("""MATCH ((a)-[r]-(b))+, ()-[x:$(r[0].name) {prop:size(r)} WHERE size(r) = 1]->()
         |RETURN *""".stripMargin) {
    passes()
  }

  test("""MATCH ((a)-[r]-(b))+, ()-[r:$(r[0].name) {prop:size(r)} WHERE size(r) = 1]->()
         |RETURN *""".stripMargin) {
    error("42N59", "Variable `r` already declared.")
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

  test("""WITH 1 AS x
         |CALL {
         |  USE mega.graph1
         |  WITH 2 AS x
         |  RETURN *
         |}
         |RETURN x""".stripMargin) {
    error(
      "42N07",
      "The variable `x` is shadowing a variable with the same name from the outer scope and needs to be renamed."
    )
  }

  test("""WITH 1 AS x
         |CALL () {
         |  USE mega.graph1
         |  WITH 2 AS x
         |  RETURN *
         |}
         |RETURN x""".stripMargin) {
    error(
      "42N07",
      "The variable `x` is shadowing a variable with the same name from the outer scope and needs to be renamed."
    )
  }

  test("""WITH "%s" AS g
         |CALL(g) {
         |  USE graph.byName(g)
         |  WITH 2 AS x
         |  RETURN *
         |}
         |RETURN x""".stripMargin) {
    passes()
  }

  test("""MATCH (a)
         |RETURN EXISTS {
         |  MATCH (b)
         |  LET a = b
         |  RETURN a
         |}""".stripMargin) {
    error(
      "42N07",
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed."
    )
  }

  test("""MATCH (a)
         |RETURN EXISTS {
         |  MATCH (b)
         |  RETURN b
         |  NEXT
         |  MATCH (b)
         |  RETURN b AS a
         |}""".stripMargin) {
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

  test("""WHEN true THEN CREATE()
         |WHEN false THEN RETURN 2 AS x
         |ELSE FINISH""".stripMargin) {
    error("42N3A", "incompatible conditional query.")
  }

  test("""WHEN true THEN RETURN 2 AS y, 3 AS x
         |WHEN false THEN RETURN 2 AS x, 5 AS y""".stripMargin) {
    passes()
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

  test("""MATCH (p:Person)
         |WHERE COUNT { MATCH (p) RETURN * } = 1
         |RETURN p.name AS name""".stripMargin) {
    passes()
  }

  test(
    """WITH 1 AS a
      |RETURN COUNT { RETURN * } AS x""".stripMargin
  ) {
    passes()
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
   * Incompatible return columns
   */

  test(
    """CALL () {
      |  RETURN 1 as x, 2 AS y
      |  UNION
      |  RETURN 3 AS y, 2 as x
      |}
      |RETURN x, y""".stripMargin
  ) {
    passes()
  }

  test(
    """CALL () {
      |  RETURN 1 as x, 2 AS y
      |  UNION
      |  RETURN 3 AS y, 2 as z
      |}
      |RETURN x, y""".stripMargin
  ) {
    error(
      "42N39",
      "error: syntax error or access rule violation - incompatible return column names. All UNION subqueries must have the same return column names. Use `AS` to ensure columns have the same name."
    )
  }

  test(
    """CALL db.labels() YIELD label
      |UNION
      |CALL db.labels() YIELD label
      |RETURN label""".stripMargin
  ) {
    error(
      "42N39",
      "error: syntax error or access rule violation - incompatible return column names. All UNION subqueries must have the same return column names. Use `AS` to ensure columns have the same name."
    )
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

  test("""MATCH ((a)-[e]->(b)-[f]->(a))+(p)-[g]->(r)-[q]->(s)
         |RETURN a, b, p, r, s""".stripMargin) {
    passes()
  }

  test("""MATCH ((a)-[b]->(c))* (d)-[e]->() ((a)-[f]->(g)){2,}
         |RETURN *""".stripMargin) {
    error("42N59", "Variable `a` already declared.")
  }

  test("""MATCH ((a)-[b]->(c))*(d), (f)-[e]->(a)
         |RETURN *""".stripMargin) {
    error("42N59", "Variable `a` already declared.")
  }

  test("""MATCH (a)-->(b)
         |MATCH (x)--(y)((a)-->(t)){1,5}()-->(z)
         |RETURN *""".stripMargin) {
    error("42N59", "Variable `a` already declared.")
  }

  test("""MATCH (a:A) MATCH p = ( ()--() WHERE EXISTS { (a) } )+
         |RETURN length(p) AS l""".stripMargin) {
    passes()
  }

  test("""MATCH (a)-->(b)
         |MATCH (x)--(y)((s)-->(t WHERE a.p = 1)){1,5}()-->(z)
         |RETURN *;""".stripMargin) {
    passes()
  }

  test("""CALL test.my.proc(null) YIELD a, b AS a
         |RETURN a""".stripMargin) {
    error("42N59", "Variable `a` already declared.")
  }

  test("""CALL test.my.proc(null) YIELD a AS c, b AS c
         |RETURN c""".stripMargin) {
    error("42N59", "Variable `c` already declared.")
  }

  test(
    """CREATE (n), (n)
      |RETURN 1 as one""".stripMargin
  ) {
    error("42N59", "Variable `n` already declared.")
  }

  test(
    """CREATE (n), (n)-[:R]->(n)
      |RETURN 1 as one""".stripMargin
  ) {
    passes()
  }

  test(
    """CREATE (n)-[:R]->(n)
      |RETURN 1 as one""".stripMargin
  ) {
    passes()
  }

  test(
    """MATCH (n), (n)
      |RETURN 1 as one""".stripMargin
  ) {
    passes()
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

  test("""MATCH (a)
         |CALL (*) {
         |  MATCH (b)
         |  RETURN b AS a
         |  UNION
         |  MATCH (a)
         |  RETURN a
         |}
         |RETURN *""".stripMargin) {
    error(
      "42N07",
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed."
    )
  }

  test("""MATCH (a)
         |CALL (a) {
         |  MATCH (b)
         |  RETURN b AS a
         |  UNION
         |  MATCH (a)
         |  RETURN a
         |}
         |RETURN *""".stripMargin) {
    error(
      "42N07",
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed."
    )
  }

  test("""MATCH (a)
         |RETURN COLLECT {
         |  MATCH (a)
         |  RETURN a
         |  UNION
         |  MATCH (a)
         |  RETURN a
         |}""".stripMargin) {
    passes()
  }

  test("""CREATE (this0:Movie)
         |CALL {
         |  WITH *
         |  RETURN this0 AS x
         |}
         |RETURN 1 AS data""".stripMargin) {
    passes()
  }

  test("SHOW TRANSACTIONS foo") {
    error("42N62", "Variable `foo` not defined.")
  }

  test("SHOW TRANSACTIONS YIELD nope") {
    error("42N62", "Variable `nope` not defined.")
  }

  test("SHOW TRANSACTIONS YIELD connectionId") {
    passes()
  }

  test("SHOW TRANSACTIONS WHERE database <> 'system'") {
    passes()
  }

  test("SHOW TRANSACTIONS") {
    passes()
  }

  test("""SHOW TRANSACTION 'neo4j-transaction-2'
         |YIELD transactionId
         |TERMINATE TRANSACTION transactionId
         |YIELD message, transactionId AS txId, username
         |RETURN *""".stripMargin) {
    passes()
  }

  test("""SHOW TRANSACTION
         |YIELD transactionId AS name
         |SHOW PROCEDURES
         |YIELD name
         |RETURN *""".stripMargin) {
    error("42N59", "Variable `name` already declared.")
  }

  test("""SHOW TRANSACTION
         |YIELD transactionId AS name
         |SHOW PROCEDURES
         |YIELD mode AS name
         |RETURN *""".stripMargin) {
    error("42N59", "Variable `name` already declared.")
  }

  test("""SHOW INDEXES
         |YIELD type, entityType
         |SHOW RANGE INDEXES
         |YIELD name AS range
         |RETURN *
         |ORDER BY type, entityType""".stripMargin) {
    passes()
  }

  test("""CREATE CONSTRAINT FOR (n:Label) REQUIRE n.prop IS UNIQUE""".stripMargin) {
    passes()
  }

  test("""CREATE INDEX FOR (n:Person) ON (n.firstName)""".stripMargin) {
    passes()
  }

  test("""WITH 1 AS x
         |RETURN x AS name
         |LIMIT x""".stripMargin) {
    passes()
  }

  test("""WITH 1 AS x
         |RETURN x AS name
         |SKIP x""".stripMargin) {
    passes()
  }

  test("""FOREACH (x IN [1] | FOREACH(x IN [1] | CREATE() ))""") {
    passes()
  }

  test("""WITH 1 AS x CALL { WITH x USE graph.byName(x) RETURN 2 AS y } RETURN x""") {
    passes()
  }
}
