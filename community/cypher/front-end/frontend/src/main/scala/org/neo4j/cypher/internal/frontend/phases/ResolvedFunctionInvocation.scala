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
package org.neo4j.cypher.internal.frontend.phases

import org.neo4j.cypher.internal.ast.semantics.SemanticCheck
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck.success
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckResult.error
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckableExpression
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticExpressionCheck
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.ast.semantics._
import org.neo4j.cypher.internal.expressions.CoerceTo
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.Expression.SemanticContext
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.functions.UserDefinedFunctionInvocation
import org.neo4j.cypher.internal.util.FunctionName
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.ZippableUtil.Zippable
import org.neo4j.cypher.internal.util.symbols

object ResolvedFunctionInvocation {

  def apply(signatureLookup: FunctionName => Option[UserFunctionSignature])(unresolved: FunctionInvocation)
    : ResolvedFunctionInvocation = {
    val position = unresolved.position
    val name = unresolved.functionName
    val signature = signatureLookup(name)
    val args = signature.map(obfuscateArgs(_, unresolved.args)).getOrElse(unresolved.args)

    ResolvedFunctionInvocation(name, signature, args)(position)
  }

  def obfuscateArgs(signature: UserFunctionSignature, args: IndexedSeq[Expression]): IndexedSeq[Expression] = {
    args.zipLeft(signature.inputSignature, null).map {
      case (exp: Expression, fieldSignature: FieldSignature) if fieldSignature.sensitive =>
        exp.endoRewrite(SensitiveParameterRewriter)
      case (exp, _) => exp
    }
  }
}

/**
 * A ResolvedFunctionInvocation is a user-defined function where the signature
 * has been resolved, i.e. verified that it exists in the database
 *
 * @param functionName The qualified name of the function.
 * @param fcnSignature Either `Some(signature)` if the signature was resolved, or
 *                     `None` if the function didn't exist
 * @param callArguments The argument list to the function
 * @param position The position in the original query string.
 */
case class ResolvedFunctionInvocation(
  functionName: FunctionName,
  fcnSignature: Option[UserFunctionSignature],
  callArguments: IndexedSeq[Expression]
)(val position: InputPosition)
    extends Expression with UserDefinedFunctionInvocation with SemanticCheckableExpression {

  def coerceArguments: ResolvedFunctionInvocation = fcnSignature match {
    case Some(signature) =>
      val optInputFields = signature.inputSignature.map(Some(_))
      val coercedArguments =
        callArguments.zipLeft(optInputFields, None)
          .map {
            case (arg, optField) =>
              // If type is CTAny we don't need any coercion
              optField.map { field => if (field.typ == symbols.CTAny) arg else CoerceTo(arg, field.typ) }.getOrElse(arg)
          }
      copy(callArguments = coercedArguments)(position)

    case None => this
  }

  override def semanticCheck(ctx: SemanticContext): SemanticCheck = fcnSignature match {
    case None =>
      functionName match {
        case FunctionName(_, qn) if qn.equalsIgnoreCase("not") =>
          SemanticError.unknownFunctionNamedNot(position)
        case _ => SemanticError.unknownFunction(functionName.fullName, position)
      }
    case Some(signature) =>
      val expectedNumArgs = signature.inputSignature.length
      val usedDefaultArgs = signature.inputSignature.drop(callArguments.length).flatMap(_.default)
      val actualNumArgs = callArguments.length + usedDefaultArgs.length

      if (expectedNumArgs == actualNumArgs) {
        // this zip is fine since it will only verify provided args in callArguments
        // default values are checked at load time
        signature.inputSignature.zip(callArguments).map {
          case (field, arg) =>
            SemanticExpressionCheck.check(SemanticContext.Results, arg) chain
              SemanticExpressionCheck.expectType(field.typ.covariant, arg)
        }.foldLeft(success)(_ chain _) chain
          SemanticExpressionCheck.specifyType(signature.outputType.covariant, this)
      } else {
        val msg =
          (if (signature.inputSignature.isEmpty) "arguments"
           else if (signature.inputSignature.size == 1)
             s"argument of type ${signature.inputSignature.head.typ.normalizedCypherTypeString()}"
           else
             s"arguments of type ${signature.inputSignature.map(_.typ.normalizedCypherTypeString()).mkString(", ")}") +
            signature.description.map(d => s"${System.lineSeparator()}Description: $d").getOrElse("")
        error(
          _: SemanticState,
          SemanticError.functionCallWrongNumberOfArguments(
            expectedNumArgs,
            actualNumArgs,
            signature.name.fullName,
            String.valueOf(signature),
            msg,
            position
          )
        )
      }
  }

  override def isAggregate: Boolean = fcnSignature.exists(_.isAggregate)

  override def asUnresolvedFunction: FunctionInvocation = FunctionInvocation(
    functionName = functionName,
    distinct = false,
    args = arguments.toIndexedSeq
  )(position)

  override def isConstantForQuery: Boolean = false
}
