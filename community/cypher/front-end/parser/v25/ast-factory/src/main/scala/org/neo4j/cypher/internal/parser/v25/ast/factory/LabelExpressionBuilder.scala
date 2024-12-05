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

import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import org.neo4j.cypher.internal.expressions
import org.neo4j.cypher.internal.expressions.DynamicLabelOrRelTypeExpression
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LabelName
import org.neo4j.cypher.internal.expressions.LabelOrRelTypeName
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.NodePattern
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.RelationshipPattern
import org.neo4j.cypher.internal.label_expressions.LabelExpression
import org.neo4j.cypher.internal.label_expressions.LabelExpression.ColonConjunction
import org.neo4j.cypher.internal.label_expressions.LabelExpression.ColonDisjunction
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Conjunctions
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Disjunctions
import org.neo4j.cypher.internal.label_expressions.LabelExpression.DynamicLeaf
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Leaf
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Negation
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Wildcard
import org.neo4j.cypher.internal.parser.AstRuleCtx
import org.neo4j.cypher.internal.parser.ast.util.Util.astChild
import org.neo4j.cypher.internal.parser.ast.util.Util.astOpt
import org.neo4j.cypher.internal.parser.ast.util.Util.astSeq
import org.neo4j.cypher.internal.parser.ast.util.Util.ctxChild
import org.neo4j.cypher.internal.parser.ast.util.Util.lastChild
import org.neo4j.cypher.internal.parser.ast.util.Util.nodeChild
import org.neo4j.cypher.internal.parser.ast.util.Util.pos
import org.neo4j.cypher.internal.parser.ast.util.Util.semanticDirection
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser.AnyLabelContext
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser.DynamicLabelContext
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser.LabelNameContext
import org.neo4j.cypher.internal.parser.v25.Cypher25Parser.ParenthesizedLabelExpressionContext
import org.neo4j.cypher.internal.parser.v25.Cypher25ParserListener

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.CollectionHasAsScala

trait LabelExpressionBuilder extends Cypher25ParserListener {

  final override def exitNodePattern(
    ctx: Cypher25Parser.NodePatternContext
  ): Unit = {
    val variable = astOpt[LogicalVariable](ctx.variable())
    val labelExpression = astOpt[LabelExpression](ctx.labelExpression())
    val properties = astOpt[Expression](ctx.properties())
    val expression = astOpt[Expression](ctx.expression())
    ctx.ast = NodePattern(variable, labelExpression, properties, expression)(pos(ctx))
  }

  final override def exitRelationshipPattern(ctx: Cypher25Parser.RelationshipPatternContext): Unit = {
    ctx.ast = RelationshipPattern(
      variable = astOpt[LogicalVariable](ctx.variable()),
      labelExpression = astOpt[LabelExpression](ctx.labelExpression()),
      length = astOpt[Option[expressions.Range]](ctx.pathLength()),
      properties = astOpt[Expression](ctx.properties()),
      predicate = astOpt[Expression](ctx.expression()),
      direction = semanticDirection(hasRightArrow = ctx.rightArrow() != null, hasLeftArrow = ctx.leftArrow() != null)
    )(pos(ctx))
  }

  final override def exitNodeLabels(ctx: Cypher25Parser.NodeLabelsContext): Unit = {
    ctx.ast = (astSeq[LabelName](ctx.labelType()), astSeq[Expression](ctx.dynamicLabelType()))
  }

  final override def exitNodeLabelsIs(ctx: Cypher25Parser.NodeLabelsIsContext): Unit = {
    val labelTypes = ArraySeq.newBuilder[LabelName]
      .addAll(Option(ctx.symbolicNameString()).map(c => LabelName(c.ast())(pos(c))))
      .addAll(astSeq(ctx.labelType()))
      .result()
    val dynamicLabels = ArraySeq.newBuilder[Expression]
      .addAll(astOpt(ctx.dynamicExpression()))
      .addAll(astSeq(ctx.dynamicLabelType()))
      .result()
    ctx.ast = (labelTypes, dynamicLabels)
  }

  final override def exitDynamicExpression(ctx: Cypher25Parser.DynamicExpressionContext): Unit = {
    ctx.ast = ctxChild(ctx, 2).ast
  }

  final override def exitDynamicAnyAllExpression(ctx: Cypher25Parser.DynamicAnyAllExpressionContext): Unit = {
    ctx.ast = DynamicLabelOrRelTypeExpression(ctx.expression().ast(), ctx.ANY() == null)(pos(ctx))
  }

  final override def exitDynamicLabelType(ctx: Cypher25Parser.DynamicLabelTypeContext): Unit = {
    ctx.ast = ctxChild(ctx, 1).ast
  }

