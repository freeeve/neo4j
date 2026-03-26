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

import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase

class ForListClauseParserTest extends AstParsingTestBase {

  test("FOR x IN [1, 2, 3] RETURN x") {
    parsesIn[Statements] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'FOR'")
      case _ => _.toAst(Statements(Seq(singleQuery(
          unwind(listOfInt(1, 2, 3), varFor("x"), useForInSyntax = true),
          return_(variableReturnItem("x"))
        ))))
    }
  }

  test("FOR x IN n.items RETURN x") {
    parsesIn[Statements] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'FOR'")
      case _ => _.toAst(Statements(Seq(singleQuery(
          unwind(prop(varFor("n"), "items"), varFor("x"), useForInSyntax = true),
          return_(variableReturnItem("x"))
        ))))
    }
  }

  test("FOR [1, 2, 3] RETURN 1") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'FOR'")
      case _       => _.withSyntaxErrorContaining("Invalid input '['")
    }
  }

  test("FOR [1, 2, 3] IN x RETURN x") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'FOR'")
      case _       => _.withSyntaxErrorContaining("Invalid input '['")
    }
  }

  test("FOR [1, 2, 3] AS x RETURN x") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'FOR'")
      case _       => _.withSyntaxErrorContaining("Invalid input '['")
    }
  }

  test("FOR x RETURN 1") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'FOR'")
      case _       => _.withSyntaxErrorContaining("Invalid input 'RETURN'")
    }
  }

  test("FOR x IN RETURN 1") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'FOR'")
      case _       => _.withSyntaxErrorContaining("Invalid input '1'")
    }
  }

  test("FOREACH [1, 2, 3] IN x RETURN 1") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input '['")
      case _       => _.withSyntaxErrorContaining("Invalid input '['")
    }
  }

  test("FOR `EACH` IN [1, 2, 3] RETURN EACH") {
    parsesIn[Statements] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'FOR'")
      case _ => _.toAst(Statements(Seq(singleQuery(
          unwind(listOfInt(1, 2, 3), varFor("EACH"), useForInSyntax = true),
          return_(variableReturnItem("EACH"))
        ))))
    }
  }

  test("FOR FOR IN IN RETURN FOR") {
    parsesIn[Statements] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'FOR'")
      case _ => _.toAst(Statements(Seq(singleQuery(
          unwind(varFor("IN"), varFor("FOR"), useForInSyntax = true),
          return_(variableReturnItem("FOR"))
        ))))
    }
  }

  test("FOR IN IN IN RETURN IN") {
    parsesIn[Statements] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'FOR'")
      case _ => _.toAst(Statements(Seq(singleQuery(
          unwind(varFor("IN"), varFor("IN"), useForInSyntax = true),
          return_(variableReturnItem("IN"))
        ))))
    }
  }

  test("FOR IN IN FOR RETURN FOR") {
    parsesIn[Statements] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'FOR'")
      case _ => _.toAst(Statements(Seq(singleQuery(
          unwind(varFor("FOR"), varFor("IN"), useForInSyntax = true),
          return_(variableReturnItem("FOR"))
        ))))
    }
  }

  test("FOR EACH IN AS RETURN EACH") {
    parsesIn[Statements] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'FOR'")
      case _ => _.toAst(Statements(Seq(singleQuery(
          unwind(varFor("AS"), varFor("EACH"), useForInSyntax = true),
          return_(variableReturnItem("EACH"))
        ))))
    }
  }
}
