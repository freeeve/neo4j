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
package org.neo4j.kernel.api.impl.index.lucene.v9;

import static java.lang.Math.toIntExact;

import java.io.IOException;
import java.util.Collection;
import org.neo4j.kernel.api.impl.index.lucene.LuceneContext;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDirectory;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDirectoryReader;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexWriter;
import org.neo4j.kernel.api.impl.index.lucene.LuceneIndexWriterConfig;
import org.neo4j.kernel.api.impl.index.lucene.v9.codec.VectorCodecV1;
import org.neo4j.kernel.api.impl.index.lucene.v9.codec.VectorCodecV2;
import org.neo4j.shaded.lucene9.codecs.Codec;
import org.neo4j.shaded.lucene9.codecs.CodecUtil;
import org.neo4j.shaded.lucene9.index.CheckIndex;
import org.neo4j.shaded.lucene9.index.DirectoryReader;
import org.neo4j.shaded.lucene9.index.IndexWriter;
import org.neo4j.shaded.lucene9.index.IndexWriterConfig;
import org.neo4j.shaded.lucene9.index.KeepOnlyLastCommitDeletionPolicy;
import org.neo4j.shaded.lucene9.index.LogByteSizeMergePolicy;
import org.neo4j.shaded.lucene9.index.MergePolicy;
import org.neo4j.shaded.lucene9.index.MergeScheduler;
import org.neo4j.shaded.lucene9.index.MergeTrigger;
import org.neo4j.shaded.lucene9.index.SegmentInfos;
import org.neo4j.shaded.lucene9.index.SnapshotDeletionPolicy;
import org.neo4j.shaded.lucene9.store.Directory;
import org.neo4j.shaded.lucene9.store.IOContext;
import org.neo4j.shaded.lucene9.store.IndexInput;
import org.neo4j.shaded.lucene9.util.Version;

public class Lucene9Directory implements LuceneDirectory {
    final Directory directory;

    public Lucene9Directory(Directory directory) {
        this.directory = directory;
    }

    @Override
    public String[] listAll() throws IOException {
        return directory.listAll();
    }

    @Override
    public void deleteFile(String name) throws IOException {
        directory.deleteFile(name);
    }

    @Override
    public boolean indexExists() throws IOException {
        return DirectoryReader.indexExists(directory);
    }

    @Override
    public LuceneDirectoryReader open() throws IOException {
        return new Lucene9DirectoryReader(DirectoryReader.open(directory));
    }

    @Override
    public boolean checkIndexIsClean() throws IOException {
        try (CheckIndex checkIndex = new CheckIndex(directory)) {
            CheckIndex.Status status = checkIndex.checkIndex();
            return status.clean;
        }
    }

    @Override
    public boolean validVersion() throws IOException {
        int createdMajorVersion = new CreatedMajorVersion().run();
        return Version.MIN_SUPPORTED_MAJOR <= createdMajorVersion && createdMajorVersion <= Version.LATEST.major;
    }

    @Override
    public boolean hasCommits() throws IOException {
        return indexExists() && SegmentInfos.readLatestCommit(directory) != null;
    }

    @Override
    public Collection<String> latestCommitFileNames() throws IOException {
        return DirectoryReader.listCommits(directory).getLast().getFileNames();
    }

    @Override
    public LuceneIndexWriter newWriter(LuceneIndexWriterConfig writerConfig) throws IOException {
        IndexWriter indexWriter = new IndexWriter(directory, convertConfig(writerConfig));
        return new Lucene9IndexWriter(indexWriter);
    }

    @Override
    public LuceneContext getLuceneContext() {
        return LuceneContext.LUCENE_9;
    }

    @Override
    public void close() throws IOException {
        directory.close();
    }

    byte[] readFile(String name) throws IOException {
        try (IndexInput in = directory.openInput(name, IOContext.DEFAULT)) {
            byte[] bytes = new byte[toIntExact(in.length())];
            in.readBytes(bytes, 0, bytes.length);
            return bytes;
        }
    }

