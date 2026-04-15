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
package org.neo4j.cypher.internal.physicalplanning.ast.prettifier

import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.expressions
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalProperty
import org.neo4j.cypher.internal.expressions.functions.ElementId
import org.neo4j.cypher.internal.expressions.functions.Labels
import org.neo4j.cypher.internal.expressions.functions.Type
import org.neo4j.cypher.internal.physicalplanning.SlotConfiguration
import org.neo4j.cypher.internal.physicalplanning.SlotConfiguration.KeyedSlot
import org.neo4j.cypher.internal.physicalplanning.SlotConfiguration.VariableSlotKey
import org.neo4j.cypher.internal.physicalplanning.ast.HasALabelFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.HasAnyLabelFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.HasLabelsFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.HasTypesFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.IsPrimitiveNull
import org.neo4j.cypher.internal.physicalplanning.ast.LabelsFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.NodeElementIdFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.NodeFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.NodeProperty
import org.neo4j.cypher.internal.physicalplanning.ast.NodePropertyExists
import org.neo4j.cypher.internal.physicalplanning.ast.NodePropertyExistsLate
import org.neo4j.cypher.internal.physicalplanning.ast.NodePropertyLate
import org.neo4j.cypher.internal.physicalplanning.ast.NullCheck
import org.neo4j.cypher.internal.physicalplanning.ast.NullCheckProperty
import org.neo4j.cypher.internal.physicalplanning.ast.NullCheckReferenceProperty
import org.neo4j.cypher.internal.physicalplanning.ast.NullCheckVariable
import org.neo4j.cypher.internal.physicalplanning.ast.PrimitiveAnds
import org.neo4j.cypher.internal.physicalplanning.ast.PrimitiveEquals
import org.neo4j.cypher.internal.physicalplanning.ast.PrimitiveNotEquals
import org.neo4j.cypher.internal.physicalplanning.ast.ReferenceFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.RelationshipElementIdFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.RelationshipFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.RelationshipProperty
import org.neo4j.cypher.internal.physicalplanning.ast.RelationshipPropertyExists
import org.neo4j.cypher.internal.physicalplanning.ast.RelationshipPropertyExistsLate
import org.neo4j.cypher.internal.physicalplanning.ast.RelationshipPropertyLate
import org.neo4j.cypher.internal.physicalplanning.ast.RelationshipTypeFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.SlottedCachedProperty
import org.neo4j.cypher.internal.physicalplanning.ast.prettifier.RuntimeExpressionStringifier.nameFromLongSlot
import org.neo4j.cypher.internal.physicalplanning.ast.prettifier.RuntimeExpressionStringifier.nameFromRefSlot
import org.neo4j.cypher.internal.planner.spi.ReadTokenContext
import org.neo4j.cypher.internal.runtime.ast.ExpressionVariable
import org.neo4j.cypher.internal.runtime.ast.ParameterFromSlot
import org.neo4j.cypher.internal.runtime.ast.RuntimeConstant
import org.neo4j.cypher.internal.runtime.ast.VariableRef
import org.neo4j.cypher.internal.util.InputPosition

