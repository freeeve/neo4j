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
package org.neo4j.cypher.internal.logical.plans

import org.neo4j.cypher.internal.expressions.Expression

/**
 * This is a utility class for subquery expressions that maps rewritten expressions to their original counterparts
 */
object RewrittenSubQueryPredicates {

  case class RewrittenSubQueryPredicatesMap(backingStore: Map[Expression, Expression]) {
    def originalExpressionOrSelf(expr: Expression): Expression = backingStore.getOrElse(expr, expr)
    def allRewrittenExpressions: Iterable[Expression] = backingStore.keys
    def originalExpressions: Iterable[Expression] = backingStore.values
    def isEmpty: Boolean = backingStore.isEmpty
    def nonEmpty: Boolean = backingStore.nonEmpty
  }
  def empty: RewrittenSubQueryPredicatesMap = RewrittenSubQueryPredicatesMap(Map.empty)
  def forMap(map: Map[Expression, Expression]): RewrittenSubQueryPredicatesMap = RewrittenSubQueryPredicatesMap(map)

  def singleton(originalExpr: Expression, rewrittenExpr: Expression): RewrittenSubQueryPredicatesMap = {
    RewrittenSubQueryPredicatesMap(Map(originalExpr -> rewrittenExpr))
  }

  def withNoRewrittenExprs(expressions: Iterable[Expression]): RewrittenSubQueryPredicatesMap = {
    RewrittenSubQueryPredicatesMap(expressions.map(expr => expr -> expr).toMap)
  }
}
