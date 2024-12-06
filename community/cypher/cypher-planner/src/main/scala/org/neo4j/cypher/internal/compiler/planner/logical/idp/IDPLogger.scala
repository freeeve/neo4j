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
package org.neo4j.cypher.internal.compiler.planner.logical.idp

import org.apache.commons.lang3.StringUtils
import org.neo4j.cypher.internal.compiler.planner.logical.idp.IDPLoggerToStringBuilder.INDENT
import org.neo4j.cypher.internal.ir.NodeConnection
import org.neo4j.cypher.internal.ir.PatternRelationship
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.options.CypherDebugOptions

trait IDPLogger {
  def log(msg: => String): this.type
  def markScope[A](scope: String)(body: => A): A
  def result(): Option[String]
}

object IDPLogger {

  def givenDebugOptions(debugOptions: CypherDebugOptions): IDPLogger = {
    if (debugOptions.printIDPLog)
      new IDPLoggerToStringBuilder()
    else
      NoLogging
  }

  case object NoLogging extends IDPLogger {
    override def log(msg: => String): this.type = this
    override def markScope[A](scope: String)(body: => A): A = body
    override def result(): Option[String] = None
  }
}

class IDPLoggerToStringBuilder extends IDPLogger {
  private val sb: StringBuilder = new StringBuilder()
  private var indentLevel = 0

  override def log(msg: => String): this.type = {
    val indent = INDENT * indentLevel
    val lines = msg.linesIterator
    for (line <- lines) {
      sb.append(indent).append(line).append('\n')
    }
    this
  }

  override def markScope[A](scope: String)(body: => A): A = {
    beginScope(scope)
    try body
    finally endScope(scope)
  }

  private def beginScope(scope: String): this.type = {
    log(s"> BEGIN $scope").indent()
  }

  private def endScope(scope: String): this.type = {
    unindent().log(s"< END $scope")
  }

  private def indent(): this.type = {
    indentLevel += 1
    this
  }

  private def unindent(): this.type = {
    indentLevel -= 1
    if (indentLevel < 0) {
      logBadIndentLevel()
      indentLevel = 0
    }
    this
  }

  override def result(): Option[String] = {
    if (indentLevel != 0) {
      logBadIndentLevel()
    }
    Some(sb.result())
  }

  private def logBadIndentLevel(): this.type = {
    log(s"!!! MALFORMED LOG, indentLevel = $indentLevel")
  }
}

object IDPLoggerToStringBuilder {
  private val INDENT = "⁞ " // Vertical Four Dots (U+205E)
}

/**
 * For concise logging of `Solvable`s in [[IDPSolver]]
 */
trait IDPLoggable[-A] {
  def summary(x: A): String
}

object IDPLoggable {
  def summary[A: IDPLoggable](a: A): String = implicitly[IDPLoggable[A]].summary(a)

  implicit object IDPLoggableNodeConnection extends IDPLoggable[NodeConnection] {

    override def summary(x: NodeConnection): String = x match {
      case p: PatternRelationship => p.toString
      case x                      => x.solvedString
    }
  }

  implicit object IDPLoggableQueryGraph extends IDPLoggable[QueryGraph] {

    override def summary(x: QueryGraph): String = {
      StringUtils.abbreviate(x.toString, "…", 128)
    }
  }
}
