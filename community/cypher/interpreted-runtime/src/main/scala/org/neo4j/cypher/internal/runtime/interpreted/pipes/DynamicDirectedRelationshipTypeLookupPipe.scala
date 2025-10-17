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
package org.neo4j.cypher.internal.runtime.interpreted.pipes

import org.neo4j.common.EntityType
import org.neo4j.cypher.internal.expressions.PropertyKeyToken
import org.neo4j.cypher.internal.logical.plans.DynamicElement
import org.neo4j.cypher.internal.logical.plans.DynamicElement.All
import org.neo4j.cypher.internal.logical.plans.DynamicElement.Any
import org.neo4j.cypher.internal.logical.plans.DynamicElement.SetOperator
import org.neo4j.cypher.internal.logical.plans.IndexOrderNone
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.ClosingLongIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.PrimitiveLongHelper
import org.neo4j.cypher.internal.runtime.RelationshipIterator
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.pipes.DirectedAllRelationshipsScanPipe.allRelationshipsIterator
import org.neo4j.cypher.internal.runtime.interpreted.pipes.DynamicDirectedRelationshipTypeLookupPipe.getIterator
import org.neo4j.cypher.internal.runtime.iterators.BaseRelationshipCursorIterator
import org.neo4j.cypher.internal.runtime.iterators.PropertyFilteringRelationshipIterator
import org.neo4j.cypher.internal.runtime.iterators.RelationshipIndexCursorIterator
import org.neo4j.cypher.internal.util.SeqSupport.RichSeq
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.operations.CypherFunctions
import org.neo4j.cypher.operations.CypherFunctions.GetSingleDynamicTypeResult
import org.neo4j.cypher.operations.CypherTypeValueMapper
import org.neo4j.exceptions.CypherTypeException
import org.neo4j.internal.kernel.api.PropertyIndexQuery
import org.neo4j.internal.schema.IndexDescriptor
import org.neo4j.values.AnyValue
import org.neo4j.values.virtual.VirtualValues

import java.util.Comparator

import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.math.Ordering.comparatorToOrdering

case class DynamicDirectedRelationshipTypeLookupPipe(
  ident: Option[String],
  fromNode: Option[String],
  typeExpr: Expression,
  toNode: Option[String],
  operator: DynamicElement.SetOperator,
  propertyExpressions: Map[PropertyKeyToken, Expression]
)(val id: Id = Id.INVALID_ID) extends Pipe {

  private val relationshipWriter =
    Relationships.compileRelationshipWriter(ident, fromNode, toNode)

  protected def internalCreateResults(state: QueryState): ClosingIterator[CypherRow] = {
    val ctx = state.newRowWithArgument(rowFactory)
    val propertyQueries = DynamicDirectedRelationshipTypeLookupPipe.mapPropertyLookups(
      propertyExpressions,
      _(ctx, state)
    )
    val relIterator = getIterator(state, operator, typeExpr(ctx, state), propertyQueries)
    PrimitiveLongHelper.map(
      relIterator,
      relationshipId => {
        relationshipWriter.writeRow(
          rowFactory,
          ctx,
          VirtualValues.relationship(relationshipId),
          VirtualValues.node(relIterator.startNodeId()),
          VirtualValues.node(relIterator.endNodeId())
        )
      }
    )
  }
}

object DynamicDirectedRelationshipTypeLookupPipe {
  private type ClosingRelationshipIterator = ClosingLongIterator with RelationshipIterator

  def mapPropertyLookups(
    propertyPredicates: Map[PropertyKeyToken, Expression],
    expressionMapper: Expression => AnyValue
  ): Array[PropertyIndexQuery.ExactPredicate] = {
    propertyPredicates.map {
      case (key, expr) =>
        PropertyIndexQuery.exact(key.nameId.id, expressionMapper(expr))
    }.toArray
  }

