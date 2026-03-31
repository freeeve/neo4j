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
package org.neo4j.cypher.internal

import org.neo4j.common.EntityType
import org.neo4j.internal.schema.SchemaDescriptors
import org.neo4j.kernel.api.query.RelationshipTypeIndexUsage
import org.neo4j.kernel.api.query.SchemaIndexUsage
import org.neo4j.kernel.impl.api.SchemaStateKey
import org.neo4j.kernel.impl.query.TransactionalContext

import java.util.concurrent.atomic.AtomicLong

import scala.collection.immutable.ArraySeq

case class SchemaToken(x: Long) extends AnyVal

class SchemaHelper(val queryCache: QueryCache[_, _], val masterCompiler: MasterCompiler) {

  private val schemaToken = new AtomicLong()
  private val schemaStateKey = SchemaStateKey.newKey()

  private val creator =
    new java.util.function.Function[SchemaStateKey, SchemaToken]() {

      def apply(key: SchemaStateKey): SchemaToken = {
        queryCache.clear()
        masterCompiler.clearExecutionPlanCaches()
        SchemaToken(schemaToken.incrementAndGet())
      }
    }

  def readSchemaToken(tc: TransactionalContext): SchemaToken = {
    tc.kernelTransaction().schemaRead().schemaStateGetOrCreate(schemaStateKey, creator)
  }

  def lockEntities(
    schemaTokenBefore: SchemaToken,
    executionPlan: ExecutableQuery,
    tc: TransactionalContext
  ): LockedEntities = {
    // Lock all used indexes
    // We do not lock semantic indexes because we use them by name. They are therefore already locked during planning.
    val labelIndexIds: Set[IndexIds] = executionPlan.labelIndexIdsOfUsedIndexes
    val relationshipIndexIds: Set[IndexIds] = executionPlan.relationshipIndexIdsOfUsedIndexes
    val semanticNodeIndexesUsed = executionPlan.semanticNodeIndexesUsed
    val semanticRelIndexesUsed = executionPlan.semanticRelationshipIndexesUsed
    val lookupTypes: ArraySeq[EntityType] = ArraySeq.unsafeWrapArray(executionPlan.lookupEntityTypes)
    acquireLocks(
      tc,
      labelIndexIds.flatMap(_.entityTypeIds).toArray,
      relationshipIndexIds.flatMap(_.entityTypeIds).toArray,
      lookupTypes
    )

    if (
      lookupTypes.nonEmpty ||
      labelIndexIds.nonEmpty ||
      relationshipIndexIds.nonEmpty ||
      semanticNodeIndexesUsed.nonEmpty ||
      semanticRelIndexesUsed.nonEmpty
    ) {
      val schemaTokenAfter = readSchemaToken(tc)
      // Need to check if index has been dropped because we can still acquire and get the lock
      val indexDropped = !indexExists(
        tc,
        labelIndexIds,
        relationshipIndexIds,
        semanticNodeIndexesUsed,
        semanticRelIndexesUsed,
        lookupTypes
      )

      // if the schema has changed while taking all locks OR if the lookup index has been dropped, we release locks and return false
      if (schemaTokenBefore != schemaTokenAfter || indexDropped) {
        releaseLocks(
          tc,
          labelIndexIds.flatMap(_.entityTypeIds).toArray,
          relationshipIndexIds.flatMap(_.entityTypeIds).toArray,
          lookupTypes
        )
        return LockedEntities(successful = false, needsReplan = indexDropped)
      }
    }
    LockedEntities(successful = true, needsReplan = false)
  }

  private def acquireLocks(
    tc: TransactionalContext,
    labelIds: Array[Long],
    relationshipIds: Array[Long],
    lookupTypes: Seq[EntityType]
  ): Unit = {
    if (labelIds.nonEmpty) {
      tc.kernelTransaction.locks().acquireSharedLabelLock(labelIds: _*)
    }

    if (relationshipIds.nonEmpty) {
      tc.kernelTransaction.locks().acquireSharedRelationshipTypeLock(relationshipIds: _*)
    }

    lookupTypes.foreach(tc.kernelTransaction.locks().acquireSharedLookupLock(_))
  }

