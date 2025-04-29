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
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.ClosedDynamicUnionType
import org.neo4j.cypher.internal.util.symbols.FloatType
import org.neo4j.cypher.internal.util.symbols.IntegerType
import org.neo4j.cypher.internal.util.symbols.ListType
import org.neo4j.cypher.internal.util.symbols.NodeType
import org.neo4j.cypher.internal.util.symbols.PropertyValueCypher5Type
import org.neo4j.cypher.internal.util.symbols.PropertyValueType
import org.neo4j.cypher.internal.util.symbols.StringType
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation
import org.neo4j.gqlstatus.GqlParams
import org.neo4j.gqlstatus.GqlStatusInfoCodes

class CypherTypeCheckingTest extends CypherFunSuite with AstConstructionTestSupport {
  private val pos1 = InputPosition(2, 1, 3)
  private val pos2 = InputPosition(16, 5, 4)
  private val pos3 = InputPosition(23, 7, 6)
  private val pos4 = InputPosition(37, 8, 9)
  private val pos5 = InputPosition(42, 11, 14)

  private val initialState =
    SemanticState.clean
      .withFeature(SemanticFeature.VectorType)

  test("nullable property type should be valid for property type constraint") {
    val constraintCommand = ast.CreateConstraint.createRelationshipPropertyTypeConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      IntegerType(isNullable = true)(pos1),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions
    )(pos2)

    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe empty
  }

  test("non-nullable property type should not be valid for property type constraint") {
    val constraintCommand = ast.CreateConstraint.createNodePropertyTypeConstraint(
      varFor("n"),
      labelName("Label"),
      prop("n", "prop"),
      IntegerType(isNullable = false)(pos1),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions
    )(pos2)

    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe SemanticCheckResult
      .error(
        initialState,
        SemanticError(
          ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N11)
            .withParam(GqlParams.StringParam.constrDescrOrName, "node property type constraint")
            .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N90)
              .withParam(GqlParams.StringParam.item, "INTEGER NOT NULL")
              .build())
            .build(),
          "Failed to create node property type constraint: Invalid property type `INTEGER NOT NULL`.",
          pos1
        )
      ).errors
  }

  test("non-property type should not be valid for property type constraint") {
    val constraintCommand = ast.CreateConstraint.createNodePropertyTypeConstraint(
      varFor("n"),
      labelName("Label"),
      prop("n", "prop"),
      NodeType(isNullable = true)(pos1),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions
    )(pos2)

    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe SemanticCheckResult
      .error(
        initialState,
        SemanticError(
          ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N11)
            .withParam(GqlParams.StringParam.constrDescrOrName, "node property type constraint")
            .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N90)
              .withParam(GqlParams.StringParam.item, "NODE")
              .build())
            .build(),
          "Failed to create node property type constraint: Invalid property type `NODE`.",
          pos1
        )
      ).errors
  }

  test("nullable list of non-nullable property type should be valid for property type constraint") {
    val constraintCommand = ast.CreateConstraint.createRelationshipPropertyTypeConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      ListType(StringType(isNullable = false)(pos1), isNullable = true)(pos2),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions
    )(pos3)

    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe empty
  }

  test("non-nullable list of property type should not be valid for property type constraint") {
    val constraintCommand = ast.CreateConstraint.createRelationshipPropertyTypeConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      ListType(StringType(isNullable = false)(pos1), isNullable = false)(pos2),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions
    )(pos3)

    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe SemanticCheckResult
      .error(
        initialState,
        SemanticError(
          ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N11)
            .withParam(GqlParams.StringParam.constrDescrOrName, "relationship property type constraint")
            .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N90)
              .withParam(GqlParams.StringParam.item, "LIST<STRING NOT NULL> NOT NULL")
              .build())
            .build(),
          "Failed to create relationship property type constraint: Invalid property type `LIST<STRING NOT NULL> NOT NULL`.",
          pos2
        )
      ).errors
  }

  test("nullable list of nullable property type should not be valid for property type constraint") {
    val constraintCommand = ast.CreateConstraint.createNodePropertyTypeConstraint(
      varFor("n"),
      labelName("Label"),
      prop("n", "prop"),
      ListType(FloatType(isNullable = true)(pos1), isNullable = true)(pos2),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions
    )(pos3)

    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe SemanticCheckResult
      .error(
        initialState,
        SemanticError(
          ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N11)
            .withParam(GqlParams.StringParam.constrDescrOrName, "node property type constraint")
            .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N90)
              .withParam(GqlParams.StringParam.item, "LIST<FLOAT>")
              .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NB9)
                .withParam(GqlParams.StringParam.item, "a nullable type")
                .build())
              .build())
            .build(),
          "Failed to create node property type constraint: Invalid property type `LIST<FLOAT>`. " +
            "Lists cannot have nullable inner types.",
          pos2
        )
      ).errors
  }

  test("list of list should not be valid for property type constraint") {
    val constraintCommand = ast.CreateConstraint.createNodePropertyTypeConstraint(
      varFor("n"),
      labelName("Label"),
      prop("n", "prop"),
      ListType(
        ListType(FloatType(isNullable = false)(pos1), isNullable = true)(pos2),
        isNullable = true
      )(pos3),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions
    )(pos4)

    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe SemanticCheckResult
      .error(
        initialState,
        SemanticError(
          ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N11)
            .withParam(GqlParams.StringParam.constrDescrOrName, "node property type constraint")
            .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N90)
              .withParam(GqlParams.StringParam.item, "LIST<LIST<FLOAT NOT NULL>>")
              .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NB9)
                .withParam(GqlParams.StringParam.item, "a list")
                .build())
              .build())
            .build(),
          "Failed to create node property type constraint: Invalid property type `LIST<LIST<FLOAT NOT NULL>>`. " +
            "Lists cannot have lists as an inner type.",
          pos3
        )
      ).errors
  }

  test("list of union should not be valid for property type constraint") {
    val constraintCommand = ast.CreateConstraint.createRelationshipPropertyTypeConstraint(
      varFor("r"),
      relTypeName("R"),
      prop("r", "prop"),
      ListType(
        ClosedDynamicUnionType(Set(
          IntegerType(isNullable = false)(pos1),
          FloatType(isNullable = false)(pos2)
        ))(pos3),
        isNullable = true
      )(pos4),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions
    )(pos5)

    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe SemanticCheckResult
      .error(
        initialState,
        SemanticError(
          ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N11)
            .withParam(GqlParams.StringParam.constrDescrOrName, "relationship property type constraint")
            .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N90)
              .withParam(GqlParams.StringParam.item, "LIST<INTEGER NOT NULL | FLOAT NOT NULL>")
              .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22NB9)
                .withParam(GqlParams.StringParam.item, "a union of types")
                .build())
              .build())
            .build(),
          "Failed to create relationship property type constraint: Invalid property type `LIST<INTEGER NOT NULL | FLOAT NOT NULL>`. " +
            "Lists cannot have a union of types as an inner type.",
          pos4
        )
      ).errors
  }

  test("union of valid types should be valid for property type constraint") {
    val constraintCommand = ast.CreateConstraint.createNodePropertyTypeConstraint(
      varFor("n"),
      labelName("Label"),
      prop("n", "prop"),
      ClosedDynamicUnionType(Set(
        ListType(IntegerType(isNullable = false)(pos1), isNullable = true)(pos2),
        FloatType(isNullable = true)(pos3)
      ))(pos4),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions
    )(pos5)

    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe empty
  }

  test("union with none valid type should not be valid for property type constraint") {
    val constraintCommand = ast.CreateConstraint.createNodePropertyTypeConstraint(
      varFor("n"),
      labelName("Label"),
      prop("n", "prop"),
      ClosedDynamicUnionType(Set(
        IntegerType(isNullable = false)(pos1),
        NodeType(isNullable = false)(pos2)
      ))(pos3),
      None,
      ast.IfExistsThrowError,
      ast.NoOptions
    )(pos4)

    constraintCommand.semanticCheck.run(initialState, SemanticCheckContext.default).errors shouldBe SemanticCheckResult
      .error(
        initialState,
        SemanticError(
          ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N11)
            .withParam(GqlParams.StringParam.constrDescrOrName, "node property type constraint")
            .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N90)
              .withParam(GqlParams.StringParam.item, "INTEGER NOT NULL | NODE NOT NULL")
              .build())
            .build(),
          "Failed to create node property type constraint: Invalid property type `INTEGER NOT NULL | NODE NOT NULL`.",
          pos3
        )
      ).errors
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
        val constraintCommand = ast.CreateConstraint.createRelationshipPropertyTypeConstraint(
          varFor("r"),
          relTypeName("R"),
          prop("r", "prop"),
          propertyType,
          None,
          ast.IfExistsThrowError,
          ast.NoOptions
        )(pos2)

        constraintCommand.semanticCheck.run(
          initialState,
          SemanticCheckContext.default
        ).errors shouldBe SemanticCheckResult
          .error(
            initialState,
            SemanticError(
              ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N11)
                .withParam(GqlParams.StringParam.constrDescrOrName, "relationship property type constraint")
                .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N90)
                  .withParam(GqlParams.StringParam.item, typeString)
                  .build())
                .build(),
              s"Failed to create relationship property type constraint: Invalid property type `$typeString`.",
              pos1
            )
          ).errors
      }

    }
  }
}
