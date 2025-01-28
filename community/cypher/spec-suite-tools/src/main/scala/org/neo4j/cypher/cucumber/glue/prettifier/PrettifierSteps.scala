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
import org.neo4j.configuration.Config
import org.neo4j.cypher.cucumber.glue.prettifier.PrettifierSteps.preParser
import org.neo4j.cypher.cucumber.glue.prettifier.PrettifierSteps.prettifier
import org.neo4j.cypher.cucumber.glue.regular.GuiceObjectFactory
import org.neo4j.cypher.cucumber.glue.regular.NoOpBeforeAndAfterAllModule
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedError
import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.PreParser
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.UnaliasedReturnItem
import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.ast.prettifier.Prettifier
import org.neo4j.cypher.internal.config.CypherConfiguration
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Leaf
import org.neo4j.cypher.internal.parser.AstParserFactory
import org.neo4j.cypher.internal.util.Neo4jCypherExceptionFactory
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp

import scala.util.Failure
import scala.util.Success
import scala.util.Try

final class PrettifierSteps @Inject() () extends CypherCucumberSteps {
  private[this] val allVersions = CypherVersion.values()

  // Mutable state
  private[this] var atLeastOneSuccess = false

  After { scenario: Scenario =>
    val expectFailure = PrettifierSteps.expectFailure(scenario)
    // Do not quietly pass if nothing parse, if that happens something is probably wrong with the test implementation.
    // We ignore partial parsing failures and rely on the regular feature tests to catch those.
    assertTrue(
      atLeastOneSuccess || expectFailure,
      s"The scenario had no query that parse in at least one version (you can mark this scenario as expected to fail in ${getClass.getName})"
    )
    assertTrue(atLeastOneSuccess != expectFailure, s"Scenario expected to fail but passed: ${scenario.getName}")
  }

  private def roundTripCheck(cypher: String): Unit = allVersions.foreach { version =>
    Try(parse(version, cypher)) match {
      case Success(parsed) =>
        atLeastOneSuccess = true
        roundTripCheck(version, cypher, parsed)
      case Failure(_) =>
      // Ignore test if individual versions do not parse, let the regular feature tests handle that case.
    }
  }

  private def roundTripCheck(version: CypherVersion, cypher: String, parsed: Statement): Unit = {
    val prettified = prettifier.asString(parsed)
    assertThat(Try(parse(version, prettified)))
      .describedAs("Query:%n%s%n%nPrettified:%n%s", cypher, prettified)
      .isEqualTo(Success(parsed))
  }

  private def parse(version: CypherVersion, cypher: String): Statement = {
    AstParserFactory(version)
      .apply(preParser.preParse(cypher).statement, Neo4jCypherExceptionFactory(cypher, None), None)
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
      case lel: Leaf if lel.containsIs => lel.copy(containsIs = false)
    })
  }

  override protected def havingExecuted(cypher: String): Unit = roundTripCheck(cypher)
  override protected def executingQuery(cypher: String): Unit = roundTripCheck(cypher)
  override protected def executingControlQuery(cypher: String): Unit = roundTripCheck(cypher)

  override protected def parametersAre(params: Map[String, String]): Unit = {}
  override protected def registerProcedure(signature: String, results: DataTable): Unit = {}
  override protected def givenCsvFile(urlParam: String, content: DataTable): Unit = {}
  override protected def resultShouldBeInAnyOrder(expected: DataTable): Unit = {}
  override protected def resultShouldBeInOrder(expected: DataTable): Unit = {}
  override protected def resultShouldBeInOrderIgnoringListOrder(expected: DataTable): Unit = {}
  override protected def resultShouldBeInAnyOrderIgnoringListOrder(expected: DataTable): Unit = {}
  override protected def sideEffectsShouldBe(expected: DataTable): Unit = {}
  override protected def errorShouldBeRaised(expected: ExpectedError): Unit = {}
}

object PrettifierSteps {
  val preParser = new PreParser(CypherConfiguration.fromConfig(Config.defaults()))

  val prettifier: Prettifier = Prettifier(ExpressionStringifier(
    alwaysParens = true,
    alwaysBacktick = true,
    sensitiveParamsAsParams = true
  ))

  final val ObjectFactoryName = "org.neo4j.cypher.cucumber.glue.prettifier.PrettifierSteps$ObjectFactory"
  class ObjectFactory extends GuiceObjectFactory(GuiceObjectFactory.injector(new NoOpBeforeAndAfterAllModule()))

  def expectFailure(scenario: Scenario): Boolean = scenariosExpectedToFail.get(scenario.getName) match {
    case Some(paths) => paths.contains(scenario.getUri.getSchemeSpecificPart)
    case None        => false
  }

  private val scenariosExpectedToFail: Map[String, Seq[String]] = Seq(
    "acceptance/features/GpmSyntaxMixingAcceptance.feature" -> "DIFFERENT NODES with var-length relationship - OK",
    "acceptance/features/GpmSyntaxMixingAcceptance.feature" -> "Mixing QPP and var-length relationship quantifiers in pattern expressions in same statement - syntax error",
    "acceptance/features/GpmSyntaxMixingAcceptance.feature" -> "Explicit match mode DIFFERENT NODES with shortestPath - syntax error",
    "acceptance/features/GpmSyntaxMixingAcceptance.feature" -> "Explicit match mode DIFFERENT NODES with allShortestPaths - syntax error",
    "acceptance/features/QuantifiedPathPatternAcceptance.feature" -> "Quantifier {-1} lower bound must be less than or equal to upper bound, upper bound needs to be positive"
  ).groupMap { case (_, name) => name } { case (path, _) => path }
}
