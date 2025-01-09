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
package org.neo4j.kernel.impl.coreapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.graphdb.TransactionFailureHelper;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.logging.LogAssertions;

class DefaultTransactionExceptionMapperTest {

    @Test
    void shouldUseCorrectGQLStatusForGenericFailure() {
        // GIVEN
        final var assertableLogProvider = new AssertableLogProvider();
        final var exception = new RuntimeException("Generic failure");
        // WHEN
        final var mappedException =
                DefaultTransactionExceptionMapper.INSTANCE.mapException(exception, assertableLogProvider.getLog("foo"));
        // THEN
        assertThat(mappedException)
                .isInstanceOf(TransactionFailureException.class)
                .hasMessageContaining(TransactionFailureHelper.UNABLE_TO_COMPLETE_TRANSACTION);
        var txFailureException = (TransactionFailureException) mappedException;

        assertThat(txFailureException.gqlStatus()).isEqualTo("25N02");
        assertThat(txFailureException.statusDescription())
                .isEqualTo(
                        "error: invalid transaction state - unable to complete transaction. Unable to complete transaction. See debug log for details.");

        LogAssertions.assertThat(assertableLogProvider).containsMessageWithException("Generic failure", exception);
    }
}
