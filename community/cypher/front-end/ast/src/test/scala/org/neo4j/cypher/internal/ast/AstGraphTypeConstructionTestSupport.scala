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
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.KeyConstraint
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.PropertyTypeConstraint
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.UniquenessConstraint
import org.neo4j.cypher.internal.expressions.LabelName
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertyKeyName
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.CypherType

import scala.collection.immutable.ArraySeq

trait AstGraphTypeConstructionTestSupport extends AstConstructionTestSupport {

  def literalName(name: String): DatabaseName = NamespacedName.apply(List(name))(defaultPos)

  def alterCurrentGraphTypeSet(graphType: GraphType): Statements = {
    AlterCurrentGraphType(graphType, AlterCurrentGraphType.Set)(defaultPos)
  }

  def alterCurrentGraphTypeAdd(graphType: GraphType): Statements = {
    AlterCurrentGraphType(graphType, AlterCurrentGraphType.Add)(defaultPos)
  }

  def alterCurrentGraphTypeDrop(graphType: GraphType): Statements = {
    AlterCurrentGraphType(graphType, AlterCurrentGraphType.Drop)(defaultPos)
  }

  def alterCurrentGraphTypeAlter(graphType: GraphType): Statements = {
    AlterCurrentGraphType(graphType, AlterCurrentGraphType.Alter)(defaultPos)
  }

  def graphType(types: GraphTypeEntry*): GraphType = {
    GraphType(types.toSet, Set.empty)(defaultPos)
  }

  def graphType(types: Seq[GraphTypeEntry], constraints: Seq[GraphTypeConstraint]): GraphType = {
    GraphType(types.toSet, constraints.toSet)(defaultPos)
  }

  def nodeType(name: String, propertyTypes: PropertyType*): NodeType = {
    NodeType(None, LabelName(name)(defaultPos), Set.empty, propertyTypes.toSet, Set.empty)(defaultPos)
  }

  def nodeType(name: String, labels: Set[String], propertyTypes: PropertyType*): NodeType = {
    NodeType(None, LabelName(name)(defaultPos), labels.map(LabelName(_)(defaultPos)), propertyTypes.toSet, Set.empty)(
      defaultPos
    )
  }

  def nodeTypeWithConstraints(
    name: String,
    labels: Set[String],
    constraints: Set[GraphTypeConstraintBody],
    propertyTypes: PropertyType*
  ): NodeType = {
    NodeType(
      None,
      LabelName(name)(defaultPos),
      labels.map(LabelName(_)(defaultPos)),
      propertyTypes.toSet,
      constraints.map((_, NoOptions))
    )(
      defaultPos
    )
  }

  def nodeType(name: String, variable: String, propertyTypes: PropertyType*): NodeType = {
    NodeType(
      Some(varFor(variable)),
      LabelName(name)(defaultPos),
      Set.empty,
      propertyTypes.toSet,
      Set.empty
    )(defaultPos)
  }

  def nodeTypeWithConstraints(
    name: String,
    variable: String,
    constraints: Set[GraphTypeConstraintBody],
    propertyTypes: PropertyType*
  ): NodeType = {
    NodeType(
      Some(varFor(variable)),
      LabelName(name)(defaultPos),
      Set.empty,
      propertyTypes.toSet,
      constraints.map((_, NoOptions))
    )(
      defaultPos
    )
  }

  def nodeType(name: String, variable: String, labels: Set[String], propertyTypes: PropertyType*)(implicit
    i: DummyImplicit): NodeType = {
    NodeType(
      Some(varFor(variable)),
      LabelName(name)(defaultPos),
      labels.map(LabelName(_)(defaultPos)),
      propertyTypes.toSet,
      Set.empty
    )(defaultPos)
  }

  def nodeTypeWithLabelsAndConstraints(
    name: String,
    variable: String,
    labels: Set[String],
    constraints: Set[(GraphTypeConstraintBody, Options)],
    propertyTypes: PropertyType*
  ): NodeType = {
    NodeType(
      Some(varFor(variable)),
      LabelName(name)(defaultPos),
      labels.map(LabelName(_)(defaultPos)),
      propertyTypes.toSet,
      constraints
    )(
      defaultPos
    )
  }

  def edgeType(
    start: NodeTypeReference,
    name: String,
    dest: NodeTypeReference,
    propertyTypes: PropertyType*
  ): EdgeType =
    EdgeType(start, None, RelTypeName(name)(defaultPos), propertyTypes.toSet, dest, Set.empty)(defaultPos)

  def edgeType(
    start: NodeTypeReference,
    name: String,
    variable: String,
    dest: NodeTypeReference,
    propertyTypes: PropertyType*
  ): EdgeType =
    EdgeType(start, Some(varFor(variable)), RelTypeName(name)(defaultPos), propertyTypes.toSet, dest, Set.empty)(
      defaultPos
    )

  def edgeTypeWithConstraints(
    start: NodeTypeReference,
    name: String,
    variable: String,
    dest: NodeTypeReference,
    constraints: Set[GraphTypeConstraintBody],
    propertyTypes: PropertyType*
  ): EdgeType =
    EdgeType(
      start,
      Some(varFor(variable)),
      RelTypeName(name)(defaultPos),
      propertyTypes.toSet,
      dest,
      constraints.map((_, NoOptions))
    )(
      defaultPos
    )

  def propertyTypeConstraint(
    reference: GraphTypeElementReference,
    properties: ArraySeq[Property],
    cypherType: CypherType
  ): GraphTypeConstraint = {
    GraphTypeConstraintDefinition(
      None,
      reference,
      PropertyTypeConstraint(properties, cypherType)(defaultPos),
      NoOptions
    )(defaultPos)
  }

