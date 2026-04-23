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
package org.neo4j.commandline.fleetmanagement.view;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscoveryView {
    public static void startDiscoveryIndicator(AtomicBoolean running, Map discoveredNodes) {
        var indicatorThread = new Thread(() -> {
            String[] spinner = {"|", "/", "-", "\\"};
            int i = 0;
            String format = "\rDiscovered %d Neo4j server%s... %s";
            String cleanLine = "\r" + " ".repeat(format.length() + 4) + "\r";
            while (running.get()) {
                System.out.printf(
                        format,
                        discoveredNodes.size(),
                        discoveredNodes.size() != 1 ? "s" : " ",
                        spinner[i % spinner.length]);
                i++;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.print(cleanLine); // print - Clear the spinner line
        });
        indicatorThread.setDaemon(true);
        indicatorThread.start();
    }
}
