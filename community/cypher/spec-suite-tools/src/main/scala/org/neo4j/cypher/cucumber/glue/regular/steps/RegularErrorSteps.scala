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

import io.cucumber.datatable.DataTable
import org.junit.jupiter.api.Assertions.fail
import org.neo4j.cypher.cucumber.glue.regular.TestConf
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.GqlFailure
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.QueryExecution
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.QueryFailure
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.QueryResults
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.doDescribeFailure
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.originalError
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularCypherSteps.unexpectedSuccess
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularErrorSteps.describeErrorCodes
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularErrorSteps.errorHierarchy
import org.neo4j.cypher.cucumber.glue.regular.steps.RegularErrorSteps.errorsMatch
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps
import org.neo4j.cypher.cucumber.steps.CypherCucumberSteps.ExpectedGqlError
import org.neo4j.gqlstatus.ErrorGqlStatusObject

import java.util.regex.Pattern

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.matching.Regex

trait RegularErrorSteps { this: CypherCucumberSteps =>
  def lastExecutionResult: QueryExecution
  def conf: TestConf

  final override def errorShouldBeRaised(expected: ExpectedGqlError): Unit = lastExecutionResult match {
    case result: QueryResults => unexpectedSuccess(result, conf)
    case failure: QueryFailure =>
      val actual = errorHierarchy(originalError(failure.cause))
      if (!errorsMatch(actual, expected)) {
        fail(
          s"""Actual errors:
             >${describeErrorCodes(actual)}
             >
             >Did not match expected errors:
             >${expected.table}
             >
             >${doDescribeFailure(failure)}
             >""".stripMargin('>')
        )
      }
  }
}

object RegularErrorSteps {

  private val InlineRegexPattern = "\\$\\{regex:(?<exp>[^}]+?)\\}".r

  @tailrec
  def errorHierarchy(throwable: AnyRef, acc: Seq[GqlFailure] = Seq.empty): Seq[GqlFailure] = throwable match {
    case e: org.neo4j.driver.exceptions.Neo4jException => errorHierarchy(
        e.gqlCause().orElse(null),
        acc.appended(GqlFailure(
          code = e.gqlStatus(),
          classification = e.classification().map[String](_.toString).orElse("UNKNOWN"),
          description = e.statusDescription(),
          message = e.getMessage
        ))
      )
    case o: ErrorGqlStatusObject => errorHierarchy(
        o.cause().orElse(null),
        acc.appended(GqlFailure(
          code = o.gqlStatus(),
          classification = o.getClassification.toString,
          description = o.statusDescription(),
          message = o.getMessage
        ))
      )
    case _ => acc
  }

  private def errorsMatch(actual: Seq[GqlFailure], expected: ExpectedGqlError): Boolean = expected match {
    case ExpectedGqlError(_, expectedErrors) =>
      actual.size == expectedErrors.size &&
      actual.zip(expectedErrors).forall { case (actual, expected) =>
        val codeMatches = expected.code.contains(actual.code)
        val classificationMatches = expected.classification.contains(actual.classification)
        val descriptionMatches = actual.description == expected.descriptionTemplate ||
          buildRegexFromTemplate(expected.descriptionTemplate).matches(actual.description)
        codeMatches && classificationMatches && descriptionMatches
      }
  }

  // Template can be a string like: "It was the year ${regex:\d\d\d\d}."
  private def buildRegexFromTemplate(template: String): Regex = {
    val regex = new StringBuilder(template.length)
    var lastIndex = 0
    for (matchResult <- InlineRegexPattern.findAllMatchIn(template)) {
      val literalPart = template.substring(lastIndex, matchResult.start)
      if (literalPart.nonEmpty) {
        regex.append(Pattern.quote(literalPart))
      }
      regex.append(matchResult.group("exp"))
      lastIndex = matchResult.end
    }
    if (lastIndex < template.length) {
      regex.append(Pattern.quote(template.substring(lastIndex)))
    }
    regex.result().r
  }

  def describeErrorCodes(failures: Seq[GqlFailure]): String = DataTable.create(
    failures
      .map(e => java.util.List.of(e.code, e.classification, e.description))
      .prepended(java.util.List.of("code", "classification", "description"))
      .asJava
  ).toString
}
