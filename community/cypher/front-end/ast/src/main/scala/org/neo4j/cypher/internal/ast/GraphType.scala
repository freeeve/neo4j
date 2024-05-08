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

import org.neo4j.cypher.internal.ast.GraphTypeConstraint.ExistenceConstraint
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.GraphTypeConstraintBody
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.PropertyTypeConstraint
import org.neo4j.cypher.internal.ast.PropertyType.PropertyInlineConstraintBody
import org.neo4j.cypher.internal.ast.semantics.CypherTypeChecking
import org.neo4j.cypher.internal.ast.semantics.SemanticAnalysisTooling
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck.error
import org.neo4j.cypher.internal.ast.semantics.SemanticCheck.success
import org.neo4j.cypher.internal.ast.semantics.SemanticCheckable
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.expressions.ElementTypeName
import org.neo4j.cypher.internal.expressions.LabelName
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertyKeyName
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.CypherType

import scala.collection.immutable.ArraySeq
import scala.collection.mutable

case class GraphType(types: Set[GraphTypeEntry], constraints: Set[GraphTypeConstraint])(val position: InputPosition)
    extends ASTNode with SemanticCheckable
    with SemanticAnalysisTooling {

  private val nodesByVar: Map[Variable, NodeType] = types.collect {
    case n @ NodeType(Some(v), _, _, _, _) => (v, n)
  }.toMap

  private val edgesByVar: Map[Variable, EdgeType] = types.collect {
    case e @ EdgeType(_, Some(v), _, _, _, _) => (v, e)
  }.toMap

  private val nodesByLabel: Map[LabelName, NodeType] = types.collect {
    case n @ NodeType(_, l, _, _, _) => (l, n)
  }.toMap

  private val edgesByType: Map[RelTypeName, EdgeType] = types.collect {
    case e @ EdgeType(_, _, rt, _, _, _) => (rt, e)
  }.toMap

  def resolveEndpoint(nodeTypeReference: NodeTypeReference): Option[NodeTypeReference] = nodeTypeReference match {
    case NodeTypeReferenceByVariable(variable) =>
      nodesByVar.get(variable).map(nt =>
        NodeTypeReferenceByIdentifyingLabel(nt.identifyingLabel, Some(variable))(nt.position)
      )
    case ntrl @ NodeTypeReferenceByLabel(label, typeRef) =>
      nodesByLabel.get(label).map(node =>
        Some(
          NodeTypeReferenceByIdentifyingLabel(node.identifyingLabel, typeRef.orElse(node.variable))(node.position)
        )
      ).getOrElse(Some(ntrl))
    case e: EmptyNodeTypeReference                       => Some(EmptyNodeTypeReference()(e.position))
    case n @ NodeTypeReferenceByIdentifyingLabel(lab, _) => if (nodesByLabel.contains(lab)) Some(n) else None
  }

  def resolveEndpoint(edgeTypeReference: EdgeTypeReference): Option[EdgeTypeReference] = edgeTypeReference match {
    case EdgeTypeReferenceByVariable(variable) =>
      edgesByVar.get(variable).map(et =>
        EdgeTypeReferenceByIdentifyingLabel(et.identifyingLabel, Some(variable))(et.position)
      )
    case e @ EdgeTypeReferenceByIdentifyingLabel(rtn, _) => if (edgesByType.contains(rtn)) Some(e) else None
    case e @ EdgeTypeReferenceByLabel(relType, typeRef) =>
      edgesByType.get(relType).map(rel =>
        Some(
          EdgeTypeReferenceByIdentifyingLabel(rel.identifyingLabel, typeRef.orElse(rel.variable))(rel.position)
        )
      ).getOrElse(Some(e))
  }

  /* Implied labels should not contain any identifying labels */
  private def checkImpliedLabels(elem: GraphTypeEntry): SemanticCheck = {
    elem match {
      case NodeType(_, _, impliedLabels, _, _) if impliedLabels.nonEmpty =>
        val clashingLabels: Set[LabelName] = impliedLabels.intersect(nodesByLabel.keySet)
        if (clashingLabels.isEmpty) success
        else error("Label clash", clashingLabels.head.position)
      case _ => success
    }
  }

  /* Variables and identifiers should be used only once */
  private def checkIdentifiers(
    elem: GraphTypeEntry,
    identifiers: mutable.Set[ElementTypeName],
    variables: mutable.Set[Variable]
  ): SemanticCheck = {
    elem match {
      case NodeType(v, l, _, _, _) =>
        (if (v.isDefined && !variables.add(v.get))
           error(s"Reference `${v.get.name}` is already declared", v.get.position)
         else success) chain
          (if (!identifiers.add(l)) error(s"Label :`${l.name}` is already declared", l.position) else success)
      case EdgeType(_, v, rt, _, _, _) =>
        (if (v.isDefined && !variables.add(v.get))
           error(s"Reference `${v.get.name}` is already declared", v.get.position)
         else success) chain
          (if (!identifiers.add(rt)) error(s"Relationship type :`${rt.name}` is already declared", rt.position)
           else success)
      case _ => success
    }
  }

  private def checkElements(): SemanticCheck = {
    val identifiers = mutable.Set[ElementTypeName]()
    val variables = mutable.Set[Variable]()
    semanticCheckFold(types) { t => checkIdentifiers(t, identifiers, variables) chain checkImpliedLabels(t) }
  }

  private def checkConstraints(): SemanticCheck = {
    val uniqueConstraintNames = mutable.Set[String]()

    /* Constraint names should be unique */
    def checkForClashingNames(constraint: GraphTypeConstraint): SemanticCheck = constraint match {
      case c: GraphTypeConstraintDefinition =>
        if (c.name.isEmpty || uniqueConstraintNames.add(c.name.get)) success
        else error("Constraint names must be unique within a graph type", c.position)
      case c @ GraphTypeConstraintName(name) =>
        if (uniqueConstraintNames.add(name)) success
        else error("Constraint names must be unique within a graph type", c.position)
    }

    /* We should not have independent constraints on define node / edge types which should be dependent */
    def checkForIndependentConstraintsOnDependentLabels(c: GraphTypeConstraint): SemanticCheck = {
      val errorMsg = "Independent constraints cannot be defined on dependent labels"
      c match {
        case GraphTypeConstraintDefinition(_, n: NodeTypeReference, p: PropertyTypeConstraint, _) =>
          resolveEndpoint(n) match {
            case Some(_: NodeTypeReferenceByIdentifyingLabel) =>
              error(errorMsg, p.position)
            case _ => success
          }
        case GraphTypeConstraintDefinition(_, n: NodeTypeReference, ex: ExistenceConstraint, _) =>
          resolveEndpoint(n) match {
            case Some(_: NodeTypeReferenceByIdentifyingLabel) =>
              error(errorMsg, ex.position)
            case _ => success
          }
        case GraphTypeConstraintDefinition(_, e: EdgeTypeReference, p: PropertyTypeConstraint, _) =>
          resolveEndpoint(e) match {
            case Some(_: EdgeTypeReferenceByIdentifyingLabel) =>
              error(errorMsg, p.position)
            case _ => success
          }
        case GraphTypeConstraintDefinition(_, e: EdgeTypeReference, ex: ExistenceConstraint, _) =>
          resolveEndpoint(e) match {
            case Some(_: EdgeTypeReferenceByIdentifyingLabel) =>
              error(errorMsg, ex.position)
            case _ => success
          }
        case _ => success
      }
    }

    semanticCheckFold(constraints)(c =>
      checkForClashingNames(c) chain
        checkForIndependentConstraintsOnDependentLabels(c)
    )
  }

  override def semanticCheck: SemanticCheck =
    semanticCheck(types) chain semanticCheck(constraints) chain checkElements chain checkConstraints
}

