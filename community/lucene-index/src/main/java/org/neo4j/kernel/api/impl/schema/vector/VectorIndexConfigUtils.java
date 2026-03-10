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

import static org.neo4j.internal.schema.IndexConfigUtils.INDEX_SETTING_COMPARATOR;
import static org.neo4j.internal.schema.InternalIndexSetting.VECTOR_QUANTIZATION_TYPE;

import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.kernel.KernelVersion;

public class VectorIndexConfigUtils {
    static final IndexSetting DIMENSIONS = IndexSetting.vector_Dimensions();
    static final IndexSetting SIMILARITY_FUNCTION = IndexSetting.vector_Similarity_Function();
    static final IndexSetting QUANTIZATION_ENABLED = IndexSetting.vector_Quantization_Enabled();
    static final IndexSetting HNSW_M = IndexSetting.vector_Hnsw_M();
    static final IndexSetting HNSW_EF_CONSTRUCTION = IndexSetting.vector_Hnsw_Ef_Construction();

    public static final SortedMap<IndexSetting, KernelVersion> INDEX_SETTING_INTRODUCED_VERSIONS;

    static {
        final SortedMap<IndexSetting, KernelVersion> indexSettingIntroducedVersions =
                new TreeMap<>(INDEX_SETTING_COMPARATOR);
        indexSettingIntroducedVersions.put(DIMENSIONS, KernelVersion.VERSION_NODE_VECTOR_INDEX_INTRODUCED);
        indexSettingIntroducedVersions.put(SIMILARITY_FUNCTION, KernelVersion.VERSION_NODE_VECTOR_INDEX_INTRODUCED);
        indexSettingIntroducedVersions.put(
                QUANTIZATION_ENABLED, KernelVersion.VERSION_VECTOR_QUANTIZATION_AND_HYPER_PARAMS);
        indexSettingIntroducedVersions.put(VECTOR_QUANTIZATION_TYPE, KernelVersion.VERSION_VECTOR_BINARY_QUANTIZATION);
        indexSettingIntroducedVersions.put(HNSW_M, KernelVersion.VERSION_VECTOR_QUANTIZATION_AND_HYPER_PARAMS);
        indexSettingIntroducedVersions.put(
                HNSW_EF_CONSTRUCTION, KernelVersion.VERSION_VECTOR_QUANTIZATION_AND_HYPER_PARAMS);
        INDEX_SETTING_INTRODUCED_VERSIONS = Collections.unmodifiableSortedMap(indexSettingIntroducedVersions);
    }
}
