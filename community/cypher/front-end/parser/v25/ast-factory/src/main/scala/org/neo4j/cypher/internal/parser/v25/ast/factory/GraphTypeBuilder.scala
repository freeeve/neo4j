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
package org.neo4j.cypher.internal.parser.v25.ast.factory

import org.neo4j.cypher.internal.ast.AlterCurrentGraphType
import org.neo4j.cypher.internal.ast.EdgeType
import org.neo4j.cypher.internal.ast.EdgeTypeReference
import org.neo4j.cypher.internal.ast.EdgeTypeReferenceByIdentifyingLabel
import org.neo4j.cypher.internal.ast.EdgeTypeReferenceByLabel
import org.neo4j.cypher.internal.ast.EdgeTypeReferenceByVariable
import org.neo4j.cypher.internal.ast.EmptyNodeTypeReference
import org.neo4j.cypher.internal.ast.GraphType
import org.neo4j.cypher.internal.ast.GraphTypeConstraint
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.ExistenceConstraint
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.GraphTypeConstraintBody
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.KeyConstraint
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.PropertyTypeConstraint
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.UniquenessConstraint
import org.neo4j.cypher.internal.ast.GraphTypeConstraintDefinition
import org.neo4j.cypher.internal.ast.GraphTypeConstraintName
import org.neo4j.cypher.internal.ast.GraphTypeElementReference
import org.neo4j.cypher.internal.ast.GraphTypeEntry
import org.neo4j.cypher.internal.ast.NoOptions
import org.neo4j.cypher.internal.ast.NodeType
import org.neo4j.cypher.internal.ast.NodeTypeReference
import org.neo4j.cypher.internal.ast.NodeTypeReferenceByIdentifyingLabel
import org.neo4j.cypher.internal.ast.NodeTypeReferenceByLabel
import org.neo4j.cypher.internal.ast.NodeTypeReferenceByVariable
import org.neo4j.cypher.internal.ast.Options
import org.neo4j.cypher.internal.ast.PropertyType
import org.neo4j.cypher.internal.ast.PropertyType.PropertyInlineConstraintBody
import org.neo4j.cypher.internal.ast.PropertyType.PropertyInlineKeyConstraint
import org.neo4j.cypher.internal.ast.PropertyType.PropertyInlineUniquenessConstraint
import org.neo4j.cypher.internal.expressions.LabelName
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertyKeyName
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.parser.ast.util.Util.astOpt
import org.neo4j.cypher.internal.parser.ast.util.Util.astSeq
import org.neo4j.cypher.internal.parser.ast.util.Util.pos
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser
import org.neo4j.cypher.internal.parser.v25.Cypher25ParserListener
import org.neo4j.cypher.internal.util.CypherExceptionFactory
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.CypherType

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.IterableHasAsScala

trait GraphTypeBuilder extends Cypher25ParserListener {

  protected def exceptionFactory: CypherExceptionFactory

  type GraphTypeElementResult = (Option[GraphTypeEntry], Set[GraphTypeConstraint])
  type InlineConstraintResult = (GraphTypeConstraintBody, Options)

  override def exitAlterCurrentGraphType(ctx: Cypher25Parser.AlterCurrentGraphTypeContext): Unit = {
    val (operation: AlterCurrentGraphType.AlterOperation, g) = if (ctx.SET() != null)
      (AlterCurrentGraphType.Set, ctx.graphTypeSpecification().ast[GraphType]())
    else if (ctx.ADD() != null) (AlterCurrentGraphType.Add, ctx.graphTypeSpecification().ast[GraphType]())
    else if (ctx.DROP() != null) (AlterCurrentGraphType.Drop, ctx.graphTypeDropSpecification().ast[GraphType]())
    else if (ctx.ALTER() != null) (AlterCurrentGraphType.Alter, ctx.graphTypeSpecification().ast[GraphType]())
    else null
    ctx.ast = AlterCurrentGraphType(g, operation)(pos(ctx))
  }

