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
package org.neo4j.kernel.impl.transaction.log.files;

import static java.util.Objects.requireNonNull;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.neo4j.kernel.KernelVersionProvider;
import org.neo4j.kernel.impl.transaction.log.LogFormatVersionProvider;
import org.neo4j.storageengine.ReadOnlyLogVersionRepository;
import org.neo4j.storageengine.api.LogMetadataProvider;
import org.neo4j.storageengine.api.LogVersionRepository;

public class TransactionLogFilesProviders {
    private final LogMetadataProvider logMetadataProvider;
    private final TransactionLogFilesOverrides overrides;
    private final LastAppendIndexLogFilesProvider lastAppendIndexLogFilesProvider;
    private final LastAppendIndexProvider lastAppendIndexProvider;
    private final LastCommittedChecksumProvider lastCommittedChecksumProvider;
    private final LogVersionRepositoryProvider logVersionRepositoryProvider;
    private final LastClosedPositionProvider lastClosedPositionProvider;

    public TransactionLogFilesProviders(
            LogMetadataProvider logMetadataProvider, TransactionLogFilesOverrides overrides) {
        this.logMetadataProvider = logMetadataProvider;
        this.overrides = overrides;
        logVersionRepositoryProvider = setupLogVersionRepositoryProvider();
        LastAppendIndexProvider availableAppendIndexProvider = availableAppendIndexProvider();
        lastAppendIndexLogFilesProvider = lastAppendIndexLogFilesProvider(availableAppendIndexProvider);
        lastAppendIndexProvider = lastAppendIndexProvider(availableAppendIndexProvider);
        lastClosedPositionProvider = closePositionProvider();
        lastCommittedChecksumProvider = lastCommittedChecksumProvider();
    }

    private LastCommittedChecksumProvider lastCommittedChecksumProvider() {
        if (overrides.lastCommittedChecksumProvider() != null) {
            return new IntSupplierLastCommittedChecksumProvider(overrides.lastCommittedChecksumProvider());
        }
        if (overrides.transactionIdStore() != null) {
            return new IntSupplierLastCommittedChecksumProvider(() ->
                    overrides.transactionIdStore().getLastCommittedTransaction().checksum());
        }
        if (overrides.fileBasedOperationsOnly()) {
            return any -> {
                throw new UnsupportedOperationException("Current version of log files can't perform any operation that"
                        + " require availability of transaction id store. Please build full version of log files to be"
                        + " able to use them.");
            };
        }
        if (overrides.readOnlyStores()) {
            requireNonNull(overrides.databaseLayout(), "Store directory is required.");
            return new ReadOnlyLastCommittedChecksumProvider();
        } else {
            return new IntSupplierLastCommittedChecksumProvider(
                    () -> logMetadataProvider.getLastCommittedTransaction().checksum());
        }
    }

    private LogVersionRepositoryProvider setupLogVersionRepositoryProvider() {
        if (overrides.logVersionRepository() != null) {
            return any -> overrides.logVersionRepository();
        }
        if (overrides.fileBasedOperationsOnly()) {
            return any -> {
                throw new UnsupportedOperationException(
                        "Current version of log files can't perform any operation that require availability of log"
                                + " version repository. Please build full version of log files to be able to use them.");
            };
        }
        if (overrides.readOnlyStores()) {
            requireNonNull(overrides.databaseLayout(), "Store directory is required.");
            return new ReadOnlyLogVersionRepositoryProvider();
        } else {
            return new SupplierLogVersionRepositoryProvider(() -> logMetadataProvider);
        }
    }

    public LogVersionRepositoryProvider getLogVersionRepositoryProvider() {
        return logVersionRepositoryProvider;
    }

    public LastAppendIndexLogFilesProvider getLastAppendIndexLogFilesProvider() {
        return lastAppendIndexLogFilesProvider;
    }

    public long appendIndex() {
        return lastAppendIndexProvider.lastAppendIndex();
    }

    LastClosedPositionProvider getLastClosedTransactionPositionProvider() {
        return lastClosedPositionProvider;
    }

    public LastCommittedChecksumProvider getLastCommittedChecksumProvider() {
        return lastCommittedChecksumProvider;
    }

    public KernelVersionProvider getKernelVersionProvider() {
        if (overrides.kernelVersionProvider() != null) {
            return overrides.kernelVersionProvider();
        }
        return logMetadataProvider;
    }

