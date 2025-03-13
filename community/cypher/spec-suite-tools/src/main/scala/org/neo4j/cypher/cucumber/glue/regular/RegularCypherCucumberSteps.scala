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
package org.neo4j.cypher.cucumber.glue.regular

import com.google.inject.Inject
import cypher.features.Neo4jExceptionToExecutionFailed
import cypher.features.Phase
import io.cucumber.datatable.DataTable
import io.cucumber.scala.Scenario
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.QueryExecution
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.QueryFailure
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.QueryResults
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.assertEqualHeaders
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.describe
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.toResultRows
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.unexpectedFailure
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.unexpectedSuccess
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedError
import org.neo4j.cypher.cucumber.value.CypherCucumberValueParser
import org.neo4j.cypher.cucumber.value.CypherCucumberValueParser.parse
import org.neo4j.cypher.cucumber.value.ResultValueMapper
import org.neo4j.cypher.cucumber.value.ResultValueMapper.UnorderedList.rowsWithUnorderedLists
import org.neo4j.cypher.testing.api.ConsumedResult
import org.neo4j.cypher.testing.impl.FeatureDatabaseManagementService
import org.neo4j.internal.helpers.Exceptions
import org.neo4j.internal.kernel.api.procs.QualifiedName
import org.neo4j.kernel.api.procedure.Context
import org.neo4j.kernel.impl.util.ValueUtils
import org.neo4j.values.AnyValue

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util
import java.util.Objects
import java.util.function.Supplier

import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using

/**
 * The default implementation of the Cypher Cucumber steps (.feature files).
 */
