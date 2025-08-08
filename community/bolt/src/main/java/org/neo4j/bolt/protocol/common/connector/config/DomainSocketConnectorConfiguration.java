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

import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.function.Consumer;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.BoltConnectorInternalSettings;

public final class DomainSocketConnectorConfiguration extends AbstractNettyConnectorConfiguration {

    private final Set<PosixFilePermission> socketFilePermissionMask;
    private final boolean deleteSocketFile;
    private final boolean permitUserDatabaseAccess;

    private DomainSocketConnectorConfiguration(Factory builder) {
        super(builder);

        this.socketFilePermissionMask = builder.socketFilePermissionMask;
        this.permitUserDatabaseAccess = builder.permitUserDatabaseAccess;
        this.deleteSocketFile = builder.deleteSocketFile;
    }

    public static Factory factory() {
        return new Factory();
    }

    public static DomainSocketConnectorConfiguration newInstance() {
        return factory().build();
    }

    public static DomainSocketConnectorConfiguration create(Consumer<Factory> configurer) {
        var factory = factory();
        configurer.accept(factory);
        return factory.build();
    }

    /**
     * Specifies whether the connector shall attempt to delete an existing socket file upon startup if
     * it exists.
     *
     * @return true if deletion shall be attempted, false otherwise.
     */
    public boolean deleteSocketFile() {
        return this.deleteSocketFile;
    }

    /**
     * Identifies the permission mask which shall be applied to the domain socket file once
     * created.
     * <p/>
     * This value effectively operates as either a secondary or primary authentication factor
     * depending on the server configuration as only users with the correct permissions will be able
     * to interact with the domain socket as a result.
     *
     * @return a Unix file permission mask.
     */
    public Set<PosixFilePermission> socketFilePermissionMask() {
        return socketFilePermissionMask;
    }

    public boolean permitUserDatabaseAccess() {
        return permitUserDatabaseAccess;
    }

    public static final class Factory extends AbstractNettyConnectorConfiguration.AbstractFactory<Factory> {

        private Set<PosixFilePermission> socketFilePermissionMask;
        private boolean deleteSocketFile;
        private boolean permitUserDatabaseAccess;

        private Factory() {}

        @Override
        public DomainSocketConnectorConfiguration build() {
            return new DomainSocketConnectorConfiguration(this);
        }

        @Override
        public Factory fromConfig(Config config) {
            this.socketFilePermissionMask =
                    config.get(BoltConnector.unix_socket_permission_mask).getPosixPermissions();
            this.permitUserDatabaseAccess = config.get(GraphDatabaseInternalSettings.enable_aura_profile)
                    || config.get(BoltConnectorInternalSettings.enable_unix_socket_user_database_access);

            this.deleteSocketFile = config.get(BoltConnector.unix_socket_delete);

            return super.fromConfig(config);
        }

        public Factory deleteSocketFile(boolean deleteSocketFile) {
            this.deleteSocketFile = deleteSocketFile;
            return this;
        }

        public Factory permitUserDatabaseAccess(boolean permitUserDatabaseAccess) {
            this.permitUserDatabaseAccess = permitUserDatabaseAccess;
            return this;
        }

        public Factory socketFilePermissionMask(Set<PosixFilePermission> socketFilePermissionMask) {
            this.socketFilePermissionMask = socketFilePermissionMask;
            return this;
        }
    }
}
