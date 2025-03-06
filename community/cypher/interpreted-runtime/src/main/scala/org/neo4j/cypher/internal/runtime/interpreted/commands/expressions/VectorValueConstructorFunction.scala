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
import org.neo4j.cypher.operations.VectorCoordinateType
import org.neo4j.values.AnyValue

case class VectorValueConstructorFunction(
  vector: Expression,
  dimension: Option[Expression] = None,
  coordinateType: Option[VectorCoordinateType] = None
) extends Expression {

  override def apply(ctx: ReadableRow, state: QueryState): AnyValue = (dimension, coordinateType) match {
    case (Some(d), Some(c)) => CypherFunctions.vectorValueConstructor(vector(ctx, state), d(ctx, state), c)
    case (Some(d), None)    => CypherFunctions.vectorValueConstructor(vector(ctx, state), d(ctx, state))
    case _                  => CypherFunctions.vectorValueConstructor(vector(ctx, state))
  }

  override def arguments: Seq[Expression] = dimension match {
    case Some(d) => Seq(vector, d)
    case None    => Seq(vector)
  }

  override def rewrite(f: Expression => Expression): Expression = (dimension, coordinateType) match {
    case (Some(d), Some(c)) =>
      f(VectorValueConstructorFunction(vector.rewrite(f), Some(d.rewrite(f))))
    case (Some(d), None) => f(VectorValueConstructorFunction(vector.rewrite(f), Some(d.rewrite(f))))
    case _               => f(VectorValueConstructorFunction(vector.rewrite(f)))
  }

  override def children: Seq[AstNode[_]] = arguments
}
