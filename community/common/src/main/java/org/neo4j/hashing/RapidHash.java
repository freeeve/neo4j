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
package org.neo4j.hashing;

import static org.neo4j.internal.helpers.VarHandleUtils.byteArrayViewVarHandle;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * RapidHash is WyHash's official successor, with improved speed, quality and compatibility.
 * <p>
 * <a href="https://github.com/Nicoshev/rapidhash">rapidhash</a>
 */
public final class RapidHash {
    private static final VarHandle LE_INTEGER = byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LE_LONG = byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final long RAPID_SEED = 0xbdd89aa982704029L;
    private static final long SECRET0 = 0x2d358dccaa6c78a5L;
    private static final long SECRET1 = 0x8bb84b93962eacc9L;
    private static final long SECRET2 = 0x4b33a62ed433d4a3L;

    private RapidHash() {}

    public static long hash(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        return hash(bytes, bytes.length);
    }

    public static long hash(byte[] key, int len) {
        return hashInternal(key, len);
    }

    private static long hashInternal(byte[] key, int len) {
        long seed = RAPID_SEED ^ mix(RAPID_SEED ^ SECRET0, SECRET1) ^ len;
        long a, b;
        if (len <= 16) {
            if (len >= 4) {
                int pLast = len - 4;
                a = (u32(key, 0) << 32) | u32(key, pLast);
                final int delta = ((len & 24) >> (len >> 3));
                b = ((u32(key, delta) << 32) | u32(key, pLast - delta));
            } else if (len > 0) {
                a = readSmall(key, len);
                b = 0;
            } else {
                a = b = 0;
            }
        } else {
            int i = len;
            int p = 0;
            if (i > 48) {
                long see1 = seed, see2 = seed;
                while (i >= 96) {
                    seed = mix(i64(key, p) ^ SECRET0, i64(key, p + 8) ^ seed);
                    see1 = mix(i64(key, p + 16) ^ SECRET1, i64(key, p + 24) ^ see1);
                    see2 = mix(i64(key, p + 32) ^ SECRET2, i64(key, p + 40) ^ see2);
                    seed = mix(i64(key, p + 48) ^ SECRET0, i64(key, p + 56) ^ seed);
                    see1 = mix(i64(key, p + 64) ^ SECRET1, i64(key, p + 72) ^ see1);
                    see2 = mix(i64(key, p + 80) ^ SECRET2, i64(key, p + 88) ^ see2);
                    p += 96;
                    i -= 96;
                }
                if (i >= 48) {
                    seed = mix(i64(key, p) ^ SECRET0, i64(key, p + 8) ^ seed);
                    see1 = mix(i64(key, p + 16) ^ SECRET1, i64(key, p + 24) ^ see1);
                    see2 = mix(i64(key, p + 32) ^ SECRET2, i64(key, p + 40) ^ see2);
                    p += 48;
                    i -= 48;
                }
                seed ^= see1 ^ see2;
            }
            if (i > 16) {
                seed = mix(i64(key, p) ^ SECRET2, i64(key, p + 8) ^ seed ^ SECRET1);
                if (i > 32) seed = mix(i64(key, p + 16) ^ SECRET2, i64(key, p + 24) ^ seed);
            }
            a = i64(key, p + i - 16);
            b = i64(key, p + i - 8);
        }
        a ^= SECRET1;
        b ^= seed;

        long high = Math.unsignedMultiplyHigh(a, b);
        return mix((a * b) ^ SECRET0 ^ len, high ^ SECRET1);
    }

    private static long mix(long A, long B) {
        return (A * B) ^ Math.unsignedMultiplyHigh(A, B);
    }

    private static long i64(byte[] key, int p) {
        return (long) LE_LONG.get(key, p);
    }

    private static long u32(byte[] key, int p) {
        return Integer.toUnsignedLong((int) LE_INTEGER.get(key, p));
    }

    private static long readSmall(byte[] p, int k) {
        return (Byte.toUnsignedLong(p[0]) << 56)
                | (Byte.toUnsignedLong(p[k >>> 1]) << 32)
                | Byte.toUnsignedLong(p[k - 1]);
    }
}
