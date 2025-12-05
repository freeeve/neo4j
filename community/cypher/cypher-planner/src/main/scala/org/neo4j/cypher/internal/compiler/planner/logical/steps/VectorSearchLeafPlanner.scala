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
import org.neo4j.cypher.internal.expressions.NODE_TYPE
import org.neo4j.cypher.internal.expressions.PropertyKeyToken
import org.neo4j.cypher.internal.expressions.RELATIONSHIP_TYPE
import org.neo4j.cypher.internal.expressions.RelationshipTypeToken
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.ir.VectorSearchClause
import org.neo4j.cypher.internal.logical.plans.IndexedProperty
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.planner.spi.IndexDescriptor.EntityType
import org.neo4j.cypher.internal.util.symbols.CTNode
import org.neo4j.cypher.internal.util.symbols.CTRelationship
import org.neo4j.exceptions.InternalException
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
    queryGraph.searchClause match {
      case Some(vectorSearchPredicate: VectorSearchClause)
        if !skipIDs.contains(vectorSearchPredicate.bindingVariable) =>
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
          context.staticComponents.planContext.vectorIndexByName(vectorSearchPredicate.indexName).get

        val propertyKeyToken = PropertyKeyToken(
          name = context.staticComponents.planContext.getPropertyKeyName(vectorIndexDescriptor.property.id),
          nameId = vectorIndexDescriptor.property
        )

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

            val indexedProperties = List(IndexedProperty(
              propertyKeyToken,
              getValueFromIndex = context.settings.remoteBatchPropertiesStrategy.getValueFromIndexBehavior(
                vectorSearchPredicate.bindingVariable,
                propertyKeyToken.name,
                queryGraph.selections.flatPredicatesSet,
                context.plannerState.contextualPropertyAccess
              ),
              entityType = NODE_TYPE
            ))
            val nodeVectorIndexSearch = context.staticComponents.logicalPlanProducer.planNodeVectorIndexSearch(
              context = context,
              variable = vectorSearchPredicate.bindingVariable,
              label = LabelToken(name = labelName, nameId = labelId),
              indexedProperties = indexedProperties,
              indexName = vectorSearchPredicate.indexName,
              embedding = vectorSearchPredicate.embedding,
              limit = vectorSearchPredicate.limit,
              scoreVariable = vectorSearchPredicate.scoreVariable,
              argumentIds = queryGraph.argumentIds
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
            val relTypeName = context.staticComponents.planContext.getRelTypeName(relTypeId)

            val indexedProperties = List(IndexedProperty(
              propertyKeyToken,
              getValueFromIndex = context.settings.remoteBatchPropertiesStrategy.getValueFromIndexBehavior(
                vectorSearchPredicate.bindingVariable,
                propertyKeyToken.name,
                queryGraph.selections.flatPredicatesSet,
                context.plannerState.contextualPropertyAccess
              ),
              entityType = RELATIONSHIP_TYPE
            ))

            queryGraph.patternRelationships.find(_.variable == vectorSearchPredicate.bindingVariable) match {
              case Some(patternRelationship) =>
                val relationshipVectorIndexSearch =
                  context.staticComponents.logicalPlanProducer.planRelationshipVectorIndexSearch(
                    context = context,
                    patternRelationship = patternRelationship,
                    indexedTypes = Seq(new RelationshipTypeToken(relTypeName, relTypeId)),
                    indexedProperties = indexedProperties,
                    indexName = vectorSearchPredicate.indexName,
                    embedding = vectorSearchPredicate.embedding,
                    limit = vectorSearchPredicate.limit,
                    scoreVariable = vectorSearchPredicate.scoreVariable,
                    argumentIds = queryGraph.argumentIds
                  )
                Set(relationshipVectorIndexSearch)

              case None => throw InternalException.internalError(
                  "VectorSearchLeafPlanner",
                  "Cannot find the vector search binding variable in the querygraph."
                )
            }
        }

      case _ => Set.empty
    }
  }
}
