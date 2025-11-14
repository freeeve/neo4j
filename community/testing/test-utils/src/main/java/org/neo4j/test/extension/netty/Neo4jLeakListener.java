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
package org.neo4j.test.extension.netty;

import io.netty.util.ResourceLeakDetector;
import java.util.function.Supplier;

public class Neo4jLeakListener implements ResourceLeakDetector.LeakListener {

    private static volatile String leakDescription;

    @Override
    public void onLeak(String resourceType, String records) {
        leakDescription = records;
    }

    public static void checkLeaks(Supplier<String> testPlanDescriptionSupplier) {
        var description = leakDescription;
        if (description == null) {
            return;
        }
        String exceptionBuilder = """
                                                      ***WARNING***
                Netty buffer leak has been detected. The test session will be marked as failed.
                Please review the details of unreleased allocations from the tests executed in the current session below.
                Last executed tests:
                """
                + testPlanDescriptionSupplier.get()
                + System.lineSeparator()
                + System.lineSeparator()
                + "Leak description:"
                + System.lineSeparator() + description;
        throw new RuntimeException(exceptionBuilder);
    }
}
