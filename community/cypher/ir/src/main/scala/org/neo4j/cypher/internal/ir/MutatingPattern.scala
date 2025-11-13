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
package org.neo4j.cypher.internal.ir

import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.HasMappableExpressions
import org.neo4j.cypher.internal.expressions.LabelName
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.PropertyKeyName

import scala.util.hashing.MurmurHash3

sealed trait MutatingPattern extends Product {
  def coveredIds: Set[LogicalVariable]
  def dependencies: Set[LogicalVariable]

  // We spend a lot of time hashing these objects,
  // since they are all immutable we can memoize the hashcode
  override val hashCode: Int = MurmurHash3.productHash(this)

  // Get the expressions that can contain property references that might benefit from caching it at an earlier stage
  def getExpressionsWithPossiblePropertyReferences: Seq[Expression]

  def mapExpressions(f: Expression => Expression): MutatingPattern

  def invalidatesCachedProperties: Boolean
}

sealed trait NoSymbols {
  self: MutatingPattern =>
  override def coveredIds = Set.empty[LogicalVariable]
}

sealed trait SimpleMutatingPattern extends MutatingPattern {
  override def mapExpressions(f: Expression => Expression): SimpleMutatingPattern
}

sealed trait SetMutatingPattern extends SimpleMutatingPattern with NoSymbols {
  override def mapExpressions(f: Expression => Expression): SetMutatingPattern
}

sealed trait DeleteMutatingPattern extends SimpleMutatingPattern with NoSymbols

case class SetPropertyPattern(entityExpression: Expression, propertyKeyName: PropertyKeyName, expression: Expression)
    extends SetMutatingPattern
    with HasMappableExpressions[SetPropertyPattern] {
  override def dependencies: Set[LogicalVariable] = entityExpression.dependencies ++ expression.dependencies

  override def mapExpressions(f: Expression => Expression): SetPropertyPattern =
    copy(
      entityExpression = f(entityExpression),
      expression = f(expression)
    )

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = Seq(expression)

  override def invalidatesCachedProperties: Boolean = true
}

case class SetPropertiesPattern(entityExpression: Expression, items: Seq[(PropertyKeyName, Expression)])
    extends SetMutatingPattern with HasMappableExpressions[SetPropertiesPattern] {

  override def dependencies: Set[LogicalVariable] =
    items.map(_._2).flatMap(_.dependencies).toSet ++ entityExpression.dependencies

  override def mapExpressions(f: Expression => Expression): SetPropertiesPattern =
    copy(
      entityExpression = f(entityExpression),
      items = items.map {
        case (k, e) => (k, f(e))
      }
    )

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = items.map(_._2)

  override def invalidatesCachedProperties: Boolean = true
}

case class SetRelationshipPropertyPattern(
  variable: LogicalVariable,
  propertyKey: PropertyKeyName,
  expression: Expression
) extends SetMutatingPattern
    with HasMappableExpressions[SetRelationshipPropertyPattern] {
  override def dependencies: Set[LogicalVariable] = expression.dependencies + variable

  override def mapExpressions(f: Expression => Expression): SetRelationshipPropertyPattern =
    copy(expression = f(expression))

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = Seq(expression)

  override def invalidatesCachedProperties: Boolean = true
}

case class SetRelationshipPropertiesPattern(variable: LogicalVariable, items: Seq[(PropertyKeyName, Expression)])
    extends SetMutatingPattern with HasMappableExpressions[SetRelationshipPropertiesPattern] {

  override def dependencies: Set[LogicalVariable] = items.map(_._2).flatMap(_.dependencies).toSet + variable

  override def mapExpressions(f: Expression => Expression): SetRelationshipPropertiesPattern = {
    copy(items = items.map {
      case (k, e) => (k, f(e))
    })
  }

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = items.map(_._2)

  override def invalidatesCachedProperties: Boolean = true
}

case class SetNodePropertiesFromMapPattern(variable: LogicalVariable, expression: Expression, removeOtherProps: Boolean)
    extends SetMutatingPattern with HasMappableExpressions[SetNodePropertiesFromMapPattern] {
  override def dependencies: Set[LogicalVariable] = expression.dependencies + variable

  override def mapExpressions(f: Expression => Expression): SetNodePropertiesFromMapPattern =
    copy(expression = f(expression))

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = Seq(expression)

  override def invalidatesCachedProperties: Boolean = true
}

case class SetRelationshipPropertiesFromMapPattern(
  variable: LogicalVariable,
  expression: Expression,
  removeOtherProps: Boolean
) extends SetMutatingPattern with HasMappableExpressions[SetRelationshipPropertiesFromMapPattern] {
  override def dependencies: Set[LogicalVariable] = expression.dependencies + variable

  override def mapExpressions(f: Expression => Expression): SetRelationshipPropertiesFromMapPattern =
    copy(expression = f(expression))

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = Seq(expression)

  override def invalidatesCachedProperties: Boolean = true
}

