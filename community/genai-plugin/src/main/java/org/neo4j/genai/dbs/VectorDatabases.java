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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.collections.api.factory.primitive.IntObjectMaps;
import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.neo4j.genai.dbs.EntityMappingConfig.MappingMode;
import org.neo4j.genai.dbs.VectorDatabaseProvider.Command;
import org.neo4j.genai.util.GenAIProcedureException;
import org.neo4j.genai.util.HttpService;
import org.neo4j.genai.util.monitor.Monitors;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.procs.ProcedureCallContext;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Internal;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.neo4j.procedure.Sensitive;
import org.neo4j.service.Services;

/**
 * Central access to various vector databases.
 */
public class VectorDatabases {

    private static final List<ProviderResolver> PROVIDER_RESOLVER = Services.loadAll(ProviderResolver.class).stream()
            .sorted(Comparator.comparing(ProviderResolver::getOrder))
            .toList();

    private static final IntSet HTTP_OK = IntSets.immutable.of(200);

    @Context
    public Monitors monitors;

    @Context
    public Transaction tx;

    @Context
    public ProcedureCallContext procedureCallContext;

    @Context
    public HttpService httpService;

    @Internal
    @Procedure(name = "genai.vector.external.listProviders")
    @Description("Lists the available vector database providers.")
    public Stream<ProviderDTO> listProviders() {

        return ProviderResolver.KNOWN_PROVIDERS.keySet().stream().map(ProviderDTO::new);
    }

    @Internal
    @Procedure("genai.vector.external.info")
    @Description("""
            Get information about an existing collection or throws an error if it does not exist.

            In Neo4j-Browser you can declare the configuration parameter as shown in the following examples.
            Authorization can be configured either through a simple `token` entry in the config map like this:
            ```
            :param configuration => ({token: 'your-api-key'})
            ```

            Or if your vendor has an usual format, via a map entry under the key `Headers` in the configuration parameter.
            The example uses a bearer token:
            ```
            :param configuration => ({Headers: {Authorization: 'Bearer whatever'}})
            ```
            """)
    public Stream<InfoDTO> info(
            @Name("url") String url,
            @Name("collection") String collection,
            @Sensitive @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration) {

        var provider = getVectorDatabaseProvider(url);
        monitors.vectorDb().info(provider.getName());
        var finalHost = translateDefaultHost(provider, Command.GET_COLLECTION_METADATA, url, collection, configuration);
        VectorDatabaseRequest<InfoDTO> request = provider.createRequestFor(
                Command.GET_COLLECTION_METADATA, finalHost, collection, configuration, Map.of());

        return Stream.of(httpService.request(
                request.target(),
                request.requestCustomizer(),
                request.responseTransformer(),
                HTTP_OK,
                IntObjectMaps.immutable.of(404, () -> new CollectionNotFoundException(collection)),
                provider.getProviderSpecificStatusHandler(collection)));
    }

    private VectorDatabaseProvider getVectorDatabaseProvider(String url) {
        for (ProviderResolver resolver : PROVIDER_RESOLVER) {
            var resolvedProvider = resolver.resolve(url);
            if (resolvedProvider.isPresent()) {
                return resolvedProvider.get();
            }
        }
        throw new GenAIProcedureException("Resolving the host '%s' to a provider was not possible".formatted(url));
    }

    @Internal
    @Procedure("genai.vector.external.createCollection")
    @Description("""
            Creates a named collection.

            In Neo4j-Browser you can declare the configuration parameter as shown in the following examples.
            Authorization can be configured either through a simple `token` entry in the config map like this:
            ```
            :param configuration => ({token: 'your-api-key'})
            ```

            Or if your vendor has an usual format, via a map entry under the key `Headers` in the configuration parameter.
            The example uses a bearer token:
            ```
            :param configuration => ({Headers: {Authorization: 'Bearer whatever'}})
            ```
            """)
    public Stream<StatusDTO> createCollection(
            @Name("url") String url,
            @Name("collection") String collection,
            @Name("similarity") String similarity,
            @Name("size") Long size,
            @Sensitive @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration) {

        var additionalArguments = Map.<String, Object>of("similarity", similarity, "size", size);
        var provider = getVectorDatabaseProvider(url);
        monitors.vectorDb().createCollection(provider.getName());
        var finalHost = translateDefaultHost(provider, Command.CREATE_COLLECTION, url, collection, configuration);

        VectorDatabaseRequest<StatusDTO> request = provider.createRequestFor(
                Command.CREATE_COLLECTION, finalHost, collection, configuration, additionalArguments);
        return Stream.of(httpService.request(
                request.target(),
                request.requestCustomizer(),
                request.responseTransformer(),
                IntSets.immutable.of(200, 201),
                IntObjectMaps.immutable.empty(),
                provider.getProviderSpecificStatusHandler(collection)));
    }

