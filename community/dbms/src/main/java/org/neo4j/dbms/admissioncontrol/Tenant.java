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
package org.neo4j.dbms.admissioncontrol;

import java.util.Optional;
import org.neo4j.kernel.database.NamedDatabaseId;

public interface Tenant {
    Tenant DEFAULT = new Tenant() {
        @Override
        public String name() {
            return "";
        }

        @Override
        public int hashCode() {
            return name().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Tenant) {
                return this.hashCode() == obj.hashCode();
            }
            return false;
        }
    };

    String name();

    /**
     * Get the database tied to this tenant.
     * @return database name if tenant is tied to a single database.
     */
    default Optional<NamedDatabaseId> database() {
        return Optional.empty();
    }
}
