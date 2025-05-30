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
import org.neo4j.values.storable.VectorValue;

public interface VectorUtilSupport {
    VectorValue scale(VectorCandidate vector, float scale);

    float dotProduct(VectorCandidate vector1, VectorCandidate vector2);

    float cosine(VectorCandidate vector1, VectorCandidate vector2);

    float l1Distance(VectorCandidate vector1, VectorCandidate vector2);

    float l1Norm(VectorCandidate vector);

    float squareL2Distance(VectorCandidate vector1, VectorCandidate vector2);

    float squareL2Norm(VectorCandidate vector);

    int hammingDistance(VectorCandidate vector1, VectorCandidate vector2);
}