    @Internal
    @Procedure("genai.vector.external.deleteCollection")
    @Description("""
            Deletes a named collection.

            In Neo4j-Browser you can declare the configuration parameter as shown in the following examples.
            Authorization can be configured either through a simple `token` entry in the config map like this:
            ```
            :param configuration => ({token: 'your-api-key'})
            ```

            Or if your vendor has an usual format, via a map entry under the key `Headers` in the configuration parameter.
            The example uses a bearer token:
            ```
            :param configuration => ({Headers: {Authorization: 'Bearer whatever'}})
            ```
            """)
    public Stream<StatusDTO> deleteCollection(
            @Name("url") String url,
            @Name("collection") String collection,
            @Sensitive @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration) {

        var provider = getVectorDatabaseProvider(url);
        monitors.vectorDb().deleteCollection(provider.getName());
        var finalHost = translateDefaultHost(provider, Command.DELETE_COLLECTION, url, collection, configuration);

        VectorDatabaseRequest<StatusDTO> request =
                provider.createRequestFor(Command.DELETE_COLLECTION, finalHost, collection, configuration, Map.of());
        return Stream.of(httpService.request(
                request.target(),
                request.requestCustomizer(),
                request.responseTransformer(),
                IntSets.immutable.of(200, 202, 204),
                IntObjectMaps.immutable.empty(),
                provider.getProviderSpecificStatusHandler(collection)));
    }

    @Internal
    @Procedure("genai.vector.external.delete")
    @Description("""
            Deletes the vectors with the specified `ids` from the given collection

            In Neo4j-Browser you can declare the configuration parameter as shown in the following examples.
            Authorization can be configured either through a simple `token` entry in the config map like this:
            ```
            :param configuration => ({token: 'your-api-key'})
            ```

            Or if your vendor has an usual format, via a map entry under the key `Headers` in the configuration parameter.
            The example uses a bearer token:
            ```
            :param configuration => ({Headers: {Authorization: 'Bearer whatever'}})
            ```
            """)
    public Stream<StatusDTO> delete(
            @Name("url") String url,
            @Name("collection") String collection,
            @Name("ids") List<Object> ids,
            @Sensitive @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration) {

        var provider = getVectorDatabaseProvider(url);
        monitors.vectorDb().deleteVector(provider.getName());
        var finalHost = translateDefaultHost(provider, Command.DELETE, url, collection, configuration);

        VectorDatabaseRequest<StatusDTO> request =
                provider.createRequestFor(Command.DELETE, finalHost, collection, configuration, Map.of("ids", ids));
        return Stream.of(httpService.request(
                request.target(),
                request.requestCustomizer(),
                request.responseTransformer(),
                IntSets.immutable.of(200, 204)));
    }

    @Internal
    @Procedure("genai.vector.external.get")
    @Description("""
            Retrieves the vectors with the specified `ids` from the given collection.

            In Neo4j-Browser you can declare the configuration parameter as shown in the following examples.
            Authorization can be configured either through a simple `token` entry in the config map like this:
            ```
            :param configuration => ({token: 'your-api-key'})
            ```

            Or if your vendor has an usual format, via a map entry under the key `Headers` in the configuration parameter.
            The example uses a bearer token:
            ```
            :param configuration => ({Headers: {Authorization: 'Bearer whatever'}})
            ```
            """)
    public Stream<EmbeddingResult> get(
            @Name("url") String url,
            @Name("collection") String collection,
            @Name("ids") List<Object> ids,
            @Sensitive @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration) {
        return getAndUpdate0(url, collection, ids, configuration, true);
    }

