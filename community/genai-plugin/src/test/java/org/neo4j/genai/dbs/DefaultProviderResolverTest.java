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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DefaultProviderResolverTest {

    private final DefaultProviderResolver resolver = new DefaultProviderResolver();

    @CsvSource(textBlock = """
		https://cloud.qdrant.io, qdrant
		https://api.pinecone.io, pinecone
		semantic-search-c01b5b5.svc.us-west1-gcp.pinecone.io, pinecone
		https://semantic-search-c01b5b5.svc.us-west1-gcp.pinecone.io, pinecone
		https://5tyetpcj8ysi2r43p2enw.cO.us-east1.gcp.weaviate.cloud, weaviate
		""")
    @ParameterizedTest
    void shouldResolveKnownHosts(String url, String expected) {
        Assertions.assertThat(resolver.getProviderName(url)).hasValue(expected);
    }
}
