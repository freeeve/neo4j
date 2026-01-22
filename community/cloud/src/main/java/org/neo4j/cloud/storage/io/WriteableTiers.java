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
package org.neo4j.cloud.storage.io;

import static org.neo4j.util.Preconditions.requirePositive;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import org.neo4j.util.Preconditions;

public record WriteableTiers(int bufferSize, WriteableTier... tiers) {

    public WriteableTiers {
        if (tiers.length == 0) {
            throw new IllegalArgumentException("At least one tier must be specified");
        }

        for (int i = 0; i < tiers.length; i++) {
            if (tiers[i].batchSize % bufferSize != 0) {
                throw new IllegalArgumentException(
                        "The batch size of tier %d [%d] is not a multiple of the buffer size [%d]"
                                .formatted(i + 1, tiers[i].batchSize, bufferSize));
            }
        }
    }

    /**
     * Converts a tiers specification into a {@link WriteableTiers} object. The specification is as follow:
     * <p>batchesInTier1:batchSize1,batchesInTier2:batchSize2,...,batchesInTierX:batchSizeX</p>
     * where <b>batchesInTierX</b> is a 32b integer and <b>batchSizeX</b> is a 64b integer
     * <p>Please note that all the tiers <code>batchSize</code> <b>MUST</b> be a multiple of the buffer size being
     * used by the {@link WriteableChannel}</p>
     * @param bufferSize the buffer size used by the {@link WriteableChannel}
     * @param specs the tiers batch specifications
     * @return the tiers used to perform batch writes by the {@link WriteableChannel}
     */
    public static WriteableTiers parse(int bufferSize, String specs) {
        return new WriteableTiers(
                bufferSize,
                Arrays.stream(specs.split(","))
                        .map(spec -> {
                            var split = spec.trim().split(":");
                            if (split.length != 2) {
                                throw new IllegalArgumentException(
                                        "Illegal spec for writeable tier: expected 'batchesPerTier:batchSize' but"
                                                + " found '"
                                                + spec + "'");
                            }

                            return new WriteableTier(
                                    requirePositive(Integer.parseInt(split[0])),
                                    requirePositive(Long.parseLong(split[1])));
                        })
                        .toArray(WriteableTier[]::new));
    }

    public Stream<WriteableTier> stream() {
        return Arrays.stream(tiers);
    }

    public int totalNumberOfTiers() {
        return tiers.length;
    }

    public int maxNumberOfBatches() {
        return stream().mapToInt(WriteableTier::batchesPerTier).sum();
    }

    public long maxDataSize() {
        return stream().mapToLong(WriteableTier::maxDataSize).sum();
    }

    public WriteableTier tierAt(int index) {
        Preconditions.checkState(index >= 0 && index < tiers.length, "Invalid tier index: " + index);
        return tiers[index];
    }

    @Override
    public int hashCode() {
        return Objects.hash(bufferSize, Arrays.hashCode(tiers));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var other = (WriteableTiers) o;
        return bufferSize == other.bufferSize && Arrays.equals(tiers, other.tiers);
    }

    /**
     * A tier of batches to control how much data will be written in a batch by some {@link WriteableChannel}
     * @param batchesPerTier the number of batches within this tier
     * @param batchSize the total size of a batch within this tier
     */
    public record WriteableTier(int batchesPerTier, long batchSize) {
        public long maxDataSize() {
            return batchesPerTier * batchSize;
        }
    }
}