sealed trait GraphTypeEntry extends ASTNode with SemanticCheckable

object GraphTypeEntry extends SemanticAnalysisTooling {

  /* Check property types are valid, and check for duplicate keys */
  def checkProperties(props: Set[PropertyType]): SemanticCheck = {
    val keys = mutable.Set[PropertyKeyName]()
    semanticCheckFold(props) { prop =>
      CypherTypeChecking.checkPropertyTypeForGraphType(
        prop.propertyType,
        prop.normalizedPropertyType,
        SemanticError.propertyTypeUnsupportedInConstraint("graph type", _, _, _)
      ) chain {
        if (!keys.add(prop.name)) error(s"Duplicate property key `${prop.name.name}`", prop.position) else success
      }
    }
  }

  def checkOptionsMaps(constraints: Set[(GraphTypeConstraintBody, Options)]): SemanticCheck =
    semanticCheckFold(constraints) {
      case (_, options) => options.checkOptionsForSchema("")
    }
}

// Node
case class NodeType(
  variable: Option[Variable],
  identifyingLabel: LabelName,
  additionalLabels: Set[LabelName],
  propertyTypes: Set[PropertyType],
  constraints: Set[(GraphTypeConstraintBody, Options)]
)(val position: InputPosition) extends GraphTypeEntry {

  def checkNotEmpty: SemanticCheck = if (propertyTypes.isEmpty && additionalLabels.isEmpty)
    error(SemanticError("Can't be empty", position))
  else success

  override def semanticCheck: SemanticCheck =
    GraphTypeEntry.checkProperties(propertyTypes) chain checkNotEmpty chain GraphTypeEntry.checkOptionsMaps(constraints)
}

