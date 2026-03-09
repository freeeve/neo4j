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
package org.neo4j.string;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.jupiter.api.Test;

class UTF8Test {

    private final Random random = new Random();

    @Test
    void simpleLeftTrim() {
        ByteBuffer buffer = ByteBuffer.wrap("   a".getBytes(UTF_8));
        assertThat(UTF8.ltrim(buffer, 0, buffer.limit())).isEqualTo(3);

        buffer = ByteBuffer.wrap("    ".getBytes(UTF_8));
        assertThat(UTF8.ltrim(buffer, 0, buffer.limit())).isEqualTo(buffer.limit());
        assertThat(UTF8.ltrim(buffer, 0, 1)).isEqualTo(1);
    }

    @Test
    void multibyteLeftTrim() {
        // 3+3-byte utf8 codepoints
        ByteBuffer buffer = ByteBuffer.wrap("\u205F\u2060a".getBytes(UTF_8));
        assertThat(UTF8.ltrim(buffer, 0, buffer.limit())).isEqualTo(6);

        // 2+3-byte utf8 codepoints
        buffer = ByteBuffer.wrap("\u0085\u3000a".getBytes(UTF_8));
        assertThat(UTF8.ltrim(buffer, 0, buffer.limit())).isEqualTo(5);

        // 2+3-byte utf8 codepoints followed by a 3-byte non-whitespace
        buffer = ByteBuffer.wrap("\u0085\u3000€".getBytes(UTF_8));
        assertThat(UTF8.ltrim(buffer, 0, buffer.limit())).isEqualTo(5);
    }

    @Test
    void randomLeftTrim() {
        StringBuilder sb = new StringBuilder();
        int n = random.nextInt(20);
        appendRandomWhitespaces(n, sb);

        char randomNonWhitespace = randomUnicodeCharacter();
        sb.append(randomNonWhitespace);
        int randomUtf8Size = UTF_8.encode(String.valueOf(randomNonWhitespace)).limit();

        ByteBuffer encode = UTF_8.encode(sb.toString());
        int ltrim = UTF8.ltrim(encode, 0, encode.limit());
        assertThat(ltrim).isEqualTo(encode.limit() - randomUtf8Size);
    }

    @Test
    void randomLeftTrimAll() {
        StringBuilder sb = new StringBuilder();
        int n = random.nextInt(20);
        appendRandomWhitespaces(n, sb);

        ByteBuffer encode = UTF_8.encode(sb.toString());
        int ltrim = UTF8.ltrim(encode, 0, encode.limit());
        assertThat(ltrim).isEqualTo(encode.limit()); // Nothing left
    }

    @Test
    void simpleRightTrim() {
        ByteBuffer buffer = ByteBuffer.wrap("a   ".getBytes(UTF_8));
        assertThat(UTF8.rtrim(buffer, 0, buffer.limit())).isEqualTo(1);

        buffer = ByteBuffer.wrap("    ".getBytes(UTF_8));
        assertThat(UTF8.rtrim(buffer, 0, buffer.limit())).isEqualTo(0);
        assertThat(UTF8.rtrim(buffer, buffer.limit() - 2, buffer.limit())).isEqualTo(buffer.limit() - 2);
    }

    @Test
    void multibyteRightTrim() {
        // 3+3-byte utf8 codepoints
        ByteBuffer buffer = ByteBuffer.wrap("a\u205F\u2060".getBytes(UTF_8));
        assertThat(UTF8.rtrim(buffer, 0, buffer.limit())).isEqualTo(1);

        // 2+3-byte utf8 codepoints
        buffer = ByteBuffer.wrap("a\u0085\u3000".getBytes(UTF_8));
        assertThat(UTF8.rtrim(buffer, 0, buffer.limit())).isEqualTo(1);

        // 2+3-byte utf8 codepoints followed by a 3-byte non-whitespace
        buffer = ByteBuffer.wrap("€\u0085\u3000".getBytes(UTF_8));
        assertThat(UTF8.rtrim(buffer, 0, buffer.limit())).isEqualTo(3);
    }

    @Test
    void randomRightTrim() {
        StringBuilder sb = new StringBuilder();
        char randomNonWhitespace = randomUnicodeCharacter();
        sb.append(randomNonWhitespace);
        int randomUtf8Size = UTF_8.encode(String.valueOf(randomNonWhitespace)).limit();

        int n = random.nextInt(20);
        appendRandomWhitespaces(n, sb);

        ByteBuffer encode = UTF_8.encode(sb.toString());
        int rtrim = UTF8.rtrim(encode, 0, encode.limit());
        assertThat(rtrim).isEqualTo(randomUtf8Size);
    }

    @Test
    void randomRightTrimAll() {
        StringBuilder sb = new StringBuilder();
        int n = random.nextInt(20);
        appendRandomWhitespaces(n, sb);

        ByteBuffer encode = UTF_8.encode(sb.toString());
        int rtrim = UTF8.rtrim(encode, 0, encode.limit());
        assertThat(rtrim).isEqualTo(0); // Nothing left
    }

    @Test
    void trimLeftRightRandom() {
        StringBuilder sb = new StringBuilder();
        int leading = random.nextInt(10);
        int trailing = random.nextInt(10);

        appendRandomWhitespaces(leading, sb);
        sb.append("preserved");
        appendRandomWhitespaces(trailing, sb);

        ByteBuffer encode = UTF_8.encode(sb.toString());
        int ltrim = UTF8.ltrim(encode, 0, encode.limit());
        int rtrim = UTF8.rtrim(encode, 0, encode.limit());
        ByteBuffer slice = encode.slice(ltrim, rtrim - ltrim);
        String decode = UTF_8.decode(slice).toString();
        assertThat(decode).isEqualTo("preserved");
    }

    @Test
    void randomTrimAll() {
        StringBuilder sb = new StringBuilder();
        int n = random.nextInt(20);
        appendRandomWhitespaces(n, sb);

        ByteBuffer encode = UTF_8.encode(sb.toString());
        ByteBuffer trimmed = UTF8.trim(encode);
        assertThat(trimmed.limit()).isZero();
    }

    @Test
    void trimRandom() {
        StringBuilder sb = new StringBuilder();
        int leading = random.nextInt(10);
        int trailing = random.nextInt(10);

        appendRandomWhitespaces(leading, sb);
        sb.append("preserved");
        appendRandomWhitespaces(trailing, sb);

        ByteBuffer encode = UTF_8.encode(sb.toString());
        ByteBuffer trimmed = UTF8.trim(encode);
        String decode = UTF_8.decode(trimmed).toString();
        assertThat(decode).isEqualTo("preserved");
    }

    private void appendRandomWhitespaces(int n, StringBuilder sb) {
        for (int i = 0; i < n; i++) {
            sb.append(UnicodeData.WHITESPACES[random.nextInt(UnicodeData.WHITESPACES.length)]);
        }
    }

    private char randomUnicodeCharacter() {
        return UnicodeData.RND[random.nextInt(UnicodeData.RND.length)];
    }
}
