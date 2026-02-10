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
package org.neo4j.bolt.txtracking;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.neo4j.kernel.api.exceptions.Status.Database.DatabaseNotFound;
import static org.neo4j.kernel.api.exceptions.Status.General.DatabaseUnavailable;
import static org.neo4j.kernel.api.exceptions.Status.Transaction.BookmarkTimeout;
import static org.neo4j.kernel.database.DatabaseIdFactory.from;
import static org.neo4j.storageengine.api.TransactionIdStore.BASE_TX_ID;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.collection.Dependencies;
import org.neo4j.common.SystemLastTransactionIdProvider;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseNotFoundException;
import org.neo4j.dbms.api.DatabaseNotFoundHelper;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectAssertions;
import org.neo4j.gqlstatus.GqlExceptionLikeAssert;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.kernel.availability.DatabaseAvailabilityGuard;
import org.neo4j.kernel.database.AbstractDatabase;
import org.neo4j.kernel.database.Database;
import org.neo4j.kernel.database.NamedDatabaseId;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.NullLogProvider;
import org.neo4j.monitoring.Monitors;
import org.neo4j.storageengine.api.TransactionIdStore;
import org.neo4j.time.Clocks;

class TransactionIdTrackerTest {
    private static final Duration DEFAULT_DURATION = ofSeconds(10);

    private final TransactionIdStore transactionIdStore = mock(TransactionIdStore.class);
    private final DatabaseAvailabilityGuard databaseAvailabilityGuard = mock(DatabaseAvailabilityGuard.class);
    private final NamedDatabaseId namedDatabaseId = from("foo", UUID.randomUUID());
    private final Dependencies resolver = mock(Dependencies.class);
    private final Database db = mock(Database.class);
    private final DatabaseManagementService managementService = mock(DatabaseManagementService.class);

    private TransactionIdTracker transactionIdTracker;

    @BeforeEach
    void setup() {
        var dbApi = mock(GraphDatabaseAPI.class);

        when(managementService.database(namedDatabaseId.name())).thenReturn(dbApi);
        when(dbApi.getDependencyResolver()).thenReturn(resolver);
        when(dbApi.databaseId()).thenReturn(namedDatabaseId);

        when(db.getNamedDatabaseId()).thenReturn(namedDatabaseId);
        when(db.isSystem()).thenReturn(false);
        when(db.getDependencyResolver()).thenReturn(resolver);
        when(db.getDatabaseAvailabilityGuard()).thenReturn(databaseAvailabilityGuard);

        when(resolver.resolveDependency(AbstractDatabase.class)).thenReturn(db);
        when(resolver.resolveDependency(TransactionIdStore.class)).thenReturn(transactionIdStore);

        when(databaseAvailabilityGuard.isAvailable()).thenReturn(true);
        transactionIdTracker = new TransactionIdTracker(
                managementService, new Monitors(), Clocks.fakeClock(), NullLogProvider.getInstance());
    }

    @Test
    void shouldReturnImmediatelyForBaseTxIdOrLess() {
        // when
        transactionIdTracker.awaitUpToDate(namedDatabaseId, BASE_TX_ID, ofSeconds(5));

        // then
        verify(transactionIdStore, never()).getHighestGapFreeClosedTransactionId();
    }

    @Test
    void shouldReturnImmediatelyForBaseTxIdOrLessUsingSystemDb() {
        // given
        when(db.isSystem()).thenReturn(true);

        // when
        transactionIdTracker.awaitUpToDate(namedDatabaseId, BASE_TX_ID, ofSeconds(5));

        // then
        verifyNoInteractions(transactionIdStore);
    }

    @Test
    void shouldUseSystemLastTransactionIdProviderIfPresent() {
        // given
        var version = 5L;

        when(db.isSystem()).thenReturn(true);
        var systemLastTransactionIdProvider = mock(SystemLastTransactionIdProvider.class);
        when(resolver.resolveOptionalDependency(SystemLastTransactionIdProvider.class))
                .thenReturn(Optional.of(systemLastTransactionIdProvider));
        when(systemLastTransactionIdProvider.lastTransactionId())
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(6L);

        // when
        transactionIdTracker.awaitUpToDate(namedDatabaseId, version, DEFAULT_DURATION);

        // then
        verify(systemLastTransactionIdProvider, times(3)).lastTransactionId();
        verifyNoInteractions(transactionIdStore);
    }

