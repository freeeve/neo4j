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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.neo4j.bolt.connection.AuthToken;
import org.neo4j.bolt.connection.BoltAgent;
import org.neo4j.bolt.connection.BoltConnection;
import org.neo4j.bolt.connection.BoltConnectionProvider;
import org.neo4j.bolt.connection.BoltProtocolVersion;
import org.neo4j.bolt.connection.NotificationConfig;
import org.neo4j.bolt.connection.SecurityPlan;

/**
 * A delegating {@link BoltConnectionProvider} responsible for ensuring that 'neo4j' scheme is used, this makes sure
 * that routing context is used.
 * @param delegate the {@link BoltConnectionProvider} that it delegates to
 */
record QueryApiBoltConnectionProvider(BoltConnectionProvider delegate) implements BoltConnectionProvider {
    @Override
    public CompletionStage<BoltConnection> connect(
            URI uri,
            String routingContextAddress,
            BoltAgent boltAgent,
            String userAgent,
            int connectTimeoutMillis,
            SecurityPlan securityPlan,
            AuthToken authToken,
            BoltProtocolVersion minVersion,
            NotificationConfig notificationConfig) {
        try {
            uri = new URI(
                    "neo4j",
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment());
        } catch (URISyntaxException e) {
            return CompletableFuture.failedStage(e);
        }
        return delegate.connect(
                uri,
                routingContextAddress,
                boltAgent,
                userAgent,
                connectTimeoutMillis,
                securityPlan,
                authToken,
                minVersion,
                notificationConfig);
    }

    @Override
    public CompletionStage<Void> close() {
        return delegate.close();
    }
}
