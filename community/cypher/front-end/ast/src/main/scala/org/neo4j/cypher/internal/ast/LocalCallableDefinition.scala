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
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.ast.semantics.iterableOnceSemanticChecking
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.CallableName
import org.neo4j.cypher.internal.util.FunctionName
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.ProcedureName
import org.neo4j.cypher.internal.util.symbols
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.symbols.CypherType
import org.neo4j.cypher.internal.util.symbols.TypeSpec

sealed trait LocalCallableDefinition extends ASTNode with SemanticCheckable with SemanticAnalysisTooling {
  def name: CallableName
  def inputSignature: Seq[LocalFieldSignature]
  def position: InputPosition

  def semanticCheckInputSignature(inputSignature: Seq[LocalFieldSignature]): SemanticCheck =
    inputSignature.foldSemanticCheck {
      case f @ LocalFieldSignature(name, typeOpt, default) =>
        val typ = typeOpt.getOrElse(CTAny).covariant
        val parameter = Variable(name)(f.position, isIsolated = false)
        checkNotNull(f, f => SemanticError.notSupportedLocalCallableParameterType(f, this.name)) ifOkChain
          default.map(d => checkExpressionIsStatic(d, "default argument", typ)).getOrElse(success) chain
          SemanticCheck.fromFunction(s =>
            s.declareVariable(parameter, typ, overriding = false) match {
              case Left(err)        => SemanticCheckResult.error(s, err)
              case Right(nextState) => SemanticCheckResult.success(nextState)
            }
          )
    }

  override def semanticCheck: SemanticCheck = {
    for {
      base <- SemanticCheck.getState
      // base.state is already in detached empty “definitions scope”
      // (and ideally already a child scope, not Top)

      // 1) Compute queryOuter = “clean + params” (this is what body is allowed to import)
      _ <- SemanticCheck.setState(base.state)
      queryOuter <- semanticCheckInputSignature(inputSignature)

      // 2) Check body starting from a clean scope (no vars), using queryOuter for imports
      innerChecked <- {
        val bodyStart = base.state // or base.state.newChildScope if you want a fresh node per definition
        SemanticCheck.setState(bodyStart)
        semanticCheckBody(queryOuter.state, bodyStart)
      }

      // 3) Record this definition’s scope *where you want it recorded*
      _ <- SemanticState.recordCurrentScope(this)

      // 4) Reset cursor so the next definition starts from `base` again
      _ <- SemanticCheck.setState(base.state)
    } yield {
      val baseWithGraphs = updateRecordedGraphs(base.state, innerChecked.state)
      val merged = baseWithGraphs.copy(
        recordedScopes = baseWithGraphs.recordedScopes ++ innerChecked.state.recordedScopes,
        typeTable = baseWithGraphs.typeTable ++ innerChecked.state.typeTable,
        notifications = baseWithGraphs.notifications ++ innerChecked.state.notifications
      )
      val allErrors = (queryOuter.errors ++ innerChecked.errors).distinct
      SemanticCheckResult(merged, allErrors)
    }
  }

  def semanticCheckBody(queryOuter: SemanticState, definitionOuter: SemanticState): SemanticCheck

  def checkNotNull(fieldSignature: LocalFieldSignature, error: LocalFieldSignature => SemanticError): SemanticCheck =
    (_: SemanticState) =>
      if (!fieldSignature.getType.isNotNullContaining) Seq.empty
      else Seq(error(fieldSignature))

  def noUseClauseInQueryBody(query: Query): SemanticCheck = {
    query.getGraphSelections.headOption.map(uc =>
      SemanticCheck.error(errorUseClauseInLocalProcedureDefinitionNotSupported(uc.position))
    ).getOrElse(success)
  }

  def errorUseClauseInLocalProcedureDefinitionNotSupported(position: InputPosition): SemanticError
}

