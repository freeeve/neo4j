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

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.clients.Pinecone;
import io.pinecone.exceptions.PineconeNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.provider.Arguments;
import org.openapitools.db_control.client.model.IndexModel;
import org.openapitools.db_control.client.model.IndexModelStatus;

/**
 * This test is by a good chance flaky as hell due to the eventual consistent API and the weird blocking but still
 * asynchronous client provided by Pinecone. I have seen him fully green though several times, with existing indexes
 * and on a fresh tennant.
 */
@EnabledIf("pineconeHostAndKeyAvailable")
final class PineconeIT extends IntegrationTestBase {

    // What we call collections is an index at pinecone
    private static final String INDEX_NAME = "neo4j-genai-pinecone-it";
    private static final String PINECONE_API_KEY = Optional.ofNullable(System.getenv("PINECONE_API_KEY"))
            .filter(Predicate.not(String::isBlank))
            .map(String::trim)
            .orElse(null);
    private final Pinecone pc = new Pinecone.Builder(PINECONE_API_KEY).build();
    private static final String PINECONE_HOST = Optional.ofNullable(System.getenv("PINECONE_HOST"))
            .filter(Predicate.not(String::isBlank))
            .map(String::trim)
            .orElse("https://api.pinecone.io");

    static boolean pineconeHostAndKeyAvailable() {
        return PINECONE_API_KEY != null && PINECONE_HOST != null;
    }

    @Override
    Stream<Arguments> providers() {
        return Stream.of(
                Arguments.argumentSet("pinecone no token", "pinecone", PINECONE_HOST, false),
                Arguments.argumentSet("pinecone use token", PINECONE_HOST, true));
    }

    @Override
    Object getProviderSpecificFilter(String provider) {
        return Map.of("city", Map.of("$eq", "London"));
    }

    @Override
    void assertThatCollectionHasBeenCreated(String provider, String suffix) throws Exception {

        var indexName = getCollectionNameToBeCreated(provider) + suffix;
        Runnable doAssert = () -> {
            try {
                IndexModel im = pc.describeIndex(indexName);
                assertThat(im.getStatus().getState()).isEqualTo(IndexModelStatus.SERIALIZED_NAME_READY);
            } catch (PineconeNotFoundException ex) {
                Assertions.fail();
            }
        };

        spinWait(() -> {
            try {
                doAssert.run();
            } catch (AssertionError e) {
                return true;
            }
            return false;
        });
        doAssert.run();

        pc.deleteIndex(indexName);
        spinWait(() -> {
            try {
                pc.describeIndex(indexName);
                return true;
            } catch (PineconeNotFoundException ex) {
                return false;
            }
        });
    }

    @Override
    void assertThatCollectionHasBeenDeleted(String provider) throws Exception {

        Runnable doAssert = () -> {
            var indexName = getCollectionNameToBeCreated(provider);
            assertThatExceptionOfType(PineconeNotFoundException.class).isThrownBy(() -> pc.describeIndex(indexName));
        };
        spinWait(() -> {
            try {
                doAssert.run();
            } catch (AssertionError e) {
                return true;
            }
            return false;
        });
        doAssert.run();
    }

    @Override
    void createVectorForDeletion(String provider) throws Exception {

        var ic = pc.getIndexConnection(INDEX_NAME);
        ic.upsert(
                ID_3,
                List.of(0.19f, 0.81f, 0.75f, 0.11f),
                null,
                null,
                Struct.newBuilder()
                        .putAllFields(Map.of(
                                "foo", Value.newBuilder().setStringValue("baz").build()))
                        .build(),
                null);
        ic.upsert(
                ID_4,
                List.of(0.19f, 0.81f, 0.75f, 0.11f),
                null,
                null,
                Struct.newBuilder()
                        .putAllFields(Map.of(
                                "foo", Value.newBuilder().setStringValue("baz").build()))
                        .build(),
                null);

        Thread.sleep(DEFAULT_SLEEP);
    }

    @Override
    void assertThatVectorHasBeenDeleted(String provider) throws Exception {

        Runnable doAssert = () -> {
            var ic = pc.getIndexConnection(INDEX_NAME);
            for (String id : new String[] {ID_3, ID_4}) {
                var result = ic.queryByVectorId(10, id);
                assertThat(result.getMatchesList()).noneMatch(v -> v.getId().equals(id));
            }
        };

        spinWait(() -> {
            try {
                doAssert.run();
            } catch (AssertionError e) {
                return true;
            }
            return false;
        });
        doAssert.run();
    }

