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

import java.net.http.HttpRequest;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import org.neo4j.annotations.service.Service;
import org.neo4j.genai.util.GenAIProcedureException;

/**
 * A unified provider for various external vector databases.
 */
@Service
public interface VectorDatabaseProvider {

    /**
     * This enum lists the possible commands to be implemented by a vector database provider.
     */
    enum Command {
        /**
         * Retrieve information for / from the collection of vectors.
         */
        GET_COLLECTION_METADATA,
        /**
         * Creates a new collection for vectors.
         */
        CREATE_COLLECTION,
        /**
         * Deletes a collection.
         */
        DELETE_COLLECTION,
        /**
         * Inserts or updates vectors in a given collection.
         */
        UPSERT,
        /**
         * Deletes vectors from a given collection.
         */
        DELETE,
        /**
         * Gets the vectors with given ids from a given collection.
         */
        GET,
        /**
         * Gets the vectors with given ids from a given collection and also updates corresponding Neo4j entities.
         */
        GET_AND_UPDATE,
        /**
         * Queries for a list of vectors with a given vector in a collection.
         */
        QUERY,
        /**
         * Queries for a list of vectors with a given vector in a collection and also updates corresponding Neo4j entities.
         */
        QUERY_AND_UPDATE
    }

    /**
     * {@return the name of this provider}
     */
    default String getName() {
        return this.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    /**
     * {@return true if several vectors including all properties can be fetch with a single call parameterized with a set of ids}
     */
    default boolean supportsMultipleGet() {
        return true;
    }

    /**
     * {@return true if several vectors can be inserted or updated in one http request}
     */
    default boolean supportsMultipleUpserts() {
        return true;
    }

    <T> VectorDatabaseRequest<T> createRequestFor(
            Command command,
            String host,
            String collection,
            Map<String, Object> configuration,
            Map<String, Object> additionalArguments);

    /**
     * As not  all vector databases can handle upserts (idempotent put methods, that either create or update objects),
     * we might need to be able to react on such a failure
     *
     * @param exception           the exception thrown when trying to upsert, provider needs to check if the exception
     *                            is really due to a failed update or has other reasons. if the latter is the case, just rethrow.
     * @param host
     * @param collection
     * @param configuration
     * @param additionalArguments
     * @param <T>
     * @return
     */
    default <T> VectorDatabaseRequest<T> handleFailedUpsertRequest(
            GenAIProcedureException exception,
            String host,
            String collection,
            Map<String, Object> configuration,
            Map<String, Object> additionalArguments) {
        throw new UnsupportedOperationException();
    }

    default Optional<VectorDatabaseRequest<String>> getCollectionSpecificHost(
            Command command, String defaultHost, String collection, Map<String, Object> configuration) {
        return Optional.empty();
    }

    default BiFunction<Integer, String, Optional<GenAIProcedureException>> getProviderSpecificStatusHandler(
            String collection) {
        return (statusCode, message) -> Optional.empty();
    }

    @SuppressWarnings("unchecked")
    default HttpRequest.Builder addAuthorizationHeader(
            Map<String, Object> configuration, HttpRequest.Builder httpRequestBuilder) {
        Map<String, Object> headerConfig = RequestConfig.Keys.HEADERS.get(Map.class, configuration);
        if (headerConfig != null && headerConfig.containsKey(RequestConfig.Keys.AUTHORIZATION.key())) {
            httpRequestBuilder = httpRequestBuilder.header(RequestConfig.Keys.AUTHORIZATION.key(), (String)
                    headerConfig.get(RequestConfig.Keys.AUTHORIZATION.key()));
        } else if (configuration.containsKey(RequestConfig.Keys.TOKEN.key())) {
            httpRequestBuilder = httpRequestBuilder.header(
                    RequestConfig.Keys.AUTHORIZATION.key(),
                    "Bearer " + configuration.get(RequestConfig.Keys.TOKEN.key()));
        }
        return httpRequestBuilder;
    }
}
