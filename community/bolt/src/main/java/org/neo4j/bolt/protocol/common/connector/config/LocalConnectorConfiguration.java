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
package org.neo4j.bolt.protocol.common.connector.config;

import java.util.function.Consumer;

public final class LocalConnectorConfiguration extends AbstractNettyConnectorConfiguration {

    private LocalConnectorConfiguration(AbstractFactory<?> builder) {
        super(builder);
    }

    public static Factory factory() {
        return new Factory();
    }

    public static LocalConnectorConfiguration newInstance() {
        return factory().build();
    }

    public static LocalConnectorConfiguration create(Consumer<Factory> configurer) {
        var factory = factory();
        configurer.accept(factory);
        return factory.build();
    }

    public static final class Factory extends AbstractNettyConnectorConfiguration.AbstractFactory<Factory> {

        private Factory() {}

        @Override
        public LocalConnectorConfiguration build() {
            return new LocalConnectorConfiguration(this);
        }
    }
}
