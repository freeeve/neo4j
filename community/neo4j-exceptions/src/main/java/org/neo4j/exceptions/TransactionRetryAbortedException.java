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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.GqlHelper;
import org.neo4j.kernel.api.exceptions.Status;

public class TransactionRetryAbortedException extends Neo4jException {
    private TransactionRetryAbortedException(ErrorGqlStatusObject gqlStatusObject, String message, Throwable cause) {
        super(gqlStatusObject, message, cause);
    }

    private TransactionRetryAbortedException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(gqlStatusObject, message);
    }

    @Override
    public Status status() {
        return Status.Statement.ExecutionTimeout;
    }

    public static TransactionRetryAbortedException transactionRetryAborted(
            Throwable cause, int retriedCount, double timeoutInSeconds) {
        var decimalFormat = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT));
        var timeoutString = decimalFormat.format(timeoutInSeconds);
        return new TransactionRetryAbortedException(
                GqlHelper.get50N23(retriedCount, timeoutInSeconds),
                String.format(
                        "Transaction retry aborted after %d attempts. Retry timed out with a maximum retry duration of %s seconds. Last failed with cause: %s",
                        retriedCount, timeoutString, cause.getMessage()),
                cause);
    }
}
