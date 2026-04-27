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
package org.neo4j.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

class ByteUnitTest {
    @Test
    void convertZero() {
        assertThat(ByteUnit.Byte.toBytes(0)).isZero();
        assertThat(ByteUnit.Byte.toKibiBytes(0)).isZero();
        assertThat(ByteUnit.Byte.toMebiBytes(0)).isZero();
        assertThat(ByteUnit.Byte.toGibiBytes(0)).isZero();
        assertThat(ByteUnit.Byte.toTebiBytes(0)).isZero();
        assertThat(ByteUnit.Byte.toPebiBytes(0)).isZero();
        assertThat(ByteUnit.Byte.toExbiBytes(0)).isZero();

        assertThat(ByteUnit.KibiByte.toBytes(0)).isZero();
        assertThat(ByteUnit.KibiByte.toKibiBytes(0)).isZero();
        assertThat(ByteUnit.KibiByte.toMebiBytes(0)).isZero();
        assertThat(ByteUnit.KibiByte.toGibiBytes(0)).isZero();
        assertThat(ByteUnit.KibiByte.toTebiBytes(0)).isZero();
        assertThat(ByteUnit.KibiByte.toPebiBytes(0)).isZero();
        assertThat(ByteUnit.KibiByte.toExbiBytes(0)).isZero();

        assertThat(ByteUnit.MebiByte.toBytes(0)).isZero();
        assertThat(ByteUnit.MebiByte.toKibiBytes(0)).isZero();
        assertThat(ByteUnit.MebiByte.toMebiBytes(0)).isZero();
        assertThat(ByteUnit.MebiByte.toGibiBytes(0)).isZero();
        assertThat(ByteUnit.MebiByte.toTebiBytes(0)).isZero();
        assertThat(ByteUnit.MebiByte.toPebiBytes(0)).isZero();
        assertThat(ByteUnit.MebiByte.toExbiBytes(0)).isZero();

        assertThat(ByteUnit.GibiByte.toBytes(0)).isZero();
        assertThat(ByteUnit.GibiByte.toKibiBytes(0)).isZero();
        assertThat(ByteUnit.GibiByte.toMebiBytes(0)).isZero();
        assertThat(ByteUnit.GibiByte.toGibiBytes(0)).isZero();
        assertThat(ByteUnit.GibiByte.toTebiBytes(0)).isZero();
        assertThat(ByteUnit.GibiByte.toPebiBytes(0)).isZero();
        assertThat(ByteUnit.GibiByte.toExbiBytes(0)).isZero();

        assertThat(ByteUnit.TebiByte.toBytes(0)).isZero();
        assertThat(ByteUnit.TebiByte.toKibiBytes(0)).isZero();
        assertThat(ByteUnit.TebiByte.toMebiBytes(0)).isZero();
        assertThat(ByteUnit.TebiByte.toGibiBytes(0)).isZero();
        assertThat(ByteUnit.TebiByte.toTebiBytes(0)).isZero();
        assertThat(ByteUnit.TebiByte.toPebiBytes(0)).isZero();
        assertThat(ByteUnit.TebiByte.toExbiBytes(0)).isZero();

        assertThat(ByteUnit.PebiByte.toBytes(0)).isZero();
        assertThat(ByteUnit.PebiByte.toKibiBytes(0)).isZero();
        assertThat(ByteUnit.PebiByte.toMebiBytes(0)).isZero();
        assertThat(ByteUnit.PebiByte.toGibiBytes(0)).isZero();
        assertThat(ByteUnit.PebiByte.toTebiBytes(0)).isZero();
        assertThat(ByteUnit.PebiByte.toPebiBytes(0)).isZero();
        assertThat(ByteUnit.PebiByte.toExbiBytes(0)).isZero();

        assertThat(ByteUnit.ExbiByte.toBytes(0)).isZero();
        assertThat(ByteUnit.ExbiByte.toKibiBytes(0)).isZero();
        assertThat(ByteUnit.ExbiByte.toMebiBytes(0)).isZero();
        assertThat(ByteUnit.ExbiByte.toGibiBytes(0)).isZero();
        assertThat(ByteUnit.ExbiByte.toTebiBytes(0)).isZero();
        assertThat(ByteUnit.ExbiByte.toPebiBytes(0)).isZero();
        assertThat(ByteUnit.ExbiByte.toExbiBytes(0)).isZero();
    }

