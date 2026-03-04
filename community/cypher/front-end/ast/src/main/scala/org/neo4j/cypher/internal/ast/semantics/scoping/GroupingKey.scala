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
package org.neo4j.cypher.internal.ast.semantics.scoping

import org.neo4j.cypher.internal.ast.ReturnItem
import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.Variable

sealed trait ProjectionItem {
  def expression: Expression
  def alias: Option[LogicalVariable]

  def aggregatingRecognizableExpression: Option[Expression]
  def subclauseRecognizableExpression: Option[Expression]
  def subclauseRecognizableSymbols: Set[Expression]

  def scopeSymbol: LogicalVariable = alias.getOrElse(anonVariable)

  private def anonName(expression: Expression): String = {
    val stringifier = ExpressionStringifier()
    " " * 3 + "grpKey_" + stringifier.apply(expression)
  }

  private def anonVariable: LogicalVariable =
    Variable(anonName(expression))(expression.position, isIsolated = false)
}

object ProjectionItem {

  def unapply(pi: ProjectionItem): Option[(Expression, Option[LogicalVariable])] = Some((pi.expression, pi.alias))
}

case class GroupingKey(override val expression: Expression, override val alias: Option[LogicalVariable])
    extends ProjectionItem {

  override val aggregatingRecognizableExpression: Option[Expression] = expression match {
    case v: LogicalVariable                  => Some(v)
    case p @ Property(_: LogicalVariable, _) => Some(p)
    case _                                   => None
  }

  override val subclauseRecognizableExpression: Option[Expression] = Some(expression)

  override def subclauseRecognizableSymbols: Set[Expression] = (alias ++ subclauseRecognizableExpression).toSet
}

case class AggregatingItem(override val expression: Expression, override val alias: Option[LogicalVariable])
    extends ProjectionItem {

  override val aggregatingRecognizableExpression: Option[Expression] = None

  override val subclauseRecognizableExpression: Option[Expression] = Some(expression)

  override def subclauseRecognizableSymbols: Set[Expression] = (alias ++ subclauseRecognizableExpression).toSet
}

case class ProjectionItems(groupingKeys: Set[GroupingKey], aggregatingItems: Set[AggregatingItem], distinct: Boolean) {
  val items: Set[ProjectionItem] = groupingKeys ++ aggregatingItems
  val aliases: Set[LogicalVariable] = groupingKeys.flatMap(_.alias) ++ aggregatingItems.flatMap(_.alias)

  val aggregatingRecognizableExpressions: Set[Expression] = groupingKeys.flatMap(_.aggregatingRecognizableExpression)

  val subclauseRecognizableSymbols: Set[Expression] =
    groupingKeys.flatMap(_.subclauseRecognizableSymbols) ++
      aggregatingItems.flatMap(_.subclauseRecognizableSymbols)

  val subclauseScopeSymbols: Set[LogicalVariable] =
    groupingKeys.map(_.scopeSymbol) ++
      aggregatingItems.map(_.scopeSymbol)

  def getGroupingKeyReference(expression: Expression): Option[LogicalVariable] =
    groupingKeys.find(_.expression == expression).map(_.scopeSymbol)
  def containsAggregationRef(reference: Expression): Boolean = aggregatingRecognizableExpressions contains reference
  def containsSubclauseRef(reference: Expression): Boolean = subclauseRecognizableSymbols contains reference

  def isAggregating: Boolean = aggregatingItems.nonEmpty || distinct
  def isEmpty: Boolean = groupingKeys.isEmpty && aggregatingItems.isEmpty
  def size: Int = groupingKeys.size + aggregatingItems.size

}

object ProjectionItems {

  def apply(
    groupingItems: Seq[ReturnItem],
    aggregatingItems: Seq[ReturnItem],
    additionalGroupings: Set[_ <: Expression],
    distinct: Boolean
  ): ProjectionItems = {
    ProjectionItems(
      groupingItems.map(gi => GroupingKey(gi.expression, gi.alias)).toSet ++ additionalGroupings.map {
        case lv: LogicalVariable => GroupingKey(lv, Some(lv))
        case expr                => GroupingKey(expr, None)
      },
      aggregatingItems.map(ai => AggregatingItem(ai.expression, ai.alias)).toSet,
      distinct
    )
  }
}
