/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.util.test_helpers

import org.neo4j.gqlstatus.ErrorGqlStatusObject
import org.neo4j.gqlstatus.GqlStatusInfoCodes
import org.scalatest.matchers.BeMatcher
import org.scalatest.matchers.MatchResult
import org.scalatest.matchers.Matcher
import org.scalatest.matchers.should.Matchers.a
import org.scalatest.matchers.should.Matchers.be

import scala.jdk.OptionConverters.RichOptional

trait GqlExceptionMatchers {

  implicit val windowsSafe: WindowsStringSafe.type = WindowsStringSafe

  case class GqlExceptionMatcher(
    code: GqlStatusInfoCodes,
    statusDescription: String,
    causeMatcher: Option[GqlExceptionMatcher] = None
  ) extends BeMatcher[ErrorGqlStatusObject] {

    override def toString(): String = s"GqlExceptionMatcher for $code"

    override def apply(left: ErrorGqlStatusObject): MatchResult = {
      validationFailures(left)
        .getOrElse(validGqlStatusObject())
    }

    private def validationFailures(left: ErrorGqlStatusObject): Option[MatchResult] = {
      invalidCode(left)
        .orElse(invalidStatusDescription(left))
        .orElse(causeExistenceCheck(left))
    }

    private def invalidCode(left: ErrorGqlStatusObject): Option[MatchResult] = {
      if (left.gqlStatus() != code.getStatusString) {
        Some(MatchResult(
          matches = false,
          s"Expected GQL code ${code.getStatusString} but found ${left.gqlStatus()}",
          "Unreachable"
        ))
      } else {
        None
      }
    }

    private def invalidStatusDescription(left: ErrorGqlStatusObject): Option[MatchResult] = {
      if (left.statusDescription() != statusDescription) {
        Some(MatchResult(
          matches = false,
          s"""The status description for ${left.gqlStatus()}:
             |${left.statusDescription()}
             |was not equal to the expected:
             |$statusDescription
             |""".stripMargin,
          "Unreachable"
        ))
      } else {
        None
      }
    }

    private def causeExistenceCheck(left: ErrorGqlStatusObject): Option[MatchResult] = {
      causeMatcher
        .map(validateCause(left))
        .getOrElse(validateNoCause(left))
    }

    private def validateNoCause(left: ErrorGqlStatusObject): Option[MatchResult] =
      left.cause().toScala.map(_ => {
        MatchResult(
          matches = false,
          s"Expected ${left.gqlStatus()} to not have a cause but found ${left.cause().get().gqlStatus()}",
          "Unreachable"
        )
      })

    private def validateCause(left: ErrorGqlStatusObject)(causeMatcher: GqlExceptionMatcher): Option[MatchResult] = {
      def addContext(m: MatchResult): MatchResult = {
        def withContext(msg: String) =
          s"""the cause of ${left.gqlStatus()} had validation failures:
             |$msg""".stripMargin
        m.copy(
          rawFailureMessage = withContext(m.rawFailureMessage),
          rawMidSentenceFailureMessage = withContext(m.rawMidSentenceFailureMessage)
        )
      }
      left.cause().toScala.map(cause => {
        // Cause exists, run cause matcher against it
        causeMatcher.validationFailures(cause).map(addContext)
      })
        .getOrElse(
          // Cause doesn't exist, return failure
          Some(MatchResult(
            matches = false,
            s"Expected ${left.gqlStatus()} to have a cause",
            "Unreachable"
          ))
        )
    }

    private def validGqlStatusObject(): MatchResult = {
      MatchResult(
        matches = true,
        "Unreachable",
        "expected the GQL-status object to not be the provided gqlStatus, but all fields were the same"
      )
    }

    def withCause(causeMatcher: GqlExceptionMatcher): GqlExceptionMatcher = {
      /*
       Assume that cause matcher is not already set.
       This occurs when given the following pattern:
         exception should be(foo
                            .withCause(bar)
                            .withCause(baz)
                            )
       Test writer probably intended to do this:
         exception should be(foo
                              .withCause(bar
                                .withCause(baz)
                              )
                            )
       */
      assume(this.causeMatcher.isEmpty, "TEST SETUP ERROR: This would override an existing cause matcher.")
      this.copy(causeMatcher = Some(causeMatcher))
    }

    def withCause(code: GqlStatusInfoCodes, statusDescription: String): GqlExceptionMatcher = {
      withCause(GqlExceptionMatcher(code, statusDescription))
    }

  }

  def gqlStatus(code: GqlStatusInfoCodes, statusDescription: String): GqlExceptionMatcher = {
    GqlExceptionMatcher(code, statusDescription)
  }

  def gqlException(
    legacyMsg: String,
    gqlMatcher: GqlExceptionMatcher,
    fuzzyMsg: Boolean = false
  ): BeMatcher[Exception] =
    BeMatcher {
      (ex: Exception) =>
        {
          val typeMatcher: Matcher[Exception] = be(a[ErrorGqlStatusObject])
          val messageMatcher: Matcher[Exception] = {
            if (fuzzyMsg) {
              Matcher { ex =>
                MatchResult(
                  ex.getMessage.replace("\r\n", "\n").contains(legacyMsg.replace("\r\n", "\n")),
                  s"Message '${ex.getMessage}' did not start with '$legacyMsg'",
                  s"Message started with '$legacyMsg'"
                )
              }
            } else {
              Matcher { ex =>
                MatchResult(
                  ex.getMessage.equals(legacyMsg),
                  s"Message '${ex.getMessage}' did not equals '$legacyMsg'",
                  s"Message equals '$legacyMsg'"
                )
              }
            }
          }
          val exceptionMatchResult = (typeMatcher and messageMatcher)(ex)

          if (!exceptionMatchResult.matches) {
            exceptionMatchResult
          } else {
            val gqlMatchResult: MatchResult = gqlMatcher(ex.asInstanceOf[ErrorGqlStatusObject])
            MatchResult(
              gqlMatchResult.matches,
              gqlMatchResult.rawFailureMessage,
              "expected exception to not be the provided gqlException, but all fields were the same"
            )
          }
        }
    }
}

object GqlExceptionMatchers extends GqlExceptionMatchers {}
