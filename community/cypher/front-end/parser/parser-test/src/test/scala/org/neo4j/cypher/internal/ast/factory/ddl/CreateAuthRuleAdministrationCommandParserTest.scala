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

import org.neo4j.cypher.internal.ast.AuthRuleCondition
import org.neo4j.cypher.internal.ast.AuthRuleEnabled
import org.neo4j.cypher.internal.ast.CreateAuthRule
import org.neo4j.cypher.internal.ast.IfExistsDoNothing
import org.neo4j.cypher.internal.ast.IfExistsInvalidSyntax
import org.neo4j.cypher.internal.ast.IfExistsReplace
import org.neo4j.cypher.internal.ast.IfExistsThrowError
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.expressions.And
import org.neo4j.cypher.internal.expressions.Equals
import org.neo4j.cypher.internal.expressions.ExplicitParameter
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.FunctionName
import org.neo4j.cypher.internal.expressions.MapExpression
import org.neo4j.cypher.internal.expressions.Namespace
import org.neo4j.cypher.internal.expressions.NotEquals
import org.neo4j.cypher.internal.expressions.Or
import org.neo4j.cypher.internal.expressions.SignedDecimalIntegerLiteral
import org.neo4j.cypher.internal.expressions.StringLiteral
import org.neo4j.cypher.internal.util.symbols.AnyType

class CreateAuthRuleAdministrationCommandParserTest extends AdministrationAndSchemaCommandParserTestBase {

  override protected def ignorePrettifier: Boolean = true

  private val validExpressions = Seq(
    Equals(literalInt(1), literalInt(1))(pos),
    NotEquals(
      literalInt(1),
      ExplicitParameter("param", AnyType(isNullable = true)(pos))(pos)
    )(pos),
    Equals(
      FunctionInvocation(
        FunctionName(Namespace(List("abac", "oidc"))(pos), "user_attribute")(pos),
        StringLiteral("country")(pos)
      )(pos),
      StringLiteral("SE")(pos)
    )(pos),
    NotEquals(
      FunctionInvocation(
        FunctionName(Namespace(List("abac", "oidc"))(pos), "user_attribute")(pos),
        ExplicitParameter("param", AnyType(isNullable = true)(pos))(pos)
      )(pos),
      MapExpression(Seq(
        (propName("key"), listOfString("value1", "value2")),
        (propName("key2"), literalInt(1))
      ))(pos)
    )(pos)
  )

  private val complexValidAndOrExpression =
    validExpressions.tail.foldLeft[Expression](validExpressions.head)((acc, expression) =>
      Or(And(acc, expression)(pos), expression)(pos)
    )

  (validExpressions :+ complexValidAndOrExpression)
    .map(e => (ExpressionStringifier().apply(e), AuthRuleCondition(e)(pos)))
    .foreach { case (exprString, authRuleCondition) =>
      test(s"CREATE AUTH RULE foo SET CONDITION $exprString") {
        parsesIn[Statement] {
          case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'RULE'")
          case _ => _.toAst(CreateAuthRule(
              literalFoo,
              IfExistsThrowError,
              List(authRuleCondition)
            )(pos))
        }
      }

      test(s"CREATE AUTH RULE $$foo SET CONDITION $exprString") {
        parsesIn[Statement] {
          case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'RULE'")
          case _ => _.toAst(CreateAuthRule(
              paramFoo,
              IfExistsThrowError,
              List(authRuleCondition)
            )(pos))
        }
      }

      test(s"CREATE OR REPLACE AUTH RULE foo SET CONDITION $exprString") {
        parsesIn[Statement] {
          case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
          case _ => _.toAst(CreateAuthRule(
              literalFoo,
              IfExistsReplace,
              List(authRuleCondition)
            )(pos))
        }
      }

      test(s"CREATE OR REPLACE AUTH RULE $$foo SET CONDITION $exprString") {
        parsesIn[Statement] {
          case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
          case _ => _.toAst(CreateAuthRule(
              paramFoo,
              IfExistsReplace,
              List(authRuleCondition)
            )(pos))
        }
      }

      test(s"CREATE AUTH RULE foo SET CONDITION $exprString SET ENABLED FALSE") {
        parsesIn[Statement] {
          case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'RULE'")
          case _ => _.toAst(CreateAuthRule(
              literalFoo,
              IfExistsThrowError,
              List(
                authRuleCondition,
                AuthRuleEnabled(enabled = false)(pos)
              )
            )(pos))
        }
      }

      test(s"CREATE AUTH RULE $$foo SET CONDITION $exprString SET ENABLED TRUE") {
        parsesIn[Statement] {
          case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'RULE'")
          case _ => _.toAst(CreateAuthRule(
              paramFoo,
              IfExistsThrowError,
              List(
                authRuleCondition,
                AuthRuleEnabled(enabled = true)(pos)
              )
            )(pos))
        }
      }
    }

