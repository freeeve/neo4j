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

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.neo4j.bolt.connection.AuthInfo;
import org.neo4j.bolt.connection.BoltConnection;
import org.neo4j.bolt.connection.BoltConnectionState;
import org.neo4j.bolt.connection.BoltProtocolVersion;
import org.neo4j.bolt.connection.BoltServerAddress;
import org.neo4j.bolt.connection.ResponseHandler;
import org.neo4j.bolt.connection.message.Message;

/**
 * A delegating {@link BoltConnection} responsible for ensuring that read timeouts are not set.
 * @param delegate the {@link BoltConnection} that it delegates to
 */
record QueryApiBoltConnection(BoltConnection delegate) implements BoltConnection {

    /**
     * Ignores the timeout
     * @param duration the duration which will be ignored
     * @return Completed immediately
     */
    @Override
    public CompletionStage<Void> setReadTimeout(Duration duration) {
        return CompletableFuture.completedStage(null);
    }

    /**
     * Default read timeout to empty
     * @return Empty
     */
    @Override
    public Optional<Duration> defaultReadTimeout() {
        return Optional.empty();
    }

    @Override
    public CompletionStage<Void> writeAndFlush(ResponseHandler responseHandler, List<Message> list) {
        return delegate.writeAndFlush(responseHandler, list);
    }

    @Override
    public CompletionStage<Void> write(List<Message> list) {
        return delegate.write(list);
    }

    @Override
    public CompletionStage<Void> forceClose(String s) {
        return delegate.forceClose(s);
    }

    @Override
    public CompletionStage<Void> close() {
        return delegate.close();
    }

    @Override
    public BoltConnectionState state() {
        return delegate.state();
    }

    @Override
    public CompletionStage<AuthInfo> authInfo() {
        return delegate.authInfo();
    }

    @Override
    public String serverAgent() {
        return delegate.serverAgent();
    }

    @Override
    public BoltServerAddress serverAddress() {
        return delegate.serverAddress();
    }

    @Override
    public BoltProtocolVersion protocolVersion() {
        return delegate.protocolVersion();
    }

    @Override
    public boolean telemetrySupported() {
        return delegate.telemetrySupported();
    }

    @Override
    public boolean serverSideRoutingEnabled() {
        return delegate.serverSideRoutingEnabled();
    }
}
