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
package org.neo4j.internal.kernel.api.exceptions.schema;

import org.neo4j.common.TokenNameLookup;
import org.neo4j.exceptions.InvalidArgumentException;
import org.neo4j.exceptions.KernelException;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation;
import org.neo4j.gqlstatus.GqlParams;
import org.neo4j.gqlstatus.GqlStatusInfoCodes;
import org.neo4j.internal.schema.ConstraintDescriptor;
import org.neo4j.internal.schema.EndpointType;
import org.neo4j.kernel.api.exceptions.Status;

public class CreateConstraintFailureException extends SchemaKernelException {
    private final ConstraintDescriptor constraint;

    private final String cause;

    private CreateConstraintFailureException(
            ErrorGqlStatusObject gqlStatusObject,
            ConstraintDescriptor constraint,
            Throwable cause,
            String causeString) {
        super(
                gqlStatusObject,
                Status.Schema.ConstraintCreationFailed,
                cause,
                "Unable to create constraint %s: %s",
                constraint,
                causeString);

        this.constraint = constraint;
        this.cause = causeString;
    }

    // KNL-028
    public static CreateConstraintFailureException constraintCreationFailed(
            ConstraintDescriptor constraint, TokenNameLookup tokenNameLookup, Throwable cause) {
        return constraintCreationFailed(constraint, tokenNameLookup, cause, cause.getMessage(), null);
    }

    // KNL-028
    public static CreateConstraintFailureException constraintCreationFailed(
            ConstraintDescriptor constraint, TokenNameLookup tokenNameLookup, String causeMessage) {
        return constraintCreationFailed(constraint, tokenNameLookup, null, causeMessage, null);
    }

    public static CreateConstraintFailureException constraintCreationFailedOnCommunity(
            ConstraintDescriptor constraint, TokenNameLookup tokenNameLookup, String causeMessage) {
        String constraintType;
        switch (constraint.type()) {
            case EXISTS -> constraintType = "Property existence";
            case PROPERTY_TYPE -> constraintType = "Property type";
            case NODE_LABEL_EXISTENCE -> constraintType = "Node Label existence";
            case RELATIONSHIP_ENDPOINT_LABEL -> {
                EndpointType type =
                        constraint.asRelationshipEndpointLabelConstraint().endpointType();
                switch (type) {
                    case START -> constraintType = "Relationship source label";
                    case END -> constraintType = "Relationship target label";
                    default ->
                        throw InvalidArgumentException.internalError(
                                CreateConstraintFailureException.class.getSimpleName(),
                                String.format("Unexpected endpoint type: %s", type));
                }
            }
            case UNIQUE_EXISTS -> constraintType = "Key";
            default ->
                throw InvalidArgumentException.internalError(
                        CreateConstraintFailureException.class.getSimpleName(),
                        String.format("Unexpected constraint type: %s", constraint.type()));
        }

        var gqlStatusCause = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_51N27)
                .withParam(GqlParams.StringParam.feat, String.format("%s constraint", constraintType))
                .withParam(GqlParams.StringParam.edition, "community edition")
                .build();
        return constraintCreationFailed(constraint, tokenNameLookup, null, causeMessage, gqlStatusCause);
    }

    private static CreateConstraintFailureException constraintCreationFailed(
            ConstraintDescriptor constraint,
            TokenNameLookup tokenNameLookup,
            Throwable cause,
            String causeString,
            ErrorGqlStatusObject gqlStatusCause) {
        String constraintString = constraint.userDescription(tokenNameLookup);
        var gqlStatusBuilder = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_50N11)
                .withParam(
                        GqlParams.StringParam.constrDescrOrName,
                        constraint.getName() != null ? constraint.getName() : constraintString);
        if (gqlStatusCause != null) {
            gqlStatusBuilder.withCause(gqlStatusCause);
        }
        return new CreateConstraintFailureException(gqlStatusBuilder.build(), constraint, cause, causeString);
    }

    public ConstraintDescriptor constraint() {
        return constraint;
    }

    @Override
    public String getUserMessage(TokenNameLookup tokenNameLookup) {
        final var sb = new StringBuilder("Unable to create ").append(constraint.userDescription(tokenNameLookup));
        if (getCause() instanceof KernelException kernelCause) {
            sb.append(':').append(System.lineSeparator()).append(kernelCause.getUserMessage(tokenNameLookup));
        } else if (cause != null) {
            sb.append(':').append(System.lineSeparator()).append(cause);
        }
        return sb.append(". Note that only the first found violation is shown.").toString();
    }
}
