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
 * This is a utility class that maps expressions to their rewritten counterparts
 */
sealed trait RewrittenExpressions {
  def rewrittenExpressionOrSelf(expr: Expression): Expression
  def allRewrittenExpressions: Iterable[Expression]
  def originalExpressions: Iterable[Expression]
  def isEmpty: Boolean
  def nonEmpty: Boolean
}

object RewrittenExpressions {

  private case class RewrittenExpressionMap(backingStore: Map[Expression, Expression]) extends RewrittenExpressions {
    override def rewrittenExpressionOrSelf(expr: Expression): Expression = backingStore.getOrElse(expr, expr)
    override def allRewrittenExpressions: Iterable[Expression] = backingStore.values
    override def originalExpressions: Iterable[Expression] = backingStore.keys
    override def isEmpty: Boolean = backingStore.isEmpty
    override def nonEmpty: Boolean = backingStore.nonEmpty
  }

  private case class NoRewrittenExpressions(originalExpressions: Iterable[Expression]) extends RewrittenExpressions {
    def rewrittenExpressionOrSelf(expr: Expression): Expression = expr
    def allRewrittenExpressions: Iterable[Expression] = originalExpressions
    def isEmpty: Boolean = originalExpressions.isEmpty
    override def nonEmpty: Boolean = originalExpressions.nonEmpty
  }

  def empty: RewrittenExpressions = RewrittenExpressionMap(Map.empty)

  def forMap(map: Map[Expression, Expression]): RewrittenExpressions = RewrittenExpressionMap(map)

  def singleton(originalExpr: Expression, rewrittenExpr: Expression): RewrittenExpressions =
    RewrittenExpressionMap(Map(originalExpr -> rewrittenExpr))

  def withNoRewrittenExprs(expressions: Iterable[Expression]): RewrittenExpressions =
    NoRewrittenExpressions(expressions)
}
