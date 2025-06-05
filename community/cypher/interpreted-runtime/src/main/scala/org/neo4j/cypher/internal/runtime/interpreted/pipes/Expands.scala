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
package org.neo4j.cypher.internal.runtime.interpreted.pipes

import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.values.AnyValue

object Expands {

  sealed trait ExpandWriter {

    def writeRow(
      rowFactory: CypherRowFactory,
      row: CypherRow,
      rel: AnyValue,
      endNode: AnyValue
    ): CypherRow
  }

  def compileWriter(
    maybeRelationship: Option[String],
    maybeToNode: Option[String]
  ): ExpandWriter = (maybeRelationship, maybeToNode) match {
    case (Some(relName), Some(endName)) =>
      new ExpandWriter {
        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          rel: AnyValue,
          endNode: AnyValue
        ): CypherRow =
          rowFactory.copyWith(
            row,
            relName,
            rel,
            endName,
            endNode
          )
      }

    case (Some(relName), None) =>
      new ExpandWriter {
        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          rel: AnyValue,
          endNode: AnyValue
        ): CypherRow =
          rowFactory.copyWith(
            row,
            relName,
            rel
          )
      }

    case (None, Some(endName)) =>
      new ExpandWriter {
        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          rel: AnyValue,
          endNode: AnyValue
        ): CypherRow =
          rowFactory.copyWith(
            row,
            endName,
            endNode
          )
      }

    case (None, None) =>
      new ExpandWriter {
        override def writeRow(
          rowFactory: CypherRowFactory,
          row: CypherRow,
          rel: AnyValue,
          endNode: AnyValue
        ): CypherRow = rowFactory.copyWith(row)
      }
  }
}
