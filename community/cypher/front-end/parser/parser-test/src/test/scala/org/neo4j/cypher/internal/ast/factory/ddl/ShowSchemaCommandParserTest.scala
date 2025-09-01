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

import org.neo4j.cypher.internal.ast.AllConstraints
import org.neo4j.cypher.internal.ast.AllExistsConstraints
import org.neo4j.cypher.internal.ast.AllIndexes
import org.neo4j.cypher.internal.ast.FulltextIndexes
import org.neo4j.cypher.internal.ast.KeyConstraints
import org.neo4j.cypher.internal.ast.LookupIndexes
import org.neo4j.cypher.internal.ast.NodeAllExistsConstraints
import org.neo4j.cypher.internal.ast.NodeKeyConstraints
import org.neo4j.cypher.internal.ast.NodePropExistsConstraints
import org.neo4j.cypher.internal.ast.NodePropTypeConstraints
import org.neo4j.cypher.internal.ast.NodeUniqueConstraints
import org.neo4j.cypher.internal.ast.PointIndexes
import org.neo4j.cypher.internal.ast.PropExistsConstraints
import org.neo4j.cypher.internal.ast.PropTypeConstraints
import org.neo4j.cypher.internal.ast.RangeIndexes
import org.neo4j.cypher.internal.ast.RelAllExistsConstraints
import org.neo4j.cypher.internal.ast.RelKeyConstraints
import org.neo4j.cypher.internal.ast.RelPropExistsConstraints
import org.neo4j.cypher.internal.ast.RelPropTypeConstraints
import org.neo4j.cypher.internal.ast.RelUniqueConstraints
import org.neo4j.cypher.internal.ast.ShowConstraintsClause
import org.neo4j.cypher.internal.ast.ShowIndexesClause
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.TextIndexes
import org.neo4j.cypher.internal.ast.UniqueConstraints
import org.neo4j.cypher.internal.ast.VectorIndexes
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.expressions.AllIterablePredicate
import org.neo4j.cypher.internal.util.symbols.IntegerType

/* Tests for listing indexes and constraints */
class ShowSchemaCommandParserTest extends AdministrationAndSchemaCommandParserTestBase {

  // Show indexes

  Seq("INDEX", "INDEXES").foreach { indexKeyword =>
    test(s"SHOW $indexKeyword") {
      assertAst(
        singleQuery(ShowIndexesClause(
          AllIndexes,
          None,
          List.empty,
          yieldAll = false,
          None
        )(defaultPos))
      )
    }

    test(s"SHOW ALL $indexKeyword") {
      assertAst(
        singleQuery(ShowIndexesClause(
          AllIndexes,
          None,
          List.empty,
          yieldAll = false,
          None
        )(defaultPos))
      )
    }

    test(s"SHOW RANGE $indexKeyword") {
      assertAst(
        singleQuery(ShowIndexesClause(
          RangeIndexes,
          None,
          List.empty,
          yieldAll = false,
          None
        )(defaultPos))
      )
    }

    test(s"SHOW FULLTEXT $indexKeyword") {
      assertAst(
        singleQuery(
          ShowIndexesClause(
            FulltextIndexes,
            None,
            List.empty,
            yieldAll = false,
            None
          )(defaultPos)
        )
      )
    }

    test(s"SHOW TEXT $indexKeyword") {
      assertAst(
        singleQuery(ShowIndexesClause(
          TextIndexes,
          None,
          List.empty,
          yieldAll = false,
          None
        )(defaultPos))
      )
    }

    test(s"SHOW POINT $indexKeyword") {
      assertAst(
        singleQuery(ShowIndexesClause(
          PointIndexes,
          None,
          List.empty,
          yieldAll = false,
          None
        )(defaultPos))
      )
    }

    test(s"SHOW VECTOR $indexKeyword") {
      assertAst(
        singleQuery(
          ShowIndexesClause(
            VectorIndexes,
            None,
            List.empty,
            yieldAll = false,
            None
          )(defaultPos)
        )
      )
    }

    test(s"SHOW LOOKUP $indexKeyword") {
      assertAst(
        singleQuery(
          ShowIndexesClause(
            LookupIndexes,
            None,
            List.empty,
            yieldAll = false,
            None
          )(defaultPos)
        )
      )
    }

    test(s"USE db SHOW $indexKeyword") {
      assertAstVersionBased(
        cypher5 =>
          singleQuery(
            use(List("db"), !cypher5),
            ShowIndexesClause(AllIndexes, None, List.empty, yieldAll = false, None)(pos)
          ),
        comparePosition = false
      )
    }
  }

  // Show indexes filtering

  test("SHOW INDEX WHERE uniqueness = 'UNIQUE'") {
    assertAst(
      singleQuery(ShowIndexesClause(
        AllIndexes,
        Some(where(equals(varFor("uniqueness"), literalString("UNIQUE")))),
        List.empty,
        yieldAll = false,
        None
      )(pos)),
      comparePosition = false
    )
  }

  test("SHOW INDEXES YIELD populationPercent") {
    assertAst(
      singleQuery(
        ShowIndexesClause(
          AllIndexes,
          None,
          List(commandResultItem("populationPercent")),
          yieldAll = false,
          Some(withFromYield(returnAllItems.withDefaultOrderOnColumns(List("populationPercent"))))
        )(pos)
      ),
      comparePosition = false
    )
  }

  test("SHOW POINT INDEXES YIELD populationPercent") {
    assertAst(
      singleQuery(
        ShowIndexesClause(
          PointIndexes,
          None,
          List(commandResultItem("populationPercent")),
          yieldAll = false,
          Some(withFromYield(returnAllItems.withDefaultOrderOnColumns(List("populationPercent"))))
        )(pos)
      ),
      comparePosition = false
    )
  }

  test("SHOW ALL INDEXES YIELD *") {
    assertAst(
      singleQuery(
        ShowIndexesClause(AllIndexes, None, List.empty, yieldAll = true, Some(withFromYield(returnAllItems)))(pos)
      ),
      comparePosition = false
    )
  }

  test("SHOW INDEXES YIELD * ORDER BY name SKIP 2 LIMIT 5") {
    assertAst(
      singleQuery(
        ShowIndexesClause(
          AllIndexes,
          None,
          List.empty,
          yieldAll = true,
          Some(withFromYield(returnAllItems, Some(orderBy(sortItem(varFor("name")))), Some(skip(2)), Some(limit(5))))
        )(pos)
      ),
      comparePosition = false
    )
  }

  test("SHOW RANGE INDEXES YIELD * ORDER BY name SKIP 2 LIMIT 5") {
    assertAst(
      singleQuery(
        ShowIndexesClause(
          RangeIndexes,
          None,
          List.empty,
          yieldAll = true,
          Some(withFromYield(returnAllItems, Some(orderBy(sortItem(varFor("name")))), Some(skip(2)), Some(limit(5))))
        )(pos)
      ),
      comparePosition = false
    )
  }

  test("USE db SHOW FULLTEXT INDEXES YIELD name, populationPercent AS pp WHERE pp < 50.0 RETURN name") {
    assertAstVersionBased(
      cypher5 =>
        singleQuery(
          use(List("db"), !cypher5),
          ShowIndexesClause(
            FulltextIndexes,
            None,
            List(commandResultItem("name"), commandResultItem("populationPercent", Some("pp"))),
            yieldAll = false,
            Some(
              withFromYield(
                returnAllItems.withDefaultOrderOnColumns(List("name", "pp")),
                where = Some(where(lessThan(varFor("pp"), literalFloat(50.0))))
              )
            )
          )(pos),
          return_(variableReturnItem("name"))
        ),
      comparePosition = false
    )
  }

  test(
    "USE db SHOW VECTOR INDEXES YIELD name, populationPercent AS pp ORDER BY pp SKIP 2 LIMIT 5 WHERE pp < 50.0 RETURN name"
  ) {
    assertAstVersionBased(
      cypher5 =>
        singleQuery(
          use(List("db"), !cypher5),
          ShowIndexesClause(
            VectorIndexes,
            None,
            List(commandResultItem("name"), commandResultItem("populationPercent", Some("pp"))),
            yieldAll = false,
            Some(
              withFromYield(
                returnAllItems.withDefaultOrderOnColumns(List("name", "pp")),
                Some(orderBy(sortItem(varFor("pp")))),
                Some(skip(2)),
                Some(limit(5)),
                Some(where(lessThan(varFor("pp"), literalFloat(50.0))))
              )
            )
          )(pos),
          return_(variableReturnItem("name"))
        ),
      comparePosition = false
    )
  }

