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

import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.VariableStringInterpolator
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.physicalplanning.SlotConfiguration
import org.neo4j.cypher.internal.physicalplanning.SlotConfigurationBuilder
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.ResourceManager
import org.neo4j.cypher.internal.runtime.interpreted.QueryStateHelper
import org.neo4j.cypher.internal.runtime.interpreted.pipes.QueryState
import org.neo4j.cypher.internal.runtime.slotted.SlottedCypherRowFactory
import org.neo4j.cypher.internal.runtime.slotted.pipes.JdbcSlottedPipeTest.Setup
import org.neo4j.cypher.internal.runtime.slotted.pipes.JdbcSlottedPipeTest.SingleConnectionDataSource
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.cypher.jdbc.mapping.value.JdbcValueMapper
import org.neo4j.cypher.jdbc.mapping.value.impl.BooleanMapping
import org.neo4j.cypher.jdbc.mapping.value.impl.LongMapping
import org.neo4j.cypher.jdbc.mapping.value.impl.StringMapping
import org.neo4j.values.storable.Values.NO_VALUE
import org.neo4j.values.storable.Values.booleanValue
import org.neo4j.values.storable.Values.longValue
import org.neo4j.values.storable.Values.stringValue
import org.sqlite.SQLiteDataSource

import java.io.PrintWriter
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.logging.Logger

import scala.util.Using

import javax.sql.DataSource

class JdbcSlottedPipeTest extends CypherFunSuite with SlottedPipeTestHelper {

  test("read empty table") {
    setup().run().hasNext shouldBe false
    intercept[NoSuchElementException](setup().run().next())
  }

  test("single row table") {
    val population =
      """insert into the_table (a, b, c)
        |values (1, 'a', true);""".stripMargin
    setup(Some(population)).consume() shouldBe Seq(
      Map("cypher_a" -> longValue(1), "cypher_b" -> stringValue("a"), "cypher_c" -> booleanValue(true))
    )
  }

  test("multi row table including nulls") {
    val population =
      """insert into the_table (a, b, c)
        |values
        |  (-1, 'a', true),
        |  (null, null, null),
        |  (2, 'b', false),
        |  (3, 'c', true);""".stripMargin
    setup(Some(population)).consume() shouldBe Seq(
      Map("cypher_a" -> longValue(-1), "cypher_b" -> stringValue("a"), "cypher_c" -> booleanValue(true)),
      Map("cypher_a" -> NO_VALUE, "cypher_b" -> NO_VALUE, "cypher_c" -> NO_VALUE),
      Map("cypher_a" -> longValue(2), "cypher_b" -> stringValue("b"), "cypher_c" -> booleanValue(false)),
      Map("cypher_a" -> longValue(3), "cypher_b" -> stringValue("c"), "cypher_c" -> booleanValue(true))
    )
  }

  private def setup(sqlPopulate: Option[String] = None): Setup = {
    val dataSource = SingleConnectionDataSource(new SQLiteDataSource())
    executeUpdate(dataSource)(
      """create table the_table (
        |  a int,
        |  b text,
        |  c boolean
        |);""".stripMargin
    )
    sqlPopulate.foreach(executeUpdate(dataSource))

    val sql = "select a, b, c from the_table"

    val projections = Map[LogicalVariable, JdbcValueMapper](
      v"cypher_a" -> new LongMapping(1),
      v"cypher_b" -> new StringMapping(2),
      v"cypher_c" -> new BooleanMapping(3)
    )
    val slots = SlotConfigurationBuilder.empty
      .newReference("cypher_a", nullable = true, CTAny)
      .newReference("cypher_b", nullable = true, CTAny)
      .newReference("cypher_c", nullable = true, CTAny)
      .build()
    val monitor = QueryStateHelper.trackClosedMonitor
    val resourceManager = new ResourceManager(monitor)
    val state = QueryStateHelper.emptyWithResourceManager(resourceManager)

    val pipe = JdbcSlottedPipe(dataSource, statement(sql), projections, slots)(Id.INVALID_ID)
    pipe.rowFactory = SlottedCypherRowFactory(slots, SlotConfiguration.Size.zero)
    Setup(pipe, state, slots, dataSource)
  }

  private def statement(sql: String)(conn: Connection): PreparedStatement = conn.prepareStatement(sql)

  private def executeUpdate(source: DataSource)(sql: String): Unit = {
    Using.resource(source.getConnection.prepareStatement(sql)) { stmt =>
      stmt.executeUpdate()
    }
  }
}

object JdbcSlottedPipeTest {

  case class Setup(
    pipe: JdbcSlottedPipe,
    state: QueryState,
    slots: SlotConfiguration,
    dataSource: SingleConnectionDataSource
  ) extends SlottedPipeTestHelper {
    def run(): ClosingIterator[CypherRow] = pipe.createResults(state)

    def consume(): List[Map[String, Any]] = {
      val result = testableResult(run(), slots)
      dataSource.getConnection.isClosed shouldBe true
      result
    }
  }

  case class SingleConnectionDataSource(inner: DataSource) extends DataSource {
    private lazy val conn = inner.getConnection
    override def getConnection: Connection = conn
    override def getConnection(username: String, password: String): Connection = ???
    override def getLogWriter: PrintWriter = ???
    override def setLogWriter(out: PrintWriter): Unit = ???
    override def setLoginTimeout(seconds: Int): Unit = ???
    override def getLoginTimeout: Int = ???
    override def getParentLogger: Logger = ???
    override def unwrap[T](iface: Class[T]): T = ???
    override def isWrapperFor(iface: Class[_]): Boolean = ???
  }
}
