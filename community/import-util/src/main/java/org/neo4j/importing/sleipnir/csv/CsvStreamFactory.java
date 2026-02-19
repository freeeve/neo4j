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
package org.neo4j.importing.sleipnir.csv;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

public final class CsvStreamFactory {
    private static final CsvStreamProvider SWAR_CONSTRUCTOR = SWARCsvStream::new;
    private static final CsvStreamProvider PROVIDER = loadBestProvider();

    private CsvStreamFactory() {}

    public static CsvStream create(int separatorCodePoint, int quoteCodePoint, boolean legacyStyleEscape) {
        if ((separatorCodePoint & 0xff) != separatorCodePoint || (quoteCodePoint & 0xff) != quoteCodePoint) {
            // TODO: add fallback for multi-byte separator support
            throw new UnsupportedOperationException("Multi-byte separators not supported yet");
        }
        return PROVIDER.create(separatorCodePoint, quoteCodePoint, legacyStyleEscape);
    }

    private static CsvStreamProvider loadBestProvider() {
        int runtimeVersion = Runtime.version().feature();
        if (runtimeVersion < 25) {
            // SIMD provider requires the FFM API
            return SWAR_CONSTRUCTOR;
        }

        ModuleLayer layer = CsvStreamFactory.class.getModule().getLayer();
        if (layer == null) {
            layer = ModuleLayer.boot();
        }
        Optional<Module> vectorModule = layer.findModule("jdk.incubator.vector");
        if (vectorModule.isEmpty()) {
            // Vector incubator not enabled
            return SWAR_CONSTRUCTOR;
        }
        vectorModule.ifPresent(CsvStreamFactory.class.getModule()::addReads);
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> clazz = lookup.findClass("org.neo4j.importing.sleipnir.csv.simd.SIMDCsvStreamProvider");
            MethodHandle constructor = lookup.findConstructor(clazz, MethodType.methodType(void.class));
            try {
                return (CsvStreamProvider) constructor.invoke();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } catch (ClassNotFoundException e) {
            // Vector jar is not present
            return SWAR_CONSTRUCTOR;
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new LinkageError("SIMD provider is missing a public default constructor", e);
        }
    }
}
