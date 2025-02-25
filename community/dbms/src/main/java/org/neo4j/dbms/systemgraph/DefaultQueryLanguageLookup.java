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

import java.util.Optional;
import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.cypher.internal.DefaultQueryLanguageScope;
import org.neo4j.kernel.database.NamedDatabaseId;

public interface DefaultQueryLanguageLookup {

    /**
     * Returns the default query language for the specified database.
     * Or an empty optional if there is no database with the specified id or
     * the call is made before system has fully started (happens for initialization queries, internal.dbms.init_file).
     * Implementations are allowed to cache the value,
     * expect a short delay after changes to the default language are reflected here.
     */
    Optional<CypherVersion> defaultLanguage(NamedDatabaseId dbId);

    /**
     * Returns the default query language from the scope if it's set.
     * Or else, looks it up using {@link #defaultLanguage(NamedDatabaseId)}.
     * Multiple queries in the same transaction needs to use the same default,
     * this mechanism is here to guarantee that.
     *
     * @param scope transactional default query language scope
     * @param dbId database id
     * @param fallbackLanguage fallback query language
     */
    default CypherVersion dbDefaultQueryLanguage(
            DefaultQueryLanguageScope scope, NamedDatabaseId dbId, CypherVersion fallbackLanguage) {
        var defaultLanguage = scope.defaultQueryLanguage();
        if (defaultLanguage == null) {
            defaultLanguage = defaultLanguage(dbId).orElse(fallbackLanguage);
            scope.setDefaultQueryLanguage(defaultLanguage);
        }
        return defaultLanguage;
    }
}
