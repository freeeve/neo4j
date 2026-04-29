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

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Scanner;
import org.junit.jupiter.api.Test;

class RapidHashTest {

    @Test
    void rapid() {
        runHash("RapidHashV3.txt", (rapidHash, bytes) -> rapidHash.hash(bytes, 0, bytes.length));
    }

    @Test
    void hashingOfLongValues() {
        // Ensure that hash(long) produces the same as hash(bytes)
        RapidHash rapidHash = RapidHash.create();
        Random random = new Random();
        for (int i = 0; i < 1000000; i++) {
            long l = random.nextLong();
            assertThat(rapidHash.hash(toByteArray(l), 0, 8)).isEqualTo(rapidHash.hash(l));
        }
    }

    private interface HashingMethod {

        long hash(RapidHash rapidHash, byte[] bytes);
    }

    private static byte[] toByteArray(long l) {
        return ByteBuffer.wrap(new byte[8])
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(l)
                .array();
    }

    private static void runHash(String file, HashingMethod method) {
        Scanner scanner = new Scanner(requireNonNull(RapidHashTest.class.getResourceAsStream(file)));
        scanner.nextLine(); // Skip header
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String[] split = line.split(",");
            long expectedHash = Long.parseUnsignedLong(split[0]);
            long seed = Long.parseUnsignedLong(split[1]);
            byte[] bytes = split.length == 4 ? split[3].getBytes(StandardCharsets.UTF_8) : new byte[0];
            long actualHash = method.hash(RapidHash.create(seed), bytes);
            assertThat(actualHash).isEqualTo(expectedHash);
        }
    }
    // For future reference, here is the program that generates test data
    /*
       #include <cstdlib>
       #include <ctime>
       #include <cstdio>

       #include "rapidhash.h"

       void rand_str(char *dest, size_t length) {
           while (length-- > 0) {
               constexpr char charset[] = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
               const size_t index = (double) rand() / RAND_MAX * (sizeof charset - 1);
               *dest++ = charset[index];
           }
           *dest = '\0';
       }

       int main() {
           srand(time(nullptr));
           char str[256];
           printf("hash,seed,length,data\n");
           for (size_t i = 0; i < 256; i++) {
               rand_str(str, i);
               const uint64_t seed = rand() | static_cast<uint64_t>(rand()) << 32;
               printf("%lu,%lu,%lu,%s\n", rapidhash_withSeed(str, i, seed), seed, i, str);
           }
           return 0;
       }
    */
}