    @Test
    void convertOneToEIC() {
        assertThat(ByteUnit.KibiByte.toBytes(1)).isEqualTo(1024L);
        assertThat(ByteUnit.KibiByte.toKibiBytes(1)).isOne();
        assertThat(ByteUnit.KibiByte.toMebiBytes(1)).isZero();
        assertThat(ByteUnit.KibiByte.toGibiBytes(1)).isZero();
        assertThat(ByteUnit.KibiByte.toTebiBytes(1)).isZero();
        assertThat(ByteUnit.KibiByte.toPebiBytes(1)).isZero();
        assertThat(ByteUnit.KibiByte.toExbiBytes(1)).isZero();

        assertThat(ByteUnit.MebiByte.toBytes(1)).isEqualTo(1048576L);
        assertThat(ByteUnit.MebiByte.toKibiBytes(1)).isEqualTo(1024L);
        assertThat(ByteUnit.MebiByte.toMebiBytes(1)).isOne();
        assertThat(ByteUnit.MebiByte.toGibiBytes(1)).isZero();
        assertThat(ByteUnit.MebiByte.toTebiBytes(1)).isZero();
        assertThat(ByteUnit.MebiByte.toPebiBytes(1)).isZero();
        assertThat(ByteUnit.MebiByte.toExbiBytes(1)).isZero();

        assertThat(ByteUnit.GibiByte.toBytes(1)).isEqualTo(1073741824L);
        assertThat(ByteUnit.GibiByte.toKibiBytes(1)).isEqualTo(1048576L);
        assertThat(ByteUnit.GibiByte.toMebiBytes(1)).isEqualTo(1024L);
        assertThat(ByteUnit.GibiByte.toGibiBytes(1)).isOne();
        assertThat(ByteUnit.GibiByte.toTebiBytes(1)).isZero();
        assertThat(ByteUnit.GibiByte.toPebiBytes(1)).isZero();
        assertThat(ByteUnit.GibiByte.toExbiBytes(1)).isZero();

        assertThat(ByteUnit.TebiByte.toBytes(1)).isEqualTo(1099511627776L);
        assertThat(ByteUnit.TebiByte.toKibiBytes(1)).isEqualTo(1073741824L);
        assertThat(ByteUnit.TebiByte.toMebiBytes(1)).isEqualTo(1048576L);
        assertThat(ByteUnit.TebiByte.toGibiBytes(1)).isEqualTo(1024L);
        assertThat(ByteUnit.TebiByte.toTebiBytes(1)).isOne();
        assertThat(ByteUnit.TebiByte.toPebiBytes(1)).isZero();
        assertThat(ByteUnit.TebiByte.toExbiBytes(1)).isZero();

        assertThat(ByteUnit.PebiByte.toBytes(1)).isEqualTo(1125899906842624L);
        assertThat(ByteUnit.PebiByte.toKibiBytes(1)).isEqualTo(1099511627776L);
        assertThat(ByteUnit.PebiByte.toMebiBytes(1)).isEqualTo(1073741824L);
        assertThat(ByteUnit.PebiByte.toGibiBytes(1)).isEqualTo(1048576L);
        assertThat(ByteUnit.PebiByte.toTebiBytes(1)).isEqualTo(1024L);
        assertThat(ByteUnit.PebiByte.toPebiBytes(1)).isOne();
        assertThat(ByteUnit.PebiByte.toExbiBytes(1)).isZero();

        assertThat(ByteUnit.ExbiByte.toBytes(1)).isEqualTo(1152921504606846976L);
        assertThat(ByteUnit.ExbiByte.toKibiBytes(1)).isEqualTo(1125899906842624L);
        assertThat(ByteUnit.ExbiByte.toMebiBytes(1)).isEqualTo(1099511627776L);
        assertThat(ByteUnit.ExbiByte.toGibiBytes(1)).isEqualTo(1073741824L);
        assertThat(ByteUnit.ExbiByte.toTebiBytes(1)).isEqualTo(1048576L);
        assertThat(ByteUnit.ExbiByte.toPebiBytes(1)).isEqualTo(1024L);
        assertThat(ByteUnit.ExbiByte.toExbiBytes(1)).isOne();
    }

