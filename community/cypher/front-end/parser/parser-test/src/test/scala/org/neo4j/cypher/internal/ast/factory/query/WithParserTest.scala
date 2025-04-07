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
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.expressions.AllIterablePredicate
import org.neo4j.cypher.internal.expressions.ListLiteral

class WithParserTest extends AstParsingTestBase {

  test("WITH 1 AS a") {
    parsesTo[Clause](
      with_(
        aliasedReturnItem(literal(1), "a")
      )
    )
  }

  test("WITH DISTINCT 1 AS a, 2 AS b") {
    parsesTo[Clause](
      withDistinct(
        aliasedReturnItem(literal(1), "a"),
        aliasedReturnItem(literal(2), "b")
      )
    )
  }

  test("WITH ALL 1 AS a, 2 AS b") {
    parsesIn[Clause] {
      case Cypher5 => _.withMessageStart("Invalid input '1'")
      case _ => _.toAst(
          with_(
            aliasedReturnItem(literal(1), "a"),
            aliasedReturnItem(literal(2), "b")
          )
        )
    }
  }

  test("WITH all(x IN [1, 2, 3] WHERE x > 1) AS all") {
    parsesTo[Clause](
      with_(
        aliasedReturnItem(
          AllIterablePredicate(
            varFor("x"),
            ListLiteral(Seq(literalInt(1), literalInt(2), literalInt(3)))(pos),
            Some(greaterThan(varFor("x"), literalInt(1)))
          )(pos),
          "all"
        )
      )
    )
  }

  test("WITH ALL all(x IN [1, 2, 3] WHERE x > 1) AS all") {
    parsesIn[Clause] {
      case Cypher5 => _.withMessageStart("Invalid input 'all'")
      case _ => _.toAst(
          with_(
            aliasedReturnItem(
              AllIterablePredicate(
                varFor("x"),
                ListLiteral(Seq(literalInt(1), literalInt(2), literalInt(3)))(pos),
                Some(greaterThan(varFor("x"), literalInt(1)))
              )(pos),
              "all"
            )
          )
        )
    }
  }

  test("WITH 1 AS a, ALL 2 AS b") {
    parsesIn[Clause](_ => _.withMessageStart("Invalid input '2'"))
  }

  test("WITH 1 AS a, DISTINCT 2 AS b") {
    parsesIn[Clause](_ => _.withMessageStart("Invalid input '2'"))
  }

  test("WITH ALL DISTINCT 1 AS a, 2 AS b") {
    parsesIn[Clause]({
      case Cypher5 => _.withMessageStart("Invalid input 'DISTINCT'")
      case _       => _.withMessageStart("Invalid input '1'")
    })
  }

  test("WITH DISTINCT ALL 1 AS a, 2 AS b") {
    parsesIn[Clause](_ => _.withMessageStart("Invalid input '1'"))
  }
}
