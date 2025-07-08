/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.constraint

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.SchemaCommandRuntime.constraintInfo
import org.neo4j.cypher.internal.SchemaCommandRuntime.convertConstraintTypeToConstraintMatcher
import org.neo4j.cypher.internal.SchemaCommandRuntime.existingConstraintInfo
import org.neo4j.cypher.internal.SchemaCommandRuntime.getEntityInfo
import org.neo4j.cypher.internal.SchemaCommandRuntime.getName
import org.neo4j.cypher.internal.SchemaCommandRuntime.indexContext
import org.neo4j.cypher.internal.SchemaCommandRuntime.labelPropWithName
import org.neo4j.cypher.internal.SchemaCommandRuntime.propertyToId
import org.neo4j.cypher.internal.SchemaCommandRuntime.typePropWithName
import org.neo4j.cypher.internal.ast.CreateConstraintType
import org.neo4j.cypher.internal.ast.NodeKey
import org.neo4j.cypher.internal.ast.NodePropertyExistence
import org.neo4j.cypher.internal.ast.NodePropertyType
import org.neo4j.cypher.internal.ast.NodePropertyUniqueness
import org.neo4j.cypher.internal.ast.Options
import org.neo4j.cypher.internal.ast.RelationshipKey
import org.neo4j.cypher.internal.ast.RelationshipPropertyExistence
import org.neo4j.cypher.internal.ast.RelationshipPropertyType
import org.neo4j.cypher.internal.ast.RelationshipPropertyUniqueness
import org.neo4j.cypher.internal.ast.prettifier.Prettifier
import org.neo4j.cypher.internal.expressions.ElementTypeName
import org.neo4j.cypher.internal.expressions.LabelName
import org.neo4j.cypher.internal.expressions.Parameter
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.optionsmap.IndexBackedConstraintsOptionsConverter
import org.neo4j.cypher.internal.optionsmap.PropertyExistenceOrTypeConstraintOptionsConverter
import org.neo4j.cypher.internal.procs.IgnoredResult
import org.neo4j.cypher.internal.procs.PropertyTypeMapper
import org.neo4j.cypher.internal.procs.SchemaExecutionResult
import org.neo4j.cypher.internal.procs.SuccessResult
import org.neo4j.cypher.internal.runtime.QueryContext
import org.neo4j.cypher.internal.util.IndexOrConstraintAlreadyExistsNotification
import org.neo4j.cypher.internal.util.IndexOrConstraintDoesNotExistNotification
import org.neo4j.cypher.internal.util.InternalNotification
import org.neo4j.cypher.internal.util.symbols.CypherType
import org.neo4j.values.virtual.MapValue

/**
 * Create the schema write functions for the community and enterprise constraint command SchemaExecutionPlan's.
 * The enterprise commands are just here to not change the existing errors for them.
 */
object ConstraintCommandPlanner {

  // Create methods

  def createNodeKeyConstraint(
    nodeKey: NodeKey,
    label: LabelName,
    props: Seq[Property],
    name: Option[Either[String, Parameter]],
    options: Options,
    cypherVersion: CypherVersion
  ): (QueryContext, MapValue) => SchemaExecutionResult =
    (ctx, params) => {
      val constraintName = getName(name, params)
      val (maybeIndexProvider, notifications) =
        IndexBackedConstraintsOptionsConverter(s"${nodeKey.description} constraint", indexContext(ctx))
          .convert(cypherVersion, options, params)
          .toOptionNotification
      val indexProvider = maybeIndexProvider.flatMap(_.provider)
      val labelId = ctx.getOrCreateLabelId(label.name)
      val propertyKeyIds = props.map(p => propertyToId(ctx)(p.propertyKey).id)
      ctx.createNodeKeyConstraint(labelId, propertyKeyIds, constraintName, indexProvider)
      SuccessResult(notifications)
    }

  def createRelationshipKeyConstraint(
    relKey: RelationshipKey,
    relType: RelTypeName,
    props: Seq[Property],
    name: Option[Either[String, Parameter]],
    options: Options,
    cypherVersion: CypherVersion
  ): (QueryContext, MapValue) => SchemaExecutionResult =
    (ctx, params) => {
      val constraintName = getName(name, params)
      val (maybeIndexProvider, notifications) =
        IndexBackedConstraintsOptionsConverter(s"${relKey.description} constraint", indexContext(ctx))
          .convert(cypherVersion, options, params)
          .toOptionNotification
      val indexProvider = maybeIndexProvider.flatMap(_.provider)
      val relId = ctx.getOrCreateRelTypeId(relType.name)
      val propertyKeyIds = props.map(p => propertyToId(ctx)(p.propertyKey).id)
      ctx.createRelationshipKeyConstraint(relId, propertyKeyIds, constraintName, indexProvider)
      SuccessResult(notifications)
    }

