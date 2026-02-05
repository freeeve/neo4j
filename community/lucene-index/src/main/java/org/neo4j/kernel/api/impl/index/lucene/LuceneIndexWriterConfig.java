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
package org.neo4j.kernel.api.impl.index.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriterConfig;
import org.neo4j.kernel.api.impl.index.lucene.codec.LuceneCodec;
import org.neo4j.logging.LogProvider;
import org.neo4j.logging.NullLogProvider;

public class LuceneIndexWriterConfig {
    public static final int DISABLE_AUTO_FLUSH = IndexWriterConfig.DISABLE_AUTO_FLUSH;
    public static final double DEFAULT_RAM_BUFFER_SIZE_MB = IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB;
    public final boolean analyzerOnly;
    public final Analyzer analyzer;
    public LogProvider logProvider = NullLogProvider.getInstance();
    public boolean useOnThreadConcurrentMergeScheduler;
    public Integer maxBufferedDocs;
    public Double RAMBufferSizeMB;
    public boolean commitOnClose = true;
    public LuceneCodec codec;
    public double noCFSRatio;
    public double minMergeMB;
    public double maxMergeMB;
    public int mergeFactor;

    /**
     * Only configure analyzer, use for in memory only implementation that does not require anything else.
     */
    public static LuceneIndexWriterConfig analyzerOnly(Analyzer analyzer) {
        return new LuceneIndexWriterConfig(analyzer, true);
    }

    public LuceneIndexWriterConfig(Analyzer analyzer) {
        this(analyzer, false);
    }

    private LuceneIndexWriterConfig(Analyzer analyzer, boolean analyzerOnly) {
        this.analyzer = analyzer;
        this.analyzerOnly = analyzerOnly;
    }

    public LuceneIndexWriterConfig setLogProvider(LogProvider logProvider) {
        this.logProvider = logProvider;
        return this;
    }

    public LuceneIndexWriterConfig setRAMBufferSizeMB(double RAMBufferSizeMB) {
        this.RAMBufferSizeMB = RAMBufferSizeMB;
        return this;
    }

    public LuceneIndexWriterConfig setMaxBufferedDocs(int maxBufferedDocs) {
        this.maxBufferedDocs = maxBufferedDocs;
        return this;
    }

    public LuceneIndexWriterConfig useOnThreadConcurrentMergeScheduler() {
        this.useOnThreadConcurrentMergeScheduler = true;
        return this;
    }

    public LuceneIndexWriterConfig setCommitOnClose(boolean commitOnClose) {
        this.commitOnClose = commitOnClose;
        return this;
    }

    public LuceneIndexWriterConfig setCodec(LuceneCodec codec) {
        this.codec = codec;
        return this;
    }

    public void setMergingParameters(double noCFSRatio, double minMergeMB, double maxMergeMB, int mergeFactor) {
        this.noCFSRatio = noCFSRatio;
        this.minMergeMB = minMergeMB;
        this.maxMergeMB = maxMergeMB;
        this.mergeFactor = mergeFactor;
    }
}
