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
import org.neo4j.cypher.internal.ast.AdministrationCommand
import org.neo4j.cypher.internal.ast.ConditionalQueryBranch
import org.neo4j.cypher.internal.ast.ConditionalQueryWhen
import org.neo4j.cypher.internal.ast.NextStatement
import org.neo4j.cypher.internal.ast.SingleQuery
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.TopLevelBraces
import org.neo4j.cypher.internal.ast.Union
import org.neo4j.cypher.internal.ast.UnresolvedCall
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator

case class pegStatement(anonVarGen: AnonymousVariableNameGenerator) {

  def apply(statement: Statement, incoming: RegularContext, version: CypherVersion): WorkingScope = {
    implicit val astNode: ASTNode = statement
    statement match {

      /**
       * Statement
       */
      case NextStatement(queries) =>
        val children = queries.scanLeft(WorkingScope.apriori(incoming)) {
          case (previous, query) => apply(query, incoming.replaceWith(previous.outgoing.variables), version)
        }.tail
        // Alternatively, referenced can be computed by referencedInChildren minus "declaredInChildren"
        val referenced =
          Some(WorkingScope.referencedInChildren(children) intersect incoming.constantsAndVariables)
        incoming.resultScope(children.last.outgoing, children.last.result, children, referenced)
      case u: Union =>
        val children = Seq(u.lhs, u.rhs).map(q => apply(q, incoming, version))
        incoming.resultScope(children.head.outgoing, children.head.result, children)
      case ConditionalQueryWhen(branches, defaultOpt) =>
        val allBranched = branches.appendedAll(defaultOpt)
        val branchIncoming = incoming.constantChildContext()
        val children = allBranched.map {
          case branch @ ConditionalQueryBranch(predicateOpt, query) =>
            val predicateScopeOpt =
              predicateOpt.map(predicate => pegExpression(anonVarGen)(predicate, branchIncoming, version))
            val queryScope = apply(query, branchIncoming, version)
            val branchChildren = Seq(predicateScopeOpt, Some(queryScope)).flatten
            val referenced =
              Some(WorkingScope.referencedInChildren(branchChildren) intersect branchIncoming.constantsAndVariables)
            implicit val astNode: ASTNode = branch
            branchIncoming.resultScope(queryScope.outgoing, queryScope.result, branchChildren, referenced)
        }
        incoming.resultScope(children.head.outgoing, children.head.result, children)
      case SingleQuery(clauses) =>
        if (clauses.size == 1 && clauses.head.isInstanceOf[UnresolvedCall]) {
          val child = pegClause(anonVarGen)(clauses.head, incoming, version)
          val referenced =
            Some(WorkingScope.referencedInChildren(Seq(child)) intersect incoming.constantsAndVariables)
          incoming.resultScope(child.outgoing, child.result, Seq(child), referenced)
        } else {
          val children = clauses.scanLeft(WorkingScope.apriori(incoming)) {
            case (previous, clause) => pegClause(anonVarGen)(clause, previous.outgoing, version) match {
                // adjusting for in-query calls to have no result
                case ws @ StatementScope(_: UnresolvedCall, _, _, _, _, TableResult(_), _) =>
                  ws.copy(result = NoResult)
                case ws => ws
              }
          }.tail
          // Alternatively, referenced can be computed by referencedInChildren minus "declaredInChildren"
          val referenced =
            Some(WorkingScope.referencedInChildren(children) intersect incoming.constantsAndVariables)
          // The following also wraps a single child in a parent scope.
          // Alternatively, we could simply forward a single child.
          incoming.resultScope(children.last.outgoing, children.last.result, children, referenced)
        }
      case TopLevelBraces(query, _) => apply(query, incoming, version)

      // TODO other query forms and admin commands
      case command: AdministrationCommand => pegCommand(anonVarGen)(command, incoming, version)

      /**
       * To make match exhaustive
       */
      case _ => UnexpectedAstNodeScopingError(astNode, incoming)
    }
  }
}
