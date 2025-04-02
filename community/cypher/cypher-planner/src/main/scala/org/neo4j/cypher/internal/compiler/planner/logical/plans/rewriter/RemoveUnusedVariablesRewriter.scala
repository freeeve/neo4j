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
package org.neo4j.cypher.internal.compiler.planner.logical.plans.rewriter

import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.logical.plans.RelationshipLogicalLeafPlan
import org.neo4j.cypher.internal.util.Foldable.FoldableAny
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.topDown

/**
 * Removes variables that are declared but never used in the logical plan
 */
case object RemoveUnusedVariablesRewriter extends Rewriter {

  override def apply(value: AnyRef): AnyRef = {
    val variableUsage = value.folder.treeFold(Map.empty[LogicalVariable, Int]) {
      case v: LogicalVariable => acc =>
          TraverseChildren(acc.updatedWith(v) {
            case Some(v) => Some(v + 1)
            case None    => Some(1)
          })
    }

    def shouldRemove(maybeVariable: Option[LogicalVariable]): Boolean =
      maybeVariable match {
        case Some(v) => variableUsage(v) <= 1
        case None    => false
      }

    topDown(Rewriter.lift {
      case leaf: RelationshipLogicalLeafPlan if shouldRemove(leaf.leftNode) && shouldRemove(leaf.rightNode) =>
        leaf.withNewLeftAndRightNodes(None, None)
      case leaf: RelationshipLogicalLeafPlan if shouldRemove(leaf.leftNode) =>
        leaf.withNewLeftAndRightNodes(None, leaf.rightNode)
      case leaf: RelationshipLogicalLeafPlan if shouldRemove(leaf.rightNode) =>
        leaf.withNewLeftAndRightNodes(leaf.leftNode, None)
    })(value)
  }
}
