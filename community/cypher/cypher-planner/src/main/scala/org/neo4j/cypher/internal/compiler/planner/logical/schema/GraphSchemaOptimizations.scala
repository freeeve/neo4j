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

import org.neo4j.cypher.internal.ast.semantics.SemanticTable
import org.neo4j.cypher.internal.compiler.planner.logical.Metrics.LabelInfo
import org.neo4j.cypher.internal.expressions.LabelName
import org.neo4j.cypher.internal.planner.spi.PlanContext

sealed trait GraphSchemaOptimizations {

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

  def fromConfig(enabled: Boolean, planContext: PlanContext, semanticTable: SemanticTable): GraphSchemaOptimizations = {
    if (!enabled) Disabled
    else new Enabled(planContext, semanticTable.resolvedLabelNames.keySet)
  }

  case object Disabled extends GraphSchemaOptimizations {
    override def pruneImpliedLabels(labelInfo: LabelInfo): LabelInfo = labelInfo

    override def isLabelImplied(labelToCheck: LabelName, knownLabels: Set[LabelName]): Boolean = false
  }

  final class Enabled(planContext: PlanContext, resolvedLabels: Set[String]) extends GraphSchemaOptimizations {
    private val ilm: ImpliedLabelsMap = ImpliedLabelsMap.forLabels(resolvedLabels, planContext.getNodeLabelConstraints)

    override def pruneImpliedLabels(labelInfo: LabelInfo): LabelInfo = {
      labelInfo.view.mapValues { labels =>
        val prunedNames = ilm.pruneImpliedLabels(labels.map(_.name))
        labels.filter(l => prunedNames.contains(l.name))
      }.toMap
    }

    override def isLabelImplied(labelToCheck: LabelName, knownLabels: Set[LabelName]): Boolean = {
      knownLabels.exists { knownLabel =>
        ilm.labelToImpliedLabels.get(knownLabel.name).exists(_.contains(labelToCheck.name))
      }
    }
  }
}
