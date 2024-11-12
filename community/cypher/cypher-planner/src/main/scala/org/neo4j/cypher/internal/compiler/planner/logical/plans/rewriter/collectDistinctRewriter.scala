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
import org.neo4j.cypher.internal.expressions.ContainerIndex
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.FunctionName
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.functions.Collect
import org.neo4j.cypher.internal.expressions.functions.Head
import org.neo4j.cypher.internal.expressions.functions.Last
import org.neo4j.cypher.internal.logical.plans.Aggregation
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.OrderedAggregation
import org.neo4j.cypher.internal.logical.plans.Projection
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.attribution.SameId
import org.neo4j.cypher.internal.util.bottomUp

import scala.annotation.tailrec

case object collectDistinctRewriter extends Rewriter {

  override def apply(input: AnyRef): AnyRef = input match {
    case plan: LogicalPlan =>
      val unsafeVariables = randomAccessVariables(plan)
      bottomUp(innerRewriter(unsafeVariables)).apply(plan)
    case o => o
  }

  private def rewriteAggregations(
    unsafeVariables: Set[LogicalVariable],
    aggregationExpressions: Map[LogicalVariable, Expression]
  ) =
    aggregationExpressions.map {
      case (v, f @ FunctionInvocation(FunctionName(_, name), true, IndexedSeq(in), _, _))
        if !f.isOrdered && name.equalsIgnoreCase(Collect.name) && !unsafeVariables(v) =>
        v -> CollectDistinct(in)(f.position)
      case (v, e) => v -> e
    }

  private def innerRewriter(unsafeVariables: Set[LogicalVariable]): Rewriter = Rewriter.lift {
    case a @ Aggregation(_, _, aggregationExpressions) =>
      a.copy(aggregationExpressions = rewriteAggregations(unsafeVariables, aggregationExpressions))(SameId(a.id))

    case a @ OrderedAggregation(_, _, aggregationExpressions, _) =>
      a.copy(aggregationExpressions = rewriteAggregations(unsafeVariables, aggregationExpressions))(SameId(a.id))
  }

  private def randomAccessVariables(originalPlan: LogicalPlan) = {
    val res = originalPlan.folder.treeFold(Acc()) {
      case Projection(_, projections) =>
        val aliases = projections.collect {
          case (newVar, oldVar: LogicalVariable) => newVar -> oldVar
        }
        acc => TraverseChildren(acc.withAliases(aliases))

      case ContainerIndex(v: LogicalVariable, _) => acc => TraverseChildren(acc + v)
      case FunctionInvocation(FunctionName(_, name), _, IndexedSeq(v: LogicalVariable), _, _)
        if name.equalsIgnoreCase(Head.name) => acc => TraverseChildren(acc + v)
      case FunctionInvocation(FunctionName(_, name), _, IndexedSeq(v: LogicalVariable), _, _)
        if name.equalsIgnoreCase(Last.name) => acc => TraverseChildren(acc + v)
    }

    res.build
  }

  private case class Acc(
    unsafe: Set[LogicalVariable] = Set.empty,
    aliases: Map[LogicalVariable, LogicalVariable] = Map.empty
  ) {
    def +(v: LogicalVariable): Acc = copy(unsafe = unsafe + v)

    @tailrec
    private def flattenAliases(
      variable: LogicalVariable,
      acc: Set[LogicalVariable] = Set.empty
    ): Set[LogicalVariable] = {
      aliases.get(variable) match {
        case Some(v) => flattenAliases(v, acc + v)
        case None    => acc
      }
    }

    def build: Set[LogicalVariable] = {
      val variables = unsafe.flatMap(v => flattenAliases(v))
      val foo = unsafe ++ variables
      foo
    }

    def withAliases(newAliases: Map[LogicalVariable, LogicalVariable]): Acc = {
      if (newAliases.isEmpty) this
      else {
        copy(aliases = aliases ++ newAliases)
      }
    }
  }
}