  def getIterator(
    state: QueryState,
    operator: SetOperator,
    typeValue: AnyValue,
    propertyPredicates: Array[PropertyIndexQuery.ExactPredicate]
  ): ClosingRelationshipIterator = {
    val query = state.query
    val dynamicType = operator match {
      case Any => CypherFunctions.getDynamicTypes(typeValue)
      case All => CypherFunctions.getSingleDynamicType(typeValue, state)
    }

    def getIndexComparator: Comparator[IndexDescriptor] =
      state.indexComparatorFactory.createComparator(state.query.dataRead, state.query.transactionalContext.schemaRead)

    def findIndicesForType(
      typeId: Int,
      predicates: Seq[PropertyIndexQuery.ExactPredicate]
    ): Iterator[IndexDescriptor] = {
      predicates.orderedSubsets.flatMap { predicateSubset =>
        val indices =
          state.query.indexReferences(typeId, EntityType.RELATIONSHIP, predicateSubset.map(_.propertyKeyId): _*)
        indices.asScala.filter {
          index => predicateSubset.forall(p => index.getCapability.isQuerySupported(p.`type`(), p.valueCategory()))
        }
      }
    }

    def typeScan: ClosingRelationshipIterator = {
      dynamicType match {
        case _: CypherFunctions.GetSingleDynamicTypeResult.ConflictingDynamicTypes =>
          /* relationships only have one type */
          BaseRelationshipCursorIterator.EMPTY
        case _: CypherFunctions.GetSingleDynamicTypeResult.EmptyDynamicTypeList =>
          /* all(empty) is always true */
          allRelationshipsIterator(query)
        case relTypes: Array[String] if relTypes.length == 0 =>
          /* any(empty) is always false */
          BaseRelationshipCursorIterator.EMPTY

        case singleType: CypherFunctions.GetSingleDynamicTypeResult.SingleDynamicType =>
          /* implies all */
          query.getOptRelTypeId(singleType.value) match {
            case Some(typeId) =>
              query.getRelationshipsByType(state.relTypeTokenReadSession.get, typeId, IndexOrderNone)
            case None => BaseRelationshipCursorIterator.EMPTY
          }
        case relTypes: Array[String] if relTypes.length >= 1 =>
          /* implies any */
          DirectedUnionRelationshipTypesScanPipe.unionTypeIterator(
            state,
            relTypes.map(LazyTypeStatic(_)),
            IndexOrderNone,
            state.relTypeTokenReadSession.get
          )

        /* Developer Rob says we shouldn't ever pass this point */
        case value: AnyValue =>
          throw CypherTypeException.expectedStringOrListOfStringsNotNull(
            "Expected relationship type to be a string or list of strings.",
            value.prettify(),
            CypherTypeValueMapper.valueType(value)
          );
        case null =>
          throw CypherTypeException.expectedStringOrListOfStringsNotNull(
            "Expected relationship type to be a string or list of strings.",
            "NULL",
            "NULL"
          );
        case anything =>
          /* hail mary */
          throw CypherTypeException.expectedStringOrListOfStringsNotNull(
            "Expected relationship type to be a string or list of strings.",
            anything.toString,
            anything.toString
          );
      }
    }

    def seek(
      index: IndexDescriptor,
      predicates: Array[PropertyIndexQuery.ExactPredicate]
    ): ClosingRelationshipIterator = {
      val cursor = state.query.relationshipIndexSeek(
        state.query.dataRead.indexReadSession(index),
        needsValues = false,
        IndexOrderNone,
        predicates
      )
      new RelationshipIndexCursorIterator(cursor)
    }

    def propertyFilter(
      iterator: ClosingLongIterator,
      predicates: Array[PropertyIndexQuery.ExactPredicate]
    ): ClosingRelationshipIterator = {
      new PropertyFilteringRelationshipIterator(
        iterator,
        state.query.dataRead,
        state.cursors.relationshipScanCursor,
        state.cursors.propertyCursor,
        predicates.asInstanceOf[Array[PropertyIndexQuery]]
      )
    }

    def maybeIndexBackedSingleType(
      relType: String,
      predicates: Array[PropertyIndexQuery.ExactPredicate]
    ): Option[ClosingRelationshipIterator] = {
      query.getOptRelTypeId(relType) match {
        case None =>
          None
        case Some(relId) =>
          findIndicesForType(relId, predicates).maxOption(comparatorToOrdering(getIndexComparator))
            .map { index =>
              // if we have multiple properties, filter here
              val (indexProps, otherProps) =
                predicates.partition(p => index.schema().getPropertyIds.contains(p.propertyKeyId()))

              if (otherProps.nonEmpty) {
                propertyFilter(seek(index, indexProps), otherProps)
              } else {
                seek(index, indexProps)
              }
            }
      }
    }

    def indexSeek: Option[ClosingRelationshipIterator] = {
      (dynamicType, propertyPredicates) match {
        case (_, Array()) =>
          /* no properties - fall back to type scan + optional filter */
          None

        case (Array(), _) =>
          /* no types - no index to find */
          None

        case (singleType: GetSingleDynamicTypeResult.SingleDynamicType, _) =>
          maybeIndexBackedSingleType(singleType.value, propertyPredicates)

        case (relTypes: Array[String], _) if relTypes.length == 1 =>
          maybeIndexBackedSingleType(relTypes.head, propertyPredicates)

        /**
         * Conspicuously absent: Index seeks for any multiple types, because we can't (currently) union the
         * seeks from multiple property indexes; the index can only return properties in value order, not ID
         * order, which makes deduplication of results much more difficult. So we uh, don't do it.
         */
        case _ => None
      }
    }

    indexSeek.getOrElse {
      if (propertyPredicates.nonEmpty) {
        propertyFilter(typeScan, propertyPredicates)
      } else {
        typeScan
      }
    }
  }
}