    public LogFormatVersionProvider getLogFormatVersionProvider() {
        if (overrides.logFormatVersionProvider() != null) {
            return overrides.logFormatVersionProvider();
        }
        return logMetadataProvider;
    }

    private LastAppendIndexProvider availableAppendIndexProvider() {
        if (overrides.appendIndexProvider() != null) {
            return overrides.appendIndexProvider()::getLastAppendIndex;
        }
        return logMetadataProvider::getLastAppendIndex;
    }

    private LastAppendIndexProvider lastAppendIndexProvider(LastAppendIndexProvider availableProvider) {
        return availableProvider;
    }

    private record LongSupplierLastAppendIndexLogFilesProvider(LongSupplier idSupplier)
            implements LastAppendIndexLogFilesProvider {

        @Override
        public long getLastAppendIndex(LogFiles logFiles) {
            return idSupplier.getAsLong();
        }
    }

    private static class ReadOnlyLastAppendIndexLogFilesProvider implements LastAppendIndexLogFilesProvider {
        @Override
        public long getLastAppendIndex(LogFiles logFiles) {
            return logFiles.getTailMetadata().getLastCheckpointedAppendIndex();
        }
    }

    private record IntSupplierLastCommittedChecksumProvider(IntSupplier supplier)
            implements LastCommittedChecksumProvider {

        @Override
        public int getLastCommittedChecksum(LogFiles logFiles) {
            return supplier.getAsInt();
        }
    }

    private static class ReadOnlyLastCommittedChecksumProvider implements LastCommittedChecksumProvider {
        @Override
        public int getLastCommittedChecksum(LogFiles logFiles) {
            return logFiles.getTailMetadata().getLastCommittedTransaction().checksum();
        }
    }

    private record SupplierLogVersionRepositoryProvider(Supplier<LogVersionRepository> supplier)
            implements LogVersionRepositoryProvider {

        @Override
        public LogVersionRepository logVersionRepository(LogFiles logFiles) {
            return supplier.get();
        }
    }

    private static class ReadOnlyLogVersionRepositoryProvider implements LogVersionRepositoryProvider {
        @Override
        public LogVersionRepository logVersionRepository(LogFiles logFiles) {
            return new ReadOnlyLogVersionRepository(logFiles.getTailMetadata());
        }
    }

    private LastAppendIndexLogFilesProvider lastAppendIndexLogFilesProvider(
            LastAppendIndexProvider lastAppendIndexProvider) {
        if (lastAppendIndexProvider != null) {
            return new LongSupplierLastAppendIndexLogFilesProvider(lastAppendIndexProvider::lastAppendIndex);
        }
        if (overrides.fileBasedOperationsOnly()) {
            return any -> {
                throw new UnsupportedOperationException("Current version of log files can't perform any operation that"
                        + " require availability of append index provider. Please build full version of log files to be"
                        + " able to use them.");
            };
        }
        if (overrides.readOnlyStores()) {
            requireNonNull(overrides.databaseLayout(), "Store directory is required.");
            return new ReadOnlyLastAppendIndexLogFilesProvider();
        } else {
            return any -> {
                throw new UnsupportedOperationException("Current version of log files can't perform any operation that"
                        + " require availability of append index provider. Please build full version of log files to be"
                        + " able to use them.");
            };
        }
    }

    private LastClosedPositionProvider closePositionProvider() {
        if (overrides.lastClosedPositionSupplier() != null) {
            return any -> overrides.lastClosedPositionSupplier().get();
        }
        if (overrides.transactionIdStore() != null) {
            return any ->
                    overrides.transactionIdStore().getLastClosedTransaction().logPosition();
        }
        if (overrides.fileBasedOperationsOnly()) {
            return any -> {
                throw new UnsupportedOperationException("Current version of log files can't perform any operation that"
                        + " require availability of transaction id store. Please build full version of log files to be"
                        + " able to use them.");
            };
        }
        if (overrides.readOnlyStores()) {
            requireNonNull(overrides.databaseLayout(), "Store directory is required.");
            return logFiles -> logFiles.getTailMetadata().getLastTransactionLogPosition();
        } else {
            return any -> logMetadataProvider.getLastClosedTransaction().logPosition();
        }
    }
}
