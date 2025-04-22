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

import GqlExceptionMatchers.GqlExceptionMatcher
import GqlExceptionMatchers.gqlException
import GqlExceptionMatchers.gqlStatus
import org.neo4j.gqlstatus.ErrorGqlStatusObject
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation
import org.neo4j.gqlstatus.GqlException
import org.neo4j.gqlstatus.GqlParams
import org.neo4j.gqlstatus.GqlStatusInfoCodes
import org.scalatest.exceptions.TestFailedException

class GqlExceptionMatcherTest extends CypherFunSuite {

  /*
   Changing this to true disables catching of "TestFailedException".
   This is useful for seeing the underlying exception that would be thrown in a real test.
   */
  private val RUN_TESTS_DIRECTLY = false

  private def expectFailure(f: => Unit): Unit = {
    if (RUN_TESTS_DIRECTLY) f
    else a[TestFailedException] should be thrownBy f
  }

  private def expectSuccess(f: => Unit): Unit = {
    if (RUN_TESTS_DIRECTLY) f
    else noException should be thrownBy f
  }

  private val homeDatabaseDoesNotExistStatus = GqlExceptionMatcher(
    GqlStatusInfoCodes.STATUS_00N50,
    "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
  )

  private val privilegeAlreadyAssignedStatus = GqlExceptionMatcher(
    GqlStatusInfoCodes.STATUS_00N70,
    "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
  )

  private val invalidPowerFuncStatus = GqlExceptionMatcher(
    GqlStatusInfoCodes.STATUS_2201F,
    "error: data exception - invalid argument for power function. The value 42 is an invalid argument for the specified power function."
  )

  private case class MyException(gql: ErrorGqlStatusObject, message: String) extends GqlException(gql, message)

