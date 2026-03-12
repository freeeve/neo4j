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
package org.neo4j.bolt.discovery.packet.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.neo4j.bolt.discovery.packet.Packet;
import org.neo4j.bolt.discovery.packet.writer.PacketWriter;

final class ImmutablePacketRegistry implements PacketRegistry {

    private final Map<Class<? extends Packet>, PacketWriter<?>> writers;

    private ImmutablePacketRegistry(Map<Class<? extends Packet>, PacketWriter<?>> writers) {
        this.writers = writers;
    }

    public static FactoryImpl factory() {
        return new FactoryImpl();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <P extends Packet> Optional<PacketWriter<P>> findWriter(Class<P> type) {
        return Optional.ofNullable((PacketWriter<P>) this.writers.get(type));
    }

    static class FactoryImpl implements Factory {

        private final Map<Class<? extends Packet>, PacketWriter<?>> writers = new HashMap<>();

        @Override
        public PacketRegistry build() {
            var writers = new HashMap<>(this.writers);

            return new ImmutablePacketRegistry(writers);
        }

        @Override
        public <P extends Packet> Factory withWriter(Class<P> type, PacketWriter<? extends P> writer) {
            this.writers.put(type, writer);
            return this;
        }
    }
}
