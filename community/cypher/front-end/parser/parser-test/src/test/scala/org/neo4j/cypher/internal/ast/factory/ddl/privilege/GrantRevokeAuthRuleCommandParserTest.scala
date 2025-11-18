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
package org.neo4j.cypher.internal.ast.factory.ddl.privilege

import org.neo4j.cypher.internal.ast.GrantRolesToAuthRules
import org.neo4j.cypher.internal.ast.GrantRolesToUsers
import org.neo4j.cypher.internal.ast.RevokeRolesFromAuthRules
import org.neo4j.cypher.internal.ast.RevokeRolesFromUsers
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5

class GrantRevokeAuthRuleCommandParserTest extends DbmsPrivilegeAdministrationCommandParserTest {

  // <editor-fold desc="GRANT AUTH happy-path">

  test("GRANT ROLE reader TO AUTH RULE rule") {
    parsesIn[Statements] {
      case Cypher5 =>
        _.withSyntaxError(
          """Invalid input 'RULE': expected ',' or <EOF> (line 1, column 27 (offset: 26))
            |"GRANT ROLE reader TO AUTH RULE rule"
            |                           ^""".stripMargin
        )
      case _ =>
        _.toAst(Statements(Seq(
          GrantRolesToAuthRules(
            Seq(literalString("reader")),
            Seq(literalString("rule"))
          )(pos)
        )))
    }
  }

  // Should be able to handle keywords as names
  test("GRANT ROLE ROLE TO AUTH RULE AUTH") {
    parsesIn[Statements] {
      case Cypher5 =>
        _.withSyntaxError(
          """Invalid input 'RULE': expected ',' or <EOF> (line 1, column 25 (offset: 24))
            |"GRANT ROLE ROLE TO AUTH RULE AUTH"
            |                         ^""".stripMargin
        )
      case _ =>
        _.toAst(Statements(Seq(
          GrantRolesToAuthRules(
            Seq(literalString("ROLE")),
            Seq(literalString("AUTH"))
          )(pos)
        )))
    }
  }

  // Should interpret keywords as a username (for backwards compatibility)
  Seq("AUTH", "ROLE", "RULE").foreach(keyword =>
    test(s"GRANT ROLE ROLE TO $keyword") {
      parsesTo[Statements](Statements(Seq(
        GrantRolesToUsers(
          Seq(literalString("ROLE")),
          Seq(literalString(keyword))
        )(pos)
      )))
    }
  )

  test("GRANT ROLES $reader TO AUTH RULE $rule") {
    parsesIn[Statements] {
      case Cypher5 =>
        _.withSyntaxError(
          """Invalid input 'RULE': expected ',' or <EOF> (line 1, column 29 (offset: 28))
            |"GRANT ROLES $reader TO AUTH RULE $rule"
            |                             ^""".stripMargin
        )
      case _ =>
        _.toAst(Statements(Seq(
          GrantRolesToAuthRules(
            Seq(stringParam("reader")),
            Seq(stringParam("rule"))
          )(pos)
        )))
    }
  }

  test("GRANT ROLE $readerParam, readerLiteral TO AUTH RULES ruleLiteral, $ruleParam") {
    parsesIn[Statements] {
      case Cypher5 =>
        _.withSyntaxError(
          """Invalid input 'RULES': expected ',' or <EOF> (line 1, column 48 (offset: 47))
            |"GRANT ROLE $readerParam, readerLiteral TO AUTH RULES ruleLiteral, $ruleParam"
            |                                                ^""".stripMargin
        )
      case _ =>
        _.toAst(Statements(Seq(
          GrantRolesToAuthRules(
            Seq(stringParam("readerParam"), literalString("readerLiteral")),
            Seq(literalString("ruleLiteral"), stringParam("ruleParam"))
          )(pos)
        )))
    }
  }

  // </editor-fold>

  // <editor-fold desc="REVOKE AUTH happy-path">

  test("REVOKE ROLE reader FROM AUTH RULE rule") {
    parsesIn[Statements] {
      case Cypher5 =>
        _.withSyntaxError(
          """Invalid input 'RULE': expected ',' or <EOF> (line 1, column 30 (offset: 29))
            |"REVOKE ROLE reader FROM AUTH RULE rule"
            |                              ^""".stripMargin
        )
      case _ =>
        _.toAst(Statements(Seq(
          RevokeRolesFromAuthRules(
            Seq(literalString("reader")),
            Seq(literalString("rule"))
          )(pos)
        )))
    }
  }

  // Should be able to handle keywords as names
  test("REVOKE ROLE ROLE FROM AUTH RULE RULE") {
    parsesIn[Statements] {
      case Cypher5 =>
        _.withSyntaxError(
          """Invalid input 'RULE': expected ',' or <EOF> (line 1, column 28 (offset: 27))
            |"REVOKE ROLE ROLE FROM AUTH RULE RULE"
            |                            ^""".stripMargin
        )
      case _ =>
        _.toAst(Statements(Seq(
          RevokeRolesFromAuthRules(
            Seq(literalString("ROLE")),
            Seq(literalString("RULE"))
          )(pos)
        )))
    }
  }

