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
package org.neo4j.cypher.internal.runtime.slotted.expressions

import org.neo4j.cypher.internal.physicalplanning.ast
import org.neo4j.cypher.internal.physicalplanning.ast.PrimitiveComparison
import org.neo4j.cypher.internal.runtime.ReadableRow
import org.neo4j.cypher.internal.runtime.interpreted.commands.AstNode
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.IsMatchResult
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.Predicate
import org.neo4j.cypher.internal.runtime.interpreted.pipes.QueryState
import org.neo4j.values.storable.Value
import org.neo4j.values.storable.Values.booleanValue

sealed trait PrimitiveRuntimeComparison extends Predicate with SlottedExpression {
  override def children: Seq[AstNode[_]] = Seq.empty
  def compare(row: ReadableRow): Boolean

  final override def isMatch(row: ReadableRow, state: QueryState): IsMatchResult = {
    IsMatchResult(compare(row))
  }

  final override def apply(row: ReadableRow, state: QueryState): Value =
    booleanValue(compare(row))
}

object PrimitiveRuntimeComparison {

  def create(in: PrimitiveComparison): PrimitiveRuntimeComparison = in match {
    case ast.PrimitiveEquals(offset1, offset2)    => PrimitiveEquals(offset1, offset2)
    case ast.PrimitiveNotEquals(offset1, offset2) => PrimitiveNotEquals(offset1, offset2)
  }
}

case class PrimitiveEquals(slot1: Int, slot2: Int) extends PrimitiveRuntimeComparison {
  override def compare(row: ReadableRow): Boolean = row.getLongAt(slot1) == row.getLongAt(slot2)
  override def rewrite(f: Expression => Expression): Expression = f(PrimitiveEquals(slot1, slot2))
}

case class PrimitiveNotEquals(slot1: Int, slot2: Int) extends PrimitiveRuntimeComparison {

  override def compare(row: ReadableRow): Boolean = row.getLongAt(slot1) == row.getLongAt(slot2)
  override def rewrite(f: Expression => Expression): Expression = f(PrimitiveNotEquals(slot1, slot2))
}

case class PrimitiveAnds(predicates: Array[PrimitiveRuntimeComparison]) extends PrimitiveRuntimeComparison {

  override def compare(row: ReadableRow): Boolean = {
    var i = 0
    while (i < predicates.length) {
      if (!predicates(i).compare(row)) {
        return false
      }
      i += 1
    }
    true
  }

  override def rewrite(f: Expression => Expression): Expression =
    f(PrimitiveAnds(predicates.map(p => f(p).asInstanceOf[PrimitiveRuntimeComparison])))

  override def children: Seq[AstNode[_]] = predicates

}

object PrimitiveAnds {

  def create(in: Seq[PrimitiveComparison]): PrimitiveAnds =
    PrimitiveAnds(in.map(PrimitiveRuntimeComparison.create).toArray)
}
