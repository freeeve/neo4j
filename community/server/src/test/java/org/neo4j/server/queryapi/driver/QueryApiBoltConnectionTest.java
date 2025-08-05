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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.bolt.connection.AuthInfo;
import org.neo4j.bolt.connection.BoltConnection;
import org.neo4j.bolt.connection.BoltConnectionState;
import org.neo4j.bolt.connection.BoltProtocolVersion;
import org.neo4j.bolt.connection.BoltServerAddress;
import org.neo4j.bolt.connection.ResponseHandler;
import org.neo4j.bolt.connection.message.Message;

@ExtendWith(MockitoExtension.class)
class QueryApiBoltConnectionTest {

    @Mock
    private BoltConnection delegate;

    @InjectMocks
    private QueryApiBoltConnection boltConnection;

    @Test
    void setReadTimeout() throws ExecutionException, InterruptedException {
        var result = boltConnection.setReadTimeout(Duration.ofDays(3));

        // Then
        Assertions.assertEquals(null, result.toCompletableFuture().get());
        Mockito.verify(delegate, Mockito.never()).setReadTimeout(Mockito.any());
    }

    @Test
    void defaultReadTimeout() {
        var result = boltConnection.defaultReadTimeout();

        // Then
        Assertions.assertTrue(result.isEmpty());
        Mockito.verify(delegate, Mockito.never()).defaultReadTimeout();
    }

    @Test
    void writeAndFlush() {
        // Given
        var handler = Mockito.mock(ResponseHandler.class);
        List<Message> messages = List.of();
        var completionStage = CompletableFuture.completedStage(null);

        Mockito.doReturn(completionStage).when(delegate).writeAndFlush(handler, messages);

        // Do
        var result = boltConnection.writeAndFlush(handler, messages);

        // Then
        Assertions.assertSame(completionStage, result);
        Mockito.verify(delegate, Mockito.only()).writeAndFlush(handler, messages);
    }

    @Test
    void write() {
        // Given
        List<Message> messages = List.of();
        var completionStage = CompletableFuture.completedStage(null);

        Mockito.doReturn(completionStage).when(delegate).write(messages);

        // Do
        var result = boltConnection.write(messages);

        // Then
        Assertions.assertSame(completionStage, result);
        Mockito.verify(delegate, Mockito.only()).write(messages);
    }

    @Test
    void forceClose() {
        // Given
        var s = "Some reason";
        var completionStage = CompletableFuture.completedStage(null);

        Mockito.doReturn(completionStage).when(delegate).forceClose(s);

        // Do
        var result = boltConnection.forceClose(s);

        // Then
        Assertions.assertSame(completionStage, result);
        Mockito.verify(delegate, Mockito.only()).forceClose(s);
    }

    @Test
    void close() {
        // Given
        var completionStage = CompletableFuture.completedStage(null);

        Mockito.doReturn(completionStage).when(delegate).close();

        // Do
        var result = boltConnection.close();

        // Then
        Assertions.assertSame(completionStage, result);
        Mockito.verify(delegate, Mockito.only()).close();
    }

    @Test
    void state() {
        // Given
        var expected = BoltConnectionState.OPEN;

        Mockito.doReturn(expected).when(delegate).state();

        // Do
        var result = boltConnection.state();

        // Then
        Assertions.assertSame(expected, result);
        Mockito.verify(delegate, Mockito.only()).state();
    }

    @Test
    void authInfo() {
        // Given
        var authInfo = Mockito.mock(AuthInfo.class);
        var expected = CompletableFuture.completedStage(authInfo);

        Mockito.doReturn(expected).when(delegate).authInfo();

        // Do
        var result = boltConnection.authInfo();

        // Then
        Assertions.assertSame(expected, result);
        Mockito.verify(delegate, Mockito.only()).authInfo();
    }

    @Test
    void serverAgent() {
        // Given
        var expected = "Agent";

        Mockito.doReturn(expected).when(delegate).serverAgent();

        // Do
        var result = boltConnection.serverAgent();

        // Then
        Assertions.assertSame(expected, result);
        Mockito.verify(delegate, Mockito.only()).serverAgent();
    }

    @Test
    void serverAddress() {
        // Given
        var expected = Mockito.mock(BoltServerAddress.class);

        Mockito.doReturn(expected).when(delegate).serverAddress();

        // Do
        var result = boltConnection.serverAddress();

        // Then
        Assertions.assertSame(expected, result);
        Mockito.verify(delegate, Mockito.only()).serverAddress();
    }

    @Test
    void protocolVersion() {
        // Given
        var expected = Mockito.mock(BoltProtocolVersion.class);

        Mockito.doReturn(expected).when(delegate).protocolVersion();

        // Do
        var result = boltConnection.protocolVersion();

        // Then
        Assertions.assertSame(expected, result);
        Mockito.verify(delegate, Mockito.only()).protocolVersion();
    }

    @Test
    void telemetrySupported() {
        // Given
        var expected = false;

        Mockito.doReturn(expected).when(delegate).telemetrySupported();

        // Do
        var result = boltConnection.telemetrySupported();

        // Then
        Assertions.assertSame(expected, result);
        Mockito.verify(delegate, Mockito.only()).telemetrySupported();
    }

    @Test
    void serverSideRoutingEnabled() {
        // Given
        var expected = true;

        Mockito.doReturn(expected).when(delegate).serverSideRoutingEnabled();

        // Do
        var result = boltConnection.serverSideRoutingEnabled();

        // Then
        Assertions.assertSame(expected, result);
        Mockito.verify(delegate, Mockito.only()).serverSideRoutingEnabled();
    }

    @Test
    void delegate() {
        Assertions.assertSame(delegate, boltConnection.delegate());
    }
}
