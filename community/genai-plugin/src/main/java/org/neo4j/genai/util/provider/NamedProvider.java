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
package org.neo4j.genai.util.provider;

import static java.lang.String.CASE_INSENSITIVE_ORDER;

import org.eclipse.collections.api.list.ImmutableList;
import org.neo4j.annotations.service.Service;

/** Carries metadata for a gen ai provider. */
@Service
public interface NamedProvider {
    /** Returns the provider name. */
    String name();

    /* Returns the type of configuration options for this provder. */
    Class<?> paramType();

    /** Provider implementation. */
    interface Implementation {
        String name();
    }

    /** Lookup providers and instantiate their implementation. */
    interface Lookup<PROVIDER extends NamedProvider, IMPLEMENTATION extends Implementation> {
        ImmutableList<PROVIDER> providers();

        IMPLEMENTATION implementation(PROVIDER provider);

        default IMPLEMENTATION byName(String name) {
            for (final var provider : providers()) {
                if (CASE_INSENSITIVE_ORDER.compare(provider.name(), name) == 0) {
                    return implementation(provider);
                }
            }

            throw new RuntimeException("Provider not supported: %s".formatted(name));
        }
    }
}
