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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.auth.exception.AuthException;
import io.weaviate.client.v1.data.model.WeaviateObject;
import io.weaviate.client.v1.misc.model.VectorIndexConfig;
import io.weaviate.client.v1.schema.model.WeaviateClass;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.neo4j.genai.util.GenAIProcedureException;
import org.neo4j.genai.util.JsonUtils;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.graphdb.Transaction;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;
import org.testcontainers.qdrant.QdrantContainer;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.weaviate.WeaviateContainer;

@Testcontainers(disabledWithoutDocker = true)
final class ProvidersIT extends IntegrationTestBase {

    static final String ADMIN_KEY = "my_admin_api_key";
    static final String READONLY_KEY = "my_readonly_api_key";

    @Container
    private static final QdrantContainer QDRANT_CONTAINER = new QdrantContainer("qdrant/qdrant:v1.7.4")
            .withEnv("QDRANT__SERVICE__API_KEY", ADMIN_KEY)
            .withEnv("QDRANT__SERVICE__READ_ONLY_API_KEY", READONLY_KEY)
            .withEnv("QDRANT__TELEMETRY_DISABLED", "true");

    @Container
    private static final WeaviateContainer WEAVIATE_CONTAINER = new WeaviateContainer(
                    "semitechnologies/weaviate:1.24.5")
            .withEnv("AUTHENTICATION_APIKEY_ENABLED", "true")
            .withEnv("AUTHENTICATION_APIKEY_ALLOWED_KEYS", ADMIN_KEY + "," + READONLY_KEY)
            .withEnv("AUTHENTICATION_APIKEY_USERS", "jane@doe.com,ian-smith")
            .withEnv("AUTHORIZATION_ADMINLIST_ENABLED", "true")
            .withEnv("AUTHORIZATION_ADMINLIST_USERS", "jane@doe.com,john@doe.com")
            .withEnv("AUTHORIZATION_ADMINLIST_READONLY_USERS", "ian-smith,roberta@doe.com");

    private static final String CHROMA_AUTH_FILE_PATH = "/auth.yaml";

    @Container
    private static final ChromaDBContainer CHROMA_CONTAINER = new ChromaDBContainer("chromadb/chroma:0.5.12.dev19")
            .withCopyFileToContainer(MountableFile.forClasspathResource("/chroma_auth.yaml"), CHROMA_AUTH_FILE_PATH)
            .withEnv("CHROMA_SERVER_AUTHN_CREDENTIALS_FILE", CHROMA_AUTH_FILE_PATH)
            .withEnv("CHROMA_SERVER_AUTHZ_CONFIG_FILE", CHROMA_AUTH_FILE_PATH)
            .withEnv("CHROMA_SERVER_AUTHN_PROVIDER", "chromadb.auth.token_authn.TokenAuthenticationServerProvider")
            .withEnv("CHROMA_SERVER_AUTHZ_PROVIDER", "chromadb.auth.simple_rbac_authz.SimpleRBACAuthorizationProvider");

    @Container
    private static final MilvusContainer MILVUS_CONTAINER = new MilvusContainer("milvusdb/milvus:v2.4.17");

    static String WEAVIATE_BASE_URL;
    private static WeaviateClient WEAVIATE_CLIENT;

    static String QDRANT_BASE_URL;

    static String CHROMA_BASE_URL;
    private static String CHROMA_COLLECTION_ID;

    static String MILVUS_BASE_URL;

    private static final Map<String, Object> PROVIDER_SPECIFIC_FILTER = Map.of(
            "weaviate",
            "{operator: Equal, valueString: \"London\", path: [\"city\"]}",
            "qdrant",
            Map.of("must", List.of(Map.of("key", "city", "match", Map.of("value", "London")))),
            "chromadb",
            Map.of("city", "London"),
            "milvus",
            "payload[\"city\"] == 'London'");

    private static final String COLLECTION_NAME = "GenaiTestCollection";
    private static final String COLLECTION_TO_BE_DELETED = "CollectionToBeDeleted";
    private static final String COLLECTION_TO_BE_CREATED = "CollectionToBeCreated";

    @Override
    String getCollectionName(String provider, boolean realName) {
        if (provider.equals("chromadb") && !realName) {
            return CHROMA_COLLECTION_ID;
        }
        return COLLECTION_NAME;
    }

    @Override
    String getCollectionNameToBeDeleted(String provider) {
        return COLLECTION_TO_BE_DELETED;
    }

    @Override
    String getCollectionNameToBeCreated(String provider) {
        return COLLECTION_TO_BE_CREATED;
    }

    @Override
    Map<String, String> getReadOnlyAuth(String provider) {
        if ("milvus".equals(provider)) {
            return Map.of("Authorization", "Bearer readOnly:" + getReadOnlyKey(provider));
        }
        return Map.of("Authorization", "Bearer " + getReadOnlyKey(provider));
    }

    @Override
    String getReadOnlyKey(String provider) {
        return READONLY_KEY;
    }

    @Override
    Map<String, String> getAdminAuth(String provider) {
        if ("milvus".equals(provider)) {
            return Map.of("Authorization", "Bearer readOnly:" + getAdminKey(provider));
        }
        return Map.of("Authorization", "Bearer " + getAdminKey(provider));
    }

