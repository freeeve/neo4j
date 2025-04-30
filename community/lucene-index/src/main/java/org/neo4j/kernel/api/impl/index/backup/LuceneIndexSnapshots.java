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
package org.neo4j.kernel.api.impl.index.backup;

import static org.neo4j.internal.helpers.collection.Iterators.emptyResourceIterator;

import java.io.IOException;
import java.nio.file.Path;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDirectory;

/**
 * Create iterators over Lucene index files for a particular index commit.
 * Applicable only to a single Lucene index partition.
 */
public final class LuceneIndexSnapshots {
    private LuceneIndexSnapshots() {}

    /**
     * Create index snapshot iterator for a read only index.
     * @param indexFolder index location folder
     * @param directory index directory
     * @return index file name resource iterator
     * @throws IOException
     */
    public static ResourceIterator<Path> forIndex(Path indexFolder, LuceneDirectory directory) throws IOException {
        return directory.hasCommits()
                ? new ReadOnlyIndexSnapshotFileIterator(indexFolder, directory.latestCommitFileNames())
                : emptyResourceIterator();
    }
}
