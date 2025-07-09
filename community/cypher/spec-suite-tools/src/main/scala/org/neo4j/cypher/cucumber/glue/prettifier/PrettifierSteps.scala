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
package org.neo4j.cypher.cucumber.glue.prettifier

import com.google.inject.Inject
import io.cucumber.datatable.DataTable
import io.cucumber.scala.Scenario
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.neo4j.configuration.Config
import org.neo4j.configuration.GraphDatabaseInternalSettings
import org.neo4j.cypher.cucumber.glue.prettifier.PrettifierSteps.preParser
import org.neo4j.cypher.cucumber.glue.prettifier.PrettifierSteps.prettifier
import org.neo4j.cypher.cucumber.glue.regular.GuiceObjectFactory.injector
import org.neo4j.cypher.cucumber.glue.regular.NoOpBeforeAndAfterAllModule
import org.neo4j.cypher.cucumber.glue.regular.SingletonInjector
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedError
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedGqlError
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedGqlWarning
import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.PreParser
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.UnaliasedReturnItem
import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.ast.prettifier.Prettifier
import org.neo4j.cypher.internal.config.CypherConfiguration
import org.neo4j.cypher.internal.label_expressions.LabelExpression.DynamicLeaf
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Leaf
import org.neo4j.cypher.internal.parser.AstParserFactory
import org.neo4j.cypher.internal.util.Neo4jCypherExceptionFactory
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.internal.helpers.Exceptions

import scala.util.Failure
import scala.util.Success
import scala.util.Try

final class PrettifierSteps @Inject() () extends CypherCucumberSteps {
  private[this] val allVersions = CypherVersion.values()

  // Mutable state
  private[this] var atLeastOneSuccess = false
  private[this] var lastParseFailure: Throwable = _

  After { scenario: Scenario =>
    val expectFailure = PrettifierSteps.expectFailure(scenario)
    // Do not quietly pass if nothing parse, if that happens something is probably wrong with the test implementation.
    // We ignore partial parsing failures and rely on the regular feature tests to catch those.
    if (!atLeastOneSuccess && !expectFailure) {
      fail(
        s"""
           |The scenario had no query that parse in at least one version
           |(you can mark this scenario as expected to fail in ${getClass.getName}).
           |Last failure:
           |${Exceptions.stringify(lastParseFailure)}""".stripMargin
      )
    }
    assertTrue(atLeastOneSuccess != expectFailure, s"Scenario expected to fail but passed: ${scenario.getName}")
  }

  private def roundTripCheck(cypher: String): Unit = allVersions.foreach { version =>
    Try(parse(version, cypher)) match {
      case Success(parsed) =>
        atLeastOneSuccess = true
        roundTripCheck(version, cypher, parsed)
      case Failure(throwable) =>
        // Ignore test if individual versions do not parse, let the regular feature tests handle that case.
        lastParseFailure = throwable
    }
  }

  private def roundTripCheck(version: CypherVersion, cypher: String, parsed: Statement): Unit = {
    val prettified = prettifier.asString(parsed)
    assertThat(Try(parse(version, prettified)))
      .describedAs("Query:%n%s%n%nPrettified:%n%s", cypher, prettified)
      .isEqualTo(Success(parsed))
  }

  private def parse(version: CypherVersion, cypher: String): Statement = {
    val preparsed = preParser.preParse(cypher, version)
    AstParserFactory(version)
      .apply(preparsed.statement, Neo4jCypherExceptionFactory(cypher, None), None, Seq())
      .singleStatement()
      .endoRewrite(testRewriter)
  }

  private val testRewriter: Rewriter = {
    bottomUp(Rewriter.lift {
      /*
       * "RETURN a" might be round-tripped to "RETURN `a`"
       * This is an acceptable diversion caused by the Prettifier,
       * since it only can affect the final RETURN in a query and thus has no effect on [Fabric] subqueries.
       */
      case x: UnaliasedReturnItem => x.copy(inputText = "")(x.position)

      /*
       * The parser tracks a number of syntax characteristics
       * that causes "query" and "prettified" to have different ASTs.
       * This is rewrite removes the tracking.
       */
      case lel: Leaf if lel.containsIs        => lel.copy(containsIs = false)
      case lel: DynamicLeaf if lel.containsIs => lel.copy(containsIs = false)
    })
  }

