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
package org.neo4j.gqlstatus;

import static java.lang.String.format;

import org.assertj.core.api.AbstractAssert;
import org.neo4j.driver.exceptions.Neo4jException;

public class DriverExceptionAssertImplementation
        extends AbstractAssert<DriverExceptionAssertImplementation, Neo4jException>
        implements ErrorGqlStatusObjectAssert<DriverExceptionAssertImplementation> {

    public DriverExceptionAssertImplementation(Neo4jException driverException) {
        super(driverException, DriverExceptionAssertImplementation.class);
    }

    @Override
    public DriverExceptionAssertImplementation gqlCause() {
        isNotNull();
        var optionalGqlCause = actual.gqlCause();
        objects.assertNotNull(info, optionalGqlCause);
        if (optionalGqlCause.isPresent()) {
            return new DriverExceptionAssertImplementation(optionalGqlCause.get());
        } else throw failure("Expected gql cause to be present, but was not. %s", actual);
    }

    @Override
    public DriverExceptionAssertImplementation hasGqlStatus(GqlStatusInfoCodes expectedStatus) {
        isNotNull();
        if (!actual.gqlStatus().equals(expectedStatus.getStatusString())) {
            failWithMessage(
                    "Expected GqlStatusObject to have status <%s> but was <%s>",
                    expectedStatus.getStatusString(), actual.gqlStatus());
        }
        return this;
    }

    @Override
    public DriverExceptionAssertImplementation hasStatusDescription(String expectedDescription) {
        isNotNull();
        if (!actual.statusDescription().equals(expectedDescription)) {
            failWithMessage(
                    "Expected GqlStatusObject to have statusDescription <%s> but was <%s>",
                    expectedDescription, actual.statusDescription());
        }
        return this;
    }

    @Override
    public DriverExceptionAssertImplementation hasStatusDescription(
            String expectedDescriptionTemplate, Object... args) {
        isNotNull();
        var expectedDescription = format(expectedDescriptionTemplate, args);
        if (!actual.statusDescription().equals(expectedDescription)) {
            failWithMessage(
                    "Expected GqlStatusObject to have statusDescription <%s> but was <%s>",
                    expectedDescription, actual.statusDescription());
        }
        return this;
    }
}
