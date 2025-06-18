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
import org.neo4j.configuration.Config;
import org.neo4j.configuration.connectors.BoltConnectorInternalSettings;
import org.neo4j.util.Preconditions;

public final class SocketConnectorConfiguration extends AbstractExternalConnectorConfiguration {

    private final boolean enableTcpKeepAlive;
    private final boolean enableTcpFastOpen;
    private final int tcpFastOpenMaxPendingConnections;

    private SocketConnectorConfiguration(Factory builder) {
        super(builder);

        this.enableTcpKeepAlive = builder.enableTcpKeepAlive;
        this.enableTcpFastOpen = builder.enableTcpFastOpen;
        this.tcpFastOpenMaxPendingConnections = builder.tcpFastOpenMaxPendingConnections;
    }

    public static Factory factory() {
        return new Factory();
    }

    public static SocketConnectorConfiguration newInstance() {
        return new SocketConnectorConfiguration(factory());
    }

    public static SocketConnectorConfiguration create(Consumer<Factory> configurer) {
        var factory = factory();
        configurer.accept(factory);
        return factory.build();
    }

    /**
     * Indicates whether TCP keep-alives should be enabled on connections within the scope of this
     * connector.
     *
     * @return true, if TCP keep-alive shall be enabled, false otherwise.
     */
    public boolean enableTcpKeepAlive() {
        return this.enableTcpKeepAlive;
    }

    /**
     * Indicates whether TCP fast open should be enabled on the server socket within the scope of this
     * connector.
     *
     * @return true if TCP fast open shall be enabled, false otherwise.
     */
    public boolean enableTcpFastOpen() {
        return this.enableTcpFastOpen;
    }

    /**
     * Indicates the maximum number of TCP fast open pending connections within the scope of this
     * connector.
     *
     * @return a maximum number of pending connections.
     */
    public int tcpFastOpenMaxPendingConnections() {
        return this.tcpFastOpenMaxPendingConnections;
    }

    public static final class Factory extends AbstractExternalConnectorConfiguration.AbstractFactory<Factory> {

        private boolean enableTcpKeepAlive = true;
        private boolean enableTcpFastOpen = false;
        private int tcpFastOpenMaxPendingConnections = 128;

        private Factory() {}

        @Override
        public SocketConnectorConfiguration build() {
            return new SocketConnectorConfiguration(this);
        }

        @Override
        public Factory fromConfig(Config config) {
            super.fromConfig(config);

            this.enableTcpKeepAlive = config.get(BoltConnectorInternalSettings.tcp_keep_alive);
            this.enableTcpFastOpen = config.get(BoltConnectorInternalSettings.tcp_fast_open);
            this.tcpFastOpenMaxPendingConnections =
                    config.get(BoltConnectorInternalSettings.tcp_fast_open_max_pending_connections);

            return this;
        }

        public Factory enableTcpKeepAlive(boolean value) {
            this.enableTcpKeepAlive = value;
            return this;
        }

        public Factory enableTcpFastOpen(boolean value) {
            this.enableTcpFastOpen = value;
            return this;
        }

        public Factory tcpFastOpenMaxPendingConnections(int value) {
            Preconditions.requirePositive(value);
            this.tcpFastOpenMaxPendingConnections = value;
            return this;
        }
    }
}
