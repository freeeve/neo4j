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

import org.neo4j.cypher.internal.ast.RenameAuthRule
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5

class RenameAuthRuleAdministrationCommandParserTest extends AdministrationAndSchemaCommandParserTestBase {

  override protected def ignorePrettifier: Boolean = true

  test("RENAME AUTH RULE foo TO bar") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(RenameAuthRule(
          literalFoo,
          literalBar,
          ifExists = false
        )(pos))
    }
  }

  test("RENAME AUTH RULE $foo TO $bar") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(RenameAuthRule(
          paramFoo,
          paramBar,
          ifExists = false
        )(pos))
    }
  }

  test("RENAME AUTH RULE `` TO ``") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(RenameAuthRule(
          literalEmpty,
          literalEmpty,
          ifExists = false
        )(pos))
    }
  }

  test("RENAME AUTH RULE `f:oo` TO `f:oo`") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(RenameAuthRule(
          literalFColonOo,
          literalFColonOo,
          ifExists = false
        )(pos))
    }
  }

  test("RENAME AUTH RULE foo IF EXISTS TO bar") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(RenameAuthRule(
          literalFoo,
          literalBar,
          ifExists = true
        )(pos))
    }
  }

  test("RENAME AUTH RULE `` IF EXISTS TO ``") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(RenameAuthRule(
          literalEmpty,
          literalEmpty,
          ifExists = true
        )(pos))
    }
  }

  test("RENAME AUTH RULE `f:oo` IF EXISTS TO `f:oo`") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(RenameAuthRule(
          literalFColonOo,
          literalFColonOo,
          ifExists = true
        )(pos))
    }
  }

  // fails parsing

  test("RENAME AUTH RULE ") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ =>
        _.withSyntaxError("""Invalid input '': expected a parameter or an identifier (line 1, column 17 (offset: 16))
                            |"RENAME AUTH RULE"
                            |                 ^""".stripMargin)
    }
  }

  test("RENAME AUTH RULE foo") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ =>
        _.withSyntaxError("""Invalid input '': expected 'IF EXISTS' or 'TO' (line 1, column 21 (offset: 20))
                            |"RENAME AUTH RULE foo"
                            |                     ^""".stripMargin)
    }
  }

  test("RENAME AUTH RULE foo IF EXISTS") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ =>
        _.withSyntaxError("""Invalid input '': expected 'TO' (line 1, column 31 (offset: 30))
                            |"RENAME AUTH RULE foo IF EXISTS"
                            |                               ^""".stripMargin)
    }
  }

  test("RENAME AUTH RULE IF EXISTS TO BAR") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ =>
        _.withSyntaxError("""Invalid input 'EXISTS': expected 'IF EXISTS' or 'TO' (line 1, column 21 (offset: 20))
                            |"RENAME AUTH RULE IF EXISTS TO BAR"
                            |                     ^""".stripMargin)
    }
  }

  test("RENAME AUTH RULE foo IF NOT EXISTS TO BAR") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.withSyntaxError("""Invalid input 'NOT': expected 'EXISTS' (line 1, column 25 (offset: 24))
                                    |"RENAME AUTH RULE foo IF NOT EXISTS TO BAR"
                                    |                         ^""".stripMargin)
    }
  }
}
