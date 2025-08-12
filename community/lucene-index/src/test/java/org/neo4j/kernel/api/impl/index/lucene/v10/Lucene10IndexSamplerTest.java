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
package org.neo4j.kernel.api.impl.index.lucene.v10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.neo4j.io.pagecache.context.CursorContext.NULL_CONTEXT;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.Config;
import org.neo4j.internal.helpers.collection.MapUtil;
import org.neo4j.internal.kernel.api.exceptions.schema.IndexNotFoundKernelException;
import org.neo4j.kernel.api.impl.schema.TaskCoordinator;
import org.neo4j.kernel.api.index.IndexSample;
import org.neo4j.kernel.impl.api.index.IndexSamplingConfig;

class Lucene10IndexSamplerTest {
    private final TaskCoordinator taskControl = new TaskCoordinator();
    private final IndexSamplingConfig indexSamplingConfig = new IndexSamplingConfig(Config.defaults());

    @Test
    void nonUniqueSamplingCancel() throws IOException {
        Terms terms = getTerms("test", 1);
        Map<String, Terms> fieldTermsMap = MapUtil.genericMap("0string", terms, "id", terms, "0string", terms);
        Lucene10IndexReaderStub indexReader = new Lucene10IndexReaderStub(new SamplingFields(fieldTermsMap));
        IndexSearcher indexSearcher = mock(IndexSearcher.class);
        Lucene10IndexSearcher luceneIndexSearcher = new Lucene10IndexSearcher(indexSearcher);
        when(luceneIndexSearcher.getIndexReader()).thenReturn(indexReader);

        Lucene10IndexSampler lucene10IndexSampler = createSampler(luceneIndexSearcher);
        taskControl.cancel();
        IndexNotFoundKernelException notFoundKernelException = assertThrows(
                IndexNotFoundKernelException.class,
                () -> lucene10IndexSampler.sampleIndex(NULL_CONTEXT, new AtomicBoolean()));
        assertEquals("Index dropped while sampling.", notFoundKernelException.getMessage());
    }

    @Test
    void nonUniqueIndexSampling() throws Exception {
        Terms aTerms = getTerms("a", 1);
        Terms idTerms = getTerms("id", 2);
        Terms bTerms = getTerms("b", 3);
        Map<String, Terms> fieldTermsMap = MapUtil.genericMap("0string", aTerms, "id", idTerms, "0array", bTerms);
        Lucene10IndexReaderStub indexReader = new Lucene10IndexReaderStub(new SamplingFields(fieldTermsMap));
        indexReader.setElements(new String[4]);
        IndexSearcher indexSearcher = mock(IndexSearcher.class);
        Lucene10IndexSearcher luceneIndexSearcher = new Lucene10IndexSearcher(indexSearcher);
        when(luceneIndexSearcher.getIndexReader()).thenReturn(indexReader);

        assertEquals(
                new IndexSample(4, 2, 4),
                createSampler(luceneIndexSearcher).sampleIndex(NULL_CONTEXT, new AtomicBoolean()));
    }

    private Lucene10IndexSampler createSampler(Lucene10IndexSearcher luceneIndexSearcher) {
        return new Lucene10IndexSampler(luceneIndexSearcher, taskControl, indexSamplingConfig);
    }

    private static Terms getTerms(String value, int frequency) throws IOException {
        TermsEnum termsEnum = mock(TermsEnum.class);
        Terms terms = mock(Terms.class);
        when(terms.iterator()).thenReturn(termsEnum);
        when(termsEnum.next()).thenReturn(new BytesRef(value.getBytes())).thenReturn(null);
        when(termsEnum.docFreq()).thenReturn(frequency);
        return terms;
    }

    private static class SamplingFields extends Fields {

        private final Map<String, Terms> fieldTermsMap;

        SamplingFields(Map<String, Terms> fieldTermsMap) {
            this.fieldTermsMap = fieldTermsMap;
        }

        @Override
        public Iterator<String> iterator() {
            return fieldTermsMap.keySet().iterator();
        }

        @Override
        public Terms terms(String field) {
            return fieldTermsMap.get(field);
        }

        @Override
        public int size() {
            return fieldTermsMap.size();
        }
    }
}
