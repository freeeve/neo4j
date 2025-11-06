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
package org.neo4j.cypher.internal.compiler

import org.neo4j.common.EntityType
import org.neo4j.cypher.internal.ast.AlterCurrentGraphType
import org.neo4j.cypher.internal.ast.CreateConstraint
import org.neo4j.cypher.internal.ast.CreateFulltextIndex
import org.neo4j.cypher.internal.ast.CreateLookupIndex
import org.neo4j.cypher.internal.ast.CreateSingleLabelPropertyIndex
import org.neo4j.cypher.internal.ast.CreateVectorIndex
import org.neo4j.cypher.internal.ast.DropConstraintOnName
import org.neo4j.cypher.internal.ast.DropIndexOnName
import org.neo4j.cypher.internal.ast.EdgeType
import org.neo4j.cypher.internal.ast.EdgeTypeReferenceByIdentifyingLabel
import org.neo4j.cypher.internal.ast.EdgeTypeReferenceByLabel
import org.neo4j.cypher.internal.ast.EdgeTypeReferenceByVariable
import org.neo4j.cypher.internal.ast.EmptyNodeTypeReference
import org.neo4j.cypher.internal.ast.GraphType
import org.neo4j.cypher.internal.ast.GraphTypeConstraint
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.GraphTypeConstraintBody
import org.neo4j.cypher.internal.ast.GraphTypeConstraintDefinition
import org.neo4j.cypher.internal.ast.GraphTypeConstraintName
import org.neo4j.cypher.internal.ast.GraphTypeElementReference
import org.neo4j.cypher.internal.ast.GraphTypeEntry
import org.neo4j.cypher.internal.ast.IfExistsDoNothing
import org.neo4j.cypher.internal.ast.NodeType
import org.neo4j.cypher.internal.ast.NodeTypeReference
import org.neo4j.cypher.internal.ast.NodeTypeReferenceByIdentifyingLabel
import org.neo4j.cypher.internal.ast.NodeTypeReferenceByLabel
import org.neo4j.cypher.internal.ast.NodeTypeReferenceByVariable
import org.neo4j.cypher.internal.ast.PointCreateIndex
import org.neo4j.cypher.internal.ast.PropertyType
import org.neo4j.cypher.internal.ast.RangeCreateIndex
import org.neo4j.cypher.internal.ast.TextCreateIndex
import org.neo4j.cypher.internal.compiler.phases.LogicalPlanState
import org.neo4j.cypher.internal.compiler.phases.PlannerContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase.PIPE_BUILDING
import org.neo4j.cypher.internal.frontend.phases.Phase
import org.neo4j.cypher.internal.logical.plans
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.planner.spi.AdministrationPlannerName
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.attribution.SequentialIdGen
import org.neo4j.exceptions.InternalException
import org.neo4j.graphdb.schema.IndexType
import org.neo4j.kernel.api.exceptions.InvalidArgumentsException

/**
 * This planner takes on queries that requires no planning such as schema commands
 */