  def propertyTypeConstraint(
    name: String,
    reference: GraphTypeElementReference,
    properties: ArraySeq[Property],
    cypherType: CypherType
  ): GraphTypeConstraint = {
    GraphTypeConstraintDefinition(
      Some(name),
      reference,
      PropertyTypeConstraint(properties, cypherType)(defaultPos),
      NoOptions
    )(defaultPos)
  }

  def uniquenessConstraint(
    reference: GraphTypeElementReference,
    properties: ArraySeq[Property]
  ): GraphTypeConstraint = uniquenessConstraint(reference, properties, NoOptions)

  def uniquenessConstraint(
    name: String,
    reference: GraphTypeElementReference,
    properties: ArraySeq[Property]
  ): GraphTypeConstraint =
    GraphTypeConstraintDefinition(Some(name), reference, UniquenessConstraint(properties)(defaultPos), NoOptions)(
      defaultPos
    )

  def uniquenessConstraint(
    reference: GraphTypeElementReference,
    properties: ArraySeq[Property],
    options: Options
  ): GraphTypeConstraint = {
    GraphTypeConstraintDefinition(None, reference, UniquenessConstraint(properties)(defaultPos), options)(
      defaultPos
    )
  }

  def keyConstraint(
    reference: GraphTypeElementReference,
    properties: ArraySeq[Property]
  ): GraphTypeConstraint = {
    GraphTypeConstraintDefinition(None, reference, KeyConstraint(properties)(defaultPos), NoOptions)(
      defaultPos
    )
  }

  def keyConstraint(
    name: String,
    reference: GraphTypeElementReference,
    properties: ArraySeq[Property]
  ): GraphTypeConstraint = {
    GraphTypeConstraintDefinition(Some(name), reference, KeyConstraint(properties)(defaultPos), NoOptions)(
      defaultPos
    )
  }

  def existsConstraint(
    reference: GraphTypeElementReference,
    properties: ArraySeq[Property]
  ): GraphTypeConstraint = {
    GraphTypeConstraintDefinition(None, reference, ExistenceConstraint(properties)(defaultPos), NoOptions)(
      defaultPos
    )
  }

  def existsConstraint(
    name: String,
    reference: GraphTypeElementReference,
    properties: ArraySeq[Property]
  ): GraphTypeConstraint = {
    GraphTypeConstraintDefinition(Some(name), reference, ExistenceConstraint(properties)(defaultPos), NoOptions)(
      defaultPos
    )
  }

  def constraintName(name: String): GraphTypeConstraintName = GraphTypeConstraintName(name)(defaultPos)

  def edgeTypeRefByVar(variable: String): EdgeTypeReferenceByVariable =
    EdgeTypeReferenceByVariable(varFor(variable))(defaultPos)

  def nodeTypeRefByVar(variable: String): NodeTypeReferenceByVariable =
    NodeTypeReferenceByVariable(varFor(variable))(defaultPos)

  def edgeTypeRefByLabel(label: String): EdgeTypeReferenceByLabel =
    EdgeTypeReferenceByLabel(RelTypeName(label)(defaultPos))(defaultPos)

  def edgeTypeRefByLabel(label: String, variable: String): EdgeTypeReferenceByLabel =
    EdgeTypeReferenceByLabel(RelTypeName(label)(defaultPos), Some(varFor(variable)))(defaultPos)

  def nodeTypeRefByLabel(label: String): NodeTypeReferenceByLabel =
    NodeTypeReferenceByLabel(LabelName(label)(defaultPos))(defaultPos)

  def nodeTypeRefByLabel(label: String, variable: String): NodeTypeReferenceByLabel =
    NodeTypeReferenceByLabel(LabelName(label)(defaultPos), Some(varFor(variable)))(defaultPos)

  def identifyingNodeTypeRef(label: String): NodeTypeReferenceByIdentifyingLabel =
    NodeTypeReferenceByIdentifyingLabel(labelName(label, defaultPos))(defaultPos)

  def identifyingNodeTypeRef(label: String, variable: String): NodeTypeReferenceByIdentifyingLabel =
    NodeTypeReferenceByIdentifyingLabel(labelName(label, defaultPos), Some(varFor(variable)))(defaultPos)

  def identifyingEdgeTypeRef(label: String): EdgeTypeReferenceByIdentifyingLabel =
    EdgeTypeReferenceByIdentifyingLabel(relTypeName(label, defaultPos))(defaultPos)

  def identifyingEdgeTypeRef(label: String, variable: String): EdgeTypeReferenceByIdentifyingLabel =
    EdgeTypeReferenceByIdentifyingLabel(relTypeName(label, defaultPos), Some(varFor(variable)))(defaultPos)

  def propertyType(name: String, valueType: InputPosition => CypherType): PropertyType = {
    PropertyType(PropertyKeyName(name)(defaultPos), valueType(defaultPos), None)(defaultPos)
  }

  def propertyTypeWithPos(name: String, valueType: CypherType, position: InputPosition): PropertyType = {
    PropertyType(PropertyKeyName(name)(position), valueType, None)(position)
  }

  def propertyType(
    name: String,
    typ: InputPosition => CypherType,
    inlineKeyConstraint: PropertyType.PropertyInlineConstraintBody
  ): PropertyType = {
    PropertyType(PropertyKeyName(name)(defaultPos), typ(defaultPos), Some(inlineKeyConstraint))(defaultPos)
  }

}
