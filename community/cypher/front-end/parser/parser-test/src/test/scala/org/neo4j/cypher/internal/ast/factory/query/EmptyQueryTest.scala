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
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.ast.test.util.Parses

import scala.util.Random

class EmptyQueryTest extends AstParsingTestBase {
  val rand = new Random()

  test("empty statements") {
    Parses(parseAst[Statements]("")).withSyntaxErrorContaining(invalidInput())
  }

  test("empty statements with whitespace") {
    val whitespaces = Seq(
      " ",
      "\t",
      "\n",
      "\r",
      "\u0009",
      "\u000B",
      "\u000C",
      "\u001C",
      "\u001D",
      "\u001E",
      "\u001F",
      "\u0020",
      "\u00A0",
      "\u1680",
      "\u2000",
      "\u2001",
      "\u2002",
      "\u2003",
      "\u2004",
      "\u2005",
      "\u2006",
      "\u2007",
      "\u2008",
      "\u2009",
      "\u200A",
      "\u2028",
      "\u2029",
      "\u202F",
      "\u205F",
      "\u3000"
    )
    for {
      whitespace <- whitespaces
    } {
      Parses(parseAst[Statements](whitespace)).withSyntaxErrorContaining(invalidInput())
    }
    // sample combinations
    for {
      sampleIx <- 0 until 100
    } {
      val whitespace = rand.shuffle(whitespaces).take(rand.nextInt(whitespaces.size - 2) + 2).mkString
      Parses(parseAst[Statements](whitespace)).withSyntaxErrorContaining(invalidInput())
    }
  }

  test("empty statements with comment") {
    Parses(parseAst[Statements](" // foo ")).withSyntaxErrorContaining(invalidInput())
    Parses(parseAst[Statements](" /* foo */ ")).withSyntaxErrorContaining(invalidInput())
    Parses(parseAst[Statements](" /* foo // bar  */")).withSyntaxErrorContaining(invalidInput())
    Parses(parseAst[Statements](" /* foo */ // bar ")).withSyntaxErrorContaining(invalidInput())
    Parses(parseAst[Statements](" // foo    // bar ")).withSyntaxErrorContaining(invalidInput())
    Parses(parseAst[Statements](" // foo */ /* bar */")).withSyntaxErrorContaining(invalidInput())
  }

  test("empty statements with semicolon") {
    Parses(parseAst[Statements](";")).withSyntaxErrorContaining(invalidInput(";"))
  }

  test("empty statements with two semicolon") {
    Parses(parseAst[Statements]("; ;")).withSyntaxErrorContaining(invalidInput(";"))
  }

  test("empty statements with FINISH and two semicolon") {
    Parses(parseAst[Statements]("FINISH ; ;")).withSyntaxErrorContaining(invalidInput(";"))
  }

  test("empty statements with periodic commit hint") {
    Parses(parseAst[Statements]("USING PERIODIC COMMIT")).in {
      case Cypher5 => _.withSyntaxErrorContaining(
          "The PERIODIC COMMIT query hint is no longer supported. Please use CALL { ... } IN TRANSACTIONS instead."
        )
      case _ => _.withSyntaxErrorContaining(invalidInput("USING"))
    }
  }

  test("empty clause") {
    Parses(parseAst[Clause]("")).withSyntaxErrorContaining(invalidInput())
  }

  def invalidInput(text: String = ""): String = s"Invalid input '$text': expected"
}
