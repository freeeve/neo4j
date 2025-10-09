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
package org.neo4j.internal.kernel.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.internal.kernel.api.connectioninfo.ClientConnectionInfo;

public class AbstractSecurityLogTest {

    @Test
    public void testSecurityLogLineAsString() {

        AbstractSecurityLog.SecurityLogLine ll = new AbstractSecurityLog.SecurityLogLine(
                ClientConnectionInfo.EMBEDDED_CONNECTION,
                "database",
                "executingUser",
                "message",
                "authUser",
                GqlStatusInfoCodes.STATUS_42NFF.getGqlStatus());

        StringBuilder sb = new StringBuilder();
        ll.formatAsString(sb);
        assertEquals("[authUser:executingUser]: Exception thrown, 42NFF: message", sb.toString());
    }

    @Test
    public void testSecurityLogLineAsStringWithoutOptionalValues() {

        AbstractSecurityLog.SecurityLogLine ll = new AbstractSecurityLog.SecurityLogLine(
                ClientConnectionInfo.EMBEDDED_CONNECTION, "database", null, "message", null, null);

        StringBuilder sb = new StringBuilder();
        ll.formatAsString(sb);
        assertEquals("message", sb.toString());
    }

    @Test
    public void testSecurityLogLineAsStringWithoutConnectionInfo() {

        AbstractSecurityLog.SecurityLogLine ll = new AbstractSecurityLog.SecurityLogLine(
                ClientConnectionInfo.EMBEDDED_CONNECTION,
                "database",
                null,
                "message",
                null,
                GqlStatusInfoCodes.STATUS_42NFF.getGqlStatus());

        StringBuilder sb = new StringBuilder();
        ll.formatAsString(sb);
        assertEquals("Exception thrown, 42NFF: message", sb.toString());
    }

    @Test
    public void testSecurityLogLineAsStringWithoutExceptionThrown() {

        AbstractSecurityLog.SecurityLogLine ll = new AbstractSecurityLog.SecurityLogLine(
                ClientConnectionInfo.EMBEDDED_CONNECTION, "database", "executingUser", "message", "authUser", null);

        StringBuilder sb = new StringBuilder();
        ll.formatAsString(sb);
        assertEquals("[authUser:executingUser]: message", sb.toString());
    }

    @Test
    public void testSecurityLogLineAsStringHandlesNewlines() {

        AbstractSecurityLog.SecurityLogLine ll = new AbstractSecurityLog.SecurityLogLine(
                ClientConnectionInfo.EMBEDDED_CONNECTION,
                "database",
                "executingUser",
                "message1\nmessage2\r\nmessage3",
                "authUser",
                GqlStatusInfoCodes.STATUS_42NFF.getGqlStatus());

        StringBuilder sb = new StringBuilder();
        ll.formatAsString(sb);
        assertEquals("[authUser:executingUser]: Exception thrown, 42NFF: message1 message2 message3", sb.toString());
    }
}
