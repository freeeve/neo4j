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
package org.neo4j.cypher.internal.compiler.phases

import org.neo4j.cypher.internal.ast.AdministrationCommand
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.UnionAll
import org.neo4j.cypher.internal.ast.UnionDistinct
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.compiler.ast.convert.plannerQuery.StatementConverters
import org.neo4j.cypher.internal.frontend.phases.BaseContext
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase.LOGICAL_PLANNING
import org.neo4j.cypher.internal.frontend.phases.Namespacer
import org.neo4j.cypher.internal.frontend.phases.Phase
import org.neo4j.cypher.internal.frontend.phases.StatementCondition
import org.neo4j.cypher.internal.frontend.phases.Transformer
import org.neo4j.cypher.internal.frontend.phases.collapseMultipleInPredicates
import org.neo4j.cypher.internal.frontend.phases.factories.PlanPipelineTransformerConfig
import org.neo4j.cypher.internal.frontend.phases.factories.PlanPipelineTransformerFactory
import org.neo4j.cypher.internal.frontend.phases.rewriting.cnf.CNFNormalizer.PredicatesInCNF
import org.neo4j.cypher.internal.ir.PlannerQuery
import org.neo4j.cypher.internal.ir.QueryProjection
import org.neo4j.cypher.internal.rewriting.conditions.AggregationsAreIsolated
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNamedPathOnlyForShortestPath
import org.neo4j.cypher.internal.rewriting.conditions.ContainsNoNodesOfType
import org.neo4j.cypher.internal.rewriting.conditions.SemanticInfoAvailable
import org.neo4j.cypher.internal.util.StepSequencer
import org.neo4j.exceptions.InternalException
import org.neo4j.exceptions.NotSystemDatabaseException

/**
 * From the normalized ast, create the corresponding PlannerQuery.
 */
case class CreatePlannerQuery(semanticFeatures: Set[SemanticFeature])
    extends Phase[BaseContext, BaseState, LogicalPlanState] {

  override def phase = LOGICAL_PLANNING

  override def process(from: BaseState, context: BaseContext): LogicalPlanState = from.statement() match {
    case query: Query =>
      val plannerQuery: PlannerQuery =
        if (semanticFeatures.contains(SemanticFeature.UseAsMultipleGraphsSelector))
          StatementConverters.convertCompositePlannerQuery(
            query = query,
            semanticTable = from.semanticTable(),
            anonymousVariableNameGenerator = from.anonymousVariableNameGenerator,
            cancellationChecker = context.cancellationChecker
          )
        else
          StatementConverters.convertToPlannerQuery(
            query = query,
            semanticTable = from.semanticTable(),
            anonymousVariableNameGenerator = from.anonymousVariableNameGenerator,
            cancellationChecker = context.cancellationChecker,
            importedVariables = Set.empty,
            position = QueryProjection.Position.Final
          )

      LogicalPlanState(from).copy(maybeQuery = Some(plannerQuery))

    case command: AdministrationCommand => throw NotSystemDatabaseException.notSystemDatabaseException(command.name)

    case x => throw InternalException.internalError(this.getClass.getSimpleName, s"Expected a Query and not `$x`")
  }

  override def postConditions: Set[StepSequencer.Condition] = CreatePlannerQuery.postConditions
}

case object CreatePlannerQuery extends StepSequencer.Step with PlanPipelineTransformerFactory {

  override def preConditions: Set[StepSequencer.Condition] = Set(
    // We would get MatchErrors if the first 3 conditions would not be met.
    StatementCondition(ContainsNamedPathOnlyForShortestPath),
    StatementCondition(ContainsNoNodesOfType[UnionAll]()),
    StatementCondition(ContainsNoNodesOfType[UnionDistinct]()),
    // The PlannerQuery we create should already contain disambiguated names
    Namespacer.completed,
    // and we want to take advantage of isolated aggregations in the planner
    StatementCondition(AggregationsAreIsolated),
    collapseMultipleInPredicates.completed
  ) ++
    // The PlannerQuery should be created based on normalised predicates
    PredicatesInCNF ++
    // We look up semantic info during PlannerQuery building
    SemanticInfoAvailable

  override def postConditions: Set[StepSequencer.Condition] = Set(CompilationContains[PlannerQuery]())

  override def invalidatedConditions: Set[StepSequencer.Condition] = Set.empty

  override def getTransformer(planPipelineConfig: PlanPipelineTransformerConfig)
    : Transformer[BaseContext, BaseState, LogicalPlanState] =
    CreatePlannerQuery(planPipelineConfig.semanticFeatures.toSet)
}
