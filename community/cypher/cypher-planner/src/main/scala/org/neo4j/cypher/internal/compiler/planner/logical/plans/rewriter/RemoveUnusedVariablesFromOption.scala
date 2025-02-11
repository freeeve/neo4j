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
import org.neo4j.cypher.internal.logical.plans.LogicalLeafPlan
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.Optional
import org.neo4j.cypher.internal.util.Foldable.FoldableAny
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.Ref
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.attribution.SameId
import org.neo4j.cypher.internal.util.topDown

/**
 * A rewriter to ensure that unused variables are not propagagted further through argument and optionals.
 * This rewriter should run before removeUnusedVariableGroupings, so that repeatTrails that precede apply/optionals/arguments may be rewritten to varExpands.
 *
 * The rewriter traverses the plan top-down and siblings from right-to-left to guarantee that variables are tracked in the inverse order of execution.
 * For example, the variables introduced in the LHS of an Apply maybe used in the RHS. So traversing from right-to-left ensures that we find a variable usage first.
 * While right-to-left order doesn't matter for joins and cartesian products, this rewriter will still not remove variables on the LHS if they are used in the RHS there.
 */
case object RemoveUnusedVariablesFromOption extends Rewriter {

  override def apply(plan: AnyRef): AnyRef = {
    val replacementPlans = findReplancementPlans(plan)
    topDown(
      Rewriter.lift {
        case o: Optional                      => replacementPlans.getOrElse(Ref(o), o)
        case logicalLeafPlan: LogicalLeafPlan => replacementPlans.getOrElse(Ref(logicalLeafPlan), logicalLeafPlan)
      }
    )(plan)
  }

  private def findReplancementPlans(plan: AnyRef): Map[Ref[LogicalPlan], LogicalPlan] = {
    // Explore plans from right-to-left to ensure usage is tracked in inverse-order of execution.
    plan.folder.reverseTreeFold(Acc.empty) {
      case o: Optional => acc =>
          TraverseChildren(acc.copy(
            replacementPlans = acc.replacementPlans + (Ref(o) -> o.copy(protectedSymbols =
              o.protectedSymbols.intersect(acc.usedVariables)
            )(SameId(o.id)))
          ))
      case leafPlan: LogicalLeafPlan => acc => {
          val usedVariables = acc.usedVariables ++ variablesUsed(
            leafPlan.removeArgumentIds()
          ) // ignore argumentIds when checking for variables used.
          val replacementPlans =
            acc.replacementPlans + (Ref(leafPlan) -> leafPlan.withoutArgumentIds(
              leafPlan.argumentIds.diff(usedVariables)
            ))
          TraverseChildren(Acc(replacementPlans, usedVariables))
        }
      case p: LogicalPlan => acc => TraverseChildren(acc.copy(usedVariables = acc.usedVariables ++ variablesUsed(p)))
    }.replacementPlans
  }

  private case class Acc(replacementPlans: Map[Ref[LogicalPlan], LogicalPlan], usedVariables: Set[LogicalVariable])

  private object Acc {
    val empty: Acc = Acc(Map.empty, Set.empty)
  }

  private def variablesUsed(p: LogicalPlan): Set[LogicalVariable] = p.folder.treeFold(Set.empty[LogicalVariable]) {
    case v: LogicalVariable => acc => SkipChildren(acc + v)
    case childPlan: LogicalPlan if childPlan.id != p.id =>
      SkipChildren(_) // There is no need to explore child plans here, since the rewriter that invokes this treeFolder will explore them.
  }
}
