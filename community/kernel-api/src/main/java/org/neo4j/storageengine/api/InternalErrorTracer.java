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
package org.neo4j.storageengine.api;

import java.util.concurrent.atomic.LongAdder;
import org.neo4j.exceptions.InvalidArgumentException;
import org.neo4j.graphdb.TransientFailureException;
import org.neo4j.internal.kernel.api.exceptions.ConstraintViolationTransactionFailureException;
import org.neo4j.internal.kernel.api.exceptions.schema.TokenLengthLimitExceededException;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.impl.api.LeaseException;
import org.neo4j.kernel.impl.locking.LockClientStoppedException;
import org.neo4j.logging.InternalLog;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.monitoring.ExceptionHandlerService;

public interface InternalErrorTracer {
    long internalReadErrors();

    long internalWriteErrors();

    void traceReadError(Throwable throwable);

    void traceWriteError(Throwable throwable);

    InternalErrorTracer NO_TRACER = new InternalErrorTracer() {
        @Override
        public void traceReadError(Throwable throwable) {}

        @Override
        public void traceWriteError(Throwable throwable) {}

        @Override
        public long internalReadErrors() {
            return 0;
        }

        @Override
        public long internalWriteErrors() {
            return 0;
        }
    };

    class Impl implements InternalErrorTracer {
        private final LongAdder internalReadErrors = new LongAdder();
        private final LongAdder internalWriteErrors = new LongAdder();
        private final InternalLog log;
        private final ExceptionHandlerService exceptionHandlerService;

        public Impl(InternalLogProvider logProvider, ExceptionHandlerService exceptionHandlerService) {
            log = logProvider.getLog(InternalErrorTracer.class);
            this.exceptionHandlerService = exceptionHandlerService;
        }

        @Override
        public long internalReadErrors() {
            return internalReadErrors.sum();
        }

        @Override
        public long internalWriteErrors() {
            return internalWriteErrors.sum();
        }

        @Override
        public void traceReadError(Throwable throwable) {
            if (isUnexpected(throwable)) {
                log.info("Internal read error observed", throwable);
                internalReadErrors.increment();
                exceptionHandlerService.raiseException("Internal read error observed", throwable);
            }
        }

        @Override
        public void traceWriteError(Throwable throwable) {
            if (isUnexpected(throwable)) {
                log.info("Internal write error observed", throwable);
                internalWriteErrors.increment();
                exceptionHandlerService.raiseException("Internal write error observed", throwable);
            }
        }

        private static boolean isUnexpected(Throwable throwable) {
            return !isTransient(throwable)
                    && !isConstraintViolation(throwable)
                    && !isLockClientTermination(throwable)
                    && !isLeaseException(throwable)
                    && !isTokenLengthLimit(throwable)
                    && !isPropertyValueTooBig(throwable);
        }

        private static boolean isLeaseException(Throwable throwable) {
            return throwable instanceof LeaseException;
        }

        private static boolean isLockClientTermination(Throwable throwable) {
            return throwable instanceof LockClientStoppedException;
        }

        private static boolean isConstraintViolation(Throwable throwable) {
            return throwable instanceof ConstraintViolationTransactionFailureException;
        }

        private static boolean isTokenLengthLimit(Throwable throwable) {
            return throwable instanceof TokenLengthLimitExceededException;
        }

        private static boolean isTransient(Throwable throwable) {
            return throwable instanceof TransientFailureException
                    || throwable instanceof Status.HasStatus hasStatus
                            && hasStatus.status().code().classification() == Status.Classification.TransientError;
        }

        private static boolean isPropertyValueTooBig(Throwable throwable) {
            return throwable instanceof InvalidArgumentException iae
                    && iae.gqlStatus().equals("22NBF");
        }
    }
}
