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
import org.neo4j.cypher.internal.logical.plans.Expand.ExpandInto
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.Repeat
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
 * .repeatTrail("(a) (()--())+ (a)")
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

  override val innerRewriter: Rewriter = {
    Rewriter.lift {
      case IsRepeatTrailIntoRewritable(selection, repeat, intoVariable) =>
        val id = attributes.copy(selection.id).id()
        repeat.copy(end = intoVariable, mode = ExpandInto)(SameId(id))

      case IsRepeatWalkIntoRewritable(selection, repeat, intoVariable) =>
        val id = attributes.copy(selection.id).id()
        repeat.copy(end = intoVariable, mode = ExpandInto)(SameId(id))
    }
  }

  private val instance: Rewriter = topDown(innerRewriter)

  override def apply(input: AnyRef): AnyRef = instance.apply(input)

  private object IsRepeatTrailIntoRewritable {

    def unapply(plan: LogicalPlan): Option[(Selection, RepeatTrail, LogicalVariable)] = {
      plan match {
        case s @ Selection(IsOnlyEquality(lhs, rhs), r: RepeatTrail) => returnOtherNode(s, lhs, rhs, r)
        case _                                                       => None
      }
    }
  }

  private object IsRepeatWalkIntoRewritable {

    def unapply(plan: LogicalPlan): Option[(Selection, RepeatWalk, LogicalVariable)] = {
      plan match {
        case s @ Selection(IsOnlyEquality(lhs, rhs), r: RepeatWalk) => returnOtherNode(s, lhs, rhs, r)
        case _                                                      => None
      }
    }
  }

  private def returnOtherNode[REPEAT <: Repeat](
    s: Selection,
    lhs: LogicalVariable,
    rhs: LogicalVariable,
    r: REPEAT
  ): Option[(Selection, REPEAT, LogicalVariable)] = {
    r.end match {
      case `lhs` => Some((s, r, rhs))
      case `rhs` => Some((s, r, lhs))
      case _     => None
    }
  }

  private object IsOnlyEquality {

    def unapply(ands: Ands): Option[(LogicalVariable, LogicalVariable)] = {
      ands match {
        case Ands(Singleton(Equals(lhs: LogicalVariable, rhs: LogicalVariable))) => Some((lhs, rhs))
        case _                                                                   => None
      }
    }
  }
}
