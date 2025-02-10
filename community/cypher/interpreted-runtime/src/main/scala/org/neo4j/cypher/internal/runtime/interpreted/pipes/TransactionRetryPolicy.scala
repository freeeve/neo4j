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
package org.neo4j.cypher.internal.runtime.interpreted.pipes

import org.neo4j.cypher.internal
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsOnErrorBehaviour
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsOnErrorBehaviour.OnErrorRetryThenBreak
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsOnErrorBehaviour.OnErrorRetryThenContinue
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsOnErrorBehaviour.OnErrorRetryThenFail
import org.neo4j.cypher.internal.ast.SubqueryCall.InTransactionsRetryParameters
import org.neo4j.cypher.internal.runtime.interpreted.commands
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.values.storable.Values

sealed trait TransactionRetryPolicy

object TransactionRetryPolicy {
  case object DoNotRetry extends TransactionRetryPolicy

  case class RetryFor(maybeDurationInSeconds: Option[Expression]) extends TransactionRetryPolicy

  object RetryFor {

    def apply(durationInSeconds: Double): RetryFor = {
      RetryFor(Some(commands.expressions.Literal(Values.doubleValue(durationInSeconds))))
    }
  }

  def computeTransactionRetryPolicy(
    errorBehaviour: InTransactionsOnErrorBehaviour,
    maybeRetryParameters: Option[InTransactionsRetryParameters],
    expressionConverter: internal.expressions.Expression => commands.expressions.Expression
  ): TransactionRetryPolicy = {
    errorBehaviour match {
      case OnErrorRetryThenContinue | OnErrorRetryThenBreak | OnErrorRetryThenFail =>
        val maybeDuration = maybeRetryParameters.flatMap(_.timeout)
        TransactionRetryPolicy.RetryFor(maybeDuration.map(expressionConverter))
      case _ =>
        require(maybeRetryParameters.isEmpty, "Unexpected retry parameters for error behaviour: " + errorBehaviour)
        TransactionRetryPolicy.DoNotRetry
    }
  }
}
