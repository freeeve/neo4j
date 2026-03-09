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

import java.nio.ByteBuffer;

/**
 * Utilities for working with UTF8 encoded data.
 */
public final class UTF8 {
    private UTF8() {}

    /**
     * Convert a string to its utf8 encoded bytes.
     *
     * @param string to encode.
     * @return an array with the utf8 encoded {@code string}.
     */
    public static byte[] encode(String string) {
        return string.getBytes(UTF_8);
    }

    /**
     * Decode an array with utf8 encoded data to a string.
     *
     * @param bytes the bytes to be decoded into characters
     * @return a new {@link String} by decoding the specified array of bytes.
     */
    public static String decode(byte[] bytes) {
        return new String(bytes, UTF_8);
    }

    /**
     * Decode an array with utf8 encoded data to a string.
     *
     * @param bytes the bytes to be decoded into characters.
     * @param offset the index of the first byte to decode.
     * @param length the number of bytes to decode.
     * @return a new {@link String} by decoding the specified subarray of bytes.
     */
    public static String decode(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, UTF_8);
    }

    /**
     * Trim whitespace at the beginning and the end of a buffer.
     *
     * @param buffer utf8 encoded data.
     * @return a slice of the original buffer, with the surrounding whitespace removed.
     *
     * @see #ltrim(ByteBuffer, int, int)
     * @see #rtrim(ByteBuffer, int, int)
     */
    public static ByteBuffer trim(ByteBuffer buffer) {
        int ltrim = ltrim(buffer, 0, buffer.limit());
        int rtrim = rtrim(buffer, ltrim, buffer.limit());
        return buffer.slice(ltrim, rtrim - ltrim);
    }

    /**
     * Trim all whitespace from the left, i.e. the beginning of the string.
     *
     * @param buffer utf8 encoded data.
     * @param start where to start trimming.
     * @param end maximum position to trim to.
     * @return the position, after {@code start}, with the first non-whitespace character.
     */
    public static int ltrim(ByteBuffer buffer, int start, final int end) {
        while (start < end) {
            int len = getWhitespaceLength(buffer, start);
            if (len == 0) break;
            start += len;
        }
        return start;
    }

    /**
     * Trim all whitespace from the right, i.e. the end of the string.
     *
     * @param buffer utf8 encoded data.
     * @param start minimum position to trim to.
     * @param end where to begin trimming from.
     * @return the position, before {@code end}, right after the last non-whitespace character.
     */
    public static int rtrim(ByteBuffer buffer, final int start, int end) {
        while (end > start) {
            int len = getWhitespaceLengthReverse(buffer, end);
            if (len == 0) break;
            end -= len;
        }

        return end;
    }

    /**
     * Returns the length of the whitespace at a given position in a buffer.
     *
     * @param buffer buffer with utf8 encoded data.
     * @param position position in buffer to start decoding from.
     * @return the length of the whitespace character, or {@code 0} if it is not a whitespace character.
     */
    private static int getWhitespaceLength(ByteBuffer buffer, int position) {
        byte b1 = buffer.get(position);
        if (is1ByteWhitespace(b1)) {
            return 1;
        } else if (isNonAscii(b1)) {
            if (is2ByteSequence(b1) && is2ByteWhitespace(b1, buffer.get(position + 1))) {
                return 2;
            } else if (is3ByteSequence(b1)
                    && is3ByteWhitespace(b1, buffer.get(position + 1), buffer.get(position + 2))) {
                return 3;
            }
        }

        return 0;
    }

    /**
     * Returns the length of the whitespace character, that occur right before a position in a buffer.
     *
     * @param buffer buffer with utf8 encoded data.
     * @param position position in the buffer to
     * @return the length of the whitespace character, or {@code 0} if it is not a whitespace character.
     */
    private static int getWhitespaceLengthReverse(ByteBuffer buffer, int position) {
        byte b1 = buffer.get(position - 1);

        if (is1ByteWhitespace(b1)) {
            return 1;
        } else if (isContinuationByte(b1)) {
            byte b2 = buffer.get(position - 2);
            if (!isContinuationByte(b2)) {
                if (is2ByteWhitespace(b2, b1)) {
                    return 2;
                }
            } else {
                byte b3 = buffer.get(position - 3);
                if (!isContinuationByte(b3) && is3ByteWhitespace(b3, b2, b1)) {
                    return 3;
                }
            }
        }
        return 0;
    }

    /**
     * Test if a byte is a utf8 control character.
     *
     * @param b1 utf8 byte.
     * @return {@code true} if this is a utf8 control byte(MSB is set), {@code false} otherwise.
     */
    private static boolean isNonAscii(byte b1) {
        return b1 < 0;
    }

    /**
     * Test if a byte is the beginning of a 2 byte utf8 character, i.e. {@code 110xxxxx 10xxxxxx}.
     *
     * @param b1 utf8 byte.
     * @return {@code true} if this is the beginning of a 2 byte utf8 character, {@code false} otherwise.
     */
    private static boolean is2ByteSequence(byte b1) {
        return (b1 & 0xe0) == 0xc0;
    }

    /**
     * Test if a byte is the beginning of a 3 byte utf8 character, i.e. {@code 1110xxxx 10xxxxxx 10xxxxxx}.
     *
     * @param b1 utf8 byte.
     * @return {@code true} if this is the beginning of a 3 byte utf8 character, {@code false} otherwise.
     */
    private static boolean is3ByteSequence(byte b1) {
        return (b1 & 0xf0) == 0xe0;
    }

    /**
     * Test if a byte is an utf8 continuation character, i.e. {@code 10xxxxxx}.
     *
     * @param b1 utf8 byte.
     * @return {@code true} if this is a continuation character, {@code false} otherwise.
     */
    private static boolean isContinuationByte(byte b1) {
        return (b1 & 0xc0) == 0x80;
    }

    /**
     * Test if a byte is considered an utf8 whitespace character.
     *
     * @param b1 utf8 byte.
     * @return {@code true} if the byte is considered whitespace, {@code false} otherwise.
     */
    private static boolean is1ByteWhitespace(byte b1) {
        // U+0009 0x09 character tabulation
        // U+000A 0x0a line feed
        // U+000B 0x0b line tabulation
        // U+000C 0x0c form feed
        // U+000D 0x0d carriage return
        // U+0020 0x20 space
        return b1 == ' ' || (b1 >= 0x09 && b1 <= 0x0D);
    }

    /**
     * Test if bytes are considered an utf8 whitespace character.
     *
     * @param b1 first utf8 byte.
     * @param b2 second utf8 byte.
     * @return {@code true} if the bytes are considered whitespace, {@code false} otherwise.
     */
    private static boolean is2ByteWhitespace(byte b1, byte b2) {
        // U+0085 0xc2 0x85 next line
        // U+00A0 0xc2 0xa0 no-break space
        return b1 == (byte) 0xc2 && (b2 == (byte) 0x85 || b2 == (byte) 0xa0);
    }

    /**
     * Test if bytes are considered an utf8 whitespace character.
     *
     * @param b1 first utf8 byte.
     * @param b2 second utf8 byte.
     * @param b3 third utf8 byte.
     * @return {@code true} if the bytes are considered whitespace, {@code false} otherwise.
     */
    private static boolean is3ByteWhitespace(byte b1, byte b2, byte b3) {
        if (b1 == (byte) 0xe1
                && ((b2 == (byte) 0x9a && b3 == (byte) 0x80) || (b2 == (byte) 0xa0 && b3 == (byte) 0x8e))) {
            // U+1680 0xe1 0x9a 0x80 ogham space mark
            // U+180E 0xe1 0xa0 0x8e Mongolian vowel separator
            return true;
        } else if (b1 == (byte) 0xe2) {
            if (b2 == (byte) 0x80) {
                if (b3 <= (byte) 0x8d || b3 == (byte) 0xa8 || b3 == (byte) 0xa9 || b3 == (byte) 0xaf) {
                    // U+2000 0xe2 0x80 0x80 en quad
                    // U+2001 0xe2 0x80 0x81 em quad
                    // U+2002 0xe2 0x80 0x82 en space
                    // U+2003 0xe2 0x80 0x83 em space
                    // U+2004 0xe2 0x80 0x84 three-per-em space
                    // U+2005 0xe2 0x80 0x85 four-per-em space
                    // U+2006 0xe2 0x80 0x86 six-per-em space
                    // U+2007 0xe2 0x80 0x87 figure space
                    // U+2008 0xe2 0x80 0x88 punctuation space
                    // U+2009 0xe2 0x80 0x89 thin space
                    // U+200A 0xe2 0x80 0x8a hair space
                    // U+200B 0xe2 0x80 0x8b zero width space
                    // U+200C 0xe2 0x80 0x8c zero width non-joiner
                    // U+200D 0xe2 0x80 0x8d zero width joiner

                    // U+2028 0xe2 0x80 0xa8 line separator
                    // U+2029 0xe2 0x80 0xa9 paragraph separator
                    // U+202F 0xe2 0x80 0xaf narrow no-break space
                    return true;
                }
            } else if ((b2 == (byte) 0x81 && b3 == (byte) 0x9f) || (b2 == (byte) 0x81 && b3 == (byte) 0xa0)) {
                // U+205F 0xe2 0x81 0x9f medium mathematical space
                // U+2060 0xe2 0x81 0xa0 word joiner
                return true;
            }
        } else if (b1 == (byte) 0xe3 && b2 == (byte) 0x80 && b3 == (byte) 0x80) {
            // U+3000 0xe3 0x80 0x80 ideographic space
            return true;
        } else if (b1 == (byte) 0xef && b2 == (byte) 0xbb && b3 == (byte) 0xbf) {
            // U+FEFF 0xef 0xbb 0xbf zero width non-breaking space
            return true;
        }
        return false;
    }
}
