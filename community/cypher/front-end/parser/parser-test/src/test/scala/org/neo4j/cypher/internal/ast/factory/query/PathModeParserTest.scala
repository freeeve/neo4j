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
import org.neo4j.cypher.internal.expressions.MatchMode
import org.neo4j.cypher.internal.expressions.PathMode.Acyclic
import org.neo4j.cypher.internal.expressions.PathMode.Trail
import org.neo4j.cypher.internal.expressions.PathMode.Walk
import org.neo4j.cypher.internal.expressions.PathPatternPart
import org.neo4j.cypher.internal.expressions.PlusQuantifier
import org.neo4j.cypher.internal.expressions.QuantifiedPath
import org.neo4j.cypher.internal.expressions.RelationshipChain

class PathModeParserTest extends AstParsingTestBase {

  test("MATCH (n)-->(m)") {
    parsesTo[Clause] {
      match_pathPatternPrefix(
        pathMode = Walk(implicitlyCreated = true)(pos),
        pattern = RelationshipChain(nodePat(Some("n")), relPat(None), nodePat(Some("m")))(pos)
      )
    }
  }

  test("MATCH WALK (n)-->(m)") {
    parsesIn[Clause] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstPositioned(
          match_pathPatternPrefix(
            pathMode = Walk()(pos),
            pattern = RelationshipChain(nodePat(Some("n")), relPat(None), nodePat(Some("m")))(pos)
          )
        )
    }
  }

  test("MATCH TRAIL (n)-->(m)") {
    parsesIn[Clause] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstPositioned(
          match_pathPatternPrefix(
            pathMode = Trail()(pos),
            pattern = RelationshipChain(nodePat(Some("n")), relPat(None), nodePat(Some("m")))(pos)
          )
        )
    }
  }

  test("MATCH ACYCLIC (n)-->(m)") {
    parsesIn[Clause] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstPositioned(
          match_pathPatternPrefix(
            pathMode = Acyclic()(pos),
            pattern = RelationshipChain(nodePat(Some("n")), relPat(None), nodePat(Some("m")))(pos)
          )
        )
    }
  }

  test("CREATE TRAIL (:A)-[:REL]->(:B)-[:REL]->(:C)") {
    failsParsing[Clause].withMessageContaining("TRAIL")
  }

  test("INSERT WALK (:A)-[:REL]->(:B)-[:REL]->(:C)") {
    failsParsing[Clause].withMessageContaining("WALK")
  }

  test("MERGE ACYCLIC (:A)-[:REL]->(:B)-[:REL]->(:C)") {
    failsParsing[Clause].withMessageContaining("ACYCLIC")
  }

  test("RETURN [WALK (n)-[*]->(m) | n]") {
    failsParsing[Clause].withMessageContaining("WALK")
  }

  test("MATCH ANY ACYCLIC (n)-->(m)") {
    parsesIn[Clause] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstPositioned(
          match_pathPatternPrefix(
            pathMode = Acyclic()(pos),
            pattern = RelationshipChain(nodePat(Some("n")), relPat(None), nodePat(Some("m")))(pos),
            selector = anyPathSelector(1)
          )
        )
    }
  }

  test("MATCH SHORTEST 25 ACYCLIC PATH GROUPS (n)-->(m)") {
    parsesIn[Clause] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstPositioned(
          match_pathPatternPrefix(
            pathMode = Acyclic()(pos),
            pattern = RelationshipChain(nodePat(Some("n")), relPat(None), nodePat(Some("m")))(pos),
            selector = shortestGroups(25)
          )
        )
    }
  }

  test("MATCH SHORTEST 25 PATH GROUPS ACYCLIC (n)-->(m)") {
    failsParsing[Clause].withMessageContaining("ACYCLIC")
  }

  test("MATCH p = ACYCLIC (n)-->(m)") {
    parsesIn[Clause] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstPositioned(
          match_pathPatternPrefix(
            pathMode = Acyclic()(pos),
            pattern = RelationshipChain(nodePat(Some("n")), relPat(None), nodePat(Some("m")))(pos),
            pathVariable = Some("p")
          )
        )
    }
  }

  test("MATCH ACYCLIC p = (n)-->(m)") {
    failsParsing[Clause].withMessageContaining("ACYCLIC")
  }

  test("MATCH DIFFERENT RELATIONSHIPS ACYCLIC PATH ((n)-->(m))+") {
    parsesIn[Clause] {
      case Cypher5 => _.withAnyFailure
      case _ => _.toAstPositioned(
          match_pathPatternPrefix(
            pathMode = Acyclic()(pos),
            pattern = QuantifiedPath(
              part = PathPatternPart(RelationshipChain(nodePat(Some("n")), relPat(None), nodePat(Some("m")))(pos)),
              quantifier = PlusQuantifier()(pos),
              optionalWhereExpression = None
            )(pos),
            matchMode = MatchMode.DifferentRelationships()(pos)
          )
        )
    }
  }
}
