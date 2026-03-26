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
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.neo4j.internal.schema.IndexSettingRecord.State.INCORRECT_TYPE;
import static org.neo4j.internal.schema.IndexSettingRecord.State.INVALID_VALUE;
import static org.neo4j.internal.schema.IndexSettingRecord.State.MISSING_SETTING;
import static org.neo4j.internal.schema.IndexSettingRecord.State.UNRECOGNIZED_SETTING;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.DIMENSIONS;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.HNSW_EF_CONSTRUCTION;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.HNSW_M;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.QUANTIZATION_ENABLED;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.QUANTIZATION_TYPE;
import static org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfigUtils.SIMILARITY_FUNCTION;

import java.util.Locale;
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
import org.neo4j.internal.schema.IndexSettingRecord.MissingSetting;
import org.neo4j.internal.schema.IndexSettingRecord.RecordWithValue;
import org.neo4j.internal.schema.IndexSettingRecord.UnrecognizedSetting;
import org.neo4j.internal.schema.SettingsAccessor;
import org.neo4j.internal.schema.SettingsAccessor.IndexConfigAccessor;
import org.neo4j.internal.schema.TypedIndexSettingsValidator;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.api.impl.schema.vector.VectorIndexConfig.HnswConfig;
import org.neo4j.kernel.api.schema.vector.VectorTestUtils.VectorIndexSettings;
import org.neo4j.kernel.api.vector.VectorSimilarityFunction;
import org.neo4j.values.storable.IntegralValue;
import org.neo4j.values.storable.NumberValue;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.storable.Values;

class VectorIndexV1ForV512ConfigValidationTest {
    private static final VectorIndexVersion VERSION = VectorIndexVersion.V1_0;
    private static final TypedIndexSettingsValidator<VectorIndexConfig> VALIDATOR =
            VERSION.indexSettingValidator(KernelVersion.V5_12);

    @Test
    void validV1ForV511IndexConfig() {
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions() + 1) // unfortunately valid
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
                .toSettingsAccessor();

        final var vectorIndexConfigAsIfCreatedOn511 =
                VERSION.indexSettingValidator(KernelVersion.V5_11).validateToTypedConfig(settings);

        final var vectorIndexConfig = VALIDATOR.interpretAuthoritativeToTypedConfig(
                new IndexConfigAccessor(vectorIndexConfigAsIfCreatedOn511.config()));

