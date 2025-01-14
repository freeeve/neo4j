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
package org.neo4j.kernel.api;

import static org.neo4j.internal.kernel.api.security.LoginContext.AUTH_DISABLED;
import static org.neo4j.kernel.api.KernelTransactionFactory.kernelTransaction;

import org.junit.jupiter.api.Test;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectAssertions;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.internal.kernel.api.exceptions.InvalidTransactionTypeKernelException;
import org.neo4j.kernel.api.security.AnonymousContext;

class TransactionStatementSequenceTest {
    @Test
    void shouldAllowReadStatementAfterReadStatement() {
        // given
        KernelTransaction tx = kernelTransaction(AnonymousContext.read());
        tx.dataRead();

        // when / then
        tx.dataRead();
    }

    @Test
    void shouldAllowDataStatementAfterReadStatement() throws Exception {
        // given
        KernelTransaction tx = kernelTransaction(AnonymousContext.write());
        tx.dataRead();

        // when / then
        tx.dataWrite();
    }

    @Test
    void shouldAllowSchemaStatementAfterReadStatement() throws Exception {
        // given
        KernelTransaction tx = kernelTransaction(AUTH_DISABLED);
        tx.dataRead();

        // when / then
        tx.schemaWrite();
    }

    @Test
    void shouldRejectSchemaStatementAfterDataStatement() throws Exception {
        // given
        KernelTransaction tx = kernelTransaction(AUTH_DISABLED);
        tx.dataWrite();

        // when
        ErrorGqlStatusObjectAssertions.assertThatThrownBy(tx::schemaWrite)
                .isInstanceOf(InvalidTransactionTypeKernelException.class)
                .hasMessage("Cannot perform schema updates in a transaction that has performed data updates.")
                .hasGqlStatus(GqlStatusInfoCodes.STATUS_25G02)
                .hasStatusDescription(
                        "error: invalid transaction state - catalog and data statement mixing not supported");
    }

    @Test
    void shouldRejectDataStatementAfterSchemaStatement() throws Exception {
        // given
        KernelTransaction tx = kernelTransaction(AUTH_DISABLED);
        tx.schemaWrite();

        // when
        ErrorGqlStatusObjectAssertions.assertThatThrownBy(tx::dataWrite)
                .isInstanceOf(InvalidTransactionTypeKernelException.class)
                .hasMessage("Cannot perform data updates in a transaction that has performed schema updates.")
                .hasGqlStatus(GqlStatusInfoCodes.STATUS_25G02)
                .hasStatusDescription(
                        "error: invalid transaction state - catalog and data statement mixing not supported");
    }

    @Test
    void shouldAllowDataStatementAfterDataStatement() throws Exception {
        // given
        KernelTransaction tx = kernelTransaction(AnonymousContext.write());
        tx.dataWrite();

        // when / then
        tx.dataWrite();
    }

    @Test
    void shouldAllowSchemaStatementAfterSchemaStatement() throws Exception {
        // given
        KernelTransaction tx = kernelTransaction(AUTH_DISABLED);
        tx.schemaWrite();

        // when / then
        tx.schemaWrite();
    }

    @Test
    void shouldAllowReadStatementAfterDataStatement() throws Exception {
        // given
        KernelTransaction tx = kernelTransaction(AnonymousContext.write());
        tx.dataWrite();

        // when / then
        tx.dataRead();
    }

    @Test
    void shouldAllowReadStatementAfterSchemaStatement() throws Exception {
        // given
        KernelTransaction tx = kernelTransaction(AUTH_DISABLED);
        tx.schemaWrite();

        // when / then
        tx.dataRead();
    }
}
