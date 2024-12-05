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

import org.neo4j.cypher.internal.ir.EagernessReason
import org.neo4j.cypher.internal.ir.SinglePlannerQuery
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.util.collection.immutable.ListSet

object EagerAnalyzer {

  def apply(context: LogicalPlanningContext): EagerAnalyzer = {
    if (!context.staticComponents.readOnly && context.settings.updateStrategy.alwaysEager) {
      NoopEagerAnalyzer//new AlwaysEagerEagerAnalyzer(context)
    } else {
      NoopEagerAnalyzer
    }
  }
}

trait EagerAnalyzer {
  def headReadWriteEagerize(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan
  def tailReadWriteEagerizeNonRecursive(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan
  def tailReadWriteEagerizeRecursive(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan
  def writeReadEagerize(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan
  def horizonEagerize(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan
}

/**
 * Always plans Eager.
 */
class AlwaysEagerEagerAnalyzer(context: LogicalPlanningContext) extends EagerAnalyzer {

  private def planEager(inputPlan: LogicalPlan): LogicalPlan = context.staticComponents.logicalPlanProducer.planEager(
    inputPlan,
    context,
    ListSet(EagernessReason.UpdateStrategyEager)
  )

  override def headReadWriteEagerize(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan =
    planEager(inputPlan)

  override def tailReadWriteEagerizeNonRecursive(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan =
    planEager(inputPlan)

  override def tailReadWriteEagerizeRecursive(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan =
    planEager(inputPlan)

  override def writeReadEagerize(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan =
    planEager(inputPlan)

  override def horizonEagerize(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan =
    planEager(inputPlan)
}

object NoopEagerAnalyzer extends EagerAnalyzer {
  override def headReadWriteEagerize(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan = inputPlan

  override def tailReadWriteEagerizeNonRecursive(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan =
    inputPlan

  override def tailReadWriteEagerizeRecursive(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan =
    inputPlan
  override def writeReadEagerize(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan = inputPlan

  override def horizonEagerize(inputPlan: LogicalPlan, query: SinglePlannerQuery): LogicalPlan = inputPlan
}