    @Override
    void assertThatVectorHasBeenCreated(String provider) throws Exception {
        var ic = pc.getIndexConnection(INDEX_NAME);
        assertThat(ic.queryByVectorId(1, ID_5).getMatchesList()).hasSize(1);

        ic.deleteByIds(List.of(ID_5));
        spinWait(() -> ic.queryByVectorId(1, ID_5).getMatchesList().stream()
                .anyMatch(v -> v.getId().equals(ID_5)));
        assertThat(ic.queryByVectorId(1, ID_5).getMatchesList())
                .noneMatch(v -> v.getId().equals(ID_5));
    }

    @Override
    void assertThatVectorHasBeenUpserted(String provider) throws Exception {

        var ic = pc.getIndexConnection(INDEX_NAME);
        Runnable doAssert = () -> {
            var matchesList = ic.queryByVectorId(2, ID_6, true, true).getMatchesList();
            assertThat(matchesList).isNotEmpty();
            assertThat(matchesList)
                    .filteredOn(v -> v.getId().equals(ID_6))
                    .hasSize(1)
                    .first()
                    .satisfies(o -> {
                        assertThat(o.getMetadata().getFieldsMap())
                                .containsEntry(
                                        "bla",
                                        Value.newBuilder()
                                                .setStringValue("wurstsalat")
                                                .build());
                        assertThat(o.getValuesList()).containsExactly(0.01f, 0.01f, 0.01f, 0.01f);
                    });
        };

        spinWait(() -> {
            try {
                doAssert.run();
            } catch (AssertionError e) {
                return true;
            }
            return false;
        });
        doAssert.run();

        ic.deleteByIds(List.of(ID_6));
        spinWait(() -> !ic.queryByVectorId(1, ID_6).getMatchesList().isEmpty());
        assertThat(ic.queryByVectorId(1, ID_6).getMatchesList()).isEmpty();
    }

    @Override
    String getCollectionName(String provider, boolean realName) {
        return INDEX_NAME;
    }

    @Override
    String getCollectionNameToBeDeleted(String provider) {
        return INDEX_NAME + "-delete-me";
    }

    @Override
    String getCollectionNameToBeCreated(String provider) {
        return INDEX_NAME + "-new";
    }

    @Override
    Map<String, String> getReadOnlyAuth(String provider) {
        return Map.of("Authorization", getReadOnlyKey(provider));
    }

    @Override
    String getReadOnlyKey(String provider) {
        return PINECONE_API_KEY;
    }

    @Override
    Map<String, String> getAdminAuth(String provider) {
        return Map.of("Authorization", getAdminKey(provider));
    }

    @Override
    String getAdminKey(String provider) {
        return PINECONE_API_KEY;
    }

    @BeforeAll
    void setup() throws InterruptedException {
        for (String indexName : new String[] {
            INDEX_NAME,
            getCollectionNameToBeDeleted("pinecone") + true,
            getCollectionNameToBeDeleted("pinecone") + false,
            "somecollection"
        }) {
            spinWait(() -> {
                IndexModel im;
                try {
                    im = pc.describeIndex(indexName);
                } catch (PineconeNotFoundException ex) {
                    im = pc.createServerlessIndex(
                            indexName,
                            "cosine",
                            4,
                            "aws",
                            "us-east-1",
                            indexName.equals("somecollection") ? "enabled" : "disabled",
                            emptyMap());
                }
                return !IndexModelStatus.SERIALIZED_NAME_READY.equals(
                        im.getStatus().getState());
            });
        }

        var ic = pc.getIndexConnection(INDEX_NAME);
        ic.upsert(
                ID_1,
                List.of(0.05f, 0.61f, 0.76f, 0.74f),
                null,
                null,
                Struct.newBuilder()
                        .putAllFields(Map.of(
                                "city",
                                Value.newBuilder().setStringValue("Berlin").build(),
                                "foo",
                                Value.newBuilder().setStringValue("one").build()))
                        .build(),
                null);
        ic.upsert(
                ID_2,
                List.of(0.19f, 0.81f, 0.75f, 0.11f),
                null,
                null,
                Struct.newBuilder()
                        .putAllFields(Map.of(
                                "city",
                                Value.newBuilder().setStringValue("London").build(),
                                "foo",
                                Value.newBuilder().setStringValue("two").build()))
                        .build(),
                null);
    }

    @Override
    Map<String, Object> enrichCreateCollection(String provider, Map<String, Object> defaultConfig) {
        defaultConfig.put("spec", Map.of("serverless", Map.of("cloud", "aws", "region", "us-east-1")));
        return defaultConfig;
    }
}