  override def exitGraphTypeSpecification(ctx: Cypher25Parser.GraphTypeSpecificationContext): Unit = {
    val elements = if (ctx.graphTypeSpecificationBody() != null) {
      ctx.graphTypeSpecificationBody().ast[Seq[GraphTypeElementResult]]()
    } else {
      Seq.empty
    }
    ctx.ast = buildGraphType(elements, pos(ctx))
  }

  override def exitGraphTypeDropSpecification(ctx: Cypher25Parser.GraphTypeDropSpecificationContext): Unit = {
    val elements = if (ctx.graphTypeDropSpecificationBody() != null) {
      ctx.graphTypeDropSpecificationBody().ast[Seq[GraphTypeElementResult]]()
    } else {
      Seq.empty
    }
    ctx.ast = buildGraphType(elements, pos(ctx))
  }

  private def buildGraphType(elements: Seq[GraphTypeElementResult], pos: InputPosition): GraphType = {
    val (graphTypeEntries, constraints) = elements.foldLeft((Set[GraphTypeEntry](), Set[GraphTypeConstraint]())) {
      case ((gtes, consts), (Some(graphTypeEntry), constraints)) => (gtes + graphTypeEntry, consts ++ constraints)
      case ((gtes, consts), (None, constraints))                 => (gtes, consts ++ constraints)
    }

    GraphType(graphTypeEntries, constraints)(pos)
  }

  override def exitGraphTypeDropSpecificationBody(ctx: Cypher25Parser.GraphTypeDropSpecificationBodyContext): Unit = {
    ctx.ast = astSeq[GraphTypeElementResult](ctx.graphTypeDropElement())
  }

  override def exitGraphTypeSpecificationBody(ctx: Cypher25Parser.GraphTypeSpecificationBodyContext): Unit = {
    ctx.ast = astSeq[GraphTypeElementResult](ctx.graphTypeElement())
  }

  override def exitGraphTypeElement(ctx: Cypher25Parser.GraphTypeElementContext): Unit = {
    if (ctx.nodeTypeSpecification() != null) {
      val nodeType = ctx.nodeTypeSpecification().ast[NodeType]()
      ctx.ast = (Some(nodeType), Set.empty)
    } else if (ctx.edgeTypeSpecification() != null) {
      val edgeType = ctx.edgeTypeSpecification().ast[EdgeType]()
      ctx.ast = (Some(edgeType), Set.empty)
    } else {
      ctx.ast = (None, Set(ctx.constraintSpecification().ast[GraphTypeConstraint]))
    }
  }

  override def exitGraphTypeDropElement(ctx: Cypher25Parser.GraphTypeDropElementContext): Unit = {
    if (ctx.nodeTypeSpecification() != null) {
      val nodeType = ctx.nodeTypeSpecification().ast[NodeType]()
      ctx.ast = (Some(nodeType), Set.empty)
    } else if (ctx.edgeTypeSpecification() != null) {
      val edgeType = ctx.edgeTypeSpecification().ast[EdgeType]()
      ctx.ast = (Some(edgeType), Set.empty)
    } else {
      ctx.ast = (None, Set(GraphTypeConstraintName(ctx.symbolicNameString().ast[String]())(pos(ctx))))
    }
  }

  override def exitNodeTypeInlineConstraintList(ctx: Cypher25Parser.NodeTypeInlineConstraintListContext): Unit = {
    ctx.ast = ctx.constraintType().asScala.zipWithIndex.map { case (ct, i) =>
      (constraintType(ct), astOpt[Options](ctx.commandOptions(i)).getOrElse(NoOptions))
    }.toSet
  }

  override def exitEdgeTypeInlineConstraintList(ctx: Cypher25Parser.EdgeTypeInlineConstraintListContext): Unit = {
    ctx.ast = ctx.constraintType().asScala.zipWithIndex.map { case (ct, i) =>
      (constraintType(ct), astOpt[Options](ctx.commandOptions(i)).getOrElse(NoOptions))
    }.toSet
  }

