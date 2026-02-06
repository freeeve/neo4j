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

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast
import org.neo4j.cypher.internal.ast.GraphReference
import org.neo4j.cypher.internal.ast._
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.expressions
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.util.FunctionName
import org.neo4j.cypher.internal.util.Namespace
import org.neo4j.cypher.internal.util.symbols

class MultipleGraphClausesParsingTest extends AstParsingTestBase {

  val keywords: Seq[(String, GraphReference => ast.UseGraph)] = Seq(
    "USE" -> use,
    "USE GRAPH" -> use
  )

  val graphSelection: Seq[(String, CypherVersion => GraphReference)] = Seq(
    "foo.bar" ->
      (cypherVersion =>
        GraphDirectReference(CatalogName(List.apply("foo", "bar"), cypherVersion.equals(CypherVersion.Cypher25)))(pos)
      ),
    "(foo.bar)" ->
      (cypherVersion =>
        GraphDirectReference(CatalogName(List.apply("foo", "bar"), cypherVersion.equals(CypherVersion.Cypher25)))(pos)
      ),
    "((foo.bar))" ->
      (cypherVersion =>
        GraphDirectReference(CatalogName(List.apply("foo", "bar"), cypherVersion.equals(CypherVersion.Cypher25)))(pos)
      ),
    "foo()" ->
      (cypherVersion =>
        GraphFunctionReference(function(true, "foo")(), cypherVersion.equals(CypherVersion.Cypher25))(pos)
      ),
    "foo   (    )" ->
      (cypherVersion =>
        GraphFunctionReference(function(true, "foo")(), cypherVersion.equals(CypherVersion.Cypher25))(pos)
      ),
    "graph.foo" ->
      (cypherVersion =>
        GraphDirectReference(CatalogName(List("graph", "foo"), cypherVersion.equals(CypherVersion.Cypher25)))(pos)
      ),
    "graph.foo()" ->
      (cypherVersion =>
        GraphFunctionReference(function(true, "graph", "foo")(), cypherVersion.equals(CypherVersion.Cypher25))(pos)
      ),
    "foo.bar(baz(grok))" ->
      (cypherVersion =>
        GraphFunctionReference(
          function(true, "foo", "bar")(function(false, "baz")(varFor("grok"))),
          cypherVersion.equals(CypherVersion.Cypher25)
        )(pos)
      ),
    "foo. bar   (baz  (grok   )  )" ->
      (cypherVersion =>
        GraphFunctionReference(
          function(true, "foo", "bar")(function(false, "baz")(varFor("grok"))),
          cypherVersion.equals(CypherVersion.Cypher25)
        )(pos)
      ),
    "foo.bar(baz(grok), another.name)" ->
      (cypherVersion =>
        GraphFunctionReference(
          function(true, "foo", "bar")(
            function(false, "baz")(varFor("grok")),
            prop(varFor("another"), "name")
          ),
          cypherVersion.equals(CypherVersion.Cypher25)
        )(
          pos
        )
      ),
    "foo.bar(1, $par)" ->
      (cypherVersion =>
        GraphFunctionReference(
          function(true, "foo", "bar")(
            literalInt(1),
            parameter("par", symbols.CTAny)
          ),
          cypherVersion.equals(CypherVersion.Cypher25)
        )(pos)
      ),
    "`graph`" ->
      (cypherVersion =>
        GraphDirectReference(CatalogName(List("graph"), cypherVersion.equals(CypherVersion.Cypher25)))(pos)
      ),
    "graph1" ->
      (cypherVersion =>
        GraphDirectReference(CatalogName(List("graph1"), cypherVersion.equals(CypherVersion.Cypher25)))(pos)
      ),
    "`foo.bar.baz.baz`" ->
      (cypherVersion =>
        GraphDirectReference(CatalogName(List("foo.bar.baz.baz"), cypherVersion.equals(CypherVersion.Cypher25)))(pos)
      )
  )

  val removedInCypher25: Seq[(String, CypherVersion => GraphReference)] = Seq(
    "`foo.bar`.`baz.baz`" ->
      (cypherVersion =>
        GraphDirectReference(CatalogName(List("foo.bar", "baz.baz"), cypherVersion.equals(CypherVersion.Cypher25)))(pos)
      ),
    "`foo.bar`.baz" ->
      (cypherVersion =>
        GraphDirectReference(CatalogName(List("foo.bar", "baz"), cypherVersion.equals(CypherVersion.Cypher25)))(pos)
      ),
    "foo.`bar.baz`" ->
      (cypherVersion =>
        GraphDirectReference(CatalogName(List("foo", "bar.baz"), cypherVersion.equals(CypherVersion.Cypher25)))(pos)
      )
  )

  val fullGraphSelections: Seq[(String, CypherVersion => GraphSelection)] =
    Seq(
      "USE GRAPH graph()" -> (cypherVersion =>
        use(function(true, "graph")(), cypherVersion.equals(CypherVersion.Cypher25))
      ),
      // Interpreted as GRAPH keyword, followed by parenthesized expression
      "USE graph(x)" -> (cypherVersion => use(List.apply("x"), cypherVersion.equals(CypherVersion.Cypher25)))
    )

  val combinations: Seq[(String, CypherVersion => GraphSelection)] = for {
    (keyword, clause) <- keywords
    (input, expectedGraphReference) <- graphSelection
  } yield s"$keyword $input" -> ((version: CypherVersion) => clause(expectedGraphReference(version)))

  for {
    (input: String, expected: (CypherVersion => GraphSelection)) <- combinations ++ fullGraphSelections
  } {
    test(input) {
      parsesIn[Clause] {
        case Cypher5 => _.toAst(expected(CypherVersion.Cypher5))
        case _       => _.toAst(expected(CypherVersion.Cypher25))
      }
    }
  }

  val combinationsRemovedInCypher25: Seq[(String, CypherVersion => UseGraph)] = for {
    (keyword, clause) <- keywords
    (input, expectedGraphReference) <- removedInCypher25
  } yield s"$keyword $input" -> ((version: CypherVersion) => clause(expectedGraphReference(version)))

  for {
    (input: String, expected: (CypherVersion => GraphSelection)) <- combinationsRemovedInCypher25
  } {
    test(input) {
      parsesIn[Clause] {
        case Cypher5 => _.toAst(expected(CypherVersion.Cypher5))
        case _       => _.withAnyFailure
      }
    }
  }

  private def function(
    calledFromUseClause: Boolean,
    nameParts: String*
  )(args: expressions.Expression*): FunctionInvocation =
    expressions.FunctionInvocation(
      FunctionName(Namespace(nameParts.init.toList)(pos), nameParts.last)(pos),
      distinct = false,
      args.toIndexedSeq,
      calledFromUseClause = calledFromUseClause
    )(pos)

}
