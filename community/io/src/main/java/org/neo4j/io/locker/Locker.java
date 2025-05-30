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
package org.neo4j.io.locker;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.fs.StoreChannel;

/**
 * The class takes a lock on provided file. The lock is valid after a successful call to
 * {@link #checkLock()} until a call to {@link #close()}.
 */
public class Locker implements Closeable {
    private final FileSystemAbstraction fileSystemAbstraction;
    private final Path lockFile;

    protected FileLock lockFileLock;
    private StoreChannel lockFileChannel;

    public Locker(FileSystemAbstraction fileSystemAbstraction, Path lockFile) {
        this.fileSystemAbstraction = fileSystemAbstraction;
        this.lockFile = lockFile;
    }

    public final Path lockFile() {
        return lockFile;
    }

    /**
     * Obtains lock on file so that we can ensure the store is not shared between different database instances
     * <p>
     * Creates dir if necessary, creates lock file if necessary
     * <p>
     * Please note that this lock is only valid for as long the {@link #lockFileChannel} lives, so make sure the
     * lock cannot be garbage collected as long as the lock should be valid.
     *
     * @throws FileLockException if lock could not be acquired
     */
    public void checkLock() {
        if (haveLockAlready()) {
            return;
        }

        try {
            if (!fileSystemAbstraction.fileExists(lockFile)) {
                fileSystemAbstraction.mkdirs(lockFile.getParent());
            }
        } catch (IOException e) {
            String message = "Unable to create path for dir: " + lockFile.getParent();
            throw storeLockException(message, e);
        }

        try {
            if (lockFileChannel == null) {
                lockFileChannel = fileSystemAbstraction.write(lockFile);
            }
            lockFileLock = lockFileChannel.tryLock();
            if (lockFileLock == null) {
                String message = "Lock file has been locked by another process: " + lockFile;
                throw storeLockException(message, null);
            }
        } catch (OverlappingFileLockException e) {
            throw unableToObtainLockException();
        } catch (IOException e) {
            // This isn't your normal "locked by another process" error, it may be related to permissions or something
            // else,
            // so in this case try to figure out as much as possible about the state of the file and directory and
            // include that
            // in the error message given to the user.
            throw unableToObtainLockException(tryCollectPermissionInformation(fileSystemAbstraction, lockFile), e);
        }
    }

    public static String tryCollectPermissionInformation(FileSystemAbstraction fs, Path file) {
        String processUserName = ProcessHandle.current().info().user().orElse(System.getProperty("user.name"));
        // The user.name can be "?" in some linux setups
        if (processUserName != null && !processUserName.equals("?")) {
            String filePermissionInformation = null;
            try {
                filePermissionInformation = tryCollectPermissionInformation(processUserName, file);
            } catch (IOException e) {
                // We tried to get the owner of the file, but we couldn't. Let's check the folder, if this was a file.
            }

            if (filePermissionInformation == null && !fs.isDirectory(file)) {
                try {
                    filePermissionInformation = tryCollectPermissionInformation(processUserName, file.getParent());
                } catch (IOException ex) {
                    // We tried to get the owner of the directory, but couldn't. There's not much more we can do
                }
            }
            return filePermissionInformation;
        }
        return null;
    }

    private static String tryCollectPermissionInformation(String processUserName, Path file) throws IOException {
        String fileOwner = Files.getOwner(file).getName();
        if (!processUserName.equals(fileOwner)) {
            return String.format(
                    "Owner '%s' of '%s' and user running this process '%s' differs, potentially resulting in a file permission problem. "
                            + "Ensure that the file has the same owner, or at least has write access for the user running the Neo4j process "
                            + "trying to use it.",
                    fileOwner, file, processUserName);
        }
        // else no useful additional information can be provided
        return null;
    }

    protected boolean haveLockAlready() {
        return lockFileLock != null && lockFileChannel != null;
    }

    protected FileLockException unableToObtainLockException() {
        return unableToObtainLockException(null, null);
    }

    protected FileLockException unableToObtainLockException(String additionalInformation, Exception cause) {
        String message = String.format(
                "Unable to obtain lock on file: %s%s",
                lockFile, additionalInformation != null ? ": " + additionalInformation : "");
        return storeLockException(message, cause);
    }

    private static FileLockException storeLockException(String message, Exception e) {
        String help = "Please ensure no other process is using this database, and that the directory is writable "
                + "(required even for read-only access)";
        return new FileLockException(message + ". " + help, e);
    }

    @Override
    public void close() throws IOException {
        if (lockFileLock != null) {
            releaseLock();
        }
        if (lockFileChannel != null) {
            releaseChannel();
        }
    }

    private void releaseChannel() throws IOException {
        lockFileChannel.close();
        lockFileChannel = null;
    }

    protected void releaseLock() throws IOException {
        lockFileLock.release();
        lockFileLock = null;
    }
}
