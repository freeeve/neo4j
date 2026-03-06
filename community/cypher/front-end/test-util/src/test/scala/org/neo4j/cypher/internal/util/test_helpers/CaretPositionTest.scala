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

import org.scalactic.source
import org.scalatest.Tag

class CaretPositionTest extends CypherFunSuite with TestName {

  private val startMarker = '<'
  private val endMarker = '>'

  private def hasCorrectPositionIngoringLineTerminationCharactersInOffset(
    clean: String,
    positions: InputPositionFromCaret*
  ): Unit = {
    // note that the test name gets trimmed by scalatest
    // so we added non-whitespace around it and have to remove that non-whitespace here again
    val orgTestName = testName.stripPrefix(startMarker.toString).stripSuffix(endMarker.toString)

    val caretPosition = CaretPosition(orgTestName)

    withClue("clean text") {
      caretPosition.cleanInput shouldEqual clean
    }
    withClue("positions") {
      withClue("number") {
        caretPosition.positions.size shouldEqual positions.size
      }
      // adjust position offset for length of line termination characters
      val ltPrefixSums = lineTerminationPrefixSums(caretPosition.cleanInput)
      caretPosition.positions.zip(positions).zipWithIndex.foreach {
        case ((actual, expected), i) =>
          withClue(s"position ${i + 1} (1-based counting)") {
            removeLineTerminationFromOffset(actual, ltPrefixSums) shouldEqual expected
          }
      }
    }
  }

  override def test(testName: String, testTags: Tag*)(testFun: => Any /* Assertion */ )(implicit
    pos: source.Position): Unit = {
    // note that the test name gets trimmed by scalatest so we add non-whitespace around it
    val preventLtrim =
      if (testName.headOption.exists(c => c.isWhitespace || c == startMarker)) startMarker + testName else testName
    val preventRtrim = if (preventLtrim.lastOption.exists(c => c.isWhitespace || c == endMarker))
      preventLtrim + endMarker
    else preventLtrim
    super.test(preventRtrim, testTags: _*)(testFun)
  }

  def lineTerminationPrefixSums(s: String): IndexedSeq[Int] = {
    val buf = scala.collection.mutable.ArrayBuffer.empty[Int]

    var i = 0
    var cumulative = 0
    buf += cumulative // for the first line

    while (i < s.length) {
      s.charAt(i) match {
        case '\r' =>
          if (i + 1 < s.length && s.charAt(i + 1) == '\n') {
            cumulative += 2
            i += 1 // skip '\n'
          } else {
            cumulative += 1
          }
          buf += cumulative

        case '\n' =>
          cumulative += 1
          buf += cumulative

        case _ => ()
      }

      i += 1
    }

    buf.toIndexedSeq
  }

  def removeLineTerminationFromOffset(
    pos: InputPositionFromCaret,
    lineTerminationPrefixSums: IndexedSeq[Int]
  ): InputPositionFromCaret = {
    val ltLength = lineTerminationPrefixSums(pos.line - 1)
    InputPositionFromCaret(pos.offset - ltLength, pos.line, pos.column)
  }