  // failure scenarios

  test("CREATE AUTH RULE foo") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'RULE'")
      case _       => _.withSyntaxErrorContaining("Invalid input '': expected 'SET'")
    }
  }

  test("CREATE OR REPLACE AUTH RULE foo") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _       => _.withSyntaxErrorContaining("Invalid input '': expected 'SET'")
    }
  }

  test("CREATE AUTH RULE foo IF NOT EXISTS") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'RULE'")
      case _       => _.withSyntaxErrorContaining("Invalid input '': expected 'SET'")
    }
  }

  test("CREATE AUTH RULE foo SET ENABLED $param") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'RULE'")
      case _       => _.withSyntaxErrorContaining("Invalid input '$': expected 'FALSE' or 'TRUE'")
    }
  }

  // pass, should fail in semantic checking

  test("CREATE AUTH RULE foo SET ENABLED true") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'RULE'")
      case _ => _.toAst(CreateAuthRule(
          literalFoo,
          IfExistsThrowError,
          List(AuthRuleEnabled(enabled = true)(pos))
        )(pos))
    }
  }

  test("CREATE AUTH RULE $foo SET ENABLED false") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'RULE'")
      case _ => _.toAst(CreateAuthRule(
          paramFoo,
          IfExistsThrowError,
          List(AuthRuleEnabled(enabled = false)(pos))
        )(pos))
    }
  }

  test("CREATE AUTH RULE foo SET ENABLED true SET ENABLED false") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'RULE'")
      case _ => _.toAst(CreateAuthRule(
          literalFoo,
          IfExistsThrowError,
          List(
            AuthRuleEnabled(enabled = true)(pos),
            AuthRuleEnabled(enabled = false)(pos)
          )
        )(pos))
    }
  }

  test("CREATE AUTH RULE foo SET CONDITION 1 = 1 SET CONDITION 2 = 2") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'RULE'")
      case _ => _.toAst(CreateAuthRule(
          literalFoo,
          IfExistsThrowError,
          List(
            AuthRuleCondition(Equals(
              literalInt(1),
              literalInt(1)
            )(pos))(pos),
            AuthRuleCondition(Equals(
              SignedDecimalIntegerLiteral("2")(pos),
              SignedDecimalIntegerLiteral("2")(pos)
            )(pos))(pos)
          )
        )(pos))
    }
  }

  test("CREATE AUTH RULE foo IF NOT EXISTS SET ENABLED true") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'RULE'")
      case _ => _.toAst(CreateAuthRule(
          literalFoo,
          IfExistsDoNothing,
          List(AuthRuleEnabled(enabled = true)(pos))
        )(pos))
    }
  }

  test("CREATE OR REPLACE AUTH RULE foo IF NOT EXISTS SET CONDITION 1 = 1") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'AUTH'")
      case _ => _.toAst(CreateAuthRule(
          literalFoo,
          IfExistsInvalidSyntax,
          List(AuthRuleCondition(Equals(
            literalInt(1),
            literalInt(1)
          )(pos))(pos))
        )(pos))
    }
  }

  test("CREATE AUTH RULE foo SET CONDITION $param") {
    parsesIn[Statement] {
      case Cypher5 => _.withSyntaxErrorContaining("Invalid input 'RULE'")
      case _ => _.toAst(CreateAuthRule(
          literalFoo,
          IfExistsThrowError,
          List(
            AuthRuleCondition(
              ExplicitParameter("param", AnyType(isNullable = true)(pos))(pos)
            )(pos)
          )
        )(pos))
    }
  }
}
