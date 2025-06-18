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

import java.net.SocketAddress;
import java.util.Objects;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.connectors.BoltConnector;

public abstract class AbstractExternalConnectorConfiguration extends AbstractNettyConnectorConfiguration
        implements ExternalConnectorConfiguration {

    private final SocketAddress advertisedAddress;
    private final boolean requiresEncryption;

    public AbstractExternalConnectorConfiguration(AbstractFactory<?> builder) {
        super(builder);

        Objects.requireNonNull(builder.advertisedAddress, "Advertised address cannot be null");

        this.advertisedAddress = builder.advertisedAddress;
        this.requiresEncryption = builder.requiresEncryption;
    }

    @Override
    public SocketAddress advertisedAddress() {
        return this.advertisedAddress;
    }

    @Override
    public boolean requireEncryption() {
        return this.requiresEncryption;
    }

    public abstract static class AbstractFactory<SELF extends AbstractFactory<SELF>>
            extends AbstractNettyConnectorConfiguration.AbstractFactory<SELF>
            implements ExternalConnectorConfiguration.Factory<SELF> {

        private SocketAddress advertisedAddress;
        private boolean requiresEncryption;

        @Override
        public abstract AbstractExternalConnectorConfiguration build();

        @Override
        public SELF fromConfig(Config config) {
            super.fromConfig(config);

            this.advertisedAddress =
                    config.get(BoltConnector.advertised_address).socketAddress();

            return (SELF) this;
        }

        @Override
        public SELF advertisedAddress(SocketAddress value) {
            this.advertisedAddress = value;
            return (SELF) this;
        }

        @Override
        public SELF requireEncryption(boolean value) {
            this.requiresEncryption = value;
            return (SELF) this;
        }
    }
}
