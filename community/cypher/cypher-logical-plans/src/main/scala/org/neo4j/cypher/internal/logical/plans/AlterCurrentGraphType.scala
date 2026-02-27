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
import org.neo4j.cypher.internal.util.attribution.IdGen
import org.neo4j.exceptions.InvalidArgumentException

// Graph types

case class AlterCurrentGraphType(graphType: GraphType)(implicit idGen: IdGen) extends SchemaLogicalPlan(idGen)

object AlterCurrentGraphType {

  // All of these help methods assume the graph type is valid (excluding the checks based on the existing graph type)
  // and normalized, so they will throw away parts of the ast they no longer care about based on that.

  def apply(graphType: ast.GraphType, operation: ast.AlterCurrentGraphType.AlterOperation)(implicit
    idGen: IdGen): AlterCurrentGraphType = {
    operation match {
      case ast.AlterCurrentGraphType.Set   => AlterCurrentGraphType(getGraphTypeForSet(graphType))
      case ast.AlterCurrentGraphType.Add   => AlterCurrentGraphType(getGraphTypeForAdd(graphType))
      case ast.AlterCurrentGraphType.Alter => AlterCurrentGraphType(getGraphTypeForAlter(graphType))
      case ast.AlterCurrentGraphType.Drop  => AlterCurrentGraphType(getGraphTypeForDrop(graphType))
    }
  }

  private def getGraphTypeForSet(graphType: ast.GraphType) = GraphTypeForSet(
    getPlanElementTypes(graphType.types),
    getPlanCreateConstraints(graphType.constraints, "SET")
  )

  private def getGraphTypeForAdd(graphType: ast.GraphType) = GraphTypeForAdd(
    getPlanElementTypes(graphType.types),
    getPlanCreateConstraints(graphType.constraints, "ADD")
  )

  private def getGraphTypeForAlter(graphType: ast.GraphType) =
    GraphTypeForAlter(getPlanElementTypes(graphType.types))

  private def getGraphTypeForDrop(graphType: ast.GraphType) = GraphTypeForDrop(
    getPlanElementTypes(graphType.types),
    getPlanDropConstraints(graphType.constraints)
  )

  private def getPlanElementTypes(elementTypes: Set[ast.GraphTypeEntry]): Set[GraphTypeEntry] = {
    elementTypes.map {
      case net: ast.NodeType =>
        NodeElementType(net.identifyingLabel, net.additionalLabels, getPlanPropertyType(net.propertyTypes))
      case ret: ast.EdgeType => RelationshipElementType(
          ret.identifyingLabel,
          getPlanNodeReference(ret.src),
          getPlanNodeReference(ret.dest),
          getPlanPropertyType(ret.propertyTypes)
        )
    }
  }

  private def getPlanPropertyType(propertyTypes: Set[ast.PropertyType]): Set[PropertyType] =
    propertyTypes.map(pt => PropertyType(pt.name, pt.normalizedPropertyType))

  private def getPlanNodeReference(
    nodeTypeRef: ast.NodeTypeReference
  ): NodeElementTypeReferenceForRelationshipElementType = nodeTypeRef match {
    case _: ast.EmptyNodeTypeReference                => EmptyNodeElementTypeReference
    case ntr: ast.NodeTypeReferenceByLabel            => NodeElementTypeReferenceByLabel(ntr.labelName)
    case ntr: ast.NodeTypeReferenceByIdentifyingLabel => NodeElementTypeReferenceByIdentifyingLabel(ntr.labelName)
    case _: ast.NodeTypeReferenceByVariable => throw InvalidArgumentException.internalError(
        "invalid node element type reference",
        "did not expect node element type variable references here, it should already have been resolved."
      )
  }

  private def getPlanCreateConstraints(
    graphTypeConstraints: Set[ast.GraphTypeConstraint],
    operation: String
  ): Set[GraphTypeCreateConstraint] =
    graphTypeConstraints.map {
      case gtc: ast.GraphTypeConstraintDefinition => GraphTypeCreateConstraint(
          gtc.name,
          getPlanConstraintReference(gtc.reference),
          gtc.body.properties.map(_.propertyKey),
          getPlanConstraintType(gtc.body),
          gtc.options
        )
      case _: ast.GraphTypeConstraintName => throw InvalidArgumentException.internalError(
          "invalid constraint definition",
          s"Did not expect name only constraint definitions for $operation."
        )
    }

  private def getPlanConstraintReference(
    ref: ast.GraphTypeElementReference
  ): GraphElementTypeReferenceForConstraint = ref match {
    case ntr: ast.NodeTypeReferenceByLabel            => NodeElementTypeReferenceByLabel(ntr.labelName)
    case ntr: ast.NodeTypeReferenceByIdentifyingLabel => NodeElementTypeReferenceByIdentifyingLabel(ntr.labelName)
    case etr: ast.EdgeTypeReferenceByLabel            => RelationshipElementTypeReferenceByLabel(etr.relTypeName)
    case etr: ast.EdgeTypeReferenceByIdentifyingLabel =>
      RelationshipElementTypeReferenceByIdentifyingLabel(etr.relTypeName)
    case _: ast.EmptyNodeTypeReference => throw InvalidArgumentException.internalError(
        "invalid element type reference",
        "Did not expect empty node element type references here, it is not a valid target for a constraint."
      )
    case _: ast.NodeTypeReferenceByVariable => throw InvalidArgumentException.internalError(
        "invalid element type reference",
        "Did not expect node element type variable references here, it should already have been resolved."
      )
    case _: ast.EdgeTypeReferenceByVariable => throw InvalidArgumentException.internalError(
        "invalid element type reference",
        "Did not expect relationship element type variable references here, it should already have been resolved."
      )
  }

  private def getPlanConstraintType(
    constraintBody: ast.GraphTypeConstraint.GraphTypeConstraintBody
  ): GraphTypeConstraintType =
    constraintBody match {
      case _: ast.GraphTypeConstraint.ExistenceConstraint      => ExistenceConstraint
      case gtc: ast.GraphTypeConstraint.PropertyTypeConstraint => PropertyTypeConstraint(gtc.normalizedPropertyType)
      case _: ast.GraphTypeConstraint.KeyConstraint            => KeyConstraint
      case _: ast.GraphTypeConstraint.UniquenessConstraint     => UniquenessConstraint
    }

  private def getPlanDropConstraints(
    graphTypeConstraints: Set[ast.GraphTypeConstraint]
  ): Set[GraphTypeDropConstraint] =
    graphTypeConstraints.map {
      case gtc: ast.GraphTypeConstraintName => GraphTypeDropConstraint(gtc.name)
      case _: ast.GraphTypeConstraintDefinition => throw InvalidArgumentException.internalError(
          "invalid constraint definition",
          "Did not expect constraint specification definitions for DROP."
        )
    }
}
