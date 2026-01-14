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

import org.neo4j.cypher.internal.ast.ShowCurrentGraphTypeClause
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.Parses
import org.neo4j.cypher.internal.util.symbols.IntegerType
import org.neo4j.gqlstatus.GqlStatusInfoCodes

/* Tests for listing the current graph type */
class ShowCurrentGraphTypeCommandParserTest extends AdministrationAndSchemaCommandParserTestBase {

  private val cypher5Error: Parses[Statements] => Parses[Statements] = _.withSyntaxErrorContaining(
    "Invalid input ",
    GqlStatusInfoCodes.STATUS_42I06,
    "error: syntax error or access rule violation - invalid input. Invalid input 'GRAPH', expected: 'USER'."
  )

  // General tests

  test("SHOW CURRENT GRAPH TYPE") {
    assertAst(
      singleQuery(ShowCurrentGraphTypeClause(None, List.empty, yieldAll = false, None)(defaultPos)),
      supportedInCypher5 = false
    )
  }

  test("USE db SHOW CURRENT GRAPH TYPE") {
    assertAst(
      singleQuery(
        use(List("db"), resolveStrictly = true),
        ShowCurrentGraphTypeClause(None, List.empty, yieldAll = false, None)(pos)
      ),
      supportedInCypher5 = false
    )
  }

  // Filtering