  override def exitNodeTypeSpecification(ctx: Cypher25Parser.NodeTypeSpecificationContext): Unit = {
    val name = ctx.identifyingLabel().ast[LabelName]()
    val variable = astOpt[Variable](ctx.variable())
    val additionalLabels = astOpt[Set[LabelName]](ctx.impliedLabelSet()).getOrElse(Set.empty)
    val props = astOpt[Set[PropertyType]](ctx.propertyTypeList()).getOrElse(Set.empty)
    val constraintsBodies = astOpt[Set[InlineConstraintResult]](ctx.nodeTypeInlineConstraintList()).getOrElse(Set.empty)
    ctx.ast = NodeType(variable, name, additionalLabels, props, constraintsBodies)(pos(ctx))
  }

  override def exitImpliedLabelSet(ctx: Cypher25Parser.ImpliedLabelSetContext): Unit = {
    ctx.ast = Set(ctx.labelType().ast[LabelName]()) ++ ctx.symbolicNameString().asScala.map(sns =>
      LabelName(sns.ast[String]())(pos(sns))
    )
  }

  override def exitNodeTypeReference(ctx: Cypher25Parser.NodeTypeReferenceContext): Unit = {
    ctx.ast = if (ctx.nodeTypeInSituReference() != null) {
      ctx.nodeTypeInSituReference().ast[NodeTypeReference]()
    } else {
      ctx.nodeTypeAliasReference().ast[NodeTypeReference]
    }
  }

  override def exitNodeTypeAliasReference(ctx: Cypher25Parser.NodeTypeAliasReferenceContext): Unit = {
    ctx.ast = NodeTypeReferenceByVariable(ctx.variable().ast())(pos(ctx))
  }

  override def exitNodeTypeInSituReference(ctx: Cypher25Parser.NodeTypeInSituReferenceContext): Unit = {
    ctx.ast = if (ctx.labelType() != null) {
      if (ctx.implies() != null) {
        NodeTypeReferenceByIdentifyingLabel(ctx.labelType().ast[LabelName](), astOpt[Variable](ctx.variable()))(pos(
          ctx
        ))
      } else {
        NodeTypeReferenceByLabel(ctx.labelType().ast[LabelName](), astOpt[Variable](ctx.variable()))(pos(ctx))
      }
    } else {
      EmptyNodeTypeReference()(pos(ctx))
    }
  }

  override def exitEdgeTypeSpecification(ctx: Cypher25Parser.EdgeTypeSpecificationContext): Unit = {
    val (variable, relType, props) =
      ctx.arcTypePointingRight().ast[(Option[Variable], RelTypeName, Set[PropertyType])]()
    val constraintsBodies: Set[InlineConstraintResult] =
      astOpt[Set[InlineConstraintResult]](ctx.edgeTypeInlineConstraintList()).getOrElse(Set.empty)
    ctx.ast = EdgeType(
      ctx.nodeTypeReference(0).ast[NodeTypeReference],
      variable,
      relType,
      props,
      ctx.nodeTypeReference(1).ast[NodeTypeReference](),
      constraintsBodies
    )(pos(ctx))
  }

  override def exitArcTypePointingRight(ctx: Cypher25Parser.ArcTypePointingRightContext): Unit = {
    val variable = astOpt[Variable](ctx.variable())
    ctx.ast = (
      variable,
      ctx.identifyingRelationship().ast[RelTypeName](),
      astOpt[Set[PropertyType]](ctx.propertyTypeList()).getOrElse(Set.empty)
    )
  }

  override def exitEdgeTypeReference(ctx: Cypher25Parser.EdgeTypeReferenceContext): Unit = {
    if (ctx.edgeTypeInSituReference() != null) {
      ctx.ast = ctx.edgeTypeInSituReference().ast[EdgeTypeReference]
    } else if (ctx.edgeTypeAliasReference() != null) {
      ctx.ast = ctx.edgeTypeAliasReference().ast[EdgeTypeReference]()
    }
  }

