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
package org.neo4j.cypher.internal.util

import org.neo4j.exceptions.Neo4jException
import org.neo4j.exceptions.SyntaxException
import org.neo4j.gqlstatus.CommonGqlStatusObjectImplementation
import org.neo4j.gqlstatus.ErrorGqlStatusObject
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation
import org.neo4j.gqlstatus.GqlHelper
import org.neo4j.gqlstatus.GqlParams
import org.neo4j.gqlstatus.GqlStatusInfoCodes

import scala.jdk.CollectionConverters.SeqHasAsJava

trait CypherExceptionFactory {

  @deprecated("Use version with gqlStatusObject instead", since = "2025.05")
  def syntaxException(message: String, pos: InputPosition): RuntimeException
  def syntaxException(gqlStatusObject: ErrorGqlStatusObject, message: String, pos: InputPosition): RuntimeException

  def duplicateClause(clause: String, legacyMessage: String, pos: InputPosition): RuntimeException = {
    val gql = GqlHelper.getGql42001_42N19(clause, pos.offset, pos.line, pos.column)
    syntaxException(
      gql,
      legacyMessage,
      pos
    )
  }

  def unsupportedRequestOnSystemDatabaseException(
    invalidInput: String,
    legacyMessage: String,
    pos: InputPosition
  ): RuntimeException = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .withCause(
        ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N17)
          .withParam(GqlParams.StringParam.input, invalidInput)
          .build()
      )
      .build()
    syntaxException(gql, legacyMessage, pos)
  }

  def invalidInputException(
    wrongInput: String,
    forField: String,
    expectedInput: List[String],
    legacyMessage: String,
    pos: InputPosition
  ): RuntimeException = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .withCause(
        ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N04)
          .withParam(GqlParams.StringParam.input, wrongInput)
          .withParam(GqlParams.StringParam.context, forField)
          .withParam(GqlParams.ListParam.inputList, expectedInput.asJava)
          .build()
      )
      .build()
    syntaxException(gql, legacyMessage, pos)
  }

  def invalidUseOfAggregationInOrderBy(
    clause: String,
    position: InputPosition
  ): RuntimeException = {
    syntaxException(
      GqlHelper.getGql42001_42N23(clause, position.offset, position.line, position.column),
      s"Cannot use aggregation in ORDER BY if there are no aggregate expressions in the preceding $clause",
      position
    )
  }

  def unsupportedPathSelectorInPathPattern(
    selector: String,
    pathPatternKind: String,
    position: InputPosition
  ): RuntimeException = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N35)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.selector, selector)
        .build())
      .build()

    syntaxException(
      gql,
      s"Path selectors such as `$selector` are not supported within $pathPatternKind path patterns.",
      position
    )
  }

  def unsupportedMultiplePropertiesInConstraint(
    constraintType: String,
    position: InputPosition
  ): RuntimeException = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N16)
        .atPosition(position.offset, position.line, position.column)
        .withParam(GqlParams.StringParam.idxType, s"'$constraintType' constraints")
        .build())
      .build()
    syntaxException(gql, s"Constraint type '$constraintType' does not allow multiple properties", position)
  }

  def invalidNormalForm(normalForm: ASTNode): RuntimeException = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(normalForm.position.offset, normalForm.position.line, normalForm.position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N49)
        .atPosition(normalForm.position.offset, normalForm.position.line, normalForm.position.column)
        .withParam(GqlParams.StringParam.input, normalForm.asCanonicalStringVal)
        .build)
      .build

    syntaxException(
      gql,
      "Invalid normal form, expected NFC, NFD, NFKC, NFKD",
      normalForm.position
    )
  }

  def invalidVectorType(vectorType: ASTNode): RuntimeException = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(vectorType.position.offset, vectorType.position.line, vectorType.position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I53)
        .atPosition(vectorType.position.offset, vectorType.position.line, vectorType.position.column)
        .withParam(GqlParams.StringParam.input, vectorType.asCanonicalStringVal)
        .build)
      .build

    syntaxException(
      gql,
      "Invalid vector inner type, expected INTEGER64, INTEGER32, INTEGER16, INTEGER8, FLOAT64 or FLOAT32",
      vectorType.position
    )
  }

  def invalidNotNullClosedDynamicUnion(position: InputPosition): RuntimeException = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I33)
        .atPosition(position.offset, position.line, position.column)
        .build())
      .build()

    syntaxException(
      gql,
      "Closed Dynamic Union Types can not be appended with `NOT NULL`, specify `NOT NULL` on all inner types instead.",
      position
    )
  }

  def duplicateClauseParameter(description: String, position: InputPosition): RuntimeException = {
    val gql = GqlHelper.getGql42001_42N19(description, position.offset, position.line, position.column)
    syntaxException(
      gql,
      s"Duplicated $description parameters",
      position
    )
  }

  def stringLiteralWithInvalidQuotes(position: InputPosition): RuntimeException = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(
        ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42I19)
          .atPosition(position.offset, position.line, position.column)
          .build()
      ).build()

    syntaxException(
      gql,
      SyntaxException.QUOTE_MISMATCH_ERROR_MESSAGE,
      position
    )
  }

  def invalidNameTooManyComponents(
    errorTemplate: String,
    context: String,
    maxComponents: Int,
    prettyName: String,
    position: InputPosition
  ): RuntimeException = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(
        ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N05)
          .atPosition(position.offset, position.line, position.column)
          .withParam(GqlParams.StringParam.input, prettyName)
          .withParam(GqlParams.StringParam.context, context)
          .withCause(
            ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N83)
              .atPosition(position.offset, position.line, position.column)
              .withParam(GqlParams.NumberParam.upper, maxComponents)
              .build()
          )
          .build()
      )
      .build()

    syntaxException(
      gql,
      errorTemplate.formatted(prettyName),
      position
    )
  }

  def invalidCharacterForRemoteAlias(aliasName: String, position: InputPosition): RuntimeException = {
    val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
      .atPosition(position.offset, position.line, position.column)
      .withCause(
        ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N05)
          .atPosition(position.offset, position.line, position.column)
          .withParam(GqlParams.StringParam.input, aliasName)
          .withParam(GqlParams.StringParam.context, "remote alias name")
          .withCause(
            ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N82)
              .atPosition(position.offset, position.line, position.column)
              .withParam(GqlParams.StringParam.input, aliasName)
              .withParam(GqlParams.StringParam.context, "remote alias name")
              .build()
          )
          .build(
          )
      ).build()
    syntaxException(
      gql,
      s"'.' is not a valid character in the remote alias name '$aliasName'. Remote alias names using '.' must be quoted with backticks e.g. `remote.alias`.",
      position
    )
  }
}

