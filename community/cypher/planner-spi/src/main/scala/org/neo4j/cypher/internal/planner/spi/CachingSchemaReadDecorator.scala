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
package org.neo4j.cypher.internal.planner.spi

import org.neo4j.cypher.internal.ir.helpers.CachedFunction
import org.neo4j.internal.kernel.api.InternalIndexState
import org.neo4j.internal.kernel.api.PopulationProgress
import org.neo4j.internal.kernel.api.SchemaRead
import org.neo4j.internal.kernel.api.SchemaReadCore
import org.neo4j.internal.schema
import org.neo4j.internal.schema.ConstraintDescriptor
import org.neo4j.internal.schema.IndexDescriptor
import org.neo4j.internal.schema.IndexType
import org.neo4j.internal.schema.SchemaDescriptor
import org.neo4j.kernel.api.index.IndexSample
import org.neo4j.kernel.api.index.IndexUsageStats

import java.lang
import java.util
import java.util.function

/**
 * Adds a caching layer on top of another SchemaRead implementation.
 * Since CachedFunction is not thread-safe, this decorator is not thread-safe either.
 *
 * @param delegate - The schemaRead implementation to delegate calls to.
 */
case class CachingSchemaReadDecorator(delegate: SchemaRead) extends SchemaRead {

  private val cachedIndexState: IndexDescriptor => InternalIndexState = CachedFunction {
    index: IndexDescriptor => delegate.indexGetStateNonLocking(index)
  }

  private val cachedIndexSample: IndexDescriptor => IndexSample = CachedFunction {
    index: IndexDescriptor => delegate.indexSample(index)
  }

  override def indexForSchemaNonTransactional(schema: SchemaDescriptor): util.Iterator[IndexDescriptor] =
    delegate.indexForSchemaNonTransactional(schema)

  override def indexForSchemaAndIndexTypeNonTransactional(
    schema: SchemaDescriptor,
    indexType: IndexType
  ): IndexDescriptor = delegate.indexForSchemaAndIndexTypeNonTransactional(schema, indexType)

  override def indexForSchemaNonLocking(schema: SchemaDescriptor): util.Iterator[IndexDescriptor] =
    delegate.indexForSchemaNonLocking(schema)

  override def getLabelIndexesNonLocking(labelId: Int): util.Iterator[IndexDescriptor] =
    delegate.getLabelIndexesNonLocking(labelId)

  override def getRelTypeIndexesNonLocking(relTypeId: Int): util.Iterator[IndexDescriptor] =
    delegate.getRelTypeIndexesNonLocking(relTypeId)

  override def indexesGetAllNonLocking(): util.Iterator[IndexDescriptor] = delegate.indexesGetAllNonLocking()

  override def indexUniqueValuesSelectivity(index: IndexDescriptor): Double = {
    val indexSample = cachedIndexSample(index)
    if (indexSample.sampleSize() == 0) {
      1.0d
    } else {
      indexSample.uniqueValues().toDouble / indexSample.sampleSize()
    }
  }

  override def indexSample(index: IndexDescriptor): IndexSample = cachedIndexSample(index)

  override def constraintsGetForSchema(descriptor: SchemaDescriptor): util.Iterator[ConstraintDescriptor] =
    delegate.constraintsGetForSchema(descriptor)

  override def constraintsGetForSchemaNonLocking(descriptor: SchemaDescriptor): util.Iterator[ConstraintDescriptor] =
    delegate.constraintsGetForSchemaNonLocking(descriptor)

  override def constraintExists(descriptor: ConstraintDescriptor): Boolean = delegate.constraintExists(descriptor)

  override def snapshot(): SchemaReadCore = delegate.snapshot()

  override def indexGetOwningUniquenessConstraintId(index: IndexDescriptor): lang.Long =
    delegate.indexGetOwningUniquenessConstraintId(index)

  override def indexGetOwningUniquenessConstraintIdNonLocking(index: IndexDescriptor): lang.Long =
    delegate.indexGetOwningUniquenessConstraintIdNonLocking(index)

  override def schemaStateGetOrCreate[K, V](key: K, creator: function.Function[K, V]): V =
    delegate.schemaStateGetOrCreate(key, creator)

  override def schemaStateFlush(): Unit = delegate.schemaStateFlush()

  override def assertIndexExists(index: IndexDescriptor): Unit = delegate.assertIndexExists(index)

  override def indexGetForName(name: String): schema.IndexDescriptor = delegate.indexGetForName(name)

  override def constraintGetForName(name: String): ConstraintDescriptor = delegate.constraintGetForName(name)

  override def index(schema: SchemaDescriptor): util.Iterator[IndexDescriptor] = delegate.index(schema)

  override def index(schema: SchemaDescriptor, `type`: IndexType): IndexDescriptor = delegate.index(schema, `type`)

  override def indexesGetForLabel(labelId: Int): util.Iterator[IndexDescriptor] = delegate.indexesGetForLabel(labelId)

  override def indexesGetForRelationshipType(relationshipType: Int): util.Iterator[IndexDescriptor] =
    delegate.indexesGetForRelationshipType(relationshipType)

  override def indexesGetAll(): util.Iterator[IndexDescriptor] = delegate.indexesGetAll()

  override def indexGetState(index: IndexDescriptor): InternalIndexState = delegate.indexGetState(index)

  override def indexGetStateNonLocking(index: IndexDescriptor): InternalIndexState = cachedIndexState(index)

  override def indexGetPopulationProgress(index: IndexDescriptor): PopulationProgress =
    delegate.indexGetPopulationProgress(index)

  override def indexGetFailure(index: IndexDescriptor): String = delegate.indexGetFailure(index)

  override def constraintsGetForLabel(labelId: Int): util.Iterator[ConstraintDescriptor] =
    delegate.constraintsGetForLabel(labelId)

  override def constraintsGetForLabelNonLocking(labelId: Int): util.Iterator[ConstraintDescriptor] =
    delegate.constraintsGetForLabelNonLocking(labelId)

  override def constraintsGetForRelationshipType(typeId: Int): util.Iterator[ConstraintDescriptor] =
    delegate.constraintsGetForRelationshipType(typeId)

  override def constraintsGetForRelationshipTypeNonLocking(typeId: Int): util.Iterator[ConstraintDescriptor] =
    delegate.constraintsGetForRelationshipTypeNonLocking(typeId)

  override def constraintsGetAll(): util.Iterator[ConstraintDescriptor] = delegate.constraintsGetAll()

  override def constraintsGetAllNonLocking(): util.Iterator[ConstraintDescriptor] =
    delegate.constraintsGetAllNonLocking()

  override def indexUsageStats(index: IndexDescriptor): IndexUsageStats = delegate.indexUsageStats(index)

  override def indexSize(index: IndexDescriptor): Long = cachedIndexSample(index).indexSize()
}
