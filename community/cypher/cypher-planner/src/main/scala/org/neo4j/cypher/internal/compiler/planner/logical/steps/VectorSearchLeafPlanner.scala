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
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.HasLabel
import org.neo4j.cypher.internal.expressions.HasTypes
import org.neo4j.cypher.internal.expressions.IsNotNull
import org.neo4j.cypher.internal.expressions.LabelToken
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.NODE_TYPE
import org.neo4j.cypher.internal.expressions.Ors
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertyKeyToken
import org.neo4j.cypher.internal.expressions.RELATIONSHIP_TYPE
import org.neo4j.cypher.internal.expressions.RelationshipTypeToken
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.ir.VectorSearchClause
import org.neo4j.cypher.internal.logical.plans.IndexedProperty
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.planner.spi.VectorIndexError
import org.neo4j.exceptions.InternalException
import org.neo4j.exceptions.InvalidArgumentException
import org.neo4j.exceptions.VectorIndexSearchException
import org.neo4j.graphdb.schema.IndexType

import java.util.Locale

/**
 * Plans ANN vector search leaf plans for nodes or relationships in SEARCH sub-clauses.
 * @param skipIDs IDs of variables that should not be planned as vector search.
 */
final case class VectorSearchLeafPlanner(skipIDs: Set[LogicalVariable]) extends LeafPlanner {

  private def handleErrors(indexDescriptorError: VectorIndexError, indexName: String, bindingVariableName: String) = {
    indexDescriptorError match {
      case VectorIndexError.NotFound =>
        throw VectorIndexSearchException.indexNotFound(indexName)
      case VectorIndexError.WrongIndexType(wrongIndexType) =>
        throw InvalidArgumentException.wrongIndexType(
          indexName,
          IndexType.VECTOR.name().toLowerCase(Locale.ROOT), // must be a VECTOR index
          wrongIndexType.name().toLowerCase(Locale.ROOT) // the index type of the index name that was provided
        )
      case VectorIndexError.WrongEntityType(variableType, indexType) =>
        throw VectorIndexSearchException.wrongBindingVariableType(
          bindingVariableName,
          // the required type (for the binding variable) for the index name that was provided
          indexType.name(),
          // the actual type of the binding variable
          variableType.name()
        )
    }
  }

  override def apply(
    queryGraph: QueryGraph,
    interestingOrderConfig: InterestingOrderConfig,
    context: LogicalPlanningContext
  ): Set[LogicalPlan] = {
    queryGraph.searchClause match {
      case Some(vectorSearchPredicate: VectorSearchClause)
        if !skipIDs.contains(vectorSearchPredicate.resultVariable) =>
        val dependencies = vectorSearchPredicate.embedding.dependencies union vectorSearchPredicate.limit.dependencies
        val unresolvedDependencies = dependencies diff queryGraph.argumentIds
        // TODO: add support for vector search where the embedding refers to the binding variable
        //  See PLAN-3087
        assert(
          unresolvedDependencies.isEmpty,
          s"unexpected dependencies $unresolvedDependencies in vector search predicate $vectorSearchPredicate"
        )
        if (queryGraph.patternNodes.contains(vectorSearchPredicate.resultVariable)) {
          context.staticComponents.planContext.nodeVectorIndexByName(vectorSearchPredicate.indexName) match {
            case Right(descriptor) =>
              val labelTokens =
                descriptor.labelIds.map(labelId =>
                  LabelToken(context.staticComponents.planContext.getLabelName(labelId), labelId)
                )

              val propertyKeyToken = PropertyKeyToken(
                name = context.staticComponents.planContext.getPropertyKeyName(descriptor.property.id),
                nameId = descriptor.property
              )
              val (implicitlySolvedPredicates, otherPredicates) =
                partitionNodePredicates(
                  vectorSearchPredicate.resultVariable,
                  propertyKeyToken,
                  labelTokens,
                  queryGraph.selections.flatPredicatesSet
                )

              val indexedProperties = List(IndexedProperty(
                propertyKeyToken,
                getValueFromIndex = context.settings.remoteBatchPropertiesStrategy.getValueFromIndexBehavior(
                  vectorSearchPredicate.resultVariable,
                  propertyKeyToken.name,
                  otherPredicates,
                  context.plannerState.contextualPropertyAccess
                ),
                entityType = NODE_TYPE
              ))
              val nodeVectorIndexSearch = context.staticComponents.logicalPlanProducer.planNodeVectorIndexSearch(
                context = context,
                resultVariable = vectorSearchPredicate.resultVariable,
                labels = labelTokens,
                indexedProperties = indexedProperties,
                indexName = vectorSearchPredicate.indexName,
                embedding = vectorSearchPredicate.embedding,
                limit = vectorSearchPredicate.limit,
                scoreVariable = vectorSearchPredicate.scoreVariable,
                argumentIds = queryGraph.argumentIds,
                implicitlySolvedPredicates = implicitlySolvedPredicates
              )
              Set(nodeVectorIndexSearch)

            case Left(vectorIndexError) => handleErrors(
                vectorIndexError,
                vectorSearchPredicate.indexName,
                vectorSearchPredicate.resultVariable.name
              )
          }
        } else {
          val patternRelationship =
            queryGraph.patternRelationships.find(_.variable == vectorSearchPredicate.resultVariable).getOrElse(
              throw InternalException.internalError(
                "VectorSearchLeafPlanner",
                "The binding variable of the vector search is not a node or relationship in the query graph"
              )
            )

          context.staticComponents.planContext.relationshipVectorIndexByName(vectorSearchPredicate.indexName) match {
            case Right(descriptor) =>

              val propertyKeyToken = PropertyKeyToken(
                name = context.staticComponents.planContext.getPropertyKeyName(descriptor.property.id),
                nameId = descriptor.property
              )

              val indexedTypes = descriptor.relTypeIds.map(relTypeId =>
                RelationshipTypeToken(context.staticComponents.planContext.getRelTypeName(relTypeId), relTypeId)
              ).toSeq

              val (implicitlySolvedPredicates, otherPredicates) =
                partitionRelationshipPredicates(
                  vectorSearchPredicate.resultVariable,
                  propertyKeyToken,
                  indexedTypes,
                  queryGraph.selections.flatPredicatesSet
                )

              val indexedProperties = List(IndexedProperty(
                propertyKeyToken,
                getValueFromIndex = context.settings.remoteBatchPropertiesStrategy.getValueFromIndexBehavior(
                  vectorSearchPredicate.resultVariable,
                  propertyKeyToken.name,
                  otherPredicates,
                  context.plannerState.contextualPropertyAccess
                ),
                entityType = RELATIONSHIP_TYPE
              ))

              val relationshipVectorIndexSearch =
                context.staticComponents.logicalPlanProducer.planRelationshipVectorIndexSearch(
                  context = context,
                  patternRelationship = patternRelationship,
                  indexedTypes = indexedTypes,
                  indexedProperties = indexedProperties,
                  indexName = vectorSearchPredicate.indexName,
                  embedding = vectorSearchPredicate.embedding,
                  limit = vectorSearchPredicate.limit,
                  scoreVariable = vectorSearchPredicate.scoreVariable,
                  argumentIds = queryGraph.argumentIds,
                  implicitlySolvedPredicates = implicitlySolvedPredicates
                )
              Set(relationshipVectorIndexSearch)

            case Left(vectorIndexError) => handleErrors(
                vectorIndexError,
                vectorSearchPredicate.indexName,
                vectorSearchPredicate.resultVariable.name
              )
          }
        }

      case _ => Set.empty
    }
  }

  private def partitionNodePredicates(
    vectorSearchVariable: LogicalVariable,
    propertyKeyToken: PropertyKeyToken,
    labelTokens: Set[LabelToken],
    predicates: Set[Expression]
  ): (Set[Expression], Set[Expression]) = {
    val isSolved: Expression => Boolean =
      if (labelTokens.size == 1) {
        val labelToken = labelTokens.head
        expression =>
          solvesIsNotNull(vectorSearchVariable, propertyKeyToken, expression) ||
            solvesHasLabel(vectorSearchVariable, labelToken, expression)
      } else {
        // If the index is defined for more than one label, the logic needs to be more complicated.
        // For example, if it is defined for (n:Foo|Bar), it implicitly solves x:Foo|Bar or any superset like
        // x:Foo|Bar|Zor but not x:Foo or x:Bar|Zor.
        // We decided not to handle it for now.
        solvesIsNotNull(vectorSearchVariable, propertyKeyToken, _)
      }

    partitionPredicates(predicates, isSolved)
  }

  private def partitionRelationshipPredicates(
    vectorSearchVariable: LogicalVariable,
    propertyKeyToken: PropertyKeyToken,
    relationshipTokens: Seq[RelationshipTypeToken],
    predicates: Set[Expression]
  ): (Set[Expression], Set[Expression]) = {
    val isSolved: Expression => Boolean =
      if (relationshipTokens.size == 1) {
        val typeToken = relationshipTokens.head
        expression =>
          solvesIsNotNull(vectorSearchVariable, propertyKeyToken, expression) ||
            solvesHasTypes(vectorSearchVariable, typeToken, expression)
      } else {
        // If the index is defined for more than one relationship type, the logic needs to be more complicated.
        // For example, if it is defined for ()-[r:R|S]-(), it implicitly solves x:R|S or any superset like
        // x:R|S|T but not x:R or x:S|T.
        // We decided not to handle it for now.
        solvesIsNotNull(vectorSearchVariable, propertyKeyToken, _)
      }

    partitionPredicates(predicates, isSolved)
  }

  private def partitionPredicates(
    predicates: Set[Expression],
    isSolved: Expression => Boolean
  ): (Set[Expression], Set[Expression]) =
    predicates.partition {
      case Ors(nestedExpressions) => nestedExpressions.exists(isSolved)
      case otherExpression        => isSolved(otherExpression)
    }

  private def solvesIsNotNull(
    vectorSearchVariable: LogicalVariable,
    propertyKeyToken: PropertyKeyToken,
    expression: Expression
  ): Boolean =
    expression match {
      case IsNotNull(Property(`vectorSearchVariable`, propertyKey)) if propertyKey.name == propertyKeyToken.name => true
      case _ => false
    }

  private def solvesHasLabel(
    vectorSearchVariable: LogicalVariable,
    labelToken: LabelToken,
    expression: Expression
  ): Boolean =
    expression match {
      case HasLabel(`vectorSearchVariable`, lbl) if lbl.name == labelToken.name => true
      case _                                                                    => false
    }

  private def solvesHasTypes(
    vectorSearchVariable: LogicalVariable,
    typeToken: RelationshipTypeToken,
    expression: Expression
  ): Boolean =
    expression match {
      case HasTypes(`vectorSearchVariable`, relTypes)
        if relTypes.size == 1 && relTypes.exists(_.name == typeToken.name) => true
      case _ => false
    }

}
