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
package org.neo4j.bolt.discovery.packet.writer;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.neo4j.bolt.discovery.packet.beacon.DiscoveryBeaconSignal;
import org.neo4j.bolt.discovery.packet.error.PacketWriterException;
import org.neo4j.bolt.testing.annotation.StrictBufferExtension;
import org.neo4j.bolt.testing.assertions.ByteBufAssertions;
import org.neo4j.bolt.testing.channel.StrictBufferContext;

@StrictBufferExtension
class DiscoveryBeaconSignalWriterTest {

    public static final String DBMS_ID = "abcdef023451225";
    public static final String NODE_ID = "neo-west-1";
    public static final String PRODUCT_VERSION = "2026.01";
    public static final String EDITION = "community";
    public static final String ADVERTISEMENT_ADDRESS = "10.0.1.3:7687";

    @Test
    void shouldWriteBeaconSignal(StrictBufferContext ctx) throws PacketWriterException {
        var out = ctx.outputBuffer();

        DiscoveryBeaconSignalWriter writer = new DiscoveryBeaconSignalWriter();
        writer.writeTo(
                out, new DiscoveryBeaconSignal(DBMS_ID, NODE_ID, PRODUCT_VERSION, EDITION, ADVERTISEMENT_ADDRESS));

        ByteBufAssertions.assertThat(out)
                .isNotNull()
                .hasReadableBytes(50)
                .containsByte(15)
                .containsBytes(DBMS_ID.getBytes(StandardCharsets.UTF_8))
                .containsByte(10)
                .containsBytes(NODE_ID.getBytes(StandardCharsets.UTF_8))
                .containsByte(7)
                .containsBytes(PRODUCT_VERSION.getBytes(StandardCharsets.UTF_8))
                .containsByte(9)
                .containsBytes(EDITION.getBytes(StandardCharsets.UTF_8))
                .containsByte(13)
                .containsBytes(ADVERTISEMENT_ADDRESS.getBytes(StandardCharsets.UTF_8))
                .hasNoRemainingReadableBytes();
    }
}
