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
package org.neo4j.cypher.cucumber.glue.regular.steps

import com.google.inject.Inject
import cypher.features.Phase
import io.cucumber.datatable.DataTable
import io.cucumber.scala.Scenario
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.neo4j.cypher.cucumber.glue.regular.DbAccessor
import org.neo4j.cypher.cucumber.glue.regular.Executors
import org.neo4j.cypher.cucumber.glue.regular.Expectations
import org.neo4j.cypher.cucumber.glue.regular.TestConf
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.QueryExecution
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.QueryFailure
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.QueryResults
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.ResultDoublePrecision.Exact
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.ResultDoublePrecision.Within
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.ResultOrderOption.InAnyOrder
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.ResultOrderOption.InOrder
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.describeConf
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.describeGqlStatusObject
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.originalError
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.renderAsTable
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.toResultRows
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.unexpectedFailure
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedGqlWarning
import org.neo4j.cypher.cucumber.steps.ResultAssertionBuilder
import org.neo4j.cypher.cucumber.user.function.SeededRandFunction
import org.neo4j.cypher.cucumber.user.function.TestFailNTimesFunction
import org.neo4j.cypher.cucumber.value.CypherCucumberValueParser
import org.neo4j.cypher.cucumber.value.CypherCucumberValueParser.parse
import org.neo4j.cypher.cucumber.value.ResultValueMapper
import org.neo4j.cypher.cucumber.value.ResultValueMapper.CloseEnoughNumbersList.rowsWithCloseEnoughNumbers
import org.neo4j.cypher.cucumber.value.ResultValueMapper.UnorderedList.rowsWithUnorderedLists
import org.neo4j.cypher.testing.api.ConsumedResult
import org.neo4j.cypher.testing.api.CypherExecutorException
import org.neo4j.cypher.testing.api.CypherExecutorTransaction
import org.neo4j.cypher.testing.impl.FeatureDatabaseManagementService
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

import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using

/**
 * The default implementation of the Cypher Cucumber steps (.feature files).
 */
final class RegularCypherSteps @Inject() (
  val conf: TestConf,
  executors: Executors,
  expectations: Expectations
) extends CypherCucumberSteps with RegularErrorSteps {

  // Mutable state
  private[this] var dbmsAccessor: DbAccessor = _
  private[this] var db: FeatureDatabaseManagementService = _
  private[this] var parameters = Map.empty[String, AnyRef]
  private[this] var lastGraphState: GraphState = _
  private[this] var lastResult: QueryExecution = _
  private[this] var registeredProcedures = Seq.empty[QualifiedName]
  private[this] var openTx: CypherExecutorTransaction = _ // Only used for certain steps
  private[this] var scenario: Scenario = _

  override def lastExecutionResult: QueryExecution = lastResult

  Before { scenario: Scenario => before(scenario) }

  def before(scenario: Scenario): Unit = {
    // Note, @fail tags are handled in SkipFailsScenarios (or OnlyFailsScenarios).
    assumeFalse(expectations.ignore(scenario), "Scenario ignored because of @ignore tag")
    dbmsAccessor = executors.acquire(scenario)
    db = dbmsAccessor.dbms
    this.scenario = scenario
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

  override def resultShouldBe(expected: DataTable)(in: ResultAssertionBuilder => ResultAssertionBuilder): Unit =
    lastResult match {
      case failure: QueryFailure =>
        unexpectedFailure(failure, conf)
      case actual: QueryResults =>
        val builder: RegularResultAssertionBuilder =
          in(new RegularResultAssertionBuilder(
            actual,
            expected,
            conf.isParallelRuntime
          )).asInstanceOf[RegularResultAssertionBuilder]
        builder.assert()
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
    epsilon: Option[Double] = None,
    header: String = "Incorrect query result."
  ): Supplier[String] = () => {
    val expectedHeaders = if (expected.height() > 0) expected.row(0) else java.util.List.of[String]()
    val expectedSuffix = (order, epsilon) match {
      case ("", None)             => ""
      case ("", Some(epsilon))    => s" (to within $epsilon)"
      case (order, None)          => s" ($order)"
      case (order, Some(epsilon)) => s" ($order, to within $epsilon)"
    }
    s"""
       >$header
       >
       >Actual results:
       >${renderAsTable(actual.results)}
       >Expected results$expectedSuffix:
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
        .as(describeResults(actual, expected, "", header = "Result has correct headers"))
        .containsExactlyElementsOf(expectedHeaders)
    }
  }

  def getDbmsAccessor: DbAccessor = dbmsAccessor

  class RegularResultAssertionBuilder(actual: QueryResults, expected: DataTable, isParallelRuntime: Boolean)
      extends ResultAssertionBuilder(isParallelRuntime) {

    def assert(): Unit = {
      val rowsMapper: util.List[util.List[AnyRef]] => util.List[util.List[AnyRef]] =
        (wouldOrderSublists(), doublePrecision) match {
          case (InOrder, Exact) =>
            identity[util.List[util.List[AnyRef]]]
          case (InAnyOrder, Exact) =>
            rowsWithUnorderedLists
          case (InOrder, Within(epsilon)) =>
            rowsWithCloseEnoughNumbers(epsilon)
          case (InAnyOrder, Within(epsilon)) =>
            rowsWithUnorderedLists _ andThen rowsWithCloseEnoughNumbers(epsilon)
        }

      val actualRows = rowsMapper(actual.results.rows)
      val expectedRows = rowsMapper(toResultRows(expected))

      wouldOrderResults() match {
        case InOrder =>
          assertThat(actualRows).as(describeResults(
            actual,
            expected,
            this.toString()
          )).containsExactlyElementsOf(expectedRows)
        case InAnyOrder =>
          assertThat(actualRows).as(describeResults(
            actual,
            expected,
            this.toString()
          )).containsExactlyInAnyOrderElementsOf(expectedRows)
      }

      assertEqualHeaders(actual, expected)
    }
  }
}

object RegularCypherSteps {

  sealed trait QueryExecution {
    def query: String
  }
  case class QueryResults(query: String, results: ConsumedResult) extends QueryExecution
  case class QueryFailure(query: String, phase: String, cause: Throwable) extends QueryExecution

  case class GqlFailure(code: String, classification: String, description: String, message: String)

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
       >${RegularCypherSteps.renderAsTable(results.results)}
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

  private[steps] def describeFailure(failure: QueryFailure): Supplier[String] = () =>
    "Query failure (you need to scroll past the stacktrace for actual assertion error).\n" + doDescribeFailure(failure)

  private[steps] def doDescribeFailure(failure: QueryFailure): String = {
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

  sealed trait ResultOrderOption

  object ResultOrderOption {
    case object InOrder extends ResultOrderOption
    case object InAnyOrder extends ResultOrderOption
  }

  sealed trait ResultDoublePrecision

  object ResultDoublePrecision {
    case object Exact extends ResultDoublePrecision
    case class Within(epsilon: Double) extends ResultDoublePrecision
  }
}
