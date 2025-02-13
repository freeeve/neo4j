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

/**
 * This interface abstracts the entrypoint into the admission control process which is to ensure a minimum QoS and stop
 * server being overloaded by requests.
 */
public interface AdmissionControlService {

    /**
     * Request a new admission control token, admission control tokens can be returned already released or may be
     * released later.
     * Implementations must be fast to complete as this can be invoked frequently and in performance critical components
     * such as on the bolt IO thread.
     */
    AdmissionControlToken requestToken(Tenant tenant);

    /**
     * Removes the tenant from this admission control services tracking.
     * @param tenant tenant to be removed.
     */
    void removeTenant(Tenant tenant);
}
