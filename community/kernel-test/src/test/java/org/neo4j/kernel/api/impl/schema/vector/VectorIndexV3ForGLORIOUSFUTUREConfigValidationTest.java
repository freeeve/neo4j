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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.CLASS;
import static org.assertj.core.api.InstanceOfAssertFactories.iterable;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.assertj.core.groups.Tuple.tuple;
import static org.neo4j.internal.schema.IndexSettingRecord.State.INCORRECT_TYPE;
import static org.neo4j.internal.schema.IndexSettingRecord.State.INVALID_VALUE;
import static org.neo4j.internal.schema.IndexSettingRecord.State.UNRECOGNIZED_SETTING;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.DIMENSIONS;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.HNSW_EF_CONSTRUCTION;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.HNSW_M;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.QUANTIZATION_ENABLED;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.QUANTIZATION_TYPE;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.SIMILARITY_FUNCTION;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.StringJoiner;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.exceptions.InvalidArgumentException;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.internal.schema.IndexConfigUtils.HasSetting;
import org.neo4j.internal.schema.IndexConfigUtils.NamedSetting;
import org.neo4j.internal.schema.IndexSettingRecord.IncorrectType;
import org.neo4j.internal.schema.IndexSettingRecord.InvalidValue;
import org.neo4j.internal.schema.IndexSettingRecord.RecordWithValue;
import org.neo4j.internal.schema.IndexSettingRecord.UnrecognizedSetting;
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

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.valid()).isTrue();

        final var ref = new MutableObject<VectorIndexConfig>();
        assertThatCode(() -> ref.setValue(VALIDATOR.validateToTypedConfig(settings)))
                .doesNotThrowAnyException();
        final var vectorIndexConfig = ref.get();

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

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.valid()).isTrue();

        final var ref = new MutableObject<VectorIndexConfig>();
        assertThatCode(() -> ref.setValue(VALIDATOR.validateToTypedConfig(settings)))
                .doesNotThrowAnyException();
        final var vectorIndexConfig = ref.get();

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

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        assertThat(validationRecords.get(UNRECOGNIZED_SETTING))
                .hasSize(1)
                .first()
                .asInstanceOf(type(UnrecognizedSetting.class))
                .extracting(NamedSetting::settingName)
                .isEqualTo(unrecognisedSetting.getSettingName());

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

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        final var incorrectTypeAssert = assertThat(validationRecords.get(INCORRECT_TYPE))
                .hasSize(1)
                .first()
                .asInstanceOf(type(IncorrectType.class));
        incorrectTypeAssert
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactly(DIMENSIONS, Values.stringValue(incorrectDimensions));
        incorrectTypeAssert
                .extracting(IncorrectType::providedType)
                .asInstanceOf(CLASS)
                .isAssignableTo(TextValue.class);
        incorrectTypeAssert
                .extracting(IncorrectType::targetType)
                .asInstanceOf(CLASS)
                .isAssignableTo(IntegralValue.class);

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
        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        assertThat(validationRecords.get(INVALID_VALUE))
                .hasSize(1)
                .first()
                .asInstanceOf(type(InvalidValue.class))
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactly(DIMENSIONS, OptionalInt.of(invalidDimensions));

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

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        final var incorrectTypeAssert = assertThat(validationRecords.get(INCORRECT_TYPE))
                .hasSize(1)
                .first()
                .asInstanceOf(type(IncorrectType.class));
        incorrectTypeAssert
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactly(SIMILARITY_FUNCTION, Values.longValue(incorrectSimilarityFunction));
        incorrectTypeAssert
                .extracting(IncorrectType::providedType)
                .asInstanceOf(CLASS)
                .isAssignableTo(NumberValue.class);
        incorrectTypeAssert
                .extracting(IncorrectType::targetType)
                .asInstanceOf(CLASS)
                .isAssignableTo(TextValue.class);

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

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        assertThat(validationRecords.get(INVALID_VALUE))
                .hasSize(1)
                .first()
                .asInstanceOf(type(InvalidValue.class))
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactly(SIMILARITY_FUNCTION, normalizedInvalidSimilarityFunction);

        final StringJoiner supportedSimilarityFunctions = new StringJoiner(", ", "[", "]");
        for (final VectorSimilarityFunction similarityFunction : VERSION.supportedSimilarityFunctions()) {
            supportedSimilarityFunctions.add(similarityFunction.functionName());
        }
        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContainingAll(
                        normalizedInvalidSimilarityFunction,
                        "is an unsupported",
                        SIMILARITY_FUNCTION.getSettingName(),
                        supportedSimilarityFunctions.toString());
    }

    @Test
    void nonUpperCaseSimilarityFunction() {
        final var similarityFunction = "coSIne";
        final var settings = VectorIndexSettings.create()
                .withSimilarityFunction(similarityFunction)
                .toSettingsAccessor();

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.valid()).isTrue();

        final var vectorIndexConfig = VALIDATOR.validateToTypedConfig(validationRecords);
        assertThat(vectorIndexConfig)
                .extracting(
                        VectorIndexConfig::similarityFunction,
                        config -> config.get(SIMILARITY_FUNCTION),
                        config -> config.getValue(SIMILARITY_FUNCTION))
                .containsExactly(
                        Neo4jVectorSimilarityFunction.L2_NORM_COSINE,
                        Neo4jVectorSimilarityFunction.L2_NORM_COSINE,
                        Values.utf8Value(Neo4jVectorSimilarityFunction.L2_NORM_COSINE.functionName()));
    }

    @Test
    void incorrectTypeForQuantizationEnabled() {
        final var incorrectQuantizationEnabled = 123L;
        final var settings = VectorIndexSettings.create()
                .set(QUANTIZATION_ENABLED, incorrectQuantizationEnabled)
                .toSettingsAccessor();

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        final var incorrectTypeAssert = assertThat(validationRecords.get(INCORRECT_TYPE))
                .hasSize(1)
                .first()
                .asInstanceOf(type(IncorrectType.class));
        incorrectTypeAssert
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactly(QUANTIZATION_ENABLED, Values.longValue(incorrectQuantizationEnabled));
        incorrectTypeAssert
                .extracting(IncorrectType::providedType)
                .asInstanceOf(CLASS)
                .isAssignableTo(NumberValue.class);
        incorrectTypeAssert
                .extracting(IncorrectType::targetType)
                .asInstanceOf(CLASS)
                .isAssignableTo(BooleanValue.class);

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

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        final var incorrectTypeAssert = assertThat(validationRecords.get(INCORRECT_TYPE))
                .hasSize(1)
                .first()
                .asInstanceOf(type(IncorrectType.class));
        incorrectTypeAssert
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactly(QUANTIZATION_TYPE, Values.longValue(incorrectQuantizationType));
        incorrectTypeAssert
                .extracting(IncorrectType::providedType)
                .asInstanceOf(CLASS)
                .isAssignableTo(NumberValue.class);
        incorrectTypeAssert
                .extracting(IncorrectType::targetType)
                .asInstanceOf(CLASS)
                .isAssignableTo(TextValue.class);

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

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        assertThat(validationRecords.get(INVALID_VALUE))
                .hasSize(1)
                .first()
                .asInstanceOf(type(InvalidValue.class))
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactly(QUANTIZATION_TYPE, normalizedIncorrectQuantizationType);

        final StringJoiner supportedQuantizationTypes = new StringJoiner(", ", "[", "]");
        for (final VectorQuantizationType quantizationType : VERSION.supportedQuantizationTypes()) {
            supportedQuantizationTypes.add(quantizationType.name());
        }
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
        final var quantizationType = "sCAlaR";
        final var settings = VectorIndexSettings.create()
                .withQuantizationType(quantizationType)
                .toSettingsAccessor();

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.valid()).isTrue();

        final var vectorIndexConfig = VALIDATOR.validateToTypedConfig(validationRecords);
        assertThat(vectorIndexConfig)
                .extracting(
                        VectorIndexConfig::quantization,
                        config -> config.get(QUANTIZATION_TYPE),
                        config -> config.getValue(QUANTIZATION_TYPE))
                .containsExactly(
                        VectorQuantizationType.SCALAR,
                        VectorQuantizationType.SCALAR,
                        Values.utf8Value(VectorQuantizationType.SCALAR.name()));
    }

    @Test
    void invalidQuantizationEnabledFalseForQuantizationType() {
        final var settings = VectorIndexSettings.create()
                .withQuantizationDisabled()
                .withQuantizationType(VectorQuantizationType.SCALAR)
                .toSettingsAccessor();

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
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

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
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
        final var settings = VectorIndexSettings.create()
                .withQuantizationEnabled()
                .withQuantizationType(VectorQuantizationType.SCALAR)
                .toSettingsAccessor();

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.valid()).isTrue();

        final var vectorIndexConfig = VALIDATOR.validateToTypedConfig(validationRecords);
        assertThat(vectorIndexConfig)
                .extracting(
                        VectorIndexConfig::quantizationEnabled,
                        config -> config.get(QUANTIZATION_ENABLED),
                        config -> config.getValue(QUANTIZATION_ENABLED))
                .containsExactly(true, Optional.empty(), Values.NO_VALUE);

        assertThat(vectorIndexConfig)
                .extracting(
                        VectorIndexConfig::quantization,
                        config -> config.get(QUANTIZATION_TYPE),
                        config -> config.getValue(QUANTIZATION_TYPE))
                .containsExactly(
                        VectorQuantizationType.SCALAR,
                        VectorQuantizationType.SCALAR,
                        Values.utf8Value(VectorQuantizationType.SCALAR.name()));
    }

    @Test
    void incorrectTypeForHnswM() {
        final var incorrectHnswM = "Here is a String";
        final var settings =
                VectorIndexSettings.create().set(HNSW_M, incorrectHnswM).toSettingsAccessor();

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        final var incorrectTypeAssert = assertThat(validationRecords.get(INCORRECT_TYPE))
                .hasSize(1)
                .first()
                .asInstanceOf(type(IncorrectType.class));
        incorrectTypeAssert
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactly(HNSW_M, Values.stringValue(incorrectHnswM));
        incorrectTypeAssert
                .extracting(IncorrectType::providedType)
                .asInstanceOf(CLASS)
                .isAssignableTo(TextValue.class);
        incorrectTypeAssert
                .extracting(IncorrectType::targetType)
                .asInstanceOf(CLASS)
                .isAssignableTo(IntegralValue.class);

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
        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        assertThat(validationRecords.get(INVALID_VALUE))
                .hasSize(1)
                .first()
                .asInstanceOf(type(InvalidValue.class))
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactly(HNSW_M, invalidM);

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

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        final var incorrectTypeAssert = assertThat(validationRecords.get(INCORRECT_TYPE))
                .hasSize(1)
                .first()
                .asInstanceOf(type(IncorrectType.class));
        incorrectTypeAssert
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactly(HNSW_EF_CONSTRUCTION, Values.stringValue(incorrectHnswEfConstruction));
        incorrectTypeAssert
                .extracting(IncorrectType::providedType)
                .asInstanceOf(CLASS)
                .isAssignableTo(TextValue.class);
        incorrectTypeAssert
                .extracting(IncorrectType::targetType)
                .asInstanceOf(CLASS)
                .isAssignableTo(IntegralValue.class);

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
        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        assertThat(validationRecords.get(INVALID_VALUE))
                .hasSize(1)
                .first()
                .asInstanceOf(type(InvalidValue.class))
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactly(HNSW_EF_CONSTRUCTION, invalidHnswEfConstruction);

        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContainingAll(
                        HNSW_EF_CONSTRUCTION.getSettingName(),
                        "must be between 1 and",
                        String.valueOf(VERSION.maxHnswEfConstruction()));
    }
}
