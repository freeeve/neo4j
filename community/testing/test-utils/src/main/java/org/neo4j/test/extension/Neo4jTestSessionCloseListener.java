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
package org.neo4j.test.extension;

import static org.neo4j.io.memory.ByteBuffers.BUFFER_LEAK_TRACKER;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class Neo4jTestSessionCloseListener implements LauncherSessionListener {

    private static final String MISSING_TEST_PLAN = "<Missing test plan>";
    private final ConcurrentHashMap<LauncherSession, TestPlan> sessions = new ConcurrentHashMap<>();

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        session.getLauncher().registerTestExecutionListeners(new TestExecutionListener() {
            @Override
            public void testPlanExecutionFinished(TestPlan testPlan) {
                sessions.put(session, testPlan);
            }
        });
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        var testPlan = sessions.remove(session);
        BUFFER_LEAK_TRACKER.checkLeaks(() -> describeTestPlan(testPlan));
    }

    String describeTestPlan(TestPlan testPlan) {
        if (testPlan == null) {
            return MISSING_TEST_PLAN;
        }
        var identifiers = new HashSet<String>();
        testPlan.accept(new TestPlan.Visitor() {
            @Override
            public void visit(TestIdentifier testIdentifier) {
                if (testIdentifier.isTest()) {
                    identifiers.add(testIdentifier.getUniqueId());
                }
            }
        });
        return identifiers.stream().sorted().collect(Collectors.joining(System.lineSeparator()));
    }
}