final class RegularCypherCucumberSteps @Inject() (
  conf: TestConf,
  executors: Executors,
  expectations: Expectations
) extends CypherCucumberSteps {

  // Mutable state
  private[this] var dbmsAccessor: DbAccessor = _
  private[this] var db: FeatureDatabaseManagementService = _
  private[this] var parameters = Map.empty[String, AnyRef]
  private[this] var lastGraphState: GraphState = _
  private[this] var lastResult: QueryExecution = _
  private[this] var registeredProcedures = Seq.empty[QualifiedName]

  Before { scenario: Scenario =>
    // Note, @fail tags are handled in SkipFailsScenarios (or OnlyFailsScenarios).
    assumeFalse(expectations.ignore(scenario), "Scenario ignored because of @ignore tag")
    dbmsAccessor = executors.acquire(scenario)
    db = dbmsAccessor.dbms
  }

  After {
    if (db != null) db.unregisterProcedures(registeredProcedures)
    if (dbmsAccessor != null) executors.release(dbmsAccessor)
  }

  override protected def registerProcedure(signature: String, results: DataTable): Unit = {
    val output = results.cells().stream()
      .skip(1) // Header row
      .map(row => row.stream().map(v => ValueUtils.asValue(parse(v))).toArray(i => new Array[AnyValue](i)))
      .toArray(i => new Array[Array[AnyValue]](i))
    val procedure = ProcedureBuilder.createProcedure(signature, output)
    db.registerProcedure(procedure)
    registeredProcedures = registeredProcedures.appended(procedure.signature().name())
  }

  override protected def registerUserFunction(name: String): Unit = {
    name match {
      case "failNTimes" =>
        val state = new TestFailNTimesFunction.State
        db.registerComponent[TestFailNTimesFunction.State](
          classOf[TestFailNTimesFunction.State],
          (t: Context) => state,
          safe = true
        )
        db.unregisterProcedures(Seq(TestFailNTimesFunction.name))
        db.registerFunction(classOf[TestFailNTimesFunction])
      case _ =>
        throw new IllegalArgumentException(s"$name is not a recognised UDF name")
    }
  }

  override protected def parametersAre(params: Map[String, String]): Unit = {
    parameters = parameters ++ params.view.mapValues(CypherCucumberValueParser.parse)
  }

  override protected def givenCsvFile(urlParam: String, content: DataTable): Unit = {
    val file = Files.createTempFile("test-csv", ".csv")
    file.toFile.deleteOnExit()
    val csvContent = content.cells().asScala.view.map(_.asScala.mkString(",")).mkString("\n")
    Files.write(file, csvContent.getBytes(StandardCharsets.UTF_8))
    parameters = parameters + (urlParam -> file.toUri.toURL.toString)
  }

  override protected def havingExecuted(cypher: String): Unit = {
    db.execute(cypher, parameters, _.consume())
  }

  override protected def executingQuery(cypher: String): Unit = {
    lastGraphState = GraphState.recordGraphState(db.database)
    lastResult = execute(conf.preparserPrefix + cypher)
  }

  override protected def executingControlQuery(cypher: String): Unit = {
    lastResult = execute(cypher)
  }

  private def execute(cypher: String): QueryExecution = {
    Try(db.execute(cypher, parameters, _.consume(ResultValueMapper))) match {
      case Success(results) => QueryResults(cypher, results)
      case Failure(queryException) => Using.resource(db.begin()) { tx =>
          val explainSucceeds = Try(tx.execute("EXPLAIN\n" + cypher, parameters).consume()).isSuccess
          val rollbackSucceeds = Try(tx.rollback()).isSuccess
          val phase = if (explainSucceeds || !rollbackSucceeds) Phase.runtime else Phase.compile
          QueryFailure(cypher, phase, queryException)
        }
    }
  }

  override protected def resultShouldBeInOrder(expected: DataTable): Unit = lastResult match {
    case actual: QueryResults =>
      val actualRows = actual.results.rows
      val expectedRows = toResultRows(expected)

      if (actualRows != expectedRows) {
        // The assertion is more expensive so only run it if the equality check fails.
        assertThat(actualRows).as(describe(actual, expected, "in order")).containsExactlyElementsOf(expectedRows)
      }
      assertEqualHeaders(actual, expected)
    case failure: QueryFailure => unexpectedFailure(failure)
  }

  override protected def resultShouldBeInAnyOrder(expected: DataTable): Unit = lastResult match {
    case actual: QueryResults =>
      val actualRows = actual.results.rows
      val expectedRows = toResultRows(expected)
      if (actualRows != expectedRows) {
        assertThat(actualRows)
          .as(describe(actual, expected, "in any order"))
          .containsExactlyInAnyOrderElementsOf(expectedRows)
      }
      assertEqualHeaders(actual, expected)
    case failure: QueryFailure => unexpectedFailure(failure)
  }

  override protected def resultShouldBeInOrderIgnoringListOrder(expected: DataTable): Unit = lastResult match {
    case actual: QueryResults =>
      val actualRows = actual.results.rows
      val expectedRows = toResultRows(expected)
      if (actualRows != expectedRows) {
        // The assertion is more expensive so only run it if the equality check fails.
        assertThat(rowsWithUnorderedLists(actualRows))
          .as(describe(actual, expected, "rows in order, ignoring element order of lists"))
          .containsExactlyElementsOf(rowsWithUnorderedLists(expectedRows))
      }
      assertEqualHeaders(actual, expected)
    case failure: QueryFailure => unexpectedFailure(failure)
  }

  override protected def resultShouldBeInAnyOrderIgnoringListOrder(expected: DataTable): Unit = lastResult match {
    case actual: QueryResults =>
      val actualRows = actual.results.rows
      val expectedRows = toResultRows(expected)
      if (actualRows != expectedRows) {
        // The assertion is more expensive so only run it if the equality check fails.
        assertThat(rowsWithUnorderedLists(actualRows))
          .as(describe(actual, expected, "rows in any order, ignoring element order of lists"))
          .containsExactlyInAnyOrderElementsOf(rowsWithUnorderedLists(expectedRows))
      }
      assertEqualHeaders(actual, expected)
    case failure: QueryFailure => unexpectedFailure(failure)
  }

  override protected def sideEffectsShouldBe(expected: DataTable): Unit = {
    assertThat(lastGraphState.sideEffects(GraphState.recordGraphState(db.database)))
      .describedAs("Incorrect side effects")
      .isEqualTo(SideEffects.from(expected))
  }

  override protected def errorShouldBeRaised(expected: ExpectedError): Unit = {
    lastResult match {
      case success: QueryResults => unexpectedSuccess(success)
      case failure: QueryFailure =>
        val actual = Neo4jExceptionToExecutionFailed.convert(failure.phase, failure.cause)
        val desc = describe(failure)
        assertThat[Any](actual.errorType).as(desc).isEqualTo(expected.error)
        expected.phase.foreach(expectedPhase => assertThat[Any](actual.phase).as(desc).isEqualTo(expectedPhase))
        expected.description.foreach(expectedDesc => assertThat[Any](actual.detail).as(desc).isEqualTo(expectedDesc))
    }
  }
}

