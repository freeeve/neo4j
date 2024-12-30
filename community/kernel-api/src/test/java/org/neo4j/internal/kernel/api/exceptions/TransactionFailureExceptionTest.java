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
package org.neo4j.internal.kernel.api.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransactionFailureExceptionTest {

    @Test
    void testCannotRollbackCannotDropCreatedConstraintIndex() {
        var e = TransactionFailureException.cannotRollbackCannotDropCreatedConstraintIndex(
                new IllegalStateException("boom"));
        assertThat(e).hasMessageContaining("Could not drop created constraint indexes");
        assertThat(e.gqlStatus()).isEqualTo("40N01");
        assertThat(e.statusDescription())
                .isEqualTo(
                        "error: transaction rollback - rollback failed. Failed to rollback transaction. See debug log for details.");
        assertThat(e.cause()).isPresent();
        var cause = e.cause().get();
        assertThat(cause.gqlStatus()).isEqualTo("50N10");
        // Unfortunately we don't get a nice index name in the error, since that information is not available.
        assertThat(cause.statusDescription())
                .isEqualTo(
                        "error: general processing exception - index drop failed. Unable to drop '$idxDescrOrName'.");
        assertThat(cause.cause()).isNotPresent();
    }
}
