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
import org.neo4j.cypher.internal.ast.Where
import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.util.Rewritable

sealed trait SearchClause extends Rewritable {
  def resultVariable: LogicalVariable
  def dependencies: Set[LogicalVariable]
}

case class VectorSearchClause(
  resultVariable: LogicalVariable,
  indexName: String,
  embedding: Expression,
  where: Option[Where],
  limit: Expression,
  scoreVariable: Option[LogicalVariable]
) extends SearchClause {

  override def dependencies: Set[LogicalVariable] = embedding.dependencies ++ limit.dependencies

  override def toString: String = {
    val embeddingStr = SearchClause.stringifier(embedding)
    val whereStr = where.map(w => s"WHERE ${SearchClause.stringifier(w.expression)}").getOrElse("")
    val limitStr = SearchClause.stringifier(limit)
    val scoreStr = scoreVariable.map(v => s" SCORE AS ${v.name}").getOrElse("")
    s"SEARCH ${resultVariable.name} IN (VECTOR INDEX $indexName FOR $embeddingStr $whereStr LIMIT $limitStr)$scoreStr"
  }

  def dup(children: Seq[AnyRef]): this.type =
    copy(
      resultVariable = children.head.asInstanceOf[LogicalVariable],
      indexName = children(1).asInstanceOf[String],
      embedding = children(2).asInstanceOf[Expression],
      where = children(3).asInstanceOf[Option[Where]],
      limit = children(4).asInstanceOf[Expression],
      scoreVariable = children(5).asInstanceOf[Option[LogicalVariable]]
    ).asInstanceOf[this.type]
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
          resultVariable = ast.bindingVariable,
          indexName = indexName,
          embedding = ast.embedding,
          where = ast.where,
          limit = ast.limit.expression,
          scoreVariable = ast.score
        )
    }
  })
}
