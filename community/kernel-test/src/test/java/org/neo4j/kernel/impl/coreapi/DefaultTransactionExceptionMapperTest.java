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
import static org.neo4j.logging.LogAssertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.graphdb.TransactionFailureHelper;
import org.neo4j.logging.AssertableLogProvider;
import org.neo4j.monitoring.ExceptionHandlerService;

class DefaultTransactionExceptionMapperTest {

    @Test
    void shouldUseCorrectGQLStatusForGenericFailure() {
        // GIVEN
        AssertableLogProvider assertableLogProvider = new AssertableLogProvider();
        RuntimeException exception = new RuntimeException("Generic failure");
        ExceptionHandlerService exceptionHandlerService = new ExceptionHandlerService(assertableLogProvider);
        List<String> messages = new ArrayList<>();
        exceptionHandlerService.addExceptionHandler((message, e) -> messages.add(message));
        // WHEN
        RuntimeException mappedException = DefaultTransactionExceptionMapper.INSTANCE.mapException(
                exception, assertableLogProvider.getLog("foo"), exceptionHandlerService);
        // THEN
        assertThat(mappedException)
                .isInstanceOf(TransactionFailureException.class)
                .hasMessageContaining(TransactionFailureHelper.UNABLE_TO_COMPLETE_TRANSACTION);
        TransactionFailureException txFailureException = (TransactionFailureException) mappedException;

        assertThat(txFailureException.gqlStatus()).isEqualTo("25N02");
        assertThat(txFailureException.statusDescription())
                .isEqualTo(
                        "error: invalid transaction state - unable to complete transaction. Unable to complete transaction. See debug log for details.");

        assertThat(assertableLogProvider).containsMessageWithException("Generic failure", exception);

        assertThat(messages).containsExactly("Generic failure");
    }
}
