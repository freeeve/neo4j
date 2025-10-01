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
package org.neo4j.cypher.internal.compiler.planner.logical

import org.neo4j.cypher.internal.expressions.CachedProperty
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertyKeyName
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.topDown

case class propertyRewriterBasedOnPreviouslyCachedProperty(context: LogicalPlanningContext) extends Rewriter {

  private val instance = topDown(
    Rewriter.lift {
      case prop @ Property(lv: LogicalVariable, pk: PropertyKeyName)
        if context.plannerState.previouslyCachedProperties.contains(lv, pk) =>
        val cachedPropsForVariable = context.plannerState.previouslyCachedProperties.entries(lv)
        CachedProperty(cachedPropsForVariable.originalEntity, lv, pk, cachedPropsForVariable.entityType)(prop.position)
    },
    cancellation = context.staticComponents.cancellationChecker
  )
  override def apply(that: AnyRef): AnyRef = instance(that)
}
