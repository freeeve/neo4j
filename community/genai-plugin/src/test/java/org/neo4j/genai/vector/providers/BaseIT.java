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
package org.neo4j.genai.vector.providers;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.function.Suppliers;
import org.neo4j.genai.util.ParametersTest;
import org.neo4j.genai.vector.VectorEncoding;
import org.neo4j.genai.vector.VectorEncoding.InternalBatchRow;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.kernel.api.impl.schema.vector.Neo4jVectorSimilarityFunction;
import org.neo4j.values.VectorCandidate;
import org.neo4j.values.storable.NoValue;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

@TestInstance(Lifecycle.PER_CLASS)
abstract class BaseIT {
    private static final VectorEncoding VECTOR_ENCODING = new VectorEncoding();
    private static final List<String> RESOURCES = Arrays.asList(
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
            null,
            "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.",
            null,
            "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.");

    private final String provider;
    private final Map<String, ?> config;
    private final String expectedVectorsFilename;
    private final Supplier<List<float[]>> expectedVectors;
    private final Set<EmbeddingGenerator> embeddingGenerators;

    static List<float[]> loadExpectedEmbeddings(String filename) {
        final var vectors = new ArrayList<float[]>();
        final var scanner = new Scanner(requireNonNull(BaseIT.class.getResourceAsStream(filename)));
        while (scanner.hasNextLine()) {
            final String line = scanner.nextLine();
            if (line.equals("null")) {
                vectors.add(null);
            } else {
                final var vector = ArrayUtils.toPrimitive(
                        Arrays.stream(line.split(", ")).map(Float::parseFloat).toArray(Float[]::new));
                vectors.add(vector);
            }
        }
        return vectors;
    }

    protected BaseIT(
            String provider,
            Map<String, ?> config,
            String expectedVectorsFilename,
            EmbeddingGenerator... embeddingGenerators) {
        this.provider = provider;
        this.config = config;
        this.expectedVectorsFilename = expectedVectorsFilename;
        this.expectedVectors = expectedVectorsFilename != null
                ? Suppliers.lazySingleton(() -> loadExpectedEmbeddings(expectedVectorsFilename))
                : null;
        this.embeddingGenerators = EnumSet.copyOf(Arrays.asList(embeddingGenerators));
    }

    protected BaseIT(
            String provider,
            String expectedVectorsFileName,
            Map<String, ?> baseConfig,
            Map<String, ?> configExtension) {
        this(
                provider,
                mergeBaseAndExtendedConfig(baseConfig, configExtension),
                expectedVectorsFileName,
                EmbeddingGenerator.SINGLE,
                EmbeddingGenerator.BATCHED);
    }

    protected BaseIT(String provider, Map<String, ?> baseConfig, Map<String, ?> configExtension) {
        this(provider, mergeBaseAndExtendedConfig(baseConfig, configExtension), null, EmbeddingGenerator.BATCHED_LOREM);
    }

    static Map<String, ?> mergeBaseAndExtendedConfig(Map<String, ?> baseConfig, Map<String, ?> configExtension) {
        final var config = new HashMap<String, Object>();
        if (baseConfig != null && !baseConfig.isEmpty()) {
            config.putAll(baseConfig);
        }
        if (configExtension != null && !configExtension.isEmpty()) {
            config.putAll(configExtension);
        }
        return config;
    }

    @ParameterizedTest
    @MethodSource
    void shouldGenerateApproximatelyExpectedEmbeddings(Supplier<Stream<Value>> supplier) {
        final var similarity = Neo4jVectorSimilarityFunction.EUCLIDEAN;
        if (expectedVectors != null) {
            assertThat(supplier.get()).zipSatisfy(expectedVectors.get(), (vector, expectedVector) -> {
                if (expectedVector == null) {
                    assertThat(vector).isEqualTo(Values.NO_VALUE);
                } else {
                    final var score = similarity.compare(similarity.maybeToValidVector(vector), expectedVector);
                    assertThat(score).as("should be similar").isCloseTo(1.f, withinPercentage(1));
                }
            });
        } else {
            var count = supplier.get().count();
            assertThat(count).isPositive();
        }
    }

    Stream<Named<Supplier<Stream<Value>>>> shouldGenerateApproximatelyExpectedEmbeddings() {
        return embeddingGenerators.stream()
                .map(embeddingGenerator ->
                        Named.of(embeddingGenerator.name(), () -> embeddingGenerator.embeddings(provider, config)));
    }

    enum EmbeddingGenerator {
        SINGLE {
            @Override
            Stream<Value> embeddings(String provider, Map<String, ?> config) {
                return RESOURCES.stream()
                        .map(resource -> VECTOR_ENCODING.encode(resource, provider, ParametersTest.from(config)));
            }
        },

        BATCHED {
            @Override
            Stream<Value> embeddings(String provider, Map<String, ?> config) {
                return VECTOR_ENCODING
                        .encode(RESOURCES, provider, ParametersTest.from(config))
                        .map(InternalBatchRow::vector);
            }
        },

        BATCHED_LOREM {
            @Override
            Stream<Value> embeddings(String provider, Map<String, ?> config) {
                try (final var in = new BufferedReader(new InputStreamReader(
                        requireNonNull(BaseIT.class.getResourceAsStream("lorem.txt")), StandardCharsets.UTF_8))) {
                    final var resources =
                            in.lines().filter(Predicate.not(String::isEmpty)).toList();
                    return VECTOR_ENCODING
                            .encode(resources, provider, ParametersTest.from(config))
                            .map(InternalBatchRow::vector);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };

        abstract Stream<Value> embeddings(String provider, Map<String, ?> config);
    }

    @Disabled("Used for generation only. Run directly for class.")
    @Test
    void generateAndWriteEmbeddings() throws IOException {
        // replace with location wanted
        final var directory = Path.of(System.getProperty("user.home"), "genai-plugin-embeddings");

        assumeThat(expectedVectorsFilename).isNotNull();
        final var embeddingsGenerator = embeddingGenerators.contains(EmbeddingGenerator.BATCHED)
                ? EmbeddingGenerator.BATCHED
                : EmbeddingGenerator.SINGLE;

        try (final var fs = new DefaultFileSystemAbstraction()) {
            final var path = directory.resolve(expectedVectorsFilename).toAbsolutePath();
            fs.mkdirs(path.getParent());
            try (final var out = new PrintStream(fs.openAsOutputStream(path, false));
                    final var embeddings = embeddingsGenerator.embeddings(provider, config)) {
                embeddings.forEach(embedding -> {
                    switch (embedding) {
                        case NoValue ignored -> out.println("null");
                        case VectorCandidate vectorCandidate -> {
                            final int dimensions = vectorCandidate.dimensions();
                            assert dimensions > 0;
                            out.print(vectorCandidate.floatValue(0));
                            out.print('f');
                            for (int i = 1; i < dimensions; i++) {
                                out.print(", ");
                                out.print(vectorCandidate.floatValue(i));
                                out.print('f');
                            }
                            out.println();
                        }
                        case null, default -> throw new IllegalStateException("Should be a vector or no value");
                    }
                });
            }
        }
    }
}
