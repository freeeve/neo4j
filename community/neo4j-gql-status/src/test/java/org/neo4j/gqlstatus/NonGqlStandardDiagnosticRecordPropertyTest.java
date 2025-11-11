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
package org.neo4j.gqlstatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NonGqlStandardDiagnosticRecordPropertyTest {

    @Nested
    class Builder {
        @ParameterizedTest
        @ValueSource(
                strings = {
                    "_key",
                    "_other_key",
                    "__double_underscore_key",
                    "_CAPS-KEY",
                    "_snakeCaseKey",
                    "_strangeFoRmaTedKey?",
                    "_key with spaces, right?"
                })
        void shouldBuildPropertyStartingWithUnderscore(String key) {
            var property =
                    NonGqlStandardDiagnosticRecordProperty.Builder.fromKey(key).build();
            var value = "Value";

            assertEquals(key, property.key());
            assertFalse(property.disabled());
            assertSame(value, property.serializeValue(value));
            assertFalse(property.isValueOmitted(value));
            assertFalse(property.defaultEntry().isPresent());
            assertFalse(property.defaultValue().isPresent());
        }

        @ParameterizedTest
        @ValueSource(strings = {"key", " __double_underscore_key"})
        void shouldFailToBuildPropertyKeyNotStartingWithUnderscore(String key) {
            assertThrows(
                    IllegalArgumentException.class, () -> NonGqlStandardDiagnosticRecordProperty.Builder.fromKey(key));
        }

        @Test
        void shouldBuildDisabledKeyProperty() {
            var property = NonGqlStandardDiagnosticRecordProperty.Builder.fromKey("_disabled_key")
                    .disabled()
                    .build();

            assertTrue(property.disabled());
        }

        @Test
        void shouldBuildWithOmittedValueProperty() {
            var property = NonGqlStandardDiagnosticRecordProperty.Builder.fromKey("_custom_key")
                    .withValueOmittedPredicate(value -> value.equals("omitted"))
                    .build();
            var value = "Value";
            var omittedValue = "omitted";

            assertFalse(property.isValueOmitted(value));
            assertTrue(property.isValueOmitted(omittedValue));
        }

        @Test
        void shouldBuildWithSerializedValueProperty() {
            var property = NonGqlStandardDiagnosticRecordProperty.Builder.fromKey("_custom_key")
                    .withValueSerializer(value -> "Value: ".concat(value.toString()))
                    .build();

            var value = "the value";

            assertEquals("Value: the value", property.serializeValue(value));
        }
    }
}