  private def releaseLocks(
    tc: TransactionalContext,
    labelIds: Array[Long],
    relationshipIds: Array[Long],
    lookupTypes: Seq[EntityType]
  ): Unit = {
    if (labelIds.nonEmpty) {
      tc.kernelTransaction.locks().releaseSharedLabelLock(labelIds: _*)
    }

    if (relationshipIds.nonEmpty) {
      tc.kernelTransaction.locks().releaseSharedRelationshipTypeLock(relationshipIds: _*)
    }

    lookupTypes.foreach(tc.kernelTransaction.locks().releaseSharedLookupLock(_))
  }

  private def indexExists(
    tc: TransactionalContext,
    labelIndexes: Set[IndexIds],
    relIndexes: Set[IndexIds],
    semanticNodeIndexesUsed: Set[SchemaIndexUsage],
    semanticRelationshipIndexUsage: Set[RelationshipTypeIndexUsage],
    lookupEntities: Seq[EntityType]
  ): Boolean =
    labelIndexes.forall(hasLabelIndex(tc, _)) &&
      relIndexes.forall(hasRelationshipTypeIndex(tc, _)) &&
      semanticNodeIndexesUsed.forall(hasSemanticNodeIndex(tc, _)) &&
      semanticRelationshipIndexUsage.forall(hasSemanticRelationshipIndex(tc, _)) &&
      lookupEntities.forall(hasLookupIndex(tc, _))

  private def hasLookupIndex(tc: TransactionalContext, entityType: EntityType): Boolean =
    tc.kernelTransaction.schemaRead().indexForSchemaNonTransactional(
      SchemaDescriptors.forAnyEntityTokens(entityType)
    ).hasNext

  private def hasSemanticNodeIndex(tc: TransactionalContext, indexUsage: SchemaIndexUsage): Boolean =
    tc.kernelTransaction
      .schemaRead()
      .indexForSchemaNonTransactional(SchemaDescriptors.forSemanticSearch(
        EntityType.NODE,
        indexUsage.getLabelIds,
        indexUsage.getPropertyKeyIds
      )).hasNext

  private def hasSemanticRelationshipIndex(tc: TransactionalContext, indexUsage: RelationshipTypeIndexUsage): Boolean =
    tc.kernelTransaction
      .schemaRead()
      .indexForSchemaNonTransactional(SchemaDescriptors.forSemanticSearch(
        EntityType.RELATIONSHIP,
        indexUsage.getRelationshipTypeIds,
        indexUsage.getPropertyKeyIds
      )).hasNext

  private def hasRelationshipTypeIndex(tc: TransactionalContext, indexId: IndexIds): Boolean =
    hasRelationshipTypeIndex(tc, indexId.entityTypeIds.head, indexId.propertyKeyIds)

  private def hasRelationshipTypeIndex(tc: TransactionalContext, relType: Long, properties: Array[Int]): Boolean =
    tc.kernelTransaction
      .schemaRead()
      .indexForSchemaNonTransactional(SchemaDescriptors.forRelType(relType.toInt, properties: _*)).hasNext

  private def hasLabelIndex(tc: TransactionalContext, indexId: IndexIds): Boolean =
    hasLabelIndex(tc, indexId.entityTypeIds.head, indexId.propertyKeyIds)

  private def hasLabelIndex(tc: TransactionalContext, label: Long, properties: Array[Int]): Boolean =
    tc.kernelTransaction
      .schemaRead()
      .indexForSchemaNonTransactional(SchemaDescriptors.forLabel(label.toInt, properties: _*)).hasNext

  case class LockedEntities(successful: Boolean, needsReplan: Boolean)
}

case class IndexIds(entityTypeIds: Array[Long], propertyKeyIds: Array[Int])
