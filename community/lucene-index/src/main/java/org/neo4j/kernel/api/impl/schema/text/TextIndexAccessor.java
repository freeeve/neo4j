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
package org.neo4j.kernel.api.impl.schema.text;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.neo4j.common.TokenNameLookup;
import org.neo4j.internal.helpers.collection.BoundedIterable;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.kernel.api.impl.index.AbstractLuceneIndexAccessor;
import org.neo4j.kernel.api.impl.index.DatabaseIndex;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocument;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDocumentsFactory;
import org.neo4j.kernel.api.index.IndexUpdater;
import org.neo4j.kernel.api.index.ValueIndexReader;
import org.neo4j.kernel.impl.api.LuceneIndexValueValidator;
import org.neo4j.kernel.impl.api.index.IndexUpdateMode;
import org.neo4j.kernel.impl.index.schema.IndexUpdateIgnoreStrategy;
import org.neo4j.values.ElementIdMapper;
import org.neo4j.values.storable.Value;

public class TextIndexAccessor extends AbstractLuceneIndexAccessor<ValueIndexReader, DatabaseIndex<ValueIndexReader>> {
    private final LuceneIndexValueValidator valueValidator;

    public TextIndexAccessor(
            DatabaseIndex<ValueIndexReader> luceneIndex,
            IndexDescriptor descriptor,
            TokenNameLookup tokenNameLookup,
            ElementIdMapper elementIdMapper,
            IndexUpdateIgnoreStrategy ignoreStrategy) {
        super(luceneIndex, descriptor, ignoreStrategy);
        this.valueValidator = new LuceneIndexValueValidator(descriptor, tokenNameLookup, elementIdMapper);
    }

    @Override
    protected IndexUpdater getIndexUpdater(IndexUpdateMode mode) {
        return new TextIndexUpdater(mode.requiresIdempotency(), mode.requiresRefresh());
    }

    @Override
    public BoundedIterable<Long> newAllEntriesValueReader(
            long fromIdInclusive, long toIdExclusive, CursorContext cursorContext) {
        return super.newAllEntriesReader(LuceneDocumentsFactory::getEntityId, fromIdInclusive, toIdExclusive);
    }

    @Override
    public void validateBeforeCommit(long entityId, Value[] tuple) {
        valueValidator.validate(entityId, tuple);
    }

    private class TextIndexUpdater extends AbstractLuceneIndexUpdater {

        TextIndexUpdater(boolean idempotent, boolean refresh) {
            super(idempotent, refresh);
        }

        @Override
        protected void addIdempotent(long entityId, Value[] values) {
            try {
                LuceneDocument document = documentsFactory.reusableTextDocument(entityId, values);
                writer.updateOrDeleteDocument(LuceneDocumentsFactory.ENTITY_ID_KEY, entityId, document);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        protected void add(long entityId, Value[] values) {
            try {
                LuceneDocument document = documentsFactory.reusableTextDocument(entityId, values);
                writer.nullableAddDocument(document);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        protected void change(long entityId, Value[] values) {
            try {
                LuceneDocument document = documentsFactory.reusableTextDocument(entityId, values);
                writer.updateOrDeleteDocument(LuceneDocumentsFactory.ENTITY_ID_KEY, entityId, document);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        protected void remove(long entityId) {
            try {
                writer.deleteDocuments(LuceneDocumentsFactory.ENTITY_ID_KEY, entityId);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
