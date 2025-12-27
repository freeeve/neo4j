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
package org.neo4j.dbms.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.test.RandomSupport;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomSupportExtension;

@RandomSupportExtension
class MagicSignatureTest {

    @Inject
    RandomSupport random;

    @ParameterizedTest
    @MethodSource("positives")
    void shouldMatchExpectedSignature(MagicSignature sig, byte[] expected) {
        assertThat(sig.matches(expected))
                .as("Actual signature: %s, read: %s"
                        .formatted(Arrays.toString(sig.getBytes()), Arrays.toString(expected)))
                .isTrue();
    }

    @ParameterizedTest
    @MethodSource("positives")
    void shouldNotMatchOtherSignatures(MagicSignature sig, byte[] other) {
        int idx = random.intBetween(0, other.length - 1);
        other[idx] = 0;

        assertThat(sig.matches(other))
                .as("Actual signature: %s, read: %s".formatted(Arrays.toString(sig.getBytes()), Arrays.toString(other)))
                .isFalse();
    }

    @Test
    void shouldOnlyAllowUnsignedCharValues() {
        assertThatThrownBy(() -> MagicSignature.of(0, 0xFFFF)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MagicSignature.of(0xFFFF, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MagicSignature.of(-1, -1)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> MagicSignature.of(0, 0, 0, 0xFFFF)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MagicSignature.of(0, 0, 0xFFFF, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MagicSignature.of(0, 0xFFFF, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MagicSignature.of(0xFFFF, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("positives")
    void tooFewBytesShouldNotCauseFalsePositive(MagicSignature sig, byte[] expected) {
        int len = random.intBetween(0, expected.length - 1);
        byte[] other = Arrays.copyOfRange(expected, 0, len);

        assertThat(sig.matches(other))
                .as("Actual signature: %s, read: %s".formatted(Arrays.toString(sig.getBytes()), Arrays.toString(other)))
                .isFalse();
    }

    @ParameterizedTest
    @MethodSource("positives")
    void shouldDetectMagicWhenReadIsLongerThanNecessary(MagicSignature sig, byte[] expected) {
        byte[] read = new byte[expected.length + 5];
        random.nextBytes(read);
        System.arraycopy(expected, 0, read, 0, expected.length);

        assertThat(sig.matches(read))
                .as("Actual signature: %s, read: %s".formatted(Arrays.toString(sig.getBytes()), Arrays.toString(read)))
                .isTrue();
    }

    public static Stream<Arguments> positives() {
        return Stream.of(
                Arguments.of(MagicSignature.of("DZV1"), new byte[] {0x44, 0x5a, 0x56, 0x31}),
                Arguments.of(MagicSignature.of("DGV1"), new byte[] {0x44, 0x47, 0x56, 0x31}),
                Arguments.of(MagicSignature.of(0xff, 0xff), new byte[] {(byte) 0xff, (byte) 0xff}));
    }
}
