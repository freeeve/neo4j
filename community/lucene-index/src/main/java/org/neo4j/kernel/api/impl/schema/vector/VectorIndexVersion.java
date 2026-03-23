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
package org.neo4j.kernel.api.impl.schema.vector;

import static org.neo4j.kernel.api.impl.schema.vector.LegacyVectorIndexSettingValidators.dimensionsValidator;
import static org.neo4j.kernel.api.impl.schema.vector.LegacyVectorIndexSettingValidators.hnswEfConstructionValidator;
import static org.neo4j.kernel.api.impl.schema.vector.LegacyVectorIndexSettingValidators.hnswMValidator;
import static org.neo4j.kernel.api.impl.schema.vector.LegacyVectorIndexSettingValidators.quantizationEnabledValidator;
import static org.neo4j.kernel.api.impl.schema.vector.LegacyVectorIndexSettingValidators.similarityFunctionValidator;
import static org.neo4j.kernel.api.impl.schema.vector.Neo4jVectorSimilarityFunction.EUCLIDEAN;
import static org.neo4j.kernel.api.impl.schema.vector.Neo4jVectorSimilarityFunction.L2_NORM_COSINE;
import static org.neo4j.kernel.api.impl.schema.vector.Neo4jVectorSimilarityFunction.SIMPLE_COSINE;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.neo4j.configuration.Config;
import org.neo4j.exceptions.InvalidArgumentException;
import org.neo4j.internal.schema.AllIndexProviderDescriptors;
import org.neo4j.internal.schema.IndexProviderDescriptor;
import org.neo4j.internal.schema.NotFoundTypedIndexSettingsValidator;
import org.neo4j.internal.schema.TypedIndexSettingsValidator;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.api.impl.schema.vector.VectorIndexSettingsValidators.LegacyVersionedValidator;
import org.neo4j.kernel.api.vector.VectorSimilarityFunction;
import org.neo4j.util.VisibleForTesting;
import org.neo4j.values.VectorCandidate;
import org.neo4j.values.storable.FloatingPointArray;
import org.neo4j.values.storable.Value;

public enum VectorIndexVersion {
    UNKNOWN(
            AllIndexProviderDescriptors.UNDECIDED,
            KernelVersion.EARLIEST,
            0,
            0,
            0,
            Collections.emptySet(),
            Collections.emptySet()) {
        @Override
        protected Map<KernelVersion, TypedIndexSettingsValidator<VectorIndexConfig>> configureValidators() {
            return Map.ofEntries(Map.entry(
                    KernelVersion.EARLIEST,
                    new NotFoundTypedIndexSettingsValidator<>(
                            AllIndexProviderDescriptors.UNDECIDED,
                            InvalidArgumentException.internalError(
                                    "Validator Not Found",
                                    "Validator not found for '%s'"
                                            .formatted(descriptor().name())))));
        }

        @Override
        public boolean acceptsValueInstanceType(Value candidate) {
            return false;
        }
    },

    V1_0(
            AllIndexProviderDescriptors.VECTOR_V1_DESCRIPTOR,
            KernelVersion.VERSION_NODE_VECTOR_INDEX_INTRODUCED,
            2048,
            512,
            3200,
            Set.of(EUCLIDEAN, SIMPLE_COSINE),
            Collections.emptySet()) {
        @Override
        protected Map<KernelVersion, TypedIndexSettingsValidator<VectorIndexConfig>> configureValidators() {
            return Map.ofEntries(
                    Map.entry(
                            KernelVersion.VERSION_NODE_VECTOR_INDEX_INTRODUCED,
                            new LegacyVersionedValidator(
                                    this,
                                    dimensionsValidator(1, Integer.MAX_VALUE), // this was a bug
                                    similarityFunctionValidator(nameToSimilarityFunction()),
                                    quantizationEnabledValidator(false),
                                    hnswMValidator(16),
                                    hnswEfConstructionValidator(100))),
                    Map.entry(
                            KernelVersion.V5_12,
                            new LegacyVersionedValidator(
                                    this,
                                    dimensionsValidator(1, maxDimensions()),
                                    similarityFunctionValidator(nameToSimilarityFunction()),
                                    quantizationEnabledValidator(false),
                                    hnswMValidator(16),
                                    hnswEfConstructionValidator(100))));
        }

        @Override
        public boolean acceptsValueInstanceType(Value candidate) {
            return candidate instanceof FloatingPointArray;
        }
    },

