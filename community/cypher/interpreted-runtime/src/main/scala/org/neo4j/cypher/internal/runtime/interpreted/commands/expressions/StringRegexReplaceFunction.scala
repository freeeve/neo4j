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
import org.neo4j.values.storable.TextValue

import java.util.regex.Pattern

case class StringRegexReplaceFunction(
  original: Expression,
  regex: Expression,
  replacement: Expression
) extends Expression {
  override def children: Seq[AstNode[_]] = arguments

  override def apply(ctx: ReadableRow, state: QueryState): AnyValue =
    CypherFunctions.stringRegexReplace(original(ctx, state), regex(ctx, state), replacement(ctx, state))

  override def arguments: Seq[Expression] = Seq(original, regex, replacement)

  override def rewrite(f: Expression => Expression): Expression = regex.rewrite(f) match {
    case lit: Literal => f(LiteralStringRegexReplaceFunction(original.rewrite(f), lit, replacement.rewrite(f)))
    case other        => f(StringRegexReplaceFunction(original.rewrite(f), other, replacement.rewrite(f)))
  }
}

case class LiteralStringRegexReplaceFunction(
  original: Expression,
  regexLiteral: Literal,
  replacement: Expression
) extends Expression {
  lazy val pattern: Pattern = Pattern.compile(regexLiteral.value.asInstanceOf[TextValue].stringValue())

  override def children: Seq[AstNode[_]] = arguments

  override def apply(ctx: ReadableRow, state: QueryState): AnyValue =
    CypherFunctions.stringRegexReplaceWithPattern(original(ctx, state), pattern, replacement(ctx, state))

  override def arguments: Seq[Expression] = Seq(original, regexLiteral, replacement)

  override def rewrite(f: Expression => Expression): Expression = regexLiteral.rewrite(f) match {
    case lit: Literal => f(LiteralStringRegexReplaceFunction(original.rewrite(f), lit, replacement.rewrite(f)))
    case other        => f(StringRegexReplaceFunction(original.rewrite(f), other, replacement.rewrite(f)))
  }
}