  final override def exitLabelType(ctx: Cypher25Parser.LabelTypeContext): Unit = {
    val child = ctxChild(ctx, 1)
    ctx.ast = LabelName(child.ast())(pos(child))
  }

  override def exitRelType(ctx: Cypher25Parser.RelTypeContext): Unit = {
    val child = ctxChild(ctx, 1)
    ctx.ast = RelTypeName(child.ast())(pos(child))
  }

  final override def exitLabelOrRelType(ctx: Cypher25Parser.LabelOrRelTypeContext): Unit = {
    val child = ctxChild(ctx, 1)
    ctx.ast = LabelOrRelTypeName(child.ast())(pos(child))
  }

  final override def exitLabelExpression(ctx: Cypher25Parser.LabelExpressionContext): Unit = {
    def setContainsIs(labelExpression: LabelExpression): LabelExpression = labelExpression match {
      case sl: Leaf           => sl.copy(containsIs = true)
      case dl: DynamicLeaf    => dl.copy(containsIs = true)
      case wc: Wildcard       => wc.copy(containsIs = true)(labelExpression.position)
      case Negation(child, _) => Negation(setContainsIs(child), containsIs = true)(labelExpression.position)
      case Conjunctions(children, _) =>
        Conjunctions(children.map(setContainsIs), containsIs = true)(labelExpression.position)
      case ColonConjunction(lhs, rhs, _) =>
        ColonConjunction(setContainsIs(lhs), setContainsIs(rhs), containsIs = true)(labelExpression.position)
      case Disjunctions(children, _) =>
        Disjunctions(children.map(setContainsIs), containsIs = true)(labelExpression.position)
      case ColonDisjunction(lhs, rhs, _) =>
        ColonDisjunction(setContainsIs(lhs), setContainsIs(rhs), containsIs = true)(labelExpression.position)
    }
    val containsIs = ctx.IS() != null
    val labelExpression = ctxChild(ctx, 1).ast[LabelExpression]
    ctx.ast = if (containsIs) setContainsIs(labelExpression) else labelExpression
  }

  final override def exitLabelExpression4(ctx: Cypher25Parser.LabelExpression4Context): Unit = {
    val children = ctx.children; val size = children.size()
    var result = children.get(0).asInstanceOf[AstRuleCtx].ast[LabelExpression]()
    var colon = false
    var i = 1
    while (i < size) {
      children.get(i) match {
        case node: TerminalNode if node.getSymbol.getType == Cypher25Parser.COLON => colon = true
        case lblCtx: Cypher25Parser.LabelExpression3Context =>
          val rhs = lblCtx.ast[LabelExpression]()
          if (colon) {
            result = ColonDisjunction(result, rhs)(pos(nodeChild(ctx, i - 2)))
            colon = false
          } else {
            result = Disjunctions.flat(result, rhs, pos(nodeChild(ctx, i - 1)))
          }
        case _ =>
      }
      i += 1
    }
    ctx.ast = result
  }

  final override def exitLabelExpression3(ctx: Cypher25Parser.LabelExpression3Context): Unit = {
    val children = ctx.children; val size = children.size()
    // Left most LE2
    var result = children.get(0).asInstanceOf[AstRuleCtx].ast[LabelExpression]()
    var colon = false
    var i = 1
    while (i < size) {
      children.get(i) match {
        case node: TerminalNode if node.getSymbol.getType == Cypher25Parser.COLON => colon = true
        case lblCtx: Cypher25Parser.LabelExpression2Context =>
          val rhs = lblCtx.ast[LabelExpression]()
          if (colon) {
            result = ColonConjunction(result, rhs)(pos(nodeChild(ctx, i - 1)))
            colon = false
          } else {
            result = Conjunctions.flat(result, rhs, pos(nodeChild(ctx, i - 1)))
          }
        case _ =>
      }
      i += 1
    }
    ctx.ast = result
  }

  final override def exitLabelExpression2(ctx: Cypher25Parser.LabelExpression2Context): Unit = {
    ctx.ast = ctx.children.size match {
      case 1 => ctxChild(ctx, 0).ast
      case 2 => Negation(astChild(ctx, 1))(pos(ctx))
      case _ => ctx.EXCLAMATION_MARK().asScala.foldRight(lastChild[AstRuleCtx](ctx).ast[LabelExpression]()) {
          case (exclamation, acc) =>
            Negation(acc)(pos(exclamation.getSymbol))
        }
    }
  }

  def getIsLabel(ctx: RuleContext): Int = {
    var parent = ctx.parent
    var isLabel = 0
    while (isLabel == 0) {
      if (parent == null || parent.getRuleIndex == Cypher25Parser.RULE_comparisonExpression6) isLabel = 3
      else if (parent.getRuleIndex == Cypher25Parser.RULE_nodePattern) isLabel = 1
      else if (parent.getRuleIndex == Cypher25Parser.RULE_relationshipPattern) isLabel = 2
      else parent = parent.getParent
    }
    isLabel
  }

