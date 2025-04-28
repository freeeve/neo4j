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
package org.neo4j.cypher.internal.compiler.planner.logical.schema

import org.neo4j.cypher.internal.compiler.planner.logical.Metrics.LabelInfo
import org.neo4j.cypher.internal.expressions.LabelName
import org.neo4j.cypher.internal.ir.helpers.CachedFunction
import org.neo4j.cypher.internal.planner.spi.PlanContext
import org.neo4j.cypher.internal.util.InputPosition

sealed trait GraphSchemaOptimizations {

  /**
   * Removes labels that are implied by other labels, ensuring that only non-redundant labels remain.
   */
  def pruneImpliedLabels(labels: Set[LabelName]): Set[LabelName]

  /**
   * Removes labels from the given LabelInfo that are implied by other labels, ensuring that
   * only non-redundant labels remain.
   */
  def pruneImpliedLabels(labelInfo: LabelInfo): LabelInfo

  /**
   * Returns true if the presence of `labelToCheck` is implied by one or more of the `knownLabels`.
   */
  def isLabelImplied(labelToCheck: LabelName, knownLabels: Set[LabelName]): Boolean
}

object GraphSchemaOptimizations {

  def fromConfig(enabled: Boolean, planContext: PlanContext): GraphSchemaOptimizations = {
    if (!enabled) Disabled
    else new Enabled(planContext)
  }

  case object Disabled extends GraphSchemaOptimizations {
    override def pruneImpliedLabels(labels: Set[LabelName]): Set[LabelName] = labels

    override def pruneImpliedLabels(labelInfo: LabelInfo): LabelInfo = labelInfo

    override def isLabelImplied(labelToCheck: LabelName, knownLabels: Set[LabelName]): Boolean = false
  }

  final class Enabled(planContext: PlanContext) extends GraphSchemaOptimizations {

    private val impliedLabels: (LabelName => Set[LabelName]) = CachedFunction {
      (labelName: LabelName) =>
        planContext.getNodeLabelConstraints(labelName.name)
          .map(strLabel => LabelName(strLabel)(InputPosition.NONE))
    }

    override def pruneImpliedLabels(labels: Set[LabelName]): Set[LabelName] = {
      labels.foldLeft(labels) {
        (labelsToKeep, label) => labelsToKeep.diff(impliedLabels(label))
      }
    }

    override def pruneImpliedLabels(labelInfo: LabelInfo): LabelInfo = {
      labelInfo.view.mapValues(pruneImpliedLabels).toMap
    }

    override def isLabelImplied(labelToCheck: LabelName, knownLabels: Set[LabelName]): Boolean = {
      knownLabels.exists { knownLabel =>
        impliedLabels(knownLabel).contains(labelToCheck)
      }
    }
  }
}
