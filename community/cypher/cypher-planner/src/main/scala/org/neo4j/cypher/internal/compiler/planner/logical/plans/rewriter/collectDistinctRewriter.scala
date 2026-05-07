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

import org.neo4j.cypher.internal.expressions.CollectDistinct
import org.neo4j.cypher.internal.expressions.CollectDistinctIds
import org.neo4j.cypher.internal.expressions.ContainerIndex
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.In
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.functions.Collect
import org.neo4j.cypher.internal.expressions.functions.Id
import org.neo4j.cypher.internal.logical.plans.Aggregation
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.OrderedAggregation
import org.neo4j.cypher.internal.logical.plans.ProjectingPlan
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.FunctionName
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.attribution.SameId
import org.neo4j.cypher.internal.util.bottomUp

import scala.annotation.tailrec

case object collectDistinctRewriter extends Rewriter {

  override def apply(input: AnyRef): AnyRef = input match {
    case plan: LogicalPlan =>
      val rewriteConstraints = getRewriteConstraints(plan)
      bottomUp(innerRewriter(rewriteConstraints)).apply(plan)
    case o => o
  }

  private def rewriteAggregations(
    rewriteConstraints: RewriteConstraints,
    aggregationExpressions: Map[LogicalVariable, Expression]
  ) =
    aggregationExpressions.map {
      case (v, f @ CollectFunction(arg @ Id(_), true, false))
        if !rewriteConstraints.implicitlyDistinctVariables(v) && !rewriteConstraints.unsafeVariables(v) =>
        v -> CollectDistinctIds(arg)(f.position)
      case (v, f @ CollectFunction(in, true, isOrdered)) if !rewriteConstraints.unsafeVariables(v) =>
        v -> CollectDistinct(in, isOrdered)(f.position)
      case (v, f @ CollectFunction(in, false, isOrdered)) if rewriteConstraints.implicitlyDistinctVariables(v) =>
        v -> CollectDistinct(in, isOrdered)(f.position)

      case (v, e) => v -> e
    }

  private def innerRewriter(rewriteConstraints: RewriteConstraints): Rewriter =
    Rewriter.lift {
      case a @ Aggregation(_, _, aggregationExpressions) =>
        a.copy(aggregationExpressions = rewriteAggregations(rewriteConstraints, aggregationExpressions))(
          SameId(a.id)
        )

      case a @ OrderedAggregation(_, _, aggregationExpressions, _) =>
        a.copy(aggregationExpressions = rewriteAggregations(rewriteConstraints, aggregationExpressions))(
          SameId(a.id)
        )
    }

  private case class RewriteConstraints(
    unsafeVariables: Set[LogicalVariable],
    implicitlyDistinctVariables: Set[LogicalVariable]
  )

  private def getRewriteConstraints(originalPlan: LogicalPlan): RewriteConstraints = {
    val res = originalPlan.folder.treeFold(Acc()) {
      case p: ProjectingPlan =>
        val aliases = p.projectExpressions.collect {
          case (newVar, oldVar: LogicalVariable) => newVar -> oldVar
        }
        acc => TraverseChildren(acc.withAliases(aliases))

      case ContainerIndex(v: LogicalVariable, _) => acc => SkipChildren(acc.addUnsafe(v))

      case In(_, list: LogicalVariable) => acc => SkipChildren(acc.addIn(list))

      case v: LogicalVariable => acc => SkipChildren(acc.add(v))
    }

    res.build
  }

  private case class Acc(
    unsafe: Set[LogicalVariable] = Set.empty,
    in: Set[LogicalVariable] = Set.empty,
    all: Map[LogicalVariable, Int] = Map.empty,
    aliases: Map[LogicalVariable, LogicalVariable] = Map.empty
  ) {
    def addUnsafe(v: LogicalVariable): Acc = copy(unsafe = unsafe + v)

    def addIn(v: LogicalVariable): Acc = copy(in = in + v)

    def add(v: LogicalVariable): Acc = {
      copy(all = all.updatedWith(v) {
        case None    => Some(1)
        case Some(n) => Some(n + 1)
      })
    }

    @tailrec
    private def flattenAliases(
      variable: LogicalVariable,
      acc: Set[LogicalVariable] = Set.empty
    ): Set[LogicalVariable] = {
      aliases.get(variable) match {
        case None => acc
        case Some(v) =>
          val newAcc = acc + v
          if (v eq variable)
            newAcc
          else
            flattenAliases(v, newAcc)
      }
    }

    def build: RewriteConstraints = {
      val variables = unsafe.flatMap(v => flattenAliases(v))
      // is only safe to rewrite non-DISTINCT collected list if its only usage is where it was introduced (aggregation)
      // if variable is used anywhere else we assume it is not safe to rewrite
      val onlyIn = in -- all.filter { case (_, n) => n > 1 }.keySet
      RewriteConstraints(unsafeVariables = unsafe ++ variables, implicitlyDistinctVariables = onlyIn)
    }

    def withAliases(newAliases: Map[LogicalVariable, LogicalVariable]): Acc = {
      if (newAliases.isEmpty) this
      else {
        copy(aliases = aliases ++ newAliases)
      }
    }
  }

  object CollectFunction {

    def unapply(arg: Expression): Option[(Expression, Boolean, Boolean)] = {
      arg match {
        case f @ FunctionInvocation(FunctionName(_, name), distinct, IndexedSeq(in), _, _, _, _)
          if name.equalsIgnoreCase(Collect.name) =>
          Some((in, distinct, f.isOrdered))
        case _ => None
      }
    }
  }
}
