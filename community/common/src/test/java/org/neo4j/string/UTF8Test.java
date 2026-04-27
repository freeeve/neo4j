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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.util.Arrays;
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

    @Test
    void lengthEmptyString() {
        assertThat(UTF8.length("")).isEqualTo(0);
    }

    @Test
    void lengthPureAscii() {
        assertThat(UTF8.length("hello")).isEqualTo(5);
        assertThat(UTF8.length("a")).isEqualTo(1);
    }

    @Test
    void lengthTwoByteCharacters() {
        assertThat(UTF8.length("é")).isEqualTo(2); // U+00E9, first common 2-byte char
        assertThat(UTF8.length("߿")).isEqualTo(2); // U+07FF, last 2-byte char
    }

    @Test
    void lengthThreeByteCharacters() {
        // U+0800-U+FFFF (non-surrogate) encode to 3 bytes each.
        assertThat(UTF8.length("ࠀ")).isEqualTo(3); // U+0800, first 3-byte char
        assertThat(UTF8.length("€")).isEqualTo(3); // € — U+20AC
        assertThat(UTF8.length("\uFFFF")).isEqualTo(3); // U+FFFF, last BMP char
    }

    @Test
    void lengthFourByteCharacter() {
        // Supplementary chars (U+10000-U+10FFFF) are surrogate pairs in UTF-16 and 4 bytes in UTF-8.
        assertThat(UTF8.length("😀")).isEqualTo(4); // U+1F600 😀
    }

    @Test
    void lengthMixedCharTypes() {
        // a(1) + é(2) + €(3) + 😀(4) = 10 bytes
        assertThat(UTF8.length("aé€😀")).isEqualTo(10);
    }

    @Test
    void lengthUnpairedSurrogateThrows() {
        assertThatThrownBy(() -> UTF8.length("\uD800"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("surrogate");
    }

    @Test
    void lengthMatchesGetBytesForRandomStrings() {
        for (int t = 0; t < 1000; t++) {
            String s = randomUnicodeString(random, 50);
            assertThat(UTF8.length(s))
                    .as("UTF8.length mismatch for string of %d chars", s.length())
                    .isEqualTo(s.getBytes(UTF_8).length);
        }
    }

    @Test
    void lengthMatchesGetBytesForRandomStringsWithSurrogatePairs() {
        for (int t = 0; t < 1000; t++) {
            String s = randomStringWithSurrogates(random, 20);
            assertThat(UTF8.length(s))
                    .as(
                            "UTF8.length mismatch for string of %d chars (UTF-16 length %d)",
                            s.codePointCount(0, s.length()), s.length())
                    .isEqualTo(s.getBytes(UTF_8).length);
        }
    }

    @Test
    void encodeAsciiOnly() {
        byte[] dst = new byte[5];
        int end = UTF8.encode("hello", dst, 0, dst.length);
        assertThat(end).isEqualTo(5);
        assertThat(dst).isEqualTo("hello".getBytes(UTF_8));
    }

    @Test
    void encodeWithNonZeroOffset() {
        byte[] dst = new byte[7];
        Arrays.fill(dst, (byte) 0xFF);
        int end = UTF8.encode("hi", dst, 3, 4);
        assertThat(end).isEqualTo(5);
        assertThat(dst[0]).isEqualTo((byte) 0xFF);
        assertThat(dst[1]).isEqualTo((byte) 0xFF);
        assertThat(dst[2]).isEqualTo((byte) 0xFF);
        assertThat(dst[3]).isEqualTo((byte) 'h');
        assertThat(dst[4]).isEqualTo((byte) 'i');
        assertThat(dst[5]).isEqualTo((byte) 0xFF);
    }

    @Test
    void encodeTwoByteCharacter() {
        // é = U+00E9 -> 2 bytes
        byte[] dst = new byte[2];
        int end = UTF8.encode("é", dst, 0, dst.length);
        assertThat(end).isEqualTo(2);
        assertThat(dst).isEqualTo("é".getBytes(UTF_8));
    }

    @Test
    void encodeThreeByteCharacter() {
        // € = U+20AC -> 3 bytes
        byte[] dst = new byte[3];
        int end = UTF8.encode("€", dst, 0, dst.length);
        assertThat(end).isEqualTo(3);
        assertThat(dst).isEqualTo("€".getBytes(UTF_8));
    }

    @Test
    void encodeFourByteCharacter() {
        // 😀 = U+1F600 -> 4 bytes
        byte[] dst = new byte[4];
        int end = UTF8.encode("😀", dst, 0, dst.length);
        assertThat(end).isEqualTo(4);
        assertThat(dst).isEqualTo("😀".getBytes(UTF_8));
    }

    @Test
    void encodeMixedCharTypes() {
        // a(1) + é(2) + €(3) + 😀(4) = 10 bytes
        String s = "aé€😀";
        byte[] expected = s.getBytes(UTF_8);
        byte[] dst = new byte[expected.length];
        int end = UTF8.encode(s, dst, 0, dst.length);
        assertThat(end).isEqualTo(expected.length);
        assertThat(dst).isEqualTo(expected);
    }

    @Test
    void encodeBufferTooSmallThrows() {
        assertThatThrownBy(() -> UTF8.encode("hello", new byte[4], 0, 4))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }

    @Test
    void encodeUnpairedSurrogateThrows() {
        assertThatThrownBy(() -> UTF8.encode("\uD800", new byte[10], 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("surrogate");
    }

    @Test
    void encodeRandomRoundTrip() {
        for (int t = 0; t < 1000; t++) {
            String s = randomUnicodeString(random, 50);
            byte[] expected = s.getBytes(UTF_8);
            byte[] dst = new byte[expected.length];
            int end = UTF8.encode(s, dst, 0, dst.length);
            assertThat(end)
                    .as("end index mismatch for string of %d chars", s.length())
                    .isEqualTo(expected.length);
            assertThat(dst)
                    .as("encoded bytes mismatch for string of %d chars", s.length())
                    .isEqualTo(expected);
        }
    }

    @Test
    void encodeRandomRoundTripWithSurrogatePairs() {
        for (int t = 0; t < 1000; t++) {
            String s = randomStringWithSurrogates(random, 20);
            byte[] expected = s.getBytes(UTF_8);
            byte[] dst = new byte[expected.length];
            int end = UTF8.encode(s, dst, 0, dst.length);
            assertThat(end)
                    .as("end index mismatch for string with surrogates, UTF-16 length %d", s.length())
                    .isEqualTo(expected.length);
            assertThat(dst)
                    .as("encoded bytes mismatch for string with surrogates, UTF-16 length %d", s.length())
                    .isEqualTo(expected);
        }
    }

    private void appendRandomWhitespaces(int n, StringBuilder sb) {
        for (int i = 0; i < n; i++) {
            sb.append(UnicodeData.WHITESPACES[random.nextInt(UnicodeData.WHITESPACES.length)]);
        }
    }

    /**
     * Will never return a whitespace character, so it can safely be used in trim tests
     */
    private char randomUnicodeCharacter() {
        return UnicodeData.RND[random.nextInt(UnicodeData.RND.length)];
    }

    private static String randomUnicodeString(Random rnd, int maxCodePoints) {
        int n = rnd.nextInt(maxCodePoints + 1);
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            int cp;
            do {
                cp = rnd.nextInt(Character.MAX_CODE_POINT + 1);
            } while (Character.getType(cp) == Character.SURROGATE);
            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }

    private static String randomStringWithSurrogates(Random rnd, int maxCodePoints) {
        int n = 1 + rnd.nextInt(maxCodePoints); // at least 1 code point
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            if (rnd.nextBoolean()) {
                // Supplementary (U+10000-U+10FFFF): 4-byte UTF-8, surrogate pair in UTF-16
                sb.appendCodePoint(0x10000 + rnd.nextInt(Character.MAX_CODE_POINT - 0xFFFF));
            } else {
                // BMP (U+0000-U+FFFF, excluding surrogates): 1-3 bytes in UTF-8
                int cp;
                do {
                    cp = rnd.nextInt(0x10000);
                } while (Character.getType(cp) == Character.SURROGATE);
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }
}
