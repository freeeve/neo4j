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

import org.neo4j.cypher.internal.ast
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.CreateConstraint
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.ClosedDynamicUnionType
import org.neo4j.cypher.internal.util.symbols.CypherType
import org.neo4j.cypher.internal.util.symbols.Float32Type
import org.neo4j.cypher.internal.util.symbols.FloatType
import org.neo4j.cypher.internal.util.symbols.Integer16Type
import org.neo4j.cypher.internal.util.symbols.Integer32Type
import org.neo4j.cypher.internal.util.symbols.Integer8Type
import org.neo4j.cypher.internal.util.symbols.IntegerType
import org.neo4j.cypher.internal.util.symbols.ListType
import org.neo4j.cypher.internal.util.symbols.NodeType
import org.neo4j.cypher.internal.util.symbols.PropertyValueCypher5Type
import org.neo4j.cypher.internal.util.symbols.PropertyValueType
import org.neo4j.cypher.internal.util.symbols.StringType
import org.neo4j.cypher.internal.util.symbols.VectorType
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.gqlstatus.ErrorGqlStatusObject
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation
import org.neo4j.gqlstatus.GqlParams
import org.neo4j.gqlstatus.GqlStatusInfoCodes

class CypherTypeCheckingTest extends CypherFunSuite with AstConstructionTestSupport {
  private val pos1 = InputPosition(2, 1, 3)
  private val pos2 = InputPosition(16, 5, 4)
  private val pos3 = InputPosition(23, 7, 6)
  private val pos4 = InputPosition(37, 8, 9)
  private val pos5 = InputPosition(42, 11, 14)

  private val initialStateNoFeatureFlag = SemanticState.clean
  private val initialState = SemanticState.clean.withFeature(SemanticFeature.VectorType)

  test("nullable property type should be valid for property type constraint") {
    val constraintCommand = relPropertyTypeConstraint(IntegerType(isNullable = true)(pos1), pos2)
    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe empty
  }

  test("non-nullable property type should not be valid for property type constraint") {
    val constraintCommand = nodePropertyTypeConstraint(IntegerType(isNullable = false)(pos1), pos2)
    assertPropertyTypeConstraintError(constraintCommand, "node", "INTEGER NOT NULL", pos1)
  }

  test("non-property type should not be valid for property type constraint") {
    val constraintCommand = nodePropertyTypeConstraint(NodeType(isNullable = true)(pos1), pos2)
    assertPropertyTypeConstraintError(constraintCommand, "node", "NODE", pos1)
  }

  // Vector types

  test("vector should be valid for node property type constraint") {
    val constraintCommand = nodePropertyTypeConstraint(
      VectorType(Some(IntegerType(isNullable = false)(pos1)), Some(512), isNullable = true)(pos2),
      pos3
    )
    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe empty
  }

  test("vector should be valid for relationship property type constraint") {
    val constraintCommand = relPropertyTypeConstraint(
      VectorType(Some(Float32Type(isNullable = false)(pos1)), Some(12), isNullable = true)(pos2),
      pos3
    )
    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe empty
  }

