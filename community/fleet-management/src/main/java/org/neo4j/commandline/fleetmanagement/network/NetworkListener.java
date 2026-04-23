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
package org.neo4j.commandline.fleetmanagement.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.neo4j.bolt.discovery.packet.DiscoveryConstants;
import org.neo4j.bolt.discovery.packet.beacon.DiscoveryBeaconSignal;
import org.neo4j.commandline.fleetmanagement.model.Server;
import org.neo4j.commandline.fleetmanagement.util.Utilities;

public class NetworkListener {
    private final int port;

    public NetworkListener(int port) {
        this.port = port;
    }

    public void listen(AtomicBoolean running, Map<String, Server> discoveredNodes) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            socket.setSoTimeout(1000); // 1-second timeout for each receive attempt
            byte[] buffer = new byte[4096];
            while (running.get()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    Server server = decodePacket(packet);
                    if (server != null) {
                        discoveredNodes.put(server.getNodeId(), server);
                    }
                } catch (SocketTimeoutException e) {
                    // Just check the time and try again
                }
            }
        } catch (Exception e) {
            System.err.println("\nError: " + e.getMessage());
            System.exit(1);
        } finally {
            running.set(false);
        }
    }

    private Server decodePacket(DatagramPacket packet) {
        ByteBuffer buf = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());

        // 1. Magic Number
        int magic = buf.getInt();
        if (magic != DiscoveryConstants.MAGIC_NUMBER) {
            return null; // Not a discovery packet we care about
        }

        // 2. Version (VarInt)
        int version = Utilities.readVarInt(buf);
        if (version > DiscoveryConstants.LATEST_VERSION) {
            System.out.printf(
                    "Received discovery packet with unsupported version: <%d>, latest known version is <%d>. This version of neo4j-admin may be out of date, will ignore this packet.\n",
                    version, DiscoveryConstants.LATEST_VERSION);
            return null;
        }

        // 3. Opcode (Byte)
        byte opcode = buf.get();

        if (opcode == DiscoveryBeaconSignal.OPCODE) {
            return new Server(
                    Utilities.readString(buf), // dbmsId
                    Utilities.readString(buf), // nodeId
                    Utilities.readString(buf), // productVersion
                    Utilities.readString(buf), // edition
                    Utilities.readString(buf) // advertisedAddress
                    );
        } else {
            System.out.println("Received unknown opcode: " + opcode + " from " + packet.getSocketAddress());
            return null;
        }
    }
}