    private final class CreatedMajorVersion extends SegmentInfos.FindSegmentsFile<Integer> {
        private CreatedMajorVersion() {
            super(directory);
        }

        @Override
        protected Integer doBody(String segmentFileName) throws IOException {
            try (IndexInput input = directory.openInput(segmentFileName, IOContext.DEFAULT)) {
                CodecUtil.readIndexHeader(input);
                input.readVInt(); // Version.major
                input.readVInt(); // Version.minor
                input.readVInt(); // Version.bugfix
                return input.readVInt();
            }
        }
    }

    private static IndexWriterConfig convertConfig(LuceneIndexWriterConfig config) {
        if (config.analyzerOnly) {
            return new IndexWriterConfig(Lucene9Utils.loadAnalyzer(config.analyzer));
        }

        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(Lucene9Utils.loadAnalyzer(config.analyzer));
        if (config.RAMBufferSizeMB != null) {
            indexWriterConfig.setRAMBufferSizeMB(config.RAMBufferSizeMB);
        }
        if (config.maxBufferedDocs != null) {
            indexWriterConfig.setMaxBufferedDocs(config.maxBufferedDocs);
        }

        indexWriterConfig.setCommitOnClose(true);
        indexWriterConfig.setUseCompoundFile(true);
        indexWriterConfig.setMaxFullFlushMergeWaitMillis(0);
        indexWriterConfig.setIndexDeletionPolicy(new SnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy()));

        if (config.codec != null) {
            indexWriterConfig.setCodec(loadCodec(config));
        }
        if (config.useOnThreadConcurrentMergeScheduler) {
            indexWriterConfig.setMergeScheduler(new OnThreadConcurrentMergeScheduler());
        }

        LogByteSizeMergePolicy mergePolicy = new LogByteSizeMergePolicy();
        mergePolicy.setNoCFSRatio(config.noCFSRatio);
        mergePolicy.setMinMergeMB(config.minMergeMB);
        mergePolicy.setMaxMergeMB(config.maxMergeMB);
        mergePolicy.setMergeFactor(config.mergeFactor);
        indexWriterConfig.setMergePolicy(mergePolicy);

        return indexWriterConfig;
    }

    private static Codec loadCodec(LuceneIndexWriterConfig config) {
        org.apache.lucene.codecs.Codec oldCodec = config.codec;
        String codecClassName = oldCodec.getClass().getName();
        return switch (codecClassName) {
            case "org.neo4j.kernel.api.impl.index.lucene.v10.codec.VectorCodecV1" ->
                new VectorCodecV1(oldCodec.knnVectorsFormat().getMaxDimensions(""));
            case "org.neo4j.kernel.api.impl.index.lucene.v10.codec.VectorCodecV2" ->
                new VectorCodecV2(
                        ((org.neo4j.kernel.api.impl.index.lucene.v10.codec.VectorCodecV2) oldCodec).getConfig());
            default -> Codec.forName(config.codec.getName());
        };
    }

    /**
     * This is a {@link MergeScheduler} which is a version of {@link org.apache.lucene.index.SerialMergeScheduler},
     * but with the important difference that multiple threads can run merge of difference sources in parallel.
     * I.e. in the scenario of index population where the population threads that adds documents go and do merge
     * on their individual threads, in parallel with the other population threads. This effectively comes close
     * to the {@link org.apache.lucene.index.ConcurrentMergeScheduler} parallel-wise w/o spawning additional
     * background threads.
     */
    static class OnThreadConcurrentMergeScheduler extends MergeScheduler {
        @Override
        public void merge(MergeSource mergeSource, MergeTrigger trigger) throws IOException {
            while (true) {
                MergePolicy.OneMerge merge = nextMergeSynchronized(mergeSource);
                if (merge == null) {
                    break;
                }
                mergeSource.merge(merge);
            }
        }

        private synchronized MergePolicy.OneMerge nextMergeSynchronized(MergeSource mergeSource) {
            return mergeSource.getNextMerge();
        }

        @Override
        public void close() {}
    }
}
