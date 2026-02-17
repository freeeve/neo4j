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

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.kernel.api.impl.schema.vector.IndexSettingValidators.BooleanValidator;
import org.neo4j.kernel.api.impl.schema.vector.IndexSettingValidators.IntegerValidator;
import org.neo4j.kernel.api.impl.schema.vector.IndexSettingValidators.OptionalIntValidator;
import org.neo4j.kernel.api.impl.schema.vector.IndexSettingValidators.ReadDefaultOnly;
import org.neo4j.kernel.api.impl.schema.vector.IndexSettingValidators.StringLookupValidator;
import org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.Range;
import org.neo4j.kernel.api.vector.VectorSimilarityFunction;

class VectorIndexSettingValidators {
    private VectorIndexSettingValidators() {}

    static OptionalIntValidator dimensionsValidator(int min, int max) {
        return new OptionalIntValidator(IndexSetting.vector_Dimensions(), new Range<>(min, max), null);
    }

    static OptionalIntValidator dimensionsValidator(int min, int max, OptionalInt defaultValue) {
        return new OptionalIntValidator(IndexSetting.vector_Dimensions(), new Range<>(min, max), defaultValue);
    }

    static SimilarityFunctionValidator similarityFunctionValidator(Map<String, VectorSimilarityFunction> supported) {
        return new SimilarityFunctionValidator(supported, null);
    }

    static SimilarityFunctionValidator similarityFunctionValidator(
            Map<String, VectorSimilarityFunction> supported, VectorSimilarityFunction defaultValue) {
        return new SimilarityFunctionValidator(supported, defaultValue);
    }

    static final class SimilarityFunctionValidator extends StringLookupValidator<VectorSimilarityFunction> {
        private SimilarityFunctionValidator(
                Map<String, VectorSimilarityFunction> supported, VectorSimilarityFunction defaultValue) {
            super(IndexSetting.vector_Similarity_Function(), supported, defaultValue);
        }

        @Override
        protected String key(VectorSimilarityFunction similarityFunction) {
            return similarityFunction.functionName();
        }
    }

    static ReadDefaultOnly<Boolean> quantizationEnabledValidator(boolean readDefault) {
        return new ReadDefaultOnly<>(IndexSetting.vector_Quantization_Enabled(), readDefault);
    }

    static BooleanValidator quantizationEnabledValidator(
            Set<Boolean> supported, boolean readDefault, boolean writeDefault) {
        return new BooleanValidator(IndexSetting.vector_Quantization_Enabled(), supported, readDefault, writeDefault);
    }

    static ReadDefaultOnly<Integer> hnswMValidator(int readDefault) {
        return new ReadDefaultOnly<>(IndexSetting.vector_Hnsw_M(), readDefault);
    }

    static IntegerValidator hnswMValidator(int min, int max, int defaultValue) {
        return new IntegerValidator(IndexSetting.vector_Hnsw_M(), new Range<>(min, max), defaultValue);
    }

    static ReadDefaultOnly<Integer> hnswEfConstructionValidator(int readDefault) {
        return new ReadDefaultOnly<>(IndexSetting.vector_Hnsw_Ef_Construction(), readDefault);
    }

    static IntegerValidator hnswEfConstructionValidator(int min, int max, int defaultValue) {
        return new IntegerValidator(IndexSetting.vector_Hnsw_Ef_Construction(), new Range<>(min, max), defaultValue);
    }
}