  final override def exitLabelExpression1(ctx: Cypher25Parser.LabelExpression1Context): Unit = {
    ctx.ast = ctx match {
      case ctx: ParenthesizedLabelExpressionContext =>
        ctx.labelExpression4().ast
      case ctx: AnyLabelContext =>
        Wildcard()(pos(ctx))
      case ctx: LabelNameContext =>
        getIsLabel(ctx) match {
          case 1 =>
            LabelExpression.Leaf(
              LabelName(ctx.symbolicNameString().ast())(pos(ctx))
            )
          case 2 =>
            LabelExpression.Leaf(
              RelTypeName(ctx.symbolicNameString().ast())(pos(ctx))
            )
          case 3 =>
            LabelExpression.Leaf(
              LabelOrRelTypeName(ctx.symbolicNameString().ast())(pos(ctx))
            )
        }
      case ctx: DynamicLabelContext =>
        getIsLabel(ctx) match {
          case 1 =>
            LabelExpression.DynamicLeaf(
              ctx.dynamicAnyAllExpression().ast[DynamicLabelOrRelTypeExpression]().asDynamicLabelExpression
            )
          case 2 =>
            LabelExpression.DynamicLeaf(
              ctx.dynamicAnyAllExpression().ast[DynamicLabelOrRelTypeExpression]().asDynamicRelTypeExpression
            )
          case 3 =>
            LabelExpression.DynamicLeaf(
              ctx.dynamicAnyAllExpression().ast()
            )
        }
      case _ =>
        throw new IllegalStateException("Parsed an unknown LabelExpression1 type")
    }
  }

  final override def exitInsertNodePattern(ctx: Cypher25Parser.InsertNodePatternContext): Unit = {
    ctx.ast = NodePattern(
      variable = astOpt(ctx.variable()),
      labelExpression = astOpt(ctx.insertNodeLabelExpression()),
      properties = astOpt(ctx.map()),
      predicate = astOpt(ctx.expression())
    )(pos(ctx))
  }

  final override def exitInsertNodeLabelExpression(
    ctx: Cypher25Parser.InsertNodeLabelExpressionContext
  ): Unit = {
//    ctx.ast = Leaf(ctx.insertLabelConjunction().ast(), containsIs = ctx.IS != null)
    val containsIs = ctx.IS != null
    val children = ctx.children; val size = children.size()
    // Left most LE2
    val firstChild = ctxChild(ctx, 1)
    var result: LabelExpression = Leaf(LabelName(firstChild.ast())(pos(firstChild)), containsIs)
    if (size > 2) {
      var colon = false
      var i = 2
      while (i < size) {
        children.get(i) match {
          case node: TerminalNode if node.getSymbol.getType == Cypher25Parser.COLON => colon = true
          case lblCtx: Cypher25Parser.SymbolicNameStringContext =>
            val rhs = Leaf(LabelName(lblCtx.ast())(pos(lblCtx)), containsIs = ctx.IS != null)
            if (colon) {
              result = ColonConjunction(result, rhs, containsIs)(pos(nodeChild(ctx, i - 1)))
              colon = false
            } else {
              result = Conjunctions.flat(result, rhs, pos(nodeChild(ctx, i - 1)), containsIs)
            }
          case _ =>
        }
        i += 1
      }

    }
    ctx.ast = result
  }

  final override def exitInsertRelationshipPattern(ctx: Cypher25Parser.InsertRelationshipPatternContext): Unit = {
    ctx.ast = RelationshipPattern(
      variable = astOpt(ctx.variable()),
      labelExpression = astOpt(ctx.insertRelationshipLabelExpression()),
      length = None,
      properties = astOpt(ctx.map),
      predicate = astOpt(ctx.expression()),
      direction = semanticDirection(hasRightArrow = ctx.rightArrow() != null, hasLeftArrow = ctx.leftArrow() != null)
    )(pos(ctx))
  }

  final override def exitInsertRelationshipLabelExpression(
    ctx: Cypher25Parser.InsertRelationshipLabelExpressionContext
  ): Unit = {
    val symbolicNameString = ctx.symbolicNameString()
    ctx.ast = Leaf(RelTypeName(symbolicNameString.ast())(pos(symbolicNameString)), containsIs = ctx.IS != null)
  }

  final override def exitLeftArrow(
    ctx: Cypher25Parser.LeftArrowContext
  ): Unit = {}

  final override def exitArrowLine(
    ctx: Cypher25Parser.ArrowLineContext
  ): Unit = {}

  final override def exitRightArrow(
    ctx: Cypher25Parser.RightArrowContext
  ): Unit = {}

}
