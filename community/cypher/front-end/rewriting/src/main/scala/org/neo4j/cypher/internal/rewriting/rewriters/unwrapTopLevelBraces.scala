/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.rewriting.rewriters

import org.neo4j.cypher.internal.ast._
import org.neo4j.cypher.internal.rewriting.conditions.SemanticInfoAvailable
import org.neo4j.cypher.internal.rewriting.rewriters.factories.PreparatoryRewritingRewriterFactory
import org.neo4j.cypher.internal.util.CypherExceptionFactory
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.StepSequencer.Condition
import org.neo4j.cypher.internal.util.StepSequencer.Step
import org.neo4j.cypher.internal.util.topDown

case object TopLevelBracesUnwrapped extends Condition

/**
 * unwrapTopLevelBraces makes sure that all TopLevelBraces are either removed or
 * replaced in the AST with a CALL (*) { ... } RETURN * if used as a Union argument.
 *
 * This makes sure that a query such as:
 *    USE outerGraph {
 *          USE innerGraph {
 *             RETURN 1 AS x
 *          }
 *       UNION
 *          USE innerGraph {
 *             RETURN 2 AS x
 *          }
 *       UNION
 *          {
 *             RETURN 3 AS x
 *          }
 *       UNION
 *          RETURN 4 AS x
 *    }
 * Is rewritten to:
 *    CALL (*) {
 *       USE innerGraph
 *       RETURN 1 AS x
 *    }
 *    RETURN *
 *    UNION
 *    CALL (*) {
 *       USE innerGraph
 *       RETURN 2 AS x
 *    }
 *    RETURN *
 *    UNION
 *    CALL (*) {
 *       USE outerGraph
 *       RETURN 3 AS x
 *    }
 *    RETURN *
 *    UNION
 *    USE outerGraph
 *    RETURN 4 AS x  *
 *
 */
case object unwrapTopLevelBraces extends Step with PreparatoryRewritingRewriterFactory {

  override def preConditions: Set[StepSequencer.Condition] = Set()

  override def postConditions: Set[StepSequencer.Condition] = Set(TopLevelBracesUnwrapped)

  override def invalidatedConditions: Set[StepSequencer.Condition] = SemanticInfoAvailable

  private def pushDownUse(query: Query, use: Option[UseGraph]): Query = {
    query match {
      case ua: UnionArgument           => pushDownUse(ua, use)
      case u @ UnionDistinct(lhs, rhs) => u.copy(pushDownUse(lhs, use), pushDownUse(rhs, use))(u.position)
      case u @ UnionAll(lhs, rhs)      => u.copy(pushDownUse(lhs, use), pushDownUse(rhs, use))(u.position)
      case _: ProjectingUnion =>
        throw new IllegalStateException(
          "Didn't expect ProjectingUnion, only SingleQuery, TopLevelBraces, UnionAll, or UnionDistinct."
        )
    }
  }

  private def pushDownUse(query: UnionArgument, use: Option[UseGraph]): UnionArgument = {
    query match {
      case sq @ SingleQuery(clauses) =>
        if (sq.partitionedClauses.leadingGraphSelection.isDefined) sq else sq.copy(use.toSeq ++ clauses)(sq.position)
      case innerTlb @ TopLevelBraces(_, None)    => innerTlb.copy(use = use)(innerTlb.position)
      case innerTlb @ TopLevelBraces(_, Some(_)) => innerTlb
    }
  }

  private val propagateUse: Rewriter = topDown(Rewriter.lift {
    case tlb @ TopLevelBraces(query, use) => if (use.isDefined) {
        tlb.copy(pushDownUse(query, use), None)(tlb.position)
      } else tlb
  })

  private val markReturns: Rewriter = topDown(Rewriter.lift {
    case tlb: TopLevelBraces =>
      tlb.copy(query =
        tlb.query.endoRewrite(
          topDown(Rewriter.lift {
            case ret: Return =>
              ret.copy(inTopLevelBraces = true)(ret.position)
          })
        )
      )(tlb.position)
  })

  private val rewriter: Rewriter = topDown(Rewriter.lift {
    case u @ UnionDistinct(lhs, rhs) =>
      u.copy(lhs.getQuery(true), rhs.getSingleQuery)(u.position)
    case u @ UnionAll(lhs, rhs) =>
      u.copy(lhs.getQuery(true), rhs.getSingleQuery)(u.position)
    case tlb: TopLevelBraces =>
      tlb.getQuery(false)
  })

  val instance: Rewriter = propagateUse andThen markReturns andThen rewriter

  override def getRewriter(cypherExceptionFactory: CypherExceptionFactory): Rewriter = instance
}
