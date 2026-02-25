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
package org.neo4j.cypher.internal.logical.plans

import org.neo4j.cypher.internal.ast
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
import org.neo4j.cypher.internal.ast.NodeType
import org.neo4j.cypher.internal.ast.NodeTypeReference
import org.neo4j.cypher.internal.ast.NodeTypeReferenceByIdentifyingLabel
import org.neo4j.cypher.internal.ast.NodeTypeReferenceByLabel
import org.neo4j.cypher.internal.ast.NodeTypeReferenceByVariable
import org.neo4j.cypher.internal.ast.PropertyType
import org.neo4j.cypher.internal.logical.plans
import org.neo4j.cypher.internal.util.attribution.IdGen
import org.neo4j.exceptions.InvalidArgumentException

// Graph types

case class AlterCurrentGraphType(graphType: plans.GraphType)(implicit idGen: IdGen) extends SchemaLogicalPlan(idGen)

object AlterCurrentGraphType {

  // All of these help methods assume the graph type is valid (excluding the checks based on the existing graph type)
  // and normalized, so they will throw away parts of the ast they no longer care about based on that.

  def apply(graphType: GraphType, operation: ast.AlterCurrentGraphType.AlterOperation)(implicit
    idGen: IdGen): plans.AlterCurrentGraphType = {
    operation match {
      case ast.AlterCurrentGraphType.Set   => AlterCurrentGraphType(getGraphTypeForSet(graphType))
      case ast.AlterCurrentGraphType.Add   => AlterCurrentGraphType(getGraphTypeForAdd(graphType))
      case ast.AlterCurrentGraphType.Alter => AlterCurrentGraphType(getGraphTypeForAlter(graphType))
      case ast.AlterCurrentGraphType.Drop  => AlterCurrentGraphType(getGraphTypeForDrop(graphType))
    }
  }

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
    case _: NodeTypeReferenceByVariable => throw InvalidArgumentException.internalError(
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
      case _: GraphTypeConstraintName => throw InvalidArgumentException.internalError(
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
    case _: EmptyNodeTypeReference => throw InvalidArgumentException.internalError(
        "invalid element type reference",
        "Did not expect empty node element type references here, it is not a valid target for a constraint."
      )
    case _: NodeTypeReferenceByVariable => throw InvalidArgumentException.internalError(
        "invalid element type reference",
        "Did not expect node element type variable references here, it should already have been resolved."
      )
    case _: EdgeTypeReferenceByVariable => throw InvalidArgumentException.internalError(
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
      case _: GraphTypeConstraintDefinition => throw InvalidArgumentException.internalError(
          "invalid constraint definition",
          "Did not expect constraint specification definitions for DROP."
        )
    }
}
