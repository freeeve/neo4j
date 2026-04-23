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
package org.neo4j.commandline.fleetmanagement.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;
import org.neo4j.commandline.fleetmanagement.model.Dbmss;
import org.neo4j.commandline.fleetmanagement.model.RegistrationData;

public class Pretty implements IFormat {
    @Override
    public void printDiscoveredDbmss(Dbmss discoveredDbmss, PrintStream out) {
        if (discoveredDbmss.isEmpty()) {
            out.println("No Neo4j servers discovered.");
            return;
        }

        var serverCount = discoveredDbmss.serverCount();
        out.println("Discovered " + serverCount + " Neo4j server" + (serverCount != 1 ? "s" : ""));
        discoveredDbmss.forEach((dbmsId, dbms) -> {
            out.println();
            out.printf("  DBMS ID:    %s%n", dbmsId);
            var addresses = dbms.getAddresses();
            out.printf("  Address%s  %s%n", addresses.size() != 1 ? "es:" : ":  ", String.join(",", addresses));
            dbms.getServers().forEach(server -> {
                out.println();
                out.printf("    Server ID:    %s%n", server.getNodeId());
                out.printf("    Version:      %s%n", server.getProductVersion());
                out.printf("    Edition:      %s%n", server.getEdition());
                out.printf("    Address:      %s%n", server.getAdvertisedAddress());
            });
        });

        out.println();
        out.println(
                "To add discovered servers with Fleet Manager, log in or sign up to Neo4j Aura and open the dialog to add deployments from Neo4j Admin.");
        out.println("Please see the documentation at: https://neo4j.com/docs/aura/fleet-management/setup/");
        out.println("When prompted, paste the following code into the dialog:");
        out.println();

        var registrationData = discoveredDbmss.entrySet().stream()
                .map(entry -> new RegistrationData(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().getAddresses()))
                .collect(Collectors.toList());
        try {
            var json = new ObjectMapper().writeValueAsString(registrationData);
            var encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            out.println(encoded);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize discovery results", e);
        }
    }
}
