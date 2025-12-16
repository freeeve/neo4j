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
package org.neo4j.kernel;

import static org.neo4j.kernel.impl.transaction.log.entry.LogFormat.V11;

import java.util.HashMap;
import java.util.Map;

/**
 * This version is used to update databases after a DBMS has been updated to a new version. The flushed log may change
 * in two ways. Either the header may change which will be signaled by a bump in the logFormatVersion, or the content
 * marshallers have changed. KernelVersion may change how transactions are serialized but will also define what is the
 * active kernel version once this version has been fully applied by the system.
 * <p>
 * Any change to the log serialization will be triggered immediately in the appended state, hence those changes may still
 * be truncated if the system is Rafted.
 * <p>
 * Note: V0 is the initial version, any member on this version should behave as if no raft upgrade mechanism exists.
 * This is useful for when the binaries have updated, but the database is still rolling from an older version that is
 * not aware of the Raft upgrade mechanism.
 * */
public enum DatabaseVersion {
    V0((byte) 0, (byte) -1, (byte) -1, (byte) -1),
    V1((byte) 1, KernelVersion.GLORIOUS_FUTURE.version(), 5, V11.getVersionByte()),
    V2((byte) 2, KernelVersion.GLORIOUS_FUTURE.version(), 7, V11.getVersionByte());

    // Maps for fast lookups given a version number, kernel version, or DBMS runtime version
    private static final Map<Byte, DatabaseVersion> BY_VERSION = new HashMap<>();

    static {
        for (DatabaseVersion e : values()) {
            BY_VERSION.put(e.identifier, e);
        }
    }

    private static final DatabaseVersion LATEST_DATABASE_VERSION = V2;

    private final byte identifier;
    private final byte kernelVersion;
    private final int contentMarshallerVersion;
    private final byte logFormatHeader;

    DatabaseVersion(byte identifier, byte kernelVersion, int contentMarshallerVersion, byte logFormatHeader) {
        this.identifier = identifier;
        this.kernelVersion = kernelVersion;
        this.contentMarshallerVersion = contentMarshallerVersion;
        this.logFormatHeader = logFormatHeader;
    }

    public byte identifier() {
        return identifier;
    }

    public byte kernelVersion() {
        return kernelVersion;
    }

    public int contentMarshallerVersion() {
        return contentMarshallerVersion;
    }

    public byte getLogFormatHeader() {
        return logFormatHeader;
    }

    public static DatabaseVersion getLatestVersion() {
        return LATEST_DATABASE_VERSION;
    }

    public boolean isHigherThan(DatabaseVersion other) {
        return this.identifier > other.identifier;
    }

    public static DatabaseVersion fromVersionNumber(byte versionNumber) {
        var v = BY_VERSION.get(versionNumber);
        if (v == null) {
            throw new IllegalArgumentException(
                    "DatabaseVersion with version number " + versionNumber + " does not exist");
        }
        return v;
    }
}