object RegularCypherCucumberSteps {
  sealed trait QueryExecution
  case class QueryResults(query: String, results: ConsumedResult) extends QueryExecution
  case class QueryFailure(query: String, phase: String, cause: Throwable) extends QueryExecution

  def toResultRows(table: DataTable): java.util.List[java.util.List[AnyRef]] = {
    if (table.isEmpty) {
      java.util.List.of()
    } else {
      val result = new util.ArrayList[java.util.List[AnyRef]](table.height() - 1)
      val cells = table.cells()
      var i = 1
      while (i < cells.size()) {
        val row = cells.get(i)
        var j = 0
        val parsedRow = new java.util.ArrayList[AnyRef](row.size())
        while (j < row.size()) {
          parsedRow.add(parse(row.get(j)))
          j += 1
        }
        result.add(parsedRow)
        i += 1
      }
      result
    }
  }

  private def renderAsTable(results: ConsumedResult): String = {
    val table = new util.ArrayList[util.List[String]](results.rows.size() + 1)
    table.add(results.headers)
    results.rows.forEach(row => table.add(row.stream().map[String](v => Objects.toString(v)).toList))
    DataTable.create(table).toString
  }

  private def assertEqualHeaders(actual: QueryResults, expected: DataTable): Unit = {
    val actualHeaders = actual.results.headers
    val expectedHeaders = if (expected.isEmpty) java.util.List.of() else expected.row(0)
    if (actualHeaders != expectedHeaders) {
      assertThat(actualHeaders).as("Result has correct headers").containsExactlyElementsOf(expectedHeaders)
    }
  }

  def unexpectedFailure(failure: QueryFailure): Unit = fail(
    s"""
       |Query failed but was expected to succeed.
       |Phase: ${failure.phase}
       |Query:
       |${failure.query}
       |
       |Cause: ${Exceptions.stringify(failure.cause)}
       |""".stripMargin
  )

  def unexpectedSuccess(results: QueryResults): Unit = fail(
    s"""
       >Query was expected to fail, but executed successfully.
       >
       >Results:
       >${RegularCypherCucumberSteps.renderAsTable(results.results)}
       >Query:
       >${results.query}
       |""".stripMargin('>') // | margins messes with the tables
  )

  private def describe(actual: QueryResults, expected: DataTable, order: String): Supplier[String] = () => {
    val expectedHeaders = if (expected.height() > 0) expected.row(0) else java.util.List.of[String]()
    s"""
       >Incorrect query result.
       >
       >Actual results:
       >${renderAsTable(actual.results)}
       >Expected results ($order):
       >${renderAsTable(ConsumedResult(expectedHeaders, toResultRows(expected)))}
       >Query:
       >${actual.query}
       >""".stripMargin('>') // | margins messes with the tables
  }

  private def describe(failure: QueryFailure): Supplier[String] = () => {
    s"""
       |Query failure (you need to scroll past the stacktrace for actual assertion error).
       |Phase: ${failure.phase}
       |Query:
       |${failure.query}
       |
       |Cause: ${Exceptions.stringify(failure.cause)}
       |""".stripMargin
  }
}