    @Internal
    @Procedure(value = "genai.vector.external.getAndUpdate", mode = Mode.WRITE)
    @Description("""
            Retrieves the vectors with the specified `ids` from the given collection and updates existing Neo4j entities(Nodes or relationships).
            In Neo4j-Browser you can declare the configuration parameter as shown in the following examples.

            The mapping between vectors and entities is configured via a map entry under the key `mapping` in the configuration parameter.
            ```
            :param configuration => ({mapping: {embeddingKey: 'the_property_storing_the_vector', nodeLabel: 'TheLabel', entityKey: 'the_property_storing_the_mapping_key', metadataKey: ' the_property_stored_with_the_vector_in_the_vendor_db_matching_the_entity_key'}})
            ```

            Authorization can be configured either through a simple `token` entry in the config map like this:
            ```
            :param configuration => ({token: 'your-api-key'})
            ```

            Or if your vendor has an usual format, via a map entry under the key `Headers` in the configuration parameter.
            The example uses a bearer token:
            ```
            :param configuration => ({Headers: {Authorization: 'Bearer whatever'}})
            ```

            *NOTE* The name of this procedure is subject to change.
            """)
    public Stream<EmbeddingResult> getAndUpdate(
            @Name("url") String url,
            @Name("collection") String collection,
            @Name("ids") List<Object> ids,
            @Sensitive @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration) {

        return getAndUpdate0(url, collection, ids, configuration, null);
    }

    @SuppressWarnings("unchecked")
    private Stream<EmbeddingResult> getAndUpdate0(
            String url, String collection, List<Object> ids, Map<String, Object> configuration, Boolean forceReadOnly) {

        var rowMappingConfig = RowMappingConfig.of(configuration);
        var mappingConfig =
                EntityMappingConfig.of(RowMappingConfig.Keys.MAPPING.get(Map.class, configuration), forceReadOnly);
        var procedureArguments = ProcedureArguments.of(configuration, procedureCallContext);

        var provider = getVectorDatabaseProvider(url);
        monitors.vectorDb().getVector(provider.getName());
        var finalHost = translateDefaultHost(provider, Command.GET, url, collection, configuration);

        if (provider.supportsMultipleGet()) {
            VectorDatabaseRequest<Stream<Map<String, Object>>> request = provider.createRequestFor(
                    Command.GET,
                    finalHost,
                    collection,
                    configuration,
                    Map.of("ids", ids, "procedureArguments", procedureArguments, "rowMappingConfig", rowMappingConfig));
            return httpService
                    .request(request.target(), request.requestCustomizer(), request.responseTransformer())
                    .map(m -> toEmbeddingResult(m, procedureArguments, rowMappingConfig, mappingConfig));
        } else {
            return ids.stream()
                    .map(id -> provider.<Map<String, Object>>createRequestFor(
                            Command.GET,
                            finalHost,
                            collection,
                            configuration,
                            Map.of(
                                    "id",
                                    id,
                                    "procedureArguments",
                                    procedureArguments,
                                    "rowMappingConfig",
                                    rowMappingConfig)))
                    .map(request -> httpService.request(
                            request.target(), request.requestCustomizer(), request.responseTransformer()))
                    .map(m -> toEmbeddingResult(m, procedureArguments, rowMappingConfig, mappingConfig));
        }
    }

