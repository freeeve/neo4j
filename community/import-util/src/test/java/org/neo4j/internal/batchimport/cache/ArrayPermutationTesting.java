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
package org.neo4j.internal.batchimport.cache;

import java.nio.file.Path;
import org.neo4j.io.fs.FileSystemAbstraction;

public final class ArrayPermutationTesting {
    public static final NumberArrayFactoryCreator ON_HEAP = (fs, workDirectory) -> NumberArrayFactories.OFF_HEAP;
    public static final NumberArrayFactoryCreator OFF_HEAP = (fs, workDirectory) -> NumberArrayFactories.OFF_HEAP;
    public static final NumberArrayFactoryCreator AUTO_NO_SWAP =
            (fs, workDirectory) -> NumberArrayFactories.AUTO_WITHOUT_SWAP;

    public static final NumberArrayFactoryCreator FILE_BACKED = (fs, workDirectory) ->
            NumberArrayFactories.fromBufferFactory(new NumberArraysArgumentProvider.RandomBufferFactory(
                    BufferFactories.OFF_HEAP, BufferFactories.fileBacked(fs, workDirectory)));

    public interface ByteArrayCreator {
        ByteArray createByteArray(NumberArrayFactory factory);
    }

    public interface NumberArrayFactoryCreator {
        NumberArrayFactory createNumberArrayFactory(FileSystemAbstraction fs, Path workDirectory);
    }

    private ArrayPermutationTesting() {}
}