  def createNodePropertyUniquenessConstraint(
    nodePropUnique: NodePropertyUniqueness,
    label: LabelName,
    props: Seq[Property],
    name: Option[Either[String, Parameter]],
    options: Options,
    cypherVersion: CypherVersion
  ): (QueryContext, MapValue) => SchemaExecutionResult =
    (ctx, params) => {
      val constraintName = getName(name, params)
      val (maybeIndexProvider, notifications) =
        IndexBackedConstraintsOptionsConverter(s"${nodePropUnique.description} constraint", indexContext(ctx))
          .convert(cypherVersion, options, params)
          .toOptionNotification
      val indexProvider = maybeIndexProvider.flatMap(_.provider)
      val labelId = ctx.getOrCreateLabelId(label.name)
      val propertyKeyIds = props.map(p => propertyToId(ctx)(p.propertyKey).id)
      ctx.createNodeUniqueConstraint(labelId, propertyKeyIds, constraintName, indexProvider)
      SuccessResult(notifications)
    }

  def createRelationshipPropertyUniquenessConstraint(
    relPropUnique: RelationshipPropertyUniqueness,
    relType: RelTypeName,
    props: Seq[Property],
    name: Option[Either[String, Parameter]],
    options: Options,
    cypherVersion: CypherVersion
  ): (QueryContext, MapValue) => SchemaExecutionResult =
    (ctx, params) => {
      val constraintName = getName(name, params)
      val (maybeIndexProvider, notifications) =
        IndexBackedConstraintsOptionsConverter(s"${relPropUnique.description} constraint", indexContext(ctx))
          .convert(cypherVersion, options, params)
          .toOptionNotification
      val indexProvider = maybeIndexProvider.flatMap(_.provider)
      val relTypeId = ctx.getOrCreateRelTypeId(relType.name)
      val propertyKeyIds = props.map(p => propertyToId(ctx)(p.propertyKey).id)
      ctx.createRelationshipUniqueConstraint(relTypeId, propertyKeyIds, constraintName, indexProvider)
      SuccessResult(notifications)
    }

  def createNodePropertyExistenceConstraint(
    label: LabelName,
    prop: Seq[Property],
    name: Option[Either[String, Parameter]],
    options: Options,
    cypherVersion: CypherVersion
  ): (QueryContext, MapValue) => SchemaExecutionResult =
    (ctx, params) => {
      val constraintName = getName(name, params)
      // Assert empty options
      PropertyExistenceOrTypeConstraintOptionsConverter("node", "existence", indexContext(ctx))
        .convert(cypherVersion, options, params)
      (ctx.createNodePropertyExistenceConstraint(_, _, _, dependent = false))
        .tupled(labelPropWithName(ctx)(label, prop.head.propertyKey, constraintName))
      SuccessResult()
    }

  def createRelationshipPropertyExistenceConstraint(
    relType: RelTypeName,
    prop: Seq[Property],
    name: Option[Either[String, Parameter]],
    options: Options,
    cypherVersion: CypherVersion
  ): (QueryContext, MapValue) => SchemaExecutionResult =
    (ctx, params) => {
      val constraintName = getName(name, params)
      // Assert empty options
      PropertyExistenceOrTypeConstraintOptionsConverter("relationship", "existence", indexContext(ctx))
        .convert(cypherVersion, options, params)
      (ctx.createRelationshipPropertyExistenceConstraint(_, _, _, dependent = false))
        .tupled(typePropWithName(ctx)(relType, prop.head.propertyKey, constraintName))
      SuccessResult()
    }

  def createNodePropertyTypeConstraint(
    propertyType: CypherType,
    label: LabelName,
    prop: Seq[Property],
    name: Option[Either[String, Parameter]],
    options: Options,
    cypherVersion: CypherVersion
  ): (QueryContext, MapValue) => SchemaExecutionResult =
    (ctx, params) => {
      val constraintName = getName(name, params)
      // Assert empty options
      PropertyExistenceOrTypeConstraintOptionsConverter("node", "type", indexContext(ctx))
        .convert(cypherVersion, options, params)
      val (labelId, propId, _) = labelPropWithName(ctx)(label, prop.head.propertyKey, constraintName)
      ctx.createNodePropertyTypeConstraint(
        labelId,
        propId,
        PropertyTypeMapper.asPropertyTypeSet(propertyType),
        constraintName,
        dependent = false
      )
      SuccessResult()
    }

  def createRelationshipPropertyTypeConstraint(
    propertyType: CypherType,
    relType: RelTypeName,
    prop: Seq[Property],
    name: Option[Either[String, Parameter]],
    options: Options,
    cypherVersion: CypherVersion
  ): (QueryContext, MapValue) => SchemaExecutionResult =
    (ctx, params) => {
      val constraintName = getName(name, params)
      // Assert empty options
      PropertyExistenceOrTypeConstraintOptionsConverter("relationship", "type", indexContext(ctx))
        .convert(cypherVersion, options, params)
      val (relTypeId, propId, _) = typePropWithName(ctx)(relType, prop.head.propertyKey, constraintName)
      ctx.createRelationshipPropertyTypeConstraint(
        relTypeId,
        propId,
        PropertyTypeMapper.asPropertyTypeSet(propertyType),
        constraintName,
        dependent = false
      )
      SuccessResult()
    }