  test("should pass test on correct code and status description - gqlStatusObject") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should be(gqlStatus(
        GqlStatusInfoCodes.STATUS_00N50,
        "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
      ))
    }
  }

  test("should pass test on correct message, code and status description - exception") {
    expectSuccess {
      val exception = the[Exception] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "oops")
      }
      exception should be(gqlException(
        "oops",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
      ))
    }
  }

  test("should not pass on incorrect code - gqlStatusObject") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_22000) // Incorrect code
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should be(gqlStatus(
        GqlStatusInfoCodes.STATUS_00N50,
        "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
      ))
    }
  }

  test("should not pass on incorrect code - exception") {
    expectFailure {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_22000) // Incorrect code
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "oops")
      }
      exception should be(gqlException(
        "oops",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
      ))
    }
  }

  test("should not pass on incorrect status description - gqlStatusObject") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "incorrect!") // Incorrect parameter / status description
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should be(gqlStatus(
        GqlStatusInfoCodes.STATUS_00N50,
        "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
      ))
    }
  }

  test("should not pass on incorrect status description - exception") {
    expectFailure {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "incorrect!") // Incorrect parameter / status description
          .build()
        throw MyException(gql, "oops")
      }
      exception should be(gqlException(
        "oops",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
      ))
    }
  }

  test("should not pass on incorrect message - exception") {
    expectFailure {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "failure") // wrong message
      }
      exception should be(gqlException(
        "oops",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
      ))
    }
  }

  test("should not pass on partial message without fuzzy") {
    expectFailure {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "my scary failure")
      }
      exception should be(gqlException(
        "scary",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
      ))
    }
  }

  test("should pass on partial message with fuzzy") {
    expectSuccess {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "my scary failure")
      }
      exception should be(gqlException(
        "scary",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        ),
        fuzzyMsg = true
      ))
    }
  }

  test("should not pass on partial status description without fuzzy") {
    expectFailure {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "my scary failure")
      }
      exception should be(gqlException(
        "my scary failure",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database "
        )
      ))
    }
  }

  test("should pass on partial status description with fuzzy") {
    expectSuccess {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "my scary failure")
      }
      exception should be(gqlException(
        "my scary failure",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database ",
          fuzzyStatusDescr = true
        )
      ))
    }
  }

  test("should not pass on incorrect exception type") {
    expectFailure {
      val exception = the[RuntimeException] thrownBy {
        throw new RuntimeException("oops") // no GQLSTATUS
      }

      exception should be(gqlException(
        "oops",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
      ))
    }
  }

  test("should pass on gql with correct cause - gqlStatusObject") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should
        be(gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
          .withCause(
            GqlStatusInfoCodes.STATUS_00N70,
            "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
          ))
    }
  }

  test("should pass on gql with correct cause - exception") {
    expectSuccess {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }
      exception should
        be(gqlException(
          "oops",
          gqlStatus(
            GqlStatusInfoCodes.STATUS_00N50,
            "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
          )
            .withCause(
              GqlStatusInfoCodes.STATUS_00N70,
              "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
            )
        ))
    }
  }

  test("should fail on gql with cause with incorrect code - exception") {
    expectFailure {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_22000) // Incorrect code
              .withParam(GqlParams.StringParam.cmd, "bar")
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }
      exception should
        be(gqlException(
          "oops",
          gqlStatus(
            GqlStatusInfoCodes.STATUS_00N50,
            "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
          )
            .withCause(
              GqlStatusInfoCodes.STATUS_00N70,
              "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
            )
        ))
    }
  }

  test("should fail on gql with cause with incorrect status description - gqlStatusObject") {
    expectFailure {

      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "incorrect!") // Incorrect parameter / status description
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should
        be(gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
          .withCause(
            GqlStatusInfoCodes.STATUS_00N70,
            "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
          ))
    }
  }

  test("should fail on gql with cause with incorrect status description - exception") {
    expectFailure {

      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "incorrect!") // Incorrect parameter / status description
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }
      exception should
        be(gqlException(
          "oops",
          gqlStatus(
            GqlStatusInfoCodes.STATUS_00N50,
            "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
          )
            .withCause(
              GqlStatusInfoCodes.STATUS_00N70,
              "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
            )
        ))
    }
  }

  test("should fail on gql with missing cause") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          // cause missing entirely
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should
        be(gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
          .withCause(
            GqlStatusInfoCodes.STATUS_00N70,
            "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
          ))
    }
  }

  test("should fail on gql with unexpected cause") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should
        be(gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        ))
      // cause 00N70 is not expected
    }
  }

  test("should pass on gql with correct cause cause") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .withCause(
                ErrorGqlStatusObjectImplementation
                  .from(GqlStatusInfoCodes.STATUS_2201F)
                  .withParam(GqlParams.NumberParam.value, 42)
                  .build()
              )
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }

      // Explicitly declaring gqlStatus inline
      gqlStatusObject should be(
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        ).withCause(
          gqlStatus(
            GqlStatusInfoCodes.STATUS_00N70,
            "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
          )
            .withCause(
              GqlStatusInfoCodes.STATUS_2201F,
              "error: data exception - invalid argument for power function. The value 42 is an invalid argument for the specified power function."
            )
        )
      )

      // Alternative syntax using helpers and infix notation (This might go away in scala 3):
      gqlStatusObject should be(
        homeDatabaseDoesNotExistStatus
          withCause (privilegeAlreadyAssignedStatus
            withCause invalidPowerFuncStatus)
      )
      // Short form without infix
      gqlStatusObject should be(
        homeDatabaseDoesNotExistStatus
          .withCause(
            privilegeAlreadyAssignedStatus
              .withCause(invalidPowerFuncStatus)
          )
      )
    }
  }

  test("should fail on gql with cause cause with incorrect status description") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .withCause(
                ErrorGqlStatusObjectImplementation
                  .from(GqlStatusInfoCodes.STATUS_2201F)
                  .withParam(GqlParams.NumberParam.value, -100) // Incorrect parameter / status description
                  .build()
              )
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should be(
        homeDatabaseDoesNotExistStatus withCause (privilegeAlreadyAssignedStatus withCause invalidPowerFuncStatus)
      )
    }
  }

  test("should fail on gql with cause cause with incorrect code") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .withCause(
                ErrorGqlStatusObjectImplementation
                  .from(GqlStatusInfoCodes.STATUS_2201E) // Incorrect code
                  .withParam(GqlParams.NumberParam.value, 42)
                  .build()
              )
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should be(
        homeDatabaseDoesNotExistStatus withCause
          (privilegeAlreadyAssignedStatus withCause
            invalidPowerFuncStatus)
      )
    }
  }

  test("should fail on gql with missing cause cause") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              // Cause-cause (invalidPowerFuncStatus) missing entirely
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should be(
        homeDatabaseDoesNotExistStatus withCause
          (privilegeAlreadyAssignedStatus withCause
            invalidPowerFuncStatus)
      )
    }
  }

  test("should fail on gql with unexpected cause cause") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .withCause(
                ErrorGqlStatusObjectImplementation
                  .from(GqlStatusInfoCodes.STATUS_2201F)
                  .withParam(GqlParams.NumberParam.value, 42)
                  .build()
              )
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should be(
        homeDatabaseDoesNotExistStatus withCause
          privilegeAlreadyAssignedStatus // not expecting the cause-cause
      )
    }
  }

  test("should not accept incorrectly written test") {
    a[AssertionError] should be thrownBy {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .withCause(
                ErrorGqlStatusObjectImplementation
                  .from(GqlStatusInfoCodes.STATUS_2201F)
                  .withParam(GqlParams.NumberParam.value, 42)
                  .build()
              )
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should be(
        homeDatabaseDoesNotExistStatus
          withCause privilegeAlreadyAssignedStatus
          // This would overwrite privilegeAlreadyAssignedStatus, instead of chaining
          withCause invalidPowerFuncStatus
      )
    }
  }

  // Negation tests

  test("should not pass negation test on correct code and status description - gqlStatusObject") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should not be gqlStatus(
        GqlStatusInfoCodes.STATUS_00N50,
        "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
      )
    }
  }

  test("should not pass negation test on correct message, code and status description - exception") {
    expectFailure {
      val exception = the[Exception] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "oops")
      }
      exception should not be gqlException(
        "oops",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
      )
    }
  }

  test("should pass negation on incorrect code - gqlStatusObject") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_22000) // Incorrect code
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should not be gqlStatus(
        GqlStatusInfoCodes.STATUS_00N50,
        "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
      )
    }
  }

  test("should pass negation on incorrect code - exception") {
    expectSuccess {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_22000) // Incorrect code
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "oops")
      }
      exception should not be gqlException(
        "oops",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
      )
    }
  }

  test("should pass negation on incorrect status description - gqlStatusObject") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "incorrect!") // Incorrect parameter / status description
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should not be gqlStatus(
        GqlStatusInfoCodes.STATUS_00N50,
        "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
      )
    }
  }

  test("should pass negation on incorrect status description - exception") {
    expectSuccess {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "incorrect!") // Incorrect parameter / status description
          .build()
        throw MyException(gql, "oops")
      }
      exception should not be gqlException(
        "oops",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
      )
    }
  }

  test("should pass negation on incorrect message - exception") {
    expectSuccess {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .build()
        throw MyException(gql, "failure") // wrong message
      }
      exception should not be gqlException(
        "oops",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
      )
    }
  }

  test("should pass negation on incorrect exception type") {
    expectSuccess {
      val exception = the[RuntimeException] thrownBy {
        throw new RuntimeException("oops") // no GQLSTATUS
      }

      exception should not be gqlException(
        "oops",
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
      )
    }
  }

  test("should not pass negation on gql with correct cause - gqlStatusObject") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should not be
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
          .withCause(
            GqlStatusInfoCodes.STATUS_00N70,
            "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
          )
    }
  }

  test("should not pass negation on gql with correct cause - exception") {
    expectFailure {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }
      exception should not be
        gqlException(
          "oops",
          gqlStatus(
            GqlStatusInfoCodes.STATUS_00N50,
            "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
          )
            .withCause(
              GqlStatusInfoCodes.STATUS_00N70,
              "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
            )
        )
    }
  }

  test("should pass negation on gql with cause with incorrect code - exception") {
    expectSuccess {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_22000) // Incorrect code
              .withParam(GqlParams.StringParam.cmd, "bar")
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }
      exception should not be
        gqlException(
          "oops",
          gqlStatus(
            GqlStatusInfoCodes.STATUS_00N50,
            "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
          )
            .withCause(
              GqlStatusInfoCodes.STATUS_00N70,
              "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
            )
        )
    }
  }

  test("should pass negation on gql with cause with incorrect status description - gqlStatusObject") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "incorrect!") // Incorrect parameter / status description
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should not be
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
          .withCause(
            GqlStatusInfoCodes.STATUS_00N70,
            "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
          )
    }
  }

  test("should pass negation on gql with cause with incorrect status description - exception") {
    expectSuccess {
      val exception = the[MyException] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "incorrect!") // Incorrect parameter / status description
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }
      exception should not be
        gqlException(
          "oops",
          gqlStatus(
            GqlStatusInfoCodes.STATUS_00N50,
            "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
          )
            .withCause(
              GqlStatusInfoCodes.STATUS_00N70,
              "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
            )
        )
    }
  }

  test("should pass negation on gql with missing cause") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          // cause missing entirely
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should not be
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
          .withCause(
            GqlStatusInfoCodes.STATUS_00N70,
            "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
          )
    }
  }

  test("should pass negation on gql with unexpected cause") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }
      gqlStatusObject should not be
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        )
      // cause 00N70 is not expected
    }
  }

  test("should fail negation on gql with correct cause cause") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .withCause(
                ErrorGqlStatusObjectImplementation
                  .from(GqlStatusInfoCodes.STATUS_2201F)
                  .withParam(GqlParams.NumberParam.value, 42)
                  .build()
              )
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }

      // Explicitly declaring gqlStatus inline
      gqlStatusObject should not be
        gqlStatus(
          GqlStatusInfoCodes.STATUS_00N50,
          "note: successful completion - home database does not exist. The database `foo` does not exist. Verify that the spelling is correct or create the database for the command to take effect."
        ).withCause(
          gqlStatus(
            GqlStatusInfoCodes.STATUS_00N70,
            "note: successful completion - role or privilege already assigned. The command 'bar' has no effect. The role or privilege is already assigned."
          )
            .withCause(
              GqlStatusInfoCodes.STATUS_2201F,
              "error: data exception - invalid argument for power function. The value 42 is an invalid argument for the specified power function."
            )
        )

      // Alternative syntax using helpers and infix notation (This might go away in scala 3):
      gqlStatusObject should be(
        homeDatabaseDoesNotExistStatus
          withCause (privilegeAlreadyAssignedStatus
            withCause invalidPowerFuncStatus)
      )
      // Short form without infix
      gqlStatusObject should be(
        homeDatabaseDoesNotExistStatus
          .withCause(
            privilegeAlreadyAssignedStatus
              .withCause(invalidPowerFuncStatus)
          )
      )
    }
  }

  test("should pass negation on gql with cause cause with incorrect status description") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .withCause(
                ErrorGqlStatusObjectImplementation
                  .from(GqlStatusInfoCodes.STATUS_2201F)
                  .withParam(GqlParams.NumberParam.value, -100) // Incorrect parameter / status description
                  .build()
              )
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should not be (
        homeDatabaseDoesNotExistStatus withCause (privilegeAlreadyAssignedStatus withCause invalidPowerFuncStatus)
      )
    }
  }

  test("should pass negation on gql with cause cause with incorrect code") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .withCause(
                ErrorGqlStatusObjectImplementation
                  .from(GqlStatusInfoCodes.STATUS_2201E) // Incorrect code
                  .withParam(GqlParams.NumberParam.value, 42)
                  .build()
              )
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should not be (
        homeDatabaseDoesNotExistStatus withCause
          (privilegeAlreadyAssignedStatus withCause
            invalidPowerFuncStatus)
      )
    }
  }

  test("should pass negation on gql with missing cause cause") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              // Cause-cause (invalidPowerFuncStatus) missing entirely
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should not be (
        homeDatabaseDoesNotExistStatus withCause
          (privilegeAlreadyAssignedStatus withCause
            invalidPowerFuncStatus)
      )
    }
  }

  test("should pass negation on gql with unexpected cause cause") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .withCause(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_00N70)
              .withParam(GqlParams.StringParam.cmd, "bar")
              .withCause(
                ErrorGqlStatusObjectImplementation
                  .from(GqlStatusInfoCodes.STATUS_2201F)
                  .withParam(GqlParams.NumberParam.value, 42)
                  .build()
              )
              .build()
          )
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should not be (
        homeDatabaseDoesNotExistStatus withCause
          privilegeAlreadyAssignedStatus // not expecting the cause-cause
      )
    }
  }

  test("should pass on correct position") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .atPosition(15, 2, 3)
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should be(
        homeDatabaseDoesNotExistStatus.withPosition(15, 2, 3)
      )
    }
  }

  test("should not pass negation on correct position") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .atPosition(15, 2, 3)
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should not be homeDatabaseDoesNotExistStatus.withPosition(15, 2, 3)
    }
  }

  test("should not pass on wrong offset") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .atPosition(14, 2, 3)
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should be(
        homeDatabaseDoesNotExistStatus.withPosition(15, 2, 3)
      )
    }
  }

  test("should pass negative on wrong offset") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .atPosition(14, 2, 3)
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should not be homeDatabaseDoesNotExistStatus.withPosition(15, 2, 3)
    }
  }

  test("should not pass on wrong line") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .atPosition(15, 3, 3)
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should be(
        homeDatabaseDoesNotExistStatus.withPosition(15, 2, 3)
      )
    }
  }

  test("should pass negative on wrong line") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .atPosition(15, 3, 3)
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should not be homeDatabaseDoesNotExistStatus.withPosition(15, 2, 3)
    }
  }

  test("should not pass on wrong column") {
    expectFailure {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .atPosition(15, 2, 5)
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should be(
        homeDatabaseDoesNotExistStatus.withPosition(15, 2, 3)
      )
    }
  }

  test("should pass negative on wrong column") {
    expectSuccess {
      val gqlStatusObject = the[ErrorGqlStatusObject] thrownBy {
        val gql = ErrorGqlStatusObjectImplementation
          .from(GqlStatusInfoCodes.STATUS_00N50)
          .withParam(GqlParams.StringParam.db, "foo")
          .atPosition(15, 2, 5)
          .build()
        throw MyException(gql, "oops")
      }

      gqlStatusObject should not be homeDatabaseDoesNotExistStatus.withPosition(15, 2, 3)
    }
  }
}
