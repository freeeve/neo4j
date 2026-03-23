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
import org.neo4j.internal.helpers.InclusiveRange;
import org.neo4j.kernel.api.impl.schema.vector.LegacyIndexSettingValidators.LegacyBooleanValidator;
import org.neo4j.kernel.api.impl.schema.vector.LegacyIndexSettingValidators.LegacyIntegerValidator;
import org.neo4j.kernel.api.impl.schema.vector.LegacyIndexSettingValidators.LegacyOptionalIntValidator;
import org.neo4j.kernel.api.impl.schema.vector.LegacyIndexSettingValidators.LegacyReadDefaultOnly;
import org.neo4j.kernel.api.impl.schema.vector.LegacyIndexSettingValidators.LegacyStringLookupValidator;
import org.neo4j.kernel.api.vector.VectorSimilarityFunction;

class LegacyVectorIndexSettingValidators {
    private LegacyVectorIndexSettingValidators() {}

    static LegacyOptionalIntValidator dimensionsValidator(int min, int max) {
        return new LegacyOptionalIntValidator(IndexSetting.vector_Dimensions(), new InclusiveRange<>(min, max), null);
    }

    static LegacyOptionalIntValidator dimensionsValidator(int min, int max, OptionalInt defaultValue) {
        return new LegacyOptionalIntValidator(
                IndexSetting.vector_Dimensions(), new InclusiveRange<>(min, max), defaultValue);
    }

    static LegacySimilarityFunctionValidator similarityFunctionValidator(
            Map<String, VectorSimilarityFunction> supported) {
        return new LegacySimilarityFunctionValidator(supported, null);
    }

    static LegacySimilarityFunctionValidator similarityFunctionValidator(
            Map<String, VectorSimilarityFunction> supported, VectorSimilarityFunction defaultValue) {
        return new LegacySimilarityFunctionValidator(supported, defaultValue);
    }

    static final class LegacySimilarityFunctionValidator extends LegacyStringLookupValidator<VectorSimilarityFunction> {
        private LegacySimilarityFunctionValidator(
                Map<String, VectorSimilarityFunction> supported, VectorSimilarityFunction defaultValue) {
            super(IndexSetting.vector_Similarity_Function(), supported, defaultValue);
        }

        @Override
        protected String key(VectorSimilarityFunction similarityFunction) {
            return similarityFunction.functionName();
        }
    }

    static LegacyReadDefaultOnly<Boolean> quantizationEnabledValidator(boolean readDefault) {
        return new LegacyReadDefaultOnly<>(IndexSetting.vector_Quantization_Enabled(), readDefault);
    }

    static LegacyBooleanValidator quantizationEnabledValidator(
            Set<Boolean> supported, boolean readDefault, boolean writeDefault) {
        return new LegacyBooleanValidator(
                IndexSetting.vector_Quantization_Enabled(), supported, readDefault, writeDefault);
    }

    static LegacyReadDefaultOnly<Integer> hnswMValidator(int readDefault) {
        return new LegacyReadDefaultOnly<>(IndexSetting.vector_Hnsw_M(), readDefault);
    }

    static LegacyIntegerValidator hnswMValidator(int min, int max, int defaultValue) {
        return new LegacyIntegerValidator(IndexSetting.vector_Hnsw_M(), new InclusiveRange<>(min, max), defaultValue);
    }

    static LegacyReadDefaultOnly<Integer> hnswEfConstructionValidator(int readDefault) {
        return new LegacyReadDefaultOnly<>(IndexSetting.vector_Hnsw_Ef_Construction(), readDefault);
    }

    static LegacyIntegerValidator hnswEfConstructionValidator(int min, int max, int defaultValue) {
        return new LegacyIntegerValidator(
                IndexSetting.vector_Hnsw_Ef_Construction(), new InclusiveRange<>(min, max), defaultValue);
    }
}
