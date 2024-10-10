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
package org.neo4j.kernel.impl.api.index;

public enum IndexUpdateMode {
    /**
     * Used when the db is online
     */
    ONLINE(false, true, true),

    /**
     * Used when flipping from populating to online
     */
    ONLINE_IDEMPOTENT(true, true, true),

    /**
     * Used when the db is recovering
     */
    RECOVERY(true, false, true),

    DIRECT(false, false, false);

    private final boolean idempotency;
    private final boolean refresh;
    private final boolean includeEntityIdInUniqueness;

    IndexUpdateMode(boolean idempotency, boolean refresh, boolean includeEntityIdInUniqueness) {
        this.idempotency = idempotency;
        this.refresh = refresh;
        this.includeEntityIdInUniqueness = includeEntityIdInUniqueness;
    }

    public boolean requiresIdempotency() {
        return idempotency;
    }

    public boolean requiresRefresh() {
        return refresh;
    }

    public boolean includeEntityIdInUniqueness() {
        return includeEntityIdInUniqueness;
    }
}
