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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.genai.util.GenAIProcedureException;
import org.neo4j.graphdb.Entity;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.DbmsExtension;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.Inject;

/**
 * This test base essentially describes the contract of the Vector database integrations we provide. It is written as an
 * abstract base classes with all tests being final and some required hooks to set up additional test data. We introduced
 * this level of abstraction as not all database vendors we want to integrate with provide docker images to be used.
 * With this setup we can disable all tests that require specific keys in a very easy way.
 * The abstract base class here also provides the general setup of Neo4j.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DbmsExtension(configurationCallback = "configureNeo4j")
abstract class IntegrationTestBase {

    static final int DEFAULT_SLEEP = 500;

    protected static final List<String> FIELDS = List.of("city", "foo");

    protected static final String ID_1 = "8ef2b3a7-1e56-4ddd-b8c3-2ca8901ce308";
    protected static final String ID_2 = "9ef2b3a7-1e56-4ddd-b8c3-2ca8901ce308";
    protected static final String ID_3 = "7ef2b3a7-1e56-4ddd-b8c3-2ca8901ce308";
    protected static final String ID_4 = "7ef2b3a7-1e56-4ddd-b8c3-2ca8901ce309";
    protected static final String ID_5 = "ed0f4fb3-ac60-4b97-a847-dd4d13b2b217";
    protected static final String ID_6 = "24e3cbc4-5e68-4e4a-8e66-26052aa41bfb";

    protected static final Long LONG_ID_1 = 1L;
    protected static final Long LONG_ID_2 = 2L;
    protected static final Long LONG_ID_3 = 3L;
    protected static final Long LONG_ID_4 = 4L;
    protected static final Long LONG_ID_5 = 5L;
    protected static final Long LONG_ID_6 = 6L;

    /**
     * {@return the list of provides to be tested with the concrete implementation}
     */
    abstract Stream<Arguments> providers();

    /**
     * Different providers have different syntax to be used in their filters. This is the hook to specific those.
     * @param provider the provider to retrieve a filter for
     * @return the provider specific filter
     */
    abstract Object getProviderSpecificFilter(String provider);

    /**
     * Asserts that a collection has been created by the given provider
     * @param provider the provider used
     * @param suffix a suffix the test might have added to the collection name
     * @throws Exception anything that might happen
     */
    abstract void assertThatCollectionHasBeenCreated(String provider, String suffix) throws Exception;

    /**
     * Asserts that a collection has been successfully deleted
     * @param provider the provider used
     * @throws Exception anything that might happen
     */
    abstract void assertThatCollectionHasBeenDeleted(String provider) throws Exception;

    /**
     * Should create a new vector with means outside the stored procedure so that the deletion of vectors can be asserted
     * @param provider the provider used
     * @throws Exception anything that might happen
     */
    abstract void createVectorForDeletion(String provider) throws Exception;

    /**
     * Asserts that a vector has been successfully deleted
     * @param provider the provider used
     * @throws Exception anything that might happen
     */
    abstract void assertThatVectorHasBeenDeleted(String provider) throws Exception;

    /**
     * Asserts that a vector has been successfully created
     * @param provider the provider used
     * @throws Exception anything that might happen
     */
    abstract void assertThatVectorHasBeenCreated(String provider) throws Exception;

    /**
     * Asserts that a vector has been successfully created or updated ("upserted")
     * @param provider the provider used
     * @throws Exception anything that might happen
     */
    abstract void assertThatVectorHasBeenUpserted(String provider) throws Exception;

    abstract String getCollectionName(String provider, boolean realName);

    String getCollectionName(String provider) {
        return getCollectionName(provider, false);
    }

    abstract String getCollectionNameToBeDeleted(String provider);

    abstract String getCollectionNameToBeCreated(String provider);

    /**
     * {@return a read only auth for access the provider}
     */
    abstract Map<String, String> getReadOnlyAuth(String provider);

    /**
     * {@return a read only key for access the provider}
     */
    abstract String getReadOnlyKey(String provider);

    /**
     * {@return an admin auth for access the provider}
     */
    abstract Map<String, String> getAdminAuth(String provider);

    /**
     * {@return an admin key for access the provider}
     */
    abstract String getAdminKey(String provider);

    protected Object getIdValue(String id, String provider) {
        return id;
    }

    static void spinWait(Supplier<Boolean> stillWaiting) throws InterruptedException {
        if (!stillWaiting.get()) {
            return;
        }

        var maxRetry = 100;
        var cnt = 0;
        while (stillWaiting.get() && ++cnt < maxRetry) {
            Thread.sleep(DEFAULT_SLEEP);
        }
    }
    /**
     * Enriches the config map with additional specs per provider
     * @param provider
     * @param defaultConfig mutable map with default data
     * @return
     */
    Map<String, Object> enrichCreateCollection(String provider, Map<String, Object> defaultConfig) {
        return defaultConfig;
    }

    @Inject
    protected GraphDatabaseAPI database;

    @ExtensionCallback
    final void configureNeo4j(TestDatabaseManagementServiceBuilder builder) {
        builder.addExtension(new VectorDatabasesExtension());
    }

    @AfterEach
    void clearNeo4j() {
        database.executeTransactionally("MATCH (n) DETACH DELETE n");
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void shouldIncludeProviders(String provider) {

        var minExpectedProviders =
                (int) providers().map(a -> a.get()[0]).distinct().count();

        try (var tx = database.beginTx()) {
            assertThat(tx.execute("CALL genai.vector.external.listProviders()").stream()
                            .toList())
                    .hasSizeGreaterThanOrEqualTo(minExpectedProviders)
                    .isSortedAccordingTo(Comparator.comparing(row -> (String) row.get("name")))
                    .map(row -> row.get("name"))
                    .contains(provider);
        }
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void getInfo(String provider, String baseUrl, boolean useToken) {
        Map<String, Object> conf = new HashMap<>(Map.of(RequestConfig.Keys.ALL_RESULTS.key(), true));
        configureReadOnlyAuth(provider, useToken, conf);
        Helper.testResult(
                database,
                "CALL genai.vector.external.info($host, $collectionName, $conf)",
                Map.of("host", baseUrl, "collectionName", getCollectionName(provider, true), "conf", conf),
                r -> {
                    Map<String, Object> row = r.next();
                    assertThat(row).containsEntry("status", "ok");
                    assertThat(row.get("vendor"))
                            .isInstanceOf(Map.class)
                            .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                            .isNotEmpty();
                });
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void getInfoNotExistentCollection(String provider, String baseUrl, boolean useToken) {
        Map<String, Object> conf = new HashMap<>(Map.of(RequestConfig.Keys.ALL_RESULTS.key(), true));
        configureReadOnlyAuth(provider, useToken, conf);
        Helper.assertFails(
                database,
                "CALL genai.vector.external.info($host, 'wrong_collection', $conf)",
                Map.of("host", baseUrl, "collectionName", getCollectionName(provider), "conf", conf),
                org.neo4j.genai.dbs.CollectionNotFoundException.class,
                "Collection 'wrong_collection' not found");
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void getVectorsWithReadOnlyApiKey(String provider, String baseUrl, boolean useToken) {
        Map<String, Object> conf = new HashMap<>(Map.of(RequestConfig.Keys.ALL_RESULTS.key(), true));
        configureReadOnlyAuth(provider, useToken, conf);
        Helper.testResult(
                database,
                "CALL genai.vector.external.get($host, $collectionName, [$id1], $conf)",
                Map.of(
                        "host",
                        baseUrl,
                        "id1",
                        getIdValue(ID_1, provider),
                        "collectionName",
                        getCollectionName(provider),
                        "conf",
                        conf),
                r -> {
                    Map<String, Object> row = r.next();
                    Helper.assertBerlinResult(row, getIdValue(ID_1, provider), Helper.EntityType.UNDEFINED);
                    assertThat(row)
                            .hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());
                    assertThat(r.hasNext()).isFalse();
                });

        Helper.testResult(
                database,
                "CALL genai.vector.external.get($host, $collectionName, $ids, $conf)",
                Map.of(
                        "host",
                        baseUrl,
                        "collectionName",
                        getCollectionName(provider),
                        "ids",
                        List.of(getIdValue(ID_1, provider), getIdValue(ID_2, provider)),
                        "conf",
                        conf),
                r -> {
                    Map<String, Object> row1 = r.next();
                    assertThat(r.hasNext()).isTrue();
                    Map<String, Object> row2 = r.next();
                    assertThat(r.hasNext()).isFalse();

                    assertThat(List.of(row1, row2))
                            .satisfiesExactlyInAnyOrder(
                                    row -> Helper.assertBerlinResult(
                                            row, getIdValue(ID_1, provider), Helper.EntityType.UNDEFINED),
                                    row -> Helper.assertLondonResult(
                                            row, getIdValue(ID_2, provider), Helper.EntityType.UNDEFINED));
                });
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void getVectorsWithoutVectorResult(String provider, String baseUrl, boolean useToken) {
        Map<String, Object> conf = new HashMap<>();
        configureAdminAuthAndWait(provider, useToken, conf);
        Helper.testResult(
                database,
                "CALL genai.vector.external.get($host, $collectionName, [$id1], $conf)",
                Map.of(
                        "host",
                        baseUrl,
                        "collectionName",
                        getCollectionName(provider),
                        "id1",
                        getIdValue(ID_1, provider),
                        "conf",
                        conf),
                r -> {
                    Map<String, Object> row = r.next();
                    assertThat(row.get("metadata"))
                            .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                            .containsExactlyInAnyOrderEntriesOf(Map.of("city", "Berlin", "foo", "one"));
                    assertThat(row).containsEntry("score", null);
                    assertThat(row).containsEntry("vector", null);
                    assertThat(row).containsEntry("id", null);
                });
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void queryVectors(String provider, String baseUrl, boolean useToken) {
        Map<String, Object> conf = new HashMap<>(
                Map.of(RequestConfig.Keys.ALL_RESULTS.key(), true, RowMappingConfig.Keys.FIELDS.key(), FIELDS));
        configureAdminAuthAndWait(provider, useToken, conf);
        Helper.testResult(
                database,
                "CALL genai.vector.external.query($host, $collectionName, [0.2, 0.1, 0.9, 0.7], null, 5, $conf) "
                        + " YIELD score, vector, id, metadata RETURN * ORDER BY id",
                Map.of("host", baseUrl, "collectionName", getCollectionName(provider), "conf", conf),
                r -> {
                    Map<String, Object> row = r.next();
                    Helper.assertBerlinResult(row, getIdValue(ID_1, provider), Helper.EntityType.UNDEFINED);
                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row)
                            .hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());

                    row = r.next();
                    Helper.assertLondonResult(row, getIdValue(ID_2, provider), Helper.EntityType.UNDEFINED);
                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row)
                            .hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());
                    assertThat(r.hasNext()).isFalse();
                });
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void queryVectorsWithFilter(String provider, String baseUrl, boolean useToken) {
        Map<String, Object> conf = new HashMap<>(
                Map.of(RequestConfig.Keys.ALL_RESULTS.key(), true, RowMappingConfig.Keys.FIELDS.key(), FIELDS));
        configureAdminAuthAndWait(provider, useToken, conf);
        Helper.testResult(
                database,
                """
				CALL genai.vector.external.query($host, $collectionName, [0.2, 0.1, 0.9, 0.7],
				$filter,
				5, $conf) YIELD metadata, id RETURN * ORDER BY id""",
                Map.of(
                        "host",
                        baseUrl,
                        "collectionName",
                        getCollectionName(provider),
                        "filter",
                        getProviderSpecificFilter(provider),
                        "conf",
                        conf),
                r -> {
                    Helper.assertLondonResult(r.next(), getIdValue(ID_2, provider), Helper.EntityType.UNDEFINED);
                });
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void queryVectorsWithoutVectorResult(String provider, String baseUrl, boolean useToken) {
        Map<String, Object> conf = new HashMap<>(Map.of(RowMappingConfig.Keys.FIELDS.key(), FIELDS));
        configureAdminAuthAndWait(provider, useToken, conf);
        Helper.testResult(
                database,
                "CALL genai.vector.external.query($host, $collectionName, [0.2, 0.1, 0.9, 0.7], null, 5, $conf) "
                        + " YIELD score, vector, id, metadata, node RETURN * ORDER BY id",
                Map.of("host", baseUrl, "collectionName", getCollectionName(provider), "conf", conf),
                r -> {
                    Map<String, Object> row = r.next();
                    assertThat(row.get("metadata"))
                            .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                            .containsExactlyInAnyOrderEntriesOf(Map.of("city", "Berlin", "foo", "one"));

                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row).containsEntry("vector", null);
                    assertThat(row).containsEntry("id", null);

                    row = r.next();
                    assertThat(row.get("metadata"))
                            .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                            .containsExactlyInAnyOrderEntriesOf(Map.of("city", "London", "foo", "two"));
                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row).containsEntry("vector", null);
                    assertThat(row).containsEntry("id", null);
                });
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void queryVectorsWithYield(String provider, String baseUrl, boolean useToken) {
        Map<String, Object> conf = new HashMap<>(
                Map.of(RequestConfig.Keys.ALL_RESULTS.key(), true, RowMappingConfig.Keys.FIELDS.key(), FIELDS));
        configureAdminAuthAndWait(provider, useToken, conf);
        Helper.testResult(
                database,
                "CALL genai.vector.external.query($host, $collectionName, [0.2, 0.1, 0.9, 0.7], null, 5, $conf) "
                        + "YIELD metadata, id RETURN * ORDER BY id",
                Map.of("host", baseUrl, "collectionName", getCollectionName(provider), "conf", conf),
                r -> {
                    Helper.assertBerlinResult(r.next(), getIdValue(ID_1, provider), Helper.EntityType.UNDEFINED);
                    Helper.assertLondonResult(r.next(), getIdValue(ID_2, provider), Helper.EntityType.UNDEFINED);
                });
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void queryVectorsWithLimit(String provider, String baseUrl, boolean useToken) {
        Map<String, Object> conf = new HashMap<>(
                Map.of(RequestConfig.Keys.ALL_RESULTS.key(), true, RowMappingConfig.Keys.FIELDS.key(), FIELDS));
        configureAdminAuthAndWait(provider, useToken, conf);
        Helper.testResult(
                database,
                """
				CALL genai.vector.external.query($host, $collectionName, [0.2, 0.1, 0.9, 0.7], null, 1, $conf) YIELD metadata, id RETURN * ORDER BY id""",
                Map.of("host", baseUrl, "collectionName", getCollectionName(provider), "conf", conf),
                r -> Helper.assertBerlinResult(r.next(), getIdValue(ID_1, provider), Helper.EntityType.UNDEFINED));
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void queryVectorsWithCreateNode(String provider, String baseUrl, boolean useToken) {
        Map<String, Object> conf = new HashMap<>(Map.of(
                RequestConfig.Keys.ALL_RESULTS.key(),
                true,
                RowMappingConfig.Keys.FIELDS.key(),
                FIELDS,
                RowMappingConfig.Keys.MAPPING.key(),
                Map.of(
                        EntityMappingConfig.Keys.EMBEDDING.key(),
                        "vect",
                        EntityMappingConfig.Keys.NODE_LABEL.key(),
                        "Test",
                        EntityMappingConfig.Keys.ENTITY.key(),
                        "myId",
                        EntityMappingConfig.Keys.METADATA.key(),
                        "foo",
                        EntityMappingConfig.Keys.MODE.key(),
                        EntityMappingConfig.MappingMode.CREATE_IF_MISSING.toString())));
        configureAdminAuthAndWait(provider, useToken, conf);

        Helper.testResult(
                database,
                "CALL genai.vector.external.queryAndUpdate($host, $collectionName, [0.2, 0.1, 0.9, 0.7], null, 5, $conf) "
                        + "YIELD score, vector, id, metadata, node RETURN * ORDER BY id",
                Map.of("host", baseUrl, "conf", conf, "collectionName", getCollectionName(provider)),
                r -> {
                    Map<String, Object> row = r.next();
                    Helper.assertBerlinResult(row, getIdValue(ID_1, provider), Helper.EntityType.NODE);
                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row)
                            .hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());

                    row = r.next();
                    Helper.assertLondonResult(row, getIdValue(ID_2, provider), Helper.EntityType.NODE);
                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row)
                            .hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());
                });

        Helper.assertNodesCreated(database);

        Helper.testResult(
                database,
                "MATCH (n:Test) RETURN properties(n) AS props ORDER BY n.myId",
                Helper::vectorEntityAssertions);

        Helper.testResult(
                database,
                "CALL genai.vector.external.queryAndUpdate($host, $collectionName, [0.2, 0.1, 0.9, 0.7], null, 5, $conf) "
                        + " YIELD score, vector, id, metadata, node RETURN * ORDER BY id",
                Map.of("host", baseUrl, "conf", conf, "collectionName", getCollectionName(provider)),
                r -> {
                    Map<String, Object> row = r.next();
                    Helper.assertBerlinResult(row, getIdValue(ID_1, provider), Helper.EntityType.NODE);
                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row)
                            .hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());

                    row = r.next();
                    Helper.assertLondonResult(row, getIdValue(ID_2, provider), Helper.EntityType.NODE);
                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row)
                            .hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());
                });

        Helper.assertNodesCreated(database);
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void queryVectorsWithCreateNodeUsingExistingNode(String provider, String baseUrl, boolean useToken) {

        database.executeTransactionally("CREATE (:Test {myId: 'one'}), (:Test {myId: 'two'})");

        Map<String, Object> conf = new HashMap<>(Map.of(
                RequestConfig.Keys.ALL_RESULTS.key(),
                true,
                RowMappingConfig.Keys.FIELDS.key(),
                FIELDS,
                RowMappingConfig.Keys.MAPPING.key(),
                Map.of(
                        EntityMappingConfig.Keys.EMBEDDING.key(), "vect",
                        EntityMappingConfig.Keys.NODE_LABEL.key(), "Test",
                        EntityMappingConfig.Keys.ENTITY.key(), "myId",
                        EntityMappingConfig.Keys.METADATA.key(), "foo")));
        configureAdminAuthAndWait(provider, useToken, conf);
        Helper.testResult(
                database,
                "CALL genai.vector.external.queryAndUpdate($host, $collectionName, [0.2, 0.1, 0.9, 0.7], null, 5, $conf) "
                        + " YIELD score, vector, id, metadata, node RETURN * ORDER BY id",
                Map.of("host", baseUrl, "conf", conf, "collectionName", getCollectionName(provider)),
                r -> {
                    Map<String, Object> row = r.next();
                    Helper.assertBerlinResult(row, getIdValue(ID_1, provider), Helper.EntityType.NODE);
                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row)
                            .hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());

                    row = r.next();
                    Helper.assertLondonResult(row, getIdValue(ID_2, provider), Helper.EntityType.NODE);
                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row)
                            .hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());
                });

        Helper.assertNodesCreated(database);
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void queryVectorsWithCreateRel(String provider, String baseUrl, boolean useToken) {
        database.executeTransactionally(
                "CREATE (:Start)-[:TEST {myId: 'one'}]->(:End), (:Start)-[:TEST {myId: 'two'}]->(:End)");

        Map<String, Object> conf = new HashMap<>(Map.of(
                RequestConfig.Keys.ALL_RESULTS.key(),
                true,
                RowMappingConfig.Keys.FIELDS.key(),
                FIELDS,
                RowMappingConfig.Keys.MAPPING.key(),
                Map.of(
                        EntityMappingConfig.Keys.EMBEDDING.key(), "vect",
                        EntityMappingConfig.Keys.REL_TYPE.key(), "TEST",
                        EntityMappingConfig.Keys.ENTITY.key(), "myId",
                        EntityMappingConfig.Keys.METADATA.key(), "foo")));

        configureAdminAuthAndWait(provider, useToken, conf);
        Helper.testResult(
                database,
                "CALL genai.vector.external.queryAndUpdate($host, $collectionName, [0.2, 0.1, 0.9, 0.7], null, 5, $conf) "
                        + " YIELD score, vector, id, metadata, rel RETURN * ORDER BY id",
                Map.of("host", baseUrl, "conf", conf, "collectionName", getCollectionName(provider)),
                r -> {
                    Map<String, Object> row = r.next();
                    Helper.assertBerlinResult(row, getIdValue(ID_1, provider), Helper.EntityType.REL);
                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row)
                            .hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());

                    row = r.next();
                    Helper.assertLondonResult(row, getIdValue(ID_2, provider), Helper.EntityType.REL);
                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row)
                            .hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());
                });

        Helper.assertRelsCreated(database);
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void queryReadOnlyVectorsWithMapping(String provider, String baseUrl, boolean useToken) {
        database.executeTransactionally(
                "CREATE (:Start)-[:TEST {readID: 'one'}]->(:End), (:Start)-[:TEST {readID: 'two'}]->(:End)");

        Map<String, Object> conf = new HashMap<>(Map.of(
                RequestConfig.Keys.ALL_RESULTS.key(),
                true,
                RowMappingConfig.Keys.FIELDS.key(),
                FIELDS,
                RowMappingConfig.Keys.MAPPING.key(),
                Map.of(
                        EntityMappingConfig.Keys.REL_TYPE.key(), "TEST",
                        EntityMappingConfig.Keys.ENTITY.key(), "readID",
                        EntityMappingConfig.Keys.METADATA.key(), "foo")));

        configureReadOnlyAuth(provider, useToken, conf);

        Helper.testResult(
                database,
                "CALL genai.vector.external.query($host, $collectionName, [0.2, 0.1, 0.9, 0.7], null, 5, $conf) "
                        + " YIELD score, vector, id, metadata, rel RETURN * ORDER BY id",
                Map.of("host", baseUrl, "conf", conf, "collectionName", getCollectionName(provider)),
                r -> Helper.assertReadOnlyProcWithMappingResults(r, "rel", true));
    }

    private void configureReadOnlyAuth(String provider, boolean useToken, Map<String, Object> conf) {
        if (useToken) {
            conf.put(RequestConfig.Keys.TOKEN.key(), getReadOnlyKey(provider));
        } else {
            conf.put(RequestConfig.Keys.HEADERS.key(), getReadOnlyAuth(provider));
        }
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void queryVectorsWithCreateRelWithoutVectorResult(String provider, String baseUrl, boolean useToken) {

        database.executeTransactionally(
                "CREATE (:Start)-[:TEST {myId: 'one'}]->(:End), (:Start)-[:TEST {myId: 'two'}]->(:End)");

        Map<String, Object> conf = new HashMap<>(Map.of(
                RowMappingConfig.Keys.FIELDS.key(),
                FIELDS,
                RowMappingConfig.Keys.MAPPING.key(),
                Map.of(
                        EntityMappingConfig.Keys.REL_TYPE.key(), "TEST",
                        EntityMappingConfig.Keys.ENTITY.key(), "myId",
                        EntityMappingConfig.Keys.METADATA.key(), "foo")));

        configureAdminAuthAndWait(provider, useToken, conf);

        Helper.testResult(
                database,
                "CALL genai.vector.external.queryAndUpdate($host, $collectionName, [0.2, 0.1, 0.9, 0.7], null, 5, $conf) "
                        + " YIELD score, vector, id, metadata, rel RETURN * ORDER BY id",
                Map.of("host", baseUrl, "conf", conf, "collectionName", getCollectionName(provider)),
                r -> {
                    Map<String, Object> row = r.next();
                    Map<String, Object> props = ((Entity) row.get("rel")).getAllProperties();
                    assertThat(props).containsEntry("city", "Berlin");
                    assertThat(props).containsEntry("myId", "one");
                    assertThat(props).doesNotContainKey("vector");

                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row).containsEntry("vector", null);

                    row = r.next();
                    props = ((Entity) row.get("rel")).getAllProperties();
                    assertThat(props).containsEntry("city", "London");
                    assertThat(props).containsEntry("myId", "two");
                    assertThat(props).doesNotContainKey("vector");

                    assertThat(row)
                            .hasEntrySatisfying("score", v -> assertThat(v).isNotNull());
                    assertThat(row).containsEntry("vector", null);
                });
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void getReadOnlyVectorsWithMapping(String provider, String baseUrl, boolean useToken) {
        database.executeTransactionally("CREATE (:Test {readID: 'one'}), (:Test {readID: 'two'})");

        Map<String, Object> conf = new HashMap<>(Map.of(
                RequestConfig.Keys.ALL_RESULTS.key(),
                true,
                RowMappingConfig.Keys.MAPPING.key(),
                Map.of(
                        EntityMappingConfig.Keys.NODE_LABEL.key(), "Test",
                        EntityMappingConfig.Keys.ENTITY.key(), "readID",
                        EntityMappingConfig.Keys.METADATA.key(), "foo")));

        configureAdminAuthAndWait(provider, useToken, conf);

        Helper.testResult(
                database,
                "CALL genai.vector.external.get($host, $collectionName, [$id1, $id2], $conf) "
                        + "YIELD vector, id, metadata, node RETURN * ORDER BY id",
                Map.of(
                        "host",
                        baseUrl,
                        "collectionName",
                        getCollectionName(provider),
                        "id1",
                        getIdValue(ID_1, provider),
                        "id2",
                        getIdValue(ID_2, provider),
                        "conf",
                        conf),
                r -> Helper.assertReadOnlyProcWithMappingResults(r, "node", false));
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void getVectorsWithCreateNodeUsingExistingNode(String provider, String baseUrl, boolean useToken) {

        database.executeTransactionally("CREATE (:Test {myId: 'one'}), (:Test {myId: 'two'})");

        Map<String, Object> conf = new HashMap<>(Map.of(
                RequestConfig.Keys.ALL_RESULTS.key(),
                true,
                RowMappingConfig.Keys.MAPPING.key(),
                Map.of(
                        EntityMappingConfig.Keys.EMBEDDING.key(),
                        "vect",
                        EntityMappingConfig.Keys.NODE_LABEL.key(),
                        "Test",
                        EntityMappingConfig.Keys.ENTITY.key(),
                        "myId",
                        EntityMappingConfig.Keys.METADATA.key(),
                        "foo")));

        configureAdminAuthAndWait(provider, useToken, conf);

        Helper.testResult(
                database,
                "CALL genai.vector.external.getAndUpdate($host, $collectionName, [$id1, $id2], $conf)",
                Map.of(
                        "host",
                        baseUrl,
                        "collectionName",
                        getCollectionName(provider),
                        "id1",
                        getIdValue(ID_1, provider),
                        "id2",
                        getIdValue(ID_2, provider),
                        "conf",
                        conf),
                r -> {
                    var row1 = r.next();
                    assertThat(r.hasNext()).isTrue();
                    var row2 = r.next();
                    assertThat(r.hasNext()).isFalse();
                    assertThat(Set.of(row1, row2))
                            .satisfiesExactlyInAnyOrder(
                                    row -> {
                                        Helper.assertBerlinResult(
                                                row, getIdValue(ID_1, provider), Helper.EntityType.NODE);
                                        assertThat(row).hasEntrySatisfying("vector", v -> assertThat(v)
                                                .isNotNull());
                                    },
                                    row -> {
                                        Helper.assertLondonResult(
                                                row, getIdValue(ID_2, provider), Helper.EntityType.NODE);
                                        assertThat(row).hasEntrySatisfying("vector", v -> assertThat(v)
                                                .isNotNull());
                                    });
                });

        Helper.assertNodesCreated(database);
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void createCollection(String provider, String baseUrl, boolean useToken) throws Exception {
        Map<String, Object> conf = new HashMap<>();
        configureAdminAuthAndWait(provider, useToken, conf);

        Helper.testResult(
                database,
                "CALL genai.vector.external.createCollection($host, $collectionName, 'Cosine', 4, $conf)",
                Map.of(
                        "host",
                        baseUrl,
                        "collectionName",
                        getCollectionNameToBeCreated(provider) + useToken,
                        "conf",
                        enrichCreateCollection(provider, conf)),
                r -> {
                    Map<String, Object> row = r.next();
                    assertThat(row).containsEntry("status", "ok");
                });

        assertThatCollectionHasBeenCreated(provider, useToken + "");
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void deleteCollection(String provider, String baseUrl, boolean useToken) throws Exception {

        Map<String, Object> conf = new HashMap<>();
        configureAdminAuthAndWait(provider, useToken, conf);

        // delete collection aka "when"
        Helper.testResult(
                database,
                "CALL genai.vector.external.deleteCollection($host, $collectionName, $conf)",
                Map.of(
                        "host",
                        baseUrl,
                        "collectionName",
                        getCollectionNameToBeDeleted(provider) + useToken,
                        "conf",
                        conf),
                r -> {
                    Map<String, Object> row = r.next();
                    var status = row.get("status");
                    assertThat(status).isEqualTo("ok");
                    assertThat(row).containsEntry("vendor", Map.of());
                });

        assertThatCollectionHasBeenDeleted(provider);
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void queryWithAllResults(String provider, String baseUrl, boolean useToken) {
        Map<String, Object> conf = new HashMap<>(
                Map.of(RequestConfig.Keys.ALL_RESULTS.key(), true, RowMappingConfig.Keys.FIELDS.key(), FIELDS));
        configureAdminAuthAndWait(provider, useToken, conf);

        Helper.testResult(
                database,
                "CALL genai.vector.external.query($host, $collectionName, $vectors, null, 10, $conf)",
                Map.of(
                        "host",
                        baseUrl,
                        "collectionName",
                        getCollectionName(provider),
                        "vectors",
                        new double[] {0.2, 0.1, 0.9, 0.7},
                        "conf",
                        conf),
                r -> {
                    Map<String, Object> row = r.next();
                    assertThat(row).containsEntry("metadata", Map.of("city", "Berlin", "foo", "one"));
                    assertThat(row.get("score")).isNotNull();
                    assertThat(row)
                            .hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());
                    assertThat(row).containsEntry("id", getIdValue(ID_1, provider));

                    row = r.next();
                    assertThat(row).containsEntry("metadata", Map.of("city", "London", "foo", "two"));
                    assertThat(row.get("score")).isNotNull();
                    assertThat(row)
                            .hasEntrySatisfying("vector", v -> assertThat(v).isNotNull());
                    assertThat(row).containsEntry("id", getIdValue(ID_2, provider));
                });
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void deleteVector(String provider, String baseUrl, boolean useToken) throws Exception {

        Map<String, Object> conf = new HashMap<>();
        configureAdminAuthAndWait(provider, useToken, conf);

        createVectorForDeletion(provider);
        Helper.testResult(
                database,
                """
		CALL genai.vector.external.delete(
			$host, $collectionName, [$id1, $id2], $conf
		)""",
                Map.of(
                        "host",
                        baseUrl,
                        "conf",
                        conf,
                        "collectionName",
                        getCollectionName(provider),
                        "id1",
                        getIdValue(ID_3, provider),
                        "id2",
                        getIdValue(ID_4, provider)),
                r -> {
                    Map<String, Object> row = r.next();
                    var status = row.get("status");
                    assertThat(status).isEqualTo("ok");
                    assertThat(row.get("vendor")).isNotNull();
                });
        assertThatVectorHasBeenDeleted(provider);
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void writeOperationWithReadOnlyUser(String provider, String baseUrl, boolean useToken) {
        Map<String, Object> conf = new HashMap<>();
        configureReadOnlyAuth(provider, useToken, conf);
        // milvus will just return empty data which is the same behaviour
        // as a request to an unknown collection
        Assumptions.assumeFalse(
                "milvus".equals(provider),
                () -> "milvus won't tell you that you are not allowed to delete a collection.");
        Helper.assertFails(
                database,
                "CALL genai.vector.external.deleteCollection($host, $collectionName, $conf)",
                Map.of("host", baseUrl, "collectionName", "somecollection", "conf", conf),
                GenAIProcedureException.class,
                "HTTP response code: 403");
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void createNewVectors(String provider, String baseUrl, boolean useToken) throws Exception {

        Map<String, Object> conf = new HashMap<>();
        configureAdminAuthAndWait(provider, useToken, conf);

        Helper.testResult(
                database,
                "CALL genai.vector.external.upsert($host, $collectionName, $vectors, $conf)",
                Map.of(
                        "host",
                        baseUrl,
                        "collectionName",
                        getCollectionName(provider),
                        "vectors",
                        List.of(Map.of(
                                "id",
                                getIdValue(ID_5, provider),
                                "metadata",
                                Map.of("bla", "blubb"),
                                "vector",
                                List.of(0.2, 0.2, 0.2, 0.2))),
                        "conf",
                        conf),
                r -> {
                    Map<String, Object> row = r.next();
                    assertThat(row).containsEntry("status", "ok");
                });

        assertThatVectorHasBeenCreated(provider);
    }

    @ParameterizedTest
    @MethodSource("providers")
    final void upsertExistingVectors(String provider, String baseUrl, boolean useToken) throws Exception {
        Map<String, Object> conf = new HashMap<>();
        configureAdminAuthAndWait(provider, useToken, conf);

        // initial entry
        Helper.testResult(
                database,
                "CALL genai.vector.external.upsert($host, $collectionName, $vectors, $conf)",
                Map.of(
                        "host",
                        baseUrl,
                        "collectionName",
                        getCollectionName(provider),
                        "vectors",
                        List.of(Map.of(
                                "id",
                                getIdValue(ID_6, provider),
                                "metadata",
                                Map.of("bla", "blubb"),
                                "vector",
                                List.of(0.09, 0.71, 0.71, 0.91))),
                        "conf",
                        conf),
                r -> {
                    Map<String, Object> row = r.next();
                    assertThat(row).containsEntry("status", "ok");
                });

        // update entry
        Helper.testResult(
                database,
                "CALL genai.vector.external.upsert($host, $collectionName, $vectors, $conf)",
                Map.of(
                        "host",
                        baseUrl,
                        "collectionName",
                        getCollectionName(provider),
                        "vectors",
                        List.of(Map.of(
                                "id",
                                getIdValue(ID_6, provider),
                                "metadata",
                                Map.of("bla", "wurstsalat"),
                                "vector",
                                List.of(0.01, 0.01, 0.01, 0.01))),
                        "conf",
                        conf),
                r -> {
                    Map<String, Object> row = r.next();
                    assertThat(row).containsEntry("status", "ok");
                });

        assertThatVectorHasBeenUpserted(provider);
    }

    private void configureAdminAuthAndWait(String provider, boolean useToken, Map<String, Object> conf) {
        if (useToken) {
            conf.put(RequestConfig.Keys.TOKEN.key(), getAdminKey(provider));
        } else {
            conf.put(RequestConfig.Keys.HEADERS.key(), getAdminAuth(provider));
        }
        // add wait parameter for qdrant
        conf.put("wait", true);
    }
}