  override def exitEdgeTypeAliasReference(ctx: Cypher25Parser.EdgeTypeAliasReferenceContext): Unit = {
    ctx.ast = EdgeTypeReferenceByVariable(ctx.variable().ast[Variable]())(pos(ctx))
  }

  override def exitEdgeTypeInSituReference(ctx: Cypher25Parser.EdgeTypeInSituReferenceContext): Unit = {
    if (ctx.implies() != null) {
      ctx.ast = EdgeTypeReferenceByIdentifyingLabel(ctx.relType().ast[RelTypeName](), astOpt[Variable](ctx.variable()))(
        pos(ctx.relType())
      )
    } else {
      ctx.ast =
        EdgeTypeReferenceByLabel(ctx.relType().ast[RelTypeName](), astOpt[Variable](ctx.variable()))(pos(ctx.relType()))
    }
  }

  override def exitPropertyTypeList(ctx: Cypher25Parser.PropertyTypeListContext): Unit = {
    val props = astSeq[PropertyType](ctx.propertyType())
    ctx.ast = props.toSet
  }

  override def exitPropertyType(ctx: Cypher25Parser.PropertyTypeContext): Unit = {
    val propKeyName = ctx.propertyKeyName().ast[PropertyKeyName]()
    val constraint = astOpt[PropertyInlineConstraintBody](ctx.propertyTypeInlineConstraint())
    ctx.ast = PropertyType(propKeyName, ctx.`type`().ast[CypherType](), constraint)(pos(ctx))
  }

  override def exitPropertyTypeInlineConstraint(ctx: Cypher25Parser.PropertyTypeInlineConstraintContext): Unit = {
    ctx.ast = if (ctx.UNIQUE() != null) {
      PropertyInlineUniquenessConstraint()(pos(ctx))
    } else if (ctx.KEY() != null) {
      PropertyInlineKeyConstraint()(pos(ctx))
    } else null
  }

  override def exitConstraintSpecification(ctx: Cypher25Parser.ConstraintSpecificationContext): Unit = {
    val reference: GraphTypeElementReference = astOpt[GraphTypeElementReference](ctx.nodeTypeReference())
      .getOrElse(ctx.edgeTypeReference().ast[GraphTypeElementReference]())
    val name = astOpt[String](ctx.symbolicNameString())
    ctx.ast = GraphTypeConstraintDefinition(
      name,
      reference,
      constraintType(ctx.constraintType()),
      astOpt[Options](ctx.commandOptions()).getOrElse(NoOptions)
    )(pos(ctx))
  }

  override def exitIdentifyingLabel(ctx: Cypher25Parser.IdentifyingLabelContext): Unit = {
    ctx.ast = ctx.labelType().ast
  }

  override def exitIdentifyingRelationship(ctx: Cypher25Parser.IdentifyingRelationshipContext): Unit = {
    ctx.ast = ctx.relType().ast()
  }

  override def exitImplies(ctx: Cypher25Parser.ImpliesContext): Unit = {}

  override def exitTyped(ctx: Cypher25Parser.TypedContext): Unit = {}

  private def constraintType(ctx: Cypher25Parser.ConstraintTypeContext): GraphTypeConstraintBody = ctx match {
    case c: Cypher25Parser.ConstraintTypedContext =>
      PropertyTypeConstraint(
        c.propertyList().ast[ArraySeq[Property]](),
        c.`type`().ast[CypherType]()
      )(pos(c))
    case c: Cypher25Parser.ConstraintKeyContext => KeyConstraint(c.propertyList().ast[ArraySeq[Property]]())(pos(c))
    case c: Cypher25Parser.ConstraintIsNotNullContext =>
      ExistenceConstraint(c.propertyList().ast[ArraySeq[Property]]())(pos(c))
    case c: Cypher25Parser.ConstraintIsUniqueContext =>
      UniquenessConstraint(c.propertyList().ast[ArraySeq[Property]]())(pos(c))
    case _ => throw new IllegalStateException("Unknown Constraint Command")
  }
}
