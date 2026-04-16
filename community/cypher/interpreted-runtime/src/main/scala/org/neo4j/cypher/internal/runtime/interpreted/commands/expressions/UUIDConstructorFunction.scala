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
package org.neo4j.cypher.internal.runtime.interpreted.commands.expressions

import org.neo4j.cypher.internal.runtime.ReadableRow
import org.neo4j.cypher.internal.runtime.interpreted.commands.AstNode
import org.neo4j.cypher.internal.runtime.interpreted.pipes.QueryState
import org.neo4j.cypher.operations.CypherFunctions
import org.neo4j.exceptions.InternalException
import org.neo4j.values.AnyValue

case class UUIDConstructorFunction(
  input1: Option[Expression] = None,
  input2: Option[Expression] = None
) extends Expression {
  override def children: Seq[AstNode[_]] = arguments

  override def apply(ctx: ReadableRow, state: QueryState): AnyValue = (input1, input2) match {
    case (Some(e), None) => CypherFunctions.UUIDFromString(e(ctx, state))
    case (None, Some(_)) =>
      throw InternalException.internalError(
        classOf[CypherFunctions].getSimpleName,
        "Expected input 1 to be defined if input 2 is."
      );
    case (Some(e1), Some(e2)) => CypherFunctions.UUIDFromLongs(e1(ctx, state), e2(ctx, state))
    case (None, None)         => CypherFunctions.generateUUID()
  }

  override def arguments: Seq[Expression] = (input1, input2) match {
    case (Some(e1), None) => Seq(e1)
    case (None, Some(_)) => throw InternalException.internalError(
        classOf[CypherFunctions].getSimpleName,
        "Expected input 1 to be defined if input 2 is."
      );
    case (Some(e1), Some(e2)) => Seq(e1, e2)
    case (None, None)         => Seq()
  }

  override def rewrite(f: Expression => Expression): Expression = (input1, input2) match {
    case (Some(e1), None) => f(UUIDConstructorFunction(Some(e1.rewrite(f))))
    case (None, Some(_)) =>
      throw InternalException.internalError(
        classOf[CypherFunctions].getSimpleName,
        "Expected input 1 to be defined if input 2 is."
      );
    case (Some(e1), Some(e2)) => f(UUIDConstructorFunction(Some(e1.rewrite(f)), Some(e2.rewrite(f))))
    case (None, None)         => f(UUIDConstructorFunction())
  }
}