case class SetPropertiesFromMapPattern(entityExpression: Expression, expression: Expression, removeOtherProps: Boolean)
    extends SetMutatingPattern with HasMappableExpressions[SetPropertiesFromMapPattern] {

  override def dependencies: Set[LogicalVariable] =
    entityExpression.dependencies ++ expression.dependencies

  override def mapExpressions(f: Expression => Expression): SetPropertiesFromMapPattern =
    copy(f(entityExpression), f(expression))

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = Seq(expression)

  override def invalidatesCachedProperties: Boolean = true
}

case class SetDynamicPropertyPattern(entity: Expression, property: Expression, expression: Expression)
    extends SetMutatingPattern with HasMappableExpressions[SetDynamicPropertyPattern] {

  override def mapExpressions(f: Expression => Expression): SetDynamicPropertyPattern =
    copy(f(entity), f(property), f(expression))

  override def dependencies: Set[LogicalVariable] =
    entity.dependencies ++ property.dependencies ++ expression.dependencies

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = Seq(expression)

  override def invalidatesCachedProperties: Boolean = true
}

case class SetNodePropertyPattern(variable: LogicalVariable, propertyKey: PropertyKeyName, expression: Expression)
    extends SetMutatingPattern with HasMappableExpressions[SetNodePropertyPattern] {
  override def dependencies: Set[LogicalVariable] = expression.dependencies + variable
  override def mapExpressions(f: Expression => Expression): SetNodePropertyPattern = copy(expression = f(expression))
  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = Seq(expression)
  override def invalidatesCachedProperties: Boolean = true
}

case class SetNodePropertiesPattern(variable: LogicalVariable, items: Seq[(PropertyKeyName, Expression)])
    extends SetMutatingPattern with HasMappableExpressions[SetNodePropertiesPattern] {

  override def dependencies: Set[LogicalVariable] = items.map(_._2).flatMap(_.dependencies).toSet + variable

  override def mapExpressions(f: Expression => Expression): SetNodePropertiesPattern = {
    copy(items = items.map {
      case (k, e) => (k, f(e))
    })
  }

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = items.map(_._2)

  override def invalidatesCachedProperties: Boolean = true
}

case class SetLabelPattern(variable: LogicalVariable, labels: Seq[LabelName], dynamicLabels: Seq[Expression])
    extends SetMutatingPattern with HasMappableExpressions[SetLabelPattern] {
  override def dependencies: Set[LogicalVariable] = dynamicLabels.flatMap(_.dependencies).toSet + variable
  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = dynamicLabels

  override def mapExpressions(f: Expression => Expression): SetLabelPattern =
    copy(dynamicLabels = dynamicLabels.map(f(_)))

  override def invalidatesCachedProperties: Boolean = false
}

case class RemoveLabelPattern(variable: LogicalVariable, labels: Seq[LabelName], dynamicLabels: Seq[Expression])
    extends SetMutatingPattern
    with NoSymbols
    with HasMappableExpressions[RemoveLabelPattern] {
  override def dependencies: Set[LogicalVariable] = dynamicLabels.flatMap(_.dependencies).toSet + variable
  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = dynamicLabels

  override def mapExpressions(f: Expression => Expression): RemoveLabelPattern =
    copy(dynamicLabels = dynamicLabels.map(f(_)))
  override def invalidatesCachedProperties: Boolean = false
}

case class CreatePattern(commands: Seq[CreateCommand]) extends SimpleMutatingPattern
    with HasMappableExpressions[CreatePattern] {

  def nodes: Seq[CreateNode] = commands.collect {
    case c: CreateNode => c
  }

  def relationships: Seq[CreateRelationship] = commands.collect {
    case c: CreateRelationship => c
  }

  override def coveredIds: Set[LogicalVariable] = {
    val builder = Set.newBuilder[LogicalVariable]
    for (command <- commands)
      builder += command.variable
    builder.result()
  }

  override def dependencies: Set[LogicalVariable] = {
    val builder = Set.newBuilder[LogicalVariable]
    for (command <- commands)
      builder ++= command.dependencies
    builder.result()
  }

  override def mapExpressions(f: Expression => Expression): CreatePattern = {
    copy(commands = commands.map(_.mapExpressions(f)))
  }

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = commands.flatMap(_.properties)

  override def invalidatesCachedProperties: Boolean = false
}

case class DeleteExpression(expression: Expression, detachDelete: Boolean) extends DeleteMutatingPattern
    with NoSymbols with HasMappableExpressions[DeleteExpression] {
  override def dependencies: Set[LogicalVariable] = expression.dependencies

  override def mapExpressions(f: Expression => Expression): DeleteExpression = copy(expression = f(expression))

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = Seq.empty

  override def invalidatesCachedProperties: Boolean = false
}

sealed trait MergePattern {
  self: MutatingPattern =>
  def matchGraph: QueryGraph
  def createNodePatterns: Seq[CreateNode]
  def createRelationshipPatterns: Seq[CreateRelationship]
  def onMatchPatterns: Seq[SetMutatingPattern]
  def onCreatePatterns: Seq[SetMutatingPattern]
}

