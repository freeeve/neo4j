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
package org.neo4j.cli;

import static java.lang.String.format;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.neo4j.function.ThrowingConsumer;
import org.neo4j.io.fs.FileSystemAbstraction;
import picocli.CommandLine;

public class CommandTestUtils {
    /**
     * Creates an {@link ExecutionContext} which captures System.out and System.err and will include
     * captured prints in an exception if the {@code command} fails. This allows commands to run w/o the
     * need to print to the system-wide System.out/System.err and still get all that information on failure.
     *
     * @param homeDir home directory to give to the {@link ExecutionContext}.
     * @param confDir config directory to give to the {@link ExecutionContext}.
     * @param fs file system this is run on.
     * @param command to set up and run the command, given this created {@link ExecutionContext}.
     */
    public static CapturingExecutionContext withSuppressedOutput(
            Path homeDir,
            Path confDir,
            FileSystemAbstraction fs,
            ThrowingConsumer<CapturingExecutionContext, Throwable> command) {
        var executionContext = capturingExecutionContext(homeDir, confDir, fs);
        try {
            command.accept(executionContext);
            return executionContext;
        } catch (Throwable e) {
            throw new RuntimeException(
                    format(
                            "%nCaptured System.out:%n%s%nCaptured System.err:%n%s",
                            executionContext.outAsString(), executionContext.errAsString()),
                    e);
        }
    }

    /**
     * Runs a command with suppressed output. The difference between this method and {@link #runAdminToolWithSuppressedOutput(Path, Path, FileSystemAbstraction, String...)}
     * is that this method calls {@link AbstractCommand#execute()} directly which propagates its exception,
     * whereas the other one calls {@link AdminTool#execute(ExecutionContext, String...)} which just returns
     * the exit code of the command.
     *
     * @param homeDir home directory to give to the {@link ExecutionContext}.
     * @param confDir config directory to give to the {@link ExecutionContext}.
     * @param fs file system this is run on.
     * @param command instantiator of the command.
     * @param args arguments to the command.
     */
    public static CapturingExecutionContext runCommandWithSuppressedOutput(
            Path homeDir,
            Path confDir,
            FileSystemAbstraction fs,
            Function<ExecutionContext, AbstractCommand> command,
            String... args) {
        return withSuppressedOutput(homeDir, confDir, fs, ctx -> {
            var cmd = command.apply(ctx);
            CommandLine.populateCommand(cmd, args);
            cmd.execute();
        });
    }

    @FunctionalInterface
    public interface TestCallable {
        void call(CapturingExecutionContext ctx) throws Exception;
    }

    public static CapturingExecutionContext callWithSuppressedOutput(ExecutionContext ctx, TestCallable callable) {
        var executionContext = capturingExecutionContext(ctx.homeDir(), ctx.confDir(), ctx.fs());
        try {
            callable.call(executionContext);
            return executionContext;
        } catch (Throwable e) {
            throw new RuntimeException(
                    format(
                            "%nCaptured System.out:%n%s%nCaptured System.err:%n%s",
                            executionContext.outAsString(), executionContext.errAsString()),
                    e);
        }
    }

    /**
     * Runs the {@link AdminTool} with suppressed output.
     *
     * @param homeDir home directory to give to the {@link ExecutionContext}.
     * @param confDir config directory to give to the {@link ExecutionContext}.
     * @param fs file system this is run on.
     * @param args arguments to the admin tool
     */
    public static CapturingExecutionContext runAdminToolWithSuppressedOutput(
            Path homeDir, Path confDir, FileSystemAbstraction fs, String... args) {
        return withSuppressedOutput(homeDir, confDir, fs, ctx -> AdminTool.execute(ctx, args));
    }

    public static CapturingExecutionContext capturingExecutionContext(
            Path homeDir, Path confDir, FileSystemAbstraction fs) {
        var rawOut = new ByteArrayOutputStream();
        var rawErr = new ByteArrayOutputStream();
        var out = new PrintStream(rawOut);
        var err = new PrintStream(rawErr);
        return new CapturingExecutionContext(homeDir, confDir, rawOut, rawErr, out, err, fs);
    }

    public static class CapturingExecutionContext extends ExecutionContext {
        private final ByteArrayOutputStream rawOut;
        private final ByteArrayOutputStream rawErr;

        CapturingExecutionContext(
                Path homePath,
                Path confPath,
                ByteArrayOutputStream rawOut,
                ByteArrayOutputStream rawErr,
                PrintStream out,
                PrintStream err,
                FileSystemAbstraction fs) {
            super(homePath, confPath, out, err, fs);
            this.rawOut = rawOut;
            this.rawErr = rawErr;
        }

        public String outAsString() {
            out().flush();
            return rawOut.toString();
        }

        public String errAsString() {
            err().flush();
            return rawErr.toString();
        }
    }

    // A collection of miscellaneous utils

    /**
     * Find the latest file in a directory, e.g. when searching a set of backups
     *
     * @param fs filesystem to use
     * @param path to search
     * @return the path of the latest file, or null if no files
     * @throws IOException if there is an IO problem accessing the filesystem
     */
    public static Path latestFileInDirectory(FileSystemAbstraction fs, Path path) throws IOException {
        var list = filesByTime(fs, path);
        if (list.isEmpty()) {
            return null;
        }
        return list.getLast();
    }

    /**
     * The escaped (eg ' ' -> %20) version of a path string
     * @param path to convert to an escaped string
     * @return The escaped string (eg ' ' -> %20) from the input path
     * @throws URISyntaxException
     */
    public static String escapedPath(String path) throws URISyntaxException {
        return new URI(null, null, path, null).getRawPath();
    }

    /**
     * Count the number of lines which contain the supplied string as a substring
     * @param lines the stream of lines to search
     * @param substring to look for
     * @return n, where n is the number of lines which contain the supplied string as a substring
     */
    public static long containCount(Stream<String> lines, String substring) {
        return lines.filter(s -> s.contains(substring)).count();
    }

    private static List<Path> filesByTime(FileSystemAbstraction fs, Path path) throws IOException {
        var files = Arrays.asList(fs.listFiles(path));
        files.sort((o1, o2) -> {
            try {
                return (int) (fs.lastModifiedTime(o1) - fs.lastModifiedTime(o2));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return files;
    }
}
