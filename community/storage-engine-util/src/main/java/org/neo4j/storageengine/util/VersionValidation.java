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
package org.neo4j.storageengine.util;

import static org.neo4j.lock.ResourceType.PAGE;

import java.io.IOException;
import org.neo4j.io.layout.DatabaseFile;
import org.neo4j.io.pagecache.PageCursor;
import org.neo4j.io.pagecache.context.VersionContext;
import org.neo4j.kernel.impl.monitoring.TransactionMonitor;
import org.neo4j.lock.LockTracer;
import org.neo4j.lock.ResourceLocker;
import org.neo4j.storageengine.api.txstate.validation.TransactionConflictException;

public class VersionValidation {
    private static final short PAGE_ID_BITS = 54;

    public static void validatePageVersion(
            DatabaseFile databaseFile,
            long pageId,
            VersionContext versionContext,
            PageCursor pageCursor,
            long position,
            boolean failFast,
            ResourceLocker validationLockClient,
            TransactionMonitor transactionMonitor,
            LockTracer lockTracer)
            throws IOException {

        long nodeId = pageId | (position << PAGE_ID_BITS);
        if (failFast) {
            if (!validationLockClient.tryExclusiveLock(PAGE, nodeId)) {
                throw new TransactionConflictException(databaseFile, pageId);
            }
        } else {
            validationLockClient.acquireExclusive(lockTracer, PAGE, nodeId);
        }

        if (pageCursor.next(pageId)) {
            if (versionContext.invisibleHeadObserved()) {
                transactionMonitor.transactionValidationFailure(databaseFile);
                throw new TransactionConflictException(databaseFile, versionContext, pageId);
            }
        }
    }
}