  test(""" 0 """.stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """ 0 """.stripMargin
    )
  }

  test("""000""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """000""".stripMargin
    )
  }

  test("""001
         | ^""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """001""".stripMargin,
      InputPositionFromCaret(1, 1, 2)
    )
  }

  test("""001
         |^^^""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """001""".stripMargin,
      InputPositionFromCaret(0, 1, 1),
      InputPositionFromCaret(1, 1, 2),
      InputPositionFromCaret(2, 1, 3)
    )
  }

  test("""002
         |^^^
         |""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """002
        |""".stripMargin,
      InputPositionFromCaret(0, 1, 1),
      InputPositionFromCaret(1, 1, 2),
      InputPositionFromCaret(2, 1, 3)
    )
  }

  test("""000
         |abc
         |def
         |""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """000
        |abc
        |def
        |""".stripMargin
    )
  }

  test("""001
         |^
         |abc
         |def
         |""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """001
        |abc
        |def
        |""".stripMargin,
      InputPositionFromCaret(0, 1, 1)
    )
  }

  test("""002
         |abc
         |^
         |def
         |""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """002
        |abc
        |def
        |""".stripMargin,
      InputPositionFromCaret(3, 2, 1)
    )
  }

  test("""003
         |  ^
         |abc
         |def
         |""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """003
        |abc
        |def
        |""".stripMargin,
      InputPositionFromCaret(2, 1, 3)
    )
  }

  test("""003
         |abc
         |  ^
         |def
         |""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """003
        |abc
        |def
        |""".stripMargin,
      InputPositionFromCaret(5, 2, 3)
    )
  }

  test("""004
         |abc
         |def
         |  ^
         |""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """004
        |abc
        |def
        |""".stripMargin,
      InputPositionFromCaret(8, 3, 3)
    )
  }

  test("""005
         |^
         |abc
         |def
         |  ^
         |""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """005
        |abc
        |def
        |""".stripMargin,
      InputPositionFromCaret(0, 1, 1),
      InputPositionFromCaret(8, 3, 3)
    )
  }

  test("""006
         |abc
         |def
         |^^^
         |""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """006
        |abc
        |def
        |""".stripMargin,
      InputPositionFromCaret(6, 3, 1),
      InputPositionFromCaret(7, 3, 2),
      InputPositionFromCaret(8, 3, 3)
    )
  }

  test("""0
         |abc
         |def
         |ghi
         |  ^
         |jmn
         |""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """0
        |abc
        |def
        |ghi
        |jmn
        |""".stripMargin,
      InputPositionFromCaret(9, 4, 3)
    )
  }

  test("""1
         |abc
         |def
         | ^
         |ghi
         | ^^
         |jmn
         |^
         |""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """1
        |abc
        |def
        |ghi
        |jmn
        |""".stripMargin,
      InputPositionFromCaret(5, 3, 2),
      InputPositionFromCaret(8, 4, 2),
      InputPositionFromCaret(9, 4, 3),
      InputPositionFromCaret(10, 5, 1)
    )
  }

  test("""DEFINE PROCEDURE foo.a() :: (a :: INT) {
         |                             ^
         |  RETURN 1.0 AS a
         |                ^
         |}
         |
         |CALL foo.a() YIELD a AS x
         |RETURN x
         |       ^
         |""".stripMargin) {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      """DEFINE PROCEDURE foo.a() :: (a :: INT) {
        |  RETURN 1.0 AS a
        |}
        |
        |CALL foo.a() YIELD a AS x
        |RETURN x
        |""".stripMargin,
      InputPositionFromCaret(29, 1, 30),
      InputPositionFromCaret(56, 2, 17),
      InputPositionFromCaret(90, 6, 8)
    )
  }

  test("007\nabc\r\ndef\r^^^\n") {
    hasCorrectPositionIngoringLineTerminationCharactersInOffset(
      "007\nabc\r\ndef\n",
      InputPositionFromCaret(6, 3, 1),
      InputPositionFromCaret(7, 3, 2),
      InputPositionFromCaret(8, 3, 3)
    )
  }

  test("throws if caret line is the first line") {
    val in =
      s"""^
         |one
         |""".stripMargin

    val ex = intercept[IllegalArgumentException] {
      CaretPosition(in)
    }
    ex.getMessage should include("Caret line cannot be the first line")
  }

  test("throws if a caret line follows another caret line") {
    val in =
      s"""one
         | ^
         |  ^
         |two
         |""".stripMargin

    val ex = intercept[IllegalArgumentException] {
      CaretPosition(in)
    }
    ex.getMessage should include("cannot point to another caret line")
  }

  test("a line containing '^' but also other characters is not a caret line and remains in clean input") {
    val in =
      s"""one
         |  x^
         |two
         |""".stripMargin

    val cp = CaretPosition(in)

    cp.cleanInput shouldEqual in
    cp.positions shouldBe empty
  }

  test("caret must be at a valid position in the preceding line (1)") {
    val in =
      s"""one
         |   ^
         |two
         |""".stripMargin

    val ex = intercept[IllegalArgumentException] {
      CaretPosition(in)
    }
    ex.getMessage should include("Caret column 4 exceeds length of preceding line (3).")
  }

  test("caret must be at a valid position in the preceding line (2)") {
    val in =
      s"""one
         |         ^
         |two
         |""".stripMargin

    val ex = intercept[IllegalArgumentException] {
      CaretPosition(in)
    }
    ex.getMessage should include("Caret column 10 exceeds length of preceding line (3).")
  }
}
