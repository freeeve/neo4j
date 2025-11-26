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
package org.neo4j.cypher.internal.compiler.planner.logical.steps

import org.neo4j.cypher.internal.compiler.planner.logical.LeafPlanner
import org.neo4j.cypher.internal.compiler.planner.logical.LogicalPlanningContext
import org.neo4j.cypher.internal.compiler.planner.logical.ordering.InterestingOrderConfig
import org.neo4j.cypher.internal.expressions.LabelToken
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.PropertyKeyToken
import org.neo4j.cypher.internal.expressions.VectorSearchPredicate
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.planner.spi.IndexDescriptor.EntityType
import org.neo4j.cypher.internal.util.symbols.CTNode
import org.neo4j.cypher.internal.util.symbols.CTRelationship
import org.neo4j.exceptions.VectorIndexSearchException

/**
 * Plans ANN vector search leaf plans for nodes or relationships in SEARCH sub-clauses.
 * @param skipIDs IDs of variables that should not be planned as vector search.
 */
final case class VectorSearchLeafPlanner(skipIDs: Set[LogicalVariable]) extends LeafPlanner {

  override def apply(
    queryGraph: QueryGraph,
    interestingOrderConfig: InterestingOrderConfig,
    context: LogicalPlanningContext
  ): Set[LogicalPlan] = {
    val vectorSearchPredicates = queryGraph.selections.flatPredicates.collect {
      case predicate: VectorSearchPredicate if !skipIDs.contains(predicate.bindingVariable) =>
        predicate
    }
    if (vectorSearchPredicates.isEmpty) {
      Set.empty
    } else {
      assert(
        vectorSearchPredicates.size == 1,
        s"expected only one vector search predicate, got ${vectorSearchPredicates.size}"
      )
      val vectorSearchPredicate = vectorSearchPredicates.head

      val dependencies = vectorSearchPredicate.embedding.dependencies union vectorSearchPredicate.limit.dependencies
      val unresolvedDependencies = dependencies diff queryGraph.argumentIds
      // TODO: add support for vector search where the embedding refers to the binding variable
      //  See PLAN-2843
      assert(
        unresolvedDependencies.isEmpty,
        s"unexpected dependencies $unresolvedDependencies in vector search predicate $vectorSearchPredicate"
      )

      val variableType = context.semanticTable.typeFor(vectorSearchPredicate.bindingVariable)

      val vectorIndexDescriptor =
        context.staticComponents.planContext.vectorIndexByName(vectorSearchPredicate.indexName)
          .getOrElse(throw VectorIndexSearchException.indexNotFound(vectorSearchPredicate.indexName))

      vectorIndexDescriptor.entityType match {
        case EntityType.Node(labelId) =>

          if (!variableType.is(CTNode)) {
            throw VectorIndexSearchException.wrongBindingVariableType(
              vectorSearchPredicate.bindingVariable.name,
              java.util.List.of("NODE"),
              variableType.typeInfo match {
                case None           => "UNKNOWN"
                case Some(typeSpec) => typeSpec.toShortString.toUpperCase()
              }
            )
          }

          val labelName = context.staticComponents.planContext.getLabelName(labelId)
          val propertyName = context.staticComponents.planContext.getPropertyKeyName(vectorIndexDescriptor.property.id)
          val nodeVectorIndexSearch = context.staticComponents.logicalPlanProducer.planNodeVectorIndexSearch(
            queryGraph = queryGraph,
            context = context,
            variable = vectorSearchPredicate.bindingVariable,
            label = LabelToken(name = labelName, nameId = labelId),
            property = PropertyKeyToken(name = propertyName, nameId = vectorIndexDescriptor.property),
            indexName = vectorSearchPredicate.indexName,
            embedding = vectorSearchPredicate.embedding,
            limit = vectorSearchPredicate.limit
          )
          Set(nodeVectorIndexSearch)
        case EntityType.Relationship(relTypeId) =>
          if (!variableType.is(CTRelationship)) {
            throw VectorIndexSearchException.wrongBindingVariableType(
              vectorSearchPredicate.bindingVariable.name,
              java.util.List.of("RELATIONSHIP"),
              variableType.typeInfo match {
                case None           => "UNKNOWN"
                case Some(typeSpec) => typeSpec.toShortString.toUpperCase()
              }
            )
          }
          ??? // TODO: Plan relationship vector search. See PLAN-2847
      }
    }
  }
}
