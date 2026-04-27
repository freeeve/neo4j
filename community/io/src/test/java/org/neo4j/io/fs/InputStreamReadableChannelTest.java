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
package org.neo4j.io.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;

class InputStreamReadableChannelTest {

    @Test
    void getByteArray_simple() throws IOException {
        var input = "hello".getBytes();

        try (InputStreamReadableChannel channel = new InputStreamReadableChannel(new ByteArrayInputStream(input))) {

            var output = new byte[input.length];
            channel.get(output, input.length);

            assertThat(output).isEqualTo(input);
        }
    }

    @Test
    void getByteArray_readPastStreamEnd() throws IOException {
        var input = "small".getBytes();

        try (InputStreamReadableChannel channel = new InputStreamReadableChannel(new ByteArrayInputStream(input))) {

            var output = new byte[input.length + 2];
            assertThatThrownBy(() -> channel.get(output, output.length)).isInstanceOf(ReadPastEndException.class);
        }
    }

    @Test
    void getByteArray_readMoreThanByteArraySize() throws IOException {
        var input = "too large".getBytes();

        try (InputStreamReadableChannel channel = new InputStreamReadableChannel(new ByteArrayInputStream(input))) {

            var output = new byte[2];

            // Asking for more than we can handle.
            assertThatThrownBy(() -> channel.get(output, input.length)).isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Test
    void getByteArray_readLessThanByteArraySize() throws IOException {
        var input = "just a slice please".getBytes();

        try (InputStreamReadableChannel channel = new InputStreamReadableChannel(new ByteArrayInputStream(input))) {

            var output = new byte[input.length];
            // Ask for less than we need (make sure we respect the length parameter)
            channel.get(output, 6);

            assertThat(output).startsWith("just a".getBytes()).endsWith(new byte["slice please".length()]);
        }
    }

    @Test
    void getByteArray_streamReturnsFewerBytesThanRequested() throws IOException {
        var stream = new SomeExceptionsInputStream(49, "An exception", 50, 51, "Another exception", 52, 53);

        byte[] expected = "12345".getBytes();
        byte[] output = new byte[expected.length];

        try (var channel = new InputStreamReadableChannel(stream)) {
            channel.get(output, output.length);
        }

        assertThat(output).isEqualTo(expected);
    }

    /**
     * An input stream that will potentially give less than a full result when asked to fill an array. This is
     * allowed by the API, but can surprise users...
     */
    private static class SomeExceptionsInputStream extends InputStream {
        private final List<IntSupplier> items = new ArrayList<>();

        public SomeExceptionsInputStream(Object... results) {
            for (Object result : results) {
                if (result instanceof Integer someInt) {
                    items.add(() -> someInt);
                } else {
                    items.add(() -> {
                        throw new RuntimeException(result.toString());
                    });
                }
            }
        }

        @Override
        public int read() throws IOException {
            if (items.isEmpty()) {
                return -1;
            }
            try {
                return items.remove(0).getAsInt();
            } catch (RuntimeException e) {
                throw new IOException(e);
            }
        }
    }
}