    V2_0(
            AllIndexProviderDescriptors.VECTOR_V2_DESCRIPTOR,
            KernelVersion.VERSION_VECTOR_2_INTRODUCED,
            4096,
            512,
            3200,
            Set.of(EUCLIDEAN, L2_NORM_COSINE),
            Set.of(false, true)) {
        @Override
        protected Map<KernelVersion, TypedIndexSettingsValidator<VectorIndexConfig>> configureValidators() {
            return Map.ofEntries(
                    Map.entry(
                            KernelVersion.VERSION_VECTOR_2_INTRODUCED,
                            new LegacyVersionedValidator(
                                    this,
                                    dimensionsValidator(1, maxDimensions()),
                                    similarityFunctionValidator(nameToSimilarityFunction()),
                                    quantizationEnabledValidator(false),
                                    hnswMValidator(16),
                                    hnswEfConstructionValidator(100))),
                    Map.entry(
                            KernelVersion.VERSION_VECTOR_QUANTIZATION_AND_HYPER_PARAMS,
                            new LegacyVersionedValidator(
                                    this,
                                    dimensionsValidator(1, maxDimensions(), OptionalInt.empty()),
                                    similarityFunctionValidator(nameToSimilarityFunction(), L2_NORM_COSINE),
                                    quantizationEnabledValidator(supportedQuantizationBooleans(), false, true),
                                    hnswMValidator(1, maxHnswM(), 16),
                                    hnswEfConstructionValidator(1, maxHnswEfConstruction(), 100))));
        }

        @Override
        public boolean acceptsValueInstanceType(Value candidate) {
            return candidate instanceof VectorCandidate;
        }
    },
    V3_0(
            AllIndexProviderDescriptors.VECTOR_V3_DESCRIPTOR,
            KernelVersion.VERSION_LUCENE_10_INTRODUCED,
            4096,
            512,
            3200,
            Set.of(EUCLIDEAN, L2_NORM_COSINE),
            Set.of(false, true)) {
        @Override
        protected Map<KernelVersion, TypedIndexSettingsValidator<VectorIndexConfig>> configureValidators() {
            return Map.ofEntries(Map.entry(
                    KernelVersion.VERSION_LUCENE_10_INTRODUCED,
                    new LegacyVersionedValidator(
                            this,
                            dimensionsValidator(1, maxDimensions(), OptionalInt.empty()),
                            similarityFunctionValidator(nameToSimilarityFunction(), L2_NORM_COSINE),
                            quantizationEnabledValidator(supportedQuantizationBooleans(), false, true),
                            hnswMValidator(1, maxHnswM(), 16),
                            hnswEfConstructionValidator(1, maxHnswEfConstruction(), 100))));
        }

        @Override
        public boolean acceptsValueInstanceType(Value candidate) {
            return candidate instanceof VectorCandidate;
        }
    };

    public static final SortedSet<VectorIndexVersion> KNOWN_VERSIONS;

    static {
        final TreeSet<VectorIndexVersion> versions = new TreeSet<>();
        for (final VectorIndexVersion version : values()) {
            if (version != UNKNOWN) {
                versions.add(version);
            }
        }
        KNOWN_VERSIONS = Collections.unmodifiableSortedSet(versions);
    }

    public static VectorIndexVersion latestSupportedVersion(KernelVersion kernelVersion) {
        for (final var version : KNOWN_VERSIONS.reversed()) {
            if (kernelVersion.isAtLeast(version.minimumRequiredKernelVersion)) {
                return version;
            }
        }
        return UNKNOWN;
    }

    public static VectorIndexVersion fromDescriptor(IndexProviderDescriptor descriptor) {
        for (final var version : KNOWN_VERSIONS.reversed()) {
            if (version.descriptor.equals(descriptor)) {
                return version;
            }
        }
        return UNKNOWN;
    }

    private final KernelVersion minimumRequiredKernelVersion;
    private final IndexProviderDescriptor descriptor;
    private final int maxDimensions;
    private final Map<String, VectorSimilarityFunction> similarityFunctions;
    private final Set<Boolean> quantizationBooleans;
    private final int maxHnswM;
    private final int maxHnswEfConstruction;
    private final SortedMap<KernelVersion, TypedIndexSettingsValidator<VectorIndexConfig>> validators;
    private final TypedIndexSettingsValidator<VectorIndexConfig> latestIndexSettingValidator;

