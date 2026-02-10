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
package org.neo4j.kernel.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;
import static org.neo4j.kernel.KernelVersion.VERSION_ENVELOPED_TRANSACTION_LOGS_GUARANTEED;
import static org.neo4j.memory.EmptyMemoryTracker.INSTANCE;
import static org.neo4j.test.LatestVersions.LATEST_RUNTIME_VERSION;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.database.DbmsRuntimeVersion;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.io.fs.StoreChannel;
import org.neo4j.io.memory.ByteBuffers;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.impl.transaction.log.LogPosition;
import org.neo4j.kernel.impl.transaction.log.entry.LogEnvelopeHeader;
import org.neo4j.kernel.impl.transaction.log.entry.v522.DetachedCheckpointLogEntrySerializerV5_22;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.LogAssertions;
import org.neo4j.storageengine.api.TransactionIdStore;

class EnvelopedRecoveryCorruptedTransactionLogIT extends RecoveryCorruptedTransactionLogIT {
    // TODO MERGELOG turn into no-op when default format
    @Override
    protected Map<Setting<?>, Object> additionalConfig() {
        return Map.of(
                GraphDatabaseInternalSettings.latest_kernel_version,
                VERSION_ENVELOPED_TRANSACTION_LOGS_GUARANTEED.version(),
                GraphDatabaseInternalSettings.latest_runtime_version,
                LATEST_RUNTIME_VERSION.kernelVersion().isAtLeast(VERSION_ENVELOPED_TRANSACTION_LOGS_GUARANTEED)
                        ? LATEST_RUNTIME_VERSION.getVersion()
                        : DbmsRuntimeVersion.GLORIOUS_FUTURE.getVersion(),
                GraphDatabaseInternalSettings.allow_new_log_format_on_upgrade_or_create,
                true);
    }

    @Override
    protected KernelVersion kernelVersion() {
        return VERSION_ENVELOPED_TRANSACTION_LOGS_GUARANTEED;
    }

    @Override
    protected int checkpointRecordSize() {
        return DetachedCheckpointLogEntrySerializerV5_22.checkPointRecordSizeDependingOnVersion(true);
    }

    @Override
    protected int minimumBytesToConsiderARecordBroken() {
        // Anything the length of an envelope header or less is just considered a broken
        // last entry by default and will not be considered corrupted (just last broken entry which is recoverable).
        return LogEnvelopeHeader.HEADER_SIZE + 1;
    }

    @Test
    void allowRecoveryOnHeaderEndingInPreviousChecksum() throws IOException {
        DatabaseManagementService managementService = databaseFactory.build();
        GraphDatabaseAPI database = (GraphDatabaseAPI) managementService.database(DEFAULT_DATABASE_NAME);
        logFiles = buildDefaultLogFiles(getStoreId(database));

        TransactionIdStore transactionIdStore = getTransactionIdStore(database);
        LogPosition logOffsetBeforeTestTransactions =
                transactionIdStore.getHighestGapFreeClosedTransaction().logPosition();
        long lastClosedTransactionBeforeStart = transactionIdStore.getHighestGapFreeClosedTransactionId();
        generateTransaction(database);
        long numberOfClosedTransactions = getTransactionIdStore(database).getHighestGapFreeClosedTransactionId()
                - lastClosedTransactionBeforeStart;
        LogPosition logOffsetAfterTestTransactions =
                transactionIdStore.getHighestGapFreeClosedTransaction().logPosition();
        generateTransaction(database);

        // Zero out everything after first part of header of the second transaction.
        // Will break it in the middle of the previousChecksum field
        managementService.shutdown();
        long offset = logOffsetAfterTestTransactions.getByteOffset()
                + (LogEnvelopeHeader.HEADER_SIZE
                        - (Long.BYTES + Byte.BYTES) // fields after checksum
                        - (Short.BYTES)); // most of checksum
        try (StoreChannel storeChannel = fileSystem.write(
                logFiles.getLogFile().getLogFileForVersion(logFiles.getLogFile().getCurrentLogVersion()))) {
            storeChannel.position(offset);
            storeChannel.writeAll(ByteBuffers.allocate(2000, ByteOrder.LITTLE_ENDIAN, INSTANCE));
        }
        removeLastCheckpointRecordFromLastLogFile();

        startStopDatabase();

        assertEquals(numberOfClosedTransactions, recoveryMonitor.getNumberOfRecoveredTransactions());
        LogAssertions.assertThat(logProvider)
                .containsMessages("Recovery required from position LogPosition{logVersion=0, byteOffset="
                        + logOffsetBeforeTestTransactions.getByteOffset() + "}");
    }
}
