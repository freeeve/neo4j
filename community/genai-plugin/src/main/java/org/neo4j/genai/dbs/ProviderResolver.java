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
package org.neo4j.genai.dbs;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.neo4j.annotations.service.Service;
import org.neo4j.service.Services;

@Service
public interface ProviderResolver {

    /**
     * All known providers
     */
    Map<String, VectorDatabaseProvider> KNOWN_PROVIDERS = Services.loadAll(VectorDatabaseProvider.class).stream()
            .collect(Collectors.collectingAndThen(
                    Collectors.toMap(
                            VectorDatabaseProvider::getName,
                            Function.identity(),
                            (p1, p2) -> {
                                throw new RuntimeException("Duplicate provider name");
                            },
                            TreeMap::new),
                    Collections::unmodifiableMap));

    /**
     * Useful constant for the highest precedence value.
     *
     * @see java.lang.Integer#MIN_VALUE
     */
    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;

    /**
     * Useful constant for the lowest precedence value.
     *
     * @see java.lang.Integer#MAX_VALUE
     */
    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;

    /**
     * Get the order value of this object.
     * <p>
     * Higher values are interpreted as lower precedence. As a consequence, the object
     * with the lowest value has the highest precedence.
     * <p>
     * Same order values will result in arbitrary sort positions for the affected objects.
     * <p>
     * The default implementation has the lowest precedence possible.
     *
     * @return the order value
     * @see #HIGHEST_PRECEDENCE
     * @see #LOWEST_PRECEDENCE
     */
    default int getOrder() {
        return LOWEST_PRECEDENCE;
    }

    /**
     * Validates the given host against the list of known endpoints, and picks a corresponding provider
     * @param host The host to use
     * @return the provider or an empty optional if the URL could not be validated.
     */
    Optional<VectorDatabaseProvider> resolve(String host);
}
