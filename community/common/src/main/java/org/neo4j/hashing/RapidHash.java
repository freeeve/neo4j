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

import static java.lang.Math.unsignedMultiplyHigh;
import static org.neo4j.internal.helpers.VarHandleUtils.byteArrayViewVarHandle;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * RapidHash v3 ported to Java.
 * <p>
 * <a href="https://github.com/Nicoshev/rapidhash">rapidhash</a>
 */
public final class RapidHash {
    private static final VarHandle LE_INTEGER = byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LE_LONG = byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final long SECRET0 = 0x2d358dccaa6c78a5L;
    private static final long SECRET1 = 0x8bb84b93962eacc9L;
    private static final long SECRET2 = 0x4b33a62ed433d4a3L;
    private static final long SECRET3 = 0x4d5a2da51de1aa47L;
    private static final long SECRET4 = 0xa0761d6478bd642fL;
    private static final long SECRET5 = 0xe7037ed1a0b428dbL;
    private static final long SECRET6 = 0x90ed1765281c388cL;
    private static final long SECRET7 = 0xaaaaaaaaaaaaaaaaL;
    private static final RapidHash DEFAULT_HASHER_INSTANCE = create(0L);

    private final long seed;

    private RapidHash(long seed) {
        this.seed = seed ^ mix(seed ^ SECRET2, SECRET1);
    }

    /**
     * Create a new {@link RapidHash} instance with the default seed.
     *
     * @return the default hasher instance.
     */
    public static RapidHash create() {
        return DEFAULT_HASHER_INSTANCE;
    }

    /**
     * Create a new {@link RapidHash} instance with the given seed.
     *
     * @param seed the seed value for the hasher
     * @return a new hasher instance with the specified seed.
     */
    public static RapidHash create(long seed) {
        return new RapidHash(seed);
    }

    /**
     * Hashes a single long value.
     *
     * @param l the long value to hash.
     * @return the hash value.
     */
    public long hash(long l) {
        long a = l ^ SECRET1;
        long b = l ^ (seed ^ 8);
        return mix((a * b) ^ SECRET7, unsignedMultiplyHigh(a, b) ^ (SECRET1 ^ 8));
    }

    /**
     * Hash a byte array with the standard RapidHash algorithm.
     *
     * @param key the byte array to hash.
     * @param offset the offset in the byte array to start hashing from.
     * @param length the length of the byte array to hash.
     * @return the hash value.
     */
    public long hash(byte[] key, int offset, int length) {
        return hashInternal(key, offset, length, seed);
    }

    private static long hashInternal(byte[] key, int offset, int len, long seed) {
        long a, b;
        int i = len;
        if (len <= 16) {
            if (len >= 4) {
                seed ^= len;
                if (len >= 8) {
                    a = i64(key, offset);
                    b = i64(key, offset + len - 8);
                } else {
                    a = u32(key, offset);
                    b = u32(key, offset + len - 4);
                }
            } else if (len > 0) {
                a = ((key[offset] & 0xFFL) << 45) | (key[offset + len - 1] & 0xFFL);
                b = key[offset + (len >> 1)] & 0xFFL;
            } else a = b = 0;
        } else {
            if (len > 112) {
                long see1 = seed, see2 = seed;
                long see3 = seed, see4 = seed;
                long see5 = seed, see6 = seed;

                do {
                    seed = mix(i64(key, offset) ^ SECRET0, i64(key, offset + 8) ^ seed);
                    see1 = mix(i64(key, offset + 16) ^ SECRET1, i64(key, offset + 24) ^ see1);
                    see2 = mix(i64(key, offset + 32) ^ SECRET2, i64(key, offset + 40) ^ see2);
                    see3 = mix(i64(key, offset + 48) ^ SECRET3, i64(key, offset + 56) ^ see3);
                    see4 = mix(i64(key, offset + 64) ^ SECRET4, i64(key, offset + 72) ^ see4);
                    see5 = mix(i64(key, offset + 80) ^ SECRET5, i64(key, offset + 88) ^ see5);
                    see6 = mix(i64(key, offset + 96) ^ SECRET6, i64(key, offset + 104) ^ see6);
                    offset += 112;
                    i -= 112;
                } while (i > 112);

                seed ^= see1;
                see2 ^= see3;
                see4 ^= see5;
                seed ^= see6;
                see2 ^= see4;
                seed ^= see2;
            }
            if (i > 16) {
                seed = mix(i64(key, offset) ^ SECRET2, i64(key, offset + 8) ^ seed);
                if (i > 32) {
                    seed = mix(i64(key, offset + 16) ^ SECRET2, i64(key, offset + 24) ^ seed);
                    if (i > 48) {
                        seed = mix(i64(key, offset + 32) ^ SECRET1, i64(key, offset + 40) ^ seed);
                        if (i > 64) {
                            seed = mix(i64(key, offset + 48) ^ SECRET1, i64(key, offset + 56) ^ seed);
                            if (i > 80) {
                                seed = mix(i64(key, offset + 64) ^ SECRET2, i64(key, offset + 72) ^ seed);
                                if (i > 96) {
                                    seed = mix(i64(key, offset + 80) ^ SECRET1, i64(key, offset + 88) ^ seed);
                                }
                            }
                        }
                    }
                }
            }
            a = i64(key, offset + i - 16) ^ i;
            b = i64(key, offset + i - 8);
        }
        a ^= SECRET1;
        b ^= seed;
        return mix((a * b) ^ SECRET7, unsignedMultiplyHigh(a, b) ^ SECRET1 ^ i);
    }

    static long mix(long a, long b) {
        return a * b ^ unsignedMultiplyHigh(a, b);
    }

    private static long i64(byte[] key, int p) {
        return (long) LE_LONG.get(key, p);
    }

    private static long u32(byte[] key, int p) {
        return Integer.toUnsignedLong((int) LE_INTEGER.get(key, p));
    }
}
