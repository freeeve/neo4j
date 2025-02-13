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
package org.neo4j.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.eclipse.collections.api.block.function.Function;
import org.neo4j.internal.helpers.Exceptions;

public class JobHandles {
    /**
     * Calls {@link JobHandle#get()} on all jobs in {@code handles} and returns their results.
     *
     * @param handles the {@link JobHandle jobs} to get results for.
     * @return the results from those jobs.
     * @param <T> type of result instances.
     * @throws ExecutionException if there's one or more jobs throwing exception. The thrown exception often
     * wraps another actual {@link ExecutionException}.
     */
    public static <T> List<T> getAllResults(Collection<JobHandle<T>> handles) throws ExecutionException {
        List<T> result = new ArrayList<>(handles.size());
        Throwable finalError = null;
        for (JobHandle<T> handle : handles) {
            try {
                result.add(handle.get());
            } catch (Throwable e) {
                finalError = Exceptions.chain(finalError, e);
            }
        }
        if (finalError != null) {
            throw new ExecutionException(finalError);
        }
        return result;
    }

    /**
     * A more convenient variant of {@link #getAllResults(Collection)} with more customizable and convenient
     * exception handling.
     * @param handles the {@link JobHandle jobs} to get results for.
     * @param passThroughException type of exception to look for in the inner {@link ExecutionException} to
     * potentially unwrap and throw directly.
     * @param fallbackConvertToException if the actual exception didn't match the {@code passThroughException}
     * this will converter that exception to another exception to be thrown.
     * @return the results from those jobs.
     * @param <T> type of result instances.
     * @param <E> pass-through exception type.
     * @param <F> fallback exception type.
     * @throws E if the actual exception was of this type.
     * @throws F if the fallback exception convert was used.
     */
    public static <T, E extends Exception, F extends Exception> List<T> getAllResults(
            Collection<JobHandle<T>> handles,
            Class<E> passThroughException,
            Function<Throwable, F> fallbackConvertToException)
            throws E, F {
        try {
            return getAllResults(handles);
        } catch (ExecutionException e) {
            // this caught exception is a wrapper
            if (e.getCause() instanceof ExecutionException actualExecutionException) {
                var cause2 = actualExecutionException.getCause();
                if (passThroughException.isAssignableFrom(cause2.getClass())) {
                    throw passThroughException.cast(cause2);
                }
                throw fallbackConvertToException.apply(cause2);
            }
            throw fallbackConvertToException.apply(e.getCause());
        }
    }
}
