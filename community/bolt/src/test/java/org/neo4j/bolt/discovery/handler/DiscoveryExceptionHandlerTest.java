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
package org.neo4j.bolt.discovery.handler;

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.AssertableLogProvider.Level;
import org.neo4j.logging.LogAssertions;
import org.neo4j.logging.internal.SimpleLogService;

class DiscoveryExceptionHandlerTest {

    private AssertableLogProvider logProvider;

    private DiscoveryExceptionHandler handler;

    @BeforeEach
    void prepare() {
        this.logProvider = new AssertableLogProvider();

        this.handler = new DiscoveryExceptionHandler(new SimpleLogService(this.logProvider));
    }

    @Test
    void shouldReportExceptions() throws Exception {
        var ex = new RuntimeException("Something broke!");
        this.handler.exceptionCaught(Mockito.mock(ChannelHandlerContext.class), ex);

        LogAssertions.assertThat(this.logProvider)
                .forLevel(Level.ERROR)
                .forClass(DiscoveryExceptionHandler.class)
                .containsMessageWithException(
                        "Discovery service triggered an unexpected error within network pipeline", ex);
    }
}