  test(
    "USE db SHOW VECTOR INDEXES YIELD name, populationPercent AS pp ORDER BY pp OFFSET 2 LIMIT 5 WHERE pp < 50.0 RETURN name"
  ) {
    assertAstVersionBased(
      cypher5 =>
        singleQuery(
          use(List("db"), !cypher5),
          ShowIndexesClause(
            VectorIndexes,
            None,
            List(commandResultItem("name"), commandResultItem("populationPercent", Some("pp"))),
            yieldAll = false,
            Some(
              withFromYield(
                returnAllItems.withDefaultOrderOnColumns(List("name", "pp")),
                Some(orderBy(sortItem(varFor("pp")))),
                Some(skip(2)),
                Some(limit(5)),
                Some(where(lessThan(varFor("pp"), literalFloat(50.0))))
              )
            )
          )(pos),
          return_(variableReturnItem("name"))
        ),
      comparePosition = false
    )
  }

  test("SHOW INDEXES YIELD name AS INDEX, type AS OUTPUT") {
    assertAst(
      singleQuery(
        ShowIndexesClause(
          AllIndexes,
          None,
          List(commandResultItem("name", Some("INDEX")), commandResultItem("type", Some("OUTPUT"))),
          yieldAll = false,
          Some(withFromYield(returnAllItems.withDefaultOrderOnColumns(List("INDEX", "OUTPUT"))))
        )(pos)
      ),
      comparePosition = false
    )
  }

  test("SHOW TEXT INDEXES YIELD name AS INDEX, type AS OUTPUT") {
    assertAst(
      singleQuery(
        ShowIndexesClause(
          TextIndexes,
          None,
          List(commandResultItem("name", Some("INDEX")), commandResultItem("type", Some("OUTPUT"))),
          yieldAll = false,
          Some(withFromYield(returnAllItems.withDefaultOrderOnColumns(List("INDEX", "OUTPUT"))))
        )(pos)
      ),
      comparePosition = false
    )
  }

  test("SHOW LOOKUP INDEXES WHERE name = 'GRANT'") {
    assertAst(
      singleQuery(ShowIndexesClause(
        LookupIndexes,
        Some(where(equals(varFor("name"), literalString("GRANT")))),
        List.empty,
        yieldAll = false,
        None
      )(pos)),
      comparePosition = false
    )
  }

  test("SHOW INDEXES YIELD a ORDER BY a WHERE a = 1") {
    assertAst(singleQuery(
      ShowIndexesClause(
        AllIndexes,
        None,
        List(commandResultItem("a")),
        yieldAll = false,
        Some(withFromYield(
          returnAllItems.withDefaultOrderOnColumns(List("a")),
          Some(orderBy(sortItem(varFor("a")))),
          where = Some(where(equals(varFor("a"), literalInt(1))))
        ))
      )(pos)
    ))
  }

  test("SHOW INDEXES YIELD a AS b ORDER BY b WHERE b = 1") {
    assertAst(singleQuery(
      ShowIndexesClause(
        AllIndexes,
        None,
        List(commandResultItem("a", Some("b"))),
        yieldAll = false,
        Some(withFromYield(
          returnAllItems.withDefaultOrderOnColumns(List("b")),
          Some(orderBy(sortItem(varFor("b")))),
          where = Some(where(equals(varFor("b"), literalInt(1))))
        ))
      )(pos)
    ))
  }

  test("SHOW INDEXES YIELD a AS b ORDER BY a WHERE a = 1") {
    assertAst(singleQuery(
      ShowIndexesClause(
        AllIndexes,
        None,
        List(commandResultItem("a", Some("b"))),
        yieldAll = false,
        Some(withFromYield(
          returnAllItems.withDefaultOrderOnColumns(List("b")),
          Some(orderBy(sortItem(varFor("b")))),
          where = Some(where(equals(varFor("b"), literalInt(1))))
        ))
      )(pos)
    ))
  }

  test("SHOW INDEXES YIELD a ORDER BY EXISTS { (a) } WHERE EXISTS { (a) }") {
    assertAst(singleQuery(
      ShowIndexesClause(
        AllIndexes,
        None,
        List(commandResultItem("a")),
        yieldAll = false,
        Some(withFromYield(
          returnAllItems.withDefaultOrderOnColumns(List("a")),
          Some(orderBy(sortItem(simpleExistsExpression(patternForMatch(nodePat(Some("a"))), None)))),
          where = Some(where(simpleExistsExpression(patternForMatch(nodePat(Some("a"))), None)))
        ))
      )(pos)
    ))
  }

  test("SHOW INDEXES YIELD a ORDER BY EXISTS { (b) } WHERE EXISTS { (b) }") {
    assertAst(singleQuery(
      ShowIndexesClause(
        AllIndexes,
        None,
        List(commandResultItem("a")),
        yieldAll = false,
        Some(withFromYield(
          returnAllItems.withDefaultOrderOnColumns(List("a")),
          Some(orderBy(sortItem(simpleExistsExpression(patternForMatch(nodePat(Some("b"))), None)))),
          where = Some(where(simpleExistsExpression(patternForMatch(nodePat(Some("b"))), None)))
        ))
      )(pos)
    ))
  }

  test("SHOW INDEXES YIELD a AS b ORDER BY COUNT { (b) } WHERE EXISTS { (b) }") {
    assertAst(singleQuery(
      ShowIndexesClause(
        AllIndexes,
        None,
        List(commandResultItem("a", Some("b"))),
        yieldAll = false,
        Some(withFromYield(
          returnAllItems.withDefaultOrderOnColumns(List("b")),
          Some(orderBy(sortItem(simpleCountExpression(patternForMatch(nodePat(Some("b"))), None)))),
          where = Some(where(simpleExistsExpression(patternForMatch(nodePat(Some("b"))), None)))
        ))
      )(pos)
    ))
  }

  test("SHOW INDEXES YIELD a AS b ORDER BY EXISTS { (a) } WHERE COLLECT { MATCH (a) RETURN a } <> []") {
    assertAst(singleQuery(
      ShowIndexesClause(
        AllIndexes,
        None,
        List(commandResultItem("a", Some("b"))),
        yieldAll = false,
        Some(withFromYield(
          returnAllItems.withDefaultOrderOnColumns(List("b")),
          Some(orderBy(sortItem(simpleExistsExpression(patternForMatch(nodePat(Some("b"))), None)))),
          where = Some(where(notEquals(
            simpleCollectExpression(patternForMatch(nodePat(Some("b"))), None, return__(returnItem(varFor("b"), "a"))),
            listOf()
          )))
        ))
      )(pos)
    ))
  }

  test("SHOW INDEXES YIELD a AS b ORDER BY b + COUNT { () } WHERE b OR EXISTS { () }") {
    assertAst(singleQuery(
      ShowIndexesClause(
        AllIndexes,
        None,
        List(commandResultItem("a", Some("b"))),
        yieldAll = false,
        Some(withFromYield(
          returnAllItems.withDefaultOrderOnColumns(List("b")),
          Some(orderBy(sortItem(add(varFor("b"), simpleCountExpression(patternForMatch(nodePat()), None))))),
          where = Some(where(or(varFor("b"), simpleExistsExpression(patternForMatch(nodePat()), None))))
        ))
      )(pos)
    ))
  }

  test("SHOW INDEXES YIELD a AS b ORDER BY a + EXISTS { () } WHERE a OR ALL (x IN [1, 2] WHERE x IS :: INT)") {
    assertAst(singleQuery(
      ShowIndexesClause(
        AllIndexes,
        None,
        List(commandResultItem("a", Some("b"))),
        yieldAll = false,
        Some(withFromYield(
          returnAllItems.withDefaultOrderOnColumns(List("b")),
          Some(orderBy(sortItem(add(varFor("b"), simpleExistsExpression(patternForMatch(nodePat()), None))))),
          where = Some(where(or(
            varFor("b"),
            AllIterablePredicate(
              varFor("x"),
              listOfInt(1, 2),
              Some(isTyped(varFor("x"), IntegerType(isNullable = true)(pos)))
            )(pos)
          )))
        ))
      )(pos)
    ))
  }

