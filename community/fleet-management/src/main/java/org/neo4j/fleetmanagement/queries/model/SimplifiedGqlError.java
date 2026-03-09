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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Objects;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;

public class SimplifiedGqlError {
    @JsonPropertyDescription("Nested error (if any)")
    public SimplifiedGqlError cause;

    @JsonPropertyDescription("Error classification")
    public String classification;

    @JsonPropertyDescription("Error message description")
    public String statusDescription;

    @JsonPropertyDescription("GQL status code")
    public String gqlStatus;

    public static SimplifiedGqlError from(ErrorGqlStatusObject errorGqlStatusObject) {
        if (errorGqlStatusObject == null) {
            return null;
        }

        var o = new SimplifiedGqlError();
        o.classification = errorGqlStatusObject.getClassification().toString();
        o.statusDescription = errorGqlStatusObject.obfuscatedStatusDescription();
        o.gqlStatus = errorGqlStatusObject.gqlStatus();
        o.cause = from(errorGqlStatusObject.cause().orElse(null));

        return o;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimplifiedGqlError that = (SimplifiedGqlError) o;
        return Objects.equals(cause, that.cause)
                && Objects.equals(classification, that.classification)
                && Objects.equals(statusDescription, that.statusDescription)
                && Objects.equals(gqlStatus, that.gqlStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cause, classification, statusDescription, gqlStatus);
    }

    @Override
    public String toString() {
        return "SimplifiedGqlError{" + "cause="
                + cause + ", classification='"
                + classification + '\'' + ", message='"
                + statusDescription + '\'' + ", gqlStatus='"
                + gqlStatus + '\'' + '}';
    }
}
