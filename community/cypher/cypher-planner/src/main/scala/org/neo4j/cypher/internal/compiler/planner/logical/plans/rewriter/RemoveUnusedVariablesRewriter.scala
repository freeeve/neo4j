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
import org.neo4j.cypher.internal.logical.plans.BFSPruningVarExpand
import org.neo4j.cypher.internal.logical.plans.Expand
import org.neo4j.cypher.internal.logical.plans.OptionalExpand
import org.neo4j.cypher.internal.logical.plans.PruningVarExpand
import org.neo4j.cypher.internal.logical.plans.RelationshipLogicalLeafPlan
import org.neo4j.cypher.internal.logical.plans.UnwindCollection
import org.neo4j.cypher.internal.logical.plans.VarExpand
import org.neo4j.cypher.internal.util.Foldable.FoldableAny
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.attribution.SameId
import org.neo4j.cypher.internal.util.topDown

/**
 * Removes variables that are declared but never used in the logical plan
 */
case object RemoveUnusedVariablesRewriter extends Rewriter {

  override def apply(value: AnyRef): AnyRef = {
    lazy val variableUsage = value.folder.treeFold(Map.empty[LogicalVariable, Int]) {
      case v: LogicalVariable => acc =>
          TraverseChildren(acc.updatedWith(v) {
            case Some(v) => Some(v + 1)
            case None    => Some(1)
          })
    }

    def remove(maybeVariable: Option[LogicalVariable]): Option[LogicalVariable] =
      maybeVariable match {
        case Some(v) if variableUsage(v) > 1 => maybeVariable
        case _                               => None
      }

    topDown(Rewriter.lift {
      case leaf: RelationshipLogicalLeafPlan =>
        leaf.updateVariables(
          idName = remove(leaf.idName),
          leftNode = remove(leaf.leftNode),
          rightNode = remove(leaf.rightNode)
        )
      case expand: Expand =>
        expand.copy(maybeTo = remove(expand.maybeTo), maybeRelName = remove(expand.maybeRelName))(SameId(expand.id))
      case expand: OptionalExpand =>
        expand.copy(maybeTo = remove(expand.maybeTo), maybeRelName = remove(expand.maybeRelName))(SameId(expand.id))
      case expand: VarExpand =>
        expand.copy(maybeTo = remove(expand.maybeTo), maybeRelName = remove(expand.maybeRelName))(SameId(expand.id))
      case expand: BFSPruningVarExpand =>
        expand.copy(maybeTo = remove(expand.maybeTo))(SameId(expand.id))
      case expand: PruningVarExpand =>
        expand.copy(maybeTo = remove(expand.maybeTo))(SameId(expand.id))
      case unwind: UnwindCollection =>
        unwind.copy(maybeVariable = remove(unwind.maybeVariable))(SameId(unwind.id))

    })(value)
  }
}
