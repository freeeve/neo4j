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

import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.DIMENSIONS;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.HNSW_EF_CONSTRUCTION;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.HNSW_M;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.QUANTIZATION_ENABLED;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.SIMILARITY_FUNCTION;

import java.util.Collections;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.schema.IndexConfigValidationRecord.Valid;
import org.neo4j.internal.schema.TypedIndexConfig;
import org.neo4j.kernel.api.vector.VectorSimilarityFunction;

public class VectorIndexConfig extends TypedIndexConfig {
    public static final VectorIndexConfig EMPTY = new VectorIndexConfig();

    private final VectorIndexVersion version;
    private final OptionalInt dimensions;
    private final VectorSimilarityFunction similarityFunction;
    private final VectorQuantizationType quantization;
    private final HnswConfig hnswConfig;

    VectorIndexConfig(VectorIndexVersion version, Set<IndexSetting> acceptedSettings, Iterable<Valid> records) {
        super(version.descriptor(), acceptedSettings, records);
        this.version = version;
        this.dimensions = get(DIMENSIONS);
        this.similarityFunction = get(SIMILARITY_FUNCTION);
        final boolean quantizationEnabled = get(QUANTIZATION_ENABLED);
        this.quantization = quantizationEnabled ? VectorQuantizationType.SCALAR : VectorQuantizationType.NONE;
        this.hnswConfig = new HnswConfig(get(HNSW_M), get(HNSW_EF_CONSTRUCTION));
    }

    private VectorIndexConfig() {
        super(VectorIndexVersion.UNKNOWN.descriptor(), Collections.emptySet(), Iterables.empty());
        this.version = VectorIndexVersion.UNKNOWN;
        this.dimensions = OptionalInt.empty();
        this.similarityFunction = null;
        this.quantization = VectorQuantizationType.NONE;
        this.hnswConfig = HnswConfig.DUMMY;
    }

    public VectorIndexVersion version() {
        return version;
    }

    public OptionalInt dimensions() {
        return dimensions;
    }

    public VectorSimilarityFunction similarityFunction() {
        return similarityFunction;
    }

    public boolean quantizationEnabled() {
        return quantization != VectorQuantizationType.NONE;
    }

    public VectorQuantizationType quantization() {
        return quantization;
    }

    public HnswConfig hnsw() {
        return hnswConfig;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensions, similarityFunction, quantization, hnswConfig);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VectorIndexConfig that)) {
            return false;
        }
        return Objects.equals(this.dimensions, that.dimensions)
                && Objects.equals(this.similarityFunction, that.similarityFunction)
                && this.quantization == that.quantization
                && Objects.equals(this.hnswConfig, that.hnswConfig);
    }

    public record HnswConfig(int M, int efConstruction) {
        public static final HnswConfig DUMMY = new HnswConfig(16, 100);
    }
}
