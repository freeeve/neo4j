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
package org.neo4j.bolt.discovery.packet.writer;

import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.neo4j.bolt.testing.annotation.StrictBufferExtension;
import org.neo4j.bolt.testing.assertions.ByteBufAssertions;
import org.neo4j.bolt.testing.channel.StrictBufferContext;

@StrictBufferExtension
class PacketWriterUtilTest {

    @TestFactory
    Stream<DynamicTest> shouldWriteVarInt(StrictBufferContext ctx) {
        return IntStream.of(1, 8, 127, 130, 513, 1025)
                .mapToObj(value -> DynamicTest.dynamicTest(Integer.toString(value), () -> {
                    var buffer = ctx.outputBuffer();

                    PacketWriterUtil.writeVarInt(buffer, value);

                    var bits = Integer.SIZE - Integer.numberOfLeadingZeros(value);
                    var expectedLength = bits / 7 + (bits % 7 == 0 ? 0 : 1);

                    ByteBufAssertions.assertThat(buffer).hasReadableBytes(expectedLength);

                    var remaining = value;
                    do {
                        var c = remaining & 0x7F;
                        remaining >>>= 7;

                        if (remaining != 0) {
                            c ^= 0x80;
                        }

                        ByteBufAssertions.assertThat(buffer).containsByte(c);
                    } while (remaining > 0);

                    ByteBufAssertions.assertThat(buffer).hasNoRemainingReadableBytes();
                }));
    }

    @TestFactory
    Stream<DynamicTest> shouldWriteString(StrictBufferContext ctx) {
        return Stream.of("abcdef", "neo4j-west-1.db.europe-central.tenant.example.org")
                .map(value -> DynamicTest.dynamicTest(value, () -> {
                    var buffer = ctx.outputBuffer();

                    PacketWriterUtil.writeString(buffer, value);

                    var encoded = value.getBytes(StandardCharsets.UTF_8);
                    var length = encoded.length;
                    var varIntLength = 32 - Integer.numberOfLeadingZeros(length);
                    varIntLength = varIntLength / 7 + (varIntLength % 7 != 0 ? 1 : 0);

                    ByteBufAssertions.assertThat(buffer).hasReadableBytes(varIntLength + length);

                    buffer.skipBytes(varIntLength);

                    var actual = new byte[length];
                    buffer.readBytes(actual);

                    Assertions.assertThat(actual).isEqualTo(encoded);

                    ByteBufAssertions.assertThat(buffer).hasNoRemainingReadableBytes();
                }));
    }
}
