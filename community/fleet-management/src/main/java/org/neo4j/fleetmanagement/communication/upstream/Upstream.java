package org.neo4j.fleetmanagement.communication.upstream;

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

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.URL;
import java.util.Date;
import java.util.Objects;
import org.neo4j.fleetmanagement.communication.Helpers;
import org.neo4j.fleetmanagement.configuration.State;
import org.neo4j.fleetmanagement.topology.TopologyMapper;
import org.neo4j.fleetmanagement.transactions.ITransactor;
import org.neo4j.fleetmanagement.utils.TokenUtils;

public class Upstream {
    public enum Endpoint {
        REPORTING,
        METRICS,
        CONNECT,
        CONFIG,
        PING
    }

    private static long maxTokenAge = 59 * 60 * 1000L; // 59 minutes

    private final String baseUrl;

    private final ITransactor transactor;
    private ApiKeyProvider.ApiKey apiKey;
    private ApiTokenGenerator apiTokenGenerator;
    private long generatedAt;

    private volatile String apiToken;

    public Upstream(ITransactor transactor) {
        String customBaseUrl = System.getenv("FLEET_MANAGEMENT_API_BASE_URL");
        this.baseUrl = Objects.requireNonNullElse(customBaseUrl, "https://fleet-management-api.neo4j.io/api/v1");

        this.transactor = transactor;
    }

    public void setToken(String token) throws IOException {
        if (token == null || token.isEmpty()) {
            apiToken = null;
        } else {
            var apiKey = TokenUtils.parseToken(token);
            ApiKeyProvider apiKeyProvider = new ApiKeyProvider(apiKey);
            this.apiTokenGenerator = new ApiTokenGenerator(apiKeyProvider);
            this.apiKey = apiKey;
            generateToken();
        }
    }

    private String getToken() {
        if (State.getInstance().isActive()
                && (this.apiToken == null || this.generatedAt + maxTokenAge < System.currentTimeMillis())) {
            generateToken();
        }

        return this.apiToken;
    }

    public ApiKeyProvider.ApiKey getApiKey() {
        return apiKey;
    }

    public void generateToken() {
        var tokenExpiry = new Date(System.currentTimeMillis() + 3600_000L);
        this.apiToken = apiTokenGenerator.generate(new ApiTokenGenerator.ApiTokenClaims(
                "plugin", "plugin", "fleet-management-api", tokenExpiry, apiKey.projectId, getDbmsId()));
        this.generatedAt = System.currentTimeMillis();
    }

    public String getDbmsId() {
        var databasesByInstance = transactor.getDatabases();
        return TopologyMapper.getDbmsId(databasesByInstance);
    }

    public UpstreamPostRequest postTo(Endpoint endpoint) throws IOException {
        switch (endpoint) {
            case CONNECT:
                return postTo("plugin/connect");
            case REPORTING:
                return postTo("plugin/report");
            case METRICS:
                return postTo("plugin/metrics");
            case CONFIG:
                return postTo("plugin/config");
            case PING:
                return postTo("plugin/ping");
        }

        return null;
    }

    public boolean isReachable() {
        URL url;
        try {
            url = new URL(this.baseUrl);
        } catch (MalformedURLException e) {
            return false;
        }

        try (Socket soc = new Socket(url.getHost(), url.getPort())) {
            soc.connect(new InetSocketAddress(url.getHost(), url.getPort()), 5000);
        } catch (IOException ex) {
            return false;
        }
        return true;
    }

    private UpstreamPostRequest postTo(String path) throws IOException {
        var url = new URL(String.format("%s/%s", this.baseUrl, path));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestProperty("Authorization", String.format("Bearer %s", getToken()));

        return new UpstreamPostRequest(conn);
    }

    public static class UpstreamPostRequest {
        private final HttpURLConnection conn;

        private UpstreamPostRequest(HttpURLConnection conn) throws ProtocolException {
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            this.conn = conn;
        }

        public int transmit(byte[] data) throws IOException {
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
                os.flush();
            } catch (IOException e) {
                throw new IOException("Exception while writing request data: ", e);
            }

            try {
                return conn.getResponseCode();
            } catch (IOException e) {
                throw new IOException("Exception while getting error code: ", e);
            }
        }

        public byte[] getResponseBody() throws IOException {
            int responseCode = 0;
            try {
                responseCode = conn.getResponseCode();
            } catch (IOException e) {
                throw new IOException("Exception while getting the response code: ", e);
            }

            if (Helpers.responseOk(responseCode)) {
                try {
                    return conn.getInputStream().readAllBytes();
                } catch (IOException e) {
                    throw new IOException("Exception while reading response data: ", e);
                }
            }

            try {
                return conn.getErrorStream().readAllBytes();
            } catch (IOException e) {
                throw new IOException("Exception while reading error response data: ", e);
            }
        }
    }
}
