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
package org.neo4j.exceptions;

import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.collections.impl.factory.Maps;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorMessageHolder;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.kernel.api.exceptions.Status;

/**
 * Used to provide additional information on a Neo4jException.
 */
public class StatusWrapCypherException extends Neo4jException {
    public enum ExtraInformation {
        LINE_NUMBER,
        TRANSACTIONS_COMMITTED
    }

    private final Map<ExtraInformation, String> extraInfoMap = Maps.mutable.of();

    private <EX extends Throwable & ErrorGqlStatusObject> StatusWrapCypherException(EX cause) {
        super(cause, cause.legacyMessage(), cause);
    }

    private StatusWrapCypherException(ErrorGqlStatusObject gqlStatusObject, Throwable cause) {
        super(gqlStatusObject, cause.getMessage(), cause);
    }

    public static <EX extends Throwable & ErrorGqlStatusObject> StatusWrapCypherException wrapCypherException(EX e) {
        if (e.gqlStatusObject() != null) {
            return new StatusWrapCypherException(e);
        }
        // This case can be removed once all instances of Neo4jException has been ported to GQLSTATUS
        return new StatusWrapCypherException(GqlHelper.getDefaultObject(), e);
    }

    public StatusWrapCypherException addExtraInfo(ExtraInformation informationType, String extraInfo) {
        extraInfoMap.put(informationType, extraInfo);
        return this;
    }

    public boolean containsInfoFor(ExtraInformation informationType) {
        return extraInfoMap.containsKey(informationType);
    }

    @Override
    public String getMessage() {
        return formatMessage(getCause().getMessage());
    }

    @Override
    public String legacyMessage() {
        return formatMessage(ErrorMessageHolder.getOldCauseMessage(getCause()));
    }

    private String formatMessage(String message) {
        return String.format(
                "%s (%s)",
                message,
                extraInfoMap.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue)
                        .collect(Collectors.joining(", ")));
    }

    @Override
    public Status status() {
        return ((Neo4jException) getCause()).status();
    }
}
