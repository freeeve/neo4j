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
package org.neo4j.cypher.internal

import org.neo4j.cypher.internal.NonFatalCypherError.isNonFatal
import org.neo4j.kernel.api.exceptions.Status.Classification
import org.neo4j.kernel.api.exceptions.Status.HasStatus

/**
 * Used for deciding for which errors we should attempt to retry the transaction
 * with CALL IN TRANSACTION error handling mode RETRY.
 *
 * This includes errors with status classification TransientError plus
 * a few exception.
 */
object RetryableCypherError {

  def apply(t: Throwable): Boolean = isRetryable(t)

  def unapply(t: Throwable): Option[Throwable] = Some(t).filter(isRetryable)

  // We use a whitelist approach here:
  // Only non-fatal classified errors are considered recoverable,
  // except DatabaseError which is used for more serious and/or internal problems of the database.
  // NOTE: If you believe a specific error is incorrectly classified here, first consider changing the classification
  //       of its error code before complicating this logic with special cases,
  //       so we can keep this reasonably principled and easily explainable to users.
  def isRetryable(t: Throwable): Boolean = t match {
    case e: HasStatus if isNonFatal(e) =>
      val classification = e.status().code().classification()
      classification match {
        case Classification.TransientError =>
          true
        case _ =>
          false
      }

    // NOTE: Some Security errors that are considered retryable by the driver (even though the classification is not transient)
    //       would not work to retry on the server-side, since there is currently no way to renew the authentication and/or authorization.
    //       E.g. Status.Security.CredentialsExpired, Status.Security.AuthorizationExpired

    // Unfortunately there are still some public API exceptions that do not implement HasStatus that we still
    // want to consider retryable.
    // We should probably add a HasStatus in the next major release where we can make API changes to get rid
    // of this special case.
    // (These should also be matching the above classifications after passing through
    //  org.neo4j.cypher.internal.macros.TranslateExceptionMacros)
    case _: org.neo4j.graphdb.ConstraintViolationException =>
      true

    case e: org.neo4j.kernel.api.exceptions.ResourceCloseFailureException =>
      val cause = e.getCause
      val causeIsRetryable = if (cause != null && cause != e) {
        isRetryable(cause)
      } else {
        true
      }
      // Look at the suppressed exceptions to see if they contain a non-retryable one
      causeIsRetryable && !e.getSuppressed.exists {
        case t if t eq e =>
          // Defensive measure to avoid infinite recursion
          false
        case t if !isRetryable(t) =>
          true
        case _ =>
          false
      }

    case _ =>
      false
  }
}