case class MergeNodePattern(
  createNode: CreateNode,
  matchGraph: QueryGraph,
  onCreate: Seq[SetMutatingPattern],
  onMatch: Seq[SetMutatingPattern]
) extends MutatingPattern with MergePattern with HasMappableExpressions[MergeNodePattern] {
  override def coveredIds: Set[LogicalVariable] = matchGraph.allCoveredIds

  override def dependencies: Set[LogicalVariable] =
    createNode.dependencies ++
      matchGraph.dependencies ++
      onCreate.flatMap(_.dependencies) ++
      onMatch.flatMap(_.dependencies)

  override def mapExpressions(f: Expression => Expression): MergeNodePattern = copy(
    createNode = createNode.mapExpressions(f),
    onCreate = onCreate.map(_.mapExpressions(f)),
    onMatch = onMatch.map(_.mapExpressions(f))
  )

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = {
    onCreate.flatMap(_.getExpressionsWithPossiblePropertyReferences) ++
      onMatch.flatMap(_.getExpressionsWithPossiblePropertyReferences) ++
      createNode.properties
  }

  override def invalidatesCachedProperties: Boolean = onMatch.exists(_.invalidatesCachedProperties) ||
    onCreate.exists(_.invalidatesCachedProperties)

  override def createNodePatterns: Seq[CreateNode] = Seq(createNode)
  override def createRelationshipPatterns: Seq[CreateRelationship] = Seq.empty
  override def onMatchPatterns: Seq[SetMutatingPattern] = onMatch
  override def onCreatePatterns: Seq[SetMutatingPattern] = onCreate
}

case class MergeRelationshipPattern(
  createNodes: Seq[CreateNode],
  createRelationships: Seq[CreateRelationship],
  matchGraph: QueryGraph,
  onCreate: Seq[SetMutatingPattern],
  onMatch: Seq[SetMutatingPattern]
) extends MutatingPattern with MergePattern with HasMappableExpressions[MergeRelationshipPattern] {
  override def coveredIds: Set[LogicalVariable] = matchGraph.allCoveredIds

  override def dependencies: Set[LogicalVariable] =
    createNodes.flatMap(_.dependencies).toSet ++
      createRelationships.flatMap(_.dependencies).toSet ++
      matchGraph.dependencies ++
      onCreate.flatMap(_.dependencies) ++
      onMatch.flatMap(_.dependencies)

  override def mapExpressions(f: Expression => Expression): MergeRelationshipPattern = copy(
    createNodes = createNodes.map(_.mapExpressions(f)),
    createRelationships = createRelationships.map(_.mapExpressions(f)),
    onCreate = onCreate.map(_.mapExpressions(f)),
    onMatch = onMatch.map(_.mapExpressions(f))
  )

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] = {
    onCreate.flatMap(_.getExpressionsWithPossiblePropertyReferences) ++
      onMatch.flatMap(_.getExpressionsWithPossiblePropertyReferences) ++
      createNodes.flatMap(_.properties) ++
      createRelationships.flatMap(_.properties)
  }

  override def invalidatesCachedProperties: Boolean = onMatch.exists(_.invalidatesCachedProperties) ||
    onCreate.exists(_.invalidatesCachedProperties)

  override def createNodePatterns: Seq[CreateNode] = createNodes
  override def createRelationshipPatterns: Seq[CreateRelationship] = createRelationships
  override def onMatchPatterns: Seq[SetMutatingPattern] = onMatch
  override def onCreatePatterns: Seq[SetMutatingPattern] = onCreate
}

case class ForeachPattern(variable: LogicalVariable, expression: Expression, innerUpdates: SinglePlannerQuery)
    extends MutatingPattern with NoSymbols with HasMappableExpressions[ForeachPattern] {
  override def dependencies: Set[LogicalVariable] = expression.dependencies ++ innerUpdates.dependencies

  override def mapExpressions(f: Expression => Expression): ForeachPattern = copy(
    expression = f(expression),
    innerUpdates = innerUpdates.withQueryGraph(
      innerUpdates.queryGraph.withMutatingPattern(
        innerUpdates.queryGraph.mutatingPatterns.map(_.mapExpressions(f))
      )
    )
  )

  def getSimpleMutatingPatterns: collection.Seq[SimpleMutatingPattern] = {
    innerUpdates.allPlannerQueries.flatMap(_.queryGraph.mutatingPatterns) collect {
      case smp: SimpleMutatingPattern => smp
    }
  }

  override def getExpressionsWithPossiblePropertyReferences: Seq[Expression] =
    expression +: innerUpdates.queryGraph.mutatingPatterns.flatMap(_.getExpressionsWithPossiblePropertyReferences)

  override def invalidatesCachedProperties: Boolean =
    innerUpdates.allPlannerQueries.exists(
      _.queryGraph.mutatingPatterns.exists(_.invalidatesCachedProperties)
    )
}