    @Test
    void unitsAsBytes() {
        assertThat(ByteUnit.bytes(1)).isOne();
        assertThat(ByteUnit.kibiBytes(1)).isEqualTo(1024L);
        assertThat(ByteUnit.mebiBytes(1)).isEqualTo(1048576L);
        assertThat(ByteUnit.gibiBytes(1)).isEqualTo(1073741824L);
        assertThat(ByteUnit.tebiBytes(1)).isEqualTo(1099511627776L);
        assertThat(ByteUnit.pebiBytes(1)).isEqualTo(1125899906842624L);
        assertThat(ByteUnit.exbiBytes(1)).isEqualTo(1152921504606846976L);

        assertThat(ByteUnit.parse("1")).isOne();
        assertThat(ByteUnit.parse("1 KiB")).isEqualTo(1024L);
        assertThat(ByteUnit.parse("1KiB")).isEqualTo(1024L);
        assertThat(ByteUnit.parse("1 KB")).isEqualTo(1024L);
        assertThat(ByteUnit.parse("1KB")).isEqualTo(1024L);
        assertThat(ByteUnit.parse(" 1    KB ")).isEqualTo(1024L);
        assertThat(ByteUnit.parse("1 kB")).isEqualTo(1024L);
        assertThat(ByteUnit.parse("1kB")).isEqualTo(1024L);
        assertThat(ByteUnit.parse(" 1    kB ")).isEqualTo(1024L);
        assertThat(ByteUnit.parse("1 kb")).isEqualTo(1024L);
        assertThat(ByteUnit.parse("1kb")).isEqualTo(1024L);
        assertThat(ByteUnit.parse(" 1    kb ")).isEqualTo(1024L);
        assertThat(ByteUnit.parse("1 k")).isEqualTo(1024L);
        assertThat(ByteUnit.parse("1k")).isEqualTo(1024L);
        assertThat(ByteUnit.parse(" 1    k")).isEqualTo(1024L);
        assertThat(ByteUnit.parse("1 MiB")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse("1MiB")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse("1 MB")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse("1MB")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse(" 1    MB ")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse("1 mB")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse("1mB")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse(" 1    mB ")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse("1 mb")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse("1mb")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse(" 1    mb ")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse("1 m")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse("1m")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse(" 1    m")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse("1 GiB")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse("1GiB")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse("1 GB")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse("1GB")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse(" 1    GB ")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse("1 gB")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse("1gB")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse(" 1    gB ")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse("1 gb")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse("1gb")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse(" 1    gb ")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse("1 g")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse("1g")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse(" 1    g")).isEqualTo(1073741824L);
        assertThat(ByteUnit.parse("1 TiB")).isEqualTo(1099511627776L);
        assertThat(ByteUnit.parse("1TiB")).isEqualTo(1099511627776L);
        assertThat(ByteUnit.parse("1 TB")).isEqualTo(1099511627776L);
        assertThat(ByteUnit.parse("1TB")).isEqualTo(1099511627776L);
        assertThat(ByteUnit.parse(" 1    TB ")).isEqualTo(1099511627776L);
        assertThat(ByteUnit.parse("1 PiB")).isEqualTo(1125899906842624L);
        assertThat(ByteUnit.parse("1PiB")).isEqualTo(1125899906842624L);
        assertThat(ByteUnit.parse("1 PB")).isEqualTo(1125899906842624L);
        assertThat(ByteUnit.parse("1PB")).isEqualTo(1125899906842624L);
        assertThat(ByteUnit.parse(" 1    PB ")).isEqualTo(1125899906842624L);
        assertThat(ByteUnit.parse("1 EiB")).isEqualTo(1152921504606846976L);
        assertThat(ByteUnit.parse("1EiB")).isEqualTo(1152921504606846976L);
        assertThat(ByteUnit.parse("1 EB")).isEqualTo(1152921504606846976L);
        assertThat(ByteUnit.parse("1EB")).isEqualTo(1152921504606846976L);
        assertThat(ByteUnit.parse(" 1    EB ")).isEqualTo(1152921504606846976L);
    }

