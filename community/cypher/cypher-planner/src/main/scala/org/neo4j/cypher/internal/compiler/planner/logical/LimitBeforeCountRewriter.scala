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
package org.neo4j.cypher.internal.compiler.planner.logical

import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.ast.semantics.SemanticTable
import org.neo4j.cypher.internal.compiler.phases.CompilationContains
import org.neo4j.cypher.internal.compiler.phases.LogicalPlanState
import org.neo4j.cypher.internal.compiler.phases.PlannerContext
import org.neo4j.cypher.internal.expressions.Add
import org.neo4j.cypher.internal.expressions.CaseExpression
import org.neo4j.cypher.internal.expressions.CountStar
import org.neo4j.cypher.internal.expressions.Equals
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.GreaterThanOrEqual
import org.neo4j.cypher.internal.expressions.In
import org.neo4j.cypher.internal.expressions.InequalityExpression
import org.neo4j.cypher.internal.expressions.IsNotNull
import org.neo4j.cypher.internal.expressions.ListLiteral
import org.neo4j.cypher.internal.expressions.Not
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.SignedDecimalIntegerLiteral
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.expressions.functions.Count
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.frontend.phases.factories.PlanPipelineTransformerFactory
import org.neo4j.cypher.internal.ir.AggregatingQueryProjection
import org.neo4j.cypher.internal.ir.AggregatingQueryProjection.OptionalPreprocessing.FilterAndLimit
import org.neo4j.cypher.internal.ir.AggregatingQueryProjection.OptionalPreprocessing.Passthrough
import org.neo4j.cypher.internal.ir.PlannerQuery
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.ir.QueryPagination
import org.neo4j.cypher.internal.ir.QueryProjection
import org.neo4j.cypher.internal.ir.RegularQueryProjection
import org.neo4j.cypher.internal.ir.RegularSinglePlannerQuery
import org.neo4j.cypher.internal.ir.Selections
import org.neo4j.cypher.internal.ir.SinglePlannerQuery
import org.neo4j.cypher.internal.ir.UnionQuery
import org.neo4j.cypher.internal.ir.ast.CountIRExpression
import org.neo4j.cypher.internal.ir.ordering.InterestingOrder
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.FoldConstants
import org.neo4j.cypher.internal.util.CancellationChecker
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.cypher.internal.util.StepSequencer.DefaultPostCondition
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.cypher.internal.util.symbols.CTInteger

/**
 * Finds and marks count(...) aggregations that are compared against a number and do not need the full input.
 *
 * Later they can be planned with filter and limit before aggregation, e.g.:
 *
 * {{{
 * COUNT { (n)-->(n) } > $param
 * =>
 * COUNT { MATCH (n)-->(n) LIMIT CASE WHEN $param >= 0 THEN $param + 1 ELSE 0 END } > $param
 *
 * RETURN count(*) > $param
 * =>
 * LIMIT CASE WHEN $param >= 0 THEN $param + 1 ELSE 0 END
 * RETURN count(*) > $param
 *
 * RETURN count(n.prop) > $param
 * =>
 * WITH * WHERE n.prop IS NOT NULL
 * LIMIT CASE WHEN $param >= 0 THEN $param + 1 ELSE 0 END
 * RETURN count(n.prop) > $param
 * }}}
 */
