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
package org.neo4j.cypher.internal.ast.semantics

import org.neo4j.cypher.internal.ast.UsingJoinHint
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.CypherType
import org.neo4j.gqlstatus.ErrorGqlStatusObject
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation
import org.neo4j.gqlstatus.GqlHelper
import org.neo4j.gqlstatus.GqlParams
import org.neo4j.gqlstatus.GqlStatusInfoCodes

import scala.jdk.CollectionConverters.SeqHasAsJava

sealed trait SemanticErrorDef {
  def msg: String
  def position: InputPosition
  def gqlStatusObject: ErrorGqlStatusObject
  def withMsg(message: String): SemanticErrorDef
}

final case class SemanticError(
  override val gqlStatusObject: ErrorGqlStatusObject,
  override val msg: String,
  override val position: InputPosition
) extends SemanticErrorDef {
  def this(msg: String, position: InputPosition) = this(null, msg, position)
  override def withMsg(message: String): SemanticError = copy(msg = message)
}

object SemanticError {

  def apply(msg: String, position: InputPosition): SemanticError = new SemanticError(null, msg, position)

  def unapply(errorDef: SemanticErrorDef): Option[(String, InputPosition)] = Some((errorDef.msg, errorDef.position))

  def invalidOption(
    invalidOptionsString: String,
    validOptions: Seq[String],
    errorMessageOverride: Option[String],
    position: InputPosition
  ): SemanticError = {
    val gql = GqlHelper.getGql42001_22N04(
      invalidOptionsString,
      GqlParams.StringParam.input.process("OPTIONS"),
      validOptions.asJava,
      position.offset,
      position.line,
      position.column
    )
    SemanticError(
      gql,
      errorMessageOverride.getOrElse(GqlHelper.getCompleteMessage(gql)),
      position
    )
  }

  def authForbidsClauseError(
    provider: String,
    unsupportedClause: String,
    expected: java.util.List[String],
    position: InputPosition
  ): SemanticError = {
    val gql = GqlHelper.getGql42001_22N04(
      unsupportedClause,
      "auth provider " + GqlParams.StringParam.input.process(provider) + " attribute",
      expected,
      position.offset,
      position.line,
      position.column
    )
    SemanticError(
      gql,
      s"Auth provider `$provider` does not allow `$unsupportedClause` clause.",
      position
    )
  }

  def unsupportedActionAccess(
    actionName: String,
    expectedActions: java.util.List[String],
    position: InputPosition
  ): SemanticError = {
    val gql = GqlHelper.getGql42001_22N04(
      actionName,
      "property value access rules",
      expectedActions,
      position.offset,
      position.line,
      position.column
    )
    SemanticError(gql, s"$actionName is not supported for property value access rules.", position)
  }

  def yieldMissingColumn(
    originalName: String,
    expectedColumns: java.util.List[String],
    position: InputPosition
  ): SemanticError = {
    val gql = GqlHelper.getGql42001_22N04(
      originalName,
      "column name",
      expectedColumns,
      position.offset,
      position.line,
      position.column
    )
    SemanticError(gql, s"Trying to YIELD non-existing column: `$originalName`", position)
  }

  def invalidFunctionForIndex(
    entityIndexDescription: String,
    name: String,
    validFunction: String,
    position: InputPosition
  ): SemanticError = {
    val gql = GqlHelper.getGql42001_22N04(
      name,
      "function name",
      java.util.List.of(validFunction),
      position.offset,
      position.line,
      position.column
    )
    SemanticError(
      gql,
      s"Failed to create $entityIndexDescription: Function '$name' is not allowed, valid function is '$validFunction'.",
      position
    )
  }