    @Override
    String getAdminKey(String provider) {
        return ADMIN_KEY;
    }

    @Override
    protected Object getIdValue(String id, String provider) {
        if ("milvus".equals(provider)) {
            return switch (id) {
                case ID_1 -> LONG_ID_1;
                case ID_2 -> LONG_ID_2;
                case ID_3 -> LONG_ID_3;
                case ID_4 -> LONG_ID_4;
                case ID_5 -> LONG_ID_5;
                case ID_6 -> LONG_ID_6;
                default -> throw new IllegalArgumentException("cannot convert id %s to long".formatted(id));
            };
        }

        return id;
    }

    @BeforeAll
    void setup() throws Exception {
        setupWeaviate();
        setupQdrant();
        setupChromaDb();
        setupMilvus();
    }

    @Test
    void shouldHandleWeaviateErrors() {
        try (Transaction tx = database.beginTx()) {
            var query = """
				WITH
				    $url AS uri,
				    $token AS token,
				    $collection AS collection,
				    [1, 2, 3, 4] AS search_vector
				
				CALL genai.vector.external.query(uri, collection, search_vector, null, 10,
				{
				    token: token,
				    fields:["unknownField"],
				    allResults: true
				})
				YIELD  score, metadata, id
				RETURN score, metadata, id
				""";
            var result = tx.execute(
                    query, Map.of("url", WEAVIATE_BASE_URL, "token", ADMIN_KEY, "collection", COLLECTION_NAME));
            assertThatExceptionOfType(QueryExecutionException.class)
                    .isThrownBy(result::hasNext)
                    .withRootCauseInstanceOf(GenAIProcedureException.class)
                    .withStackTraceContaining("Cannot query field \"unknownField\" on type \"GenaiTestCollection\".");
        }
    }

    @Test
    void shouldHandleQdrantCreateCollectionErrors() {
        try (Transaction tx = database.beginTx()) {
            var query =
                    "CALL genai.vector.external.createCollection($url, $collectionName, 'Wurstsalat', 4, {token: $token})";
            var result = tx.execute(
                    query,
                    Map.of("url", QDRANT_BASE_URL, "token", ADMIN_KEY, "collectionName", "aRandomNewCollection"));
            assertThatExceptionOfType(QueryExecutionException.class)
                    .isThrownBy(result::hasNext)
                    .withRootCauseInstanceOf(GenAIProcedureException.class)
                    .withStackTraceContaining(
                            "Format error in JSON body: data did not match any variant of untagged enum VectorsConfig at line 1 column 46");
        }
    }

    static void setupWeaviate() throws AuthException {
        WEAVIATE_BASE_URL = "http://" + WEAVIATE_CONTAINER.getHttpHostAddress();
        WEAVIATE_CLIENT =
                WeaviateAuthClient.apiKey(new Config("http", WEAVIATE_CONTAINER.getHttpHostAddress()), ADMIN_KEY);

        var emptyClass = WeaviateClass.builder()
                .className(COLLECTION_NAME)
                .vectorIndexConfig(
                        VectorIndexConfig.builder().distance("cosine").build())
                .build();

        var result =
                WEAVIATE_CLIENT.schema().classCreator().withClass(emptyClass).run();
        assertThat(result.getResult()).isTrue();
        assertThat(result.hasErrors()).isFalse();

        try (var batcher = WEAVIATE_CLIENT.batch().objectsAutoBatcher()) {
            batcher.withObjects(
                            WeaviateObject.builder()
                                    .className(COLLECTION_NAME)
                                    .id(ID_1)
                                    .vector(new Float[] {0.05f, 0.61f, 0.76f, 0.74f})
                                    .properties(Map.of("city", "Berlin", "foo", "one"))
                                    .build(),
                            WeaviateObject.builder()
                                    .className(COLLECTION_NAME)
                                    .id(ID_2)
                                    .vector(new Float[] {0.19f, 0.81f, 0.75f, 0.11f})
                                    .properties(Map.of("city", "London", "foo", "two"))
                                    .build())
                    .run();
        }

        for (boolean b : new boolean[] {true, false}) {
            result = WEAVIATE_CLIENT
                    .schema()
                    .classCreator()
                    .withClass(WeaviateClass.builder()
                            .className(COLLECTION_TO_BE_DELETED + b)
                            .build())
                    .run();

            assertThat(result.getResult()).isTrue();
            assertThat(result.hasErrors()).isFalse();
        }
    }

