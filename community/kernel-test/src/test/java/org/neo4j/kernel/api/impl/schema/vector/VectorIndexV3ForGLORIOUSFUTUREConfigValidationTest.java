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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.iterable;
import static org.assertj.core.groups.Tuple.tuple;
import static org.neo4j.internal.schema.IndexSettingRecord.State.INVALID_VALUE;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.DIMENSIONS;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.HNSW_EF_CONSTRUCTION;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.HNSW_M;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.QUANTIZATION_ENABLED;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.QUANTIZATION_TYPE;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.SIMILARITY_FUNCTION;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigValidationTestUtils.assertAndReturnFunctionDoesNotThrow;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigValidationTestUtils.assertIncorrectType;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigValidationTestUtils.assertInvalidValue;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigValidationTestUtils.assertUnrecognizedSetting;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigValidationTestUtils.assertVectorIndexConfigSetting;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigValidationTestUtils.similarityFunctionsToString;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigValidationTestUtils.validateAsInvalid;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigValidationTestUtils.validateAsValid;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.exceptions.InvalidArgumentException;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.schema.IndexConfigUtils.HasSetting;
import org.neo4j.internal.schema.IndexSettingRecord.InvalidValue;
import org.neo4j.internal.schema.IndexSettingRecord.RecordWithValue;
import org.neo4j.internal.schema.SettingsAccessor;
import org.neo4j.internal.schema.SettingsAccessor.IndexConfigAccessor;
import org.neo4j.internal.schema.TypedIndexSettingsValidator;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfig.HnswConfig;
import org.neo4j.kernel.api.schema.vector.VectorTestUtils.VectorIndexSettings;
import org.neo4j.kernel.api.vector.VectorSimilarityFunction;
import org.neo4j.values.storable.BooleanValue;
import org.neo4j.values.storable.IntegralValue;
import org.neo4j.values.storable.NumberValue;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.storable.Values;

class VectorIndexV3ForGLORIOUSFUTUREConfigValidationTest {
    private static final VectorIndexVersion VERSION = VectorIndexVersion.V3_0;
    private static final TypedIndexSettingsValidator<VectorIndexConfig> VALIDATOR =
            VERSION.indexSettingValidator(KernelVersion.GLORIOUS_FUTURE);

    @Test
    void validV3ForV202509IndexConfig() {
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions())
                .withHnswM(16)
                .withHnswEfConstruction(100)
                .withQuantizationEnabled()
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
                .toSettingsAccessor();

        final var vectorIndexConfigAsIfCreatedOn202509 =
                VERSION.indexSettingValidator(KernelVersion.V2025_09).validateToTypedConfig(settings);

        final var vectorIndexConfig = VALIDATOR.interpretAuthoritativeToTypedConfig(
                new IndexConfigAccessor(vectorIndexConfigAsIfCreatedOn202509.config()));

