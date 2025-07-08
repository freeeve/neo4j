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

import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.CollectExpression
import org.neo4j.cypher.internal.ast.DefaultWith
import org.neo4j.cypher.internal.ast.ExistsExpression
import org.neo4j.cypher.internal.ast.ParsedAsFilter
import org.neo4j.cypher.internal.ast.ParsedAsOrderBy
import org.neo4j.cypher.internal.ast.SingleQuery
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.ast.test.util.Parsers
import org.neo4j.cypher.internal.ast.test.util.Parses
import org.neo4j.cypher.internal.expressions.DecimalDoubleLiteral
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.FunctionName
import org.neo4j.cypher.internal.expressions.Namespace
import org.neo4j.cypher.internal.util.ASTNode

import scala.reflect.ClassTag

class FilterParserTest extends AstParsingTestBase {

  val predicates: Seq[(String, Expression)] = Seq(
    ("true", trueLiteral),
    ("1 = 1", equals(literalInt(1), literalInt(1))),
    ("a.prop > 5", greaterThan(prop(varFor("a"), "prop"), literalInt(5))),
    (
      "cos(0.5) < a.prop",
      lessThan(
        FunctionInvocation(
          functionName = FunctionName(namespace = Namespace(parts = List())(pos), name = "cos")(pos),
          distinct = false,
          args = Vector(DecimalDoubleLiteral(stringVal = "0.5")(pos))
        )(pos),
        prop(varFor("a"), "prop")
      )
    ),
    (
      "2 = COLLECT { MATCH (a)-->(b) RETURN b }",
      equals(
        literalInt(2),
        CollectExpression(
          singleQuery(
            match_(relationshipChain(nodePat(Some("a")), relPat(), nodePat(Some("b")))),
            return_(variableReturnItem("b"))
          )
        )(pos, None, None)
      )
    ),
    (
      "EXISTS { FILTER false }",
      ExistsExpression(singleQuery(withAllTyped(Some(where(falseLiteral)), ParsedAsFilter)))(pos, None, None)
    )
  )
  val keywords: Seq[String] = Seq("FILTER", "FILTER WHERE")

  def parsesFilter[T <: ASTNode : ClassTag](expected: T, offset: Int)(implicit p: Parsers[T]): Parses[T] =
    parsesIn[T] {
      case Cypher5 => _.withSyntaxErrorContaining(
          "Invalid input 'FILTER': expected"
        ).withSyntaxErrorContaining(s"(line 1, column ${offset + 1} (offset: $offset))")
      case _ => _.toAst(expected)
    }

  for {
    (cypherExpression, expectedExpression) <- predicates
    filter <- keywords
  } yield {
    test(s"$filter $cypherExpression") {
      parsesFilter[Clause](
        withAllTyped(Some(where(expectedExpression)), ParsedAsFilter),
        0
      )
    }
    test(s"MATCH (a) $filter $cypherExpression RETURN a") {
      parsesFilter[Statements](
        Statements(Seq(SingleQuery(Seq(
          match_(Seq(nodePat(Some("a"))), None),
          withAllTyped(Some(where(expectedExpression)), ParsedAsFilter),
          return_(variableReturnItem("a"))
        ))(pos))),
        10
      )
    }
    test(s"MATCH (a) WHERE true $filter $cypherExpression RETURN a") {
      parsesFilter[Statements](
        Statements(Seq(SingleQuery(Seq(
          match_(Seq(nodePat(Some("a"))), Some(where(trueLiteral))),
          withAllTyped(Some(where(expectedExpression)), ParsedAsFilter),
          return_(variableReturnItem("a"))
        ))(pos))),
        21
      )
    }
    test(s"MATCH (a) WITH * WHERE true $filter $cypherExpression RETURN a") {
      parsesFilter[Statements](
        Statements(Seq(SingleQuery(Seq(
          match_(Seq(nodePat(Some("a"))), None),
          withAllTyped(Some(where(trueLiteral)), DefaultWith),
          withAllTyped(Some(where(expectedExpression)), ParsedAsFilter),
          return_(variableReturnItem("a"))
        ))(pos))),
        28
      )
    }
    test(s"MATCH (a) $filter true WITH * WHERE $cypherExpression RETURN a") {
      parsesFilter[Statements](
        Statements(Seq(SingleQuery(Seq(
          match_(Seq(nodePat(Some("a"))), None),
          withAllTyped(Some(where(trueLiteral)), ParsedAsFilter),
          withAllTyped(Some(where(expectedExpression)), DefaultWith),
          return_(variableReturnItem("a"))
        ))(pos))),
        10
      )
    }
    test(s"MATCH (a) ORDER BY a.prop OFFSET 1 LIMIT 1 $filter $cypherExpression RETURN a") {
      parsesFilter[Statements](
        Statements(Seq(SingleQuery(Seq(
          match_(Seq(nodePat(Some("a"))), None),
          withAllTyped(
            Some(orderBy(sortItem(prop(varFor("a"), "prop")))),
            Some(offset(1)),
            Some(limit(1)),
            ParsedAsOrderBy
          ),
          withAllTyped(Some(where(expectedExpression)), ParsedAsFilter),
          return_(variableReturnItem("a"))
        ))(pos))),
        43
      )
    }
    test(s"MATCH (a) $filter $cypherExpression ORDER BY a.prop OFFSET 1 LIMIT 1 RETURN a") {
      parsesFilter[Statements](
        Statements(Seq(SingleQuery(Seq(
          match_(Seq(nodePat(Some("a"))), None),
          withAllTyped(Some(where(expectedExpression)), ParsedAsFilter),
          withAllTyped(
            Some(orderBy(sortItem(prop(varFor("a"), "prop")))),
            Some(offset(1)),
            Some(limit(1)),
            ParsedAsOrderBy
          ),
          return_(variableReturnItem("a"))
        ))(pos))),
        10
      )
    }
  }
}