  override def havingExecuted(cypher: String): Unit = roundTripCheck(cypher)
  override def executingQuery(cypher: String): Unit = roundTripCheck(cypher)
  override def executingControlQuery(cypher: String): Unit = roundTripCheck(cypher)
  override def havingExecutedInOpenTx(cypher: String): Unit = roundTripCheck(cypher)
  override def executingQueryInOpenTx(cypher: String): Unit = roundTripCheck(cypher)
  override def executingControlQueryInOpenTx(cypher: String): Unit = roundTripCheck(cypher)

  override def parametersAre(params: Map[String, String]): Unit = {}
  override def registerProcedure(signature: String, results: DataTable): Unit = {}
  override def registerUserFunction(name: String): Unit = {}
  override def givenCsvFile(urlParam: String, content: DataTable): Unit = {}
  override def resultShouldBeInAnyOrder(expected: DataTable): Unit = {}
  override def resultShouldBeInOrder(expected: DataTable): Unit = {}
  override def resultShouldBeInOrderIgnoringListOrder(expected: DataTable): Unit = {}
  override def resultShouldBeInAnyOrderIgnoringListOrder(expected: DataTable): Unit = {}
  override def sideEffectsShouldBe(expected: DataTable): Unit = {}
  override def errorShouldBeRaised(expected: ExpectedError): Unit = {}
  override def errorShouldBeRaised(expectedError: ExpectedGqlError): Unit = {}
  override def warningShouldBeRaised(expectedGqlWarning: ExpectedGqlWarning): Unit = {}
  override def openTransaction(): Unit = {}
  override def commitOpenTx(): Unit = {}
}

object PrettifierSteps {

  val preParser = new PreParser(CypherConfiguration.fromConfig(Config.defaults(
    GraphDatabaseInternalSettings.enable_experimental_cypher_versions,
    java.lang.Boolean.TRUE
  )))

  val prettifier: Prettifier = Prettifier(ExpressionStringifier(
    alwaysParens = true,
    alwaysBacktick = true,
    sensitiveParamsAsParams = true
  ))

  final val FactoryName = "org.neo4j.cypher.cucumber.glue.prettifier.PrettifierSteps$ObjectFactory"
  class ObjectFactory extends SingletonInjector(injector(new NoOpBeforeAndAfterAllModule()))

  def expectFailure(scenario: Scenario): Boolean = scenariosExpectedToFail.get(scenario.getName) match {
    case Some(paths) => paths.contains(scenario.getUri.getSchemeSpecificPart)
    case None        => false
  }

  private val scenariosExpectedToFail: Map[String, Seq[String]] = Seq(
    "features/general/acceptance/GpmSyntaxMixingAcceptance.feature" -> "DIFFERENT NODES with var-length relationship - OK",
    "features/general/acceptance/GpmSyntaxMixingAcceptance.feature" -> "Mixing QPP and var-length relationship quantifiers in pattern expressions in same statement - syntax error",
    "features/general/acceptance/GpmSyntaxMixingAcceptance.feature" -> "Explicit match mode DIFFERENT NODES with shortestPath - syntax error",
    "features/general/acceptance/GpmSyntaxMixingAcceptance.feature" -> "Explicit match mode DIFFERENT NODES with allShortestPaths - syntax error",
    "features/general/acceptance/GpmSyntaxMixingAllowedAcceptance.feature" -> "DIFFERENT NODES with var-length relationship - OK",
    "features/general/acceptance/GpmSyntaxMixingAllowedAcceptance.feature" -> "Mixing QPP and var-length relationship quantifiers in pattern expressions in same statement - syntax error",
    "features/general/acceptance/GpmSyntaxMixingAllowedAcceptance.feature" -> "Explicit match mode DIFFERENT NODES with shortestPath - syntax error",
    "features/general/acceptance/GpmSyntaxMixingAllowedAcceptance.feature" -> "Explicit match mode DIFFERENT NODES with allShortestPaths - syntax error",
    "features/general/acceptance/QuantifiedPathPatternAcceptance.feature" -> "Quantifier {-1} lower bound must be less than or equal to upper bound, upper bound needs to be positive",
    "features/general/acceptance/MiscAcceptance.feature" -> "Syntax error has correct code",
    "features/general/acceptance/MiscAcceptance.feature" -> "Syntax error has correct code and message"
  ).groupMap { case (_, name) => name } { case (path, _) => path }
}
