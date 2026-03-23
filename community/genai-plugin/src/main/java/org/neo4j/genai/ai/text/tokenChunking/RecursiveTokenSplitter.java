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

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.IntArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class RecursiveTokenSplitter {
    private final List<String> separators;
    private final int chunkSize;
    private final int chunkOverlap;
    private final Encoding encoding;

    public RecursiveTokenSplitter(Encoding encoding, int chunkSize, int chunkOverlap) {
        this.encoding = encoding;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.separators = Arrays.asList("\n\n", "\n", " ", "");
    }

    public List<String> splitText(String text) {
        return recursiveSplit(text, separators);
    }

    private List<String> recursiveSplit(String text, List<String> activeSeparators) {
        List<String> finalChunks = new ArrayList<>();

        // Base Case: If text is already small enough, return it
        var tokenCount = encoding.countTokens(text);
        if (tokenCount <= chunkSize) {
            finalChunks.add(text);
            return finalChunks;
        }

        // Find the best separator to use, default to last
        String separator = activeSeparators.getLast();
        List<String> remainingSeparators = new ArrayList<>();

        for (int i = 0; i < activeSeparators.size(); i++) {
            String s = activeSeparators.get(i);
            if (s.isEmpty() || text.contains(s)) {
                separator = s;
                remainingSeparators = activeSeparators.subList(i + 1, activeSeparators.size());
                break;
            }
        }

        // Split the text by the chosen separator
        String[] parts;
        if (separator.isEmpty()) {
            IntArrayList allTokens = encoding.encode(text);
            List<String> chunkList = new ArrayList<>();

            int step = Math.max(1, chunkSize - chunkOverlap);

            for (int start = 0; start < tokenCount; start += step) {
                int end = Math.min(start + chunkSize, tokenCount);
                // Double check size is not greater than allTokens
                end = Math.min(end, allTokens.size());

                IntArrayList window = new IntArrayList();
                for (int i = start; i < end; i++) {
                    window.add(allTokens.get(i));
                }

                chunkList.add(encoding.decode(window));

                if (end == tokenCount) break;
            }

            return chunkList;
        } else {
            parts = text.split(Pattern.quote(separator));
        }

        List<String> filteredParts = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) filteredParts.add(part);
        }

        return combineChunks(filteredParts, separator, remainingSeparators, text.endsWith(separator));
    }

    private List<String> combineChunks(
            List<String> parts, String separator, List<String> nextSeparators, boolean endsWithSeparator) {
        List<String> chunks = new ArrayList<>();
        List<String> currentDoc = new ArrayList<>();
        int currentCount = 0;

        for (String part : parts) {
            int partCount = encoding.countTokens(part);

            // If a single part is bigger than chunkSize, we must recurse on that part
            if (partCount > chunkSize) {
                if (!currentDoc.isEmpty()) {
                    chunks.add(String.join(separator, currentDoc));
                    currentDoc.clear();
                    currentCount = 0;
                }

                List<String> subChunks = recursiveSplit(part, nextSeparators);
                chunks.addAll(subChunks);
            } else if (currentCount + partCount > chunkSize) {
                // Current bucket is full, seal it and start next
                chunks.add(String.join(separator, currentDoc));

                // Handle overlap: keep the last few parts to maintain context
                while (currentCount > chunkOverlap && !currentDoc.isEmpty()) {
                    String removed = currentDoc.removeFirst();
                    currentCount -= encoding.countTokens(removed);
                }
                currentDoc.add(part);
                currentCount += partCount;
            } else {
                currentDoc.add(part);
                currentCount += partCount;
            }
        }

        if (!currentDoc.isEmpty()) {
            chunks.add(String.join(separator, currentDoc));
        }

        // Add the separator to all chunks except the last one (unless specified to do so)
        var countToAddTo = endsWithSeparator ? chunks.size() : chunks.size() - 1;
        for (int i = 0; i < countToAddTo; i++) {
            chunks.set(i, chunks.get(i) + separator);
        }

        return chunks;
    }
}
