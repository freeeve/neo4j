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
import org.neo4j.cypher.internal.ast.Match
import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.ast.test.util.LegacyAstParsingTestSupport
import org.neo4j.cypher.internal.expressions.MatchMode
import org.neo4j.cypher.internal.expressions.MatchMode.DifferentRelationships
import org.neo4j.cypher.internal.expressions.MatchMode.RepeatableElements
import org.neo4j.cypher.internal.expressions.NamedPatternPart
import org.neo4j.cypher.internal.expressions.NodePattern
import org.neo4j.cypher.internal.expressions.PathPatternPart
import org.neo4j.cypher.internal.expressions.Pattern.ForMatch
import org.neo4j.cypher.internal.expressions.PatternPart.AllPaths
import org.neo4j.cypher.internal.expressions.PrefixedPatternPart
import org.neo4j.cypher.internal.expressions.RelationshipChain
import org.neo4j.cypher.internal.expressions.RelationshipPattern
import org.neo4j.cypher.internal.expressions.SemanticDirection.OUTGOING

import scala.collection.immutable.ArraySeq

class MatchModeParserTest extends AstParsingTestBase with LegacyAstParsingTestSupport {

  test("MATCH DIFFERENT RELATIONSHIPS (n)-->(m)") {
    parsesTo[Clause] {
      match_(
        relationshipChain(nodePat(Some("n")), relPat(), nodePat(Some("m"))),
        matchMode = MatchMode.DifferentRelationships()(pos)
      )
    }
  }

  test("MATCH DIFFERENT RELATIONSHIP BINDINGS (n)-->(m)") {
    parsesTo[Clause] {
      match_(
        relationshipChain(nodePat(Some("n")), relPat(), nodePat(Some("m"))),
        matchMode = MatchMode.DifferentRelationships()(pos)
      )
    }
  }

  test("MATCH DIFFERENT RELATIONSHIP (n)-->(m)") {
    parsesTo[Clause] {
      match_(
        relationshipChain(nodePat(Some("n")), relPat(), nodePat(Some("m"))),
        matchMode = MatchMode.DifferentRelationships()(pos)
      )
    }
  }

