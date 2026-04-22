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
package org.neo4j.genai.ai.text.tokenChunking;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

public class TextChunkConfig {

    private int limit;
    private int overlap;
    private String model;
    private Encoding encoding = null;

    public TextChunkConfig(Long limit, Long overlap, String model) {
        if (limit == null) {
            return;
        }
        checkLimitAndOverlap(limit, overlap);
        encoding = getEncoding(model);
        this.limit = limit.intValue();
        this.overlap = overlap.intValue();
        this.model = model;
    }

    public boolean shouldUseChunking() {
        return encoding != null;
    }

    public Encoding getEncoding() {
        return encoding;
    }

    public int getLimit() {
        return limit;
    }

    public int getOverlap() {
        return overlap;
    }

    private Encoding getEncoding(String model) {
        final var registry = Encodings.newDefaultEncodingRegistry();
        return (model == null || model.isEmpty())
                ? registry.getEncoding(EncodingType.R50K_BASE)
                : registry.getEncodingForModel(model).orElseGet(() -> registry.getEncoding(EncodingType.R50K_BASE));
    }

    public static void checkLimitAndOverlap(Long limit, Long overlap) {
        if (limit <= 0) {
            throw new IllegalArgumentException("'limit' must be greater than 0");
        }
        if (limit > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("'limit' must be less than or equal to " + Integer.MAX_VALUE);
        }
        if (overlap == null) {
            throw new IllegalArgumentException("'overlap' must not be null");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("'overlap' must be greater than or equal to 0");
        }
        if (overlap >= limit) {
            throw new IllegalArgumentException("'overlap' must be less than 'limit'");
        }
    }
}
