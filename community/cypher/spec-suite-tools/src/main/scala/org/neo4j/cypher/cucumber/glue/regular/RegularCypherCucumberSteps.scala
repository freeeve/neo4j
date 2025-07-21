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
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.describeConf
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.describeFailure
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.describeGqlStatusObject
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.doDescribeFailure
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.findAllGqlCodes
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.findMatchingGqlFailure
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.originalError
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.renderAsTable
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.toResultRows
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.unexpectedFailure
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.unexpectedSuccess
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedError
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedGqlError
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedGqlWarning
import org.neo4j.cypher.cucumber.user.function.SeededRandFunction
import org.neo4j.cypher.cucumber.user.function.TestFailNTimesFunction
import org.neo4j.cypher.cucumber.value.CypherCucumberValueParser
import org.neo4j.cypher.cucumber.value.CypherCucumberValueParser.parse
import org.neo4j.cypher.cucumber.value.ResultValueMapper
import org.neo4j.cypher.cucumber.value.ResultValueMapper.UnorderedList.rowsWithUnorderedLists
import org.neo4j.cypher.testing.api.ConsumedResult
import org.neo4j.cypher.testing.api.CypherExecutorException
import org.neo4j.cypher.testing.api.CypherExecutorTransaction
import org.neo4j.cypher.testing.impl.FeatureDatabaseManagementService
import org.neo4j.gqlstatus.ErrorGqlStatusObject
import org.neo4j.graphdb.GqlStatusObject
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

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.IterableHasAsScala
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
  private[this] var openTx: CypherExecutorTransaction = _ // Only used for certain steps

  Before { scenario: Scenario => before(scenario) }

  def before(scenario: Scenario): Unit = {
    // Note, @fail tags are handled in SkipFailsScenarios (or OnlyFailsScenarios).
    assumeFalse(expectations.ignore(scenario), "Scenario ignored because of @ignore tag")
    dbmsAccessor = executors.acquire(scenario)
    db = dbmsAccessor.dbms
  }

  After { after() }

  def after(): Unit = {
    if (openTx != null) openTx.close()
    if (db != null) db.unregisterProcedures(registeredProcedures)
    if (dbmsAccessor != null) executors.release(dbmsAccessor)
  }

  override def registerProcedure(signature: String, results: DataTable): Unit = {
    val output = results.cells().stream()
      .skip(1) // Header row
      .map(row => row.stream().map(v => ValueUtils.asValue(parse(v))).toArray(i => new Array[AnyValue](i)))
      .toArray(i => new Array[Array[AnyValue]](i))
    val procedure = ProcedureBuilder.createProcedure(signature, output)
    db.registerProcedure(procedure)
    registeredProcedures = registeredProcedures.appended(procedure.signature().name())
  }

  override def registerUserFunction(name: String): Unit = {
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
      case "test.seededRand" =>
        registeredProcedures =
          registeredProcedures ++ Seq(new QualifiedName("test", "seededRand"), new QualifiedName("test", "setSeed"))
        db.registerFunction(classOf[SeededRandFunction])
      case _ =>
        throw new IllegalArgumentException(s"$name is not a recognised UDF name")
    }
  }

  override def parametersAre(params: Map[String, String]): Unit = {
    parameters = parameters ++ params.view.mapValues(string => CypherCucumberValueParser.parse(string, conf.useBolt))
  }

  override def givenCsvFile(urlParam: String, content: DataTable): Unit = {
    val file = Files.createTempFile("test-csv", ".csv")
    file.toFile.deleteOnExit()
    val csvContent = content.cells().asScala.view.map(_.asScala.mkString(",")).mkString("\n")
    Files.write(file, csvContent.getBytes(StandardCharsets.UTF_8))
    parameters = parameters + (urlParam -> file.toUri.toURL.toString)
  }

  override def havingExecuted(cypher: String): Unit = {
    db.execute(cypher, parameters, _.consume())
  }

  override def executingQuery(cypher: String): Unit = {
    lastGraphState = KernelGraphState.recordGraphState(db.database)
    lastResult = execute(conf.preparserPrefix + cypher)
  }

  override def executingControlQuery(cypher: String): Unit = {
    lastResult = execute(cypher)
  }

  private def execute(cypher: String): QueryExecution = {
    convertConsumedResult(cypher, Try(db.execute(cypher, parameters, _.consume(ResultValueMapper))))
  }

  private def executeInOpenTx(cypher: String): QueryExecution = {
    convertConsumedResult(cypher, Try(openTx.execute(cypher, parameters).consume(ResultValueMapper)))
  }

  private def convertConsumedResult(cypher: String, result: Try[ConsumedResult]): QueryExecution = result match {
    case Success(results) => QueryResults(cypher, results)
    case Failure(queryException) =>
      val phase = Try(failurePhase(cypher)) match {
        case Success(value) => value
        case Failure(error) => s"Failed to find phase: $error"
      }
      QueryFailure(cypher, phase, queryException)
  }

  private def failurePhase(cypher: String): String = Using.resource(db.begin()) { tx =>
    val explainSucceeds = Try(tx.execute("EXPLAIN\n" + cypher, parameters).consume()).isSuccess
    val rollbackSucceeds = Try(tx.rollback()).isSuccess
    if (explainSucceeds || !rollbackSucceeds) Phase.runtime else Phase.compile
  }

  override def resultShouldBeInOrder(expected: DataTable): Unit = lastResult match {
    case actual: QueryResults =>
      val actualRows = actual.results.rows
      val expectedRows = toResultRows(expected)

      if (actualRows != expectedRows) {
        // The assertion is more expensive so only run it if the equality check fails.
        assertThat(actualRows).as(describeResults(actual, expected, "in order")).containsExactlyElementsOf(expectedRows)
      }
      assertEqualHeaders(actual, expected)
    case failure: QueryFailure => unexpectedFailure(failure, conf)
  }

  override def resultShouldBeInAnyOrder(expected: DataTable): Unit = lastResult match {
    case actual: QueryResults =>
      val actualRows = actual.results.rows
      val expectedRows = toResultRows(expected)
      if (actualRows != expectedRows) {
        assertThat(actualRows)
          .as(describeResults(actual, expected, "in any order"))
          .containsExactlyInAnyOrderElementsOf(expectedRows)
      }
      assertEqualHeaders(actual, expected)
    case failure: QueryFailure => unexpectedFailure(failure, conf)
  }

  override def resultShouldBeInOrderIgnoringListOrder(expected: DataTable): Unit = lastResult match {
    case actual: QueryResults =>
      val actualRows = actual.results.rows
      val expectedRows = toResultRows(expected)
      if (actualRows != expectedRows) {
        // The assertion is more expensive so only run it if the equality check fails.
        assertThat(rowsWithUnorderedLists(actualRows))
          .as(describeResults(actual, expected, "rows in order, ignoring element order of lists"))
          .containsExactlyElementsOf(rowsWithUnorderedLists(expectedRows))
      }
      assertEqualHeaders(actual, expected)
    case failure: QueryFailure => unexpectedFailure(failure, conf)
  }

  override def resultShouldBeInAnyOrderIgnoringListOrder(expected: DataTable): Unit = lastResult match {
    case actual: QueryResults =>
      val actualRows = actual.results.rows
      val expectedRows = toResultRows(expected)
      if (actualRows != expectedRows) {
        // The assertion is more expensive so only run it if the equality check fails.
        assertThat(rowsWithUnorderedLists(actualRows))
          .as(describeResults(actual, expected, "rows in any order, ignoring element order of lists"))
          .containsExactlyInAnyOrderElementsOf(rowsWithUnorderedLists(expectedRows))
      }
      assertEqualHeaders(actual, expected)
    case failure: QueryFailure => unexpectedFailure(failure, conf)
  }

  override def sideEffectsShouldBe(expectedTable: DataTable): Unit = {
    val actual = lastGraphState match {
      case state: KernelGraphState => state.sideEffects(KernelGraphState.recordGraphState(db.database))
      case state: CypherGraphState => state.sideEffects(CypherGraphState.recordGraphState(openTx))
      case other                   => throw new IllegalStateException(s"Unexpected graph state: " + other)
    }
    val expected = SideEffects.from(expectedTable)
    if (actual != expected) {
      fail(
        s"""
           >Incorrect side effects.
           >
           >Actual side effects:
           >$actual
           >Expected side effects:
           >$expected
           >Plan:
           >${describePlan(lastResult)}
           >""".stripMargin('>') // | margins messes with the tables
      )
    }
  }

  override def errorShouldBeRaised(expected: ExpectedError): Unit = lastResult match {
    case success: QueryResults => unexpectedSuccess(success, conf)
    case failure: QueryFailure =>
      val actual = Neo4jExceptionToExecutionFailed.convert(failure.phase, failure.cause)
      val desc = describeFailure(failure)
      assertThat[Any](actual.errorType).as(desc).isEqualTo(expected.error)
      expected.phase.foreach(expectedPhase => assertThat[Any](actual.phase).as(desc).isEqualTo(expectedPhase))
      expected.description.foreach(expectedDesc => assertThat[Any](actual.detail).as(desc).isEqualTo(expectedDesc))
  }

  override def errorShouldBeRaised(expectedError: ExpectedGqlError): Unit = lastResult match {
    case success: QueryResults => unexpectedSuccess(success, conf)
    case failure: QueryFailure => findMatchingGqlFailure(expectedError.code, originalError(failure.cause)) match {
        case Some(actualGql) =>
          val desc = describeFailure(failure)
          assertThat[Any](actualGql.code).as(desc).isEqualTo(expectedError.code)
          expectedError.descriptionContains.foreach { e =>
            assertThat[Any](actualGql.message).as(desc).asString.contains(e)
          }
        case None =>
          val found = findAllGqlCodes(originalError(failure.cause))
          fail(
            s"""
               |Expected GQL status ${expectedError.code} but found $found in:
               |${doDescribeFailure(failure)}
               |""".stripMargin
          )
      }
  }

  override def warningShouldBeRaised(expectedWarning: ExpectedGqlWarning): Unit = lastResult match {
    case actual: QueryResults =>
      val actualGqlStatusObjects = actual.results.qqlStatusObjects.asScala
      actualGqlStatusObjects.find(qqlStatusObject => qqlStatusObject.gqlStatus() == expectedWarning.code) match {
        case Some(actualGqlStatusObject) =>
          val desc = describeGqlStatusObject(actualGqlStatusObject)
          assertThat[Any](actualGqlStatusObject.gqlStatus).as(desc).isEqualTo(expectedWarning.code)
          expectedWarning.descriptionContains.foreach { e =>
            assertThat[Any](actualGqlStatusObject.statusDescription()).as(desc).asString.contains(e)
          }
        case None =>
          val found = actualGqlStatusObjects.map(_.gqlStatus())
          fail(
            s"""
               |Expected GQL status ${expectedWarning.code} but found $found
               |""".stripMargin
          )
      }
    case failure: QueryFailure => unexpectedFailure(failure, conf)
  }

  override def openTransaction(): Unit = {
    openTx = db.begin()
  }

  override def havingExecutedInOpenTx(cypher: String): Unit = {
    openTx.execute(cypher, parameters).consume()
  }

  override def executingQueryInOpenTx(cypher: String): Unit = {
    lastGraphState = CypherGraphState.recordGraphState(openTx)
    lastResult = executeInOpenTx(conf.preparserPrefix + cypher)
  }

  override def executingControlQueryInOpenTx(cypher: String): Unit = {
    lastResult = executeInOpenTx(cypher)
  }

  override def commitOpenTx(): Unit = {
    try {
      openTx.commit()
    } catch {
      case t: Throwable => fail("Failed to commit open transaction", originalError(t))
    } finally {
      openTx.close()
    }
  }

  private def describeResults(
    actual: QueryResults,
    expected: DataTable,
    order: String,
    header: String = "Incorrect query result."
  ): Supplier[String] = () => {
    val expectedHeaders = if (expected.height() > 0) expected.row(0) else java.util.List.of[String]()

    s"""
       >$header
       >
       >Actual results:
       >${renderAsTable(actual.results)}
       >Expected results${Some(order).filter(_.nonEmpty).map(o => s" ($o)").getOrElse("")}:
       >${renderAsTable(ConsumedResult(expectedHeaders, toResultRows(expected), null))}
       >Query:
       >${actual.query}
       >
       >Config (excl tag based config, @conf:...):
       >${describeConf(conf)}
       >
       >Plan:
       >${describePlan(actual)}
       >""".stripMargin('>') // | margins messes with the tables
  }

  private def describePlan(actual: QueryExecution): String = Try {
    Using.resource(db.database.beginTx()) { tx =>
      tx.execute("EXPLAIN\n" + actual.query).getExecutionPlanDescription.toString
    }
  } match {
    case Success(planDesc) => planDesc
    case Failure(error)    => s"Failed to produce plan: " + error
  }

  private def assertEqualHeaders(actual: QueryResults, expected: DataTable): Unit = {
    val actualHeaders = actual.results.headers
    val expectedHeaders = if (expected.isEmpty) java.util.List.of() else expected.row(0)
    if (actualHeaders != expectedHeaders) {
      assertThat(actualHeaders)
        .as(describeResults(actual, expected, "", "Result has correct headers"))
        .containsExactlyElementsOf(expectedHeaders)
    }
  }

  def getDbmsAccessor: DbAccessor = dbmsAccessor
}

