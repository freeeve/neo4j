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

import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.api.impl.index.lucene.v10.Lucene10DirectoryFactory;
import org.neo4j.kernel.api.impl.index.lucene.v10.Lucene10DocumentsFactory;
import org.neo4j.kernel.api.impl.index.lucene.v9.Lucene9DirectoryFactory;
import org.neo4j.kernel.api.impl.index.lucene.v9.Lucene9DocumentsFactory;

public enum LuceneContext {
    LUCENE_10(Lucene10DirectoryFactory.INSTANCE, Lucene10DocumentsFactory.INSTANCE),
    LUCENE_9(Lucene9DirectoryFactory.INSTANCE, Lucene9DocumentsFactory.INSTANCE);

    public static LuceneContext getDefault() {
        return LUCENE_10;
    }

    public static LuceneContext getDefault(KernelVersion version) {
        if (version.isAtLeast(KernelVersion.VERSION_LUCENE_10_INTRODUCED)) {
            return LUCENE_10;
        }
        return LUCENE_9;
    }

    private final LuceneDirectoryFactory directoryFactory;
    private final LuceneDocumentsFactory documentsFactory;

    LuceneContext(LuceneDirectoryFactory directoryFactory, LuceneDocumentsFactory documentsFactory) {
        this.directoryFactory = directoryFactory;
        this.documentsFactory = documentsFactory;
    }

    public LuceneDirectoryFactory directoryFactory() {
        return directoryFactory;
    }

    public LuceneDocumentsFactory documentsFactory() {
        return documentsFactory;
    }
}
