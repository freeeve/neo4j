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
package org.neo4j.kernel.api.impl.schema.vector;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.neo4j.kernel.api.exceptions.index.IndexEntryConflictException;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory;
import org.neo4j.kernel.api.impl.schema.writer.LucenePartitionIndexWriter;
import org.neo4j.kernel.api.index.IndexUpdater;
import org.neo4j.kernel.impl.index.schema.IndexUpdateIgnoreStrategy;
import org.neo4j.storageengine.api.IndexEntryUpdate;
import org.neo4j.storageengine.api.ValueIndexEntryUpdate;
import org.neo4j.values.VectorCandidate;

class VectorIndexPopulatingUpdater implements IndexUpdater {
    private final LucenePartitionIndexWriter writer;
    private final IndexUpdateIgnoreStrategy ignoreStrategy;
    private final VectorDocumentStructure documentStructure;
    private final Neo4jVectorSimilarityFunction similarityFunction;
    private final LuceneDocumentsFactory documentsFactory;

    VectorIndexPopulatingUpdater(
            LucenePartitionIndexWriter writer,
            IndexUpdateIgnoreStrategy ignoreStrategy,
            VectorDocumentStructure documentStructure,
            Neo4jVectorSimilarityFunction similarityFunction) {
        this.writer = writer;
        this.documentStructure = documentStructure;
        this.ignoreStrategy = ignoreStrategy;
        this.similarityFunction = similarityFunction;
        this.documentsFactory = writer.documentsFactory();
    }

    @Override
    public void process(IndexEntryUpdate update) {
        final var valueUpdate = asValueUpdate(update);
        if (valueUpdate == null) {
            return;
        }

        try {
            final var entityId = valueUpdate.getEntityId();
            final var values = valueUpdate.values();
            final var candidate = VectorCandidate.maybeFrom(values[0]);
            final var updateMode = valueUpdate.updateMode();
            switch (updateMode) {
                case ADDED ->
                    writer.updateDocument(
                            VectorDocumentStructure.ENTITY_ID_KEY,
                            entityId,
                            documentsFactory.createVectorDocument(
                                    documentStructure, entityId, candidate, similarityFunction));
                case CHANGED ->
                    writer.updateOrDeleteDocument(
                            VectorDocumentStructure.ENTITY_ID_KEY,
                            entityId,
                            documentsFactory.createVectorDocument(
                                    documentStructure, entityId, candidate, similarityFunction));
                case REMOVED -> writer.deleteDocuments(VectorDocumentStructure.ENTITY_ID_KEY, entityId);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ValueIndexEntryUpdate asValueUpdate(IndexEntryUpdate update) {
        final var valueUpdate = IndexUpdater.super.asValueUpdate(update);
        return !ignoreStrategy.ignore(valueUpdate) ? ignoreStrategy.toEquivalentUpdate(valueUpdate) : null;
    }

    @Override
    public void close() throws IndexEntryConflictException {}
}
