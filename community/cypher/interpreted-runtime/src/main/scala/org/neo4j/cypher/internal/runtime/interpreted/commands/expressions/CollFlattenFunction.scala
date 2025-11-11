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
import org.neo4j.values.AnyValue

case class CollFlattenFunction(
  expression: Expression,
  depth: Option[Expression] = None
) extends Expression {
  override def children: Seq[AstNode[_]] = arguments

  override def apply(ctx: ReadableRow, state: QueryState): AnyValue = depth match {
    case Some(e) => CypherFunctions.collFlatten(expression(ctx, state), e(ctx, state))
    case None    => CypherFunctions.collFlatten(expression(ctx, state))
  }

  override def arguments: Seq[Expression] = depth match {
    case Some(e) => Seq(expression, e)
    case None    => Seq(expression)
  }

  override def rewrite(f: Expression => Expression): Expression = depth match {
    case Some(e) => f(CollFlattenFunction(expression.rewrite(f), Some(e.rewrite(f))))
    case None    => f(CollFlattenFunction(expression.rewrite(f)))
  }
}