  test("SHOW INDEXES YIELD name as options, options as name where size(options) > 0 RETURN options as name") {
    assertAst(singleQuery(
      ShowIndexesClause(
        AllIndexes,
        None,
        List(
          commandResultItem("name", Some("options")),
          commandResultItem("options", Some("name"))
        ),
        yieldAll = false,
        Some(withFromYield(
          returnAllItems.withDefaultOrderOnColumns(List("options", "name")),
          where = Some(where(
            greaterThan(size(varFor("options")), literalInt(0))
          ))
        ))
      )(pos),
      return_(aliasedReturnItem("options", "name"))
    ))
  }

  // Negative tests for show indexes

  test("SHOW INDEX YIELD (123 + xyz)") {
    failsParsing[Statements]
  }

  test("SHOW INDEX YIELD (123 + xyz) AS foo") {
    failsParsing[Statements]
  }

  test("SHOW ALL RANGE INDEXES") {
    failsParsing[Statements].withSyntaxError(
      """|Invalid input 'RANGE': expected 'CONSTRAINT', 'CONSTRAINTS', 'FUNCTION', 'FUNCTIONS', 'INDEX', 'INDEXES', 'PRIVILEGE', 'PRIVILEGES', 'ROLE' or 'ROLES' (line 1, column 10 (offset: 9))
         |"SHOW ALL RANGE INDEXES"
         |          ^""".stripMargin
    )
  }

  test("SHOW INDEX YIELD") {
    failsParsing[Statements]
  }

  test("SHOW INDEXES YIELD * YIELD *") {
    failsParsing[Statements]
  }

  test("SHOW INDEXES WHERE uniqueness = 'UNIQUE' YIELD *") {
    failsParsing[Statements]
  }

  test("SHOW INDEXES WHERE uniqueness = 'UNIQUE' RETURN *") {
    failsParsing[Statements]
  }

  test("SHOW INDEXES YIELD a b RETURN *") {
    failsParsing[Statements]
  }

  test("SHOW INDEXES WHERE true RETURN *") {
    failsParsing[Statements].withSyntaxErrorContaining("Invalid input 'RETURN'")
  }

  test("SHOW INDEXES RETURN *") {
    failsParsing[Statements]
  }

  test("SHOW NODE INDEXES") {
    failsParsing[Statements]
  }

  test("SHOW REL INDEXES") {
    failsParsing[Statements]
  }

  test("SHOW RELATIONSHIP INDEXES") {
    failsParsing[Statements]
  }

