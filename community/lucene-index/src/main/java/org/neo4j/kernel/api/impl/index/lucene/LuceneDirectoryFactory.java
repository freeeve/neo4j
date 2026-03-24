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

import java.io.IOException;
import java.nio.file.Path;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.api.impl.index.storage.DirectoryFactory;
import org.neo4j.util.FeatureToggles;

public interface LuceneDirectoryFactory {
    int MAX_MERGE_SIZE_MB = FeatureToggles.getInteger(DirectoryFactory.class, "max_merge_size_mb", 5);
    int MAX_CACHED_MB = FeatureToggles.getInteger(DirectoryFactory.class, "max_cached_mb", 50);
    boolean USE_DEFAULT_DIRECTORY_FACTORY =
            FeatureToggles.flag(DirectoryFactory.class, "default_directory_factory", true);

    LuceneDirectory openPersistent(Path dir) throws IOException;

    LuceneDirectory inMemoryDirectory();

    DirectoryFactory newInMemoryDirectoryFactory();

    DirectoryFactory newInMemoryDirectoryFactory(FileSystemAbstraction fs);
}
