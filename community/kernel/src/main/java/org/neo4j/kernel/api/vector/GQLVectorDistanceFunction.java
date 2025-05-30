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
package org.neo4j.kernel.api.vector;

import org.neo4j.values.VectorCandidate;

public enum GQLVectorDistanceFunction implements VectorDistanceFunction {
    EUCLIDEAN {
        @Override
        public float distance(VectorCandidate vector1, VectorCandidate vector2) {
            return VectorUtil.l2Distance(vector1, vector2);
        }
    },

    EUCLIDEAN_SQUARED {
        @Override
        public float distance(VectorCandidate vector1, VectorCandidate vector2) {
            return VectorUtil.squareL2Distance(vector1, vector2);
        }
    },

    MANHATTAN {
        @Override
        public float distance(VectorCandidate vector1, VectorCandidate vector2) {
            return VectorUtil.l1Distance(vector1, vector2);
        }
    },

    COSINE {
        @Override
        public float distance(VectorCandidate vector1, VectorCandidate vector2) {
            return 1 - VectorUtil.cosine(vector1, vector2);
        }

        @Override
        public boolean valid(VectorCandidate vector) {
            return VectorUtil.valid(vector) && !VectorUtil.origin(vector);
        }
    },

    DOT {
        @Override
        public float distance(VectorCandidate vector1, VectorCandidate vector2) {
            return -VectorUtil.dotProduct(vector1, vector2);
        }
    },

    HAMMING {
        @Override
        public float distance(VectorCandidate vector1, VectorCandidate vector2) {
            return VectorUtil.hammingDistance(vector1, vector2);
        }
    };

    @Override
    public boolean valid(VectorCandidate vector) {
        return VectorUtil.valid(vector);
    }
}
