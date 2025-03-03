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

import org.neo4j.cypher.internal.expressions.Ands
import org.neo4j.cypher.internal.expressions.Equals
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.Repeat.EndNodePredicates
import org.neo4j.cypher.internal.logical.plans.RepeatTrail
import org.neo4j.cypher.internal.logical.plans.RepeatWalk
import org.neo4j.cypher.internal.logical.plans.Selection
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.Rewriter.TopDownMergeableRewriter
import org.neo4j.cypher.internal.util.attribution.Attributes
import org.neo4j.cypher.internal.util.attribution.SameId
import org.neo4j.cypher.internal.util.collection.immutable.ListSet.Singleton
import org.neo4j.cypher.internal.util.topDown

/**
 * Pushes down predicates into Repeat. 
 * 
 * This is an optimization to prevent Repeat from yielding rows (which often requires allocations & data copy) that 
 * will be discarded immediately anyway.
 * 
 * Before
 * EXPLAIN MATCH (a)(()--())+(b) WHERE b=a RETURN b
 * .produceResults(b)
 * .filter("b=a")
 * .repeatTrail("(a) (()--())+ (b)")
 * .|.expand("()--()")
 * .|.argument()
 * .allNodeScan("a")
 * .build()
 *
 * EXPLAIN MATCH (a)(()--())+(b) WHERE b=a RETURN b
 * .produceResults(b)
 * .repeatTrail("(a) (()--())+ (b) WHERE b=a")
 * .|.expand("()--()")
 * .|.argument()
 * .allNodeScan("a")
 * .build()
 *
 * Should run after [[pruningVarExpander]] & [[TrailToVarExpandRewriter]] in order to rewrite as many (pruning) 
 * VarExpand as possible.
 */
case class repeatTrailAndWalkEndNodePredicateRewriter(attributes: Attributes[LogicalPlan]) extends Rewriter
    with TopDownMergeableRewriter {

  /**
   * For the predicate that is pushed down into [[Repeat]] it is necessary to rename [[end]] to [[innerEnd]],
   * this is because [[end]] is only projected into rows that are emitted by [[Repeat]].
   * Rows moving through operators on the RHS of [[Repeat]] have end node stored in the [[innerEnd]] variable.
   */
  private def renameEnd(innerEnd: LogicalVariable, end: LogicalVariable, predicates: Ands): Ands = {
    val rewriter = topDown(Rewriter.lift {
      case v @ Variable(name) if name == end.name =>
        v.copy(name = innerEnd.name)(v.position, v.isIsolated)
    })
    predicates.endoRewrite(rewriter)
  }

  private def mergeEndNodePredicates(
    prevEndNodePredicates: Option[EndNodePredicates],
    endNodePredicates: Ands,
    pushedDownEndNodePredicates: Ands
  ): EndNodePredicates = {
    val newEndNodePredicates = prevEndNodePredicates match {
      case Some(predicates) =>
        val mergedEndNodePredicates =
          Ands(endNodePredicates.exprs ++ predicates.zeroRepetition.exprs)(pushedDownEndNodePredicates.position)
        val mergedPushedDownEndNodePredicates = Ands(
          pushedDownEndNodePredicates.exprs ++ predicates.otherRepetitions.exprs
        )(pushedDownEndNodePredicates.position)
        EndNodePredicates(mergedEndNodePredicates, mergedPushedDownEndNodePredicates)
      case None =>
        EndNodePredicates(endNodePredicates, pushedDownEndNodePredicates)
    }
    newEndNodePredicates
  }

  private def isRewritable(
    predicates: Ands,
    endVariableName: String
  ): Boolean = {

    /**
     * NOTE: in the previous commit more predicates were allowed, including label & property predicates,
     * but that affected eagerness analysis and at the time of writing the query most affected by this
     * optimization (uk_railway Q1b) only requires node equality.
     * 
     * Support for more predicates is left as an exercise for the reader. 
     */
    isOnlyEndNodeEquality(endVariableName, predicates)
  }

  private def isOnlyEndNodeEquality(endVariableName: String, predicates: Ands): Boolean = {
    predicates match {
      case Ands(Singleton(Equals(Variable(lhs), Variable(rhs)))) => lhs == endVariableName || rhs == endVariableName
      case _                                                     => false
    }
  }

  override val innerRewriter: Rewriter = {
    Rewriter.lift {
      case s @ Selection(predicates, r: RepeatTrail) if isRewritable(predicates, r.end.name) =>
        val rewrittenPredicates = renameEnd(r.innerEnd, r.end, predicates)
        val newEndNodePredicates = mergeEndNodePredicates(r.endNodePredicate, predicates, rewrittenPredicates)
        val id = attributes.copy(s.id).id()
        r.copy(endNodePredicate = Some(newEndNodePredicates))(SameId(id))

      case s @ Selection(predicates, r: RepeatWalk) if isRewritable(predicates, r.end.name) =>
        val rewrittenPredicates = renameEnd(r.innerEnd, r.end, predicates)
        val newEndNodePredicates = mergeEndNodePredicates(r.endNodePredicate, predicates, rewrittenPredicates)
        val id = attributes.copy(s.id).id()
        r.copy(endNodePredicate = Some(newEndNodePredicates))(SameId(id))
    }
  }

  private val instance: Rewriter = topDown(innerRewriter)

  override def apply(input: AnyRef): AnyRef = instance.apply(input)
}