  test("SHOW CURRENT GRAPH TYPE WHERE true") {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(Some(where(literalBoolean(true))), List.empty, yieldAll = false, None)(defaultPos)
      ),
      supportedInCypher5 = false
    )
  }

  test("SHOW CURRENT GRAPH TYPE WHERE something = 'somethingElse'") {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(
          Some(where(equals(varFor("something"), literalString("somethingElse")))),
          List.empty,
          yieldAll = false,
          None
        )(defaultPos)
      ),
      supportedInCypher5 = false
    )
  }

  test("SHOW CURRENT GRAPH TYPE YIELD column") {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(
          None,
          List(commandResultItem("column")),
          yieldAll = false,
          Some(withFromYield(returnAllItems.withDefaultOrderOnColumns(List("column"))))
        )(defaultPos)
      ),
      supportedInCypher5 = false
    )
  }

  test("SHOW CURRENT GRAPH TYPE YIELD type AS CURRENT, specification AS GRAPH") {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(
          None,
          List(commandResultItem("type", Some("CURRENT")), commandResultItem("specification", Some("GRAPH"))),
          yieldAll = false,
          Some(withFromYield(returnAllItems.withDefaultOrderOnColumns(List("CURRENT", "GRAPH"))))
        )(defaultPos)
      ),
      supportedInCypher5 = false
    )
  }

  test("SHOW CURRENT GRAPH TYPE YIELD *") {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(None, List.empty, yieldAll = true, Some(withFromYield(returnAllItems)))(defaultPos)
      ),
      supportedInCypher5 = false
    )
  }

  test("SHOW CURRENT GRAPH TYPE YIELD * ORDER BY specification OFFSET 2 LIMIT 5") {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(
          None,
          List.empty,
          yieldAll = true,
          Some(withFromYield(
            returnAllItems,
            Some(orderBy(sortItem(varFor("specification")))),
            Some(skip(2)),
            Some(limit(5))
          ))
        )(defaultPos)
      ),
      supportedInCypher5 = false
    )
  }

  test(
    "USE db SHOW CURRENT GRAPH TYPE YIELD type, specification AS spec WHERE spec CONTAINS 'CONSTRAINT' RETURN type"
  ) {
    assertAst(
      singleQuery(
        use(List("db"), resolveStrictly = true),
        ShowCurrentGraphTypeClause(
          None,
          List(commandResultItem("type"), commandResultItem("specification", Some("spec"))),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("type", "spec")),
            where = Some(where(contains(varFor("spec"), literalString("CONSTRAINT"))))
          ))
        )(pos),
        return_(variableReturnItem("type"))
      ),
      supportedInCypher5 = false
    )
  }

  test(
    "USE db SHOW CURRENT GRAPH TYPE YIELD type, specification AS spec ORDER BY type SKIP 2 LIMIT 5 WHERE type = 'OPEN' RETURN spec"
  ) {
    assertAst(
      singleQuery(
        use(List("db"), resolveStrictly = true),
        ShowCurrentGraphTypeClause(
          None,
          List(commandResultItem("type"), commandResultItem("specification", Some("spec"))),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("type", "spec")),
            Some(orderBy(sortItem(varFor("type")))),
            Some(skip(2)),
            Some(limit(5)),
            Some(where(equals(varFor("type"), literalString("OPEN"))))
          ))
        )(pos),
        return_(variableReturnItem("spec"))
      ),
      supportedInCypher5 = false
    )
  }

  test("SHOW CURRENT GRAPH TYPE YIELD a AS b ORDER BY b WHERE b = 1") {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(
          None,
          List(commandResultItem("a", Some("b"))),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("b")),
            Some(orderBy(sortItem(varFor("b")))),
            where = Some(where(equals(varFor("b"), literalInt(1))))
          ))
        )(defaultPos)
      ),
      supportedInCypher5 = false
    )
  }

  test("SHOW CURRENT GRAPH TYPE YIELD a AS b ORDER BY a WHERE a = 1") {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(
          None,
          List(commandResultItem("a", Some("b"))),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("b")),
            Some(orderBy(sortItem(varFor("b")))),
            where = Some(where(equals(varFor("b"), literalInt(1))))
          ))
        )(defaultPos)
      ),
      supportedInCypher5 = false
    )
  }

  test("SHOW CURRENT GRAPH TYPE YIELD a ORDER BY EXISTS { (b) } WHERE EXISTS { (b) }") {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(
          None,
          List(commandResultItem("a")),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("a")),
            Some(orderBy(sortItem(simpleExistsExpression(patternForMatch(nodePat(Some("b"))), None)))),
            where = Some(where(simpleExistsExpression(patternForMatch(nodePat(Some("b"))), None)))
          ))
        )(defaultPos)
      ),
      supportedInCypher5 = false
    )
  }

  test("SHOW CURRENT GRAPH TYPE YIELD a AS b ORDER BY EXISTS { (b) } WHERE COLLECT { MATCH (a) RETURN a } <> []") {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(
          None,
          List(commandResultItem("a", Some("b"))),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("b")),
            Some(orderBy(sortItem(simpleExistsExpression(patternForMatch(nodePat(Some("b"))), None)))),
            where = Some(where(notEquals(
              simpleCollectExpression(
                patternForMatch(nodePat(Some("b"))),
                None,
                return_(returnItem(varFor("b"), "a"))
              ),
              listOf()
            )))
          ))
        )(defaultPos)
      ),
      supportedInCypher5 = false
    )
  }

  test("SHOW CURRENT GRAPH TYPE YIELD a AS b ORDER BY b + COUNT { () } WHERE b OR EXISTS { () }") {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(
          None,
          List(commandResultItem("a", Some("b"))),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("b")),
            Some(orderBy(sortItem(add(varFor("b"), simpleCountExpression(patternForMatch(nodePat()), None))))),
            where = Some(where(or(varFor("b"), simpleExistsExpression(patternForMatch(nodePat()), None))))
          ))
        )(defaultPos)
      ),
      supportedInCypher5 = false
    )
  }

  test(
    "SHOW CURRENT GRAPH TYPE YIELD a AS b ORDER BY a + EXISTS { () } WHERE a OR ALL (x IN [1, 2] WHERE x IS :: INT)"
  ) {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(
          None,
          List(commandResultItem("a", Some("b"))),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("b")),
            Some(orderBy(sortItem(add(varFor("b"), simpleExistsExpression(patternForMatch(nodePat()), None))))),
            where = Some(where(or(
              varFor("b"),
              allInList(
                varFor("x"),
                listOfInt(1, 2),
                isTyped(varFor("x"), IntegerType(isNullable = true)(pos))
              )
            )))
          ))
        )(defaultPos)
      ),
      supportedInCypher5 = false
    )
  }

  test(
    "SHOW CURRENT GRAPH TYPE " +
      "YIELD type AS specification, specification AS type " +
      "WHERE specification CONTAINS 'CONSTRAINT' " +
      "RETURN type AS specification"
  ) {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(
          None,
          List(commandResultItem("type", Some("specification")), commandResultItem("specification", Some("type"))),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("specification", "type")),
            where = Some(where(contains(varFor("specification"), literalString("CONSTRAINT"))))
          ))
        )(defaultPos),
        return_(aliasedReturnItem("type", "specification"))
      ),
      supportedInCypher5 = false
    )
  }

  test(
    "SHOW CURRENT GRAPH TYPE YIELD name RETURN name ORDER BY name"
  ) {
    assertAst(
      singleQuery(
        ShowCurrentGraphTypeClause(
          None,
          List(commandResultItem("name")),
          yieldAll = false,
          Some(withFromYield(returnAllItems.withDefaultOrderOnColumns(List("name"))))
        )(pos),
        return_(orderBy(sortItem(varFor("name"))), variableReturnItem("name"))
      ),
      comparePosition = false,
      supportedInCypher5 = false
    )
  }

  // Negative tests

  test("SHOW GRAPH TYPE") {
    // Missing CURRENT
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxErrorContaining(
          "Invalid input 'GRAPH': expected 'ALIAS', 'ALIASES', 'ALL', 'BTREE', 'CONSTRAINT', 'CONSTRAINTS', " +
            "'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'EXISTS', 'FULLTEXT', " +
            "'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', " +
            "'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', " +
            "'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', " +
            "'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'CURRENT USER', 'USERS' or 'VECTOR' (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. " +
            "Invalid input 'GRAPH', expected: 'ALIAS', 'ALIASES', 'ALL', 'BTREE', 'CONSTRAINT', 'CONSTRAINTS', " +
            "'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'EXISTS', 'FULLTEXT', " +
            "'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', " +
            "'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', " +
            "'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', " +
            "'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'CURRENT USER', 'USERS' or 'VECTOR'."
        )
      case _ => _.withSyntaxErrorContaining(
          "Invalid input 'GRAPH': expected 'ALIAS', 'ALIASES', 'ALL', 'AUTH', 'CONSTRAINT', 'CONSTRAINTS', 'CURRENT', " +
            "'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'FULLTEXT', " +
            "'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', " +
            "'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', " +
            "'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', " +
            "'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'USERS' or 'VECTOR' (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. " +
            "Invalid input 'GRAPH', expected: 'ALIAS', 'ALIASES', 'ALL', 'AUTH', 'CONSTRAINT', 'CONSTRAINTS', 'CURRENT', " +
            "'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'FULLTEXT', " +
            "'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', " +
            "'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', " +
            "'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', " +
            "'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'USERS' or 'VECTOR'."
        )
    }
  }

  test("SHOW GRAPH TYPES") {
    // Missing CURRENT
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxErrorContaining(
          "Invalid input 'GRAPH': expected 'ALIAS', 'ALIASES', 'ALL', 'BTREE', 'CONSTRAINT', 'CONSTRAINTS', " +
            "'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'EXISTS', 'FULLTEXT', " +
            "'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', " +
            "'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', " +
            "'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', " +
            "'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'CURRENT USER', 'USERS' or 'VECTOR' (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. " +
            "Invalid input 'GRAPH', expected: 'ALIAS', 'ALIASES', 'ALL', 'BTREE', 'CONSTRAINT', 'CONSTRAINTS', " +
            "'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'EXISTS', 'FULLTEXT', " +
            "'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', " +
            "'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', " +
            "'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', " +
            "'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'CURRENT USER', 'USERS' or 'VECTOR'."
        )
      case _ => _.withSyntaxErrorContaining(
          "Invalid input 'GRAPH': expected 'ALIAS', 'ALIASES', 'ALL', 'AUTH', 'CONSTRAINT', 'CONSTRAINTS', 'CURRENT', " +
            "'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'FULLTEXT', " +
            "'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', " +
            "'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', " +
            "'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', " +
            "'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'USERS' or 'VECTOR' (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. " +
            "Invalid input 'GRAPH', expected: 'ALIAS', 'ALIASES', 'ALL', 'AUTH', 'CONSTRAINT', 'CONSTRAINTS', 'CURRENT', " +
            "'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'FULLTEXT', " +
            "'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', " +
            "'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', " +
            "'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', " +
            "'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'USERS' or 'VECTOR'."
        )
    }
  }

  test("SHOW CURRENT GRAPH TYPES") {
    // TYPES instead of TYPE,
    // there's only one current graph type so we don't allow the plural form
    failsParsing[Statements].in {
      case Cypher5 => cypher5Error
      case _ => _.withSyntaxErrorContaining(
          "Invalid input 'TYPES': expected 'TYPE' (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. Invalid input 'TYPES', expected: 'TYPE'."
        )
    }
  }

  test("SHOW CURRENT GRAPH TYPE YIELD (123 + xyz)") {
    failsParsing[Statements].in {
      case Cypher5 => cypher5Error
      case _ => _.withSyntaxErrorContaining(
          "Invalid input '(': expected a variable name or '*' (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. Invalid input '(', expected: a variable name or '*'."
        )
    }
  }

  test("SHOW CURRENT GRAPH TYPE YIELD (123 + xyz) AS foo") {
    failsParsing[Statements].in {
      case Cypher5 => cypher5Error
      case _ => _.withSyntaxErrorContaining(
          "Invalid input '(': expected a variable name or '*' (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. Invalid input '(', expected: a variable name or '*'."
        )
    }
  }

  test("SHOW CURRENT GRAPH TYPE YIELD a b RETURN *") {
    failsParsing[Statements].in {
      case Cypher5 => cypher5Error
      case _ => _.withSyntaxErrorContaining(
          "Invalid input 'b': expected ',', 'AS', 'ORDER BY', 'LIMIT', 'OFFSET', 'RETURN', 'SHOW', 'SKIP', 'TERMINATE', 'WHERE' or <EOF> (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. " +
            "Invalid input 'b', expected: ',', 'AS', 'ORDER BY', 'LIMIT', 'OFFSET', 'RETURN', 'SHOW', 'SKIP', 'TERMINATE', 'WHERE' or <EOF>."
        )
    }
  }

  test("SHOW CURRENT GRAPH TYPE YIELD") {
    failsParsing[Statements].in {
      case Cypher5 => cypher5Error
      case _ => _.withSyntaxErrorContaining(
          "Invalid input '': expected a variable name or '*' (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. Invalid input '', expected: a variable name or '*'."
        )
    }
  }

  test("SHOW CURRENT GRAPH TYPE YIELD * YIELD *") {
    failsParsing[Statements].in {
      case Cypher5 => cypher5Error
      case _ => _.withSyntaxErrorContaining(
          "Invalid input 'YIELD': expected 'ORDER BY', 'LIMIT', 'OFFSET', 'RETURN', 'SHOW', 'SKIP', 'TERMINATE', 'WHERE' or <EOF> (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. " +
            "Invalid input 'YIELD', expected: 'ORDER BY', 'LIMIT', 'OFFSET', 'RETURN', 'SHOW', 'SKIP', 'TERMINATE', 'WHERE' or <EOF>."
        )
    }
  }

  test("SHOW CURRENT GRAPH TYPE WHERE type = 'OPEN' YIELD *") {
    failsParsing[Statements].in {
      case Cypher5 => cypher5Error
      case _ => _.withSyntaxErrorContaining(
          "Invalid input 'YIELD': expected an expression, 'SHOW', 'TERMINATE' or <EOF> (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. " +
            "Invalid input 'YIELD', expected: an expression, 'SHOW', 'TERMINATE' or <EOF>."
        )
    }
  }

  test("SHOW CURRENT GRAPH TYPE WHERE type = 'OPEN' RETURN *") {
    failsParsing[Statements].in {
      case Cypher5 => cypher5Error
      case _ => _.withSyntaxErrorContaining(
          "Invalid input 'RETURN': expected an expression, 'SHOW', 'TERMINATE' or <EOF> (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. " +
            "Invalid input 'RETURN', expected: an expression, 'SHOW', 'TERMINATE' or <EOF>."
        )
    }
  }

  test("SHOW CURRENT GRAPH TYPE WHERE true RETURN *") {
    failsParsing[Statements].in {
      case Cypher5 => cypher5Error
      case _ => _.withSyntaxErrorContaining(
          // Not sure why we only expect `an expression` and not `an expression, 'SHOW', 'TERMINATE' or <EOF>` here
          // as those are all valid but parser nonsense I guess
          "Invalid input 'RETURN': expected an expression (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. " +
            "Invalid input 'RETURN', expected: an expression."
        )
    }
  }

  test("SHOW CURRENT GRAPH TYPE RETURN *") {
    failsParsing[Statements].in {
      case Cypher5 => cypher5Error
      case _ => _.withSyntaxErrorContaining(
          "Invalid input 'RETURN': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF> (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. " +
            "Invalid input 'RETURN', expected: 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>."
        )
    }
  }

  test("SHOW CURRENT GRAPH TYPE RETURN type as something") {
    failsParsing[Statements].in {
      case Cypher5 => cypher5Error
      case _ => _.withSyntaxErrorContaining(
          "Invalid input 'RETURN': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF> (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. " +
            "Invalid input 'RETURN', expected: 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>."
        )
    }
  }

  test("SHOW CURRENT GRAPH TYPE RETURN type2 YIELD type2") {
    failsParsing[Statements].in {
      case Cypher5 => cypher5Error
      case _ => _.withSyntaxErrorContaining(
          "Invalid input 'RETURN': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF> (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. " +
            "Invalid input 'RETURN', expected: 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>."
        )
    }
  }

  test("SHOW CURRENT GRAPH TYPE USE db") {
    failsParsing[Statements].in {
      case Cypher5 => cypher5Error
      case _ => _.withSyntaxErrorContaining(
          "Invalid input 'USE': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF> (",
          GqlStatusInfoCodes.STATUS_42I06,
          "error: syntax error or access rule violation - invalid input. " +
            "Invalid input 'USE', expected: 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>."
        )
    }
  }

  // Invalid clause combinations

  for {
    prefix <- Seq("USE neo4j", "")
  } {
    test(s"$prefix SHOW CURRENT GRAPH TYPE YIELD * WITH * MATCH (n) RETURN n") {
      // Can't parse WITH after SHOW
      failsParsing[Statements].in {
        case Cypher5 => cypher5Error
        case _ => _.withSyntaxErrorContaining(
            "Invalid input 'WITH': expected 'ORDER BY', 'LIMIT', 'OFFSET', 'RETURN', 'SHOW', 'SKIP', 'TERMINATE', 'WHERE' or <EOF> (",
            GqlStatusInfoCodes.STATUS_42I06,
            "error: syntax error or access rule violation - invalid input. " +
              "Invalid input 'WITH', expected: 'ORDER BY', 'LIMIT', 'OFFSET', 'RETURN', 'SHOW', 'SKIP', 'TERMINATE', 'WHERE' or <EOF>."
          )
      }
    }

    test(s"$prefix UNWIND range(1,10) as b SHOW CURRENT GRAPH TYPE YIELD * RETURN *") {
      // Can't parse SHOW  after UNWIND
      parsesIn[Statements] {
        case Cypher5 => _.withSyntaxErrorContaining(
            """Invalid input 'SHOW': expected 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF>""".stripMargin
          )
        case _ => _.withSyntaxErrorContaining(
            """Invalid input 'SHOW': expected 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOREACH', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NEXT', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WITH' or <EOF>""".stripMargin
          )
      }
    }

    test(s"$prefix SHOW CURRENT GRAPH TYPE WITH specification, type RETURN *") {
      // Can't parse WITH after SHOW
      failsParsing[Statements].in {
        case Cypher5 => cypher5Error
        case _ => _.withSyntaxErrorContaining(
            "Invalid input 'WITH': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF> (",
            GqlStatusInfoCodes.STATUS_42I06,
            "error: syntax error or access rule violation - invalid input. " +
              "Invalid input 'WITH', expected: 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>."
          )
      }
    }

    test(s"$prefix WITH 'n' as n SHOW CURRENT GRAPH TYPE YIELD type RETURN type as something") {
      // Can't parse SHOW  after WITH
      parsesIn[Statements] {
        case Cypher5 => _.withSyntaxErrorContaining(
            """Invalid input 'SHOW': expected ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WHERE', 'WITH' or <EOF>"""
          )
        case _ => _.withSyntaxErrorContaining(
            """Invalid input 'SHOW': expected ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOREACH', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NEXT', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WHERE', 'WITH' or <EOF>"""
          )
      }
    }

    test(s"$prefix SHOW CURRENT GRAPH TYPE WITH 1 as c RETURN type as something") {
      // Can't parse WITH after SHOW
      failsParsing[Statements].in {
        case Cypher5 => cypher5Error
        case _ => _.withSyntaxErrorContaining(
            "Invalid input 'WITH': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF> (",
            GqlStatusInfoCodes.STATUS_42I06,
            "error: syntax error or access rule violation - invalid input. " +
              "Invalid input 'WITH', expected: 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>."
          )
      }
    }

    test(s"$prefix SHOW CURRENT GRAPH TYPE WITH 1 as c") {
      // Can't parse WITH after SHOW
      failsParsing[Statements].in {
        case Cypher5 => cypher5Error
        case _ => _.withSyntaxErrorContaining(
            "Invalid input 'WITH': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF> (",
            GqlStatusInfoCodes.STATUS_42I06,
            "error: syntax error or access rule violation - invalid input. " +
              "Invalid input 'WITH', expected: 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>."
          )
      }
    }

    test(s"$prefix SHOW CURRENT GRAPH TYPE YIELD a WITH a RETURN a") {
      // Can't parse WITH after SHOW
      failsParsing[Statements].in {
        case Cypher5 => cypher5Error
        case _ => _.withSyntaxErrorContaining(
            "Invalid input 'WITH': expected ',', 'AS', 'ORDER BY', 'LIMIT', 'OFFSET', 'RETURN', 'SHOW', 'SKIP', 'TERMINATE', 'WHERE' or <EOF> (",
            GqlStatusInfoCodes.STATUS_42I06,
            "error: syntax error or access rule violation - invalid input. " +
              "Invalid input 'WITH', expected: ',', 'AS', 'ORDER BY', 'LIMIT', 'OFFSET', 'RETURN', 'SHOW', 'SKIP', 'TERMINATE', 'WHERE' or <EOF>."
          )
      }
    }

    test(s"$prefix SHOW CURRENT GRAPH TYPE YIELD as UNWIND as as a RETURN a") {
      // Can't parse UNWIND after SHOW
      failsParsing[Statements].in {
        case Cypher5 => cypher5Error
        case _ => _.withSyntaxErrorContaining(
            "Invalid input 'UNWIND': expected ',', 'AS', 'ORDER BY', 'LIMIT', 'OFFSET', 'RETURN', 'SHOW', 'SKIP', 'TERMINATE', 'WHERE' or <EOF> (",
            GqlStatusInfoCodes.STATUS_42I06,
            "error: syntax error or access rule violation - invalid input. " +
              "Invalid input 'UNWIND', expected: ',', 'AS', 'ORDER BY', 'LIMIT', 'OFFSET', 'RETURN', 'SHOW', 'SKIP', 'TERMINATE', 'WHERE' or <EOF>."
          )
      }
    }
  }

}
