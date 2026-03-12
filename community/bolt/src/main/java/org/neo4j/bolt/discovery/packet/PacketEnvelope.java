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
package org.neo4j.bolt.discovery.packet;

import java.net.InetSocketAddress;
import java.util.Objects;

public final class PacketEnvelope<P extends Packet> {

    private final InetSocketAddress recipient;
    private final InetSocketAddress sender;

    private final int magicNumber;
    private final int version;
    private final short opcode;

    private final P content;

    public PacketEnvelope(
            InetSocketAddress recipient,
            InetSocketAddress sender,
            int magicNumber,
            int version,
            short opcode,
            P content) {
        this.recipient = recipient;
        this.sender = sender;

        this.magicNumber = magicNumber;
        this.version = version;
        this.opcode = opcode;

        this.content = content;
    }

    public PacketEnvelope(InetSocketAddress recipient, int magicNumber, int version, short opcode, P content) {
        this(recipient, null, magicNumber, version, opcode, content);
    }

    public P content() {
        return this.content;
    }

    public InetSocketAddress sender() {
        return this.sender;
    }

    public InetSocketAddress recipient() {
        return this.recipient;
    }

    public int magicNumber() {
        return this.magicNumber;
    }

    public int version() {
        return this.version;
    }

    public short opcode() {
        return this.opcode;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PacketEnvelope that)) {
            return false;
        }
        return Objects.equals(recipient, that.recipient)
                && Objects.equals(sender, that.sender)
                && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipient, sender, content);
    }

    @Override
    public String toString() {
        if (this.sender == null) {
            return "PacketEnvelope{" + "recipient=" + recipient + ", content=" + content + '}';
        }

        return "PacketEnvelope{" + "recipient=" + recipient + ", sender=" + sender + ", content=" + content + '}';
    }
}
