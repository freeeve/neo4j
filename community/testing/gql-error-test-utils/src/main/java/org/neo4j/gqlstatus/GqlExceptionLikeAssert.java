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
package org.neo4j.gqlstatus;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.assertj.core.internal.Throwables;

/**
 * Provides assertions against ErrorGqlStatusObject for Throwables that implement ErrorGqlStatusObject.
 * There are multiple exceptions extending ErrorGqlStatusObject, thus this is not implemented directly
 * against GqlException, and thus the generics on many methods.
 */
public class GqlExceptionLikeAssert extends AbstractThrowableAssert<GqlExceptionLikeAssert, Throwable>
        implements ErrorGqlStatusObjectAssertDelegate<GqlExceptionLikeAssert> {
    private final Throwables throwables = Throwables.instance();

    protected <T extends Throwable & ErrorGqlStatusObject> GqlExceptionLikeAssert(T t) {
        super(t, GqlExceptionLikeAssert.class);
        if (actual == null) {
            failWithMessage("Expecting code to raise a Throwable that implements ErrorGqlStatusObject.");
        }
    }

    /**
     * Cast a Throwable that extends ErrorGqlStatusObject to the correct type.
     * Return null otherwise.
     */
    private static <T extends Throwable & ErrorGqlStatusObject> T asT(Throwable t) {
        if (t instanceof ErrorGqlStatusObject) {
            //noinspection unchecked
            return (T) t;
        }
        return null;
    }

    /**
     * Catch a Throwable that also implements ErrorGqlStatusObject and return it.
     * Return null if no such Throwable is thrown.
     */
    public static <T extends Throwable & ErrorGqlStatusObject> T catchGqlException(
            ThrowableAssert.ThrowingCallable shouldRaiseGqlException) {
        try {
            shouldRaiseGqlException.call();
        } catch (Throwable t) {
            return asT(t);
        }
        return null;
    }

    /**
     * Expose actual for better compatibility with other asserts.
     */
    public <T extends Throwable & ErrorGqlStatusObject> T getActual() {
        //noinspection unchecked
        return (T) actual;
    }

    /**
     * Use this instead of {@link ThrowableAssert#cause()} to assert on a cause that is expected to also implement ErrorGqlStatusObject.
     */
    public GqlExceptionLikeAssert causeWithGqlStatus() {
        throwables.assertHasCause(info, actual);
        var cause = actual.getCause();
        if (cause instanceof ErrorGqlStatusObject) {
            return new GqlExceptionLikeAssert(asT(cause));
        }
        throw failure("Expected cause to be a Throwable implementing ErrorGqlStatusObject, but was: %s", cause);
    }

    @Override
    public ErrorGqlStatusObjectAssert<?> gqlStatusObject() {
        return new ErrorGqlStatusObjectAssertImplementation(asT(actual).gqlStatusObject());
    }
}
