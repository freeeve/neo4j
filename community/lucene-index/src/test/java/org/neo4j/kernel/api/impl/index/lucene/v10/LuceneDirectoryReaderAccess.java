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
package org.neo4j.kernel.api.impl.index.lucene.v10;

import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.StandardDirectoryReader;
import org.neo4j.kernel.api.impl.index.lucene.LuceneDirectoryReader;

public class LuceneDirectoryReaderAccess {
    public static SegmentInfos getSegmentInfos(LuceneDirectoryReader reader) {
        if (reader instanceof Lucene10DirectoryReader lucene10Reader) {
            if (lucene10Reader.reader instanceof StandardDirectoryReader directoryReader) {
                return directoryReader.getSegmentInfos();
            }
        }
        throw new IllegalArgumentException("Can only read segment infos for Lucene 10 StandardDirectoryReader");
    }
}
