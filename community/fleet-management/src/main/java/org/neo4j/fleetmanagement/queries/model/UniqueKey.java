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
package org.neo4j.fleetmanagement.queries.model;

import java.util.Objects;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.kernel.api.QueryLanguage;

public class UniqueKey {
    // Truncate query texts larger than 10 KiB
    private static final int MAX_QUERY_TEXT_LENGTH = 10 * 1024;

    private final String queryText;
    private final SimplifiedGqlError gqlError;
    private final QueryLanguage queryLanguage;

    public UniqueKey(String queryText, ErrorGqlStatusObject errorGqlStatusObject, QueryLanguage queryLanguage) {
        this.queryText = queryText.substring(0, Math.min(queryText.length(), MAX_QUERY_TEXT_LENGTH));
        this.gqlError = SimplifiedGqlError.from(errorGqlStatusObject);
        this.queryLanguage = queryLanguage;
    }

    public String getQueryText() {
        return queryText;
    }

    public SimplifiedGqlError getGqlError() {
        return gqlError;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UniqueKey uniqueKey = (UniqueKey) o;
        return Objects.equals(gqlError, uniqueKey.gqlError)
                && Objects.equals(queryText, uniqueKey.queryText)
                && queryLanguage == uniqueKey.queryLanguage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryText, gqlError, queryLanguage);
    }

    @Override
    public String toString() {
        return String.format(
                "UniqueKey{queryText='%s', success=%s, queryLanguage=%s}", queryText, gqlError, queryLanguage);
    }

    public String getQueryLanguage() {
        return queryLanguage.name();
    }
}
