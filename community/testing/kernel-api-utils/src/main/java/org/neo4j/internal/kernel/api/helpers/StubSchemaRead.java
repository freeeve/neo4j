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
package org.neo4j.internal.kernel.api.helpers;

import java.util.Iterator;
import java.util.function.Function;
import org.neo4j.internal.kernel.api.InternalIndexState;
import org.neo4j.internal.kernel.api.PopulationProgress;
import org.neo4j.internal.kernel.api.SchemaRead;
import org.neo4j.internal.kernel.api.SchemaReadCore;
import org.neo4j.internal.kernel.api.exceptions.schema.IndexNotFoundKernelException;
import org.neo4j.internal.schema.ConstraintDescriptor;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.IndexType;
import org.neo4j.internal.schema.SchemaDescriptor;
import org.neo4j.kernel.api.index.IndexSample;
import org.neo4j.kernel.api.index.IndexUsageStats;

public class StubSchemaRead implements SchemaRead {
    @Override
    public Iterator<IndexDescriptor> indexForSchemaNonTransactional(SchemaDescriptor schema) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexDescriptor indexForSchemaAndIndexTypeNonTransactional(SchemaDescriptor schema, IndexType indexType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<IndexDescriptor> indexForSchemaNonLocking(SchemaDescriptor schema) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<IndexDescriptor> getLabelIndexesNonLocking(int labelId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<IndexDescriptor> getRelTypeIndexesNonLocking(int relTypeId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<IndexDescriptor> indexesGetAllNonLocking() {
        throw new UnsupportedOperationException();
    }

    @Override
    public double indexUniqueValuesSelectivity(IndexDescriptor index) throws IndexNotFoundKernelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long indexSize(IndexDescriptor index) throws IndexNotFoundKernelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexSample indexSample(IndexDescriptor index) throws IndexNotFoundKernelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<ConstraintDescriptor> constraintsGetForSchema(SchemaDescriptor descriptor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<ConstraintDescriptor> constraintsGetForSchemaNonLocking(SchemaDescriptor descriptor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean constraintExists(ConstraintDescriptor descriptor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SchemaReadCore snapshot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long indexGetOwningUniquenessConstraintId(IndexDescriptor index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long indexGetOwningUniquenessConstraintIdNonLocking(IndexDescriptor index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <K, V> V schemaStateGetOrCreate(K key, Function<K, V> creator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void schemaStateFlush() {}

    @Override
    public void assertIndexExists(IndexDescriptor index) throws IndexNotFoundKernelException {}

    @Override
    public IndexDescriptor indexGetForName(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConstraintDescriptor constraintGetForName(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<IndexDescriptor> index(SchemaDescriptor schema) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexDescriptor index(SchemaDescriptor schema, IndexType type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<IndexDescriptor> indexesGetForLabel(int labelId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<IndexDescriptor> indexesGetForRelationshipType(int relationshipType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<IndexDescriptor> indexesGetAll() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InternalIndexState indexGetState(IndexDescriptor index) throws IndexNotFoundKernelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InternalIndexState indexGetStateNonLocking(IndexDescriptor index) throws IndexNotFoundKernelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PopulationProgress indexGetPopulationProgress(IndexDescriptor index) throws IndexNotFoundKernelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String indexGetFailure(IndexDescriptor index) throws IndexNotFoundKernelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<ConstraintDescriptor> constraintsGetForLabel(int labelId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<ConstraintDescriptor> constraintsGetForLabelNonLocking(int labelId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<ConstraintDescriptor> constraintsGetForRelationshipType(int typeId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<ConstraintDescriptor> constraintsGetForRelationshipTypeNonLocking(int typeId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<ConstraintDescriptor> constraintsGetAll() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<ConstraintDescriptor> constraintsGetAllNonLocking() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IndexUsageStats indexUsageStats(IndexDescriptor index) throws IndexNotFoundKernelException {
        throw new UnsupportedOperationException();
    }
}
