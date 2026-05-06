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
package org.neo4j.fleetmanagement.migration.aura;

import static java.lang.String.format;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.neo4j.cli.ExecutionContext;
import org.neo4j.fleetmanagement.communication.model.MigrationTokenMessage;
import org.neo4j.fleetmanagement.communication.model.UpdateMigrationStatusMessage;
import org.neo4j.io.fs.FileSystemAbstraction;

public class CapturingExecutionContext extends ExecutionContext {
    private static final Pattern URL_QUERY_PATTERN = Pattern.compile("(https?://\\S*?)\\?\\S*");

    // Guards every access to rawOut/rawErr: the writes performed by the migration thread
    // through the PrintStreams given to super(), and the reads/resets performed by the
    // reporting thread through the methods on this class. The migration writer thread
    // and the reporting thread are different threads (see MigrationToAuraService), so
    // without this shared lock the writes and reads would race on the underlying buffers.
    //
    // Lock ordering: writers always acquire the PrintStream's own monitor first (inside
    // PrintStream.write/print/println) and then this lock via lockedStream(...). Readers
    // only ever take this lock and never the PrintStream monitor, which keeps the order
    // consistent and prevents deadlock.
    private final Object outputLock;

    private final ByteArrayOutputStream rawOut;
    private final ByteArrayOutputStream rawErr;
    private final String dbmsId;
    private final String serverId;
    private final String projectId;
    private final String migrationId;
    private volatile UpdateMigrationStatusMessage.MigrationStep migrationStep;

    public static CapturingExecutionContext create(
            Path homePath,
            Path confPath,
            FileSystemAbstraction fs,
            String dbmsId,
            String serverId,
            String projectId,
            String migrationId) {
        Object lock = new Object();
        ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
        ByteArrayOutputStream rawErr = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(lockedStream(rawOut, lock), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(lockedStream(rawErr, lock), true, StandardCharsets.UTF_8);
        return new CapturingExecutionContext(
                homePath, confPath, rawOut, rawErr, out, err, fs, lock, dbmsId, serverId, projectId, migrationId);
    }

    private CapturingExecutionContext(
            Path homePath,
            Path confPath,
            ByteArrayOutputStream rawOut,
            ByteArrayOutputStream rawErr,
            PrintStream out,
            PrintStream err,
            FileSystemAbstraction fs,
            Object outputLock,
            String dbmsId,
            String serverId,
            String projectId,
            String migrationId) {
        super(homePath, confPath, out, err, fs);
        this.rawOut = rawOut;
        this.rawErr = rawErr;
        this.outputLock = outputLock;
        this.dbmsId = dbmsId;
        this.serverId = serverId;
        this.projectId = projectId;
        this.migrationId = migrationId;
        this.migrationStep = UpdateMigrationStatusMessage.MigrationStep.DUMP;
    }

    public String getExecutionOutput() {
        synchronized (outputLock) {
            return format("Captured System.out:%n%s%nCaptured System.err:%n%s", outAsString(), errAsString());
        }
    }

    public UpdateMigrationStatusMessage newUpdateMessageWithLogs() {
        UpdateMigrationStatusMessage.MigrationStepLogs logs;
        synchronized (outputLock) {
            logs = new UpdateMigrationStatusMessage.MigrationStepLogs(outAsString(), errAsString());
        }
        UpdateMigrationStatusMessage msg = newUpdateMessage();
        msg.logs = logs;
        return msg;
    }

    public UpdateMigrationStatusMessage newUpdateMessage() {
        UpdateMigrationStatusMessage msg = new UpdateMigrationStatusMessage();
        msg.dbmsId = dbmsId;
        msg.serverId = serverId;
        msg.projectId = projectId;
        msg.migrationId = migrationId;
        msg.migrationStep = migrationStep;
        return msg;
    }

    public MigrationTokenMessage newMigrationTokenRequest() {
        MigrationTokenMessage msg = new MigrationTokenMessage();
        msg.dbmsId = dbmsId;
        msg.serverId = serverId;
        msg.projectId = projectId;
        msg.migrationId = migrationId;
        return msg;
    }

    public void setMigrationStep(UpdateMigrationStatusMessage.MigrationStep migrationStep) {
        synchronized (outputLock) {
            this.migrationStep = migrationStep;
            rawOut.reset();
            rawErr.reset();
        }
    }

    private String outAsString() {
        synchronized (outputLock) {
            return obfuscateUrls(rawOut.toString());
        }
    }

    private String errAsString() {
        synchronized (outputLock) {
            return obfuscateUrls(rawErr.toString());
        }
    }

    private static String obfuscateUrls(String s) {
        return URL_QUERY_PATTERN.matcher(s).replaceAll("$1?<obfuscated>");
    }

    /**
     * Wraps {@code delegate} in an {@link OutputStream} that takes {@code lock} around every
     * write. This is what lets the migration thread (writer, via {@link PrintStream}) and the
     * reporting thread (reader/resetter, via this class' methods) safely share the underlying
     * {@link ByteArrayOutputStream}.
     */
    private static OutputStream lockedStream(ByteArrayOutputStream delegate, Object lock) {
        return new OutputStream() {
            @Override
            public void write(int b) {
                synchronized (lock) {
                    delegate.write(b);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) {
                synchronized (lock) {
                    delegate.write(b, off, len);
                }
            }
        };
    }
}