    @Test
    void shouldThrowUnavailableIfSystemNotFound() {
        doThrow(DatabaseNotFoundHelper.databaseNotFound(NamedDatabaseId.SYSTEM_DATABASE_NAME))
                .when(managementService)
                .database(NamedDatabaseId.SYSTEM_DATABASE_NAME);
        verifyDbUnavailableError(
                () -> transactionIdTracker.awaitUpToDate(NamedDatabaseId.NAMED_SYSTEM_DATABASE_ID, 33L, ofSeconds(5)),
                NamedDatabaseId.SYSTEM_DATABASE_NAME,
                false);
        verifyDbUnavailableError(
                () -> transactionIdTracker.newestTransactionId(NamedDatabaseId.NAMED_SYSTEM_DATABASE_ID),
                NamedDatabaseId.SYSTEM_DATABASE_NAME,
                false);
    }

    @Test
    void shouldWaitForRequestedVersion() {
        // given
        var version = 5L;

        when(transactionIdStore.getHighestGapFreeClosedTransactionId())
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(6L);

        // when
        transactionIdTracker.awaitUpToDate(namedDatabaseId, version, DEFAULT_DURATION);

        // then
        verify(transactionIdStore, times(3)).getHighestGapFreeClosedTransactionId();
    }

    @Test
    void shouldWaitForRequestedVersionUsingSystemDb() {
        // given
        when(db.isSystem()).thenReturn(true);
        var version = 42L;
        when(transactionIdStore.getHighestGapFreeClosedTransactionId()).thenReturn(version);

        // when
        transactionIdTracker.awaitUpToDate(namedDatabaseId, version, DEFAULT_DURATION);

        // then
        verify(transactionIdStore, times(1)).getHighestGapFreeClosedTransactionId();
    }

    @Test
    void shouldWrapAnyStoreCheckExceptions() {
        // given
        var version = 5L;
        var checkException = new RuntimeException();
        doThrow(checkException).when(transactionIdStore).getHighestGapFreeClosedTransactionId();

        // then
        verifyBookmarkError(
                () -> transactionIdTracker.awaitUpToDate(namedDatabaseId, version + 1, ofMillis(50)),
                namedDatabaseId.name());
    }

    @Test
    void shouldWrapAnyStoreCheckExceptionsUsingSystemDb() {
        // given
        when(db.isSystem()).thenReturn(true);
        var version = 3L;
        var checkException = new RuntimeException();
        doThrow(checkException).when(transactionIdStore).getHighestGapFreeClosedTransactionId();

        // then
        verifyBookmarkError(
                () -> transactionIdTracker.awaitUpToDate(namedDatabaseId, version + 1, ofMillis(50)),
                namedDatabaseId.name());
    }

    @Test
    void shouldThrowDatabaseIsShutdownWhenStoreShutdownAfterCheck() {
        // given
        var version = 5L;
        var checkException = new RuntimeException();
        doThrow(checkException).when(transactionIdStore).getHighestGapFreeClosedTransactionId();
        when(databaseAvailabilityGuard.isAvailable()).thenReturn(true, true, false);

        // then
        verifyDbUnavailableError(
                () -> transactionIdTracker.awaitUpToDate(namedDatabaseId, version + 1, ofMillis(50)),
                namedDatabaseId.name(),
                true);
    }

    @Test
    void shouldThrowDatabaseIsShutdownWhenStoreShutdownAfterCheckUsingSystemDb() {
        // given
        when(db.isSystem()).thenReturn(true);
        var version = 42L;
        var checkException = new RuntimeException();
        doThrow(checkException).when(transactionIdStore).getHighestGapFreeClosedTransactionId();
        when(databaseAvailabilityGuard.isAvailable()).thenReturn(true, true, false);

        // then
        verifyDbUnavailableError(
                () -> transactionIdTracker.awaitUpToDate(namedDatabaseId, version + 1, ofMillis(50)),
                namedDatabaseId.name(),
                true);
    }

    @Test
    void shouldNotWaitIfTheDatabaseIsUnavailable() {
        // given
        when(databaseAvailabilityGuard.isAvailable()).thenReturn(false);

        // then
        verifyDbUnavailableError(
                () -> transactionIdTracker.awaitUpToDate(namedDatabaseId, 1000, ofMillis(60_000)),
                namedDatabaseId.name(),
                false);
        verify(transactionIdStore, never()).getHighestGapFreeClosedTransactionId();
    }

