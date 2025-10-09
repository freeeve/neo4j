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
package org.neo4j.cypher.cucumber.steps

import io.cucumber.datatable.DataTable
import io.cucumber.scala.EN
import io.cucumber.scala.ScalaDsl
import org.apache.commons.io.IOUtils
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.ResultDoublePrecision
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.ResultDoublePrecision.Exact
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.ResultDoublePrecision.Within
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.ResultOrderOption
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.ResultOrderOption.InAnyOrder
import org.neo4j.cypher.cucumber.glue.regular.RegularCypherCucumberSteps.ResultOrderOption.InOrder
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedError
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedGqlError
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedGqlWarning

import java.nio.charset.StandardCharsets

import scala.jdk.CollectionConverters.MapHasAsScala

/**
 * Step definitions of all Cypher Cucumber steps (from the .feature test files).
 */
trait CypherCucumberSteps extends InOpenTxCypherCucumberSteps {

  // Given
  // =====

  Given("an empty graph") {}

  Given("any graph") {
    havingExecuted(readNamedGraphCypher("binary-tree-1"))
  }

  Given("""the {word} graph""") { (graphName: String) =>
    havingExecuted(readNamedGraphCypher(graphName))
  }

  Given("""parameters are:""") { (params: DataTable) =>
    parametersAre(params.asMap().asScala.toMap)
  }

  Given("^there exists a procedure (.+):$") { (signature: String, results: DataTable) =>
    registerProcedure(signature, results)
  }

  Given("there exists a CSV file with URL as ${word}, with rows:") { (param: String, content: DataTable) =>
    givenCsvFile(param, content)
  }

  Given("the {word} function is registered") { (func: String) =>
    registerUserFunction(func)
  }

  // And
  // ===

  And("having executed:") { query: String => havingExecuted(query) }

  // When
  // ====

  When("executing query:") { query: String => executingQuery(query) }

  When("""executing control query:""") { (query: String) =>
    executingControlQuery(query)
  }

  // Then
  // ====

  Then("the result should be, in order:") { (expected: DataTable) =>
    resultShouldBe(expected)(_.withRowsInOrder())
  }

  Then("the result should be, in order, to within {double}:") { (epsilon: Double, expected: DataTable) =>
    resultShouldBe(expected)(_.withRowsInOrder().withPrecision(epsilon))
  }

  Then("""the result should be, in order \(or any order if executed in parallel):""") { (expected: DataTable) =>
    resultShouldBe(expected)(_.withRowsInOrder().withAnyResultOrderInParallelRuntime())
  }

  Then("""the result should be, in order \(or any order ignoring element order for lists if executed in parallel):""") {
    (expected: DataTable) =>
      resultShouldBe(expected)(
        _.withRowsInOrder().withAnyResultOrderInParallelRuntime().withAnySublistOrderInParallelRuntime()
      )
  }

  Then(
    """the result should be, in order \(or any order if executed in parallel) \(ignoring element order for lists):"""
  ) { (expected: DataTable) =>
    resultShouldBe(expected)(_.withRowsInOrder().withAnyResultOrderInParallelRuntime().withSublistsInAnyOrder())
  }

  Then(
    """the result should be, in any order \(and ignoring element order for lists if executed in parallel):"""
  ) {
    (expected: DataTable) =>
      resultShouldBe(expected)(_.withRowsInAnyOrder().withAnySublistOrderInParallelRuntime())
  }

  Then("the result should be, in any order:") { (expected: DataTable) =>
    resultShouldBe(expected)(_.withRowsInAnyOrder())
  }

  Then("the result should be, in any order, to within {double}:") { (epsilon: Double, expected: DataTable) =>
    resultShouldBe(expected)(_.withRowsInAnyOrder().withPrecision(epsilon))
  }

  Then("""the result should be, in order \(ignoring element order for lists):""") { (expected: DataTable) =>
    resultShouldBe(expected)(_.withRowsInOrder().withSublistsInAnyOrder())
  }

  Then("""the result should be \(ignoring element order for lists):""") { (expected: DataTable) =>
    resultShouldBe(expected)(_.withRowsInAnyOrder().withSublistsInAnyOrder())
  }

  Then("the result should be empty") {
    resultShouldBe(DataTable.emptyDataTable())(_.withRowsInOrder())
  }

  Then("no side effects") {
    sideEffectsShouldBe(DataTable.emptyDataTable())
  }

  Then("the side effects should be:") { expected: DataTable =>
    sideEffectsShouldBe(expected)
  }

  Then("^an? (\\w+) should be raised at (compile time|runtime|any time): (.+)$") {
    (error: String, phase: String, description: String) =>
      errorShouldBeRaised(ExpectedError(
        error = error,
        description = Option.when(description != "*")(description),
        phase = Option.when(phase != "any time")(phase)
      ))
  }

  // Note, searches through all causes for the correct code
  Then("execution should fail with GQL code {word}") { code: String =>
    errorShouldBeRaised(ExpectedGqlError(code, None))
  }

  // Note, searches though all causes for the correct code and description
  Then("execution should fail with GQL code {word} and message containing:") {
    (code: String, description: String) =>
      errorShouldBeRaised(ExpectedGqlError(code, Some(description)))
  }

  Then("execution should raise a warning with GQL code {word}") { code: String =>
    warningShouldBeRaised(ExpectedGqlWarning(code, None))
  }

