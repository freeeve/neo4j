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
package org.neo4j.bolt.discovery.handler.codec;

import io.netty.channel.ChannelHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.EncoderException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.neo4j.bolt.discovery.packet.DiscoveryConstants;
import org.neo4j.bolt.discovery.packet.Packet;
import org.neo4j.bolt.discovery.packet.PacketEnvelope;
import org.neo4j.bolt.discovery.packet.error.NoSuchPacketWriterException;
import org.neo4j.bolt.discovery.packet.error.PacketWriterException;
import org.neo4j.bolt.discovery.packet.registry.PacketRegistry;
import org.neo4j.bolt.discovery.packet.writer.PacketWriter;
import org.neo4j.bolt.testing.annotation.StrictBufferExtension;
import org.neo4j.bolt.testing.assertions.ByteBufAssertions;
import org.neo4j.bolt.testing.channel.StrictBufferContext;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.AssertableLogProvider.Level;
import org.neo4j.logging.LogAssertions;
import org.neo4j.logging.internal.SimpleLogService;

@StrictBufferExtension
class PacketEncoderTest {

    private static final InetSocketAddress RECIPIENT_ADDR;

    private PacketRegistry registry;
    private AssertableLogProvider logProvider;

    private ChannelHandler encoder;

    static {
        try {
            RECIPIENT_ADDR = new InetSocketAddress(InetAddress.getByName("192.168.0.3"), 8687);
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
    }

    @BeforeEach
    void prepare() {
        this.registry = Mockito.mock(PacketRegistry.class);
        this.logProvider = new AssertableLogProvider();

        this.encoder = new PacketEncoder(this.registry, new SimpleLogService(this.logProvider));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldEncodeMessages(StrictBufferContext ctx) throws PacketWriterException {
        var channel = ctx.channel(this.encoder);

        var writer = Mockito.mock(PacketWriter.class);
        Mockito.when(this.registry.findWriter(TestPacket.class)).thenReturn(Optional.of(writer));

        var envelope = new PacketEnvelope<>(
                RECIPIENT_ADDR,
                DiscoveryConstants.MAGIC_NUMBER,
                DiscoveryConstants.LATEST_VERSION,
                (short) 0x01,
                new TestPacket());
        channel.writeOutbound(envelope);

        var packet = channel.<DatagramPacket>readOutbound();

        Assertions.assertThat(packet).isNotNull().hasFieldOrPropertyWithValue("recipient", RECIPIENT_ADDR);

        var content = ctx.output(packet.content());

        ByteBufAssertions.assertThat(content)
                .hasReadableBytes(6)
                .containsInt(DiscoveryConstants.MAGIC_NUMBER)
                .containsByte(0x01) // version
                .containsByte(0x01) // opcode
                .hasNoRemainingReadableBytes();

        Mockito.verify(this.registry).findWriter(TestPacket.class);
        Mockito.verify(writer).writeTo(Mockito.any(), Mockito.same(envelope.content()));

        LogAssertions.assertThat(this.logProvider)
                .forLevel(Level.DEBUG)
                .forClass(PacketEncoder.class)
                .containsMessageWithArguments("<<< %s", envelope);
    }

    @Test
    void shouldRejectUnknownPacketTypes(StrictBufferContext ctx) {
        var channel = ctx.channel(this.encoder);

        Mockito.when(this.registry.findWriter(TestPacket.class)).thenReturn(Optional.empty());

        var envelope = new PacketEnvelope<>(
                RECIPIENT_ADDR,
                DiscoveryConstants.MAGIC_NUMBER,
                DiscoveryConstants.LATEST_VERSION,
                (short) 0x01,
                new TestPacket());

        Assertions.assertThatExceptionOfType(EncoderException.class)
                .isThrownBy(() -> channel.writeOutbound(envelope))
                .withCauseInstanceOf(NoSuchPacketWriterException.class);
    }

    private static final class TestPacket implements Packet {}
}