case class Neo4jCypherExceptionFactory(queryText: String, preParserOffset: Option[InputPosition])
    extends CypherExceptionFactory {

  @deprecated("Use version with gqlStatusObject instead", since = "2025.05")
  override def syntaxException(message: String, pos: InputPosition): Neo4jException = {
    val adjustedPosition = pos.withOffset(preParserOffset)
    new SyntaxException(s"$message ($adjustedPosition)", queryText, adjustedPosition.offset)
  }

  override def syntaxException(
    gqlStatusObject: ErrorGqlStatusObject,
    message: String,
    pos: InputPosition
  ): Neo4jException = {
    val adjustedPosition = pos.withOffset(preParserOffset)

    // Adjust the position in the GQL object
    val gqlWithAdjustedPosition =
      if (gqlStatusObject != null) {
        val gqlImpl = gqlStatusObject.asInstanceOf[CommonGqlStatusObjectImplementation]
        gqlImpl.adjustPosition(
          pos.offset,
          pos.line,
          pos.column,
          adjustedPosition.offset,
          adjustedPosition.line,
          adjustedPosition.column
        )
        gqlImpl.asInstanceOf[ErrorGqlStatusObject]
      } else {
        gqlStatusObject
      }

    new SyntaxException(
      gqlWithAdjustedPosition,
      s"$message ($adjustedPosition)",
      queryText,
      adjustedPosition.offset
    )
  }

}
