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
package org.neo4j.fleetmanagement.docs;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.neo4j.fleetmanagement.procedures.Documentation;

// Run with: ./mvnw compile exec:java -pl public/community/fleet-management

public class AsciiDocGenerator {
    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new FileOutputStream("data.adoc"));

        Documentation documentation = new Documentation();

        // Group results by message type
        Map<String, java.util.List<Documentation.DocumentationResult>> resultsByType =
                documentation.generateDocumentation().collect(Collectors.groupingBy(result -> result.messageType));

        // Print header
        out.println("= Data transparency");
        out.println(":description: This page documents the payloads used in Fleet Manager messages.\n");
        out.println("This page describes the data structures used in Fleet Manager messages.\n");

        // Print each message type section
        for (Map.Entry<String, java.util.List<Documentation.DocumentationResult>> entry :
                new TreeMap<>(resultsByType).entrySet()) {
            String messageType = entry.getKey();
            java.util.List<Documentation.DocumentationResult> results = entry.getValue();

            // Print message type header
            out.println("== `" + messageType + "`\n");

            // Find and print class description
            results.stream().filter(r -> r.fieldPath.isEmpty()).findFirst().ifPresent(r -> {
                out.println(r.description + "\n");
            });

            // Print fields table
            out.println("[options=\"header\", cols=\"2,1,2\"]");
            out.println("|===");
            out.println("|Field |Type |Description");

            // Print each field
            results.stream().filter(r -> !r.fieldPath.isEmpty()).forEach(r -> {
                out.println("| `" + r.fieldPath + "`");
                out.println("| " + r.fieldType);
                out.println("| " + r.description);
            });

            out.println("|===\n");
        }
    }
}