  test("SHOW UNKNOWN INDEXES") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxError(
          """Invalid input 'UNKNOWN': expected 'ALIAS', 'ALIASES', 'ALL', 'BTREE', 'CONSTRAINT', 'CONSTRAINTS', 'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'EXISTS', 'FULLTEXT', 'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', 'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', 'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', 'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'CURRENT USER', 'USERS' or 'VECTOR' (line 1, column 6 (offset: 5))
            |"SHOW UNKNOWN INDEXES"
            |      ^""".stripMargin
        )
      case _ => _.withSyntaxError(
          """Invalid input 'UNKNOWN': expected 'ALIAS', 'ALIASES', 'ALL', 'CONSTRAINT', 'CONSTRAINTS', 'CURRENT', 'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'FULLTEXT', 'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', 'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', 'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', 'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'USERS' or 'VECTOR' (line 1, column 6 (offset: 5))
            |"SHOW UNKNOWN INDEXES"
            |      ^""".stripMargin
        )
    }
  }

  test("SHOW BUILT IN INDEXES") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'INDEXES': expected 'FUNCTION' or 'FUNCTIONS' (line 1, column 15 (offset: 14))
        |"SHOW BUILT IN INDEXES"
        |               ^""".stripMargin
    )
  }

  // Removed syntax (also includes parts using it that was invalid anyway)

  test("SHOW BTREE INDEX") {
    assertFailsOnBtree()
  }

  test("SHOW BTREE INDEXES") {
    assertFailsOnBtree()
  }

  test("SHOW BTREE INDEXES YIELD *") {
    assertFailsOnBtree()
  }

  test("SHOW BTREE INDEXES WHERE name = 'btree'") {
    assertFailsOnBtree()
  }

  test("SHOW ALL BTREE INDEXES") {
    failsParsing[Statements].withSyntaxError(
      """|Invalid input 'BTREE': expected 'CONSTRAINT', 'CONSTRAINTS', 'FUNCTION', 'FUNCTIONS', 'INDEX', 'INDEXES', 'PRIVILEGE', 'PRIVILEGES', 'ROLE' or 'ROLES' (line 1, column 10 (offset: 9))
         |"SHOW ALL BTREE INDEXES"
         |          ^""".stripMargin
    )
  }

  test("SHOW INDEXES BRIEF") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "BRIEF")
  }

  test("SHOW INDEX BRIEF OUTPUT") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "BRIEF")
  }

  test("SHOW ALL INDEXES BRIEF") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "BRIEF")
  }

  test("SHOW  ALL INDEX BRIEF OUTPUT") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "BRIEF")
  }

  test("SHOW BTREE INDEXES BRIEF") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "BRIEF", failOnBtree = true)
  }

  test("SHOW INDEXES VERBOSE") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "VERBOSE")
  }

  test("SHOW ALL INDEX VERBOSE") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "VERBOSE")
  }

  test("SHOW BTREE INDEXES VERBOSE OUTPUT") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "VERBOSE", failOnBtree = true)
  }

  test("SHOW INDEX OUTPUT") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxErrorContaining(
          "Invalid input 'OUTPUT': expected 'BRIEF', 'SHOW', 'TERMINATE', 'VERBOSE', 'WHERE', 'YIELD' or <EOF>"
        )
      case _ =>
        _.withSyntaxErrorContaining("Invalid input 'OUTPUT': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>")
    }
  }

  test("SHOW INDEX VERBOSE BRIEF OUTPUT") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "VERBOSE")
  }

  test("SHOW INDEXES BRIEF YIELD *") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "BRIEF")
  }

  test("SHOW INDEXES VERBOSE YIELD *") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "VERBOSE")
  }

  test("SHOW INDEXES BRIEF RETURN *") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "BRIEF")
  }

  test("SHOW INDEXES VERBOSE RETURN *") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "VERBOSE")
  }

  test("SHOW INDEXES BRIEF WHERE uniqueness = 'UNIQUE'") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "BRIEF")
  }

  test("SHOW INDEXES VERBOSE WHERE uniqueness = 'UNIQUE'") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW INDEXES", "VERBOSE")
  }

  test("SHOW RANGE INDEXES BRIEF") {
    assertFailsOnBriefVerboseNeverAllowed("BRIEF")
  }

  test("SHOW RANGE INDEXES VERBOSE") {
    assertFailsOnBriefVerboseNeverAllowed("VERBOSE")
  }

  test("SHOW FULLTEXT INDEXES BRIEF") {
    assertFailsOnBriefVerboseNeverAllowed("BRIEF")
  }

  test("SHOW FULLTEXT INDEXES VERBOSE") {
    assertFailsOnBriefVerboseNeverAllowed("VERBOSE")
  }

  test("SHOW TEXT INDEXES BRIEF") {
    assertFailsOnBriefVerboseNeverAllowed("BRIEF")
  }

  test("SHOW TEXT INDEXES VERBOSE") {
    assertFailsOnBriefVerboseNeverAllowed("VERBOSE")
  }

  test("SHOW POINT INDEXES BRIEF") {
    assertFailsOnBriefVerboseNeverAllowed("BRIEF")
  }

  test("SHOW POINT INDEXES VERBOSE") {
    assertFailsOnBriefVerboseNeverAllowed("VERBOSE")
  }

  test("SHOW VECTOR INDEXES BRIEF") {
    assertFailsOnBriefVerboseNeverAllowed("BRIEF")
  }

  test("SHOW VECTOR INDEXES VERBOSE") {
    assertFailsOnBriefVerboseNeverAllowed("VERBOSE")
  }

  test("SHOW LOOKUP INDEXES BRIEF") {
    assertFailsOnBriefVerboseNeverAllowed("BRIEF")
  }

  test("SHOW LOOKUP INDEXES VERBOSE") {
    assertFailsOnBriefVerboseNeverAllowed("VERBOSE")
  }

  // Show constraints

  // initial group of constraint types, allowed brief/verbose
  private val constraintTypesV1 = Seq(
    ("", AllConstraints),
    ("ALL", AllConstraints),
    ("UNIQUE", UniqueConstraints.cypher25),
    ("NODE KEY", NodeKeyConstraints),
    ("EXIST", AllExistsConstraints),
    ("NODE EXIST", NodeAllExistsConstraints),
    ("RELATIONSHIP EXIST", RelAllExistsConstraints)
  )

  // group of added constraint types in 4 and 5, didn't allow brief/verbose
  private val constraintTypeV2 = Seq(
    ("NODE UNIQUE", NodeUniqueConstraints.cypher25),
    ("RELATIONSHIP UNIQUE", RelUniqueConstraints.cypher25),
    ("REL UNIQUE", RelUniqueConstraints.cypher25),
    ("UNIQUENESS", UniqueConstraints.cypher25),
    ("NODE UNIQUENESS", NodeUniqueConstraints.cypher25),
    ("RELATIONSHIP UNIQUENESS", RelUniqueConstraints.cypher25),
    ("REL UNIQUENESS", RelUniqueConstraints.cypher25),
    ("KEY", KeyConstraints),
    ("RELATIONSHIP KEY", RelKeyConstraints),
    ("REL KEY", RelKeyConstraints),
    ("PROPERTY EXISTENCE", PropExistsConstraints.cypher25),
    ("PROPERTY EXIST", PropExistsConstraints.cypher25),
    ("EXISTENCE", AllExistsConstraints),
    ("NODE PROPERTY EXISTENCE", NodePropExistsConstraints.cypher25),
    ("NODE PROPERTY EXIST", NodePropExistsConstraints.cypher25),
    ("NODE EXISTENCE", NodeAllExistsConstraints),
    ("RELATIONSHIP PROPERTY EXISTENCE", RelPropExistsConstraints.cypher25),
    ("RELATIONSHIP PROPERTY EXIST", RelPropExistsConstraints.cypher25),
    ("RELATIONSHIP EXISTENCE", RelAllExistsConstraints),
    ("REL PROPERTY EXISTENCE", RelPropExistsConstraints.cypher25),
    ("REL PROPERTY EXIST", RelPropExistsConstraints.cypher25),
    ("REL EXISTENCE", RelAllExistsConstraints),
    ("REL EXIST", RelAllExistsConstraints),
    ("NODE PROPERTY TYPE", NodePropTypeConstraints),
    ("RELATIONSHIP PROPERTY TYPE", RelPropTypeConstraints),
    ("REL PROPERTY TYPE", RelPropTypeConstraints),
    ("PROPERTY TYPE", PropTypeConstraints)
  )

  // group of constraint types added in Cypher 25 (not valid before that)
  private val constraintTypeV3 = Seq(
    ("PROPERTY UNIQUE", UniqueConstraints.cypher25),
    ("NODE PROPERTY UNIQUE", NodeUniqueConstraints.cypher25),
    ("RELATIONSHIP PROPERTY UNIQUE", RelUniqueConstraints.cypher25),
    ("REL PROPERTY UNIQUE", RelUniqueConstraints.cypher25),
    ("PROPERTY UNIQUENESS", UniqueConstraints.cypher25),
    ("NODE PROPERTY UNIQUENESS", NodeUniqueConstraints.cypher25),
    ("RELATIONSHIP PROPERTY UNIQUENESS", RelUniqueConstraints.cypher25),
    ("REL PROPERTY UNIQUENESS", RelUniqueConstraints.cypher25)
  )

  Seq("CONSTRAINT", "CONSTRAINTS").foreach {
    constraintKeyword =>
      (constraintTypesV1 ++ constraintTypeV2).foreach {
        case (constraintTypeKeyword, constraintType) =>
          val cypher5ConstraintType = constraintType match {
            case _: UniqueConstraints         => UniqueConstraints.cypher5
            case _: NodeUniqueConstraints     => NodeUniqueConstraints.cypher5
            case _: RelUniqueConstraints      => RelUniqueConstraints.cypher5
            case _: PropExistsConstraints     => PropExistsConstraints.cypher5
            case _: NodePropExistsConstraints => NodePropExistsConstraints.cypher5
            case _: RelPropExistsConstraints  => RelPropExistsConstraints.cypher5
            case other                        => other
          }

          test(s"SHOW $constraintTypeKeyword $constraintKeyword") {
            assertAstVersionBased(fromCypher5 =>
              singleQuery(ShowConstraintsClause(
                if (fromCypher5) cypher5ConstraintType else constraintType,
                None,
                List.empty,
                yieldAll = false,
                None,
                fromCypher5
              )(defaultPos))
            )
          }

          test(s"USE db SHOW $constraintTypeKeyword $constraintKeyword") {
            assertAstVersionBased(
              fromCypher5 =>
                singleQuery(
                  use(List("db"), !fromCypher5),
                  ShowConstraintsClause(
                    if (fromCypher5) cypher5ConstraintType else constraintType,
                    None,
                    List.empty,
                    yieldAll = false,
                    None,
                    fromCypher5
                  )(pos)
                ),
              comparePosition = false
            )
          }

      }

      constraintTypeV3.foreach {
        case (constraintTypeKeyword, constraintType) =>
          test(s"SHOW $constraintTypeKeyword $constraintKeyword") {
            val errorKeyword = constraintTypeKeyword.split(" ").last
            parsesIn[Statements] {
              case Cypher5 => _.withSyntaxErrorContaining(
                  s"Invalid input '$errorKeyword': expected 'EXIST', 'EXISTENCE' or 'TYPE' (line"
                )
              case _ => _.toAst(statementToStatements(singleQuery(ShowConstraintsClause(
                  constraintType,
                  None,
                  List.empty,
                  yieldAll = false,
                  None,
                  returnCypher5Columns = false
                )(defaultPos))))
            }
          }

          test(s"USE db SHOW $constraintTypeKeyword $constraintKeyword") {
            val errorKeyword = constraintTypeKeyword.split(" ").last
            parsesIn[Statements] {
              case Cypher5 => _.withSyntaxErrorContaining(
                  s"Invalid input '$errorKeyword': expected 'EXIST', 'EXISTENCE' or 'TYPE' (line"
                )
              case _ => _.toAst(statementToStatements(singleQuery(
                  use(List("db"), resolveStrictly = true),
                  ShowConstraintsClause(
                    constraintType,
                    None,
                    List.empty,
                    yieldAll = false,
                    None,
                    returnCypher5Columns = false
                  )(pos)
                )))
            }
          }
      }
  }

  // Show constraints filtering

  test("SHOW CONSTRAINT WHERE entityType = 'RELATIONSHIP'") {
    assertAstVersionBased(
      fromCypher5 =>
        singleQuery(ShowConstraintsClause(
          AllConstraints,
          Some(where(equals(varFor("entityType"), literalString("RELATIONSHIP")))),
          List.empty,
          yieldAll = false,
          None,
          fromCypher5
        )(pos)),
      comparePosition = false
    )
  }

  test("SHOW REL PROPERTY EXISTENCE CONSTRAINTS YIELD labelsOrTypes") {
    assertAstVersionBased(
      fromCypher5 =>
        singleQuery(
          ShowConstraintsClause(
            if (fromCypher5) RelPropExistsConstraints.cypher5 else RelPropExistsConstraints.cypher25,
            None,
            List(commandResultItem("labelsOrTypes")),
            yieldAll = false,
            Some(withFromYield(returnAllItems.withDefaultOrderOnColumns(List("labelsOrTypes")))),
            fromCypher5
          )(pos)
        ),
      comparePosition = false
    )
  }

  test("SHOW UNIQUE CONSTRAINTS YIELD *") {
    assertAstVersionBased(
      fromCypher5 =>
        singleQuery(
          ShowConstraintsClause(
            if (fromCypher5) UniqueConstraints.cypher5 else UniqueConstraints.cypher25,
            None,
            List.empty,
            yieldAll = true,
            Some(withFromYield(returnAllItems)),
            fromCypher5
          )(pos)
        ),
      comparePosition = false
    )
  }

  test("SHOW CONSTRAINTS YIELD * ORDER BY name SKIP 2 LIMIT 5") {
    assertAstVersionBased(
      fromCypher5 =>
        singleQuery(
          ShowConstraintsClause(
            AllConstraints,
            None,
            List.empty,
            yieldAll = true,
            Some(withFromYield(returnAllItems, Some(orderBy(sortItem(varFor("name")))), Some(skip(2)), Some(limit(5)))),
            fromCypher5
          )(pos)
        ),
      comparePosition = false
    )
  }

  test("USE db SHOW NODE KEY CONSTRAINTS YIELD name, properties AS pp WHERE size(pp) > 1 RETURN name") {
    assertAstVersionBased(
      fromCypher5 =>
        singleQuery(
          use(List("db"), !fromCypher5),
          ShowConstraintsClause(
            NodeKeyConstraints,
            None,
            List(
              commandResultItem("name"),
              commandResultItem("properties", Some("pp"))
            ),
            yieldAll = false,
            Some(withFromYield(
              returnAllItems.withDefaultOrderOnColumns(List("name", "pp")),
              where = Some(where(greaterThan(function("size", varFor("pp")), literalInt(1))))
            )),
            fromCypher5
          )(pos),
          return_(variableReturnItem("name"))
        ),
      comparePosition = false
    )
  }

  test(
    "USE db SHOW CONSTRAINTS YIELD name, populationPercent AS pp ORDER BY pp SKIP 2 LIMIT 5 WHERE pp < 50.0 RETURN name"
  ) {
    assertAstVersionBased(
      fromCypher5 =>
        singleQuery(
          use(List("db"), !fromCypher5),
          ShowConstraintsClause(
            AllConstraints,
            None,
            List(
              commandResultItem("name"),
              commandResultItem("populationPercent", Some("pp"))
            ),
            yieldAll = false,
            Some(withFromYield(
              returnAllItems.withDefaultOrderOnColumns(List("name", "pp")),
              Some(orderBy(sortItem(varFor("pp")))),
              Some(skip(2)),
              Some(limit(5)),
              Some(where(lessThan(varFor("pp"), literalFloat(50.0))))
            )),
            fromCypher5
          )(pos),
          return_(variableReturnItem("name"))
        ),
      comparePosition = false
    )
  }

  test("SHOW PROPERTY EXISTENCE CONSTRAINTS YIELD name AS CONSTRAINT, type AS OUTPUT") {
    assertAstVersionBased(
      fromCypher5 =>
        singleQuery(
          ShowConstraintsClause(
            if (fromCypher5) PropExistsConstraints.cypher5 else PropExistsConstraints.cypher25,
            None,
            List(
              commandResultItem("name", Some("CONSTRAINT")),
              commandResultItem("type", Some("OUTPUT"))
            ),
            yieldAll = false,
            Some(withFromYield(returnAllItems.withDefaultOrderOnColumns(List("CONSTRAINT", "OUTPUT")))),
            fromCypher5
          )(pos)
        ),
      comparePosition = false
    )
  }

  test("SHOW NODE EXIST CONSTRAINTS WHERE name = 'GRANT'") {
    assertAstVersionBased(
      fromCypher5 =>
        singleQuery(ShowConstraintsClause(
          NodeAllExistsConstraints,
          Some(where(equals(varFor("name"), literalString("GRANT")))),
          List.empty,
          yieldAll = false,
          None,
          fromCypher5
        )(pos)),
      comparePosition = false
    )
  }

  test("SHOW CONSTRAINTS YIELD a ORDER BY a WHERE a = 1") {
    assertAstVersionBased(fromCypher5 =>
      singleQuery(
        ShowConstraintsClause(
          AllConstraints,
          None,
          List(commandResultItem("a")),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("a")),
            Some(orderBy(sortItem(varFor("a")))),
            where = Some(where(equals(varFor("a"), literalInt(1))))
          )),
          fromCypher5
        )(pos)
      )
    )
  }

  test("SHOW CONSTRAINTS YIELD a AS b ORDER BY b WHERE b = 1") {
    assertAstVersionBased(fromCypher5 =>
      singleQuery(
        ShowConstraintsClause(
          AllConstraints,
          None,
          List(commandResultItem("a", Some("b"))),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("b")),
            Some(orderBy(sortItem(varFor("b")))),
            where = Some(where(equals(varFor("b"), literalInt(1))))
          )),
          fromCypher5
        )(pos)
      )
    )
  }

  test("SHOW CONSTRAINTS YIELD a AS b ORDER BY a WHERE a = 1") {
    assertAstVersionBased(fromCypher5 =>
      singleQuery(
        ShowConstraintsClause(
          AllConstraints,
          None,
          List(commandResultItem("a", Some("b"))),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("b")),
            Some(orderBy(sortItem(varFor("b")))),
            where = Some(where(equals(varFor("b"), literalInt(1))))
          )),
          fromCypher5
        )(pos)
      )
    )
  }

  test("SHOW CONSTRAINTS YIELD a ORDER BY EXISTS { (a) } WHERE EXISTS { (a) }") {
    assertAstVersionBased(fromCypher5 =>
      singleQuery(
        ShowConstraintsClause(
          AllConstraints,
          None,
          List(commandResultItem("a")),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("a")),
            Some(orderBy(sortItem(simpleExistsExpression(patternForMatch(nodePat(Some("a"))), None)))),
            where = Some(where(simpleExistsExpression(patternForMatch(nodePat(Some("a"))), None)))
          )),
          fromCypher5
        )(pos)
      )
    )
  }

  test("SHOW CONSTRAINTS YIELD a ORDER BY EXISTS { (b) } WHERE EXISTS { (b) }") {
    assertAstVersionBased(fromCypher5 =>
      singleQuery(
        ShowConstraintsClause(
          AllConstraints,
          None,
          List(commandResultItem("a")),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("a")),
            Some(orderBy(sortItem(simpleExistsExpression(patternForMatch(nodePat(Some("b"))), None)))),
            where = Some(where(simpleExistsExpression(patternForMatch(nodePat(Some("b"))), None)))
          )),
          fromCypher5
        )(pos)
      )
    )
  }

  test("SHOW CONSTRAINTS YIELD a AS b ORDER BY COUNT { (b) } WHERE EXISTS { (b) }") {
    assertAstVersionBased(fromCypher5 =>
      singleQuery(
        ShowConstraintsClause(
          AllConstraints,
          None,
          List(commandResultItem("a", Some("b"))),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("b")),
            Some(orderBy(sortItem(simpleCountExpression(patternForMatch(nodePat(Some("b"))), None)))),
            where = Some(where(simpleExistsExpression(patternForMatch(nodePat(Some("b"))), None)))
          )),
          fromCypher5
        )(pos)
      )
    )
  }

  test("SHOW CONSTRAINTS YIELD a AS b ORDER BY EXISTS { (a) } WHERE COLLECT { MATCH (a) RETURN a } <> []") {
    assertAstVersionBased(fromCypher5 =>
      singleQuery(
        ShowConstraintsClause(
          AllConstraints,
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
                return__(returnItem(varFor("b"), "a"))
              ),
              listOf()
            )))
          )),
          fromCypher5
        )(pos)
      )
    )
  }

  test("SHOW CONSTRAINTS YIELD a AS b ORDER BY b + COUNT { () } WHERE b OR EXISTS { () }") {
    assertAstVersionBased(fromCypher5 =>
      singleQuery(
        ShowConstraintsClause(
          AllConstraints,
          None,
          List(commandResultItem("a", Some("b"))),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("b")),
            Some(orderBy(sortItem(add(varFor("b"), simpleCountExpression(patternForMatch(nodePat()), None))))),
            where = Some(where(or(varFor("b"), simpleExistsExpression(patternForMatch(nodePat()), None))))
          )),
          fromCypher5
        )(pos)
      )
    )
  }

  test("SHOW CONSTRAINTS YIELD a AS b ORDER BY a + EXISTS { () } WHERE a OR ALL (x IN [1, 2] WHERE x IS :: INT)") {
    assertAstVersionBased(fromCypher5 =>
      singleQuery(
        ShowConstraintsClause(
          AllConstraints,
          None,
          List(commandResultItem("a", Some("b"))),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("b")),
            Some(orderBy(sortItem(add(varFor("b"), simpleExistsExpression(patternForMatch(nodePat()), None))))),
            where = Some(where(or(
              varFor("b"),
              AllIterablePredicate(
                varFor("x"),
                listOfInt(1, 2),
                Some(isTyped(varFor("x"), IntegerType(isNullable = true)(pos)))
              )(pos)
            )))
          )),
          fromCypher5
        )(pos)
      )
    )
  }

  test("SHOW CONSTRAINTS YIELD name as options, properties as name where size(name) > 0 RETURN options as name") {
    assertAstVersionBased(fromCypher5 =>
      singleQuery(
        ShowConstraintsClause(
          AllConstraints,
          None,
          List(
            commandResultItem("name", Some("options")),
            commandResultItem("properties", Some("name"))
          ),
          yieldAll = false,
          Some(withFromYield(
            returnAllItems.withDefaultOrderOnColumns(List("options", "name")),
            where = Some(where(
              greaterThan(size(varFor("name")), literalInt(0))
            ))
          )),
          fromCypher5
        )(pos),
        return_(aliasedReturnItem("options", "name"))
      )
    )
  }

  // Negative tests for show constraints

  test("SHOW ALL KEY CONSTRAINTS") {
    failsParsing[Statements]
  }

  test("SHOW NODE CONSTRAINTS") {
    failsParsing[Statements]
  }

  test("SHOW NODES EXIST CONSTRAINTS") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxError(
          """|Invalid input 'NODES': expected 'ALIAS', 'ALIASES', 'ALL', 'BTREE', 'CONSTRAINT', 'CONSTRAINTS', 'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'EXISTS', 'FULLTEXT', 'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', 'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', 'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', 'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'CURRENT USER', 'USERS' or 'VECTOR' (line 1, column 6 (offset: 5))
             |"SHOW NODES EXIST CONSTRAINTS"
             |      ^""".stripMargin
        )
      case _ => _.withSyntaxError(
          """|Invalid input 'NODES': expected 'ALIAS', 'ALIASES', 'ALL', 'CONSTRAINT', 'CONSTRAINTS', 'CURRENT', 'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'FULLTEXT', 'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', 'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', 'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', 'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'USERS' or 'VECTOR' (line 1, column 6 (offset: 5))
             |"SHOW NODES EXIST CONSTRAINTS"
             |      ^""".stripMargin
        )
    }
  }

  test("SHOW RELATIONSHIP CONSTRAINTS") {
    failsParsing[Statements]
  }

  test("SHOW RELATIONSHIPS EXIST CONSTRAINTS") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxError(
          """|Invalid input 'RELATIONSHIPS': expected 'ALIAS', 'ALIASES', 'ALL', 'BTREE', 'CONSTRAINT', 'CONSTRAINTS', 'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'EXISTS', 'FULLTEXT', 'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', 'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', 'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', 'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'CURRENT USER', 'USERS' or 'VECTOR' (line 1, column 6 (offset: 5))
             |"SHOW RELATIONSHIPS EXIST CONSTRAINTS"
             |      ^""".stripMargin
        )
      case _ => _.withSyntaxError(
          """|Invalid input 'RELATIONSHIPS': expected 'ALIAS', 'ALIASES', 'ALL', 'CONSTRAINT', 'CONSTRAINTS', 'CURRENT', 'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'FULLTEXT', 'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', 'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', 'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', 'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'USERS' or 'VECTOR' (line 1, column 6 (offset: 5))
             |"SHOW RELATIONSHIPS EXIST CONSTRAINTS"
             |      ^""".stripMargin
        )
    }
  }

  test("SHOW PROPERTY CONSTRAINTS") {
    failsParsing[Statements]
  }

  test("SHOW PROP CONSTRAINTS") {
    failsParsing[Statements]
  }

  test("SHOW PROPERTY EXISTS CONSTRAINTS") {
    failsParsing[Statements]
  }

  test("SHOW NODE TYPE CONSTRAINTS") {
    failsParsing[Statements]
  }

  test("SHOW RELATIONSHIP TYPE CONSTRAINTS") {
    failsParsing[Statements]
  }

  test("SHOW REL TYPE CONSTRAINTS") {
    failsParsing[Statements]
  }

  test("SHOW TYPE CONSTRAINTS") {
    failsParsing[Statements]
  }

  test("SHOW CONSTRAINT YIELD (123 + xyz)") {
    failsParsing[Statements]
  }

  test("SHOW CONSTRAINTS YIELD (123 + xyz) AS foo") {
    failsParsing[Statements]
  }

  test("SHOW CONSTRAINT YIELD") {
    failsParsing[Statements]
  }

  test("SHOW CONSTRAINTS YIELD * YIELD *") {
    failsParsing[Statements]
  }

  test("SHOW CONSTRAINTS WHERE entityType = 'NODE' YIELD *") {
    failsParsing[Statements]
  }

  test("SHOW CONSTRAINTS WHERE entityType = 'NODE' RETURN *") {
    failsParsing[Statements]
  }

  test("SHOW CONSTRAINTS YIELD a b RETURN *") {
    failsParsing[Statements]
  }

  test("SHOW CONSTRAINTS WHERE true RETURN *") {
    failsParsing[Statements].withSyntaxErrorContaining("Invalid input 'RETURN'")
  }

  test("SHOW EXISTENCE CONSTRAINT RETURN *") {
    failsParsing[Statements]
  }

  test("SHOW UNKNOWN CONSTRAINTS") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxError(
          """|Invalid input 'UNKNOWN': expected 'ALIAS', 'ALIASES', 'ALL', 'BTREE', 'CONSTRAINT', 'CONSTRAINTS', 'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'EXISTS', 'FULLTEXT', 'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', 'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', 'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', 'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'CURRENT USER', 'USERS' or 'VECTOR' (line 1, column 6 (offset: 5))
             |"SHOW UNKNOWN CONSTRAINTS"
             |      ^""".stripMargin
        )
      case _ => _.withSyntaxError(
          """|Invalid input 'UNKNOWN': expected 'ALIAS', 'ALIASES', 'ALL', 'CONSTRAINT', 'CONSTRAINTS', 'CURRENT', 'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', 'EXIST', 'EXISTENCE', 'FULLTEXT', 'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', 'PRIVILEGE', 'PRIVILEGES', 'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', 'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', 'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'USERS' or 'VECTOR' (line 1, column 6 (offset: 5))
             |"SHOW UNKNOWN CONSTRAINTS"
             |      ^""".stripMargin
        )
    }
  }

  test("SHOW BUILT IN CONSTRAINTS") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'CONSTRAINTS': expected 'FUNCTION' or 'FUNCTIONS' (line 1, column 15 (offset: 14))
        |"SHOW BUILT IN CONSTRAINTS"
        |               ^""".stripMargin
    )
  }

  // Removed syntax (also includes parts using it that was invalid anyway)

  // group of initial constraint types that were removed in 5.0 with nice error
  private val removedConstraintTypes = Seq("EXISTS", "NODE EXISTS", "RELATIONSHIP EXISTS")

  removedConstraintTypes.foreach(constraintTypeKeyword => {
    test(s"SHOW $constraintTypeKeyword CONSTRAINT") {
      failsParsing[Statements].in {
        case Cypher5 => _.withOldSyntax(
            "`SHOW CONSTRAINTS` no longer allows the `EXISTS` keyword, please use `EXIST` or `PROPERTY EXISTENCE` instead."
          )
        case _ => // Expected will differ depending on type
          _.withSyntaxErrorContaining("Invalid input 'EXISTS': expected ")
      }
    }

    test(s"USE db SHOW $constraintTypeKeyword CONSTRAINTS") {
      failsParsing[Statements].in {
        case Cypher5 => _.withOldSyntax(
            "`SHOW CONSTRAINTS` no longer allows the `EXISTS` keyword, please use `EXIST` or `PROPERTY EXISTENCE` instead."
          )
        case _ => // Expected will differ depending on type
          _.withSyntaxErrorContaining("Invalid input 'EXISTS': expected ")
      }
    }

    test(s"SHOW $constraintTypeKeyword CONSTRAINT BRIEF") {
      failsParsing[Statements].in {
        case Cypher5 => _.withOldSyntax(
            "`SHOW CONSTRAINTS` no longer allows the `EXISTS` keyword, please use `EXIST` or `PROPERTY EXISTENCE` instead."
          )
        case _ => // Expected will differ depending on type
          _.withSyntaxErrorContaining("Invalid input 'EXISTS': expected ")
      }
    }

    test(s"SHOW $constraintTypeKeyword CONSTRAINTS BRIEF OUTPUT") {
      failsParsing[Statements].in {
        case Cypher5 => _.withOldSyntax(
            "`SHOW CONSTRAINTS` no longer allows the `EXISTS` keyword, please use `EXIST` or `PROPERTY EXISTENCE` instead."
          )
        case _ => // Expected will differ depending on type
          _.withSyntaxErrorContaining("Invalid input 'EXISTS': expected ")
      }
    }

    test(s"SHOW $constraintTypeKeyword CONSTRAINTS VERBOSE") {
      failsParsing[Statements].in {
        case Cypher5 => _.withOldSyntax(
            "`SHOW CONSTRAINTS` no longer allows the `EXISTS` keyword, please use `EXIST` or `PROPERTY EXISTENCE` instead."
          )
        case _ => // Expected will differ depending on type
          _.withSyntaxErrorContaining("Invalid input 'EXISTS': expected ")
      }
    }

    test(s"SHOW $constraintTypeKeyword CONSTRAINT VERBOSE OUTPUT") {
      failsParsing[Statements].in {
        case Cypher5 => _.withOldSyntax(
            "`SHOW CONSTRAINTS` no longer allows the `EXISTS` keyword, please use `EXIST` or `PROPERTY EXISTENCE` instead."
          )
        case _ => // Expected will differ depending on type
          _.withSyntaxErrorContaining("Invalid input 'EXISTS': expected ")
      }
    }
  })

  test("SHOW ALL EXISTS CONSTRAINTS") {
    failsParsing[Statements]
  }

  test("SHOW EXISTS NODE CONSTRAINTS") {
    failsParsing[Statements]
  }

  test("SHOW EXISTS RELATIONSHIP CONSTRAINTS") {
    failsParsing[Statements]
  }

  test("SHOW REL EXISTS CONSTRAINTS") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input 'EXISTS': expected 'EXIST', 'EXISTENCE', 'KEY', 'PROPERTY', 'UNIQUE' or 'UNIQUENESS' (line 1, column 10 (offset: 9))
        |"SHOW REL EXISTS CONSTRAINTS"
        |          ^""".stripMargin
    )
  }

  test("SHOW EXISTS CONSTRAINT WHERE name = 'foo'") {
    failsParsing[Statements]
  }

  test("SHOW NODE EXISTS CONSTRAINT WHERE name = 'foo'") {
    failsParsing[Statements]
  }

  test("SHOW RELATIONSHIP EXISTS CONSTRAINT WHERE name = 'foo'") {
    failsParsing[Statements]
  }

  test("SHOW EXISTS CONSTRAINT YIELD *") {
    failsParsing[Statements]
  }

  test("SHOW NODE EXISTS CONSTRAINT YIELD *") {
    failsParsing[Statements]
  }

  test("SHOW RELATIONSHIP EXISTS CONSTRAINT YIELD name") {
    failsParsing[Statements]
  }

  test("SHOW EXISTS CONSTRAINT RETURN *") {
    failsParsing[Statements]
  }

  constraintTypesV1.foreach {
    case (constraintTypeKeyword, _) =>
      test(s"SHOW $constraintTypeKeyword CONSTRAINTS BRIEF") {
        assertFailsOnBriefVerbosePreviouslyAllowed("SHOW CONSTRAINTS", "BRIEF")
      }

      test(s"SHOW $constraintTypeKeyword CONSTRAINT BRIEF OUTPUT") {
        assertFailsOnBriefVerbosePreviouslyAllowed("SHOW CONSTRAINTS", "BRIEF")
      }

      test(s"SHOW $constraintTypeKeyword CONSTRAINT VERBOSE") {
        assertFailsOnBriefVerbosePreviouslyAllowed("SHOW CONSTRAINTS", "VERBOSE")
      }

      test(s"SHOW $constraintTypeKeyword CONSTRAINTS VERBOSE OUTPUT") {
        assertFailsOnBriefVerbosePreviouslyAllowed("SHOW CONSTRAINTS", "VERBOSE")
      }
  }

  constraintTypeV2.foreach {
    case (constraintTypeKeyword, _) =>
      test(s"SHOW $constraintTypeKeyword CONSTRAINTS BRIEF") {
        assertFailsOnBriefVerboseNeverAllowed("BRIEF")
      }

      test(s"SHOW $constraintTypeKeyword CONSTRAINT BRIEF OUTPUT") {
        assertFailsOnBriefVerboseNeverAllowed("BRIEF")
      }

      test(s"SHOW $constraintTypeKeyword CONSTRAINT VERBOSE") {
        assertFailsOnBriefVerboseNeverAllowed("VERBOSE")
      }

      test(s"SHOW $constraintTypeKeyword CONSTRAINTS VERBOSE OUTPUT") {
        assertFailsOnBriefVerboseNeverAllowed("VERBOSE")
      }
  }

  constraintTypeV3.foreach {
    case (constraintTypeKeyword, _) =>
      test(s"SHOW $constraintTypeKeyword CONSTRAINTS BRIEF") {
        assertFailsOnBriefVerboseWhenIntroducedInCypher25("BRIEF", constraintTypeKeyword)
      }

      test(s"SHOW $constraintTypeKeyword CONSTRAINT BRIEF OUTPUT") {
        assertFailsOnBriefVerboseWhenIntroducedInCypher25("BRIEF", constraintTypeKeyword)
      }

      test(s"SHOW $constraintTypeKeyword CONSTRAINT VERBOSE") {
        assertFailsOnBriefVerboseWhenIntroducedInCypher25("VERBOSE", constraintTypeKeyword)
      }

      test(s"SHOW $constraintTypeKeyword CONSTRAINTS VERBOSE OUTPUT") {
        assertFailsOnBriefVerboseWhenIntroducedInCypher25("VERBOSE", constraintTypeKeyword)
      }
  }

  test("SHOW CONSTRAINTS OUTPUT") {
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxErrorContaining(
          "Invalid input 'OUTPUT': expected 'BRIEF', 'SHOW', 'TERMINATE', 'VERBOSE', 'WHERE', 'YIELD' or <EOF>"
        )
      case _ =>
        _.withSyntaxErrorContaining("Invalid input 'OUTPUT': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>")
    }
  }

  test("SHOW CONSTRAINTS VERBOSE BRIEF OUTPUT") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW CONSTRAINTS", "VERBOSE")
  }

  test("SHOW CONSTRAINTS BRIEF YIELD *") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW CONSTRAINTS", "BRIEF")
  }

  test("SHOW CONSTRAINTS VERBOSE YIELD *") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW CONSTRAINTS", "VERBOSE")
  }

  test("SHOW CONSTRAINTS BRIEF RETURN *") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW CONSTRAINTS", "BRIEF")
  }

  test("SHOW CONSTRAINTS VERBOSE RETURN *") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW CONSTRAINTS", "VERBOSE")
  }

  test("SHOW CONSTRAINTS BRIEF WHERE entityType = 'NODE'") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW CONSTRAINTS", "BRIEF")
  }

  test("SHOW CONSTRAINTS VERBOSE WHERE entityType = 'NODE'") {
    assertFailsOnBriefVerbosePreviouslyAllowed("SHOW CONSTRAINTS", "VERBOSE")
  }

  // Invalid clause order tests for indexes and constraints

  for {
    prefix <- Seq("USE neo4j", "")
    entity <- Seq("INDEXES", "CONSTRAINTS")
  } {
    test(s"$prefix SHOW $entity YIELD * WITH * MATCH (n) RETURN n") {
      // Can't parse WITH after SHOW
      failsParsing[Statements].withSyntaxErrorContaining(
        """Invalid input 'WITH': expected 'ORDER BY'""".stripMargin
      )
    }

    test(s"$prefix UNWIND range(1,10) as b SHOW $entity YIELD * RETURN *") {
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

    test(s"$prefix SHOW $entity WITH name, type RETURN *") {
      // Can't parse WITH after SHOW
      failsParsing[Statements].in {
        case Cypher5 => _.withSyntaxErrorContaining(
            """Invalid input 'WITH': expected 'BRIEF', 'SHOW', 'TERMINATE', 'VERBOSE', 'WHERE', 'YIELD' or <EOF>"""
          )
        case _ => _.withSyntaxErrorContaining(
            """Invalid input 'WITH': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>"""
          )
      }
    }

    test(s"$prefix WITH 'n' as n SHOW $entity YIELD name RETURN name as numIndexes") {
      parsesIn[Statements] {
        case Cypher5 => _.withSyntaxErrorContaining(
            """Invalid input 'SHOW': expected ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FINISH', 'FOREACH', 'INSERT', 'LIMIT', 'MATCH', 'MERGE', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WHERE', 'WITH' or <EOF>"""
          )
        case _ => _.withSyntaxErrorContaining(
            """Invalid input 'SHOW': expected ',', 'ORDER BY', 'CALL', 'CREATE', 'LOAD CSV', 'DELETE', 'DETACH', 'FILTER', 'FINISH', 'FOREACH', 'INSERT', 'LET', 'LIMIT', 'MATCH', 'MERGE', 'NEXT', 'NODETACH', 'OFFSET', 'OPTIONAL', 'REMOVE', 'RETURN', 'SET', 'SKIP', 'UNION', 'UNWIND', 'USE', 'WHERE', 'WITH' or <EOF>"""
          )
      }
    }

    test(s"$prefix SHOW $entity RETURN name as numIndexes") {
      failsParsing[Statements].in {
        case Cypher5 => _.withSyntaxErrorContaining(
            """Invalid input 'RETURN': expected 'BRIEF', 'SHOW', 'TERMINATE', 'VERBOSE', 'WHERE', 'YIELD' or <EOF>"""
          )
        case _ => _.withSyntaxErrorContaining(
            """Invalid input 'RETURN': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>"""
          )
      }
    }

    test(s"$prefix SHOW $entity WITH 1 as c RETURN name as numIndexes") {
      failsParsing[Statements].in {
        case Cypher5 => _.withSyntaxErrorContaining(
            """Invalid input 'WITH': expected 'BRIEF', 'SHOW', 'TERMINATE', 'VERBOSE', 'WHERE', 'YIELD' or <EOF>"""
          )
        case _ => _.withSyntaxErrorContaining(
            """Invalid input 'WITH': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>"""
          )
      }
    }

    test(s"$prefix SHOW $entity WITH 1 as c") {
      failsParsing[Statements].in {
        case Cypher5 => _.withSyntaxErrorContaining(
            """Invalid input 'WITH': expected 'BRIEF', 'SHOW', 'TERMINATE', 'VERBOSE', 'WHERE', 'YIELD' or <EOF>"""
          )
        case _ => _.withSyntaxErrorContaining(
            """Invalid input 'WITH': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>"""
          )
      }
    }

    test(s"$prefix SHOW $entity YIELD a WITH a RETURN a") {
      failsParsing[Statements].withSyntaxErrorContaining(
        """Invalid input 'WITH': expected ',', 'AS', 'ORDER BY', 'LIMIT', 'OFFSET', 'RETURN', 'SHOW', 'SKIP', 'TERMINATE', 'WHERE' or <EOF>"""
      )
    }

    test(s"$prefix SHOW $entity YIELD as UNWIND as as a RETURN a") {
      failsParsing[Statements].withSyntaxErrorContaining(
        """Invalid input 'UNWIND': expected ',', 'AS', 'ORDER BY', 'LIMIT', 'OFFSET', 'RETURN', 'SHOW', 'SKIP', 'TERMINATE', 'WHERE' or <EOF>"""
      )
    }

    test(s"$prefix SHOW $entity RETURN name2 YIELD name2") {
      failsParsing[Statements].in {
        case Cypher5 => _.withSyntaxErrorContaining(
            """Invalid input 'RETURN': expected 'BRIEF', 'SHOW', 'TERMINATE', 'VERBOSE', 'WHERE', 'YIELD' or <EOF>"""
          )
        case _ => _.withSyntaxErrorContaining(
            """Invalid input 'RETURN': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>"""
          )
      }
    }
  }

  // Help methods

  private def assertFailsOnBriefVerbosePreviouslyAllowed(
    command: String,
    keyword: String,
    failOnBtree: Boolean = false
  ) = {
    failsParsing[Statements].in {
      case Cypher5 => _.withOldSyntax(
          s"""`$command` no longer allows the `BRIEF` and `VERBOSE` keywords,
             |please omit `BRIEF` and use `YIELD *` instead of `VERBOSE`.""".stripMargin
        )
      case _ if failOnBtree =>
        _.withSyntaxErrorContaining(
          "Invalid input 'BTREE': expected 'ALIAS', 'ALIASES', 'ALL', 'CONSTRAINT', 'CONSTRAINTS', 'CURRENT', 'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', " +
            "'EXIST', 'EXISTENCE', 'FULLTEXT', 'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', 'PRIVILEGE', 'PRIVILEGES', " +
            "'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', 'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', " +
            "'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'USERS' or 'VECTOR'"
        )
      case _ => _.withSyntaxErrorContaining(
          s"Invalid input '$keyword': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>"
        )
    }
  }

  private def assertFailsOnBriefVerboseNeverAllowed(keyword: String) = {
    failsParsing[Statements]
      .withSyntaxErrorContaining(
        s"Invalid input '$keyword': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>"
      )
  }

  private def assertFailsOnBriefVerboseWhenIntroducedInCypher25(keyword: String, constraintTypeKeyword: String) = {
    val errorKeyword = constraintTypeKeyword.split(" ").last
    failsParsing[Statements].in {
      case Cypher5 => _.withSyntaxErrorContaining(
          s"Invalid input '$errorKeyword': expected 'EXIST', 'EXISTENCE' or 'TYPE' (line"
        )
      case _ => _.withSyntaxErrorContaining(
          s"Invalid input '$keyword': expected 'SHOW', 'TERMINATE', 'WHERE', 'YIELD' or <EOF>"
        )
    }
  }

  private def assertFailsOnBtree() = {
    failsParsing[Statements].in {
      case Cypher5 => _.withOldSyntax("Invalid index type b-tree, please omit the `BTREE` filter.")
      case _ => _.withSyntaxErrorContaining(
          "Invalid input 'BTREE': expected 'ALIAS', 'ALIASES', 'ALL', 'CONSTRAINT', 'CONSTRAINTS', 'CURRENT', 'DATABASE', 'DEFAULT DATABASE', 'HOME DATABASE', 'DATABASES', " +
            "'EXIST', 'EXISTENCE', 'FULLTEXT', 'FUNCTION', 'FUNCTIONS', 'BUILT IN', 'INDEX', 'INDEXES', 'KEY', 'LOOKUP', 'NODE', 'POINT', 'POPULATED', 'PRIVILEGE', 'PRIVILEGES', " +
            "'PROCEDURE', 'PROCEDURES', 'PROPERTY', 'RANGE', 'REL', 'RELATIONSHIP', 'ROLE', 'ROLES', 'SERVER', 'SERVERS', 'SETTING', 'SETTINGS', 'SUPPORTED', 'TEXT', " +
            "'TRANSACTION', 'TRANSACTIONS', 'UNIQUE', 'UNIQUENESS', 'USER', 'USERS' or 'VECTOR'"
        )
    }
  }
}
