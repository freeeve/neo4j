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

abstract class ToIntegerListFunction(arg: Expression) extends Expression {

  override def arguments: Seq[Expression] = Seq(arg)

  override def children: Seq[AstNode[_]] = Seq(arg)
}

case class ToIntegerListFunctionCypher5(arg: Expression) extends ToIntegerListFunction(arg) {
  override def rewrite(f: Expression => Expression): Expression = f(ToIntegerListFunctionCypher5(arg.rewrite(f)))

  override def apply(ctx: ReadableRow, state: QueryState): AnyValue =
    CypherFunctions.toIntegerListCypher5(arg(ctx, state))
}

case class ToIntegerListFunctionCypher25(arg: Expression) extends ToIntegerListFunction(arg) {
  override def rewrite(f: Expression => Expression): Expression = f(ToIntegerListFunctionCypher25(arg.rewrite(f)))

  override def apply(ctx: ReadableRow, state: QueryState): AnyValue =
    CypherFunctions.toIntegerList(arg(ctx, state))
}
