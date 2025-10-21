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
package org.neo4j.fleetmanagement.communication;

import static org.neo4j.fleetmanagement.communication.Helpers.responseOk;

import java.io.IOException;
import org.neo4j.fleetmanagement.communication.upstream.Upstream;
import org.neo4j.fleetmanagement.transactions.ITransactor;

public abstract class AbstractReportingService extends BaseService {
    protected final Upstream upstream;

    protected AbstractReportingService(ITransactor transactor, Upstream upstream) {
        super(transactor);
        this.upstream = upstream;
    }

    public abstract void report();

    protected void transmitReport(Object msg, Upstream.Endpoint endpoint) {
        transmitReportWithResponse(msg, endpoint);
    }

    protected Response transmitReportWithResponse(Object msg, Upstream.Endpoint endpoint) {
        var messageName = msg.getClass().getSimpleName();
        try {
            String payload = objectMapper.writeValueAsString(msg);
            this.fleetManagerLog.debug("Fleet manager reporting " + messageName);
            this.fleetManagerLog.payload("Fleet manager reporting " + messageName + ": " + payload);

            Upstream.UpstreamPostRequest upstreamPostRequest = upstream.postTo(endpoint);
            var responseCode = upstreamPostRequest.transmit(payload.getBytes());

            byte[] responseBody;
            try {
                responseBody = upstreamPostRequest.getResponseBody();
            } catch (IOException e) {
                var errorMsg = "Fleet manager failed to parse error response - IOException: " + e.getMessage();
                this.userLog.error(errorMsg);
                this.state.setDisconnected(errorMsg);
                return null;
            }

            if (!responseOk(responseCode)) {
                this.handleErrorResponse("Fleet manager failed to report", responseCode, responseBody);
                return null;
            }
            if (responseBody != null) {
                this.fleetManagerLog.payload(
                        "Fleet manager reporting " + messageName + " received response: " + new String(responseBody));
            }
            return new Response(responseCode, responseBody);
        } catch (IOException e) {
            var errorMsg = "Fleet manager failed to report " + messageName + " - " + e;
            this.state.setDisconnected(errorMsg);
            this.userLog.error(errorMsg);
            throw new RuntimeException(e);
        }
    }

    protected static class Response {
        protected int responseCode;
        protected byte[] responseBody;

        protected Response(int responseCode, byte[] responseBody) {
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }
    }
}