    @Internal
    @Procedure("genai.vector.external.upsert")
    @Description("""
            Creates or updates the vectors in the given collection.
            Depending on the provider, this procedure will issue more than one request.
            In Neo4j-Browser you can declare the configuration parameter as shown in the following examples.

            Vectors need to be specified like this:
            ```
            :param vector => ({id: 'optional_id', vector: [0.1, 0.2, 0.3], metadata: {field1: 'value1', field2: 'value2'}})
            ```

            Authorization can be configured either through a simple `token` entry in the config map like this:
            ```
            :param configuration => ({token: 'your-api-key'})
            ```

            Or if your vendor has an usual format, via a map entry under the key `Headers` in the configuration parameter.
            The example uses a bearer token:
            ```
            :param configuration => ({Headers: {Authorization: 'Bearer whatever'}})
            ```
            """)
    public Stream<StatusDTO> upsert(
            @Name("url") String url,
            @Name("collection") String collection,
            @Name("vectors") List<Map<String, Object>> vectors,
            @Sensitive @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration) {

        var provider = getVectorDatabaseProvider(url);
        monitors.vectorDb().upsert(provider.getName());
        var finalHost = translateDefaultHost(provider, Command.UPSERT, url, collection, configuration);

        if (provider.supportsMultipleUpserts()) {
            Map<String, Object> additionalArguments = Map.of("vectors", vectors);
            VectorDatabaseRequest<StatusDTO> request = provider.createRequestFor(
                    Command.UPSERT, finalHost, collection, configuration, additionalArguments);
            return Stream.of(
                    httpService.request(request.target(), request.requestCustomizer(), request.responseTransformer()));
        } else {
            for (Map<String, Object> vector : vectors) {
                try {
                    var request = provider.createRequestFor(
                            Command.UPSERT, finalHost, collection, configuration, Map.of("vector", vector));
                    httpService.request(request.target(), request.requestCustomizer(), request.responseTransformer());
                } catch (GenAIProcedureException ex) {
                    var request = provider.handleFailedUpsertRequest(
                            ex, finalHost, collection, configuration, Map.of("vector", vector));
                    httpService.request(request.target(), request.requestCustomizer(), request.responseTransformer());
                }
            }
            return Stream.of(StatusDTO.ok(null));
        }
    }

    @Internal
    @Procedure(value = "genai.vector.external.query")
    @Description("""
            Queries the closes vectors near the given vector in the named collection.
            In Neo4j-Browser you can declare the configuration parameter as shown in the following examples.

            Authorization can be configured either through a simple `token` entry in the config map like this:
            ```
            :param configuration => ({token: 'your-api-key'})
            ```

            Or if your vendor has an usual format, via a map entry under the key `Headers` in the configuration parameter.
            The example uses a bearer token:
            ```
            :param configuration => ({Headers: {Authorization: 'Bearer whatever'}})
            ```
            """)
    public Stream<EmbeddingResult> query(
            @Name("url") String url,
            @Name("collection") String collection,
            @Name("vector") List<Double> vector,
            @Name(value = "filter", defaultValue = "null") Object filter,
            @Name(value = "limit", defaultValue = "10") long limit,
            @Sensitive @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration)
            throws Exception {
        return queryAndUpdate0(url, collection, vector, filter, limit, configuration, true);
    }

    @Internal
    @Procedure(value = "genai.vector.external.queryAndUpdate", mode = Mode.WRITE)
    @Description("""
            Queries the closes vectors near the given vector in the named collection and updates existing Neo4j entities(Nodes or relationships).
            In Neo4j-Browser you can declare the configuration parameter as shown in the following examples.

            The mapping between vectors and entities is configured via a map entry under the key `mapping` in the configuration parameter.
            ```
            :param configuration => ({mapping: {embeddingKey: 'the_property_storing_the_vector', nodeLabel: 'TheLabel', entityKey: 'the_property_storing_the_mapping_key', metadataKey: ' the_property_stored_with_the_vector_in_the_vendor_db_matching_the_entity_key'}})
            ```

            Authorization can be configured either through a simple `token` entry in the config map like this:
            ```
            :param configuration => ({token: 'your-api-key'})
            ```

            Or if your vendor has an usual format, via a map entry under the key `Headers` in the configuration parameter.
            The example uses a bearer token:
            ```
            :param configuration => ({Headers: {Authorization: 'Bearer whatever'}})
            ```

            *NOTE* The name of this procedure is subject to change.
        """)
    public Stream<EmbeddingResult> queryAndUpdate(
            @Name("url") String url,
            @Name("collection") String collection,
            @Name("vector") List<Double> vector,
            @Name(value = "filter", defaultValue = "null") Object filter,
            @Name(value = "limit", defaultValue = "10") long limit,
            @Sensitive @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration) {
        return queryAndUpdate0(url, collection, vector, filter, limit, configuration, null);
    }