case class RuntimeExpressionStringifier(tokenContext: ReadTokenContext, slots: SlotConfiguration)
    extends ExpressionStringifier.Extension {

  override def apply(ctx: ExpressionStringifier)(expression: Expression): String = expression match {
    case e: SlottedCachedProperty =>
      backtickPropertyAccess(ctx, e)
    case e: ParameterFromSlot => s"$$${ctx.backtick(e.name)}"
    case e @ (_: NodeProperty | _: NodePropertyLate | _: RelationshipProperty | _: RelationshipPropertyLate) =>
      backtickPropertyAccess(ctx, e.asInstanceOf[LogicalProperty])
    case e @ (_: NodePropertyExists | _: NodePropertyExistsLate | _: RelationshipPropertyExists | _: RelationshipPropertyExistsLate) =>
      backtickPropertyAccess(ctx, e.asInstanceOf[LogicalProperty]) + " IS NOT NULL"
    case l: HasALabelFromSlot => nameFromLongSlot(slots, l.offset, ctx) + ":%"
    case l: HasLabelsFromSlot =>
      val labels = l.resolvedLabelTokens.map(tokenContext.getLabelName) ++ l.lateLabels
      labelPredicates(labels, nameFromLongSlot(slots, l.offset, ctx), " AND ")
    case l: HasAnyLabelFromSlot =>
      val labels = l.resolvedLabelTokens.map(tokenContext.getLabelName) ++ l.lateLabels
      labelPredicates(labels, nameFromLongSlot(slots, l.offset, ctx), " OR ")
    case l: HasTypesFromSlot =>
      val labels = l.resolvedTypeTokens.map(tokenContext.getRelTypeName) ++ l.lateTypes
      labelPredicates(labels, nameFromLongSlot(slots, l.offset, ctx), " AND ")
    case l: LabelsFromSlot =>
      val varName = expressions.Variable(nameFromLongSlot(slots, l.offset, ctx))(InputPosition.NONE, isIsolated = false)
      Labels.asInvocation(varName)(InputPosition.NONE).asCanonicalStringVal
    case r: RelationshipTypeFromSlot =>
      val varName = expressions.Variable(nameFromLongSlot(slots, r.offset, ctx))(InputPosition.NONE, isIsolated = false)
      Type.asInvocation(varName)(InputPosition.NONE).asCanonicalStringVal
    case e: PrimitiveEquals => nameFromLongSlot(slots, e.offset1, ctx) + " = " + nameFromLongSlot(slots, e.offset2, ctx)
    case e: PrimitiveNotEquals =>
      "NOT " + nameFromLongSlot(slots, e.offset1, ctx) + " = " + nameFromLongSlot(slots, e.offset2, ctx)
    case e: PrimitiveAnds              => e.predicates.map(p => ctx.apply(p)).mkString(" AND ")
    case e: NodeFromSlot               => nameFromLongSlot(slots, e.offset, ctx)
    case e: RelationshipFromSlot       => nameFromLongSlot(slots, e.offset, ctx)
    case e: ReferenceFromSlot          => nameFromRefSlot(slots, e.offset, ctx)
    case e: IsPrimitiveNull            => nameFromLongSlot(slots, e.offset, ctx) + " IS NULL"
    case e: RuntimeConstant            => ctx.apply(e.inner)
    case e: NullCheck                  => ctx.apply(e.inner)
    case e: NullCheckVariable          => ctx.apply(e.inner)
    case e: NullCheckProperty          => ctx.apply(e.inner)
    case e: NullCheckReferenceProperty => ctx.apply(e.inner)
    case e: NodeElementIdFromSlot =>
      val varName = expressions.Variable(nameFromLongSlot(slots, e.offset, ctx))(InputPosition.NONE, isIsolated = false)
      ElementId.asInvocation(varName)(InputPosition.NONE).asCanonicalStringVal
    case e: RelationshipElementIdFromSlot =>
      val varName = expressions.Variable(nameFromLongSlot(slots, e.offset, ctx))(InputPosition.NONE, isIsolated = false)
      ElementId.asInvocation(varName)(InputPosition.NONE).asCanonicalStringVal
    case e: VariableRef        => nameFromSlotOrAlias(e.variableName, ctx)
    case e: ExpressionVariable => nameFromSlotOrAlias(e.name, ctx)
    case e                     => throw new UnsupportedOperationException(s"Don't know how to stringify $e")
  }

  private def backtickPropertyAccess(ctx: ExpressionStringifier, ee: LogicalProperty) = {
    val name = nameFromSlotOrAlias(ee.map.asCanonicalStringVal, ctx)
    name + "." + ctx.backtick(ee.propertyKey.name)
  }

  private def labelPredicates(labels: Seq[String], predicateVariable: String, separator: String): String = {
    val predicates = labels.map(label =>
      s"$predicateVariable:$label"
    )
    predicates match {
      case x if x.size == 1 => x.head
      case x => x.mkString(
          "(",
          separator,
          ")"
        )
    }
  }

  def nameFromSlotOrAlias(variableName: String, ctx: ExpressionStringifier): String = {
    slots(variableName) match {
      case KeyedSlot(VariableSlotKey(name), _, _) => ctx.backtick(name)
      case KeyedSlot(key, _, _) =>
        throw new IllegalStateException(s"Expected a VariableSlotKey for `$variableName` but found $key")
    }
  }
}

object RuntimeExpressionStringifier {

  def nameFromLongSlot(slots: SlotConfiguration, offset: Int, ctx: ExpressionStringifier): String = {
    slots.nameOfSlot(offset, longSlot = true) match {
      case Some(value) => ctx.backtick(value)
      case None        => throw new IllegalArgumentException(s"No LongSlot with offset $offset.")
    }
  }

  def nameFromRefSlot(slots: SlotConfiguration, offset: Int, ctx: ExpressionStringifier): String = {
    slots.nameOfSlot(offset, longSlot = false) match {
      case Some(value) => ctx.backtick(value)
      case None        => throw new IllegalArgumentException(s"No RefSlot with offset $offset.")
    }
  }
}