case class LocalProcedureDefinition(
  name: ProcedureName,
  inputSignature: Seq[LocalFieldSignature],
  outputSignature: Option[Seq[LocalFieldSignature]],
  body: Query
)(val position: InputPosition) extends LocalCallableDefinition with SemanticAnalysisTooling {

  /*
   * Infers output signature from the procedure body.
   * If the body is not returning, then the inferred output signature is None
   * Otherwise, the inferred output signature are the return columns with their inferred types.
   */
  def inferredOutputSignature(semanticState: SemanticState): Option[Seq[LocalFieldSignature]] = {
    Option.when(body.isReturning)(
      body.returnColumns.map { col =>
        val typeSpec = semanticState.expectType(col, symbols.CTAny, coercion = false)._2
        LocalFieldSignature(col.name, Some(TypeSpec.cypherTypeForTypeSpec(typeSpec)), None)(position = col.position)
      }
    )
  }

  override def semanticCheckBody(queryOuterState: SemanticState, definitionOuterState: SemanticState): SemanticCheck = {
    for {
      innerChecked <-
        noUseClauseInQueryBody(body) ifOkChain
          body.semanticCheckInLocalCallableBodyContext(
            queryOuterState, // outer = clean+params (used for imports + shadowing *warnings*)
            definitionOuterState,
            optional = false
          ) ifOkChain checkReturnColumnsAgainstOutputSignature
    } yield {
      innerChecked
    }
  }

  private def checkReturnColumnsAgainstOutputSignature(): SemanticCheck = {
    def matchUp(
      left: Seq[LocalFieldSignature],
      right: Seq[LogicalVariable]
    ): Seq[(Option[LocalFieldSignature], Option[LogicalVariable])] = {
      val l = left.iterator.map(a => a.name -> a).toMap
      val r = right.iterator.map(b => b.name -> b).toMap
      (l.keySet union r.keySet).toSeq.map { k =>
        (l.get(k), r.get(k))
      }
    }

    outputSignature.map { outputFieldSignatures =>
      val pairs = matchUp(outputFieldSignatures, body.returnColumns)
      pairs.foldSemanticCheck {
        case (Some(out), Some(col)) =>
          checkNotNull(out, out => SemanticError.notSupportedLocalProcedureOutputType(out, name)) ifOkChain
            expectType(out.getType.covariant, col)
        case (Some(out), None) =>
          SemanticError.missingReturnColumnInLocalProcedure(out.name, name, out.position)
        case (None, Some(col)) =>
          SemanticError.returnColumnDoesNotMatchOutputSignatureOfLocalProcedure(col.name, name, col.position)
        case _ => success // to make match exhaustive for a happy compiler
      }
    }.getOrElse(success)
  }

  override def errorUseClauseInLocalProcedureDefinitionNotSupported(position: InputPosition): SemanticError =
    SemanticError.useClauseInLocalProcedureDefinitionNotSupported(position)
}

case class LocalFunctionDefinition(
  name: FunctionName,
  inputSignature: Seq[LocalFieldSignature],
  outputSignature: Option[CypherType],
  body: LocalFunctionBody
)(val position: InputPosition) extends LocalCallableDefinition {

  override def semanticCheckBody(queryOuterState: SemanticState, definitionOuterState: SemanticState): SemanticCheck = {
    for {
      innerChecked <-
        body match {
          case ExpressionBody(expression) =>
            // runs in the *current threaded state*, which at this point is your clean+params state
            SemanticExpressionCheck.simple(expression)

          case QueryBody(query) =>
            noUseClauseInQueryBody(query) ifOkChain
              query.semanticCheckInSubqueryContext(
                queryOuterState, // outer = clean+params (used for imports + shadowing *errors*)
                definitionOuterState,
                optional = false
              )
        }
    } yield {
      innerChecked
    }
  }

  override def errorUseClauseInLocalProcedureDefinitionNotSupported(position: InputPosition): SemanticError =
    SemanticError.useClauseInLocalFunctionDefinitionNotSupported(position)
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