// Edge
case class EdgeType(
  src: NodeTypeReference,
  variable: Option[Variable],
  identifyingLabel: RelTypeName,
  propertyTypes: Set[PropertyType],
  dest: NodeTypeReference,
  constraints: Set[(GraphTypeConstraintBody, Options)]
)(val position: InputPosition)
    extends GraphTypeEntry {

  def checkNotEmpty: SemanticCheck =
    if (
      propertyTypes.isEmpty && src.isInstanceOf[EmptyNodeTypeReference] && dest.isInstanceOf[EmptyNodeTypeReference]
    ) {
      error(SemanticError("Can't be empty", position))
    } else success

  override def semanticCheck: SemanticCheck =
    GraphTypeEntry.checkProperties(propertyTypes) chain checkNotEmpty chain GraphTypeEntry.checkOptionsMaps(constraints)
}

// Property
case class PropertyType(
  name: PropertyKeyName,
  propertyType: CypherType,
  constraint: Option[PropertyInlineConstraintBody]
)(
  val position: InputPosition
) extends ASTNode {
  val normalizedPropertyType: CypherType = CypherType.normalizeTypes(propertyType)
}

object PropertyType {
  sealed trait PropertyInlineConstraintBody extends ASTNode

  case class PropertyInlineKeyConstraint()(val position: InputPosition)
      extends PropertyInlineConstraintBody {}

  case class PropertyInlineUniquenessConstraint()(val position: InputPosition)
      extends PropertyInlineConstraintBody {}
}

// Node / Edge references

sealed trait GraphTypeElementReference extends ASTNode

sealed trait NodeTypeReference extends GraphTypeElementReference

case class EmptyNodeTypeReference()(val position: InputPosition) extends NodeTypeReference

case class NodeTypeReferenceByVariable(typeReference: Variable)(val position: InputPosition) extends NodeTypeReference

case class NodeTypeReferenceByLabel(labelName: LabelName, typeReference: Option[Variable] = None)(
  val position: InputPosition
) extends NodeTypeReference

case class NodeTypeReferenceByIdentifyingLabel(labelName: LabelName, typeReference: Option[Variable] = None)(
  val position: InputPosition
) extends NodeTypeReference

sealed trait EdgeTypeReference extends GraphTypeElementReference

case class EdgeTypeReferenceByVariable(typeReference: Variable)(val position: InputPosition) extends EdgeTypeReference

case class EdgeTypeReferenceByLabel(relTypeName: RelTypeName, typeReference: Option[Variable] = None)(
  val position: InputPosition
) extends EdgeTypeReference

case class EdgeTypeReferenceByIdentifyingLabel(relTypeName: RelTypeName, typeReference: Option[Variable] = None)(
  val position: InputPosition
) extends EdgeTypeReference

// Constraints
sealed trait GraphTypeConstraint extends ASTNode with SemanticCheckable {}

case class GraphTypeConstraintName(name: String)(val position: InputPosition)
    extends GraphTypeConstraint {
  override def semanticCheck: SemanticCheck = success
}