    @Test
    void decimalUnitsToBytes() {
        assertThat(ByteUnit.parse("0.5KiB")).isEqualTo(512L);
        assertThat(ByteUnit.parse("1.5KiB")).isEqualTo(1536L);
        assertThat(ByteUnit.parse("  1.5    KiB")).isEqualTo(1536L);
        assertThat(ByteUnit.parse("1.57KiB")).isEqualTo(1607L);
        assertThat(ByteUnit.parse("1.578945TiB")).isEqualTo(1736068387118L);
        assertThat(ByteUnit.parse("1.0MiB")).isEqualTo(1048576L);
        assertThat(ByteUnit.parse("1.000MiB")).isEqualTo(1048576L);
    }

    @Test
    void parseShouldThrowOnOverflow() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("100000000.00TiB"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ByteUnit.parse(Long.MAX_VALUE + "1"));
    }

    @Test
    void parseShouldThrowOnNegativeValues() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("-100000000.00TiB"));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("-1"));
    }

    @Test
    void bytesToString() {
        assertThat(ByteUnit.bytesToString(1)).isEqualTo("1B");
        assertThat(ByteUnit.bytesToString(10)).isEqualTo("10B");
        assertThat(ByteUnit.bytesToString(1000)).isEqualTo("1000B");
        assertThat(ByteUnit.bytesToString(1025)).isEqualTo("1.001KiB");
        assertThat(ByteUnit.bytesToString(10250)).isEqualTo("10.01KiB");
        assertThat(ByteUnit.bytesToString(102500)).isEqualTo("100.1KiB");
        assertThat(ByteUnit.bytesToString(1025000)).isEqualTo("1001KiB");
        assertThat(ByteUnit.bytesToString(10250000)).isEqualTo("9.775MiB");
        assertThat(ByteUnit.bytesToString(102500000)).isEqualTo("97.75MiB");
        assertThat(ByteUnit.bytesToString(1025000000)).isEqualTo("977.5MiB");
        assertThat(ByteUnit.bytesToString(10250000000L)).isEqualTo("9.546GiB");
        assertThat(ByteUnit.bytesToString(Long.MAX_VALUE)).isEqualTo("8.389e+06TiB");
    }

    @Test
    void bytesToStringWithoutScientificNotation() {
        assertThat(ByteUnit.bytesToStringWithoutScientificNotation(1)).isEqualTo("1B");
        assertThat(ByteUnit.bytesToStringWithoutScientificNotation(10)).isEqualTo("10B");
        assertThat(ByteUnit.bytesToStringWithoutScientificNotation(1000)).isEqualTo("1000B");
        assertThat(ByteUnit.bytesToStringWithoutScientificNotation(1025)).isEqualTo("1.00KiB");
        assertThat(ByteUnit.bytesToStringWithoutScientificNotation(10250)).isEqualTo("10.01KiB");
        assertThat(ByteUnit.bytesToStringWithoutScientificNotation(102500)).isEqualTo("100.10KiB");
        assertThat(ByteUnit.bytesToStringWithoutScientificNotation(1025000)).isEqualTo("1000.98KiB");
        assertThat(ByteUnit.bytesToStringWithoutScientificNotation(10250000)).isEqualTo("9.78MiB");
        assertThat(ByteUnit.bytesToStringWithoutScientificNotation(102500000)).isEqualTo("97.75MiB");
        assertThat(ByteUnit.bytesToStringWithoutScientificNotation(1025000000)).isEqualTo("977.52MiB");
        assertThat(ByteUnit.bytesToStringWithoutScientificNotation(10250000000L))
                .isEqualTo("9.55GiB");
        assertThat(ByteUnit.bytesToStringWithoutScientificNotation(102500000000000L))
                .isEqualTo("93.22TiB");
        assertThat(ByteUnit.bytesToStringWithoutScientificNotation(Long.MAX_VALUE))
                .isEqualTo("8388608.00TiB");
    }

    @Test
    void mustThrowWhenParsingInvalidUnit() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("1 XB"));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("1 Ki B"));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("1 Mi B"));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("1 Gi B"));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("1 Ti B"));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("1 Pi B"));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("1 Ei B"));
    }

    @Test
    void mustThrowWhenParsingUnitInterjectedWithNumber() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("1K2i3B"));
    }

    @Test
    void mustThrowWhenParsingNonNumericTest() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("abc"));
    }

    @Test
    void mustThrowWhenParsingOnlyUnit() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("MiB"));
    }

    @Test
    void mustThrowWhenParsingUnitBeforeValue() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ByteUnit.parse("MiB 1"));
    }
}
