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
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedError
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedGqlError

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
    resultShouldBeInOrder(expected)
  }

  Then("the result should be, in any order:") { (expected: DataTable) =>
    resultShouldBeInAnyOrder(expected)
  }

  Then("""the result should be, in order \(ignoring element order for lists):""") { (expected: DataTable) =>
    resultShouldBeInOrderIgnoringListOrder(expected)
  }

  Then("""the result should be \(ignoring element order for lists):""") { (expected: DataTable) =>
    resultShouldBeInAnyOrderIgnoringListOrder(expected)
  }

  Then("the result should be empty") {
    resultShouldBeInOrder(DataTable.emptyDataTable())
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

  private def readNamedGraphCypher(name: String): String = {
    IOUtils.resourceToString(s"graphs/$name/$name.cypher", StandardCharsets.UTF_8, getClass.getClassLoader)
  }
  protected def parametersAre(params: Map[String, String]): Unit
  protected def registerProcedure(signature: String, results: DataTable): Unit
  protected def registerUserFunction(name: String): Unit
  protected def givenCsvFile(urlParam: String, content: DataTable): Unit
  protected def havingExecuted(cypher: String): Unit
  protected def executingQuery(cypher: String): Unit
  protected def executingControlQuery(cypher: String): Unit
  protected def resultShouldBeInAnyOrder(expected: DataTable): Unit
  protected def resultShouldBeInOrder(expected: DataTable): Unit
  protected def resultShouldBeInOrderIgnoringListOrder(expected: DataTable): Unit
  protected def resultShouldBeInAnyOrderIgnoringListOrder(expected: DataTable): Unit
  protected def sideEffectsShouldBe(expected: DataTable): Unit
  protected def errorShouldBeRaised(expectedError: ExpectedError): Unit
  protected def errorShouldBeRaised(expectedError: ExpectedGqlError): Unit
}

object CypherCucumberSteps {
  case class ExpectedError(error: String, description: Option[String], phase: Option[String])
  case class ExpectedGqlError(code: String, descriptionContains: Option[String])
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

  protected def openTransaction(): Unit
  protected def havingExecutedInOpenTx(cypher: String): Unit
  protected def executingQueryInOpenTx(cypher: String): Unit
  protected def executingControlQueryInOpenTx(cypher: String): Unit
  protected def commitOpenTx(): Unit
}
