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
package org.neo4j.bolt.testing.mock;

import java.net.InetSocketAddress;
import org.neo4j.bolt.protocol.common.connector.config.AbstractExternalConnectorConfiguration;

public final class TestConnectorConfiguration extends AbstractExternalConnectorConfiguration {

    public TestConnectorConfiguration(Factory builder) {
        super(builder);
    }

    public static Factory factory() {
        return new TestConnectorConfiguration.Factory();
    }

    public static final class Factory extends AbstractExternalConnectorConfiguration.AbstractFactory<Factory> {

        private Factory() {
            this.advertisedAddress(new InetSocketAddress("127.0.0.1", 7687));
        }

        @Override
        public TestConnectorConfiguration build() {
            return new TestConnectorConfiguration(this);
        }
    }
}
