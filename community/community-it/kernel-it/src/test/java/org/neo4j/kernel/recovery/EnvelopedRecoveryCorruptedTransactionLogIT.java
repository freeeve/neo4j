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

import static org.neo4j.kernel.KernelVersion.VERSION_ENVELOPED_TRANSACTION_LOGS_INTRODUCED;
import static org.neo4j.test.LatestVersions.LATEST_RUNTIME_VERSION;

import java.util.Map;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.dbms.database.DbmsRuntimeVersion;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.kernel.KernelVersion;
import org.neo4j.kernel.impl.transaction.log.entry.LogEnvelopeHeader;

class EnvelopedRecoveryCorruptedTransactionLogIT extends RecoveryCorruptedTransactionLogIT {
    // TODO MERGELOG turn into no-op when default format
    @Override
    protected Map<Setting<?>, Object> additionalConfig() {
        return Map.of(
                GraphDatabaseInternalSettings.latest_kernel_version,
                VERSION_ENVELOPED_TRANSACTION_LOGS_INTRODUCED.version(),
                GraphDatabaseInternalSettings.latest_runtime_version,
                LATEST_RUNTIME_VERSION.kernelVersion().isAtLeast(VERSION_ENVELOPED_TRANSACTION_LOGS_INTRODUCED)
                        ? LATEST_RUNTIME_VERSION.getVersion()
                        : DbmsRuntimeVersion.GLORIOUS_FUTURE.getVersion());
    }

    @Override
    protected KernelVersion kernelVersion() {
        return VERSION_ENVELOPED_TRANSACTION_LOGS_INTRODUCED;
    }

    @Override
    protected int minimumBytesToConsiderARecordBroken() {
        // Anything the length of an envelope header or less is just considered a broken
        // last entry by default and will not be considered corrupted (just last broken entry which is recoverable).
        return LogEnvelopeHeader.HEADER_SIZE + 1;
    }
}