  // Drop methods

  def dropConstraint(
    name: Either[String, Parameter],
    ifExists: Boolean
  ): (QueryContext, MapValue) => SchemaExecutionResult =
    (ctx, params) => {
      val constraintName = getName(name, params)
      val notifications: Set[InternalNotification] = if (!ifExists || ctx.constraintExists(constraintName)) {
        ctx.dropNamedConstraint(constraintName, allowDependent = false)
        Set.empty
      } else {
        // Notify on non-existing constraint, replace potential parameter names with their actual value
        Set(IndexOrConstraintDoesNotExistNotification(
          s"DROP CONSTRAINT ${Prettifier.escapeName(Left(constraintName))} IF EXISTS",
          constraintName
        ))
      }
      SuccessResult(notifications)
    }

  // If exists methods

  def doNothingIfExists(
    entityName: ElementTypeName,
    props: Seq[Property],
    assertion: CreateConstraintType,
    name: Option[Either[String, Parameter]],
    options: Options,
    cypherVersion: CypherVersion
  ): (QueryContext, MapValue) => SchemaExecutionResult =
    (ctx, params) => {
      val constraintName = getName(name, params)
      // Assert correct options to get errors even if matching constraint already exists
      val conversionResult = assertion match {
        case nodeKey: NodeKey =>
          IndexBackedConstraintsOptionsConverter(s"${nodeKey.description} constraint", indexContext(ctx))
            .convert(cypherVersion, options, params)
        case relKey: RelationshipKey =>
          IndexBackedConstraintsOptionsConverter(s"${relKey.description} constraint", indexContext(ctx))
            .convert(cypherVersion, options, params)
        case nodePropUnique: NodePropertyUniqueness =>
          IndexBackedConstraintsOptionsConverter(s"${nodePropUnique.description} constraint", indexContext(ctx))
            .convert(cypherVersion, options, params)
        case relPropUnique: RelationshipPropertyUniqueness =>
          IndexBackedConstraintsOptionsConverter(s"${relPropUnique.description} constraint", indexContext(ctx))
            .convert(cypherVersion, options, params)
        case NodePropertyExistence =>
          PropertyExistenceOrTypeConstraintOptionsConverter("node", "existence", indexContext(ctx))
            .convert(cypherVersion, options, params)
        case RelationshipPropertyExistence =>
          PropertyExistenceOrTypeConstraintOptionsConverter("relationship", "existence", indexContext(ctx))
            .convert(cypherVersion, options, params)
        case _: NodePropertyType =>
          PropertyExistenceOrTypeConstraintOptionsConverter("node", "type", indexContext(ctx))
            .convert(cypherVersion, options, params)
        case _: RelationshipPropertyType =>
          PropertyExistenceOrTypeConstraintOptionsConverter("relationship", "type", indexContext(ctx))
            .convert(cypherVersion, options, params)
      }

      val optionConverterNotifications = conversionResult.toOptionNotification._2

      val (entityId, _) = getEntityInfo(entityName, ctx)
      val propertyKeyIds = props.map(p => propertyToId(ctx)(p.propertyKey).id)
      val constraintMatcher = convertConstraintTypeToConstraintMatcher(assertion)
      if (ctx.constraintExists(constraintMatcher, entityId, propertyKeyIds: _*)) {
        // Notify on pre-existing constraint, replace potential parameter names with their actual value
        val constraintDescription = constraintInfo(constraintName, entityName, props, assertion, options)
        val conflictingConstraint = existingConstraintInfo(
          ctx,
          () => ctx.getConstraintInformation(constraintMatcher, entityId, propertyKeyIds: _*),
          cypherVersion
        )

        val notification = IndexOrConstraintAlreadyExistsNotification(
          s"CREATE $constraintDescription",
          conflictingConstraint
        )
        IgnoredResult(Set(notification) ++ optionConverterNotifications)
      } else if (constraintName.exists(ctx.constraintExists)) {
        // Notify on pre-existing constraint, replace potential parameter names with their actual value
        val constraintDescription = constraintInfo(constraintName, entityName, props, assertion, options)
        val conflictingConstraint = existingConstraintInfo(
          ctx,
          () => ctx.getConstraintInformation(constraintName.get),
          cypherVersion
        )

        val notification = IndexOrConstraintAlreadyExistsNotification(
          s"CREATE $constraintDescription",
          conflictingConstraint
        )
        IgnoredResult(Set(notification) ++ optionConverterNotifications)
      } else {
        SuccessResult(optionConverterNotifications)
      }
    }
}