    @Test
    void shouldNotWaitIfTheSystemDatabaseIsUnavailable() {
        // given
        when(db.isSystem()).thenReturn(true);
        when(databaseAvailabilityGuard.isAvailable()).thenReturn(false);

        // then
        verifyDbUnavailableError(
                () -> transactionIdTracker.awaitUpToDate(namedDatabaseId, 1000, ofMillis(60_000)),
                namedDatabaseId.name(),
                false);
        verifyNoInteractions(transactionIdStore);
    }

    @Test
    void shouldReturnNewestTransactionId() {
        // given
        when(transactionIdStore.getHighestGapFreeClosedTransactionId()).thenReturn(42L);
        when(transactionIdStore.getLastCommittedTransactionId()).thenReturn(4242L);

        // then
        assertEquals(4242L, transactionIdTracker.newestTransactionId(namedDatabaseId));
    }

    @Test
    void shouldReturnNewestTransactionIdUsingSystemDb() {
        // given
        when(db.isSystem()).thenReturn(true);
        when(transactionIdStore.getLastCommittedTransactionId()).thenReturn(42L);

        // then
        assertEquals(42L, transactionIdTracker.newestTransactionId(namedDatabaseId));
    }

    @Test
    void shouldNotReturnNewestTransactionIdForDatabaseThatDoesNotExist() {
        // given
        var unknownDatabaseId = from("bar", UUID.randomUUID());
        when(managementService.database(unknownDatabaseId.name())).thenThrow(DatabaseNotFoundException.class);

        // then
        verifyDbNotFoundError(() -> transactionIdTracker.newestTransactionId(unknownDatabaseId), "bar");
    }

    @Test
    void shouldNotAwaitForTransactionForDatabaseThatDoesNotExist() {
        // given
        var unknownDatabaseId = from("bar", UUID.randomUUID());
        when(managementService.database(unknownDatabaseId.name())).thenThrow(DatabaseNotFoundException.class);

        // then
        verifyDbNotFoundError(() -> transactionIdTracker.awaitUpToDate(unknownDatabaseId, 1, ofMillis(1)), "bar");
    }

    private void verifyDbNotFoundError(ThrowableAssert.ThrowingCallable callable, String databaseName) {
        ErrorGqlStatusObjectAssertions.assertThatThrownBy(callable)
                .isInstanceOf(TransactionIdTrackerException.class)
                .hasStatus(DatabaseNotFound)
                .hasGqlStatus(GqlStatusInfoCodes.STATUS_22000)
                .hasStatusDescription("error: data exception")
                .gqlCause()
                .hasGqlStatus(GqlStatusInfoCodes.STATUS_22N51)
                .hasStatusDescription(String.format(
                        "error: data exception - graph reference not found. "
                                + "A graph reference with the name `%s` was not found. Verify that the spelling is correct.",
                        databaseName));
    }

    private void verifyDbUnavailableError(
            ThrowableAssert.ThrowingCallable callable, String databaseName, boolean runtimeCause) {
        GqlExceptionLikeAssert assertion = ErrorGqlStatusObjectAssertions.assertThatThrownBy(callable)
                .isInstanceOf(TransactionIdTrackerException.class)
                .hasStatus(DatabaseUnavailable)
                .hasGqlStatus(GqlStatusInfoCodes.STATUS_08N09)
                .hasStatusDescription(String.format(
                        "error: connection exception - database unavailable. The database `%s` is currently unavailable. "
                                + "Check the database status. Retry your request at a later time.",
                        databaseName));

        if (runtimeCause) {
            assertion.cause().isInstanceOf(RuntimeException.class);
        }
    }

    private void verifyBookmarkError(ThrowableAssert.ThrowingCallable callable, String databaseName) {
        ErrorGqlStatusObjectAssertions.assertThatThrownBy(callable)
                .isInstanceOf(TransactionIdTrackerException.class)
                .hasStatus(BookmarkTimeout)
                .hasGqlStatus(GqlStatusInfoCodes.STATUS_08N13)
                .hasStatusDescriptionContaining(String.format(
                        "error: connection exception - database not up to requested bookmark. "
                                + "The database `%s` is not up to the requested bookmark",
                        databaseName))
                .cause()
                .isInstanceOf(RuntimeException.class);
    }
}
