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
package org.neo4j.cypher.internal.ir

import org.neo4j.cypher.internal.ast.Search
import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewritable

sealed trait SearchClause extends Rewritable {
  def bindingVariable: LogicalVariable
  def dependencies: Set[LogicalVariable]
}

case class VectorSearchClause(
  bindingVariable: LogicalVariable,
  indexName: String,
  embedding: Expression,
  limit: Expression,
  scoreVariable: Option[LogicalVariable]
)(val position: InputPosition)
    extends SearchClause {
  override def dependencies: Set[LogicalVariable] = Set(bindingVariable) ++ embedding.dependencies

  override def toString: String = {
    val scoreStr = scoreVariable.map(v => s", score: ${v.name}").getOrElse("")
    s"VectorSearchPredicate(binding: ${bindingVariable.name}, index: $indexName, embedding: ${SearchClause.stringifier(
        embedding
      )}, limit: ${SearchClause.stringifier(limit)}$scoreStr)(position = $position)"
  }

  def dup(children: Seq[AnyRef]): this.type =
    copy(
      bindingVariable = children.head.asInstanceOf[LogicalVariable],
      indexName = children(1).asInstanceOf[String],
      embedding = children(2).asInstanceOf[Expression],
      limit = children(3).asInstanceOf[Expression],
      scoreVariable = children(4).asInstanceOf[Option[LogicalVariable]]
    )(position = position).asInstanceOf[this.type]
}

object SearchClause {

  val stringifier: ExpressionStringifier = ExpressionStringifier(
    extensionStringifier = new ExpressionStringifier.Extension {
      override def apply(ctx: ExpressionStringifier)(expression: Expression): String = expression.asCanonicalStringVal
    },
    alwaysParens = false,
    alwaysBacktick = false,
    preferSingleQuotes = false,
    sensitiveParamsAsParams = false,
    javaCompatible = false
  )

  def fromAst(search: Option[Search]): Option[SearchClause] = search.map(ast => {
    ast.indexName match {
      case Right(_) =>
        // We currently only support String, update this when we allow Parameter.
        throw new IllegalArgumentException(
          s"Index name as Parameter is not supported in the expression form of SEARCH at position ${ast.position}"
        )
      case Left(indexName) =>
        VectorSearchClause(
          bindingVariable = ast.bindingVariable,
          indexName = indexName,
          embedding = ast.embedding,
          limit = ast.limit.expression,
          scoreVariable = ast.score
        )(ast.position)
    }
  })
}