case object LimitBeforeCountRewriter extends PlannerQueryRewriter
    with StepSequencer.Step
    with DefaultPostCondition
    with PlanPipelineTransformerFactory {

  override def preConditions: Set[StepSequencer.Condition] = Set(
    // This works on the IR
    CompilationContains[PlannerQuery](),
    // Give getDegree rewriter a priority, since it rewrites similar expressions
    GetDegreeRewriterStep.completed
  )

  override def invalidatedConditions: Set[StepSequencer.Condition] = Set.empty

  override def getTransformer(
    pushdownPropertyReads: Boolean,
    semanticFeatures: Seq[SemanticFeature]
  ): Transformer[PlannerContext, LogicalPlanState, LogicalPlanState] = this

  override def instance(from: LogicalPlanState, context: PlannerContext): Rewriter = {
    if (!context.config.limitBeforeCountRewriterEnabled()) {
      Rewriter.noop
    } else {
      rewriter(from.semanticTable(), context.cancellationChecker)
    }
  }

  /**
   * At runtime, LIMIT only accepts non-negative integers.
   *
   * CASE
   *   WHEN expr >= 0 THEN expr + 1
   *   ELSE 0
   * END
   */
  def limitSafeExpressionFrom(possiblyUnsafeExpression: Expression): Expression = {
    val pos = InputPosition.NONE
    CaseExpression(
      expression = None,
      alternatives = List(
        GreaterThanOrEqual(possiblyUnsafeExpression, SignedDecimalIntegerLiteral("0")(pos))(pos) ->
          Add(possiblyUnsafeExpression, SignedDecimalIntegerLiteral("1")(pos))(pos)
            .rewrite(FoldConstants.instance).asInstanceOf[Expression]
      ),
      default = Some(SignedDecimalIntegerLiteral("0")(pos))
    )(pos)
  }

  private def rewriter(semanticTable: SemanticTable, cancellationChecker: CancellationChecker): Rewriter = {
    InnerRewriter(semanticTable, cancellationChecker)
  }

  private case class InnerRewriter(semanticTable: SemanticTable, cancellationChecker: CancellationChecker)
      extends Rewriter {
    override def apply(input: AnyRef): AnyRef = instance.apply(input)

    private val instance: Rewriter = bottomUp(
      cancellation = cancellationChecker,
      rewriter = Rewriter.lift {
        // WITH count(...) AS anon_0
        // RETURN anon_0 > $param
        case originalPlannerQuery @ RegularSinglePlannerQuery(
            _,
            _,
            agg @ AggregatingQueryProjection(
              EmptyMap(),
              SingletonMap(aggVar -> aggExpression),
              QueryPagination.empty,
              Selections.empty,
              _,
              _,
              Passthrough
            ),
            Some(
              RegularSinglePlannerQuery(
                queryGraph,
                InterestingOrder.empty,
                RegularQueryProjection(
                  SingletonMap(_ -> projExpression),
                  QueryPagination.empty,
                  Selections.empty,
                  _,
                  _
                ),
                _,
                _
              )
            ),
            _
          )
          if queryGraph == QueryGraph(argumentIds = agg.importedExposedSymbols + aggVar) =>

          val maybeNewPlannerQuery = for {
            limitExpr <- extractExpressionForLimit(aggVar, unwrapNegation(projExpression))
            maybeFilterExpression <- extractExpressionForFilter(aggExpression)
          } yield {
            originalPlannerQuery.withHorizon(agg.copy(
              optionalPreprocessing = FilterAndLimit(maybeFilterExpression, limitExpr)
            ))
          }
          maybeNewPlannerQuery.getOrElse(originalPlannerQuery)

        // Undo rewrite introduced by the previous case if we encounter an explicit LIMIT:
        //
        // LIMIT 100
        // WITH count(...) AS anon_0
        // RETURN anon_0 > $param
        case plannerQuery @ RegularSinglePlannerQuery(
            _,
            _,
            horizon: QueryProjection,
            Some(RegularSinglePlannerQuery(_, _, agg: AggregatingQueryProjection, _, _)),
            _
          )
          if agg.optionalPreprocessing != Passthrough && horizon.queryPagination.limit.nonEmpty =>
          plannerQuery.updateTail(_.withHorizon(agg.copy(optionalPreprocessing = Passthrough)))

        // COUNT { ... } > $param
        case expr @ ExpressionComparedToNumberExtractor(originalCountIrExpression: CountIRExpression, limitExpr) =>
          originalCountIrExpression.query match {
            case _: UnionQuery =>
              expr

            case spq: SinglePlannerQuery if hasLimitBeforeAggregationAlready(spq) =>
              expr

            case spq: SinglePlannerQuery =>
              spq.tailOrSelf match {
                case RegularSinglePlannerQuery(_, _, aggregatingProjection: AggregatingQueryProjection, None, _) =>
                  val countExpr = originalCountIrExpression.withQuery(
                    spq.updateTailOrSelf(
                      _.withHorizon(aggregatingProjection.copy(
                        optionalPreprocessing = FilterAndLimit(None, limitExpr)
                      ))
                    )
                  )
                  replaceExpressionArgument(expr, oldArgument = originalCountIrExpression, newArgument = countExpr)

                case _ => expr
              }
          }
      }
    )

    private object ExpressionComparedToNumberExtractor {

      // returns Option[Expression -> Number]
      def unapply(x: Expression): Option[(Expression, Expression)] = {
        def isConstInt(expr: Expression): Boolean =
          expr.isConstantForQuery && semanticTable.typeFor(expr).is(CTInteger)

        x match {
          case InequalityExpression(c, rhs) if isConstInt(rhs) => Some(c -> limitSafeExpressionFrom(rhs))
          case InequalityExpression(lhs, c) if isConstInt(lhs) => Some(c -> limitSafeExpressionFrom(lhs))

          case Equals(c, rhs) if isConstInt(rhs) => Some(c -> limitSafeExpressionFrom(rhs))
          case Equals(lhs, c) if isConstInt(lhs) => Some(c -> limitSafeExpressionFrom(lhs))

          case In(c, ListLiteral(Seq(expr))) if isConstInt(expr) => Some(c -> limitSafeExpressionFrom(expr))

          case _ => None
        }

      }
    }

    private def extractExpressionForLimit(
      count: Expression,
      parent: Expression
    ): Option[Expression] = {
      parent match {
        case ExpressionComparedToNumberExtractor(`count`, limitExpr) => Some(limitExpr)
        case _                                                       => None
      }
    }

    private def replaceExpressionArgument(
      expr: Expression,
      oldArgument: Expression,
      newArgument: Expression
    ): Expression = {
      expr.dup(expr.treeChildren.map {
        case `oldArgument` => newArgument
        case x             => x
      }.toSeq)
    }
  }

  private def hasLimitBeforeAggregationAlready(query: SinglePlannerQuery): Boolean = {
    query.allPlannerQueries.takeRight(2) match {
      case collection.Seq(
          RegularSinglePlannerQuery(_, _, horizon: QueryProjection, _, _),
          RegularSinglePlannerQuery(_, _, _: AggregatingQueryProjection, _, _)
        ) if horizon.queryPagination.limit.nonEmpty =>
        true

      case _ =>
        false
    }
  }

  private def unwrapNegation(projExpr: Expression): Expression = {
    projExpr match {
      case Not(negatedExpr) => negatedExpr
      case _                => projExpr
    }
  }

  private def extractExpressionForFilter(
    aggregationExpression: Expression
  ): Option[Option[Expression]] = {
    aggregationExpression match {
      case fi: FunctionInvocation if fi.distinct =>
        None

      case CountStar() =>
        Some(None)

      case Count(arg) =>
        arg match {
          // count(n)
          case v: Variable =>
            Some(Some(IsNotNull(v)(InputPosition.NONE)))

          // count(n.prop)
          case p: Property =>
            // InsertCachedProperties keeps track of Ref[Property]
            Some(Some(IsNotNull(p.copy()(p.position))(InputPosition.NONE)))

          case _ =>
            None
        }

      case _ =>
        None
    }
  }

  private object EmptyMap {
    def unapply[K, V](m: Map[K, V]): Boolean = m.isEmpty
  }

  private object SingletonMap {
    def unapply[K, V](m: Map[K, V]): Option[(K, V)] = Option.when(m.size == 1)(m.head)
  }
}
