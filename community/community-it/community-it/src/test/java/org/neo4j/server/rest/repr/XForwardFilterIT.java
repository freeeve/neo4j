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
package org.neo4j.server.rest.repr;

import static java.net.http.HttpClient.newHttpClient;
import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.server.helpers.CommunityWebContainerBuilder;
import org.neo4j.server.helpers.FunctionalTestHelper;
import org.neo4j.server.helpers.TestWebContainer;
import org.neo4j.server.rest.domain.GraphDbHelper;
import org.neo4j.test.server.ExclusiveWebContainerTestBase;

public class XForwardFilterIT extends ExclusiveWebContainerTestBase {
    private static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    private static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";

    private TestWebContainer testWebContainer;
    private GraphDbHelper helper;

    @BeforeEach
    public void setupServer() throws IOException {
        // Configure Neo4j with X-Forward security settings enabled for testing
        testWebContainer = CommunityWebContainerBuilder.serverOnRandomPorts()
                .withProperty(ServerSettings.http_x_forward_enabled.name(), "true")
                .withProperty(
                        ServerSettings.http_x_forward_allow_hosts.name(),
                        "jimwebber.org,kathwebber.com,neo4j.org,good-host")
                .build();

        helper = new FunctionalTestHelper(testWebContainer).getGraphDbHelper();
        helper.createRelationship("RELATES_TO", helper.createNode(), helper.createNode());
    }

    @AfterEach
    public void cleanup() {
        if (testWebContainer != null) {
            testWebContainer.shutdown();
        }
    }

    @Test
    public void shouldUseXForwardedHostHeaderWhenPresent() throws Exception {
        var entity = sendGetRequest(X_FORWARDED_HOST, "jimwebber.org");

        assertTrue(entity.contains("http://jimwebber.org"));
        assertFalse(entity.contains("http://localhost"));
    }

    @Test
    public void shouldUseXForwardedProtoHeaderWhenPresent() throws Exception {
        var entity = sendGetRequest(X_FORWARDED_PROTO, "https");

        assertTrue(entity.contains("https://localhost"));
        assertFalse(entity.contains("http://localhost"));
    }

    @Test
    public void shouldPickFirstXForwardedHostHeaderValueFromCommaOrCommaAndSpaceSeparatedList() throws Exception {
        var entity = sendGetRequest(X_FORWARDED_HOST, "jimwebber.org, kathwebber.com,neo4j.org");

        assertTrue(entity.contains("http://jimwebber.org"));
        assertFalse(entity.contains("http://localhost"));
    }

    @Test
    public void shouldUseBaseUriOnBadXForwardedHostHeader() throws Exception {
        var entity = sendGetRequest(X_FORWARDED_HOST, ":bad_URI");

        assertTrue(entity.contains(serverUriString()));
    }

    @Test
    public void shouldUseBaseUriIfFirstAddressInXForwardedHostHeaderIsBad() throws Exception {
        var entity = sendGetRequest(X_FORWARDED_HOST, ":bad_URI,good-host");

        assertTrue(entity.contains(serverUriString()));
    }

    @Test
    public void shouldUseBaseUriOnBadXForwardedProtoHeader() throws Exception {
        var entity = sendGetRequest(X_FORWARDED_PROTO, "%%%DEFINITELY-NOT-A-PROTO!");

        assertTrue(entity.contains(serverUriString()));
    }

    @Test
    public void shouldUseXForwardedHostAndXForwardedProtoHeadersWhenPresent() throws Exception {
        var entity = sendGetRequest(X_FORWARDED_HOST, "jimwebber.org", X_FORWARDED_PROTO, "https");

        assertTrue(entity.contains("https://jimwebber.org"));
        assertFalse(entity.contains(serverUriString()));
    }

    @Test
    public void shouldUseXForwardedHostAndXForwardedProtoHeadersInCypherResponseRepresentations() throws Exception {
        String jsonString = "{\"statements\" : [{ \"statement\": \"MATCH (n) RETURN n\", "
                + "\"resultDataContents\":[\"REST\"] }] }";

        var entity = sendPostRequest(
                txUri(), jsonString, X_FORWARDED_HOST, "jimwebber.org:2354", X_FORWARDED_PROTO, "https");

        assertTrue(entity.contains("https://jimwebber.org:2354"));
        assertFalse(entity.contains(serverUriString()));
    }

    private String sendGetRequest(String... headers) throws Exception {
        var request = HttpRequest.newBuilder(serverUri())
                .header(ACCEPT, APPLICATION_JSON)
                .headers(headers)
                .GET()
                .build();

        return newHttpClient().send(request, ofString()).body();
    }

    private String sendPostRequest(String uri, String payload, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(uri))
                .header(ACCEPT, APPLICATION_JSON)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .headers(headers)
                .POST(ofString(payload))
                .build();

        return newHttpClient().send(request, ofString()).body();
    }

    private String serverUriString() {
        return serverUri().toString();
    }

    private URI serverUri() {
        return testWebContainer.getBaseUri();
    }

    private String txUri() {
        return testWebContainer.getBaseUri().resolve("db/neo4j/tx").toString();
    }
}
