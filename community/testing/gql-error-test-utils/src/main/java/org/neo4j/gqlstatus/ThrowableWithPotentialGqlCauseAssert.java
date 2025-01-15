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

import static org.neo4j.gqlstatus.GqlExceptionLikeAssert.asT;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.assertj.core.internal.Throwables;
import org.neo4j.kernel.api.exceptions.Status;

/**
 * Same as a regular ThrowableAssert, but allows to navigate to a cause that implements ErrorGqlStatusObject
 * and do GQL assertions against it.
 */
public class ThrowableWithPotentialGqlCauseAssert<SELF extends ThrowableWithPotentialGqlCauseAssert<SELF>>
        extends AbstractThrowableAssert<SELF, Throwable> {
    private final Throwables throwables = Throwables.instance();

    ThrowableWithPotentialGqlCauseAssert(Throwable t) {
        super(t, ThrowableWithPotentialGqlCauseAssert.class);
    }

    protected ThrowableWithPotentialGqlCauseAssert(Throwable t, Class<?> selfType) {
        super(t, selfType);
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

    /**
     * Assert that the status of this Throwable is the expected status.
     * Assumes that the throwable implements Status.HasStatus, will throw an exception otherwise.
     */
    public SELF hasStatus(Status status) {
        if (!(actual instanceof Status.HasStatus hS)) {
            throw failure("Expected actual to be a Throwable implementing Status.HasStatus, but was: %s", actual);
        }
        if (hS.status() != status) {
            throw failure("Expected status to be %s, but was: %s", status, hS.status());
        }
        return myself;
    }
}
