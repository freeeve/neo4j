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
package org.neo4j.cypher.internal.ast.factory.ddl

import org.neo4j.cypher.internal.ast.DropAuthRule
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5

class DropAuthRuleAdministrationCommandParserTest extends AdministrationAndSchemaCommandParserTestBase {

  override protected def ignorePrettifier: Boolean = true

  test("DROP AUTH RULE foo") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(DropAuthRule(
          literalFoo,
          ifExists = false
        )(pos))
    }
  }

  test("DROP AUTH RULE $foo") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(DropAuthRule(
          paramFoo,
          ifExists = false
        )(pos))
    }
  }

  test("DROP AUTH RULE ``") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(DropAuthRule(
          literalEmpty,
          ifExists = false
        )(pos))
    }
  }

  test("DROP AUTH RULE `f:oo`") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(DropAuthRule(
          literalFColonOo,
          ifExists = false
        )(pos))
    }
  }

  test("DROP AUTH RULE foo IF EXISTS") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(DropAuthRule(
          literalFoo,
          ifExists = true
        )(pos))
    }
  }

  test("DROP AUTH RULE `` IF EXISTS") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(DropAuthRule(
          literalEmpty,
          ifExists = true
        )(pos))
    }
  }

  test("DROP AUTH RULE `f:oo` IF EXISTS") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(DropAuthRule(
          literalFColonOo,
          ifExists = true
        )(pos))
    }
  }

  // fails parsing

  test("DROP AUTH RULE ") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ =>
        _.withSyntaxError("""Invalid input '': expected a parameter or an identifier (line 1, column 15 (offset: 14))
                            |"DROP AUTH RULE"
                            |               ^""".stripMargin)
    }
  }

  test("DROP AUTH RULE IF EXISTS") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ =>
        _.withSyntaxError("""Invalid input 'EXISTS': expected 'IF EXISTS' or <EOF> (line 1, column 19 (offset: 18))
                            |"DROP AUTH RULE IF EXISTS"
                            |                   ^""".stripMargin)
    }
  }

  test("DROP AUTH RULE foo IF NOT EXISTS") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.withSyntaxError("""Invalid input 'NOT': expected 'EXISTS' (line 1, column 23 (offset: 22))
                                    |"DROP AUTH RULE foo IF NOT EXISTS"
                                    |                       ^""".stripMargin)
    }
  }
}
