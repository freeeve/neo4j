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
package org.neo4j.kernel.api.impl.index.lucene.v9.codec;

import java.io.IOException;
import org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfig.HnswConfig;
import org.neo4j.shaded.lucene9.codecs.KnnVectorsFormat;
import org.neo4j.shaded.lucene9.codecs.KnnVectorsReader;
import org.neo4j.shaded.lucene9.codecs.KnnVectorsWriter;
import org.neo4j.shaded.lucene9.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.neo4j.shaded.lucene9.index.SegmentReadState;
import org.neo4j.shaded.lucene9.index.SegmentWriteState;

public class LuceneKnnVectorFormatV2 extends KnnVectorsFormat {
    private static final String LUCENE_VECTOR_FORMAT_V2_NAME = "LuceneKnnVectorFormatV2";
    private final KnnVectorsFormat vectorsFormat;
    private final int maxDimensions;

    // This constructor is only needed for Lucene Service Loader
    public LuceneKnnVectorFormatV2() {
        this(Integer.MAX_VALUE, HnswConfig.DUMMY);
    }

    public LuceneKnnVectorFormatV2(int maxDimensions, HnswConfig hnswConfig) {
        super(LUCENE_VECTOR_FORMAT_V2_NAME);
        this.maxDimensions = maxDimensions;
        this.vectorsFormat = new Lucene99HnswVectorsFormat(hnswConfig.M(), hnswConfig.efConstruction());
    }

    @Override
    public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
        return vectorsFormat.fieldsWriter(state);
    }

    @Override
    public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
        return vectorsFormat.fieldsReader(state);
    }

    @Override
    public int getMaxDimensions(String fieldName) {
        return maxDimensions;
    }
}
