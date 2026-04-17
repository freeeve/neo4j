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
package org.neo4j.bolt.testing.assertions.discovery;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.assertj.core.api.DurationAssert;
import org.neo4j.bolt.testing.assertions.ByteBufAssertions;

public final class BeaconSignalAssertions {

    private final List<Consumer<ByteBufAssertions>> signalAssertions = new ArrayList<>();
    private final List<Consumer<DurationAssert>> periodAssertions = new ArrayList<>();

    private BeaconSignalAssertions() {}

    public static BeaconSignalAssertions create() {
        return new BeaconSignalAssertions();
    }

    void verifySignal(ByteBuf actual) {
        var assertions = ByteBufAssertions.assertThat(actual);
        this.signalAssertions.forEach(assertion -> assertion.accept(assertions));

        assertions.hasNoRemainingReadableBytes();
    }

    void verifyPeriod(Duration average) {
        var assertions = new DurationAssert(average);
        this.periodAssertions.forEach(assertion -> assertion.accept(assertions));
    }

    private BeaconSignalAssertions withSignalAssertion(Consumer<ByteBufAssertions> assertion) {
        this.signalAssertions.add(assertion);
        return this;
    }

    private BeaconSignalAssertions withBufferAssertion(Consumer<ByteBuf> consumer) {
        return this.withSignalAssertion(assertions -> consumer.accept(assertions.actual()));
    }

    private BeaconSignalAssertions withPeriodAssertion(Consumer<DurationAssert> assertion) {
        this.periodAssertions.add(assertion);
        return this;
    }

    public BeaconSignalAssertions hasMagicNumber(int magicNumber) {
        return this.withSignalAssertion(buf -> buf.containsInt(magicNumber));
    }

    public BeaconSignalAssertions hasVarIntAvailable() {
        return this.withBufferAssertion(buf -> {
            buf.markReaderIndex();

            try {
                if (!isVarIntAvailable(buf)) {
                    throw new AssertionError("Expected fully available VarInt");
                }
            } finally {
                buf.resetReaderIndex();
            }
        });
    }

    public BeaconSignalAssertions hasVarInt(int version) {
        return this.withSignalAssertion(buf -> {
            var remaining = version;
            do {
                var expected = (remaining & 0x7F);
                remaining >>>= 7;
                if (remaining != 0) {
                    expected ^= 0x80;
                }

                buf.containsByte(expected);
            } while (remaining != 0);
        });
    }

    public BeaconSignalAssertions hasOpcode(int opcode) {
        return this.withSignalAssertion(buf -> buf.containsUnsignedByte(opcode));
    }

    public BeaconSignalAssertions hasString(Consumer<String> assertions) {
        return this.hasVarIntAvailable().withBufferAssertion(buf -> {
            var length = readVarInt(buf);

            if (!buf.isReadable(length)) {
                throw new AssertionError(
                        "Expected string of length " + length + " but only " + buf.readableBytes() + " remain");
            }

            var heap = new byte[length];
            buf.readBytes(heap);

            assertions.accept(new String(heap, StandardCharsets.UTF_8));
        });
    }

    public BeaconSignalAssertions hasAverageSignalPeriod(Duration average, Duration deviation) {
        return this.withPeriodAssertion(duration -> {
            duration.isGreaterThan(average.minus(deviation)).isLessThan(average.plus(deviation));
        });
    }

    private static boolean isVarIntAvailable(ByteBuf buf) {
        for (var i = 0; i < 5; ++i) {
            if (!buf.isReadable()) {
                return false;
            }

            var current = buf.readUnsignedByte();
            if ((current & 0x80) == 0) {
                return true;
            }
        }

        throw new AssertionError("Illegal VarInt: Exceeds maximum permitted length of 5 bytes");
    }

    private static int readVarInt(ByteBuf buf) {
        var value = 0;
        for (var i = 0; i < 5; ++i) {
            var current = buf.readUnsignedByte();
            value |= (current & 0x7F) << (i * 7);

            if ((current & 0x80) == 0) {
                return value;
            }
        }

        throw new AssertionError("Illegal VarInt: Exceeds maximum permitted length of 5 bytes");
    }
}
