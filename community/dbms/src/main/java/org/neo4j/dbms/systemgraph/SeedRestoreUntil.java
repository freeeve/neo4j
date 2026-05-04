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
package org.neo4j.dbms.systemgraph;

import static org.neo4j.dbms.systemgraph.TopologyGraphDbmsModel.DATABASE_SEED_RESTORE_UNTIL_PROPERTY;
import static org.neo4j.util.Preconditions.checkArgument;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.neo4j.graphdb.Node;
import org.neo4j.util.VisibleForTesting;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class SeedRestoreUntil {
    OptionalLong txId;
    Optional<ZonedDateTime> dateTime;

    private SeedRestoreUntil(OptionalLong txId, Optional<ZonedDateTime> dateTime) {
        validateArgs(txId, dateTime);

        this.txId = txId;
        this.dateTime = dateTime;
    }

    public static SeedRestoreUntil txId(long txId) {
        return new SeedRestoreUntil(OptionalLong.of(txId), Optional.empty());
    }

    public static SeedRestoreUntil datetime(ZonedDateTime dateTime) {
        return new SeedRestoreUntil(OptionalLong.empty(), Optional.of(dateTime));
    }

    public void writeProperty(Node database) {
        txId.ifPresentOrElse(
                txId -> database.setProperty(DATABASE_SEED_RESTORE_UNTIL_PROPERTY, txId),
                () -> dateTime.ifPresentOrElse(
                        dateTime -> database.setProperty(DATABASE_SEED_RESTORE_UNTIL_PROPERTY, dateTime), () -> {
                            throw new IllegalStateException("Must contain either a transaction id or transaction date");
                        }));
    }

    public static SeedRestoreUntil fromObj(Object obj) {
        if (obj instanceof Number number) {
            return txId(number.longValue());
        }
        if (obj instanceof ZonedDateTime zonedDateTime) {
            return datetime(zonedDateTime);
        }
        throw new IllegalArgumentException("Provided value can't be converted to transaction id or transaction date");
    }

    public OptionalLong txId() {
        return txId;
    }

    public Optional<ZonedDateTime> dateTime() {
        return dateTime;
    }

    public String toOptionValue() {
        return txId.isPresent()
                ? String.valueOf(txId.getAsLong())
                : dateTime()
                        .map(Objects::toString)
                        .orElseThrow(() ->
                                new IllegalStateException("Must contain either a transaction id or transaction date"));
    }

    @VisibleForTesting
    static void validateArgs(OptionalLong txId, Optional<ZonedDateTime> dateTime) {
        if (txId.isPresent() && dateTime.isPresent()) {
            throw new IllegalArgumentException("Only one of transaction id or transaction date can be provided");
        }
        if (txId.isEmpty() && dateTime.isEmpty()) {
            throw new IllegalArgumentException("Must contain either a transaction id or transaction date");
        }
        txId.ifPresent(
                id -> checkArgument(id > 0, "Transaction id should be a positive number. Provided value: " + id));
    }

    @Override
    public String toString() {
        return toOptionValue();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SeedRestoreUntil that = (SeedRestoreUntil) o;
        return Objects.equals(txId, that.txId) && Objects.equals(dateTime, that.dateTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txId, dateTime);
    }
}