  // Should interpret keywords as a username (for backwards compatibility)
  Seq("AUTH", "ROLE", "RULE").foreach(keyword =>
    test(s"REVOKE ROLE ROLE FROM $keyword") {
      parsesTo[Statements](Statements(Seq(
        RevokeRolesFromUsers(
          Seq(literalString("ROLE")),
          Seq(literalString(keyword))
        )(pos)
      )))
    }
  )

  test("REVOKE ROLES $reader FROM AUTH RULE $rule") {
    parsesIn[Statements] {
      case Cypher5 =>
        _.withSyntaxError(
          """Invalid input 'RULE': expected ',' or <EOF> (line 1, column 32 (offset: 31))
            |"REVOKE ROLES $reader FROM AUTH RULE $rule"
            |                                ^""".stripMargin
        )
      case _ =>
        _.toAst(Statements(Seq(
          RevokeRolesFromAuthRules(
            Seq(stringParam("reader")),
            Seq(stringParam("rule"))
          )(pos)
        )))
    }
  }

  test("REVOKE ROLE $readerParam, readerLiteral FROM AUTH RULES ruleLiteral, $ruleParam") {
    parsesIn[Statements] {
      case Cypher5 =>
        _.withSyntaxError(
          """Invalid input 'RULES': expected ',' or <EOF> (line 1, column 51 (offset: 50))
            |"REVOKE ROLE $readerParam, readerLiteral FROM AUTH RULES ruleLiteral, $ruleParam"
            |                                                   ^""".stripMargin
        )
      case _ =>
        _.toAst(Statements(Seq(
          RevokeRolesFromAuthRules(
            Seq(stringParam("readerParam"), literalString("readerLiteral")),
            Seq(literalString("ruleLiteral"), stringParam("ruleParam"))
          )(pos)
        )))
    }
  }

  // </editor-fold>

  // <editor-fold desc="GRANT AUTH invalid scenarios">
  test(s"GRANT ROLE foo FROM AUTH RULE abc") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'FROM': expected ',' or 'TO' (line 1, column 16 (offset: 15))
        |"GRANT ROLE foo FROM AUTH RULE abc"
        |                ^""".stripMargin
    )
  }

  test(s"GRANT ROLE foo TO AUTH abc") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxError(
          """Invalid input 'abc': expected ',' or <EOF> (line 1, column 24 (offset: 23))
            |"GRANT ROLE foo TO AUTH abc"
            |                        ^""".stripMargin
        )
      case _ => _.withSyntaxError(
          """Invalid input 'abc': expected ',', 'RULE', 'RULES' or <EOF> (line 1, column 24 (offset: 23))
            |"GRANT ROLE foo TO AUTH abc"
            |                        ^""".stripMargin
        )
    }
  }

  // Globbing not supported for roles
  test(s"GRANT ROLES * TO AUTH RULE abc") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input '*': expected a parameter or an identifier (line 1, column 13 (offset: 12))
        |"GRANT ROLES * TO AUTH RULE abc"
        |             ^""".stripMargin
    )
  }

  // Globbing not supported for auth rules
  test(s"GRANT ROLES foo TO AUTH RULE *") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxError(
          // 'AUTH' is interpreted as a username here
          """Invalid input 'RULE': expected ',' or <EOF> (line 1, column 25 (offset: 24))
            |"GRANT ROLES foo TO AUTH RULE *"
            |                         ^""".stripMargin
        )
      case _ => _.withSyntaxError(
          """Invalid input '*': expected a parameter or an identifier (line 1, column 30 (offset: 29))
            |"GRANT ROLES foo TO AUTH RULE *"
            |                              ^""".stripMargin
        )
    }
  }

  // </editor-fold>

  // <editor-fold desc="REVOKE AUTH invalid scenarios">

  test(s"REVOKE ROLE foo TO AUTH RULE abc") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'TO': expected ',' or 'FROM' (line 1, column 17 (offset: 16))
        |"REVOKE ROLE foo TO AUTH RULE abc"
        |                 ^""".stripMargin
    )
  }

  test(s"REVOKE ROLE foo FROM AUTH abc") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxError(
          """Invalid input 'abc': expected ',' or <EOF> (line 1, column 27 (offset: 26))
            |"REVOKE ROLE foo FROM AUTH abc"
            |                           ^""".stripMargin
        )
      case _ => _.withSyntaxError(
          """Invalid input 'abc': expected ',', 'RULE', 'RULES' or <EOF> (line 1, column 27 (offset: 26))
            |"REVOKE ROLE foo FROM AUTH abc"
            |                           ^""".stripMargin
        )
    }
  }

  // Globbing not supported for roles
  test(s"REVOKE ROLES * FROM AUTH RULE abc") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input '*': expected a parameter or an identifier (line 1, column 14 (offset: 13))
        |"REVOKE ROLES * FROM AUTH RULE abc"
        |              ^""".stripMargin
    )
  }

  // Globbing not supported for auth rules
  test(s"REVOKE ROLES foo FROM AUTH RULE *") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxError(
          """Invalid input 'RULE': expected ',' or <EOF> (line 1, column 28 (offset: 27))
            |"REVOKE ROLES foo FROM AUTH RULE *"
            |                            ^""".stripMargin
        )
      case _ => _.withSyntaxError(
          """Invalid input '*': expected a parameter or an identifier (line 1, column 33 (offset: 32))
            |"REVOKE ROLES foo FROM AUTH RULE *"
            |                                 ^""".stripMargin
        )
    }
  }

  // </editor-fold>

}
