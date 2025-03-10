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
package org.neo4j.cypher.cucumber.glue.regular;

import java.util.concurrent.ConcurrentHashMap;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;
import org.neo4j.values.AnyValue;

public class TestFailNTimesFunction {
    public static final String name = "test.failNTimes";

    @Context
    public State state;

    @UserFunction(TestFailNTimesFunction.name)
    @Description("Throws an exception the first <n> times it is called for a given value of <key>")
    public AnyValue failNTimes(
            @Name("n") long n, @Name("key") AnyValue key, @Name("exceptionType") String exceptionType) {
        long i = state.incrementAndGet(key);
        var et =
                switch (exceptionType) {
                    case "client" -> FailNTimesException.ExceptionType.CLIENT;
                    case "transient" -> FailNTimesException.ExceptionType.TRANSIENT;
                    default -> throw new IllegalArgumentException("exceptionType");
                };
        if (i <= n) throw new FailNTimesException(i, n, et);
        return key;
    }

    public static class State {
        private final ConcurrentHashMap<AnyValue, Long> map = new ConcurrentHashMap<>();

        public long incrementAndGet(AnyValue key) {
            return map.compute(key, (_k, value) -> value == null ? 1 : value + 1);
        }
    }

    public static class FailNTimesException extends RuntimeException implements Status.HasStatus {
        private final ExceptionType type;

        public FailNTimesException(Long i, Long n, ExceptionType type) {
            super("Failing " + i + "/" + n);
            this.type = type;
        }

        @Override
        public Status status() {
            return switch (type) {
                case TRANSIENT -> Status.Transaction.Outdated;
                case CLIENT -> Status.Statement.SyntaxError;
            };
        }

        public enum ExceptionType {
            TRANSIENT,
            CLIENT,
        }
    }
}