object RegularCypherCucumberSteps {

  sealed trait QueryExecution {
    def query: String
  }
  case class QueryResults(query: String, results: ConsumedResult) extends QueryExecution
  case class QueryFailure(query: String, phase: String, cause: Throwable) extends QueryExecution

  case class GqlFailure(code: String, description: String, message: String)

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

  def renderAsTable(results: ConsumedResult): String = {
    val table = new util.ArrayList[util.List[String]](results.rows.size() + 1)
    table.add(results.headers)
    results.rows.forEach(row => table.add(row.stream().map[String](v => Objects.toString(v)).toList))
    DataTable.create(table).toString
  }

  def describeConf(conf: TestConf): String = conf.neo4jConf.map { case (key, value) => s"$key=$value" }.mkString("\n")

  def unexpectedFailure(failure: QueryFailure, conf: TestConf): Unit = fail(
    s"""
       |Query failed but was expected to succeed.
       |Phase: ${failure.phase}
       |Query:
       |${failure.query}
       |
       |Cause: ${Exceptions.stringify(originalError(failure.cause))}
       |
       |Config (excl tag based config, @conf:...):
       |${describeConf(conf)}
       |""".stripMargin
  )

  def unexpectedSuccess(results: QueryResults, conf: TestConf): Unit = fail(
    s"""
       >Query was expected to fail, but executed successfully.
       >
       >Results:
       >${RegularCypherCucumberSteps.renderAsTable(results.results)}
       >Query:
       >${results.query}
       >
       >Config (excl tag based config, @conf:...):
       >${describeConf(conf)}
       |""".stripMargin('>') // | margins messes with the tables
  )

