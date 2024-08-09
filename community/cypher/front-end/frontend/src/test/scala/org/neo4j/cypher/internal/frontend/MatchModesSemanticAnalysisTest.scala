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
package org.neo4j.cypher.internal.frontend

import org.neo4j.cypher.internal.ast.Ast.p
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.MatchModes
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.cypher.internal.util.test_helpers.TestName
import org.neo4j.gqlstatus.GqlHelper

class MatchModesSemanticAnalysisTest extends CypherFunSuite with SemanticAnalysisTestSuiteWithDefaultQuery
    with TestName {

  override def defaultQuery: String = s"MATCH $testName RETURN *"

  def unboundRepeatableElementsSemanticError(pos: InputPosition): SemanticError = SemanticError(
    "The pattern may yield an infinite number of rows under match mode REPEATABLE ELEMENTS, " +
      "perhaps use a path selector or add an upper bound to your quantified path patterns.",
    pos
  )

  def differentRelationshipsSelectivePathPatternSemanticError(
    pos: InputPosition,
    semanticFeatureEnabled: Boolean = true
  ): SemanticError = {
    val matchModeTip = if (semanticFeatureEnabled) {
      " You may want to use multiple MATCH clauses, or you might want to consider using the REPEATABLE ELEMENTS match mode."
    } else {
      ""
    }

    SemanticError(
      GqlHelper.getGql42001_42I45(matchModeTip, pos.line, pos.column, pos.offset),
      "Multiple path patterns cannot be used in the same clause in combination with a selective path selector." + matchModeTip,
      pos
    )
  }

  test("DIFFERENT RELATIONSHIPS (a)") {
    // running without semantic feature should fail
    run().hasErrorMessages(
      "Match modes such as `DIFFERENT RELATIONSHIPS` are not supported yet."
    )
  }

  test("(a)") {
    // running with implicit "DIFFERENT RELATIONSHIPS" match mode should not fail
    run().hasNoErrors
  }

  test("REPEATABLE ELEMENTS (a)") {
    // running without semantic feature should fail
    run().hasErrorMessages(
      "Match modes such as `REPEATABLE ELEMENTS` are not supported yet."
    )
  }

  test("DIFFERENT RELATIONSHIPS ((a)-[:REL]->(b)){2}") {
    runWith(MatchModes).hasNoErrors
  }

  test("((a)-[:REL]->(b)){2}, ((c)-[:REL]->(d))+") {
    runWith(MatchModes).hasNoErrors
  }

  test("REPEATABLE ELEMENTS ((a)-[:REL]->(b)){2}") {
    runWith(MatchModes).hasNoErrors
  }

  test("REPEATABLE ELEMENTS ((a)-[:REL]->(b)){1,}") {
    runWith(MatchModes).hasErrors(
      unboundRepeatableElementsSemanticError(p(26, 1, 27))
    )
  }

  test("REPEATABLE ELEMENTS ((a)-[:REL]->(b))+") {
    runWith(MatchModes).hasErrors(
      unboundRepeatableElementsSemanticError(p(26, 1, 27))
    )
  }

  test("REPEATABLE ELEMENTS (a)-[:REL*]->(b)") {
    runWith(MatchModes).hasErrors(
      unboundRepeatableElementsSemanticError(p(26, 1, 27))
    )
  }

  test("REPEATABLE ELEMENTS SHORTEST 2 PATH ((a)-[:REL]->(b))+") {
    runWith(MatchModes).hasNoErrors
  }

  test("REPEATABLE ELEMENTS ANY ((a)-[:REL]->(b))+") {
    runWith(MatchModes).hasNoErrors
  }

  test("REPEATABLE ELEMENTS SHORTEST 1 PATH GROUPS ((a)-[:REL]->(b))+") {
    runWith(MatchModes).hasNoErrors
  }

  test("shortestPath((a)-[:REL*]->(b)), shortestPath((c)-[:REL*]->(d))") {
    runWith(MatchModes).hasNoErrors
  }

  test("REPEATABLE ELEMENTS ((a)-[:REL]->(b)){2}, ((c)-[:REL]->(d))+") {
    runWith(MatchModes).hasErrors(
      unboundRepeatableElementsSemanticError(p(48, 1, 49))
    )
  }

  test("REPEATABLE ELEMENTS ((a)-[:REL]->(b))+, ((c)-[:REL]->(d))+") {
    runWith(MatchModes).hasErrors(
      unboundRepeatableElementsSemanticError(p(26, 1, 27)),
      unboundRepeatableElementsSemanticError(p(46, 1, 47))
    )
  }

  test("REPEATABLE ELEMENTS SHORTEST 1 PATH (a)-[:REL*]->(b), SHORTEST 1 PATH (c)-[:REL*]->(d)") {
    runWith(MatchModes).hasNoErrors
  }

  test("DIFFERENT RELATIONSHIPS SHORTEST 1 PATH (a)-[:REL*]->(b), SHORTEST 1 PATH (c)-[:REL*]->(d)") {
    runWith(MatchModes).hasErrors(
      differentRelationshipsSelectivePathPatternSemanticError(p(46, 1, 47))
    )
  }

  test("SHORTEST 1 PATH (a)-[:REL*]->(b), (c)-[:REL]->(d)") {
    runWith(MatchModes).hasErrors(
      differentRelationshipsSelectivePathPatternSemanticError(p(22, 1, 23))
    )
  }

  test("SHORTEST 1 PATH (a)-[:REL*]->(b), (c)-[:REL]->(e)") {
    // running without MatchModes
    run().hasErrors(
      differentRelationshipsSelectivePathPatternSemanticError(p(22, 1, 23), semanticFeatureEnabled = false)
    )
  }

  test("(a)-[:REL]->(b), ALL PATHS (c)-[:REL]->(d)") {
    runWith(MatchModes).hasNoErrors
  }

  test("REPEATABLE ELEMENTS (a)-[:REL]->(b), ALL PATHS (c)-[:REL]->(d)") {
    runWith(MatchModes).hasNoErrors
  }

  test(s"REPEATABLE ELEMENTS shortestPath((a)-->(b))") {
    runWith(MatchModes).hasErrorMessages(
      "Mixing shortestPath/allShortestPaths with path selectors (e.g. 'ANY SHORTEST') or explicit match modes ('e.g. DIFFERENT RELATIONSHIPS') is not allowed."
    )
  }

  test(s"DIFFERENT RELATIONSHIPS shortestPath((a)-->(b))") {
    runWith(MatchModes).hasErrorMessages(
      "Mixing shortestPath/allShortestPaths with path selectors (e.g. 'ANY SHORTEST') or explicit match modes ('e.g. DIFFERENT RELATIONSHIPS') is not allowed."
    )
  }

  test(s"REPEATABLE ELEMENTS allShortestPaths((a)-->(b))") {
    runWith(MatchModes).hasErrorMessages(
      "Mixing shortestPath/allShortestPaths with path selectors (e.g. 'ANY SHORTEST') or explicit match modes ('e.g. DIFFERENT RELATIONSHIPS') is not allowed."
    )
  }

  test(s"REPEATABLE ELEMENTS (a)-->(b) WHERE shortestPath((a)-->(b)) IS NOT NULL") {
    runWith(MatchModes).hasErrorMessages(
      "Mixing shortestPath/allShortestPaths with path selectors (e.g. 'ANY SHORTEST') or explicit match modes ('e.g. DIFFERENT RELATIONSHIPS') is not allowed."
    )
  }

  test(s"REPEATABLE ELEMENTS (a)-->(b) WHERE EXISTS { MATCH shortestPath((a)-->(b)) }") {
    runWith(MatchModes).hasErrorMessages(
      "Mixing shortestPath/allShortestPaths with path selectors (e.g. 'ANY SHORTEST') or explicit match modes ('e.g. DIFFERENT RELATIONSHIPS') is not allowed."
    )
  }

  test(s"CALL { MATCH REPEATABLE ELEMENTS (a)-->(b) MATCH shortestPath((c)-->(d)) RETURN * } RETURN *") {
    run(testName, pipelineWithSemanticFeatures(MatchModes)).hasErrorMessages(
      "Mixing shortestPath/allShortestPaths with path selectors (e.g. 'ANY SHORTEST') or explicit match modes ('e.g. DIFFERENT RELATIONSHIPS') is not allowed."
    )
  }

  test(s"REPEATABLE ELEMENTS (a)-->(b) MATCH shortestPath((c)-->(d))") {
    runWith(MatchModes).hasNoErrors
  }

  test(s"DIFFERENT RELATIONSHIPS (a)-->(b) MATCH shortestPath((c)-->(d))") {
    runWith(MatchModes).hasNoErrors
  }
}