    @SuppressWarnings("unchecked")
    private Stream<EmbeddingResult> queryAndUpdate0(
            String url,
            String collection,
            List<Double> vector,
            Object filter,
            long limit,
            Map<String, Object> configuration,
            Boolean forceReadOnly) {

        var rowMappingConfig = RowMappingConfig.of(configuration);
        var mappingConfig =
                EntityMappingConfig.of(RowMappingConfig.Keys.MAPPING.get(Map.class, configuration), forceReadOnly);
        var procedureResultFields = procedureCallContext.outputFields().toList();
        var procedureArguments = ProcedureArguments.of(configuration, procedureCallContext);

        var additionalArguments = Map.of(
                "rowMappingConfig",
                rowMappingConfig,
                "vector",
                vector,
                "filter",
                Optional.ofNullable(filter),
                "limit",
                limit,
                "procedureResultFields",
                procedureResultFields,
                "procedureArguments",
                procedureArguments);
        var provider = getVectorDatabaseProvider(url);
        monitors.vectorDb().query(provider.getName());
        var finalHost = translateDefaultHost(provider, Command.QUERY, url, collection, configuration);

        VectorDatabaseRequest<Stream<Map<String, Object>>> request =
                provider.createRequestFor(Command.QUERY, finalHost, collection, configuration, additionalArguments);

        return httpService
                .request(request.target(), request.requestCustomizer(), request.responseTransformer())
                .map(m -> toEmbeddingResult(m, procedureArguments, rowMappingConfig, mappingConfig));
    }

    @SuppressWarnings("unchecked")
    private EmbeddingResult toEmbeddingResult(
            Map<String, Object> m,
            ProcedureArguments procedureArguments,
            RowMappingConfig embeddingConfig,
            EntityMappingConfig mappingConfig) {
        var id = procedureArguments.allResults() ? m.get(embeddingConfig.idKey()) : null;
        var embedding = procedureArguments.hasVector() ? (List<Double>) m.get(embeddingConfig.vectorKey()) : null;
        var metadata =
                procedureArguments.hasMetadata() ? (Map<String, Object>) m.get(embeddingConfig.metadataKey()) : null;
        var score = (Double) m.get(embeddingConfig.scoreKey());
        var text = procedureArguments.allResults() ? (String) m.get(embeddingConfig.textKey()) : null;

        var entity = handleMapping(tx, mappingConfig, metadata, embedding);
        return new EmbeddingResult(
                id,
                score,
                embedding,
                metadata,
                text,
                mappingConfig.nodeLabel() == null ? null : (Node) entity,
                mappingConfig.nodeLabel() != null ? null : (Relationship) entity);
    }

    private static Entity handleMapping(
            Transaction tx, EntityMappingConfig mapping, Map<String, Object> metadata, List<Double> embedding) {

        if (mapping.entityKey() == null) {
            return null;
        }
        if (metadata == null || metadata.isEmpty()) {
            throw new InvalidUsageException(
                    "To use mapping config, the metadata should not be empty. Make sure you execute `YIELD metadata` on the procedure");
        }
        if (mapping.nodeLabel() != null) {
            return handleMappingNode(tx, mapping, metadata, embedding);
        } else if (mapping.relType() != null) {
            return handleMappingRel(tx, mapping, metadata, embedding);
        } else {
            throw new InvalidUsageException("Mapping conf has to contain either label or type key");
        }
    }

    private static Entity handleMappingNode(
            Transaction transaction,
            EntityMappingConfig mapping,
            Map<String, Object> metadata,
            List<Double> embedding) {

        var propValue = metadata.get(mapping.metadataKey());
        if (propValue == null) {
            return null;
        }
        var node = transaction.findNode(Label.label(mapping.nodeLabel()), mapping.entityKey(), propValue);

        if (mapping.readOnly()) {
            return node;
        }
        if (mapping.mode() == MappingMode.CREATE_IF_MISSING && node == null) {
            node = transaction.createNode(Label.label(mapping.nodeLabel()));
            node.setProperty(mapping.entityKey(), propValue);
        }

        setPropertiesOnEntity(mapping, metadata, embedding, node);
        return node;
    }