  def originalError(throwable: Throwable): Throwable = throwable match {
    case e: CypherExecutorException => e.original
    case _                          => throwable
  }

  @tailrec
  def findMatchingGqlFailure(code: String, throwable: AnyRef): Option[GqlFailure] = throwable match {
    case e: org.neo4j.driver.exceptions.Neo4jException if e.gqlStatus() == code =>
      Some(GqlFailure(e.gqlStatus(), e.statusDescription(), e.getMessage))
    case e: org.neo4j.driver.exceptions.Neo4jException => findMatchingGqlFailure(code, e.gqlCause().orElse(null))
    case e: ErrorGqlStatusObject if e.gqlStatus() == code =>
      Some(GqlFailure(e.gqlStatus(), e.statusDescription(), e.getMessage))
    case e: ErrorGqlStatusObject => findMatchingGqlFailure(code, e.cause().orElse(null))
    case _                       => None
  }

  @tailrec
  def findAllGqlCodes(throwable: AnyRef, result: Seq[String] = Seq.empty): Seq[String] = throwable match {
    case e: org.neo4j.driver.exceptions.Neo4jException =>
      findAllGqlCodes(e.gqlCause().orElse(null), result.appended(e.gqlStatus()))
    case e: ErrorGqlStatusObject => findAllGqlCodes(e.cause().orElse(null), result.appended(e.gqlStatus()))
    case _                       => result
  }

  private def describeFailure(failure: QueryFailure): Supplier[String] = () =>
    "Query failure (you need to scroll past the stacktrace for actual assertion error).\n" + doDescribeFailure(failure)

  private def doDescribeFailure(failure: QueryFailure): String = {
    s"""
       |Phase: ${failure.phase}
       |Query:
       |${failure.query}
       |
       |Cause: ${Exceptions.stringify(originalError(failure.cause))}
       |""".stripMargin
  }

  private def describeGqlStatusObject(gqlStatusObject: GqlStatusObject): String = {
    s"""
       |Code:
       |${gqlStatusObject.gqlStatus()}
       |
       |Message: ${gqlStatusObject.statusDescription()}
       |""".stripMargin
  }
}