case object SchemaCommandPlanBuilder extends Phase[PlannerContext, BaseState, LogicalPlanState] {

  override def phase: CompilationPhase = PIPE_BUILDING

  override def postConditions: Set[StepSequencer.Condition] = Set.empty

  override def process(from: BaseState, context: PlannerContext): LogicalPlanState = {
    implicit val idGen: SequentialIdGen = new SequentialIdGen()

    val maybeLogicalPlan: Option[LogicalPlan] = from.statement() match {
      // CREATE CONSTRAINT ... IS KEY
      // CREATE CONSTRAINT ... IS UNIQUE
      // CREATE CONSTRAINT ... IS NOT NULL
      // CREATE CONSTRAINT ... IS :: ...
      case CreateConstraint(_, entityName, props, name, constraintType, ifExistsDo, options) =>
        val source = ifExistsDo match {
          case IfExistsDoNothing => Some(plans.DoNothingIfExistsForConstraint(
              entityName,
              props,
              constraintType,
              name,
              options
            ))
          case _ => None
        }
        Some(plans.CreateConstraint(source, constraintType, entityName, props, name, options))

      // DROP CONSTRAINT name [IF EXISTS]
      case DropConstraintOnName(name, ifExists, _) =>
        Some(plans.DropConstraintOnName(name, ifExists))

      // CREATE [POINT| RANGE | TEXT] INDEX ...
      case CreateSingleLabelPropertyIndex(_, entityName, props, name, astIndexType, ifExistsDo, options) =>
        val indexType = astIndexType match {
          case PointCreateIndex    => IndexType.POINT
          case _: RangeCreateIndex => IndexType.RANGE
          case TextCreateIndex     => IndexType.TEXT
          case it =>
            throw InternalException.internalError(
              this.getClass.getSimpleName,
              s"Unexpected index type, expected point, range or text. Got: $it.",
              s"Did not expect index type ${it.command} here: only point, range or text indexes."
            )
        }
        val propKeys = props.map(_.propertyKey)
        val source = ifExistsDo match {
          case IfExistsDoNothing =>
            Some(plans.DoNothingIfExistsForIndex(entityName, propKeys, indexType, name, options))
          case _ => None
        }
        Some(plans.CreateIndex(source, indexType, entityName, propKeys, name, options))

      // CREATE LOOKUP INDEX ...
      case CreateLookupIndex(_, isNodeIndex, _, name, _, ifExistsDo, options) =>
        val entityType = if (isNodeIndex) EntityType.NODE else EntityType.RELATIONSHIP
        val source = ifExistsDo match {
          case IfExistsDoNothing =>
            Some(plans.DoNothingIfExistsForLookupIndex(entityType, name, options))
          case _ => None
        }
        Some(plans.CreateLookupIndex(source, entityType, name, options))

      // CREATE FULLTEXT INDEX ...
      case CreateFulltextIndex(_, entityNames, props, name, _, ifExistsDo, options) =>
        val propKeys = props.map(_.propertyKey)
        val source = ifExistsDo match {
          case IfExistsDoNothing =>
            Some(plans.DoNothingIfExistsForFulltextIndex(entityNames, propKeys, name, options))
          case _ => None
        }
        Some(plans.CreateFulltextIndex(source, entityNames, propKeys, name, options))

      // CREATE VECTOR INDEX ...
      case CreateVectorIndex(_, entityNames, props, additionalProps, name, _, ifExistsDo, options) =>
        val propKeys = props.map(_.propertyKey)
        val additionalPropsKeys = additionalProps.map(_.propertyKey)
        val source = ifExistsDo match {
          case IfExistsDoNothing =>
            Some(plans.DoNothingIfExistsForVectorIndex(entityNames, propKeys, additionalPropsKeys, name, options))
          case _ => None
        }
        Some(plans.CreateVectorIndex(source, entityNames, propKeys, additionalPropsKeys, name, options))

      // DROP INDEX name [IF EXISTS]
      case DropIndexOnName(name, ifExists, _) =>
        Some(plans.DropIndexOnName(name, ifExists))

      // ALTER CURRENT GRAPH TYPE SET {...}
      // ALTER CURRENT GRAPH TYPE ADD {...}
      // ALTER CURRENT GRAPH TYPE ALTER {...}
      // ALTER CURRENT GRAPH TYPE DROP {...}
      case AlterCurrentGraphType(graphType, operation, _) => operation match {
          case AlterCurrentGraphType.Set   => Some(plans.AlterCurrentGraphType(getGraphTypeForSet(graphType)))
          case AlterCurrentGraphType.Add   => Some(plans.AlterCurrentGraphType(getGraphTypeForAdd(graphType)))
          case AlterCurrentGraphType.Alter => Some(plans.AlterCurrentGraphType(getGraphTypeForAlter(graphType)))
          case AlterCurrentGraphType.Drop  => Some(plans.AlterCurrentGraphType(getGraphTypeForDrop(graphType)))
        }

      case _ => None
    }

    val planState = LogicalPlanState(from)

    if (maybeLogicalPlan.isDefined)
      planState.copy(maybeLogicalPlan = maybeLogicalPlan, plannerName = AdministrationPlannerName)
    else planState
  }

  // All of these help methods assume the graph type is valid (excluding the checks based on the existing graph type)
  // and normalized, so they will throw away parts of the ast they no longer care about based on that.

  private def getGraphTypeForSet(graphType: GraphType) = plans.GraphTypeForSet(
    getPlanElementTypes(graphType.types),
    getPlanCreateConstraints(graphType.constraints, "SET")
  )

  private def getGraphTypeForAdd(graphType: GraphType) = plans.GraphTypeForAdd(
    getPlanElementTypes(graphType.types),
    getPlanCreateConstraints(graphType.constraints, "ADD")
  )

  private def getGraphTypeForAlter(graphType: GraphType) =
    plans.GraphTypeForAlter(getPlanElementTypes(graphType.types))

  private def getGraphTypeForDrop(graphType: GraphType) = plans.GraphTypeForDrop(
    getPlanElementTypes(graphType.types),
    getPlanDropConstraints(graphType.constraints)
  )

  private def getPlanElementTypes(elementTypes: Set[GraphTypeEntry]): Set[plans.GraphTypeEntry] = {
    elementTypes.map {
      case net: NodeType =>
        plans.NodeElementType(net.identifyingLabel, net.additionalLabels, getPlanPropertyType(net.propertyTypes))
      case ret: EdgeType => plans.RelationshipElementType(
          ret.identifyingLabel,
          getPlanNodeReference(ret.src),
          getPlanNodeReference(ret.dest),
          getPlanPropertyType(ret.propertyTypes)
        )
    }
  }

  private def getPlanPropertyType(propertyTypes: Set[PropertyType]): Set[plans.PropertyType] =
    propertyTypes.map(pt => plans.PropertyType(pt.name, pt.normalizedPropertyType))

  private def getPlanNodeReference(
    nodeTypeRef: NodeTypeReference
  ): plans.NodeElementTypeReferenceForRelationshipElementType = nodeTypeRef match {
    case _: EmptyNodeTypeReference                => plans.EmptyNodeElementTypeReference
    case ntr: NodeTypeReferenceByLabel            => plans.NodeElementTypeReferenceByLabel(ntr.labelName)
    case ntr: NodeTypeReferenceByIdentifyingLabel => plans.NodeElementTypeReferenceByIdentifyingLabel(ntr.labelName)
    case _: NodeTypeReferenceByVariable => throw InvalidArgumentsException.internalError(
        "invalid node element type reference",
        "did not expect node element type variable references here, it should already have been resolved."
      )
  }

  private def getPlanCreateConstraints(
    graphTypeConstraints: Set[GraphTypeConstraint],
    operation: String
  ): Set[plans.GraphTypeCreateConstraint] =
    graphTypeConstraints.map {
      case gtc: GraphTypeConstraintDefinition => plans.GraphTypeCreateConstraint(
          gtc.name,
          getPlanConstraintReference(gtc.reference),
          gtc.body.properties.map(_.propertyKey),
          getPlanConstraintType(gtc.body),
          gtc.options
        )
      case _: GraphTypeConstraintName => throw InvalidArgumentsException.internalError(
          "invalid constraint definition",
          s"Did not expect name only constraint definitions for $operation."
        )
    }

  private def getPlanConstraintReference(
    ref: GraphTypeElementReference
  ): plans.GraphElementTypeReferenceForConstraint = ref match {
    case ntr: NodeTypeReferenceByLabel            => plans.NodeElementTypeReferenceByLabel(ntr.labelName)
    case ntr: NodeTypeReferenceByIdentifyingLabel => plans.NodeElementTypeReferenceByIdentifyingLabel(ntr.labelName)
    case etr: EdgeTypeReferenceByLabel            => plans.RelationshipElementTypeReferenceByLabel(etr.relTypeName)
    case etr: EdgeTypeReferenceByIdentifyingLabel =>
      plans.RelationshipElementTypeReferenceByIdentifyingLabel(etr.relTypeName)
    case _: EmptyNodeTypeReference => throw InvalidArgumentsException.internalError(
        "invalid element type reference",
        "Did not expect empty node element type references here, it is not a valid target for a constraint."
      )
    case _: NodeTypeReferenceByVariable => throw InvalidArgumentsException.internalError(
        "invalid element type reference",
        "Did not expect node element type variable references here, it should already have been resolved."
      )
    case _: EdgeTypeReferenceByVariable => throw InvalidArgumentsException.internalError(
        "invalid element type reference",
        "Did not expect relationship element type variable references here, it should already have been resolved."
      )
  }

  private def getPlanConstraintType(constraintBody: GraphTypeConstraintBody): plans.GraphTypeConstraintType =
    constraintBody match {
      case _: GraphTypeConstraint.ExistenceConstraint      => plans.ExistenceConstraint
      case gtc: GraphTypeConstraint.PropertyTypeConstraint => plans.PropertyTypeConstraint(gtc.normalizedPropertyType)
      case _: GraphTypeConstraint.KeyConstraint            => plans.KeyConstraint
      case _: GraphTypeConstraint.UniquenessConstraint     => plans.UniquenessConstraint
    }

  private def getPlanDropConstraints(
    graphTypeConstraints: Set[GraphTypeConstraint]
  ): Set[plans.GraphTypeDropConstraint] =
    graphTypeConstraints.map {
      case gtc: GraphTypeConstraintName => plans.GraphTypeDropConstraint(gtc.name)
      case _: GraphTypeConstraintDefinition => throw InvalidArgumentsException.internalError(
          "invalid constraint definition",
          "Did not expect constraint specification definitions for DROP."
        )
    }
}