    private static void setupQdrant() throws Exception {
        var client = HttpClient.newHttpClient();
        QDRANT_BASE_URL = "http://" + QDRANT_CONTAINER.getHost() + ":" + QDRANT_CONTAINER.getMappedPort(6333);
        var createCollectionRequest = HttpRequest.newBuilder(
                        URI.create(QDRANT_BASE_URL + "/collections/" + COLLECTION_NAME))
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .PUT(HttpRequest.BodyPublishers.ofString("""
			{
						"vectors": {
							"size": 4,
							"distance": "Cosine"
						}
					}
			"""))
                .build();

        client.send(createCollectionRequest, HttpResponse.BodyHandlers.discarding());

        var insertIntoCollectionRequest = HttpRequest.newBuilder(
                        URI.create(QDRANT_BASE_URL + "/collections/" + COLLECTION_NAME + "/points?wait=true"))
                .header(RequestConfig.Keys.AUTHORIZATION.key(), "Bearer " + ADMIN_KEY)
                .PUT(HttpRequest.BodyPublishers.ofString("""
			{
			  "points": [
				{"id": "%s", "vector": [0.05, 0.61, 0.76, 0.74], "payload": {"city": "Berlin", "foo": "one"}},
				{"id": "%s", "vector": [0.19, 0.81, 0.75, 0.11], "payload": {"city": "London", "foo": "two"}}
			]}""".formatted(ID_1, ID_2)))
                .build();

        client.send(insertIntoCollectionRequest, HttpResponse.BodyHandlers.discarding());

        QDRANT_BASE_URL = "http://" + QDRANT_CONTAINER.getHost() + ":" + QDRANT_CONTAINER.getMappedPort(6333);
        for (boolean b : new boolean[] {true, false}) {
            // create a temporary collection
            createCollectionRequest = HttpRequest.newBuilder(
                            URI.create(QDRANT_BASE_URL + "/collections/" + COLLECTION_TO_BE_DELETED + b))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .PUT(HttpRequest.BodyPublishers.ofString("""
                            {
                            			"vectors": {
                            				"size": 4,
                            				"distance": "Cosine"
                            			}
                            		}
                            """))
                    .build();

            client.send(createCollectionRequest, HttpResponse.BodyHandlers.discarding());
        }
    }

