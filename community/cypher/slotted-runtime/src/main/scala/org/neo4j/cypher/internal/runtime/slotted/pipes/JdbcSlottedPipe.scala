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
package org.neo4j.cypher.internal.runtime.slotted.pipes

import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.physicalplanning.SlotConfiguration
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.interpreted.pipes.Pipe
import org.neo4j.cypher.internal.runtime.interpreted.pipes.QueryState
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.jdbc.mapping.value.JdbcValueMapper
import org.neo4j.io.IOUtils

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

import javax.sql.DataSource

class JdbcSlottedPipe private (
  dataSource: DataSource,
  createStatement: Connection => PreparedStatement,
  projectedSlots: Array[Int],
  projectedValues: Array[JdbcValueMapper]
)(override val id: Id) extends Pipe {

  override protected def internalCreateResults(state: QueryState): ClosingIterator[CypherRow] =
    new ClosingIterator[CypherRow] {
      private[this] var conn: Connection = _
      private[this] var statement: PreparedStatement = _
      private[this] var result: ResultSet = _
      private[this] var onValidRow = false

      override protected[this] def closeMore(): Unit = {
        try if (conn != null) conn.commit()
        finally IOUtils.closeAll(result, statement, conn)
      }

      override def next(): CypherRow = {
        if (onValidRow || innerHasNext) {
          val row = state.newRowWithArgument(rowFactory)
          var i = 0
          val size = projectedSlots.length
          while (i < size) {
            row.setRefAt(projectedSlots(i), projectedValues(i).toNeo4jValue(result))
            i += 1
          }
          onValidRow = false
          row
        } else {
          ClosingIterator.empty.next()
        }
      }

      override protected[this] def innerHasNext: Boolean = {
        if (onValidRow) {
          true
        } else {
          if (result == null) {
            initialize()
          }
          if (result.isClosed) {
            false
          } else if (result.next()) {
            onValidRow = true
            true
          } else {
            result.close()
            onValidRow = false
            false
          }
        }
      }

      private def initialize(): Unit = {
        require(result == null && statement == null && conn == null)
        conn = dataSource.getConnection
        conn.setAutoCommit(false)
        statement = createStatement(conn)
        result = statement.executeQuery()
      }
    }
}

object JdbcSlottedPipe {

  def apply(
    dataSource: DataSource,
    createStatement: Connection => PreparedStatement,
    projections: Map[LogicalVariable, JdbcValueMapper],
    slots: SlotConfiguration
  )(
    id: Id
  ): JdbcSlottedPipe = {
    val (slotOffsets, mappers) = projections.view
      .map { case (variable, mapper) => slots.refOffset(variable) -> mapper }
      .toArray
      .unzip
    new JdbcSlottedPipe(dataSource, createStatement, slotOffsets, mappers)(id)
  }
}
