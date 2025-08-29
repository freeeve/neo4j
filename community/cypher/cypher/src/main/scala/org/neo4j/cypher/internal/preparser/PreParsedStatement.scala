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
package org.neo4j.cypher.internal.preparser

import org.neo4j.cypher.internal.options.CypherExecutionMode
import org.neo4j.cypher.internal.options.CypherPlanMode
import org.neo4j.cypher.internal.options.CypherVersionOption
import org.neo4j.cypher.internal.util.InputPosition

final case class PreParserOption(key: String, value: String, position: InputPosition)

object PreParserOption {
  def scope(position: InputPosition): PreParserOption = PreParserOption(CypherPlanMode.name, "SCOPE", position)
  def plan(position: InputPosition): PreParserOption = PreParserOption(CypherPlanMode.name, "PLAN", position)
  def explain(position: InputPosition): PreParserOption = PreParserOption(CypherExecutionMode.name, "EXPLAIN", position)
  def profile(position: InputPosition): PreParserOption = PreParserOption(CypherExecutionMode.name, "PROFILE", position)

  def generic(key: String, value: String, position: InputPosition): PreParserOption =
    PreParserOption(key, value, position)

  def version(version: String, position: InputPosition): PreParserOption =
    PreParserOption(CypherVersionOption.name, version, position)
}

final case class PreParsedStatement(statement: String, options: List[PreParserOption], offset: InputPosition)
