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
package org.neo4j.genai.dbs;

import java.net.URI;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.neo4j.annotations.service.ServiceProvider;

/**
 * Validates a URL request for a specific vector database against a list of well-known providers, that might be
 * called from within Aura.
 */
@ServiceProvider
public final class DefaultProviderResolver implements ProviderResolver {

    private static final Predicate<String> PATTERN_PINECONE =
            Pattern.compile(".+\\.pinecone\\.io").asMatchPredicate();
    private static final Predicate<String> PATTERN_WEAVIATE =
            Pattern.compile(".+\\.weaviate\\.cloud").asMatchPredicate();
    private static final Predicate<String> QDRANT =
            Pattern.compile(".+\\.qdrant\\.io").asMatchPredicate();

    @Override
    public Optional<VectorDatabaseProvider> resolve(String url) {
        return getProviderName(url).map(KNOWN_PROVIDERS::get);
    }

    Optional<String> getProviderName(String url) {
        var uri = URI.create(url);
        var host = uri.getHost();
        if (host == null) {
            host = url;
        }
        if (PATTERN_PINECONE.test(host)) {
            return Optional.of("pinecone");
        } else if (PATTERN_WEAVIATE.test(host)) {
            return Optional.of("weaviate");
        } else if (QDRANT.test(host)) {
            return Optional.of("qdrant");
        }
        return Optional.empty();
    }
}