  test("vector with omitted dimension should not be valid for property type constraint") {
    val constraintCommand = nodePropertyTypeConstraint(
      VectorType(Some(Integer16Type(isNullable = false)(pos1)), None, isNullable = true)(pos2),
      pos3
    )

    assertPropertyTypeConstraintErrorWithCause(
      constraintCommand,
      "node",
      "VECTOR<INTEGER16 NOT NULL>",
      ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NBA).build(),
      "Property type constraints for vectors need to define both coordinate type and dimension.",
      pos2
    )
  }

  test("vector with omitted coordinate type should not be valid for property type constraint") {
    val constraintCommand = relPropertyTypeConstraint(VectorType(None, Some(42), isNullable = true)(pos1), pos2)

    assertPropertyTypeConstraintErrorWithCause(
      constraintCommand,
      "relationship",
      "VECTOR(42)",
      ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NBA).build(),
      "Property type constraints for vectors need to define both coordinate type and dimension.",
      pos1
    )
  }

  test("vector with omitted dimension and coordinate type should not be valid for property type constraint") {
    val constraintCommand = nodePropertyTypeConstraint(VectorType(None, None, isNullable = true)(pos1), pos2)

    assertPropertyTypeConstraintErrorWithCause(
      constraintCommand,
      "node",
      "VECTOR",
      ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NBA).build(),
      "Property type constraints for vectors need to define both coordinate type and dimension.",
      pos1
    )
  }

  test("vector with negative dimension should not be valid for property type constraint") {
    val constraintCommand = relPropertyTypeConstraint(
      VectorType(Some(FloatType(isNullable = false)(pos1)), Some(-1), isNullable = true)(pos2),
      pos3
    )

    assertPropertyTypeConstraintErrorWithCause(
      constraintCommand,
      "relationship",
      "VECTOR<FLOAT NOT NULL>(-1)",
      dimensionErrorCause(-1),
      "The dimension of property type constraints for vectors needs to be between 1 and 4096.",
      pos2
    )
  }

  test("vector with zero dimension should not be valid for property type constraint") {
    val constraintCommand = nodePropertyTypeConstraint(
      VectorType(Some(IntegerType(isNullable = false)(pos1)), Some(0), isNullable = true)(pos2),
      pos3
    )

    assertPropertyTypeConstraintErrorWithCause(
      constraintCommand,
      "node",
      "VECTOR<INTEGER NOT NULL>(0)",
      dimensionErrorCause(0),
      "The dimension of property type constraints for vectors needs to be between 1 and 4096.",
      pos2
    )
  }

  test("vector with one dimension should be valid for property type constraint") {
    val constraintCommand = nodePropertyTypeConstraint(
      VectorType(Some(Integer32Type(isNullable = false)(pos1)), Some(1), isNullable = true)(pos2),
      pos3
    )
    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe empty
  }

  test("vector with dimension 4096 should be valid for property type constraint") {
    val constraintCommand = relPropertyTypeConstraint(
      VectorType(Some(Integer8Type(isNullable = false)(pos1)), Some(4096), isNullable = true)(pos2),
      pos3
    )
    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe empty
  }

  test("vector with too large dimension should not be valid for property type constraint") {
    val constraintCommand = relPropertyTypeConstraint(
      VectorType(Some(Float32Type(isNullable = false)(pos1)), Some(4097), isNullable = true)(pos2),
      pos3
    )

    assertPropertyTypeConstraintErrorWithCause(
      constraintCommand,
      "relationship",
      "VECTOR<FLOAT32 NOT NULL>(4097)",
      dimensionErrorCause(4097),
      "The dimension of property type constraints for vectors needs to be between 1 and 4096.",
      pos2
    )
  }

  test("vector should not be valid for property type constraint without feature flag") {
    val constraintCommand = nodePropertyTypeConstraint(
      VectorType(Some(Float32Type(isNullable = false)(pos1)), Some(4), isNullable = true)(pos2),
      pos3
    )
    assertVectorFeatureFlagError(constraintCommand, pos2)
  }

  // List types

  test("nullable list of non-nullable, non-vector property type should be valid for property type constraint") {
    val constraintCommand =
      relPropertyTypeConstraint(ListType(StringType(isNullable = false)(pos1), isNullable = true)(pos2), pos3)

    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe empty
  }

  test("non-nullable list of property type should not be valid for property type constraint") {
    val constraintCommand =
      relPropertyTypeConstraint(ListType(StringType(isNullable = false)(pos1), isNullable = false)(pos2), pos3)

    assertPropertyTypeConstraintError(constraintCommand, "relationship", "LIST<STRING NOT NULL> NOT NULL", pos2)
  }

  test("nullable list of nullable property type should not be valid for property type constraint") {
    val constraintCommand =
      nodePropertyTypeConstraint(ListType(FloatType(isNullable = true)(pos1), isNullable = true)(pos2), pos3)

    val expectedCause = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NB9)
      .withParam(GqlParams.StringParam.typeDescription, "a nullable type")
      .build()

    assertPropertyTypeConstraintErrorWithCause(
      constraintCommand,
      "node",
      "LIST<FLOAT>",
      expectedCause,
      "Lists cannot have nullable inner types.",
      pos2
    )
  }

  test("list of vector should not be valid for property type constraint") {
    val constraintCommand = relPropertyTypeConstraint(
      ListType(
        VectorType(Some(Float32Type(isNullable = false)(pos1)), Some(12), isNullable = false)(pos2),
        isNullable = true
      )(pos3),
      pos4
    )

    val expectedCause = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NB9)
      .withParam(GqlParams.StringParam.typeDescription, "a vector")
      .build()

    assertPropertyTypeConstraintErrorWithCause(
      constraintCommand,
      "relationship",
      "LIST<VECTOR<FLOAT32 NOT NULL>(12) NOT NULL>",
      expectedCause,
      "Lists cannot have a vector as an inner type.",
      pos3
    )
  }

  test("list of list should not be valid for property type constraint") {
    val constraintCommand = nodePropertyTypeConstraint(
      ListType(ListType(FloatType(isNullable = false)(pos1), isNullable = true)(pos2), isNullable = true)(pos3),
      pos4
    )

    val expectedCause = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NB9)
      .withParam(GqlParams.StringParam.typeDescription, "a list")
      .build()

    assertPropertyTypeConstraintErrorWithCause(
      constraintCommand,
      "node",
      "LIST<LIST<FLOAT NOT NULL>>",
      expectedCause,
      "Lists cannot have lists as an inner type.",
      pos3
    )
  }

  test("list of union should not be valid for property type constraint") {
    val constraintCommand = relPropertyTypeConstraint(
      ListType(
        ClosedDynamicUnionType(Set(IntegerType(isNullable = false)(pos1), FloatType(isNullable = false)(pos2)))(pos3),
        isNullable = true
      )(pos4),
      pos5
    )

    val expectedCause = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NB9)
      .withParam(GqlParams.StringParam.typeDescription, "a union of types")
      .build()

    assertPropertyTypeConstraintErrorWithCause(
      constraintCommand,
      "relationship",
      "LIST<INTEGER NOT NULL | FLOAT NOT NULL>",
      expectedCause,
      "Lists cannot have a union of types as an inner type.",
      pos4
    )
  }

  // Union of types

  test("union of valid types should be valid for property type constraint") {
    val constraintCommand = nodePropertyTypeConstraint(
      ClosedDynamicUnionType(Set(
        ListType(IntegerType(isNullable = false)(pos1), isNullable = true)(pos2),
        FloatType(isNullable = true)(pos3)
      ))(pos4),
      pos5
    )

    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe empty
  }

  test("union with vector should be valid for property type constraint") {
    val constraintCommand = nodePropertyTypeConstraint(
      ClosedDynamicUnionType(Set(
        ListType(IntegerType(isNullable = false)(pos1), isNullable = true)(pos2),
        VectorType(Some(IntegerType(isNullable = false)(pos3)), Some(3), isNullable = true)(pos4)
      ))(pos5),
      pos1
    )

    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe empty
  }

  test("union with vector with omitted dimension should not be valid for property type constraint") {
    val constraintCommand = nodePropertyTypeConstraint(
      ClosedDynamicUnionType(Set(
        ListType(IntegerType(isNullable = false)(pos1), isNullable = true)(pos2),
        VectorType(Some(IntegerType(isNullable = false)(pos3)), None, isNullable = true)(pos4)
      ))(pos5),
      pos1
    )

    assertPropertyTypeConstraintErrorWithCause(
      constraintCommand,
      "node",
      "VECTOR<INTEGER NOT NULL> | LIST<INTEGER NOT NULL>",
      ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NBA).build(),
      "Property type constraints for vectors need to define both coordinate type and dimension.",
      pos5
    )
  }

  test("union with vector of invalid dimension should not be valid for property type constraint") {
    val constraintCommand = nodePropertyTypeConstraint(
      ClosedDynamicUnionType(Set(
        ListType(IntegerType(isNullable = false)(pos1), isNullable = true)(pos2),
        VectorType(Some(IntegerType(isNullable = false)(pos3)), Some(0), isNullable = true)(pos4)
      ))(pos5),
      pos1
    )

    assertPropertyTypeConstraintErrorWithCause(
      constraintCommand,
      "node",
      "VECTOR<INTEGER NOT NULL>(0) | LIST<INTEGER NOT NULL>",
      dimensionErrorCause(0),
      "The dimension of property type constraints for vectors needs to be between 1 and 4096.",
      pos5
    )
  }

  test("union with vector should not be valid for property type constraint without feature flag") {
    val constraintCommand = nodePropertyTypeConstraint(
      ClosedDynamicUnionType(Set(
        ListType(IntegerType(isNullable = false)(pos1), isNullable = true)(pos2),
        VectorType(Some(IntegerType(isNullable = false)(pos3)), Some(42), isNullable = true)(pos4)
      ))(pos5),
      pos1
    )

    assertVectorFeatureFlagError(constraintCommand, pos4)
  }

  test("union with none valid type should not be valid for property type constraint") {
    val constraintCommand = nodePropertyTypeConstraint(
      ClosedDynamicUnionType(Set(
        IntegerType(isNullable = false)(pos1),
        NodeType(isNullable = false)(pos2)
      ))(pos3),
      pos4
    )

    assertPropertyTypeConstraintError(constraintCommand, "node", "INTEGER NOT NULL | NODE NOT NULL", pos3)
  }

  test("any property value should not be valid for property type constraint") {
    val propertyValueTypes = Seq(
      (PropertyValueType(isNullable = true)(pos1), "PROPERTY VALUE"),
      (PropertyValueCypher5Type(isNullable = true)(pos1), "PROPERTY VALUE"),
      (ListType(PropertyValueType(isNullable = false)(pos3), isNullable = true)(pos1), "LIST<PROPERTY VALUE NOT NULL>"),
      (
        ListType(PropertyValueCypher5Type(isNullable = false)(pos3), isNullable = true)(pos1),
        "LIST<PROPERTY VALUE NOT NULL>"
      ),
      (
        ClosedDynamicUnionType(Set(
          IntegerType(isNullable = true)(pos4),
          PropertyValueType(isNullable = true)(pos3)
        ))(pos1),
        "INTEGER | PROPERTY VALUE"
      ),
      (
        ClosedDynamicUnionType(Set(
          IntegerType(isNullable = true)(pos4),
          PropertyValueCypher5Type(isNullable = true)(pos3)
        ))(pos1),
        "INTEGER | PROPERTY VALUE"
      )
    )

    propertyValueTypes.foreach { case (propertyType, typeString) =>
      withClue(s"PropertyType = $propertyType \n") {
        val constraintCommand = relPropertyTypeConstraint(propertyType, pos2)
        assertPropertyTypeConstraintError(constraintCommand, "relationship", typeString, pos1)
      }

    }
  }

  // Help methods

  private def nodePropertyTypeConstraint(propertyType: CypherType, position: InputPosition): CreateConstraint = {
    ast.CreateConstraint.createNodePropertyTypeConstraint(
      varFor("n"),
      labelName("Label"),
      prop("n", "prop"),
      propertyType,
      None,
      ast.IfExistsThrowError,
      ast.NoOptions
    )(position)
  }

  private def relPropertyTypeConstraint(propertyType: CypherType, position: InputPosition): CreateConstraint = {
    ast.CreateConstraint.createRelationshipPropertyTypeConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      propertyType,
      None,
      ast.IfExistsThrowError,
      ast.NoOptions
    )(position)
  }

  private def assertPropertyTypeConstraintError(
    constraintCommand: CreateConstraint,
    elementType: String,
    cypherType: String,
    position: InputPosition
  ): Unit = {
    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe SemanticCheckResult
      .error(
        initialState,
        SemanticError(
          ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N11)
            .withParam(GqlParams.StringParam.constrDescrOrName, s"$elementType property type constraint")
            .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N90)
              .withParam(GqlParams.StringParam.valueType, cypherType)
              .build())
            .build(),
          s"Failed to create $elementType property type constraint: Invalid property type `$cypherType`.",
          position
        )
      ).errors
  }

  private def assertPropertyTypeConstraintErrorWithCause(
    constraintCommand: CreateConstraint,
    elementType: String,
    cypherType: String,
    cause: ErrorGqlStatusObject,
    additionalErrorInfo: String,
    position: InputPosition
  ): Unit = {
    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe SemanticCheckResult
      .error(
        initialState,
        SemanticError(
          ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N11)
            .withParam(GqlParams.StringParam.constrDescrOrName, s"$elementType property type constraint")
            .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N90)
              .withParam(GqlParams.StringParam.valueType, cypherType)
              .withCause(cause)
              .build())
            .build(),
          s"Failed to create $elementType property type constraint: Invalid property type `$cypherType`. " +
            additionalErrorInfo,
          position
        )
      ).errors
  }

  private def assertVectorFeatureFlagError(
    constraintCommand: CreateConstraint,
    position: InputPosition
  ): Unit = {
    constraintCommand.semanticCheck.run(
      initialStateNoFeatureFlag,
      SemanticCheckContext.default
    ).errors shouldBe SemanticCheckResult
      .error(
        initialStateNoFeatureFlag,
        FeatureError(
          ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N26)
            .withParam(GqlParams.StringParam.item, "Property type constraints for vectors")
            .withParam(GqlParams.StringParam.feat, "new vector type")
            .build(),
          "Property type constraints for vectors is not available in this implementation of Cypher due to lack of support for new vector type.",
          SemanticFeature.VectorType,
          position
        )
      ).errors
  }

  private def dimensionErrorCause(dimension: Int): ErrorGqlStatusObject = {
    ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N31)
      .withParam(GqlParams.StringParam.component, "DIMENSION")
      .withParam(GqlParams.StringParam.valueType, "INTEGER NOT NULL")
      .withParam(GqlParams.NumberParam.lower, 1)
      .withParam(GqlParams.NumberParam.upper, 4096)
      .withParam(GqlParams.StringParam.value, dimension.toString)
      .build()
  }
}
