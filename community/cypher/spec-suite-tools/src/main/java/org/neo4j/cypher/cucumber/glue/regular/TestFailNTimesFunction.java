package org.neo4j.cypher.cucumber.glue.regular;

import java.util.concurrent.ConcurrentHashMap;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;
import org.neo4j.values.AnyValue;

public class TestFailNTimesFunction {
    @Context
    public State state;

    @UserFunction("test.failNTimes")
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
