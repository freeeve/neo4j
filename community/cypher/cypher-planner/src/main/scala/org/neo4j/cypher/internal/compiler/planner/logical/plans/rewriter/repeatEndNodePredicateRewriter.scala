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

import org.neo4j.cypher.internal.expressions.ASTCachedProperty
import org.neo4j.cypher.internal.expressions.Ands
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.PathExpression
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
case class repeatEndNodePredicateRewriter(attributes: Attributes[LogicalPlan]) extends Rewriter
    with TopDownMergeableRewriter {

  private def renameEnd(innerEnd: LogicalVariable, end: LogicalVariable, predicates: Ands): Ands = {
    val rewriter = topDown(Rewriter.lift {
      case v @ Variable(name) if name == end.name =>
        v.copy(name = innerEnd.name)(v.position, v.isIsolated)
    })
    predicates.endoRewrite(rewriter)
  }

  private def mergeEndNodePredicates(prevEndNodePredicates: Option[Ands], endNodePredicates: Ands): Ands = {
    val newEndNodePredicates = prevEndNodePredicates match {
      case Some(predicates) =>
        Ands(endNodePredicates.exprs ++ predicates.exprs)(endNodePredicates.position)
      case None =>
        Ands(endNodePredicates.exprs)(endNodePredicates.position)
    }
    newEndNodePredicates
  }

  private def isRewritable(
    predicates: Ands,
    nodeVariableGroupings: Set[VariableGrouping],
    relationshipVariableGroupings: Set[VariableGrouping],
    endVariableName: String
  ): Boolean = {
    // is not rewritable if predicate contains any of the following: group variable, path, cached property
    val groupVars = (nodeVariableGroupings ++ relationshipVariableGroupings).map(_.group.name)
    val notRewritable = predicates.contains {
      // path is not available before Repeat yields output row
      case _: PathExpression => true
      // repeat reuses output rows for next repetition rows so cached properties are not safe to evaluate,
      // unless the runtime took special care to invalidate them on the RHS of Repeat
      case _: ASTCachedProperty => true
      // group variables are not available before Repeat yields output row
      case v: LogicalVariable if groupVars.contains(v.name) => true
    }
    !notRewritable &&
    // it is only worth rewriting if predicate contains end node
    predicates.contains {
      case Variable(name) if name == endVariableName => true
    }
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
            existingEndNodePredicate
          )
        ) =>
        if (!isRewritable(predicates, nodeVariableGroupings, relationshipVariableGroupings, end.name)) {
          val rewrittenAnds = renameEnd(innerEnd, end, predicates)
          val newEndNodePredicates = mergeEndNodePredicates(existingEndNodePredicate, rewrittenAnds)
          val id = attributes.copy(s.id).id()
          r.copy(endNodePredicate = Some(newEndNodePredicates))(SameId(id))
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
            existingEndNodePredicates
          )
        ) =>
        if (!isRewritable(predicates, nodeVariableGroupings, relationshipVariableGroupings, end.name)) {
          val rewrittenAnds = renameEnd(innerEnd, end, predicates)
          val newEndNodePredicates = mergeEndNodePredicates(existingEndNodePredicates, rewrittenAnds)
          val id = attributes.copy(s.id).id()
          r.copy(endNodePredicate = Some(newEndNodePredicates))(SameId(id))
        } else {
          s
        }
    }
  }

  private val instance: Rewriter = topDown(innerRewriter)

  override def apply(input: AnyRef): AnyRef = instance.apply(input)
}
