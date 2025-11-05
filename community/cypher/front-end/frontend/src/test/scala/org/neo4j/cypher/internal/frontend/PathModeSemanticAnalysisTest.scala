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

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.Ast.p
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.PathModes
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.gqlstatus.GqlHelper

import scala.jdk.CollectionConverters.SeqHasAsJava

class PathModeSemanticAnalysisTest extends NameBasedSemanticAnalysisTestSuite {

  private def runWithPathModes = runWith(disabledCypherVersions = Set(CypherVersion.Cypher5), features = PathModes)

  private def runWithoutPathModes = runWith(disabledCypherVersions = Set(CypherVersion.Cypher5))

  private val errFeatureFlagDisabled =
    "Explicit use of path modes WALK, TRAIL and ACYCLIC is not available in " +
      "this implementation of Cypher due to lack of support for path modes."

  private def errMatchModePathModeUnsupported(pathMode: String, pos: InputPosition): SemanticError =
    SemanticError(
      GqlHelper.getGql42001_42N60(pathMode, pos.offset, pos.line, pos.column),
      s"REPEATABLE ELEMENTS with $pathMode path mode is not supported.",
      pos
    )

  private def errPathModeMixUnsupported(pathModes: Set[String], pos: InputPosition): SemanticError =
    SemanticError(
      GqlHelper.getGql42001_42N61(pathModes.toList.asJava, pos.offset, pos.line, pos.column),
      s"Mixing path modes ${pathModes.mkString(", ")} in the same graph pattern is not supported.",
      pos
    )

  private def errLegacyShortestWithPathMode(fun: String, pos: InputPosition): SemanticError =
    SemanticError(
      GqlHelper.getGql42001_42I39(fun, pos.offset, pos.line, pos.column),
      "Mixing shortestPath/allShortestPaths with path selectors (e.g. 'ANY SHORTEST'), " +
        "explicit match modes ('e.g. DIFFERENT RELATIONSHIPS') or explicit path modes ('e.g. ACYCLIC') is not allowed.",
      pos
    )

  test("MATCH WALK (a)-->(b) RETURN *") {
    runWithPathModes.hasNoErrors
    runWithoutPathModes.hasErrorMessages(errFeatureFlagDisabled)
  }

  test("MATCH TRAIL (a)-->(b) RETURN *") {
    runWithPathModes.hasNoErrors
    runWithoutPathModes.hasErrorMessages(errFeatureFlagDisabled)
  }

  test("MATCH ACYCLIC (a)-->(b) RETURN *") {
    runWithPathModes.hasNoErrors
    runWithoutPathModes.hasErrorMessages(errFeatureFlagDisabled)
  }

  test("MATCH REPEATABLE ELEMENTS (a)-->(b) RETURN *") {
    runWithPathModes.hasNoErrors
    runWithoutPathModes.hasNoErrors
  }

  test("MATCH REPEATABLE ELEMENTS WALK (a)-->(b) RETURN *") {
    runWithPathModes.hasNoErrors
    runWithoutPathModes.hasErrorMessages(errFeatureFlagDisabled)
  }

  test("MATCH REPEATABLE ELEMENTS TRAIL (a)-->(b) RETURN *") {
    runWithPathModes.hasErrors(errMatchModePathModeUnsupported("TRAIL", p(26, 1, 27)))
  }

  test("MATCH REPEATABLE ELEMENTS ACYCLIC (a)-->(b) RETURN *") {
    runWithPathModes.hasErrors(errMatchModePathModeUnsupported("ACYCLIC", p(26, 1, 27)))
  }

  test("MATCH p = ACYCLIC (s {i: 1})-->+(s), q = ACYCLIC (s)-->+({i: 3}) RETURN p, q") {
    runWithPathModes.hasNoErrors
  }

  test("MATCH p = ACYCLIC (s {i: 1})-->+(s), q = TRAIL (s)-->+({i: 3}) RETURN p, q") {
    runWithPathModes
      .hasErrors(errPathModeMixUnsupported(Set("ACYCLIC", "TRAIL"), p(6, 1, 7)))
  }

  test("MATCH p = ACYCLIC (s {i: 1})-->+(s), q = (s)-->+({i: 3}) RETURN p, q") {
    runWithPathModes.hasErrors(errPathModeMixUnsupported(Set("ACYCLIC", "WALK"), p(6, 1, 7)))
  }

  test("MATCH REPEATABLE ELEMENTS p = WALK (s {i: 1})-->{,100}(s), q = (s)-->{,1}({i: 3}) RETURN p, q") {
    runWithPathModes.hasNoErrors
  }

  test("MATCH p = WALK allShortestPaths((s {i: 1})-[*1..100]->(t)) RETURN p") {
    runWithPathModes.hasErrors(errLegacyShortestWithPathMode("allShortestPaths", p(15, 1, 16)))
  }

  test("MATCH p = ACYCLIC shortestPath((s {i: 1})-[*1..100]->(t)) RETURN p") {
    runWithPathModes.hasErrors(errLegacyShortestWithPathMode("shortestPath", p(18, 1, 19)))
  }

  test("MATCH p = allShortestPaths((s {i: 1})-[*1..100]->(t)) RETURN p") {
    runWithPathModes.hasNoErrors
  }

  test("MATCH p = ACYCLIC (s {i: 1})-->+(s) MATCH q = allShortestPaths((s {i: 1})-[*1..100]->(t)) RETURN p, q") {
    runWithPathModes.hasNoErrors
  }
}
