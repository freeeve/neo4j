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
package org.neo4j.bolt.protocol.common.message.decoder;

import static org.neo4j.bolt.testing.util.ErrorUtil.useNewMessage;

import org.junit.jupiter.api.Test;
import org.neo4j.bolt.testing.mock.ConnectionMockFactory;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectAssertions;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.packstream.error.struct.IllegalStructSizeException;
import org.neo4j.packstream.io.PackstreamBuf;
import org.neo4j.packstream.struct.StructHeader;

public interface MessageDecoderTest<D extends MessageDecoder<?>> {

    D getDecoder();

    default int maximumNumberOfFields() {
        return 0;
    }

    default int excessNumberOfFields() {
        return this.maximumNumberOfFields() + 1;
    }

    @Test
    default void shouldFailWithIllegalStructSizeWhenExcessNumberOfFieldsIsGiven() {
        ErrorGqlStatusObjectAssertions.assertThatThrownBy(() -> this.getDecoder()
                        .read(
                                ConnectionMockFactory.newInstance(),
                                PackstreamBuf.allocUnpooled(),
                                new StructHeader(this.excessNumberOfFields(), (short) 0x42)))
                .isInstanceOf(IllegalStructSizeException.class)
                .hasMessage(useNewMessage(
                                "08N11: The request is invalid and could not be processed by the server. See cause for further details.")
                        .whenLegacyFallbackTo("Illegal struct size: Expected struct to be "
                                + this.maximumNumberOfFields() + " fields but got " + this.excessNumberOfFields()))
                .hasGqlStatus(GqlStatusInfoCodes.STATUS_08N11)
                .hasStatusDescription(
                        "error: connection exception - request error. The request is invalid and could not be processed by the server. See cause for further details.")
                .gqlCause()
                .hasGqlStatus(GqlStatusInfoCodes.STATUS_22N57)
                .hasStatusDescription(String.format(
                        "error: data exception - invalid protocol type. Protocol type is invalid. Invalid number of struct components (received %s but expected %s).",
                        this.excessNumberOfFields(), this.maximumNumberOfFields()));
    }
}
