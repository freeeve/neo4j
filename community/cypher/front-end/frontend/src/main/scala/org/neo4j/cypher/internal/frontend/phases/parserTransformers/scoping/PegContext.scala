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
package org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.ScopeState.RecordedScopes
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator

case class PegContext(
  anonVarGen: AnonymousVariableNameGenerator,
  language: CypherVersion,
  semanticFeatures: Set[SemanticFeature],
  recordedScopes: RecordedScopes
) {

  /**
   * Looks up the working scope for the given astNode in recordedScopes and
   * returns is if the recorded working scope's incoming is equal to the given incoming.
   * If no working scope is record for the given astNode or the incoming does match,
   * the peg function is used to freshly compute the working scope for the given astNode.
   *
   * Note that the lookup includes the InputPosition, i.e. it assumes that changes to AST
   * do not change positions for parts of the AST. This is usually the case for changes
   * made by rewriters. It would not necessarily be true after serialization and reparsing.
   *
   * Conversely, this assumes that all AST nodes differ at least in their position. This is
   * necessarily true after parsing. It is not necessarily true for changes made be rewriters.
   * It is deemed unlikely to not be true though.
   *
   * For same considerations, this approach is likely unsuitable for caching working scope's
   * across queries.
   */
  def getRecordScopeOrElse[T <: ASTNode](
    astNode: T,
    incoming: RegularContext,
    peg: (T, RegularContext) => WorkingScope
  ): WorkingScope = {
    recordedScopes.get(astNode).flatMap {
      case ws: WorkingScope if ws.incoming == incoming => Some(ws)
      case _                                           => None
    }.getOrElse(peg(astNode, incoming))
  }
}