        assertThat(vectorIndexConfig).isEqualTo(vectorIndexConfigAsIfCreatedOn511);
    }

    @Test
    void validIndexConfig() {
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions())
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
                        VectorQuantizationType.NONE,
                        new HnswConfig(16, 100));

        assertThat(vectorIndexConfig.config().settingNames())
                .containsExactlyInAnyOrder(DIMENSIONS.getSettingName(), SIMILARITY_FUNCTION.getSettingName());
    }

    @Test
    void unrecognisedSetting() {
        final var unrecognisedSetting = IndexSetting.fulltext_Analyzer();
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions())
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
                .set(unrecognisedSetting, "swedish")
                .toSettingsAccessor();

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
                .hasMessage("Invalid index config key 'fulltext.analyzer', it was not recognized as an index setting.");
    }

    @Test
    void missingDimensions() {
        final var settings = VectorIndexSettings.create()
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
                .toSettingsAccessor();

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        assertThat(validationRecords.get(MISSING_SETTING))
                .hasSize(1)
                .first()
                .asInstanceOf(type(MissingSetting.class))
                .extracting(HasSetting::setting)
                .isEqualTo(DIMENSIONS);

        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContainingAll(
                        "setting is expected to have been set", "Expected", DIMENSIONS.getSettingName());
    }

    @Test
    void incorrectTypeForDimensions() {
        final var incorrectDimensions = String.valueOf(VERSION.maxDimensions());
        final var settings = VectorIndexSettings.create()
                .set(DIMENSIONS, incorrectDimensions)
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
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
        final var settings = VectorIndexSettings.create()
                .withDimensions(invalidDimensions)
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
                .toSettingsAccessor();

        assertInvalidDimensions(invalidDimensions, settings);
    }

    @Test
    void aboveMaxDimensions() {
        final int invalidDimensions = VERSION.maxDimensions() + 1;
        final var settings = VectorIndexSettings.create()
                .withDimensions(invalidDimensions)
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
                .toSettingsAccessor();

        assertInvalidDimensions(invalidDimensions, settings);

        // however fine for reading no upper bound check to support vector-1.0 created on 5.11
        // trust previously created index configs as being valid
        final var ref = new MutableObject<VectorIndexConfig>();
        assertThatCode(() -> ref.setValue(VALIDATOR.interpretAuthoritativeToTypedConfig(settings)))
                .doesNotThrowAnyException();
        final var vectorIndexConfig = ref.get();

        assertThat(vectorIndexConfig)
                .extracting(VectorIndexConfig::dimensions, VectorIndexConfig::similarityFunction)
                .containsExactly(OptionalInt.of(invalidDimensions), VERSION.similarityFunction("COSINE"));
    }

    private void assertInvalidDimensions(int invalidDimensions, SettingsAccessor settings) {
        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        assertThat(validationRecords.get(INVALID_VALUE))
                .hasSize(1)
                .first()
                .asInstanceOf(type(InvalidValue.class))
                .extracting(HasSetting::setting, RecordWithValue::value)
                .containsExactly(DIMENSIONS, invalidDimensions);

        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContainingAll(
                        DIMENSIONS.getSettingName(), "must be between 1 and", String.valueOf(VERSION.maxDimensions()));
    }

    @Test
    void missingSimilarityFunction() {
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions())
                .toSettingsAccessor();

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        assertThat(validationRecords.get(MISSING_SETTING))
                .hasSize(1)
                .first()
                .asInstanceOf(type(MissingSetting.class))
                .extracting(HasSetting::setting)
                .isEqualTo(SIMILARITY_FUNCTION);

        assertThatThrownBy(() -> VALIDATOR.validateToTypedConfig(settings))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContainingAll(
                        "setting is expected to have been set", "Expected", SIMILARITY_FUNCTION.getSettingName());
    }

    @Test
    void incorrectTypeForSimilarityFunction() {
        final var incorrectSimilarityFunction = 123L;
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions())
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
                .hasMessage("Wrong type for vector.similarity_function. Expected STRING, got INTEGER");
    }

    @Test
    void invalidSimilarityFunction() {
        final var invalidSimilarityFunction = "ClearlyThisIsNotASimilarityFunction";
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions())
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
                .withDimensions(VERSION.maxDimensions())
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
                        Neo4jVectorSimilarityFunction.SIMPLE_COSINE,
                        Neo4jVectorSimilarityFunction.SIMPLE_COSINE,
                        Values.utf8Value(Neo4jVectorSimilarityFunction.SIMPLE_COSINE.functionName()));
    }

    @Test
    void cannotSetQuantizationEnabled() {
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions())
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
                .withQuantizationDisabled()
                .toSettingsAccessor();

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        assertThat(validationRecords.get(UNRECOGNIZED_SETTING))
                .hasSize(1)
                .first()
                .asInstanceOf(type(UnrecognizedSetting.class))
                .extracting(NamedSetting::settingName)
                .isEqualTo(QUANTIZATION_ENABLED.getSettingName());
    }

    @Test
    void cannotSetQuantizationType() {
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions())
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
                .withQuantizationType(VectorQuantizationType.NONE)
                .toSettingsAccessor();

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        assertThat(validationRecords.get(UNRECOGNIZED_SETTING))
                .hasSize(1)
                .first()
                .asInstanceOf(type(UnrecognizedSetting.class))
                .extracting(NamedSetting::settingName)
                .isEqualTo(QUANTIZATION_TYPE.getSettingName());
    }

    @Test
    void cannotSetHnswM() {
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions())
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
                .withHnswM(16)
                .toSettingsAccessor();

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        assertThat(validationRecords.get(UNRECOGNIZED_SETTING))
                .hasSize(1)
                .first()
                .asInstanceOf(type(UnrecognizedSetting.class))
                .extracting(NamedSetting::settingName)
                .isEqualTo(HNSW_M.getSettingName());
    }

    @Test
    void cannotSetHnswEfConstruction() {
        final var settings = VectorIndexSettings.create()
                .withDimensions(VERSION.maxDimensions())
                .withSimilarityFunction(VERSION.similarityFunction("COSINE"))
                .withHnswEfConstruction(100)
                .toSettingsAccessor();

        final var validationRecords = VALIDATOR.validate(settings);
        assertThat(validationRecords.invalid()).isTrue();
        assertThat(validationRecords.get(UNRECOGNIZED_SETTING))
                .hasSize(1)
                .first()
                .asInstanceOf(type(UnrecognizedSetting.class))
                .extracting(NamedSetting::settingName)
                .isEqualTo(HNSW_EF_CONSTRUCTION.getSettingName());
    }
}