  def invalidUseOfGraphFunction(graphFunction: String, pos: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(pos.offset, pos.line, pos.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N75)
        .atPosition(pos.offset, pos.line, pos.column)
        .withParam(GqlParams.StringParam.fun, graphFunction)
        .build())
      .build()
    SemanticError(
      gql,
      s"`$graphFunction` is only allowed at the first position of a USE clause.",
      pos
    )
  }
  val existsErrorMessage = "The EXISTS expression is not valid in driver settings."
  val countErrorMessage = "The COUNT expression is not valid in driver settings."
  val collectErrorMessage = "The COLLECT expression is not valid in driver settings."
  val genericErrorMessage = "This expression is not valid in driver settings."

  def existsInDriverSettings(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql22N81(
      GqlParams.StringParam.cmd.process("EXISTS"),
      "driver settings",
      position.offset,
      position.line,
      position.column
    )
    SemanticError(gql, existsErrorMessage, position)
  }

  def countInDriverSettings(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql22N81(
      GqlParams.StringParam.cmd.process("COUNT"),
      "driver settings",
      position.offset,
      position.line,
      position.column
    )
    SemanticError(gql, countErrorMessage, position)
  }

  def collectInDriverSettings(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql22N81(
      GqlParams.StringParam.cmd.process("COLLECT"),
      "driver settings",
      position.offset,
      position.line,
      position.column
    )
    SemanticError(gql, collectErrorMessage, position)
  }

  def genericDriverSettingsFail(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql22N81(
      GqlParams.StringParam.cmd.process("EXISTS"),
      "driver settings",
      position.offset,
      position.line,
      position.column
    )
    SemanticError(gql, genericErrorMessage, position)
  }

  def cannotUseJoinHint(hint: UsingJoinHint, prettifiedHint: String): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N76)
      .withParam(GqlParams.ListParam.hintList, Seq(prettifiedHint).asJava)
      .atPosition(hint.position.offset, hint.position.line, hint.position.column)
      .build()
    SemanticError(gql, "Cannot use join hint for single node pattern.", hint.position)
  }

  def variableAlreadyDeclaredInOuterScope(name: String, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N07(name, position.offset, position.line, position.column)
    SemanticError(gql, s"Variable `$name` already declared in outer scope", position)
  }

  def variableShadowingOuterScope(name: String, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N07(name, position.offset, position.line, position.column)
    SemanticError(
      gql,
      s"The variable `$name` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      position
    )
  }

  def legacyDisjunction(
    sanitizedLabelExpression: String,
    containsIs: Boolean,
    isNode: Boolean = false,
    position: InputPosition
  ): SemanticError = {
    val isOrColon = if (containsIs) "IS " else ":"
    val msg = if (isNode) {
      s"""Label expressions are not allowed to contain '|:'.
         |If you want to express a disjunction of labels, please use `$isOrColon$sanitizedLabelExpression` instead""".stripMargin
    } else {
      s"""The semantics of using colon in the separation of alternative relationship types in conjunction with
         |the use of variable binding, inlined property predicates, or variable length is no longer supported.
         |Please separate the relationships types using `$isOrColon$sanitizedLabelExpression` instead.""".stripMargin
    }
    val gql = GqlHelper.getGql42001_42I20(
      "|:",
      "|",
      position.offset,
      position.line,
      position.column
    )
    SemanticError(gql, msg, position)
  }

  def invalidDisjunction(isNode: Boolean, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42I20(
      if (isNode) "|:" else ":",
      "|",
      position.offset,
      position.line,
      position.column
    )
    SemanticError(
      gql,
      if (isNode) "Label expressions are not allowed to contain '|:'."
      else "Relationship types in a relationship type expressions may not be combined using ':'",
      position
    )
  }

  def subPathAssignmentNotSupported(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N42(position.offset, position.line, position.column)
    SemanticError(gql, "Sub-path assignment is currently not supported.", position)
  }

  def unsupportedRequestOnSystemDatabase(
    invalidInput: String,
    legacyMessage: String,
    pos: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .withCause(
        ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N17)
          .withParam(GqlParams.StringParam.input, invalidInput)
          .build()
      )
      .build()
    SemanticError(
      gql,
      legacyMessage,
      pos
    )
  }

  def invalidInput(
    wrongInput: String,
    forField: String,
    expectedInput: List[String],
    legacyMessage: String,
    pos: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(pos.offset, pos.line, pos.column)
      .withCause(
        ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N04)
          .atPosition(pos.offset, pos.line, pos.column)
          .withParam(GqlParams.StringParam.input, wrongInput)
          .withParam(GqlParams.StringParam.context, forField)
          .withParam(GqlParams.ListParam.inputList, java.util.List.of(expectedInput))
          .build()
      )
      .build()
    SemanticError(
      gql,
      legacyMessage,
      pos
    )
  }

  def invalidEntityType(
    invalidInput: String,
    variable: String,
    expectedValueList: Seq[String],
    legacyMessage: String,
    pos: InputPosition
  ): SemanticError = {
    val gql = GqlHelper.getGql22G03_22N27(
      invalidInput,
      variable,
      expectedValueList.asJava,
      pos.offset,
      pos.line,
      pos.column
    )

    SemanticError(
      gql,
      legacyMessage,
      pos
    )
  }

  def typeMismatch(
    expectedValueList: List[String],
    wrongType: String,
    legacyMessage: String,
    pos: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(pos.offset, pos.line, pos.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NB1)
        .atPosition(pos.offset, pos.line, pos.column)
        .withParam(GqlParams.ListParam.valueTypeList, expectedValueList.asJava)
        .withParam(GqlParams.StringParam.input, wrongType)
        .build())
      .build()

    SemanticError(
      gql,
      legacyMessage,
      pos
    )
  }

  def invalidCoercion(
    cannotCoerceFrom: String,
    cannotCoerceTo: String,
    legacyMessage: String,
    pos: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(pos.offset, pos.line, pos.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N37)
        .atPosition(pos.offset, pos.line, pos.column)
        .withParam(GqlParams.StringParam.value, cannotCoerceFrom)
        .withParam(GqlParams.StringParam.valueType, cannotCoerceTo)
        .build())
      .build()

    SemanticError(
      gql,
      legacyMessage,
      pos
    )
  }

  def specifiedNumberOutOfRange(
    component: String,
    valueType: String,
    lower: Number,
    upper: Number,
    inputValue: String,
    legacyMessage: String,
    pos: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(pos.offset, pos.line, pos.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N31)
        .atPosition(pos.offset, pos.line, pos.column)
        .withParam(GqlParams.StringParam.component, component)
        .withParam(GqlParams.StringParam.valueType, valueType)
        .withParam(GqlParams.NumberParam.lower, lower)
        .withParam(GqlParams.NumberParam.upper, upper)
        .withParam(GqlParams.StringParam.value, inputValue).build())
      .build()

    SemanticError(
      gql,
      legacyMessage,
      pos
    )
  }

  def propertyTypeUnsupportedInConstraint(
    constraintTypeDescription: String,
    originalPropertyType: CypherType
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N11)
      .withParam(GqlParams.StringParam.constrDescrOrName, constraintTypeDescription + " constraint")
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N90)
        .withParam(GqlParams.StringParam.item, originalPropertyType.description)
        .build())
      .build()
    new SemanticError(
      gql,
      s"Failed to create ${constraintTypeDescription} constraint: " +
        s"Invalid property type `${originalPropertyType.description}`.",
      originalPropertyType.position
    )
  }

  def missingMandatoryAuthClause(
    clause: String,
    authProvider: String,
    legacyMessage: String,
    pos: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N97)
      .withParam(GqlParams.StringParam.clause, clause)
      .withParam(GqlParams.StringParam.auth, authProvider)
      .build()
    SemanticError(
      gql,
      legacyMessage,
      pos
    )
  }

  def duplicateClause(clause: String, legacyMessage: String, pos: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N19)
      .withParam(GqlParams.StringParam.syntax, clause)
      .build()
    SemanticError(
      gql,
      legacyMessage,
      pos
    )
  }

  def missingHintPredicate(
    legacyMessage: String,
    hint: String,
    entity: String,
    variable: String,
    position: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N76)
      .withParam(GqlParams.ListParam.hintList, Seq(hint).asJava)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N77)
        .withParam(GqlParams.StringParam.hint, hint)
        .withParam(GqlParams.StringParam.entityType, entity)
        .withParam(GqlParams.StringParam.variable, variable)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build()
    SemanticError(gql, legacyMessage, position)
  }

  def functionRequiresWhereClause(func: String, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N70)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.fun, func)
        .build())
      .build()
    SemanticError(gql, s"$func(...) requires a WHERE predicate", position)
  }

  def aExpressionCannotContainUpdates(expr: String, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N57(expr, position.offset, position.line, position.column)
    SemanticError(gql, s"A $expr Expression cannot contain any updates", position)
  }

  def anExpressionCannotContainUpdates(expr: String, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N57(expr, position.offset, position.line, position.column)
    SemanticError(gql, s"An $expr Expression cannot contain any updates", position)
  }

  def singleReturnColumnRequired(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N22(position.offset, position.line, position.column)
    SemanticError(gql, "A Collect Expression must end with a single return column.", position)
  }

  def emptyListRangeOperator(position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N20)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build()
    SemanticError(gql, "The start or end (or both) is required for a collection slice", position)
  }

  def unboundVariablesInPatternExpression(name: String, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N29)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.variable, name)
        .build())
      .build()
    SemanticError(
      gql,
      s"PatternExpressions are not allowed to introduce new variables: '$name'.",
      position
    )
  }

  def incompatibleReturnColumns(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N39(position.offset, position.line, position.column)
    SemanticError(gql, "All sub queries in an UNION must have the same return column names", position)
  }

  def invalidUseOfOldCall(clause: String, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N3C(clause, position.offset, position.line, position.column)
    SemanticError(gql, gql.cause().get().gqlStatusObject().getMessage, position)
  }

  def invalidUseOfUnion(position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I40)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build()

    SemanticError(gql, "Invalid combination of UNION and UNION ALL", position)
  }

  def invalidUseOfCIT(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42I25(position.offset, position.line, position.column)
    SemanticError(
      gql,
      "CALL { ... } IN TRANSACTIONS after a write clause is not supported",
      position
    )
  }

  def invalidUseOfReturn(name: String, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I38)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build()

    SemanticError(gql, s"$name can only be used at the end of the query.", position)
  }

  def invalidUseOfReturnStar(position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I37)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build()
    SemanticError(gql, "RETURN * is not allowed when there are no variables in scope", position)
  }

  def invalidUseOfMatch(position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I31)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build()

    SemanticError(
      gql,
      "MATCH cannot follow OPTIONAL MATCH (perhaps use a WITH clause between them)",
      position
    )
  }

  def invalidReferenceToGroupingExpression(variables: Seq[String], position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I18)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.ListParam.variableList, variables.asJava)
        .build())
      .build()

    val errorMsg = implicitGroupingExpressionInAggregationColumnErrorMessage(variables)
    SemanticError(gql, errorMsg, position)
  }

  def invalidForeach(clause: String, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I01)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.clause, clause)
        .build())
      .build()

    SemanticError(gql, s"Invalid use of $clause inside FOREACH", position)
  }

  def unaliasedReturnItem(clause: String, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N21)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.clause, clause)
        .build())
      .build()

    SemanticError(gql, s"Expression in $clause must be aliased (use AS)", position)
  }

  def implicitGroupingExpressionInAggregationColumnErrorMessage(variables: Seq[String]): String =
    "Aggregation column contains implicit grouping expressions. " +
      "For example, in 'RETURN n.a, n.a + n.b + count(*)' the aggregation expression 'n.a + n.b + count(*)' includes the implicit grouping key 'n.b'. " +
      "It may be possible to rewrite the query by extracting these grouping/aggregation expressions into a preceding WITH clause. " +
      s"Illegal expression(s): ${variables.mkString(", ")}"

  def accessingMultipleGraphsError(legacyMessage: String, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NA5)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build();
    SemanticError(
      gql,
      legacyMessage,
      position
    )
  }

  def numberTooLarge(numberType: String, value: String, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql22003(value, position.offset, position.line, position.column)
    SemanticError(gql, s"$numberType is too large", position)
  }

  def integerOperationCannotBeRepresented(operation: String, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql22003(operation, position.offset, position.line, position.column)
    SemanticError(gql, s"result of $operation cannot be represented as an integer", position)
  }

  def notSupported(pos: InputPosition): SemanticError = {
    val msg = "Not supported."
    val gql = GqlHelper.get50N00(SemanticError.getClass.getSimpleName, msg, pos.offset, pos.line, pos.column)
    SemanticError(gql, msg, pos)
  }

  def bothOrReplaceAndIfNotExists(entity: String, userAsString: String, position: InputPosition) = {
    val gql = GqlHelper.getGql42001_42N14(
      "OR REPLACE",
      "IF NOT EXISTS",
      position.offset,
      position.line,
      position.column
    )
    SemanticError(
      gql,
      s"Failed to create the specified $entity '$userAsString': cannot have both `OR REPLACE` and `IF NOT EXISTS`.",
      position
    )
  }

  def badCommandWithOrReplace(cmd: String, cypherCmd: String, position: InputPosition) = {
    val gql = GqlHelper.getGql42001_42N14("OR REPLACE", cypherCmd, position.offset, position.line, position.column)
    SemanticError(gql, s"Failed to $cmd: `OR REPLACE` cannot be used together with this command.", position)
  }

  def denyMergeUnsupported(position: InputPosition) = {
    val gql = GqlHelper.getGql42001_42N14("DENY", "MERGE", position.offset, position.line, position.column)
    SemanticError(gql, "`DENY MERGE` is not supported. Use `DENY SET PROPERTY` and `DENY CREATE` instead.", position)
  }

  def grantDenyRevokeUnsupported(cmd: String, position: InputPosition) = {
    val gql = GqlHelper.getGql42001_42N14(
      "GRANT, DENY and REVOKE",
      cmd,
      position.offset,
      position.line,
      position.column
    )
    SemanticError(gql, s"`GRANT`, `DENY` and `REVOKE` are not supported for `$cmd`", position)
  }

  def unableToRouteUseClauseError(legacyMessage: String, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N04)
      .atPosition(position.offset, position.line, position.column)
      .withParam(GqlParams.StringParam.clause, "`USE` clause")
      .build()
    SemanticError(
      gql,
      legacyMessage,
      position
    )
  }

  def invalidNumberOfProcedureOrFunctionArguments(
    expectedNumberOfArgs: Int,
    obtainedNumberOfArgs: Int,
    procedureFunction: String,
    signature: String,
    legacyMessage: String,
    position: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I13)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.NumberParam.count1, expectedNumberOfArgs)
        .withParam(GqlParams.NumberParam.count2, obtainedNumberOfArgs)
        .withParam(GqlParams.StringParam.procFun, procedureFunction)
        .withParam(GqlParams.StringParam.sig, signature)
        .build())
      .build()

    SemanticError(
      gql,
      legacyMessage,
      position
    )
  }

  def invalidYieldStar(
    commandName: String,
    position: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N94)
      .build()
    SemanticError(
      gql,
      s"When combining `${commandName}` with other show and/or terminate commands, `YIELD *` isn't permitted.",
      position
    )
  }

  def missingYield(
    commandName: String,
    position: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N93)
      .build()
    SemanticError(
      gql,
      s"When combining `${commandName}` with other show and/or terminate commands, `YIELD` is mandatory.",
      position
    )
  }

  def missingReturn(position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N92)
      .build()
    SemanticError(
      gql,
      "When combining show and/or terminate commands, `RETURN` isn't optional.",
      position
    )
  }

  def queryMustConcludeWithClause(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N71(position.offset, position.line, position.column)
    SemanticError(gql, s"Query must conclude with $validLastClauses.", position)
  }

  def queryCannotConcludeWithCall(callName: String, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N71(position.offset, position.line, position.column)
    SemanticError(gql, s"Query cannot conclude with $callName together with YIELD", position)
  }

  def queryCannotConcludeWithClause(clause: String, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N71(position.offset, position.line, position.column)
    SemanticError(
      gql,
      s"Query cannot conclude with $clause (must be $validLastClauses).",
      position
    )
  }

  def invalidPropertyBasedAccessControlRuleInvolvingNontrivialPredicates(
    unsupportedExpression: String,
    legacyMessage: String,
    position: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NA0)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NA7)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.expr, unsupportedExpression)
        .build())
      .build()
    SemanticError(
      gql,
      legacyMessage,
      position
    )
  }

  private val validLastClauses =
    "a RETURN clause, a FINISH clause, an update clause, a unit subquery call, or a procedure call with no YIELD"

  def withIsRequiredBetween(clause1: String, clause2: String, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N24(clause1, clause2, position.offset, position.line, position.column)
    SemanticError(gql, s"WITH is required between $clause1 and $clause2", position)
  }

  def invalidType(
    value: String,
    correctTypes: List[String],
    actualType: String,
    legacyMessage: String,
    pos: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22G03)
      .atPosition(pos.offset, pos.line, pos.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N01)
        .atPosition(pos.offset, pos.line, pos.column)
        .withParam(GqlParams.StringParam.value, value)
        .withParam(GqlParams.ListParam.valueTypeList, correctTypes.asJava)
        .withParam(GqlParams.StringParam.valueType, actualType)
        .build())
      .build()

    new SemanticError(
      gql,
      legacyMessage,
      pos
    )
  }

  def invalidPlacementOfUseClause(pos: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(pos.offset, pos.line, pos.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N73)
        .atPosition(pos.offset, pos.line, pos.column)
        .build())
      .build()

    new SemanticError(
      gql,
      "USE clause must be the first clause in a (sub-)query.",
      pos
    )
  }

  def invalidSubqueryInMerge(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42I48(position.offset, position.line, position.column)
    SemanticError(gql, "Subquery expressions are not allowed in a MERGE clause.", position)
  }

  def invalidUseOfMultiplePathPatterns(matchModeAvailable: Boolean, position: InputPosition): SemanticError = {
    val baseMessage =
      "Multiple path patterns cannot be used in the same clause in combination with a selective path selector."

    // Let's only mention match modes when that is an available feature
    val action = if (matchModeAvailable) {
      " You may want to use multiple MATCH clauses, or you might want to consider using the REPEATABLE ELEMENTS match mode."
    } else {
      ""
    }

    val gql = GqlHelper.getGql42001_42I45(action, position.offset, position.line, position.column)
    SemanticError(gql, baseMessage + action, position)
  }

  def invalidFieldTerminator(position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I05)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build()
    SemanticError(gql, "CSV field terminator can only be one character wide", position)
  }

  def singleRelationshipPatternRequired(name: String, position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N40(name, position.offset, position.line, position.column)
    SemanticError(gql, s"$name(...) requires a pattern containing a single relationship", position)
  }

  def inputContainsInvalidCharacters(
    invalidInput: String,
    context: String,
    legacyMessage: String,
    position: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N05)
      .atPosition(position.offset, position.line, position.column)
      .withParam(GqlParams.StringParam.input, invalidInput)
      .withParam(GqlParams.StringParam.context, context)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N82)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.input, invalidInput)
        .withParam(GqlParams.StringParam.context, context)
        .build())
      .build()
    SemanticError(gql, legacyMessage, position)
  }

  def numPrimariesOutOfRange(
    count: Int,
    command: String,
    topologyString: String,
    position: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22003)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N52)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.NumberParam.count, count)
        .withParam(GqlParams.NumberParam.upper, 11)
        .build())
      .build()

    SemanticError(
      gql,
      s"Failed to $command with `$topologyString`, PRIMARY must be greater than 0.",
      position
    )
  }

  def numSecondariesOutOfRange(
    count: Int,
    command: String,
    topologyString: String,
    position: InputPosition
  ): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22003)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N53)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.NumberParam.count, count)
        .withParam(GqlParams.NumberParam.upper, 20)
        .build())
      .build()

    SemanticError(
      gql,
      s"Failed to $command with `$topologyString`, SECONDARY must be a positive value",
      position
    )
  }

  def unsupportedUseOfProperties(props: Expression, funcName: String, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N56)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.fun, funcName)
        .build())
      .build()
    SemanticError(
      gql,
      s"$funcName(...) contains properties $props. This is currently not supported.",
      position
    )
  }

  def nodeVariableNotBound(funcName: String, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N65)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.fun, funcName)
        .build())
      .build()
    SemanticError(gql, s"A $funcName(...) requires bound nodes when not part of a MATCH clause.", position)
  }

  def relationshipVariableAlreadyBound(funcName: String, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N66)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.fun, funcName)
        .build())
      .build()
    SemanticError(gql, s"Bound relationships not allowed in $funcName(...)", position)
  }

  def qppInShortestPath(funcName: String, position: InputPosition): SemanticError = {
    SemanticError(
      GqlHelper.getGql42001_42I23(funcName, position.offset, position.line, position.column),
      s"$funcName(...) contains quantified pattern. This is currently not supported.",
      position
    )
  }

  def invalidLowerBound(funcName: String, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I08)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.fun, funcName)
        .build())
      .build()

    SemanticError(
      gql,
      s"$funcName(...) does not support a minimal length different from 0 or 1",
      position
    )
  }

  def invalidUseOfParameterMap(keyword: String, param: String, position: InputPosition): SemanticError = {
    SemanticError(
      GqlHelper.getGql42001_42N32(keyword, position.offset, position.line, position.column),
      s"Parameter maps cannot be used in `$keyword` patterns (use a literal map instead, e.g. `{id: $$$param.id}`)",
      position
    )
  }

  def invalidUseOfPatternExpression(position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I34)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build()

    SemanticError(gql, invalidUseOfPatternExpressionMessage, position)
  }

  val invalidUseOfPatternExpressionMessage: String =
    "A pattern expression should only be used in order to test the existence of a pattern. " +
      "It should therefore only be used in contexts that evaluate to a boolean, e.g. inside the function exists() or in a WHERE-clause. " +
      "No other uses are allowed, instead they should be replaced by a pattern comprehension."

  def invalidUseOfUnionAndCIT(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N47(position.offset, position.line, position.column)
    SemanticError(gql, "CALL { ... } IN TRANSACTIONS in a UNION is not supported", position)
  }

  def invalidDelete(position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I26)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build()

    SemanticError(gql, "DELETE doesn't support removing labels from a node. Try REMOVE.", position)
  }

  def unsafeUsageOfRepeatableElements(position: InputPosition): SemanticError = {
    SemanticError(
      GqlHelper.getGql42001_42N53(position.offset, position.line, position.column),
      "The quantified path pattern may yield an infinite number of rows under match mode 'REPEATABLE ELEMENTS'. " +
        "Add an upper bound to the quantified path pattern.",
      position
    )
  }

  def variableAlreadyDeclared(variableName: String, position: InputPosition): SemanticError = {
    variableAlreadyDeclared(variableName, position, s"Variable `${variableName}` already declared")
  }

  def variableAlreadyDeclared(variableName: String, position: InputPosition, legacyMessage: String): SemanticError = {
    SemanticError(
      GqlHelper.getGql42001_42N59(variableName, position.offset, position.line, position.column),
      legacyMessage,
      position
    )
  }

  def invalidUseOfVariableLengthRelationship(
    expr: String,
    legacyMessage: String,
    position: InputPosition
  ): SemanticError = {
    SemanticError(
      ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
        .atPosition(position.offset, position.line, position.column)
        .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I41)
          .atPosition(position.offset, position.line, position.column)
          .withParam(GqlParams.StringParam.value, expr)
          .build())
        .build(),
      legacyMessage,
      position
    )
  }

  def variableNotDefined(variableName: String, legacyMessage: String, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N62)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.variable, variableName)
        .build())
      .build()

    SemanticError(gql, legacyMessage, position)
  }

  def variableNotDefined(variableName: String, position: InputPosition): SemanticError = {
    variableNotDefined(variableName, s"Variable `$variableName` not defined", position)
  }

  def wrongInequalityOperator(position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I49)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build()
    SemanticError(
      gql,
      "Unknown operation '!=' (you probably meant to use '<>', which is the operator for inequality testing)",
      position
    )
  }

  def multipleReturnColumnsWithSameName(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N38(position.offset, position.line, position.column)
    SemanticError(gql, "Multiple result columns with the same name are not supported", position)
  }

  def multipleJoinHintsForSameVariable(variable: String, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N26)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.variable, variable)
        .build())
      .build()
    SemanticError(gql, "Multiple join hints for same variable are not supported", position)
  }

  def innerTypeWithDifferentNullability(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N63(position.offset, position.line, position.column)
    SemanticError(gql, "All types in a Closed Dynamic Union must be nullable, or be appended with `NOT NULL`", position)
  }

  def expressionCanOnlyBeUsedInMatch(
    startOfLegacyMessage: String,
    expr: String,
    clause: String,
    position: InputPosition
  ): SemanticError = {
    val gql = GqlHelper.getGql42001_42I04(expr, clause, position.offset, position.line, position.column)
    SemanticError(
      gql,
      s"$startOfLegacyMessage cannot be used in a $clause clause, but only in a MATCH clause.",
      position
    )
  }

  def patternPredicateInVarLengthRel(position: InputPosition): SemanticError = {
    SemanticError(
      GqlHelper.getGql42001_42N37(position.offset, position.line, position.column),
      "Relationship pattern predicates are not supported for variable-length relationships.",
      position
    )
  }

  def procedureCallWithoutParentheses(name: String, position: InputPosition): SemanticError = {
    SemanticError(
      GqlHelper.getGql42001_42N36(position.offset, position.line, position.column),
      "Procedure call is missing parentheses: " + name,
      position
    )
  }

  def procedureCallWithParenthesesWithArgs(name: String, position: InputPosition): SemanticError = {
    SemanticError(
      GqlHelper.getGql42001_42N36(position.offset, position.line, position.column),
      "Procedure call inside a query does not support passing arguments implicitly. " +
        "Please pass arguments explicitly in parentheses after procedure name for " + name,
      position
    )
  }

  def nestedQPP(position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I12)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build()

    SemanticError(
      gql,
      "Quantified path patterns are not allowed to be nested.",
      position
    )
  }

  def shortestPathInsideQPP(position: InputPosition): SemanticError = {
    SemanticError(
      GqlHelper.getGql42001_42N69(
        "shortestPath",
        "quantified path pattern",
        position.offset,
        position.line,
        position.column
      ),
      "shortestPath(...) is only allowed as a top-level element and not inside a quantified path pattern",
      position
    )
  }

  def shortestPathInsideParenthesizedPathPattern(shortestPathFunc: String, position: InputPosition): SemanticError = {
    SemanticError(
      GqlHelper.getGql42001_42N69(
        shortestPathFunc,
        "parenthesized path pattern",
        position.offset,
        position.line,
        position.column
      ),
      s"$shortestPathFunc(...) is only allowed as a top-level element and not inside a parenthesized path pattern",
      position
    )
  }

  def pathPatternNeedsAtLeastOnePattern(patternPart: String, position: InputPosition): SemanticError = {
    SemanticError(
      GqlHelper.getGql42001_42N64(position.offset, position.line, position.column),
      s"""A top-level path pattern in a `MATCH` clause must be written such that it always evaluates to at least one node pattern.
         |In this case, `$patternPart` would result in an empty pattern.""".stripMargin,
      position
    )
  }

  def qppNeedsAtLeastOneRelationship(
    pattern: String,
    nodeCountDescription: String,
    position: InputPosition
  ): SemanticError = {
    SemanticError(
      GqlHelper.getGql42001_42N64(position.offset, position.line, position.column),
      s"""A quantified path pattern needs to have at least one relationship.
         |In this case, the quantified path pattern $pattern consists of only $nodeCountDescription.""".stripMargin,
      position
    )
  }

  def invalidNodePatternPair(inThisCase: String, position: InputPosition): SemanticError = {
    SemanticError(
      GqlHelper.getGql42001_42I46(position.offset, position.line, position.column),
      s"""Juxtaposition is currently only supported for quantified path patterns.
         |$inThisCase
         |That is, neither of these is a quantified path pattern.""".stripMargin,
      position
    )
  }

  def invalidQuantifier(lower: Long, upper: Long, position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I17)
        .atPosition(position.offset, position.line, position.column)
        .build()).build()
    SemanticError(
      gql,
      s"""A quantifier for a path pattern must not have a lower bound which exceeds its upper bound.
         |In this case, the lower bound $lower is greater than the upper bound $upper.""".stripMargin,
      position
    )
  }

  def cannotYieldFromVoidProcedure(position: InputPosition): SemanticError = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I42)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build()
    SemanticError(gql, "Cannot yield value from void procedure.", position)
  }

  def pathBoundInQPP(position: InputPosition): SemanticError = {
    val gql = GqlHelper.getGql42001_42N34(position.offset, position.line, position.column)
    SemanticError(gql, "Assigning a path in a quantified path pattern is not yet supported.", position)
  }
}

sealed trait UnsupportedOpenCypher extends SemanticErrorDef

final case class FeatureError(
  override val gqlStatusObject: ErrorGqlStatusObject,
  override val msg: String,
  feature: SemanticFeature,
  override val position: InputPosition
) extends UnsupportedOpenCypher {

  def this(msg: String, featureError: SemanticFeature, position: InputPosition) =
    this(null, msg, featureError, position)
  override def withMsg(message: String): FeatureError = copy(msg = message)
}

object FeatureError {

  def apply(msg: String, featureError: SemanticFeature, position: InputPosition): FeatureError =
    new FeatureError(null, msg, featureError, position)

  def unapply(errorDef: FeatureError): Option[(String, SemanticFeature, InputPosition)] =
    Some((errorDef.msg, errorDef.feature, errorDef.position))
}
