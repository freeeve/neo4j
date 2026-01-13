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

import org.neo4j.cypher.internal.ast.ASTAnnotationMap
import org.neo4j.cypher.internal.ast.ASTAnnotationMap.ASTAnnotationMap
import org.neo4j.cypher.internal.ast.AliasedReturnItem
import org.neo4j.cypher.internal.ast.ReturnItem
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.ScopeState.RecordedScopes
import org.neo4j.cypher.internal.util.ASTNode

case class ScopeState(
  workingScope: WorkingScope,
  recordedScopes: RecordedScopes,
  explainScope: Option[WorkingScope] = None
) {
  def getIncoming(ast: ASTNode): Seq[LogicalVariable] = recordedScopes(ast).incoming.allSymbols.map(_.copyId).toSeq

  def getOutgoing(ast: ASTNode): Seq[LogicalVariable] = recordedScopes(ast).outgoing.variables.map(_.copyId).toSeq

  def getReferenced(ast: ASTNode): Set[LogicalVariable] = recordedScopes(ast).referenced.map(_.copyId)

  def getResultCols(ast: ASTNode): Seq[LogicalVariable] = recordedScopes(ast).result match {
    case TableResult(cols) => cols
    case _                 => Seq.empty
  }

  def getIncomingConstants(ast: ASTNode): Seq[LogicalVariable] =
    recordedScopes(ast).incoming match {
      case rc: RegularContext => rc.constants.toSeq
      case wc                 => wc.allSymbols.toSeq
    }

  def getIncomingVariables(ast: ASTNode): Seq[LogicalVariable] =
    recordedScopes(ast).incoming match {
      case rc: RegularContext => rc.variables.toSeq
      case wc                 => wc.allSymbols.toSeq
    }

  def getResult(ast: ASTNode): Result = recordedScopes(ast).result

  def getIncomingReturnItemSeq(ast: ASTNode): Seq[ReturnItem] =
    getIncoming(ast).map(v =>
      AliasedReturnItem(v.withPosition(ast.position), v.withPosition(ast.position))(ast.position)
    )

  def getIncomingConstantReturnItemSeq(ast: ASTNode): Seq[ReturnItem] =
    getIncomingConstants(ast).map(v =>
      AliasedReturnItem(v.withPosition(ast.position), v.withPosition(ast.position))(ast.position)
    )

  def getOutgoingVariableReturnItemSeq(ast: ASTNode): Seq[ReturnItem] =
    getOutgoing(ast).map(v =>
      AliasedReturnItem(v.withPosition(ast.position), v.withPosition(ast.position))(ast.position)
    )

}

object ScopeState {

  type RecordedScopes = ASTAnnotationMap[ASTNode, WorkingScope]

  def emptyRecordedScopes: RecordedScopes = ASTAnnotationMap.empty
}
