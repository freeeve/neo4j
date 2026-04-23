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
package org.neo4j.commandline.fleetmanagement.util;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public class Utilities {

    public static AtomicBoolean boolWithTimeout(int timeout, Runnable onShutdown) {
        AtomicBoolean running = new AtomicBoolean(true);

        // Interrupt handler with 1s grace time after cancel
        var shutdownListener = new Thread(() -> {
            running.set(false);
            onShutdown.run();
        });
        Runtime.getRuntime().addShutdownHook(shutdownListener);

        new Thread(() -> {
                    try {
                        Thread.sleep(timeout * 1000L);
                    } catch (InterruptedException e) {
                        // Ignore
                    } finally {
                        running.set(false);
                    }
                })
                .start();

        return running;
    }

    public static int readVarInt(ByteBuffer buf) {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            b = buf.get();
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    public static String readString(ByteBuffer buf) {
        int length = readVarInt(buf);
        byte[] bytes = new byte[length];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static PrintStream getOutputStream(Path filename) throws IOException {
        if (filename != null) {
            return new PrintStream(Files.newOutputStream(filename));
        } else {
            return new PrintStream(System.out) {
                @Override
                public void close() {
                    // Do not close System.out
                }
            };
        }
    }
}
