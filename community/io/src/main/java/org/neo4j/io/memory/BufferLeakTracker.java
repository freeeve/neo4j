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
package org.neo4j.io.memory;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.neo4j.internal.helpers.Exceptions;
import org.neo4j.internal.utils.DumpUtils;

public sealed interface BufferLeakTracker
        permits BufferLeakTracker.TrackingBufferLeakTracker, BufferLeakTracker.EmptyDefaultBufferLeakTracker {

    BufferLeakTracker DISABLED_TRACKER = new EmptyDefaultBufferLeakTracker();
    BufferLeakTracker ENABLED_TRACKER = new TrackingBufferLeakTracker();

    ByteBuffer track(ByteBuffer buffer);

    void release(ByteBuffer byteBuffer);

    void checkLeaks(Supplier<String> testPlanDescriptionProvider);

    final class TrackingBufferLeakTracker implements BufferLeakTracker {

        private final Map<ByteBuffer, Exception> trackerBuffers = Collections.synchronizedMap(new IdentityHashMap<>());

        private TrackingBufferLeakTracker() {}

        @Override
        public ByteBuffer track(ByteBuffer buffer) {
            trackerBuffers.put(buffer, new RuntimeException("Buffer allocation"));
            return buffer;
        }

        @Override
        public void release(ByteBuffer byteBuffer) {
            trackerBuffers.remove(byteBuffer);
        }

        @Override
        public void checkLeaks(Supplier<String> testPlanDescriptionProvider) {
            if (trackerBuffers.isEmpty()) {
                return;
            }
            var exceptionBuilder = new StringBuilder("""
                                                          ***WARNING***
                    Native buffer leak(s) has been detected by the buffer leak tracker. The test session will be marked as failed.
                    Please review the details of unreleased allocations from the tests executed in the current session below.
                    Last executed tests:
                    """);
            exceptionBuilder
                    .append(testPlanDescriptionProvider.get())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
            exceptionBuilder.append("Leakage traces:").append(System.lineSeparator());
            for (Map.Entry<ByteBuffer, Exception> entry : trackerBuffers.entrySet()) {
                exceptionBuilder
                        .append("Leaked buffer: ")
                        .append(entry.getKey())
                        .append(" allocated at: ")
                        .append(Exceptions.stringify(entry.getValue()));
            }
            exceptionBuilder.append("All active threads dump:").append(System.lineSeparator());
            exceptionBuilder.append(DumpUtils.threadDump());
            throw new RuntimeException(exceptionBuilder.toString());
        }
    }

    final class EmptyDefaultBufferLeakTracker implements BufferLeakTracker {

        private EmptyDefaultBufferLeakTracker() {}

        @Override
        public ByteBuffer track(ByteBuffer buffer) {
            return buffer;
        }

        @Override
        public void release(ByteBuffer byteBuffer) {}

        @Override
        public void checkLeaks(Supplier<String> testPlanDescriptionProvider) {}
    }
}
