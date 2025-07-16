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
package org.neo4j.server.queryapi.driver;

import java.util.Map;
import org.neo4j.bolt.connection.BoltConnectionProvider;
import org.neo4j.bolt.connection.BoltConnectionProviderFactory;
import org.neo4j.bolt.connection.LoggingProvider;
import org.neo4j.bolt.connection.MetricsListener;
import org.neo4j.bolt.connection.netty.NettyBoltConnectionProviderFactory;
import org.neo4j.bolt.connection.values.ValueFactory;

public final class QueryApiBoltConnectionProviderFactory implements BoltConnectionProviderFactory {
    public static final String SCHEME = "queryapi";

    private static final BoltConnectionProviderFactory DELEGATE = new NettyBoltConnectionProviderFactory();

    @Override
    public boolean supports(String scheme) {
        return SCHEME.equals(scheme);
    }

    @Override
    public BoltConnectionProvider create(
            LoggingProvider loggingProvider,
            ValueFactory valueFactory,
            MetricsListener metricsListener,
            Map<String, ?> additionalConfig) {
        var delegate = DELEGATE.create(loggingProvider, valueFactory, metricsListener, additionalConfig);
        return new QueryApiBoltConnectionProvider(delegate);
    }
}