        assertThat(vectorIndexConfig).isEqualTo(vectorIndexConfigAsIfCreatedOn202509);
    }

    @Test
    void validIndexConfig() {
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions())
                .withHnswM(16)
                .withHnswEfConstruction(100)
                .withQuantizationType(VectorQuantizationType.BINARY)
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
                .toSettingsAccessor();

        validateAsValid(VALIDATOR, settings);
        final var vectorIndexConfig =
                assertAndReturnFunctionDoesNotThrow(() -> VALIDATOR.validateToTypedConfig(settings));

        assertThat(vectorIndexConfig)
                .extracting(
                        VectorIndexConfig::dimensions,
                        VectorIndexConfig::similarityFunction,
                        VectorIndexConfig::quantization,
                        VectorIndexConfig::hnsw)
                .containsExactly(
                        OptionalInt.of(VERSION.maxDimensions()),
                        VERSION.similarityFunction("COSINE"),
                        VectorQuantizationType.BINARY,
                        new HnswConfig(16, 100));

        assertThat(vectorIndexConfig.config().settingNames())
                .containsExactlyInAnyOrder(
                        DIMENSIONS.getSettingName(),
                        SIMILARITY_FUNCTION.getSettingName(),
                        QUANTIZATION_TYPE.getSettingName(),
                        HNSW_M.getSettingName(),
                        HNSW_EF_CONSTRUCTION.getSettingName());
    }

    @Test
    void validIndexConfigWithDefaults() {
        final var settings = VectorIndexSettings.create().toSettingsAccessor();

        validateAsValid(VALIDATOR, settings);
        final var vectorIndexConfig =
                assertAndReturnFunctionDoesNotThrow(() -> VALIDATOR.validateToTypedConfig(settings));

        assertThat(vectorIndexConfig)
                .extracting(
                        VectorIndexConfig::dimensions,
                        VectorIndexConfig::similarityFunction,
                        VectorIndexConfig::quantization,
                        VectorIndexConfig::hnsw)
                .containsExactly(
                        OptionalInt.empty(),
                        VERSION.similarityFunction("COSINE"),
                        VectorQuantizationType.SCALAR,
                        new HnswConfig(16, 100));

        assertThat(vectorIndexConfig.config().settingNames())
                .containsExactlyInAnyOrder(
                        SIMILARITY_FUNCTION.getSettingName(),
                        QUANTIZATION_TYPE.getSettingName(),
                        HNSW_M.getSettingName(),
                        HNSW_EF_CONSTRUCTION.getSettingName());
    }

    @Test
    void unrecognisedSetting() {
        final var unrecognisedSetting = IndexSetting.fulltext_Analyzer();
        final var settings =
                VectorIndexSettings.create().set(unrecognisedSetting, "swedish").toSettingsAccessor();

        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertUnrecognizedSetting(validationRecords, unrecognisedSetting.getSettingName());
        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContainingAll(
                        "Invalid index config key 'fulltext.analyzer', it was not recognized as an index setting.");
    }

    @Test
    void incorrectTypeForDimensions() {
        final var incorrectDimensions = String.valueOf(VERSION.maxDimensions());
        final var settings = VectorIndexSettings.create()
                .set(DIMENSIONS, incorrectDimensions)
                .toSettingsAccessor();

        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertIncorrectType(
                validationRecords,
                DIMENSIONS,
                Values.stringValue(incorrectDimensions),
                TextValue.class,
                IntegralValue.class);

        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessage("Wrong type for vector.dimensions. Expected INTEGER, got STRING");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void nonPositiveDimensions(int invalidDimensions) {
        final var settings =
                VectorIndexSettings.create().withDimensions(invalidDimensions).toSettingsAccessor();

        assertInvalidDimensions(invalidDimensions, settings);
    }

    @Test
    void aboveMaxDimensions() {
        final int invalidDimensions = VERSION.maxDimensions() + 1;
        final var settings =
                VectorIndexSettings.create().withDimensions(invalidDimensions).toSettingsAccessor();

        assertInvalidDimensions(invalidDimensions, settings);
    }

    private void assertInvalidDimensions(int invalidDimensions, SettingsAccessor settings) {
        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertInvalidValue(validationRecords, DIMENSIONS, OptionalInt.of(invalidDimensions));
        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContainingAll(
                        DIMENSIONS.getSettingName(), "must be between 1 and", String.valueOf(VERSION.maxDimensions()));
    }

    @Test
    void incorrectTypeForSimilarityFunction() {
        final var incorrectSimilarityFunction = 123L;
        final var settings = VectorIndexSettings.create()
                .set(SIMILARITY_FUNCTION, incorrectSimilarityFunction)
                .toSettingsAccessor();

        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertIncorrectType(
                validationRecords,
                SIMILARITY_FUNCTION,
                Values.longValue(incorrectSimilarityFunction),
                NumberValue.class,
                TextValue.class);

        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContainingAll("Wrong type for vector.similarity_function. Expected STRING, got INTEGER");
    }

    @Test
    void invalidSimilarityFunction() {
        final var invalidSimilarityFunction = "ClearlyThisIsNotASimilarityFunction";
        final var settings = VectorIndexSettings.create()
                .set(IndexSetting.vector_Similarity_Function(), invalidSimilarityFunction)
                .toSettingsAccessor();
        final var normalizedInvalidSimilarityFunction = invalidSimilarityFunction.toUpperCase(Locale.ROOT);

        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertInvalidValue(validationRecords, SIMILARITY_FUNCTION, normalizedInvalidSimilarityFunction);

        final String supportedSimilarityFunctions = similarityFunctionsToString(VERSION.supportedSimilarityFunctions());
        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContainingAll(
                        normalizedInvalidSimilarityFunction,
                        "is an unsupported",
                        SIMILARITY_FUNCTION.getSettingName(),
                        supportedSimilarityFunctions);
    }

    @Test
    void nonUpperCaseSimilarityFunction() {
        final var mixedCaseSimilarityFunction = "coSIne";
        final var settings = VectorIndexSettings.create()
                .withSimilarityFunction(mixedCaseSimilarityFunction)
                .toSettingsAccessor();
        final VectorSimilarityFunction corespondingSimilarityFunction = VERSION.similarityFunction("COSINE");

        final var validationRecords = validateAsValid(VALIDATOR, settings);
        final var vectorIndexConfig = VALIDATOR.validateToTypedConfig(validationRecords);
        assertVectorIndexConfigSetting(
                vectorIndexConfig,
                SIMILARITY_FUNCTION,
                VectorIndexConfig::similarityFunction,
                corespondingSimilarityFunction,
                Values.utf8Value(corespondingSimilarityFunction.functionName()));
    }

    @Test
    void incorrectTypeForQuantizationEnabled() {
        final var incorrectQuantizationEnabled = 123L;
        final var settings = VectorIndexSettings.create()
                .set(QUANTIZATION_ENABLED, incorrectQuantizationEnabled)
                .toSettingsAccessor();

        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertIncorrectType(
                validationRecords,
                QUANTIZATION_ENABLED,
                Values.longValue(incorrectQuantizationEnabled),
                NumberValue.class,
                BooleanValue.class);

        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessage("Wrong type for vector.quantization.enabled. Expected BOOLEAN, got INTEGER");
    }

    @Test
    void incorrectTypeForQuantizationType() {
        final var incorrectQuantizationType = 123L;
        final var settings = VectorIndexSettings.create()
                .set(QUANTIZATION_TYPE, incorrectQuantizationType)
                .toSettingsAccessor();

        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertIncorrectType(
                validationRecords,
                QUANTIZATION_TYPE,
                Values.longValue(incorrectQuantizationType),
                NumberValue.class,
                TextValue.class);

        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContainingAll("Wrong type for vector.quantization.type. Expected STRING, got INTEGER");
    }

    @Test
    void invalidQuantizationType() {
        final var incorrectQuantizationType = "ClearlyThisIsNotAQuantizationType";
        final var settings = VectorIndexSettings.create()
                .set(QUANTIZATION_TYPE, incorrectQuantizationType)
                .toSettingsAccessor();
        final var normalizedIncorrectQuantizationType = incorrectQuantizationType.toUpperCase(Locale.ROOT);

        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertInvalidValue(validationRecords, QUANTIZATION_TYPE, normalizedIncorrectQuantizationType);

        final String supportedQuantizationTypes =
                Iterables.toString(VERSION.supportedQuantizationTypes(), ", ", "[", "]");
        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContainingAll(
                        normalizedIncorrectQuantizationType,
                        "is an unsupported",
                        QUANTIZATION_TYPE.getSettingName(),
                        supportedQuantizationTypes.toString());
    }

    @Test
    void nonUpperCaseQuantizationType() {
        final var mixedCaseQuantizationType = "sCAlaR";
        final var settings = VectorIndexSettings.create()
                .withQuantizationType(mixedCaseQuantizationType)
                .toSettingsAccessor();
        final VectorQuantizationType corespondingQuantizationType = VectorQuantizationType.SCALAR;

        final var validationRecords = validateAsValid(VALIDATOR, settings);
        final var vectorIndexConfig = VALIDATOR.validateToTypedConfig(validationRecords);
        assertVectorIndexConfigSetting(
                vectorIndexConfig,
                QUANTIZATION_TYPE,
                VectorIndexConfig::quantization,
                corespondingQuantizationType,
                Values.utf8Value(corespondingQuantizationType.name()));
    }

    @Test
    void invalidQuantizationEnabledFalseForQuantizationType() {
        final var settings = VectorIndexSettings.create()
                .withQuantizationDisabled()
                .withQuantizationType(VectorQuantizationType.SCALAR)
                .toSettingsAccessor();

        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertThat(validationRecords.get(INVALID_VALUE))
                .hasSize(2)
                .hasOnlyElementsOfType(InvalidValue.class)
                .extracting(InvalidValue.class::cast)
                .asInstanceOf(iterable(InvalidValue.class))
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactlyInAnyOrder(
                        tuple(QUANTIZATION_ENABLED, Optional.of(false)),
                        tuple(QUANTIZATION_TYPE, VectorQuantizationType.SCALAR));

        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .message()
                .contains("is an unsupported")
                .containsAnyOf(QUANTIZATION_ENABLED.getSettingName(), QUANTIZATION_TYPE.getSettingName());
    }

    @Test
    void invalidQuantizationEnabledTrueForQuantizationType() {
        final var settings = VectorIndexSettings.create()
                .withQuantizationEnabled()
                .withQuantizationType(VectorQuantizationType.NONE)
                .toSettingsAccessor();

        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertThat(validationRecords.get(INVALID_VALUE))
                .hasSize(2)
                .hasOnlyElementsOfType(InvalidValue.class)
                .extracting(InvalidValue.class::cast)
                .asInstanceOf(iterable(InvalidValue.class))
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactlyInAnyOrder(
                        tuple(QUANTIZATION_ENABLED, Optional.of(true)),
                        tuple(QUANTIZATION_TYPE, VectorQuantizationType.NONE));

        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .message()
                .contains("is an unsupported")
                .containsAnyOf(QUANTIZATION_ENABLED.getSettingName(), QUANTIZATION_TYPE.getSettingName());
    }

    @Test
    void validQuantizationEnabledTrueForQuantizationType() {
        final VectorQuantizationType quantizationType = VectorQuantizationType.SCALAR;
        final var settings = VectorIndexSettings.create()
                .withQuantizationEnabled()
                .withQuantizationType(quantizationType)
                .toSettingsAccessor();

        final var validationRecords = validateAsValid(VALIDATOR, settings);
        final var vectorIndexConfig = VALIDATOR.validateToTypedConfig(validationRecords);
        assertThat(vectorIndexConfig)
                .extracting(
                        VectorIndexConfig::quantizationEnabled,
                        config -> config.get(QUANTIZATION_ENABLED),
                        config -> config.getValue(QUANTIZATION_ENABLED))
                .containsExactly(true, Optional.empty(), Values.NO_VALUE);

        assertVectorIndexConfigSetting(
                vectorIndexConfig,
                QUANTIZATION_TYPE,
                VectorIndexConfig::quantization,
                quantizationType,
                Values.utf8Value(quantizationType.name()));
    }

    @Test
    void incorrectTypeForHnswM() {
        final var incorrectHnswM = "Here is a String";
        final var settings =
                VectorIndexSettings.create().set(HNSW_M, incorrectHnswM).toSettingsAccessor();

        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertIncorrectType(
                validationRecords, HNSW_M, Values.stringValue(incorrectHnswM), TextValue.class, IntegralValue.class);

        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessage("Wrong type for vector.hnsw.m. Expected INTEGER, got STRING");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void nonPositiveHnswM(int invalidM) {
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions())
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
                .withHnswM(invalidM)
                .toSettingsAccessor();

        assertInvalidM(invalidM, settings);
    }

    @Test
    void aboveMaxHnswM() {
        final int invalidHnswM = VERSION.maxHnswM() + 1;
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions())
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
                .withHnswM(invalidHnswM)
                .toSettingsAccessor();

        assertInvalidM(invalidHnswM, settings);
    }

    private void assertInvalidM(int invalidM, SettingsAccessor settings) {
        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertInvalidValue(validationRecords, HNSW_M, invalidM);
        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContainingAll(
                        HNSW_M.getSettingName(), "must be between 1 and", String.valueOf(VERSION.maxHnswM()));
    }

    @Test
    void incorrectTypeForHnswEfConstruction() {
        final var incorrectHnswEfConstruction = "Here is a String";
        final var settings = VectorIndexSettings.create()
                .set(HNSW_EF_CONSTRUCTION, incorrectHnswEfConstruction)
                .toSettingsAccessor();

        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertIncorrectType(
                validationRecords,
                HNSW_EF_CONSTRUCTION,
                Values.stringValue(incorrectHnswEfConstruction),
                TextValue.class,
                IntegralValue.class);

        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessage("Wrong type for vector.hnsw.ef_construction. Expected INTEGER, got STRING");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void nonPositiveHnswEfConstruction(int invalidEfConstruction) {
        final var settings = VectorIndexSettings.create()
                .withHnswEfConstruction(invalidEfConstruction)
                .toSettingsAccessor();

        assertInvalidEfConstruction(invalidEfConstruction, settings);
    }

    @Test
    void aboveMaxHnswEfConstruction() {
        final int invalidHnswEfConstruction = VERSION.maxHnswEfConstruction() + 1;
        final var settings = VectorIndexSettings.create()
                .withHnswEfConstruction(invalidHnswEfConstruction)
                .toSettingsAccessor();

        assertInvalidEfConstruction(invalidHnswEfConstruction, settings);
    }

    private void assertInvalidEfConstruction(int invalidHnswEfConstruction, SettingsAccessor settings) {
        final var validationRecords = validateAsInvalid(VALIDATOR, settings);
        assertInvalidValue(validationRecords, HNSW_EF_CONSTRUCTION, invalidHnswEfConstruction);
        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContainingAll(
                        HNSW_EF_CONSTRUCTION.getSettingName(),
                        "must be between 1 and",
                        String.valueOf(VERSION.maxHnswEfConstruction()));
    }
}