    VectorIndexVersion(
            IndexProviderDescriptor providerDescriptor,
            KernelVersion minimumRequiredKernelVersion,
            int maxDimensions,
            int maxHnswM,
            int maxHnswEfConstruction,
            Set<VectorSimilarityFunction> supportedSimilarityFunctions,
            Set<Boolean> supportedQuantizationEnableds) {
        this.minimumRequiredKernelVersion = minimumRequiredKernelVersion;
        this.descriptor = providerDescriptor;
        this.maxDimensions = maxDimensions;
        {
            final Map<String, VectorSimilarityFunction> similarityFunctions =
                    new HashMap<>(supportedSimilarityFunctions.size());
            for (final VectorSimilarityFunction similarityFunction : supportedSimilarityFunctions) {
                similarityFunctions.put(similarityFunction.functionName().toUpperCase(Locale.ROOT), similarityFunction);
            }
            this.similarityFunctions = Collections.unmodifiableMap(similarityFunctions);
        }
        this.quantizationBooleans = Collections.unmodifiableSet(supportedQuantizationEnableds);
        this.maxHnswM = maxHnswM;
        this.maxHnswEfConstruction = maxHnswEfConstruction;
        {
            final TreeMap<KernelVersion, TypedIndexSettingsValidator<VectorIndexConfig>> validators =
                    new TreeMap<>(Comparator.reverseOrder());
            validators.putAll(configureValidators());
            this.validators = Collections.unmodifiableSortedMap(validators);
        }
        this.latestIndexSettingValidator = indexSettingValidator(KernelVersion.getLatestVersion(Config.defaults()));
    }

    public KernelVersion minimumRequiredKernelVersion() {
        return minimumRequiredKernelVersion;
    }

    public IndexProviderDescriptor descriptor() {
        return descriptor;
    }

    @VisibleForTesting
    public int maxDimensions() {
        return maxDimensions;
    }

    @VisibleForTesting
    public int maxHnswM() {
        return maxHnswM;
    }

    @VisibleForTesting
    public int maxHnswEfConstruction() {
        return maxHnswEfConstruction;
    }

    protected abstract Map<KernelVersion, TypedIndexSettingsValidator<VectorIndexConfig>> configureValidators();

    public abstract boolean acceptsValueInstanceType(Value candidate);

    public VectorSimilarityFunction maybeSimilarityFunction(String name) {
        return similarityFunctions.get(name.toUpperCase(Locale.ROOT));
    }

    public VectorSimilarityFunction similarityFunction(String name) {
        final var similarityFunction = maybeSimilarityFunction(name);
        if (similarityFunction == null) {
            throw new IllegalArgumentException(
                    "'%s' is an unsupported vector similarity function for index with provider %s. "
                                    .formatted(name, descriptor.name())
                            + "Supported: "
                            + similarityFunctions.keySet());
        }

        return similarityFunction;
    }

    @VisibleForTesting
    public Collection<VectorSimilarityFunction> supportedSimilarityFunctions() {
        return similarityFunctions.values();
    }

    Map<String, VectorSimilarityFunction> nameToSimilarityFunction() {
        return similarityFunctions;
    }

    @VisibleForTesting
    public Set<Boolean> supportedQuantizationBooleans() {
        return quantizationBooleans;
    }

    public TypedIndexSettingsValidator<VectorIndexConfig> indexSettingValidator() {
        return latestIndexSettingValidator;
    }

    public TypedIndexSettingsValidator<VectorIndexConfig> indexSettingValidator(KernelVersion kernelVersion) {
        for (final Entry<KernelVersion, TypedIndexSettingsValidator<VectorIndexConfig>> entry : validators.entrySet()) {
            if (kernelVersion.isAtLeast(entry.getKey())) {
                return entry.getValue();
            }
        }
        return new NotFoundTypedIndexSettingsValidator<>(
                descriptor,
                InvalidArgumentException.internalError(
                        "Validator Not Found",
                        "Validator not found for '%s' on '%s'.".formatted(descriptor.name(), kernelVersion)));
    }
}
