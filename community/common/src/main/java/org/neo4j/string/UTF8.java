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
     * Encode a string to a byte array using UTF-8.
     *
     * @param src {@link String} to encode.
     * @param dst byte array to encode to.
     * @param offset the index in {@code dst} where to start encoding to.
     * @param length the maximum number of bytes to encode.
     * @return the index in {@code dst} where the encoding stopped.
     *
     * @throws IllegalArgumentException if {@code src} contains an unpaired surrogate.
     * @throws ArrayIndexOutOfBoundsException if the string is too long to fit in the destination buffer.
     */
    public static int encode(String src, byte[] dst, int offset, int length) {
        int utf16Length = src.length();
        int j = offset;
        int i = 0;
        int limit = offset + length;
        // Ascii-only fast path
        for (char c; i < utf16Length && i + j < limit && (c = src.charAt(i)) < 0x80; i++) {
            dst[j + i] = (byte) c;
        }
        if (i == utf16Length) {
            // Only ascii, we are done!
            return j + utf16Length;
        }
        j += i;

        for (char c; i < utf16Length; i++) {
            c = src.charAt(i);
            if (c < 0x80 && j < limit) {
                dst[j++] = (byte) c;
            } else if (c < 0x800 && j <= limit - 2) {
                dst[j++] = (byte) (0xC0 | (c >>> 6));
                dst[j++] = (byte) (0x80 | (c & 0x3F));
            } else if (!Character.isSurrogate(c) && j <= limit - 3) {
                dst[j++] = (byte) (0xE0 | (c >>> 12));
                dst[j++] = (byte) (0x80 | ((c >>> 6) & 0x3F));
                dst[j++] = (byte) (0x80 | (c & 0x3F));
            } else if (j <= limit - 4) {
                char lowSurrogate;
                if (i + 1 == utf16Length || !Character.isSurrogatePair(c, lowSurrogate = src.charAt(++i))) {
                    throw new IllegalArgumentException("Unpaired surrogate at index " + (i - 1));
                }
                int codePoint = Character.toCodePoint(c, lowSurrogate);
                dst[j++] = (byte) (0xF0 | (codePoint >>> 18));
                dst[j++] = (byte) (0x80 | ((codePoint >>> 12) & 0x3F));
                dst[j++] = (byte) (0x80 | ((codePoint >>> 6) & 0x3F));
                dst[j++] = (byte) (0x80 | (codePoint & 0x3F));
            } else {
                if (Character.isSurrogate(c)
                        && (i + 1 == utf16Length || !Character.isSurrogatePair(c, src.charAt(i + 1)))) {
                    throw new IllegalArgumentException("Unpaired surrogate at index " + i);
                }
                throw new ArrayIndexOutOfBoundsException("Failed writing " + c + " at index " + j);
            }
        }
        return j;
    }

    /**
     * Returns the number of bytes required to encode {@code str} as UTF-8, without allocating.
     *
     * @param str the string to measure.
     * @return the UTF-8 encoded byte length of {@code str}.
     * @throws IllegalArgumentException if {@code str} contains an unpaired surrogate, or if the
     *     UTF-8 length overflows {@code int}.
     */
    public static int length(String str) {
        int utf16Length = str.length();
        // Start from the assumption that every char costs 1 byte and then add extra bytes for non-ASCII chars.
        int utf8Length = utf16Length;
        int i = 0;

        // Skip as many ASCII chars (U+0000-U+007F) as possible
        while (i < utf16Length && str.charAt(i) < 0x80) {
            i++;
        }

        // Handle chars up to U+07FF (1 or 2-byte UTF-8 sequences).
        // Drop into the slow path as soon as a 3-byte char is encountered.
        for (; i < utf16Length; i++) {
            char c = str.charAt(i);
            if (c < 0x800) {
                // +1 for non-ASCII, +0 for ASCII
                utf8Length += (0x7f - c) >>> 31;
            } else {
                // c >= U+0800: delegate remaining chars to the slow path.
                utf8Length += lengthSlowPath(str, i);
                break;
            }
        }

        // Any int overflow caused by a very long string would wrap utf8Length below utf16Length,
        // since we only ever add non-negative values starting from utf16Length.
        // The maximum expansion is 3x (each UTF-16 char -> at most 3 UTF-8 bytes for BMP chars).
        if (utf8Length < utf16Length) {
            throw new IllegalArgumentException("String cannot be formated to a single byte array, required length is "
                    + (utf8Length + (1L << 32)));
        }
        return utf8Length;
    }

    private static int lengthSlowPath(String str, int start) {
        int utf16Length = str.length();
        int utf8Length = 0;
        for (int i = start; i < utf16Length; i++) {
            char c = str.charAt(i);
            if (c < 0x800) {
                // +1 for non-ASCII, +0 for ASCII
                utf8Length += (0x7f - c) >>> 31;
            } else {
                //  U+0800-U+FFFF: 1 utf16 char -> 3 utf8 bytes, add +2
                // surrogate pair: 2 utf16 char -> 4 utf8 bytes, also adds +2
                utf8Length += 2;
                if (Character.isSurrogate(c)) {
                    if (Character.codePointAt(str, i) == c) {
                        throw new IllegalArgumentException("Unpaired surrogate at index " + i);
                    }
                    i++; // skip the low surrogate; it was already counted in the initial estimate
                }
            }
        }
        return utf8Length;
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
