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
package org.neo4j.genai.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import inet.ipaddr.IPAddressString;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.genai.util.GenAITestExtension;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.ExtensionCallback;
import org.neo4j.test.extension.ImpermanentDbmsExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.utils.TestDirectory;

@ImpermanentDbmsExtension(configurationCallback = "configure")
public class GenAiPluginSecurityIT implements GenAITestExtension {
    @Inject
    private GraphDatabaseAPI db;

    @Inject
    TestDirectory testDirectory;

    @ExtensionCallback
    public void configure(TestDatabaseManagementServiceBuilder builder) throws IOException {
        installPlugin(testDirectory);
        // Block all ip addresses
        builder.setConfig(
                GraphDatabaseInternalSettings.cypher_ip_blocklist,
                List.of(new IPAddressString("0.0.0.0/0"), new IPAddressString("::/0")));
    }

    // Note, this test will make DNS requests for external hosts.
    // Would be nice if the base url could be configurable in tests to have full control.
    @Test
    void urlAccessCheck() {
        final var queries = List.of(
                "RETURN genai.vector.encode('', 'OpenAI', {token: '-'})",
                "CALL genai.vector.encodeBatch([''], 'OpenAI', {token: '-'})",
                "RETURN genai.vector.encode('', 'AzureOpenAI', {token: '-', resource: 'xx', deployment: 'xx'})",
                "CALL genai.vector.encodeBatch([''], 'AzureOpenAI', {token: '-', resource: 'xx', deployment: 'xx'})",
                "RETURN genai.vector.encode('', 'Bedrock', {accessKeyId: '-', secretAccessKey: '-'})",
                "CALL genai.vector.encodeBatch([''], 'Bedrock', {accessKeyId: '-', secretAccessKey: '-'})",
                "RETURN genai.vector.encode('', 'VertexAI', {token: '-', projectId: '-', model: 'text-embedding-005'})",
                "CALL genai.vector.encodeBatch([''], 'VertexAI', {token: '-', projectId: '-', model: 'text-embedding-005'})");
        final var expectedMessage = Pattern.compile(
                "Failed to invoke .*: Caused by: org.neo4j.graphdb.security.URLAccessValidationError: access to .* is blocked via the configuration property internal.dbms.cypher_ip_blocklist");
        for (final var query : queries) {
            assertThatThrownBy(() -> db.executeTransactionally(
                            query, Map.of(), r -> r.stream().toList()))
                    .describedAs("Query: %s", query)
                    .hasMessageMatching(expectedMessage);
        }
    }
}
