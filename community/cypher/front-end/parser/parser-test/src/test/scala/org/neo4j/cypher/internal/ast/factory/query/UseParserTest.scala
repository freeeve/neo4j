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
package org.neo4j.cypher.internal.ast.factory.query

import org.neo4j.cypher.internal.ast.CatalogName
import org.neo4j.cypher.internal.ast.GraphDirectReference
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher25
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase

class UseParserTest extends AstParsingTestBase {

  test("USING PERIODIC COMMIT USE db LOAD CSV FROM 'url' AS line RETURN line") {
    failsParsing[Statements]
  }

  test("USE GRAPH db USING PERIODIC COMMIT LOAD CSV FROM 'url' AS line RETURN line") {
    failsParsing[Statements]
  }

  test("USE 1 RETURN 1") {
    failsParsing[Statements]
  }

  test("USE 'a' RETURN 1") {
    failsParsing[Statements]
  }

  test("USE [x] RETURN 1") {
    failsParsing[Statements]
  }

  test("USE 1 + 2 RETURN 1") {
    failsParsing[Statements]
  }

  test("CALL { USE neo4j RETURN 1 AS y } RETURN y") {
    def expected(resolveStrictly: Boolean) = {
      singleQuery(
        importingWithSubqueryCall(
          use(List("neo4j"), resolveStrictly),
          returnLit(1 -> "y")
        ),
        return_(variableReturnItem("y"))
      )
    }

    parsesIn[Statement] {
      case Cypher5 => _.toAst(expected(resolveStrictly = false))
      case _       => _.toAst(expected(resolveStrictly = true))
    }
  }

  test("WITH 1 AS x CALL { WITH x USE neo4j RETURN x AS y } RETURN x, y") {
    def expected(resolveStrictly: Boolean) = {
      singleQuery(
        with_(literal(1) as "x"),
        importingWithSubqueryCall(
          with_(variableReturnItem("x")),
          use(List("neo4j"), resolveStrictly),
          return_(varFor("x") as "y")
        ),
        return_(variableReturnItem("x"), variableReturnItem("y"))
      )
    }

    parsesIn[Statement] {
      case Cypher5 => _.toAst(expected(resolveStrictly = false))
      case _       => _.toAst(expected(resolveStrictly = true))
    }
  }

  test("USE foo UNION ALL RETURN 1") {
    parsesIn[Statement] {
      case Cypher25 => _.toAst(union(
          singleQuery(use(List("foo"), resolveStrictly = true)),
          singleQuery(return_(returnItem(literal(1), "1")))
        ).all)
      case _ => _.toAst(union(
          singleQuery(use(List("foo"), resolveStrictly = false)),
          singleQuery(return_(returnItem(literal(1), "1")))
        ).all)
    }
  }

  test("USE GRAPH neo4j RETURN 1") {
    def expected(resolveStrictly: Boolean) = {
      singleQuery(
        use(List("neo4j"), resolveStrictly),
        return_(returnItem(literal(1), "1"))
      )
    }

    parsesIn[Statement] {
      case Cypher5 => _.toAst(expected(resolveStrictly = false))
      case _       => _.toAst(expected(resolveStrictly = true))
    }
  }

  // Should be able to have database name "graph" (only works in Antlr).
  test("USE GRAPH RETURN 1") {
    def expected(resolveStrictly: Boolean) = {
      singleQuery(use(List("GRAPH"), resolveStrictly), return_(returnItem(literal(1), "1")))
    }

    parsesIn[Statement] {
      case Cypher5 => _.toAst(expected(resolveStrictly = false))
      case _       => _.toAst(expected(resolveStrictly = true))
    }
  }

  test(
    """USE db.products
      |MATCH (product)
      |RETURN product
      |UNION
      |USE db.products_bis
      |MATCH (product)
      |RETURN product""".stripMargin
  ) {

    def lhs(resolveStriclty: Boolean) = {
      singleQuery(
        use(graphReference = GraphDirectReference(CatalogName(resolveStriclty, "db", "products"))(pos)),
        match_(Seq(nodePat(Some("product"))), None),
        return_(returnItem(varFor("product"), "product"))
      )
    }

    def rhs(resolveStriclty: Boolean) = {
      singleQuery(
        use(graphReference = GraphDirectReference(CatalogName(resolveStriclty, "db", "products_bis"))(pos)),
        match_(Seq(nodePat(Some("product"))), None),
        return_(returnItem(varFor("product"), "product"))
      )
    }
    parsesIn[Statements] {
      case Cypher5 => _.toAst(Statements(Seq(union(lhs(false), rhs(false), differentReturnOrderAllowed = true))))
      case _ => _.toAstWith(
          Statements(Seq(union(lhs(true), rhs(true)))),
          prettifierRoundTrip = false // Fails the prettifier round trip, bug?
        )
    }
  }
}
