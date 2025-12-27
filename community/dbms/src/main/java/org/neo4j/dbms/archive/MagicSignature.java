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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.neo4j.util.Preconditions;

public class MagicSignature {
    private final byte[] bytes;

    private MagicSignature(byte[] bytes) {
        Preconditions.checkArgument(bytes.length == 2 || bytes.length == 4, "magic must be two or four bytes");
        this.bytes = bytes;
    }

    /** Create a magic signature from four bytes.
     * @param b1 first byte
     * @param b2 second byte
     * @param b3 third byte
     * @param b4 forth byte
     * @return a new MagicSignature
     */
    static MagicSignature of(int b1, int b2, int b3, int b4) {
        Preconditions.requireBetween(b1, 0, 0x100);
        Preconditions.requireBetween(b2, 0, 0x100);
        Preconditions.requireBetween(b3, 0, 0x100);
        Preconditions.requireBetween(b4, 0, 0x100);
        return new MagicSignature(new byte[] {(byte) b1, (byte) b2, (byte) b3, (byte) b4});
    }

    /** Create a magic signature from two bytes.
     * @param b1 first byte
     * @param b2 second byte
     * @return a new MagicSignature
     */
    static MagicSignature of(int b1, int b2) {
        Preconditions.requireBetween(b1, 0, 0x100);
        Preconditions.requireBetween(b2, 0, 0x100);
        return new MagicSignature(new byte[] {(byte) b1, (byte) b2});
    }

    /** Create a byte-signature from a UTF-8 encoded string.
     *
     * @param str The string identifier to encode.
     * @return a new MagicSignature
     */
    public static MagicSignature of(String str) {
        return new MagicSignature(str.getBytes(StandardCharsets.UTF_8));
    }

    /** Compare read bytes to byte-signature.
     *
     * @param read the bytes to compare with the magic signature, may be longer than the signature.
     * @return true if the signature matches the read bytes.
     */
    public boolean matches(byte[] read) {
        if (read.length < bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (read[i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    /** Get the bytes of the signature.
     *
     * @return a copy of the signature bytes.
     */
    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