  test("MATCH DIFFERENT RELATIONSHIPS BINDINGS (n)-->(m) RETURN *") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input '(': expected a graph pattern (line 1, column 40 (offset: 39))
        |"MATCH DIFFERENT RELATIONSHIPS BINDINGS (n)-->(m) RETURN *"
        |                                        ^""".stripMargin
    )
  }

  test("MATCH REPEATABLE ELEMENTS (n)-->(m)") {
    parsesTo[Clause] {
      match_(
        relationshipChain(nodePat(Some("n")), relPat(), nodePat(Some("m"))),
        matchMode = MatchMode.RepeatableElements()(pos)
      )
    }
  }

  test("MATCH REPEATABLE ELEMENT BINDINGS (n)-->(m)") {
    parsesTo[Clause] {
      match_(
        relationshipChain(nodePat(Some("n")), relPat(), nodePat(Some("m"))),
        matchMode = MatchMode.RepeatableElements()(pos)
      )
    }
  }

  test("MATCH REPEATABLE ELEMENT (n)-->(m)") {
    parsesTo[Clause] {
      match_(
        relationshipChain(nodePat(Some("n")), relPat(), nodePat(Some("m"))),
        matchMode = MatchMode.RepeatableElements()(pos)
      )
    }
  }

  test("MATCH REPEATABLE ELEMENTS BINDINGS (n)-->(m) RETURN *") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input '(': expected a graph pattern (line 1, column 36 (offset: 35))
        |"MATCH REPEATABLE ELEMENTS BINDINGS (n)-->(m) RETURN *"
        |                                    ^""".stripMargin
    )
  }

  test("MATCH REPEATABLE ELEMENT ELEMENTS (n)-->(m) RETURN *") {
    failsParsing[Statements].withSyntaxError(
      """Invalid input '(': expected a graph pattern (line 1, column 35 (offset: 34))
        |"MATCH REPEATABLE ELEMENT ELEMENTS (n)-->(m) RETURN *"
        |                                   ^""".stripMargin
    )
  }

  test("MATCH () WHERE EXISTS {REPEATABLE ELEMENTS (n)-->(m)}") {
    val pattern = patternForMatch(relationshipChain(nodePat(Some("n")), relPat(), nodePat(Some("m"))))
    parsesTo[Clause] {
      match_(
        nodePat(),
        where = Some(where(simpleExistsExpression(pattern, None, matchMode = MatchMode.RepeatableElements()(pos))))
      )
    }
  }

  test("MATCH () WHERE EXISTS {MATCH REPEATABLE ELEMENTS (n)-->(m)}") {
    val pattern = patternForMatch(relationshipChain(nodePat(Some("n")), relPat(), nodePat(Some("m"))))
    parsesTo[Clause] {
      match_(
        nodePat(),
        where = Some(where(simpleExistsExpression(pattern, None, matchMode = MatchMode.RepeatableElements()(pos))))
      )
    }
  }

  test("MATCH () WHERE EXISTS {(n)-->(m)}") {
    val pattern = patternForMatch(relationshipChain(nodePat(Some("n")), relPat(), nodePat(Some("m"))))
    parsesTo[Clause] {
      match_(
        nodePat(),
        where = Some(where(simpleExistsExpression(
          pattern,
          None,
          matchMode = MatchMode.DifferentRelationships(implicitlyCreated = true)(pos)
        )))
      )
    }
  }

  test("MATCH () WHERE COUNT {REPEATABLE ELEMENTS (n)-->(m)}") {
    val pattern = patternForMatch(relationshipChain(nodePat(Some("n")), relPat(), nodePat(Some("m"))))
    parsesTo[Clause] {
      match_(
        nodePat(),
        where = Some(where(simpleCountExpression(pattern, None, matchMode = MatchMode.RepeatableElements()(pos))))
      )
    }
  }

  test("MATCH () WHERE COLLECT {MATCH DIFFERENT RELATIONSHIPS (n)-->(m) RETURN *}") {
    val pattern = patternForMatch(relationshipChain(nodePat(Some("n")), relPat(), nodePat(Some("m"))))
    parsesTo[Clause] {
      match_(
        nodePat(),
        where = Some(where(simpleCollectExpression(
          pattern,
          None,
          returnAll,
          matchMode = MatchMode.DifferentRelationships()(pos)
        )))
      )
    }
  }

  test("MATCH () WHERE COLLECT {MATCH REPEATABLE ELEMENTS (n)-->(m) RETURN *}") {
    val pattern = patternForMatch(relationshipChain(nodePat(Some("n")), relPat(), nodePat(Some("m"))))
    parsesTo[Clause] {
      match_(
        nodePat(),
        where = Some(where(simpleCollectExpression(
          pattern,
          None,
          returnAll,
          matchMode = MatchMode.RepeatableElements()(pos)
        )))
      )
    }
  }

  test("MATCH REPEATABLE ELEMENTS path = (a)-[r]->(b)") {
    parsesTo[Clause] {
      Match(
        optional = false,
        matchMode = MatchMode.RepeatableElements()(pos),
        hints = List(),
        where = None,
        search = None,
        pattern = patternForMatch(NamedPatternPart(
          varFor("path"),
          PathPatternPart(
            relationshipChain(nodePat(Some("a")), relPat(Some("r")), nodePat(Some("b")))
          )
        )(pos))
      )(pos)
    }
  }

  test("MATCH REPEATABLE = (a)-[r]->(b)") {
    parsesTo[Clause] {
      Match(
        optional = false,
        matchMode = MatchMode.default(pos),
        hints = List(),
        where = None,
        search = None,
        pattern = patternForMatch(NamedPatternPart(
          varFor("REPEATABLE"),
          PathPatternPart(
            relationshipChain(nodePat(Some("a")), relPat(Some("r")), nodePat(Some("b")))
          )
        )(pos))
      )(pos)
    }
  }

  test("MATCH DIFFERENT RELATIONSHIPS path = (x)-[p]->(y)") {
    parsesTo[Clause] {
      Match(
        optional = false,
        matchMode = MatchMode.DifferentRelationships()(pos),
        hints = List(),
        where = None,
        search = None,
        pattern = patternForMatch(NamedPatternPart(
          varFor("path"),
          PathPatternPart(
            relationshipChain(nodePat(Some("x")), relPat(Some("p")), nodePat(Some("y")))
          )
        )(pos))
      )(pos)
    }
  }

  test("MATCH DIFFERENT = (x)-[p]->(y)") {
    parsesTo[Clause] {
      Match(
        optional = false,
        matchMode = MatchMode.default(pos),
        hints = List(),
        where = None,
        search = None,
        pattern = patternForMatch(NamedPatternPart(
          varFor("DIFFERENT"),
          PathPatternPart(
            relationshipChain(nodePat(Some("x")), relPat(Some("p")), nodePat(Some("y")))
          )
        )(pos))
      )(pos)
    }
  }

  test("MATCH () WHERE COLLECT {REPEATABLE ELEMENTS (n)-->(m) RETURN *} RETURN *") {
    failsParsing[Statements].withMessage(
      """Invalid input 'ELEMENTS': expected ',', ':' or '}' (line 1, column 36 (offset: 35))
        |"MATCH () WHERE COLLECT {REPEATABLE ELEMENTS (n)-->(m) RETURN *} RETURN *"
        |                                    ^""".stripMargin
    )
  }

  test("MATCH REPEATABLE ELEMENT BINDINGS = ()-->()") {
    parsesTo[Clause](Match(
      optional = false,
      RepeatableElements()(pos),
      ForMatch(ArraySeq(PrefixedPatternPart(
        AllPaths()(pos),
        NamedPatternPart(
          varFor("BINDINGS"),
          PathPatternPart(RelationshipChain(
            NodePattern(None, None, None, None)(pos),
            RelationshipPattern(None, None, None, None, None, OUTGOING)(pos),
            NodePattern(None, None, None, None)(pos)
          )(pos))
        )(pos)
      )))(pos),
      List(),
      None,
      None
    )(pos))
  }

  test("MATCH DIFFERENT RELATIONSHIP BINDINGS = ()-->()") {
    parsesTo[Clause](Match(
      optional = false,
      DifferentRelationships()(pos),
      ForMatch(ArraySeq(PrefixedPatternPart(
        AllPaths()(pos),
        NamedPatternPart(
          varFor("BINDINGS"),
          PathPatternPart(RelationshipChain(
            NodePattern(None, None, None, None)(pos),
            RelationshipPattern(None, None, None, None, None, OUTGOING)(pos),
            NodePattern(None, None, None, None)(pos)
          )(pos))
        )(pos)
      )))(pos),
      List(),
      None,
      None
    )(pos))
  }
}
