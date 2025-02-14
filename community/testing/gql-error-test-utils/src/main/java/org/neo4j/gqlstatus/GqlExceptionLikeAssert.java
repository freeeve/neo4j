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

import org.assertj.core.api.ThrowableAssert;
import org.neo4j.driver.exceptions.Neo4jException;

/**
 * Provides assertions against ErrorGqlStatusObject for Throwables
 * that implement ErrorGqlStatusObject and driver Neo4jException.
 * There are multiple exceptions extending ErrorGqlStatusObject, thus this is not implemented directly
 * against GqlException, and thus the generics on many methods.
 */
public class GqlExceptionLikeAssert extends ThrowableWithPotentialGqlCauseAssert<GqlExceptionLikeAssert>
        implements ErrorGqlStatusObjectAssertDelegate<GqlExceptionLikeAssert> {

    protected <T extends Throwable> GqlExceptionLikeAssert(T t) {
        super(t, GqlExceptionLikeAssert.class);
        if (actual == null) {
            failWithMessage(
                    "Expecting code to raise a Throwable that implements ErrorGqlStatusObject or Neo4jException.");
        }
    }

    /**
     * Cast a Throwable that extends ErrorGqlStatusObject to the correct type.
     * Return null otherwise.
     */
    static <T extends Throwable & ErrorGqlStatusObject> T asT(Throwable t) {
        if (t instanceof ErrorGqlStatusObject) {
            //noinspection unchecked
            return (T) t;
        }
        return null;
    }

    /**
     * Cast a Throwable that extends Neo4jException to the correct type.
     * Return null otherwise.
     */
    static Neo4jException asDriverException(Throwable t) {
        if (t instanceof Neo4jException driverException) {
            return driverException;
        }
        return null;
    }

    /**
     * Catch a Throwable that also implements ErrorGqlStatusObject or Driver Neo4jException and return it.
     * Return null if no such Throwable is thrown.
     */
    public static Throwable catchGqlException(ThrowableAssert.ThrowingCallable shouldRaiseGqlException) {
        try {
            shouldRaiseGqlException.call();
        } catch (Neo4jException e) {
            return asDriverException(e);
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

    @Override
    public ErrorGqlStatusObjectAssert<?> gqlStatusObject() {
        if (actual instanceof Neo4jException) {
            return new DriverExceptionAssertImplementation(asDriverException(actual));
        }
        return new ErrorGqlStatusObjectAssertImplementation(asT(actual).gqlStatusObject());
    }
}
