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
package org.neo4j.bolt.protocol.common.connector.connection.listener;

import org.neo4j.bolt.protocol.common.BoltProtocol;
import org.neo4j.bolt.protocol.common.connector.connection.Connection;
import org.neo4j.bolt.protocol.common.handler.AuthenticationProtocolLimiterHandler;
import org.neo4j.internal.kernel.api.security.LoginContext;
import org.neo4j.logging.InternalLog;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.memory.HeapEstimator;
import org.neo4j.packstream.codec.transport.ChunkFrameDecoder;

/**
 * Handles the addition and removal of {@link AuthenticationProtocolLimiterHandler} to/from the channel
 * pipeline.
 */
public class AuthenticationProtocolLimiterConnectionListener implements ConnectionListener {
    public static final long SHALLOW_SIZE =
            HeapEstimator.shallowSizeOfInstance(AuthenticationProtocolLimiterConnectionListener.class);

    private final Connection connection;
    private final InternalLog log;
    private final int structureElementLimit;
    private final int structureDepthLimit;

    private volatile AuthenticationProtocolLimiterHandler protocolLimiterHandler;

    public AuthenticationProtocolLimiterConnectionListener(
            Connection connection, int structureElementLimit, int structureDepthLimit, InternalLogProvider logging) {
        this.connection = connection;
        this.structureElementLimit = structureElementLimit;
        this.structureDepthLimit = structureDepthLimit;
        this.log = logging.getLog(AuthenticationProtocolLimiterConnectionListener.class);
    }

    @Override
    public void onListenerRemoved() {
        this.connection.memoryTracker().releaseHeap(SHALLOW_SIZE);
    }

    @Override
    public void onProtocolSelected(BoltProtocol protocol) {
        this.installStructureLimitHandler();
    }

    @Override
    public void onLogon(LoginContext ctx) {
        log.debug("[%s] Removing authentication protocol limiter handler", this.connection.id());

        if (protocolLimiterHandler != null) {
            var protocolLimiterHandler = this.protocolLimiterHandler;

            this.connection.modifyPipeline(pipeline -> {
                pipeline.remove(protocolLimiterHandler);
            });

            this.protocolLimiterHandler = null;
        }
    }

    @Override
    public void onLogoff() {
        this.installStructureLimitHandler();
    }

    private void installStructureLimitHandler() {
        this.log.debug(
                "[%s] Imposing authentication structure limits of %d elements with a maximum depth of %d",
                this.connection.id(), structureElementLimit, structureDepthLimit);

        connection.memoryTracker().allocateHeap(AuthenticationProtocolLimiterHandler.SHALLOW_SIZE);
        var protocolLimiterHandler =
                new AuthenticationProtocolLimiterHandler(structureElementLimit, structureDepthLimit);
        this.protocolLimiterHandler = protocolLimiterHandler;
        this.connection.modifyPipeline(pipeline -> {
            pipeline.addAfter(ChunkFrameDecoder.NAME, "protocolLimiterHandler", protocolLimiterHandler);
        });
    }
}
