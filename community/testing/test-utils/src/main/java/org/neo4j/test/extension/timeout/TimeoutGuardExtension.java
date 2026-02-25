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
package org.neo4j.test.extension.timeout;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.PreInterruptCallback;
import org.junit.jupiter.api.extension.PreInterruptContext;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.internal.helpers.NamedThreadFactory;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.impl.muninn.MuninnPageCache;
import org.neo4j.memory.HeapDumper;

public class TimeoutGuardExtension implements PreInterruptCallback {
    /**
     * Namespace for storing timeout specific auxiliary data
     */
    public static final ExtensionContext.Namespace TIMEOUT_NAMESPACE = ExtensionContext.Namespace.create("timeout");
    /**
     * The key used for storing an auxiliary {@link String} message
     */
    public static final String TIMEOUT_MESSAGE = "timeoutMessage";

    private static final String DBMS_KEY = "service";
    private static final ExtensionContext.Namespace DBMS_NAMESPACE =
            ExtensionContext.Namespace.create("org", "neo4j", "dbms");
    private static final String TEST_DATA = "test data";

    private static final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("HangingTestMonitor"));

    @Override
    public void beforeThreadInterrupt(PreInterruptContext preInterruptContext, ExtensionContext extensionContext) {
        // Most tests will handle interruptions and stop execution without issues. However, some may not, so we
        // will check their status after a period of time and terminate the VM if they are unresponsive.
        executor.schedule(
                new HangingTestWatchTask(preInterruptContext.getThreadToInterrupt(), extensionContext),
                1,
                TimeUnit.MINUTES);
    }

    private static class HangingTestWatchTask implements Runnable {
        private final Thread testThread;
        private final ExtensionContext extensionContext;

        public HangingTestWatchTask(Thread testThread, ExtensionContext extensionContext) {
            this.testThread = testThread;
            this.extensionContext = extensionContext;
        }

        @Override
        public void run() {
            var requiredTestMethod = extensionContext.getRequiredTestMethod();
            var clazz = requiredTestMethod.getDeclaringClass().getName();
            var methodName = requiredTestMethod.getName();
            for (StackTraceElement stackTraceElement : testThread.getStackTrace()) {
                if (clazz.equals(stackTraceElement.getClassName())
                        && methodName.equals(stackTraceElement.getMethodName())) {
                    var auxMessage =
                            extensionContext.getStore(TIMEOUT_NAMESPACE).get(TIMEOUT_MESSAGE, String.class);

                    var message = """
                                                  ***WARNING***
                        Test monitor terminating hanging execution for test %s.%s %s
                        After the test timeout was reached, an interruption attempt was made; however, the test did not progress within the allocated grace period. Terminating the VM.\
                        """.formatted(clazz, methodName, auxMessage == null ? "" : "[ " + auxMessage + " ]");

                    printWarning(System.out, message);
                    printWarning(System.err, message);
                    takeDump(extensionContext);
                    System.exit(1);
                }
            }
        }
    }

    private static void printWarning(PrintStream out, String message) {
        out.println(message);
        out.flush();
    }

    private static void takeDump(ExtensionContext extensionContext) {
        try {
            Path testData = findTestData(extensionContext);
            long timestamp = System.currentTimeMillis();
            dumpPageMetadata(
                    extensionContext, testData.resolve("hanging-test-" + timestamp + "-page-cache-metadata.txt"));
            new HeapDumper()
                    .createHeapDump(
                            testData.resolve("hanging-test-" + timestamp + ".hprof")
                                    .toAbsolutePath()
                                    .toString(),
                            true);
        } catch (Throwable t) {
            System.out.println("Failed to create heap dump or page cache metadata dump");
            //noinspection CallToPrintStackTrace
            t.printStackTrace();
        }
    }

    @SuppressWarnings("resource")
    private static void dumpPageMetadata(ExtensionContext extensionContext, Path pageMetadataFile)
            throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException {
        MuninnPageCache pageCache = findPageCache(extensionContext);
        if (pageCache == null) {
            return;
        }
        printWarning(System.out, "Dumping page cache metadata to " + pageMetadataFile.toAbsolutePath());
        try (var writer = Files.newBufferedWriter(
                pageMetadataFile,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            pageCache.dumpPageMetaData(writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Finds the "test data" directory in the same way as TestDirectoryExtension to put output there.
     * Does not resolve actual TestDirectory to be able to save heap into "test data" even for tests that don't use TestDirectoryExtension.
     */
    private static Path findTestData(ExtensionContext extensionContext) {
        return locateTarget(extensionContext.getRequiredTestMethod().getDeclaringClass())
                .resolve(TEST_DATA);
    }

    private static Path locateTarget(Class<?> owningTest) {
        try {
            Path codeSource = Path.of(owningTest
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            if (Files.isDirectory(codeSource)) {
                return codeSource.getParent();
            }
        } catch (URISyntaxException e) {
            // ignored
        }
        return Path.of("target");
    }

    private static MuninnPageCache findPageCache(ExtensionContext context)
            throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException {
        var store = context.getStore(DBMS_NAMESPACE);
        var dbms = store.get(DBMS_KEY, DatabaseManagementService.class);
        if (dbms == null) {
            return null;
        }
        String someDb = Iterables.firstOrNull(dbms.listDatabases());
        if (someDb == null) {
            return null;
        }
        var pageCache = pageCacheFromDbms(dbms, someDb);
        return unwrapDelegatingPageCache(unwrapDatabasePageCache(pageCache));
    }

    public static PageCache pageCacheFromDbms(DatabaseManagementService dbms, String databaseName) {
        try {
            var db = dbms.database(databaseName);
            var resolver = db.getClass().getMethod("getDependencyResolver").invoke(db);
            return (PageCache) resolver.getClass()
                    .getMethod("resolveDependency", Class.class)
                    .invoke(resolver, PageCache.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to resolve PageCache via reflection", e);
        }
    }

    private static PageCache unwrapDatabasePageCache(PageCache pageCache)
            throws NoSuchFieldException, IllegalAccessException {
        Class<?> wrapperClass = findDatabasePageCache();
        if (wrapperClass == null) {
            return pageCache;
        }
        if (wrapperClass.isInstance(pageCache)) {
            var globalField = wrapperClass.getDeclaredField("globalPageCache");
            globalField.setAccessible(true);
            return (PageCache) globalField.get(pageCache);
        }
        return pageCache;
    }

    private static MuninnPageCache unwrapDelegatingPageCache(PageCache pageCache) throws NoSuchMethodException {
        Class<?> delegatingClass = findDelegatingPageCache();
        if (delegatingClass == null) {
            return null;
        }
        var method = delegatingClass.getMethod("getDelegate");

        var pc = pageCache;
        while (delegatingClass.isInstance(pc)) {
            try {
                pc = (PageCache) method.invoke(pc);
            } catch (ReflectiveOperationException e) {
                break;
            }
        }
        if (pc instanceof MuninnPageCache muninnPageCache) {
            return muninnPageCache;
        }
        return null;
    }

    private static Class<?> findDatabasePageCache() {
        try {
            return Class.forName("org.neo4j.dbms.database.DatabasePageCache");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Class<?> findDelegatingPageCache() {
        try {
            return Class.forName("org.neo4j.io.pagecache.DelegatingPageCache");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
