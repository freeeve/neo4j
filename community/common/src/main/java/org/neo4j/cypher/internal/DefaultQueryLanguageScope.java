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
package org.neo4j.cypher.internal;

/**
 * Transactional default query language scope.
 */
public interface DefaultQueryLanguageScope {
    /**
     * Returns the default query language, or null if there is none.
     * Note, the default language is not always equal to the actual language used for a particular query.
     */
    CypherVersion defaultQueryLanguage();

    /** Sets the default query language. */
    void setDefaultQueryLanguage(CypherVersion defaultLanguage);

    void reset();

    static DefaultQueryLanguageScope create() {
        return new DefaultQueryLanguageProvider();
    }
}

class DefaultQueryLanguageProvider implements DefaultQueryLanguageScope {
    private CypherVersion defaultQueryLanguage;

    @Override
    public CypherVersion defaultQueryLanguage() {
        return defaultQueryLanguage;
    }

    @Override
    public void setDefaultQueryLanguage(CypherVersion defaultLanguage) {
        assert this.defaultQueryLanguage == null || this.defaultQueryLanguage == defaultLanguage;
        this.defaultQueryLanguage = defaultLanguage;
    }

    @Override
    public void reset() {
        this.defaultQueryLanguage = null;
    }
}