  Then("execution should raise a warning with GQL code {word} and message containing:") {
    (code: String, description: String) =>
      warningShouldBeRaised(ExpectedGqlWarning(code, Some(description)))
  }

  private def readNamedGraphCypher(name: String): String = {
    IOUtils.resourceToString(s"graphs/$name/$name.cypher", StandardCharsets.UTF_8, getClass.getClassLoader)
  }
  def parametersAre(params: Map[String, String]): Unit
  def registerProcedure(signature: String, results: DataTable): Unit
  def registerUserFunction(name: String): Unit
  def givenCsvFile(urlParam: String, content: DataTable): Unit
  def havingExecuted(cypher: String): Unit
  def executingQuery(cypher: String): Unit
  def executingControlQuery(cypher: String): Unit
  def resultShouldBe(expected: DataTable)(in: ResultAssertionBuilder => ResultAssertionBuilder): Unit
  def sideEffectsShouldBe(expected: DataTable): Unit
  def errorShouldBeRaised(expectedError: ExpectedError): Unit
  def errorShouldBeRaised(expectedError: ExpectedGqlError): Unit
  def warningShouldBeRaised(expectedWarning: ExpectedGqlWarning): Unit
}

object CypherCucumberSteps {
  case class ExpectedError(error: String, description: Option[String], phase: Option[String])
  case class ExpectedGqlError(code: String, descriptionContains: Option[String])
  case class ExpectedGqlWarning(code: String, descriptionContains: Option[String])
}

trait InOpenTxCypherCucumberSteps extends ScalaDsl with EN {

  // Given
  // =====

  Given("an open transaction") { openTransaction() }

  // And
  // ===

  And("having executed, in open tx:") { query: String => havingExecutedInOpenTx(query) }

  // When
  // ====

  When("executing query, in open tx:") { query: String => executingQueryInOpenTx(query) }
  When("executing control query, in open tx:") { (query: String) => executingControlQueryInOpenTx(query) }

  When("open transaction is commited and re-opened") {
    commitOpenTx()
    openTransaction()
  }

  // Then
  // ====

  Then("open transaction should commit without errors") {
    commitOpenTx()
  }

  def openTransaction(): Unit
  def havingExecutedInOpenTx(cypher: String): Unit
  def executingQueryInOpenTx(cypher: String): Unit
  def executingControlQueryInOpenTx(cypher: String): Unit
  def commitOpenTx(): Unit
}

class ResultAssertionBuilder(isParallelRuntime: Boolean) {
  protected var resultOrdering: ResultOrderOption = InOrder
  protected var sublistOrdering: ResultOrderOption = InOrder
  protected var parallelResultsOverride: Option[ResultOrderOption] = None
  protected var parallelSublistsOverride: Option[ResultOrderOption] = None
  protected var doublePrecision: ResultDoublePrecision = Exact

  def getResultOrdering: ResultOrderOption = resultOrdering
  def getSublistOrdering: ResultOrderOption = sublistOrdering
  def getPrecision: ResultDoublePrecision = doublePrecision

  def wouldOrderResults(): ResultOrderOption = parallelResultsOverride.getOrElse(resultOrdering)
  def wouldOrderSublists(): ResultOrderOption = parallelSublistsOverride.getOrElse(sublistOrdering)

  def withRowsInOrder(): ResultAssertionBuilder = {
    this.resultOrdering = InOrder
    this
  }

  def withRowsInAnyOrder(): ResultAssertionBuilder = {
    this.resultOrdering = InAnyOrder
    this
  }

  def withSublistsInOrder(): ResultAssertionBuilder = {
    this.sublistOrdering = InOrder
    this
  }

  def withSublistsInAnyOrder(): ResultAssertionBuilder = {
    this.sublistOrdering = InAnyOrder
    this
  }

  def withPrecision(epsilon: Double): ResultAssertionBuilder = {
    epsilon match {
      case 0 => this.doublePrecision = Exact
      case _ => this.doublePrecision = Within(Math.abs(epsilon))
    }
    this
  }

  def withStrictResultOrderInParallelRuntime(): ResultAssertionBuilder = {
    if (isParallelRuntime) {
      parallelResultsOverride = Some(InOrder)
    }
    this
  }

  def withAnyResultOrderInParallelRuntime(): ResultAssertionBuilder = {
    if (isParallelRuntime) {
      parallelResultsOverride = Some(InAnyOrder)
    }
    this
  }

  def withStrictSublistOrderInParallelRuntime(): ResultAssertionBuilder = {
    if (isParallelRuntime) {
      parallelSublistsOverride = Some(InOrder)
    }
    this
  }

  def withAnySublistOrderInParallelRuntime(): ResultAssertionBuilder = {
    if (isParallelRuntime) {
      parallelSublistsOverride = Some(InAnyOrder)
    }
    this
  }

  override def toString: String = {
    val rowOrderInner: String = wouldOrderResults() match {
      case InOrder    => "in order"
      case InAnyOrder => "in any order"
    }
    val rowOrderOuter: String = wouldOrderSublists() match {
      case InOrder    => rowOrderInner
      case InAnyOrder => s"rows $rowOrderInner, ignoring element order of lists"
    }
    val precisionPreference: String = doublePrecision match {
      case Exact           => ""
      case Within(epsilon) => s", to within $epsilon"
    }

    s"$rowOrderOuter$precisionPreference"
  }
}
