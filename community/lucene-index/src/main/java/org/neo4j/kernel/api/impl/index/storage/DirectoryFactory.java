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
package org.neo4j.kernel.api.impl.index.storage;

import java.io.IOException;
import java.nio.file.Path;
import org.neo4j.io.IOUtils;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.api.impl.index.lucene.LuceneContext;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDirectory;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDirectoryFactory;
import org.neo4j.kernel.api.impl.index.lucene.v9.Lucene9DirectoryFactory;

public interface DirectoryFactory extends AutoCloseable {
    LuceneDirectoryFactory CURRENT = Lucene9DirectoryFactory.INSTANCE;

    LuceneDirectory open(Path dir) throws IOException;

    LuceneContext getContext();

    static DirectoryFactory directoryFactory(LuceneContext context, FileSystemAbstraction fs) {
        return fs.isPersistent() ? DirectoryFactory.PERSISTENT : CURRENT.newInMemoryDirectoryFactory(fs);
    }

    abstract class FallbackDirectoryFactory implements DirectoryFactory {
        private final LuceneDirectoryFactory[] directoryFactories;

        FallbackDirectoryFactory(LuceneDirectoryFactory... directoryFactories) {
            this.directoryFactories = directoryFactories;
        }

        @Override
        public LuceneDirectory open(Path dir) throws IOException {
            for (LuceneDirectoryFactory directoryFactory : directoryFactories) {
                LuceneDirectory directory = actualOpen(directoryFactory, dir);
                if (!directory.indexExists() || directory.validVersion()) {
                    return directory;
                }
                directory.close();
            }
            throw new IOException("No compatible reader for index in " + dir + " found.");
        }

        protected abstract LuceneDirectory actualOpen(LuceneDirectoryFactory directoryFactory, Path dir)
                throws IOException;

        @Override
        public void close() throws IOException {
            IOUtils.closeAll(directoryFactories);
        }
    }

    class PersistentDirectoryFactory extends FallbackDirectoryFactory {
        PersistentDirectoryFactory(LuceneDirectoryFactory... directoryFactories) {
            super(directoryFactories);
        }

        @Override
        protected LuceneDirectory actualOpen(LuceneDirectoryFactory directoryFactory, Path dir) throws IOException {
            return directoryFactory.openPersistent(dir);
        }

        @Override
        public LuceneContext getContext() {
            return LuceneContext.LUCENE_9;
        }
    }

    DirectoryFactory PERSISTENT = new PersistentDirectoryFactory(CURRENT);

    static DirectoryFactory inMemory() {
        return CURRENT.newInMemoryDirectoryFactory();
    }

    static DirectoryFactory inMemory(FileSystemAbstraction fs) {
        return CURRENT.newInMemoryDirectoryFactory(fs);
    }
}
