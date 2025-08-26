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

import java.util.List;

/*
 Represents those privilege entities for which GQL codes exist for these error scenarios:
 |              | already exists | doesn't exist |
 |--------------|----------------|---------------|
 | User         | 42N12          | 42N09         |
 | Role         | 42N13          | 42N10         |
 | Database     | 42N11          | 42N00         |
 | Composite DB | 42N11          | 42N00         |
 | DB Alias     | 42N11          | 42N00         |

 This allows for compile time type checking
*/
public enum PrivilegeGqlCodeEntity {
    ROLE("Role"),
    USER("User"),
    // DB, ALIAS and COMPOSITE produce the exact same GQL status codes, but with different legacy messages.
    DATABASE("Database"),
    DATABASE_ALIAS("Database alias");

    public final String description;

    PrivilegeGqlCodeEntity(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static ErrorGqlStatusObject entityNotFound(PrivilegeGqlCodeEntity entity, String name, String paramName) {
        return entityNotFound(entity, name, paramName, null);
    }

    public static ErrorGqlStatusObject entityNotFound(
            PrivilegeGqlCodeEntity entity, String name, String paramName, String command) {
        var cause =
                switch (entity) {
                    case USER -> userNotFound(name);
                    case ROLE -> roleNotFound(name);
                    case DATABASE, DATABASE_ALIAS -> databaseNotFound(name);
                };

        var paramCause = paramName == null
                ? cause
                : ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N51)
                        .withParam(GqlParams.StringParam.param, paramName)
                        .withCause(cause)
                        .build();

        return command == null
                ? invalidReference(paramCause)
                : invalidReference(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42NA8)
                        .withParam(GqlParams.StringParam.cmd, command)
                        .withCause(paramCause)
                        .build());
    }

    public static ErrorGqlStatusObject entityAlreadyExists(PrivilegeGqlCodeEntity entity, String name) {
        return switch (entity) {
            case USER -> invalidReference(userAlreadyExists(name));
            case ROLE -> invalidReference(roleAlreadyExists(name));
            case DATABASE, DATABASE_ALIAS -> invalidReference(databaseAlreadyExists(name));
        };
    }

    public static ErrorGqlStatusObject databasesAlreadyExists(List<String> names) {
        var cause = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N11)
                .withParam(GqlParams.ListParam.dbList, names)
                .build();
        return invalidReference(cause);
    }

    private static ErrorGqlStatusObject invalidReference(ErrorGqlStatusObject cause) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42001)
                .withCause(cause)
                .build();
    }

    private static ErrorGqlStatusObject userNotFound(String name) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N09)
                .withParam(GqlParams.StringParam.user, name)
                .build();
    }

    private static ErrorGqlStatusObject roleNotFound(String name) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N10)
                .withParam(GqlParams.StringParam.role, name)
                .build();
    }

    private static ErrorGqlStatusObject databaseNotFound(String name) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N00)
                .withParam(GqlParams.StringParam.db, name)
                .build();
    }

    private static ErrorGqlStatusObject userAlreadyExists(String name) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N12)
                .withParam(GqlParams.StringParam.user, name)
                .build();
    }

    private static ErrorGqlStatusObject roleAlreadyExists(String name) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N13)
                .withParam(GqlParams.StringParam.role, name)
                .build();
    }

    private static ErrorGqlStatusObject databaseAlreadyExists(String name) {
        return ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_42N11)
                .withParam(GqlParams.ListParam.dbList, List.of(name))
                .build();
    }
}