case class GraphTypeConstraintDefinition(
  name: Option[String],
  reference: GraphTypeElementReference,
  body: GraphTypeConstraintBody,
  options: Options
)(val position: InputPosition)
    extends GraphTypeConstraint with SemanticAnalysisTooling {

  val key: GraphTypeConstraintKey = GraphTypeConstraintKey(this)

  override def semanticCheck: SemanticCheck = body.semanticCheck chain {
    def checkPropertyVariablesInScope(scope: Set[Variable], props: Seq[Property]): SemanticCheck =
      semanticCheckFold(props) {
        case p @ Property(Variable(name), _) if !scope.exists(_.name == name) =>
          error(s"Graph type element referenced by `$name` not found.", p.position)
        case _ => success
      }

    reference match {
      case NodeTypeReferenceByVariable(v)            => checkPropertyVariablesInScope(Set(v), body.properties)
      case NodeTypeReferenceByLabel(_, v)            => checkPropertyVariablesInScope(v.toSet, body.properties)
      case NodeTypeReferenceByIdentifyingLabel(_, v) => checkPropertyVariablesInScope(v.toSet, body.properties)
      case EdgeTypeReferenceByVariable(v)            => checkPropertyVariablesInScope(Set(v), body.properties)
      case EdgeTypeReferenceByLabel(_, v)            => checkPropertyVariablesInScope(v.toSet, body.properties)
      case EdgeTypeReferenceByIdentifyingLabel(_, v) => checkPropertyVariablesInScope(v.toSet, body.properties)
      case EmptyNodeTypeReference() =>
        error("The empty node type is not a valid target for a constraint", reference.position)
    }
  } chain options.checkOptionsForSchema("")
}

object GraphTypeConstraint {

  sealed trait GraphTypeConstraintBody extends ASTNode with SemanticCheckable with SemanticAnalysisTooling {

    val properties: ArraySeq[Property]

    // Check for duplicate property keys in constraint
    def checkProperties(properties: Seq[PropertyKeyName]): SemanticCheck = {
      val props = mutable.Set[PropertyKeyName]()
      semanticCheckFold(properties) { p =>
        if (props.add(p)) success else error(s"Duplicate property key `${p.name}`", p.position)
      }
    }

    def withProperties(newProperties: ArraySeq[Property]): GraphTypeConstraintBody
  }

  case class ExistenceConstraint(override val properties: ArraySeq[Property])(val position: InputPosition)
      extends GraphTypeConstraintBody {
    override def semanticCheck: SemanticCheck = checkProperties(properties.map(_.propertyKey))

    override def withProperties(newProperties: ArraySeq[Property]): GraphTypeConstraintBody =
      this.copy(properties = newProperties)(position)
  }

  case class PropertyTypeConstraint(override val properties: ArraySeq[Property], propertyType: CypherType)(
    val position: InputPosition
  ) extends GraphTypeConstraintBody {
    val normalizedPropertyType: CypherType = CypherType.normalizeTypes(propertyType)

    override def semanticCheck: SemanticCheck =
      super.checkProperties(properties.map(_.propertyKey)) chain CypherTypeChecking.checkPropertyTypeForConstraint(
        propertyType,
        normalizedPropertyType,
        SemanticError.propertyTypeUnsupportedInConstraint("graph type", _, _, _)
      )

    override def withProperties(newProperties: ArraySeq[Property]): GraphTypeConstraintBody =
      this.copy(properties = newProperties)(position)
  }

  case class KeyConstraint(override val properties: ArraySeq[Property])(val position: InputPosition)
      extends GraphTypeConstraintBody {
    override def semanticCheck: SemanticCheck = checkProperties(properties.map(_.propertyKey))

    override def withProperties(newProperties: ArraySeq[Property]): GraphTypeConstraintBody =
      this.copy(properties = newProperties)(position)
  }

  case class UniquenessConstraint(override val properties: ArraySeq[Property])(val position: InputPosition)
      extends GraphTypeConstraintBody {
    override def semanticCheck: SemanticCheck = checkProperties(properties.map(_.propertyKey))

    override def withProperties(newProperties: ArraySeq[Property]): GraphTypeConstraintBody =
      this.copy(properties = newProperties)(position)
  }
}
