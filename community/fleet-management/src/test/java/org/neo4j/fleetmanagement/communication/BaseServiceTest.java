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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.neo4j.fleetmanagement.configuration.State;
import org.neo4j.fleetmanagement.transactions.ITransactor;
import org.neo4j.fleetmanagement.utils.Logger;
import org.neo4j.logging.Log;

class BaseServiceTest {
    private BaseService baseService;

    private ITransactor mockTransactor;

    private static Log mockLog;

    @BeforeAll
    static void beforeAll() {
        mockLog = Mockito.mock(Log.class);
        Logger.initLogger(mockLog);
    }

    @BeforeEach
    public void setup() {
        mockTransactor = Mockito.mock(ITransactor.class);

        baseService = Mockito.spy(new BaseService(mockTransactor));
    }

    @AfterEach
    public void tearDown() {
        Mockito.reset(mockLog);
    }

    @Test
    void handleErrorResponseWhenResponseBodyIsInvalid() {
        var errMsgPrefix = "Failed";
        var responseCode = 500;

        // when response body is empty
        byte[] emptyResponseBody = null;
        baseService.handleErrorResponse(errMsgPrefix, responseCode, emptyResponseBody);
        assertFalse(baseService.state.isConnected());
        Mockito.verify(mockLog).error(Mockito.anyString());

        // when fail to deserialize an error message
        var unparsableResponseBody = "abc".getBytes();
        baseService.handleErrorResponse(errMsgPrefix, responseCode, unparsableResponseBody);
        assertFalse(baseService.state.isConnected());
        Mockito.verify(mockLog, Mockito.times(2)).error(Mockito.anyString());

        // when parsed response is invalid
        var parsedInvalidResponseBody = "{}".getBytes();
        baseService.handleErrorResponse(errMsgPrefix, responseCode, parsedInvalidResponseBody);
        assertFalse(baseService.state.isConnected());
        Mockito.verify(mockLog, Mockito.times(3)).error(Mockito.anyString());

        // when parsed response has invalid values
        var parsedInvalidValueResponseBody = "{\"code\": 9999}".getBytes();
        baseService.handleErrorResponse(errMsgPrefix, responseCode, parsedInvalidValueResponseBody);
        assertFalse(baseService.state.isConnected());
        Mockito.verify(mockLog, Mockito.times(4)).error(Mockito.anyString());
    }

    @Test
    void handleErrorResponseWhenResponseBodyIsValid() {
        var errMsgPrefix = "Failed";

        // token rotation due
        var tokenRotationErr = "{\"code\": 1000, \"message\": \"Token needs rotation\"}";
        baseService.handleErrorResponse(errMsgPrefix, 401, tokenRotationErr.getBytes());
        Mockito.verify(mockTransactor).rotateToken();
        assertTrue(State.getInstance().isRotatingToken());

        // token expired
        var tokenExpiredErr = "{\"code\": 1001, \"message\": \"Token expired\"}";
        baseService.handleErrorResponse(errMsgPrefix, 401, tokenExpiredErr.getBytes());
        Mockito.verify(mockTransactor).deleteToken();
        Mockito.verify(mockLog).error(Mockito.anyString());
        assertFalse(State.getInstance().isConnected());
        assertEquals(
                "Fleet management token is permanently expired - register a new one to resume operation",
                State.getInstance().getConnectionMessage());

        // token revoked
        var tokenRevokedErr = "{\"code\": 1002, \"message\": \"Token revoked\"}";
        baseService.handleErrorResponse(errMsgPrefix, 401, tokenRevokedErr.getBytes());
        Mockito.verify(mockTransactor, Mockito.times(2)).deleteToken();
        Mockito.verify(mockLog, Mockito.times(2)).error(Mockito.anyString());
        assertFalse(State.getInstance().isConnected());
        assertEquals(
                "Fleet management token is revoked - register a new one to resume operation",
                State.getInstance().getConnectionMessage());

        // access denied
        var accessDeniedErr = "{\"code\": 1003, \"message\": \"Access denied\"}";
        baseService.handleErrorResponse(errMsgPrefix, 401, accessDeniedErr.getBytes());
        Mockito.verify(mockTransactor, Mockito.times(3)).deleteToken();
        Mockito.verify(mockLog, Mockito.times(3)).error(Mockito.anyString());
        assertFalse(State.getInstance().isConnected());
        assertEquals(
                "Fleet management access denied - check your permissions",
                State.getInstance().getConnectionMessage());
    }
}