    private static void setupChromaDb() throws Exception {
        CHROMA_BASE_URL = CHROMA_CONTAINER.getEndpoint();
        var client = HttpClient.newHttpClient();
        var createCollectionRequest = HttpRequest.newBuilder(URI.create(CHROMA_BASE_URL + "/api/v1/collections"))
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .version(HttpClient.Version.HTTP_1_1)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"name\": \"%s\", \"metadata\": {\"size\": 4,\"hnsw:space\": \"cosine\"}}"
                                .formatted(COLLECTION_NAME)))
                .build();

        var response = client.send(createCollectionRequest, HttpResponse.BodyHandlers.ofString());
        var result = JsonUtils.getObjectMapper().readValue(response.body(), Map.class);
        CHROMA_COLLECTION_ID = result.get("id").toString();

        var createVectorsRequest = HttpRequest.newBuilder(
                        URI.create(CHROMA_BASE_URL + "/api/v1/collections/" + CHROMA_COLLECTION_ID + "/upsert"))
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .version(HttpClient.Version.HTTP_1_1)
                .POST(HttpRequest.BodyPublishers.ofString("""
                {
                    "ids": ["%s", "%s"],
                    "embeddings": [[0.05, 0.61, 0.76, 0.74], [0.19, 0.81, 0.75, 0.11]],
                    "metadatas": [{"city": "Berlin", "foo": "one"},{"city": "London", "foo": "two"}]
                }""".formatted(ID_1, ID_2)))
                .build();
        client.send(createVectorsRequest, HttpResponse.BodyHandlers.discarding());

        for (boolean b : new boolean[] {true, false}) {
            var createCollectionToBeDeletedRequest = HttpRequest.newBuilder(
                            URI.create(CHROMA_BASE_URL + "/api/v1/collections"))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .version(HttpClient.Version.HTTP_1_1)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"name\": \"%s\", \"metadata\": {\"size\": 4,\"hnsw:space\": \"cosine\"}}"
                                    .formatted(COLLECTION_TO_BE_DELETED + b)))
                    .build();

            client.send(createCollectionToBeDeletedRequest, HttpResponse.BodyHandlers.ofString());
        }
    }

    record ProviderAndUrl(String provider, String url) {}

    private static void setupMilvus() throws Exception {
        MILVUS_BASE_URL = MILVUS_CONTAINER.getEndpoint();

        HttpClient client = HttpClient.newHttpClient();
        spinWait(() -> {
            try {
                var createReadOnlyUser = HttpRequest.newBuilder(
                                URI.create(MILVUS_BASE_URL + "/v2/vectordb/users/create"))
                        .header("Authorization", "Bearer root:Milvus")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                    {"userName":"readOnly", "password": "%s"}""".formatted(READONLY_KEY)))
                        .build();

                client.send(createReadOnlyUser, HttpResponse.BodyHandlers.discarding());
                var grantPublicRoleRequest = HttpRequest.newBuilder(
                                URI.create(MILVUS_BASE_URL + "/v2/vectordb/users/grant_role"))
                        .header("Authorization", "Bearer root:Milvus")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                    {"userName": "readOnly", "roleName":"public"}"""))
                        .build();
                client.send(grantPublicRoleRequest, HttpResponse.BodyHandlers.discarding());
                var setAdminPasswordRequest = HttpRequest.newBuilder(
                                URI.create(MILVUS_BASE_URL + "/v2/vectordb/users/update_password"))
                        .header("Authorization", "Bearer root:Milvus")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                    {"userName": "root", "password":"Milvus", "newPassword":"%s"}""".formatted(ADMIN_KEY)))
                        .build();
                client.send(setAdminPasswordRequest, HttpResponse.BodyHandlers.discarding());
                var createCollectionRequest = HttpRequest.newBuilder(
                                URI.create(MILVUS_BASE_URL + "/v2/vectordb/collections/create"))
                        .header("Authorization", "Bearer root:" + ADMIN_KEY)
                        .POST(HttpRequest.BodyPublishers.ofString("""
                    {"collectionName": "%s", "dimension":4, "metricType":"COSINE"}""".formatted(COLLECTION_NAME)))
                        .build();
                client.send(createCollectionRequest, HttpResponse.BodyHandlers.discarding());
                var createVectorsRequest = HttpRequest.newBuilder(
                                URI.create(MILVUS_BASE_URL + "/v2/vectordb/entities/upsert"))
                        .header("Authorization", "Bearer root:" + ADMIN_KEY)
                        .POST(HttpRequest.BodyPublishers.ofString("""
                {"data": [
                    {"id": "%s", "vector": [0.05, 0.61, 0.76, 0.74], "payload": {"city": "Berlin", "foo": "one"}},
                    {"id": "%s", "vector": [0.19, 0.81, 0.75, 0.11], "payload": {"city": "London", "foo": "two"}}
                    ],
                 "collectionName":"%s"}""".formatted(LONG_ID_1, LONG_ID_2, COLLECTION_NAME)))
                        .build();
                client.send(createVectorsRequest, HttpResponse.BodyHandlers.discarding());

                // verify the existence of collection and vectors
                var getCollectionRequest = HttpRequest.newBuilder(
                                URI.create(MILVUS_BASE_URL + "/v2/vectordb/collections/describe"))
                        .header("Authorization", "Bearer root:" + ADMIN_KEY)
                        .POST(HttpRequest.BodyPublishers.ofString("""
                    {"collectionName":"%s"}""".formatted(COLLECTION_NAME)))
                        .build();
                var getCollectionResponse = client.send(getCollectionRequest, HttpResponse.BodyHandlers.ofString());
                assertThat(getCollectionResponse.body()).contains(COLLECTION_NAME);

                var getPointRequest = HttpRequest.newBuilder(URI.create(MILVUS_BASE_URL + "/v2/vectordb/entities/get"))
                        .header("Authorization", "Bearer root:" + ADMIN_KEY)
                        .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.getObjectMapper()
                                .writeValueAsString(Map.of(
                                        "id", List.of(LONG_ID_1, LONG_ID_2),
                                        "collectionName", COLLECTION_NAME,
                                        "outputFields", List.of("vector", "payload")))))
                        .build();
                var getPointResponse = client.send(getPointRequest, HttpResponse.BodyHandlers.ofString());
                assertThat(getPointResponse.body()).contains("\"Berlin\"").contains("\"London\"");
            } catch (Exception | AssertionError e) {
                return true;
            }
            return false;
        });
        client.close();
    }

    Stream<Arguments> providers() {
        return Stream.of(
                        new ProviderAndUrl("weaviate", WEAVIATE_BASE_URL),
                        new ProviderAndUrl("qdrant", QDRANT_BASE_URL),
                        new ProviderAndUrl("chromadb", CHROMA_BASE_URL),
                        new ProviderAndUrl("milvus", MILVUS_BASE_URL))
                .flatMap(pu -> Stream.of(
                        Arguments.argumentSet(pu.provider + " no token", pu.provider, pu.url, false),
                        Arguments.argumentSet(pu.provider + " use token", pu.provider, pu.url, true)));
    }

    @Override
    Object getProviderSpecificFilter(String provider) {
        return PROVIDER_SPECIFIC_FILTER.get(provider);
    }

    @Override
    void assertThatCollectionHasBeenCreated(String provider, String suffix) throws Exception {
        if ("weaviate".equals(provider)) {
            var result = WEAVIATE_CLIENT
                    .schema()
                    .exists()
                    .withClassName(COLLECTION_TO_BE_CREATED + suffix)
                    .run();
            assertThat(result.getResult()).isTrue();
            assertThat(result.hasErrors()).isFalse();
        } else if ("qdrant".equals(provider)) {
            var client = HttpClient.newHttpClient();
            QDRANT_BASE_URL = "http://" + QDRANT_CONTAINER.getHost() + ":" + QDRANT_CONTAINER.getMappedPort(6333);
            var verifyCollectionRequest = HttpRequest.newBuilder(
                            URI.create(QDRANT_BASE_URL + "/collections/" + COLLECTION_TO_BE_CREATED + suffix))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .GET()
                    .build();

            var response = client.send(verifyCollectionRequest, HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(200);
            client.close();
        } else if ("chromadb".equals(provider)) {
            var client =
                    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            var verifyCollectionRequest = HttpRequest.newBuilder(
                            URI.create(CHROMA_BASE_URL + "/api/v1/collections/" + COLLECTION_TO_BE_CREATED + suffix))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .GET()
                    .build();
            var response = client.send(verifyCollectionRequest, HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(200);
            client.close();
        } else if ("milvus".equals(provider)) {
            var client = HttpClient.newHttpClient();
            spinWait(() -> {
                try {
                    var verifyCollectionRequest = HttpRequest.newBuilder(
                                    URI.create(MILVUS_BASE_URL + "/v2/vectordb/collections/describe"))
                            .header("Authorization", "Bearer root:" + ADMIN_KEY)
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                {"collectionName":"%s"}""".formatted(COLLECTION_TO_BE_CREATED)))
                            .build();
                    var response = client.send(verifyCollectionRequest, HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).isEqualTo(200);
                } catch (Exception | AssertionError e) {
                    return true;
                }
                return false;
            });
            client.close();
        } else {
            fail("no matching provider assert for %s".formatted(provider));
        }
    }

    @Override
    void assertThatCollectionHasBeenDeleted(String provider) throws Exception {
        if ("weaviate".equals(provider)) {
            var result = WEAVIATE_CLIENT
                    .schema()
                    .exists()
                    .withClassName(COLLECTION_TO_BE_DELETED)
                    .run();
            assertThat(result.getResult()).isFalse();
            assertThat(result.hasErrors()).isFalse();
        } else if ("qdrant".equals(provider)) {
            var client = HttpClient.newHttpClient();
            var createCollectionRequest = HttpRequest.newBuilder(
                            URI.create(QDRANT_BASE_URL + "/collections/" + COLLECTION_TO_BE_DELETED))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .GET()
                    .build();
            var response = client.send(createCollectionRequest, HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(404);
            client.close();
        } else if ("chromadb".equals(provider)) {
            var client =
                    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            var verifyCollectionRequest = HttpRequest.newBuilder(
                            URI.create(CHROMA_BASE_URL + "/api/v1/collections/" + COLLECTION_TO_BE_DELETED))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .GET()
                    .build();
            var response = client.send(verifyCollectionRequest, HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(400);
            client.close();
        } else if ("milvus".equals(provider)) {
            var client = HttpClient.newHttpClient();
            spinWait(() -> {
                try {
                    var verifyCollectionRequest = HttpRequest.newBuilder(
                                    URI.create(MILVUS_BASE_URL + "/v2/vectordb/collections/describe"))
                            .header("Authorization", "Bearer " + ADMIN_KEY)
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                {"collectionName": "%s"}""".formatted(COLLECTION_TO_BE_DELETED)))
                            .build();
                    var response = client.send(verifyCollectionRequest, HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).contains("can't find collection");
                } catch (Exception | AssertionError e) {
                    return true;
                }
                return false;
            });
            client.close();
        } else {
            fail("no matching provider assert for %s".formatted(provider));
        }
    }

    @Override
    void createVectorForDeletion(String provider) throws Exception {
        if ("weaviate".equals(provider)) {
            try (var batcher = WEAVIATE_CLIENT.batch().objectsAutoBatcher()) {
                batcher.withObjects(
                                WeaviateObject.builder()
                                        .className(COLLECTION_NAME)
                                        .id(ID_3)
                                        .vector(new Float[] {0.19f, 0.81f, 0.75f, 0.11f})
                                        .properties(Map.of("foo", "baz"))
                                        .build(),
                                WeaviateObject.builder()
                                        .className(COLLECTION_NAME)
                                        .id(ID_4)
                                        .vector(new Float[] {0.19f, 0.81f, 0.75f, 0.11f})
                                        .properties(Map.of("foo", "baz"))
                                        .build())
                        .run();
            }
        } else if ("qdrant".equals(provider)) {
            // even though the test passes for unknown ids, we want to ensure that it really got
            // deleted
            HttpClient client = HttpClient.newHttpClient();
            var insertIntoCollectionRequest = HttpRequest.newBuilder(
                            URI.create(QDRANT_BASE_URL + "/collections/" + COLLECTION_NAME + "/points?wait=true"))
                    .header(RequestConfig.Keys.AUTHORIZATION.key(), "Bearer " + ADMIN_KEY)
                    .PUT(HttpRequest.BodyPublishers.ofString("""
                    {
                    "points": [
                    {"id": "%s", "vector": [0.05, 0.61, 0.76, 0.74], "payload": {"city": "Braunschweig", "foo": "three"}},
                    {"id": "%s", "vector": [0.19, 0.81, 0.75, 0.11], "payload": {"city": "Aachen", "foo": "four"}}
                    ]}""".formatted(ID_3, ID_4)))
                    .build();
            client.send(insertIntoCollectionRequest, HttpResponse.BodyHandlers.ofString());
            client.close();
        } else if ("chromadb".equals(provider)) {
            HttpClient client =
                    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            var createVectorsRequest = HttpRequest.newBuilder(
                            URI.create(CHROMA_BASE_URL + "/api/v1/collections/" + CHROMA_COLLECTION_ID + "/upsert"))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .version(HttpClient.Version.HTTP_1_1)
                    .POST(HttpRequest.BodyPublishers.ofString("""
            {
                "ids": ["%s", "%s"],
                "embeddings": [[0.05, 0.61, 0.76, 0.74], [0.19, 0.81, 0.75, 0.11]],
                "metadatas": [{"city": "Braunschweig", "foo": "three"},{"city": "Aachen", "foo": "four"}]
            }""".formatted(ID_3, ID_4)))
                    .build();
            client.send(createVectorsRequest, HttpResponse.BodyHandlers.discarding());
            client.close();
        } else if ("milvus".equals(provider)) {
            HttpClient client = HttpClient.newHttpClient();
            var createVectorsRequest = HttpRequest.newBuilder(
                            URI.create(MILVUS_BASE_URL + "/v2/vectordb/entities/upsert"))
                    .header("Authorization", "Bearer root:" + ADMIN_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString("""
                    {"data": [
                    {"id": %s, "vector": [0.05, 0.61, 0.76, 0.74], "payload": {"city": "Braunschweig", "foo": "three"}},
                    {"id": %s, "vector": [0.19, 0.81, 0.75, 0.11], "payload": {"city": "Aachen", "foo": "four"}}
                        ],
                     "collectionName":"%s"}""".formatted(LONG_ID_3, LONG_ID_4, COLLECTION_NAME)))
                    .build();
            client.send(createVectorsRequest, HttpResponse.BodyHandlers.discarding());

            spinWait(() -> {
                try {
                    var getPointRequest = HttpRequest.newBuilder(
                                    URI.create(MILVUS_BASE_URL + "/v2/vectordb/entities/get"))
                            .header("Authorization", "Bearer root:" + ADMIN_KEY)
                            .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.getObjectMapper()
                                    .writeValueAsString(Map.of(
                                            "id", List.of(LONG_ID_4),
                                            "collectionName", COLLECTION_NAME,
                                            "outputFields", List.of("vector", "payload")))))
                            .build();
                    var getPointResponse = client.send(getPointRequest, HttpResponse.BodyHandlers.ofString());
                    assertThat(getPointResponse.body()).contains("\"payload\":");
                } catch (Exception | AssertionError e) {
                    return true;
                }
                return false;
            });
            client.close();
        }
    }

    @Override
    void assertThatVectorHasBeenDeleted(String provider) throws Exception {
        if ("weaviate".equals(provider)) {
            var result = WEAVIATE_CLIENT
                    .data()
                    .objectsGetter()
                    .withClassName(COLLECTION_NAME)
                    .run();
            assertThat(result.hasErrors()).isFalse();
            assertThat(result.getResult()).hasSize(2).map(WeaviateObject::getId).containsExactlyInAnyOrder(ID_1, ID_2);
        } else if ("qdrant".equals(provider)) {
            var verifyDeletionRequest = HttpRequest.newBuilder(
                            URI.create(QDRANT_BASE_URL + "/collections/" + COLLECTION_NAME + "/points"))
                    .header(RequestConfig.Keys.AUTHORIZATION.key(), "Bearer " + ADMIN_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString("""
                    {
                    "ids": [
                    "%s", "%s"
                    ]}
                    """.formatted(ID_3, ID_4)))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            var response = client.send(verifyDeletionRequest, HttpResponse.BodyHandlers.ofString());
            var responseBody = JsonUtils.getObjectMapper().readValue(response.body(), Map.class);
            var resultEntries = (List<?>) responseBody.get("result");
            assertThat(resultEntries).isEmpty();
            client.close();
        } else if ("chromadb".equals(provider)) {
            var client =
                    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            var verifyCollectionRequest = HttpRequest.newBuilder(
                            URI.create(CHROMA_BASE_URL + "/api/v1/collections/" + getCollectionName(provider) + "/get"))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JsonUtils.getObjectMapper().writeValueAsString(Map.of("ids", List.of(ID_3, ID_4)))))
                    .build();
            var vectorResponse = client.send(verifyCollectionRequest, HttpResponse.BodyHandlers.ofString());
            assertThat(vectorResponse.body()).contains("\"ids\":[]"); // assert no ids in response means nothing found
            assertThat(vectorResponse.statusCode()).isEqualTo(200);
            client.close();
        } else if ("milvus".equals(provider)) {
            var client = HttpClient.newHttpClient();
            spinWait(() -> {
                try {
                    var verifyCollectionRequest = HttpRequest.newBuilder(
                                    URI.create(MILVUS_BASE_URL + "/v2/vectordb/entities/get"))
                            .header("Authorization", "Bearer " + ADMIN_KEY)
                            .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.getObjectMapper()
                                    .writeValueAsString(Map.of(
                                            "id", List.of(LONG_ID_3, LONG_ID_4), "collectionName", COLLECTION_NAME))))
                            .build();
                    var vectorResponse = client.send(verifyCollectionRequest, HttpResponse.BodyHandlers.ofString());
                    assertThat(vectorResponse.body()).contains("\"data\":[]"); // assert nothing found
                    assertThat(vectorResponse.statusCode()).isEqualTo(200);
                } catch (Exception | AssertionError e) {
                    return true;
                }
                return false;
            });
            client.close();
        } else {
            fail("no matching provider assert for %s".formatted(provider));
        }
    }

    @Override
    void assertThatVectorHasBeenCreated(String provider) throws Exception {
        if ("weaviate".equals(provider)) {
            var result = WEAVIATE_CLIENT
                    .data()
                    .objectsGetter()
                    .withClassName(COLLECTION_NAME)
                    .withID(ID_5)
                    .withVector()
                    .run();
            assertThat(result.hasErrors()).isFalse();
            assertThat(result.getResult()).hasSize(1);
            assertThat(result.getResult()).first().satisfies(o -> {
                assertThat(o.getProperties()).containsEntry("bla", "blubb");
                assertThat(o.getVector()).isEqualTo(new Float[] {0.2f, 0.2f, 0.2f, 0.2f});
            });
            assertThat(WEAVIATE_CLIENT
                            .data()
                            .deleter()
                            .withClassName(COLLECTION_NAME)
                            .withID(ID_5)
                            .run()
                            .hasErrors())
                    .isFalse();
        } else if ("qdrant".equals(provider)) {
            // verify update
            var client = HttpClient.newHttpClient();
            var getPointRequest = HttpRequest.newBuilder(
                            URI.create(QDRANT_BASE_URL + "/collections/" + COLLECTION_NAME + "/points/" + ID_5))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .GET()
                    .build();
            var getPointResponse = client.send(getPointRequest, HttpResponse.BodyHandlers.ofString());
            assertThat(getPointResponse.body())
                    .contains("\"payload\":{\"bla\":\"blubb\"}")
                    .contains("\"vector\":[0.5,0.5,0.5,0.5]");
            // clean up new vector
            var deleteVectorRequest = HttpRequest.newBuilder(URI.create(
                            QDRANT_BASE_URL + "/collections/" + COLLECTION_NAME + "/points/delete?wait=true"))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JsonUtils.getObjectMapper().writeValueAsString(Map.of("points", List.of(ID_5)))))
                    .build();

            var response = client.send(deleteVectorRequest, HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(200);
            client.close();
        } else if ("chromadb".equals(provider)) {
            var client =
                    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            var verifyCollectionRequest = HttpRequest.newBuilder(
                            URI.create(CHROMA_BASE_URL + "/api/v1/collections/" + getCollectionName(provider) + "/get"))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JsonUtils.getObjectMapper().writeValueAsString(Map.of("ids", List.of(ID_5)))))
                    .build();
            var response = client.send(verifyCollectionRequest, HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(200);
            // clean up new vector
            var deleteVectorRequest = HttpRequest.newBuilder(URI.create(
                            CHROMA_BASE_URL + "/api/v1/collections/" + getCollectionName(provider) + "/delete"))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString("""
            {"ids":["%s","%s"]}""".formatted(ID_5, ID_6)))
                    .build();
            client.send(deleteVectorRequest, HttpResponse.BodyHandlers.discarding());
            client.close();
        } else if ("milvus".equals(provider)) {
            // verify update
            var client = HttpClient.newHttpClient();
            spinWait(() -> {
                try {
                    var getPointRequest = HttpRequest.newBuilder(
                                    URI.create(MILVUS_BASE_URL + "/v2/vectordb/entities/get"))
                            .header("Authorization", "Bearer root:" + ADMIN_KEY)
                            .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.getObjectMapper()
                                    .writeValueAsString(Map.of(
                                            "id", List.of(LONG_ID_5),
                                            "collectionName", COLLECTION_NAME,
                                            "outputFields", List.of("vector", "payload")))))
                            .build();
                    var getPointResponse = client.send(getPointRequest, HttpResponse.BodyHandlers.ofString());
                    assertThat(getPointResponse.body())
                            .contains("\"payload\":{\"bla\":\"blubb\"}")
                            .contains("\"vector\":[0.2,0.2,0.2,0.2]");
                } catch (Exception | AssertionError e) {
                    return true;
                }
                return false;
            });
            // clean up new vector
            spinWait(() -> {
                try {
                    var deleteVectorRequest = HttpRequest.newBuilder(
                                    URI.create(MILVUS_BASE_URL + "/v2/vectordb/entities/delete"))
                            .header("Authorization", "Bearer root:" + ADMIN_KEY)
                            .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.getObjectMapper()
                                    .writeValueAsString(Map.of(
                                            "collectionName",
                                            COLLECTION_NAME,
                                            "filter",
                                            "id == %s".formatted(LONG_ID_5)))))
                            .build();

                    var response = client.send(deleteVectorRequest, HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).isEqualTo(200);
                } catch (Exception | AssertionError e) {
                    return true;
                }
                return false;
            });
            spinWait(() -> {
                try {
                    var verifyCollectionRequest = HttpRequest.newBuilder(
                                    URI.create(MILVUS_BASE_URL + "/v2/vectordb/entities/get"))
                            .header("Authorization", "Bearer " + ADMIN_KEY)
                            .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.getObjectMapper()
                                    .writeValueAsString(
                                            Map.of("id", List.of(LONG_ID_5), "collectionName", COLLECTION_NAME))))
                            .build();
                    var vectorResponse = client.send(verifyCollectionRequest, HttpResponse.BodyHandlers.ofString());
                    assertThat(vectorResponse.body()).doesNotContain("\"payload\":"); // assert nothing found
                    assertThat(vectorResponse.statusCode()).isEqualTo(200);
                } catch (Exception | AssertionError e) {
                    return true;
                }
                return false;
            });
            client.close();
        } else {
            fail("no matching provider assert for %s".formatted(provider));
        }
    }

    @Override
    void assertThatVectorHasBeenUpserted(String provider) throws Exception {
        if ("weaviate".equals(provider)) {
            var result = WEAVIATE_CLIENT
                    .data()
                    .objectsGetter()
                    .withClassName(COLLECTION_NAME)
                    .withID(ID_6)
                    .withVector()
                    .run();
            assertThat(result.hasErrors()).isFalse();
            assertThat(result.getResult()).hasSize(1);
            assertThat(result.getResult()).first().satisfies(o -> {
                assertThat(o.getProperties()).containsEntry("bla", "wurstsalat");
                assertThat(o.getVector()).isEqualTo(new Float[] {0.01f, 0.01f, 0.01f, 0.01f});
            });
            assertThat(WEAVIATE_CLIENT
                            .data()
                            .deleter()
                            .withClassName(COLLECTION_NAME)
                            .withID(ID_6)
                            .run()
                            .hasErrors())
                    .isFalse();
        } else if ("qdrant".equals(provider)) {
            var client = HttpClient.newHttpClient();
            var getPointRequest = HttpRequest.newBuilder(
                            URI.create(QDRANT_BASE_URL + "/collections/" + COLLECTION_NAME + "/points/" + ID_6))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .GET()
                    .build();
            var getPointResponse = client.send(getPointRequest, HttpResponse.BodyHandlers.ofString());
            assertThat(getPointResponse.body())
                    .contains("\"payload\":{\"bla\":\"wurstsalat\"}")
                    .contains("\"vector\":[0.5,0.5,0.5,0.5]");
            // clean up new vector
            var deleteVectorRequest = HttpRequest.newBuilder(
                            URI.create(QDRANT_BASE_URL + "/collections/" + COLLECTION_NAME + "/points/delete"))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JsonUtils.getObjectMapper().writeValueAsString(Map.of("points", List.of(ID_6)))))
                    .build();

            var response = client.send(deleteVectorRequest, HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(200);
            client.close();
        } else if ("chromadb".equals(provider)) {
            var client =
                    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            var verifyCollectionRequest = HttpRequest.newBuilder(
                            URI.create(CHROMA_BASE_URL + "/api/v1/collections/" + getCollectionName(provider) + "/get"))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JsonUtils.getObjectMapper().writeValueAsString(Map.of("ids", List.of(ID_6)))))
                    .build();
            var vectorResponse = client.send(verifyCollectionRequest, HttpResponse.BodyHandlers.ofString());
            assertThat(vectorResponse.body()).contains("wurstsalat");
            assertThat(vectorResponse.statusCode()).isEqualTo(200);
            // clean up new vector
            var deleteVectorRequest = HttpRequest.newBuilder(URI.create(
                            CHROMA_BASE_URL + "/api/v1/collections/" + getCollectionName(provider) + "/delete"))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString("""
            {"ids":["%s","%s"]}""".formatted(ID_5, ID_6)))
                    .build();
            client.send(deleteVectorRequest, HttpResponse.BodyHandlers.discarding());
            client.close();
        } else if ("milvus".equals(provider)) {
            var client = HttpClient.newHttpClient();
            spinWait(() -> {
                try {
                    var verifyCollectionRequest = HttpRequest.newBuilder(
                                    URI.create(MILVUS_BASE_URL + "/v2/vectordb/entities/get"))
                            .header("Authorization", "Bearer " + ADMIN_KEY)
                            .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.getObjectMapper()
                                    .writeValueAsString(
                                            Map.of("id", List.of(LONG_ID_6), "collectionName", COLLECTION_NAME))))
                            .build();
                    var vectorResponse = client.send(verifyCollectionRequest, HttpResponse.BodyHandlers.ofString());
                    assertThat(vectorResponse.body()).contains("\"payload\":"); // assert nothing found
                    assertThat(vectorResponse.statusCode()).isEqualTo(200);
                } catch (Exception | AssertionError e) {
                    return true;
                }
                return false;
            });
            // clean up new vector
            var body = JsonUtils.getObjectMapper()
                    .writeValueAsString(
                            Map.of("collectionName", getCollectionName(provider), "filter", "id == 5 || id == 6"));
            var deleteVectorRequest = HttpRequest.newBuilder(
                            URI.create(MILVUS_BASE_URL + "/v2/vectordb/entities/delete"))
                    .header("Authorization", "Bearer " + ADMIN_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            client.send(deleteVectorRequest, HttpResponse.BodyHandlers.discarding());
            // wait for deletion to happen
            spinWait(() -> {
                try {
                    var verifyCollectionRequest = HttpRequest.newBuilder(
                                    URI.create(MILVUS_BASE_URL + "/v2/vectordb/entities/get"))
                            .header("Authorization", "Bearer " + ADMIN_KEY)
                            .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.getObjectMapper()
                                    .writeValueAsString(
                                            Map.of("id", List.of(LONG_ID_6), "collectionName", COLLECTION_NAME))))
                            .build();
                    var vectorResponse = client.send(verifyCollectionRequest, HttpResponse.BodyHandlers.ofString());
                    assertThat(vectorResponse.body()).doesNotContain("\"payload\":"); // assert nothing found
                    assertThat(vectorResponse.statusCode()).isEqualTo(200);
                } catch (Exception | AssertionError e) {
                    return true;
                }
                return false;
            });
            client.close();
        } else {
            fail("no matching provider assert for %s".formatted(provider));
        }
    }
}
