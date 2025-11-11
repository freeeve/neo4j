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

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Base implementation for non GQL standard diagnostic record properties.
 * Those are prefixed with _.
 * @param <T>
 */
public final class NonGqlStandardDiagnosticRecordProperty<T> implements DiagnosticRecordProperty<T> {
    private final String key;
    private final boolean disabled;
    private final Predicate<T> isValueOmitted;
    private final Function<Object, Object> serializeValue;

    private NonGqlStandardDiagnosticRecordProperty(
            String key, Predicate<T> isValueOmitted, Function<Object, Object> serializeValue, boolean disabled) {
        this.key = key;
        this.disabled = disabled;
        this.isValueOmitted = isValueOmitted;
        this.serializeValue = serializeValue;
    }

    @Override
    public boolean isValueOmitted(T value) {
        return this.isValueOmitted.test(value);
    }

    @Override
    public Object serializeValue(Object value) {
        return this.serializeValue.apply(value);
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public boolean disabled() {
        return this.disabled;
    }

    public static class Builder<T> {
        private final String key;
        private boolean disabled;
        private Predicate<T> isValueOmitted;
        private Function<Object, Object> serializeValue;

        private Builder(String key) {
            this.key = key;
            this.isValueOmitted = (ignored) -> false;
            this.serializeValue = Function.identity();
        }

        /**
         * Creates a builder from a give property type T and key.
         * @param key The key from the property. This should start with _ for compliance
         *            with GQL Standards on known standard properties.
         * @return The Builder
         * @param <T> The type of the property.
         * @throws IllegalArgumentException When keys doesn't conform
         */
        public static <T> Builder<T> fromKey(String key) throws IllegalArgumentException {
            if (key == null || key.isBlank() || !key.startsWith("_")) {
                throw new IllegalArgumentException("Invalid key: " + key);
            }
            return new Builder<>(key);
        }

        /**
         * Mark this property as disabled.
         * <p>
         * A disabled property are not used in the {@link DiagnosticRecord} although it is set.
         * @deprecated Since we shouldn't keep disabled properties for long in the codebase.
         */
        public Builder<T> disabled() {
            this.disabled = true;
            return this;
        }

        /**
         * Define a predicated to define if the set value in the property is omitted from the {@link DiagnosticRecord}.
         * @param isValueOmitted The predicated.
         */
        public Builder<T> withValueOmittedPredicate(Predicate<T> isValueOmitted) {
            this.isValueOmitted = isValueOmitted;
            return this;
        }

        /**
         * Defines a function for transforming a high level value into a basic Java value (Map, Integer, List, String,
         * etc.).
         * @param serializeValue the serializer.
         */
        public Builder<T> withValueSerializer(Function<Object, Object> serializeValue) {
            this.serializeValue = serializeValue;
            return this;
        }

        /**
         * Builds the property.
         * @return The Property.
         */
        public NonGqlStandardDiagnosticRecordProperty<T> build() {
            return new NonGqlStandardDiagnosticRecordProperty<>(key, isValueOmitted, serializeValue, disabled);
        }
    }
}