    private static Entity handleMappingRel(
            Transaction transaction,
            EntityMappingConfig mapping,
            Map<String, Object> metadata,
            List<Double> embedding) {

        var propValue = metadata.get(mapping.metadataKey());
        if (propValue == null) {
            return null;
        }
        var rel = transaction.findRelationship(
                RelationshipType.withName(mapping.relType()), mapping.entityKey(), propValue);

        if (!mapping.readOnly()) {
            setPropertiesOnEntity(mapping, metadata, embedding, rel);
        }

        return rel;
    }

    private static void setPropertiesOnEntity(
            EntityMappingConfig mapping, Map<String, Object> properties, List<Double> embedding, Entity entity) {

        if (entity == null) {
            return;
        }

        for (var entry : properties.entrySet()) {
            if (entry.getValue() != null) {
                entity.setProperty(entry.getKey(), entry.getValue());
            }
        }
        if (mapping.embeddingKey() == null) {
            return;
        }
        if (embedding == null) {
            var embeddingErrMsg =
                    "The embedding value is null. Make sure you execute `YIELD embedding` on the procedure and you configured `%s: true`"
                            .formatted(RequestConfig.Keys.ALL_RESULTS.key());
            throw new InvalidUsageException(embeddingErrMsg);
        }

        float[] floats = listOfNumbersToFloatArray(embedding);
        entity.setProperty(mapping.embeddingKey(), floats);
    }

    private static float[] listOfNumbersToFloatArray(List<? extends Number> embedding) {
        float[] floats = new float[embedding.size()];
        int i = 0;
        for (var item : embedding) {
            floats[i] = item.floatValue();
            i++;
        }
        return floats;
    }

    private String translateDefaultHost(
            VectorDatabaseProvider provider,
            Command command,
            String defaultHost,
            String collection,
            Map<String, Object> configuration) {
        return provider.getCollectionSpecificHost(command, defaultHost, collection, configuration)
                .map(r -> httpService.request(r.target(), r.requestCustomizer(), r.responseTransformer()))
                .orElse(defaultHost);
    }

    public record ProcedureArguments(boolean allResults, boolean hasVector, boolean hasMetadata) {

        static ProcedureArguments of(Map<String, Object> configuration, ProcedureCallContext procedureCallContext) {

            var requestConfig = RequestConfig.of(configuration);
            var outputFields = procedureCallContext.outputFields().toList();

            boolean allResults = requestConfig.allResults();
            boolean hasVector = outputFields.contains("vector") && allResults;
            boolean hasMetadata = outputFields.contains("metadata");
            return new ProcedureArguments(allResults, hasVector, hasMetadata);
        }
    }

    public record ProviderDTO(String name) {}

    public record InfoDTO(String status, Map<String, Object> vendor) {

        public static InfoDTO of(Map<String, Object> vendor) {
            return new InfoDTO("ok", vendor == null ? Map.of() : vendor);
        }

        public InfoDTO {
            vendor = vendor == null ? Map.of() : vendor;
        }
    }

    public record StatusDTO(String status, Map<String, Object> vendor) {

        public static StatusDTO ok(Map<String, Object> vendor) {
            return new StatusDTO("ok", vendor);
        }

        public static StatusDTO failure(Map<String, Object> vendor) {
            return new StatusDTO("failure", vendor);
        }

        public static StatusDTO unknown(Map<String, Object> vendor) {
            return new StatusDTO("unknown", vendor);
        }

        public StatusDTO {
            vendor = vendor == null ? Map.of() : vendor;
        }
    }

    public record EmbeddingResult(
            Object id,
            Double score,
            List<Double> vector,
            Map<String, Object> metadata,
            String text,
            Node node,
            Relationship rel) {}

    /**
     * Holds an optional vendor token and a configuration. Might be the old configuration, or a new one, depending on
     * whether the token was extracted from it or not.
     * @param token
     * @param configuration
     */
    private record OptionalTokenAndConfig(String token, Map<String, Object> configuration) {

        static OptionalTokenAndConfig of(Map<String, Object> configuration) {

            String token = (String) configuration.get("token");
            if (token == null) {
                return new OptionalTokenAndConfig(token, configuration);
            }

            var newConfig = new HashMap<>(configuration);
            newConfig.remove("token");
            return new OptionalTokenAndConfig(token, newConfig);
        }
    }
}
