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
package org.neo4j.gqlstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

class ErrorGqlStatusObjectImplementationTest {
    @Test
    void shouldHandleErrorWithDuplicatedParameter() {
        var errorBuilder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N02);
        errorBuilder.withParam(GqlParams.StringParam.db, "my_db"); // this parameter occurs twice in the message
        errorBuilder.withParam(GqlParams.StringParam.cfgSetting, "my_setting");
        var error = errorBuilder.build();

        assertThat(error.statusDescription())
                .isEqualTo(
                        "error: connection exception - unable to route to database. Unable to connect to database `my_db`. Server-side routing is disabled. Either connect to `my_db` directly, or enable server-side routing by setting 'my_setting=true'.");
    }

    @Test
    void shouldNotFailOnErrorWithTooFewParameters() {
        var errorBuilder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N02);
        var error = errorBuilder.build();

        assertThat(error.statusDescription())
                .isEqualTo(
                        "error: procedure exception - procedure execution client error. Execution of the procedure $proc() failed due to a client error.");
    }

    @Test
    void shouldNotFailOnErrorWithTooManyParameters() {
        var errorBuilder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N02);
        errorBuilder.withParam(GqlParams.StringParam.propKey, "bar");
        errorBuilder.withParam(GqlParams.StringParam.proc, "my_proc");
        var error = errorBuilder.build();

        assertThat(error.statusDescription())
                .isEqualTo(
                        "error: procedure exception - procedure execution client error. Execution of the procedure my_proc() failed due to a client error.");
    }

    @Test
    void shouldNotFailOnErrorWithWrongParameter() {
        var errorBuilder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_52N02);
        errorBuilder.withParam(GqlParams.StringParam.propKey, "bar");
        var error = errorBuilder.build();

        assertThat(error.statusDescription())
                .isEqualTo(
                        "error: procedure exception - procedure execution client error. Execution of the procedure $proc() failed due to a client error.");
    }

    @Test
    void shouldBeSerializedAndDeserialized() {
        var errorBuilder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N02);
        errorBuilder.withParam(GqlParams.StringParam.db, "my_db"); // this parameter occurs twice in the message
        errorBuilder.withParam(GqlParams.StringParam.cfgSetting, "my_setting");
        var error = errorBuilder.build();

        byte[] data = SerializationUtils.serialize((ErrorGqlStatusObjectImplementation) error.gqlStatusObject());

        ErrorGqlStatusObjectImplementation deserialized = SerializationUtils.deserialize(data);

        assertEquals(error, deserialized);
    }

    @Test
    void positionAssertShouldWorkForValidPositions() {
        var errorBuilder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N02);

        // Default/test positions with line -1 or 0
        assertDoesNotThrow(() -> errorBuilder.atPosition(0, 0, 0));
        assertDoesNotThrow(() -> errorBuilder.atPosition(1, 0, 1));
        assertDoesNotThrow(() -> errorBuilder.atPosition(42, 0, 37));
        assertDoesNotThrow(() -> errorBuilder.atPosition(-1, -1, -1));

        // On line 1, column is always offset + 1
        assertDoesNotThrow(() -> errorBuilder.atPosition(0, 1, 1));
        assertDoesNotThrow(() -> errorBuilder.atPosition(10, 1, 11));

        // On line > 1, offset must be at least as big as column
        assertDoesNotThrow(() -> errorBuilder.atPosition(7, 2, 7));
        assertDoesNotThrow(() -> errorBuilder.atPosition(47, 2, 37));
        assertDoesNotThrow(() -> errorBuilder.atPosition(23, 5, 1));
    }

    @Test
    void positionAssertShouldFailForInvalidPositions() {
        var errorBuilder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_08N02);

        // On line 1, column is always offset + 1
        assertThrows(AssertionError.class, () -> errorBuilder.atPosition(2, 1, 2));
        assertThrows(AssertionError.class, () -> errorBuilder.atPosition(2, 1, 1));

        // On line > 1, offset must be at least as big as column
        assertThrows(AssertionError.class, () -> errorBuilder.atPosition(6, 2, 7));
        assertThrows(AssertionError.class, () -> errorBuilder.atPosition(0, 5, 1));
    }
}
