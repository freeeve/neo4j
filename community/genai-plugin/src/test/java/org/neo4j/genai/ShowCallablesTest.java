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
package org.neo4j.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.genai.util.GenAITestExtension;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.utils.TestDirectory;

@ImpermanentDbmsExtension(configurationCallback = "configure")
public class ShowCallablesTest implements GenAITestExtension {

    @Inject
    private GraphDatabaseAPI db;

    @Inject
    private TestDirectory testDirectory;

    @ExtensionCallback
    public void configure(TestDatabaseManagementServiceBuilder builder) throws IOException {
        installPlugin(testDirectory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "25"})
    void shouldListAllGenAiFunctions(String cypherVersion) throws IOException {
        final var query = ("""
                CYPHER %s SHOW FUNCTIONS YIELD
                name, category, description, signature, isBuiltIn,
                argumentDescription, returnDescription, aggregating,
                isDeprecated, deprecatedBy
                WHERE name STARTS WITH 'genai' OR name STARTS WITH 'ai'
                RETURN *
                ORDER BY name
                """).formatted(cypherVersion);

        final var result =
                db.executeTransactionally(query, Map.of(), r -> r.stream().toList());
        final var json = new ObjectMapper();
        final var expected = allExpectedCallables(json, "functions", cypherVersion);
        final var actual = json.valueToTree(result);

        // System.out.println(
        //         "Actual:\n" + json.writer().withDefaultPrettyPrinter().writeValueAsString(actual));
        for (int i = 0; i < expected.size(); ++i) assertThat(actual.get(i)).isEqualTo(expected.get(i));
        assertThat(actual).isEqualTo(expected);
    }

    private ArrayNode allExpectedCallables(ObjectMapper json, String callableType, String versionName)
            throws IOException {
        final var versionSpecificPath =
                "/callables/%s/cypher%s/%s.json".formatted(callableType, versionName, callableType);

        final var versionSpecific =
                json.reader().readTree(ShowCallablesTest.class.getResourceAsStream(versionSpecificPath));

        final var result = new ArrayList<JsonNode>(versionSpecific.size());
        for (final var node : versionSpecific) result.add(node);
        result.sort(Comparator.comparing(n -> n.path("name").asText()));
        return json.createArrayNode().addAll(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "25"})
    void shouldListAllGenAiProcedures(String cypherVersion) throws IOException {
        final var query = ("""
                CYPHER %s SHOW PROCEDURES YIELD
                name, description, signature,
                argumentDescription, returnDescription,
                isDeprecated, deprecatedBy
                WHERE name STARTS WITH 'genai' OR name STARTS WITH 'ai'
                RETURN *
                ORDER BY name
                """).formatted(cypherVersion);

        final var result =
                db.executeTransactionally(query, Map.of(), r -> r.stream().toList());
        final var json = new ObjectMapper();
        final var expected = allExpectedCallables(json, "procedures", cypherVersion);
        final var actual = json.valueToTree(result);

        // System.out.println(
        //         "Actual:\n" + json.writer().withDefaultPrettyPrinter().writeValueAsString(actual));
        for (int i = 0; i < expected.size(); ++i) assertThat(actual.get(i)).isEqualTo(expected.get(i));
        assertThat(actual).isEqualTo(expected);
    }
}
