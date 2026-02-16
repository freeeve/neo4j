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
package org.neo4j.cypher.internal.ast

import org.neo4j.cypher.internal.ast.ASTSlicingPhrase.checkExpressionIsStatic
import org.neo4j.cypher.internal.ast.semantics.SemanticAnalysisTooling
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck.success
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckResult
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckable
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticExpressionCheck
import org.neo4j.cypher.internal.ast.semantics.iterableOnceSemanticChecking
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.CallableName
import org.neo4j.cypher.internal.util.FunctionName
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.ProcedureName
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.symbols.CypherType

sealed trait LocalCallableDefinition extends ASTNode with SemanticCheckable with SemanticAnalysisTooling {
  def name: CallableName
  def inputSignature: Seq[LocalFieldSignature]
  def position: InputPosition

  def semanticCheckInputSignature(inputSignature: Seq[LocalFieldSignature]): SemanticCheck =
    SemanticCheck.mapState(_.newChildScope) chain
      inputSignature.foldSemanticCheck {
        case f @ LocalFieldSignature(name, typeOpt, default) =>
          val typ = typeOpt.getOrElse(CTAny)
          val parameter = Variable(name)(f.position, isIsolated = false)
          default.map(d => checkExpressionIsStatic(d, "default argument", typ.covariant)).getOrElse(success) chain
            SemanticCheck.fromFunction(s =>
              s.declareVariable(parameter, typ, overriding = false) match {
                case Left(err)        => SemanticCheckResult.error(s, err)
                case Right(nextState) => SemanticCheckResult.success(nextState)
              }
            )
      }
}

case class LocalProcedureDefinition(
  name: ProcedureName,
  inputSignature: Seq[LocalFieldSignature],
  outputSignature: Option[Seq[LocalFieldSignature]],
  body: Query
)(val position: InputPosition) extends LocalCallableDefinition {

  override def semanticCheck: SemanticCheck = {
    for {
      // Get current state
      current <- SemanticCheck.getState
      // Checks for errors in input signature and import parameters into new baseScope
      innerWithParameters <- semanticCheckInputSignature(inputSignature)
      // Check body
      innerChecked <-
        noUseClauseInQueryBody(body) ifOkChain
          body.semanticCheckInSubqueryContext(innerWithParameters.state, current.state, optional = false)
      //      _ <- recordCurrentScope(this)
      //      // merged
      //      afterDefinition <- SemanticCheck.fromFunction { innerState => {
      //        // Keep working from the latest state
      //        val after = innerState
      //          // but jump back to scope tree of outerState
      //          .copy(current.state.currentScope)
      //          // Copy in the scope tree from inner query (needed for Namespacer)
      //          .insertSiblingScope(current.state.currentScope.scope)
      //          // Import variables from scope before definition
      //          .newSiblingScope
      //        SemanticCheckResult.success(after)
      //      }}
    } yield {
      val allErrors = (innerWithParameters.errors ++ innerChecked.errors).distinct

      // Keep errors from inner check and from parameter declarations
      SemanticCheckResult(current.state, allErrors)
    }
  }

  private def noUseClauseInQueryBody(query: Query): SemanticCheck = {
    query.getGraphSelections.headOption.map(uc =>
      SemanticCheck.error(SemanticError.useClauseInLocalProcedureDefinitionNotSupported(uc.position))
    ).getOrElse(success)
  }
}

case class LocalFunctionDefinition(
  name: FunctionName,
  inputSignature: Seq[LocalFieldSignature],
  outputSignature: Option[CypherType],
  body: LocalFunctionBody
)(val position: InputPosition) extends LocalCallableDefinition {

  override def semanticCheck: SemanticCheck = {
    for {
      // Get current state
      current <- SemanticCheck.getState
      // Checks for errors in input signature and import parameters into new baseScope
      innerWithParameters <- semanticCheckInputSignature(inputSignature)
      // Check body
      innerChecked <- body match {
        case ExpressionBody(expression) => SemanticExpressionCheck.simple(expression)
        case QueryBody(query) =>
          noUseClauseInQueryBody(query) ifOkChain
            query.semanticCheckInSubqueryContext(innerWithParameters.state, current.state, optional = false)
      }
      //      _ <- recordCurrentScope(this)
      //      // merged
      //      afterDefinition <- SemanticCheck.fromFunction { innerState => {
      //        // Keep working from the latest state
      //        val after = innerState
      //          // but jump back to scope tree of outerState
      //          .copy(current.state.currentScope)
      //          // Copy in the scope tree from inner query (needed for Namespacer)
      //          .insertSiblingScope(current.state.currentScope.scope)
      //          // Import variables from scope before definition
      //          .newSiblingScope
      //        SemanticCheckResult.success(after)
      //      }}
    } yield {
      val allErrors = (innerWithParameters.errors ++ innerChecked.errors).distinct

      // Keep errors from inner check and from parameter declarations
      SemanticCheckResult(current.state, allErrors)
    }
  }

  private def noUseClauseInQueryBody(query: Query): SemanticCheck = {
    query.getGraphSelections.headOption.map(uc =>
      SemanticCheck.error(SemanticError.useClauseInLocalFunctionDefinitionNotSupported(uc.position))
    ).getOrElse(success)
  }
}

trait AbstractFieldSignature {
  def name: String
  def getType: CypherType
  def hasDefault: Boolean
}

case class LocalFieldSignature(
  override val name: String,
  typ: Option[CypherType],
  default: Option[Expression] = None
)(val position: InputPosition) extends ASTNode with AbstractFieldSignature {

  /**
   * Returns value of `typ` if non-empty, otherwise ctAny.
   */
  lazy val getType: CypherType = typ.getOrElse(CTAny)

  def hasDefault: Boolean = default.nonEmpty
}

sealed trait LocalFunctionBody extends ASTNode

case class ExpressionBody(expression: Expression)(val position: InputPosition) extends LocalFunctionBody {}

case class QueryBody(query: Query)(val position: InputPosition) extends LocalFunctionBody {}
