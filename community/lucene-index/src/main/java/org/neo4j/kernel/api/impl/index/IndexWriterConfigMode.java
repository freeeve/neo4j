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
package org.neo4j.kernel.api.impl.index;

import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.lucene_max_merge_at_once;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.lucene_merge_factor;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.lucene_merge_policy;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.lucene_population_max_buffered_docs;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.lucene_population_ram_buffer_size;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.lucene_population_serial_merge_scheduler;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.lucene_segments_per_tier;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.lucene_standard_ram_buffer_size;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.lucene_writer_max_buffered_docs;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.vector_max_merge_at_once;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.vector_merge_policy;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.vector_population_merge_factor;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.vector_population_ram_buffer_size;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.vector_segments_per_tier;
import static org.neo4j.kernel.api.impl.index.lucene.LuceneSettings.vector_standard_merge_factor;

import org.neo4j.configuration.Config;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexWriterConfig;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexWriterConfig.MergePolicyOption;

public enum IndexWriterConfigMode {
    VECTOR(
            false,
            vector_merge_policy,
            lucene_writer_max_buffered_docs,
            lucene_standard_ram_buffer_size,
            vector_standard_merge_factor,
            vector_segments_per_tier,
            vector_max_merge_at_once),
    VECTOR_POPULATION(
            true,
            vector_merge_policy,
            lucene_population_max_buffered_docs,
            vector_population_ram_buffer_size,
            vector_population_merge_factor,
            vector_segments_per_tier,
            vector_max_merge_at_once),
    TEXT(
            false,
            lucene_merge_policy,
            lucene_writer_max_buffered_docs,
            lucene_standard_ram_buffer_size,
            lucene_merge_factor,
            lucene_segments_per_tier,
            lucene_max_merge_at_once),
    TEXT_POPULATION(
            true,
            lucene_merge_policy,
            lucene_population_max_buffered_docs,
            lucene_population_ram_buffer_size,
            lucene_merge_factor,
            lucene_segments_per_tier,
            lucene_max_merge_at_once),
    TRANSACTION_STATE(
            false,
            lucene_merge_policy,
            lucene_writer_max_buffered_docs,
            lucene_standard_ram_buffer_size,
            lucene_merge_factor,
            lucene_segments_per_tier,
            lucene_max_merge_at_once) {
        @Override
        public LuceneIndexWriterConfig visitWithConfig(LuceneIndexWriterConfig writerConfig, Config config) {
            // Index transaction state is never directly persisted, so never commit it on close.
            return super.visitWithConfig(writerConfig, config).setCommitOnClose(false);
        }
    };

    private final boolean forPopulation;
    private final Setting<MergePolicyOption> mergePolicySetting;
    private final Setting<Integer> maxBufferedDocsSetting;
    private final Setting<Double> ramBufferSizeMBSetting;
    private final Setting<Integer> mergeFactorSetting;
    private final Setting<Double> segmentsPerTierSetting;
    private final Setting<Integer> maxMergeAtOnce;

    IndexWriterConfigMode(
            boolean forPopulation,
            Setting<MergePolicyOption> mergePolicySetting,
            Setting<Integer> maxBufferedDocsSetting,
            Setting<Double> RAMBufferSizeMB,
            Setting<Integer> mergeFactorSetting,
            Setting<Double> segmentsPerTierSetting,
            Setting<Integer> maxMergeAtOnce) {
        this.forPopulation = forPopulation;
        this.maxBufferedDocsSetting = maxBufferedDocsSetting;
        this.ramBufferSizeMBSetting = RAMBufferSizeMB;
        this.mergePolicySetting = mergePolicySetting;
        this.mergeFactorSetting = mergeFactorSetting;
        this.segmentsPerTierSetting = segmentsPerTierSetting;
        this.maxMergeAtOnce = maxMergeAtOnce;
    }

    public int getMergeFactor(Config config) {
        return config.get(mergeFactorSetting);
    }

    public MergePolicyOption mergePolicy(Config config) {
        return config.get(mergePolicySetting);
    }

    public double segmentsPerTier(Config config) {
        return config.get(segmentsPerTierSetting);
    }

    public int maxMergeAtOnce(Config config) {
        return config.get(maxMergeAtOnce);
    }

    public LuceneIndexWriterConfig visitWithConfig(LuceneIndexWriterConfig writerConfig, Config config) {
        writerConfig
                .setMaxBufferedDocs(config.get(maxBufferedDocsSetting))
                .setRAMBufferSizeMB(config.get(ramBufferSizeMBSetting));

        if (forPopulation && config.get(lucene_population_serial_merge_scheduler)) {
            // With this setting 'true' we respect the GraphDatabaseInternalSettings.index_population_workers
            // setting and don't use separate lucene threads for merging during population.
            // Population is a background task, and it is probably more important to limit CPU usage than be
            // as fast as possible here.
            writerConfig.useOnThreadConcurrentMergeScheduler();
        }

        return writerConfig;
    }
}
