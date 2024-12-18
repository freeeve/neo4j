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
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.expressions.VariableGrouping
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.RepeatTrail
import org.neo4j.cypher.internal.logical.plans.RepeatWalk
import org.neo4j.cypher.internal.logical.plans.Selection
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.Rewriter.TopDownMergeableRewriter
import org.neo4j.cypher.internal.util.attribution.Attributes
import org.neo4j.cypher.internal.util.attribution.SameId
import org.neo4j.cypher.internal.util.topDown

/**
 * Pushes down predicates into Repeat. 
 * 
 * This is an optimization to prevent Repeat from yielding rows (which often requires allocations & data copy) that 
 * will be discarded immediately anyway.
 * 
 * Before
 * EXPLAIN MATCH (a)(()--())+(b:L) RETURN b
 * .produceResults(b)
 * .filter("b:L")
 * .repeatTrail("(a) (()--())+ (b)")
 * .|.expand("()--()")
 * .|.argument()
 * .allNodeScan("a")
 * .build()
 *
 * EXPLAIN MATCH (a)(()--())+(b:L) RETURN b
 * .produceResults(b)
 * .repeatTrail("(a) (()--())+ (b) WHERE b:L")
 * .|.expand("()--()")
 * .|.argument()
 * .allNodeScan("a")
 * .build()
 *
 * Should run after [[pruningVarExpander]] & [[TrailToVarExpandRewriter]] in order to rewrite as many (pruning) 
 * VarExpand as possible.
 */
// TODO check that endNode is a Variable, if it exists
// TODO later figure out how to rewrite LogicalVariable
// TODO split into pre & post predicates, and only push down pre
case class repeatEmitPredicateRewriter(attributes: Attributes[LogicalPlan]) extends Rewriter
    with TopDownMergeableRewriter {

  private def renameEnd(innerEnd: LogicalVariable, end: LogicalVariable, predicates: Ands): Ands = {
    val rewriter = topDown(Rewriter.lift {
      case v @ Variable(name) if name == end.name =>
        v.copy(name = innerEnd.name)(v.position, v.isIsolated)
    })
    predicates.endoRewrite(rewriter)
  }

  private def mergeEmitPredicates(prevEmitPredicates: Option[Ands], emitPredicates: Ands): Ands = {
    val newEmitPredicates = prevEmitPredicates match {
      case Some(predicates) =>
        Ands(emitPredicates.exprs ++ predicates.exprs)(emitPredicates.position)
      case None =>
        Ands(emitPredicates.exprs)(emitPredicates.position)
    }
    newEmitPredicates
  }

  private def isGroupVarInPredicate(
    predicates: Ands,
    nodeVariableGroupings: Set[VariableGrouping],
    relationshipVariableGroupings: Set[VariableGrouping]
  ) = {
    val groupVars = (nodeVariableGroupings ++ relationshipVariableGroupings).map(_.group.name)
    val groupVarIsInPredicate = predicates.dependencies.exists(v => groupVars.contains(v.name))
    groupVarIsInPredicate
  }

  override val innerRewriter: Rewriter = {
    Rewriter.lift {
      case s @ Selection(
          predicates,
          r @ RepeatTrail(
            _,
            _,
            _,
            _,
            end,
            _,
            innerEnd,
            nodeVariableGroupings,
            relationshipVariableGroupings,
            _,
            _,
            _,
            _,
            existingEmitPredicates
          )
        ) =>
        if (!isGroupVarInPredicate(predicates, nodeVariableGroupings, relationshipVariableGroupings)) {
          val rewrittenAnds = renameEnd(innerEnd, end, predicates)
          val newEmitPredicates = mergeEmitPredicates(existingEmitPredicates, rewrittenAnds)
          val id = attributes.copy(s.id).id()
          r.copy(emitPredicate = Some(newEmitPredicates))(SameId(id))
        } else {
          s
        }

      case s @ Selection(
          predicates,
          r @ RepeatWalk(
            _,
            _,
            _,
            _,
            end,
            _,
            innerEnd,
            nodeVariableGroupings,
            relationshipVariableGroupings,
            _,
            existingEmitPredicates
          )
        ) =>
        if (!isGroupVarInPredicate(predicates, nodeVariableGroupings, relationshipVariableGroupings)) {
          val rewrittenAnds = renameEnd(innerEnd, end, predicates)
          val newEmitPredicates = mergeEmitPredicates(existingEmitPredicates, rewrittenAnds)
          val id = attributes.copy(s.id).id()
          r.copy(emitPredicate = Some(newEmitPredicates))(SameId(id))
        } else {
          s
        }
    }
  }

  private val instance: Rewriter = topDown(innerRewriter)

  override def apply(input: AnyRef): AnyRef = instance.apply(input)
}
