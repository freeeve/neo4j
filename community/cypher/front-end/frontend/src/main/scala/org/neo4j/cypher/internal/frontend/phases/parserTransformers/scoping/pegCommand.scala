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

import org.neo4j.cypher.internal.ast.AdministrationCommand
import org.neo4j.cypher.internal.ast.ReadAdministrationCommand
import org.neo4j.cypher.internal.util.ASTNode

object pegCommand {

  def apply(command: AdministrationCommand, incoming: RegularContext): WorkingScope = {
    implicit val astNode: ASTNode = command
    command match {

      /**
       * ReadAdministrationCommand
       */
      case read: ReadAdministrationCommand =>
        val defaultCols = read.defaultColumnVariables
        val declaringAST = read.withYieldOrWhere(None)
        val declaringScope = StatementScope(
          declaringAST,
          incoming,
          Set.empty,
          Declarations(Seq.empty, defaultCols),
          incoming.amendedWith(defaultCols.toSet),
          TableResult(defaultCols)
        )

        val yieldOrWhereScopes = read.yieldOrWhere match {
          case Some(Left((yieldClause, None))) => Seq(pegClause(yieldClause, declaringScope.outgoing))
          case Some(Left((yieldClause, optReturn))) =>
            val yieldScope = pegClause(yieldClause, declaringScope.outgoing)
            val returnScope = optReturn.map(pegClause(_, yieldScope.outgoing))
            Seq(yieldScope) ++ returnScope
          case Some(Right(whereClause)) =>
            Seq(pegExpression(whereClause.expression, declaringScope.outgoing.constantChildContext()))
          case None => Seq.empty
        }

        val hasYield = read.yields.isDefined
        val children = declaringScope +: yieldOrWhereScopes
        val outgoing = if (hasYield) children.last.outgoing else children.head.outgoing
        val result = if (hasYield) children.last.result else children.head.result

        incoming.resultScope(outgoing, result, children, None)

      /**
       * To make match exhaustive
       */
      case _ => UnexpectedAstNodeScopingError(astNode, incoming)
    }
  }
}
