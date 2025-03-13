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
package org.neo4j.ssl.config;

import org.neo4j.ssl.SslPolicy;

public interface ScopedSslPolicyProvider {
    SslPolicy getPolicy();

    void addPolicyChangeListener(SslPolicyChangeListener listener);

    void removePolicyChangeListener(SslPolicyChangeListener listener);

    static ScopedSslPolicyProvider getNullInstance() {
        return new ScopedSslPolicyProvider() {
            @Override
            public SslPolicy getPolicy() {
                return null;
            }

            @Override
            public void addPolicyChangeListener(SslPolicyChangeListener listener) {}

            @Override
            public void removePolicyChangeListener(SslPolicyChangeListener listener) {}
        };
    }
}
