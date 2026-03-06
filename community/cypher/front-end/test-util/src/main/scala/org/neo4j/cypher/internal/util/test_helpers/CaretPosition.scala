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

/**
 * Parses a string that may contain multiple caret lines and extracts all marked positions.
 *
 * A caret line is defined as:
 *  - containing at least one '^'
 *  - containing only whitespace or '^' characters
 *
 * Any line that does not satisfy these rules is treated as normal input and
 * remains part of the cleaned input.
 *
 * Each '^' character on a caret line defines one InputPositionFromCaret.
 * A caret line refers to the immediately preceding non-caret line.
 *
 * Position rules (relative to the cleaned input, i.e. with all caret lines removed):
 *  - line number (1-based) = line number of the preceding non-caret line
 *  - column number (1-based) = column of the '^' within the caret line
 *  - offset (0-based) = number of characters in cleanInput before the referenced column,
 *                       counting the *original* line break characters from the input
 *                       (i.e. "\n" counts as 1, "\r\n" counts as 2, "\r" counts as 1).
 *
 * Additional constraints:
 *  - A caret line cannot be the first line.
 *  - A caret line cannot follow another caret line.
 *  - The column of each '^' must not exceed the length of the referenced line content
 *    (line breaks are not part of a line's content).
 *
 * Returns:
 *  - cleanInput: the input string with all caret lines removed, preserving original line breaks
 *  - positions:  all extracted InputPositionFromCaret, ordered by increasing offset
 */
case class CaretPosition(input: String) {
  final private val caret = '^'

  /**
   * One logical line, represented as:
   *   prefix: the line terminator that precedes this line ("" for the first line)
   *   content: the line content without terminators (may be empty)
   *   isCaretLineContent: indicator that content is a caret line
   *   caretIndex: index of caret in content
   *
   * The original line terminators are preserved as part of `full`,
   * which makes offsets platform-independent and consistent with the input.
   */
  private case class Segment(prefix: String, content: String) {
    def full: String = prefix + content
    def fullLength: Int = prefix.length + content.length

    lazy val isCaretLine: Boolean =
      content.nonEmpty && content.forall(ch => ch.isWhitespace || ch == caret) && content.exists(_ == caret)
    lazy val caretIndexes: Seq[Int] = content.zipWithIndex.collect { case (c, i) if c == caret => i }
  }

  private object CleanLine {
    def unapply(seg: Segment): Option[Segment] = if (seg.isCaretLine) None else Some(seg)
  }

  private object CaretLine {
    def unapply(seg: Segment): Option[Segment] = if (seg.isCaretLine) Some(seg) else None
  }

  /**
   * Split into segments where each segment is either:
   *  - the first line's content (prefix = "")
   *  - or (line terminator + next line's content)
   *
   * This preserves:
   *  - original line terminators (\r\n, \n, \r)
   *  - a trailing empty line if the input ends in a terminator
   */
  private def splitIntoPrefixedLines(s: String): Vector[Segment] = {
    val out = Vector.newBuilder[Segment]
    val n = s.length

    var i = 0
    var prefix = "" // no terminator before the first line

    // Special-case empty string: one empty line
    if (n == 0) {
      out += Segment("", "")
      return out.result()
    }

    while (i <= n) {
      // read content until line break or end
      val start = i
      while (
        i < n && {
          val ch = s.charAt(i)
          ch != '\n' && ch != '\r'
        }
      ) i += 1

      val content = s.substring(start, i)
      out += Segment(prefix, content)

      if (i >= n) {
        // end of input, done
        return out.result()
      }

      // read line terminator which becomes the prefix for the NEXT segment
      val ch = s.charAt(i)
      if (ch == '\r') {
        if (i + 1 < n && s.charAt(i + 1) == '\n') {
          prefix = "\r\n"
          i += 2
        } else {
          prefix = "\r"
          i += 1
        }
      } else { // '\n'
        prefix = "\n"
        i += 1
      }

      // If the input ends right after a terminator, we must preserve the trailing empty line.
      if (i == n) {
        out += Segment(prefix, "")
        return out.result()
      }
    }

    out.result()
  }

  private def caretInFirstLine(): Nothing =
    throw new IllegalArgumentException("Caret line cannot be the first line (needs a line before it).")

  private def caretAfterCaretLine(): Nothing =
    throw new IllegalArgumentException("Caret line must follow a non-caret line (cannot point to another caret line).")

  private def caretTooFarRight(line: Segment, caretLine: Segment): Nothing =
    throw new IllegalArgumentException(
      s"Caret column ${caretLine.caretIndexes.find(_ >= line.content.length).get + 1} exceeds length of preceding line (${line.content.length})."
    )

  val (cleanInput: String, positions: Seq[InputPositionFromCaret]) = {
    val segments: Vector[Segment] = splitIntoPrefixedLines(input) :+ Segment("", "")

    sealed trait SegmentPair
    object Head extends SegmentPair
    object Tail extends SegmentPair

    val (_, cleanInput: Vector[Segment], _, positions: Seq[InputPositionFromCaret]) = segments match {
      case Seq() =>
        (false, Vector.empty[Segment], 0, Seq.empty[InputPositionFromCaret])
      case Seq(CaretLine(_)) => caretInFirstLine()
      case Seq(CleanLine(line)) =>
        (false, Vector(line), 0, Seq.empty[InputPositionFromCaret])
      case _ =>
        segments.sliding(2).foldLeft((
          Head.asInstanceOf[SegmentPair],
          Vector.empty[Segment],
          0,
          Seq.empty[InputPositionFromCaret]
        )) {
          // error cases
          case ((Head, _, _, _), Seq(CaretLine(_), _))         => caretInFirstLine()
          case ((_, _, _, _), Seq(CaretLine(_), CaretLine(_))) => caretAfterCaretLine()
          // regular cases
          case ((_, cleanInput, prefixLen, positions), Seq(CaretLine(_), CleanLine(_))) =>
            (Tail, cleanInput, prefixLen, positions) // just skip
          case ((_, cleanInput, prefixLen, positions), Seq(CleanLine(line), CleanLine(_))) =>
            (Tail, cleanInput :+ line, prefixLen + line.fullLength, positions) // accumulate line
          case ((_, cleanInput, prefixLen, positions), Seq(CleanLine(line), CaretLine(caretLine))) =>
            // collect positions
            val position = {
              if (caretLine.caretIndexes.exists(_ >= line.content.length))
                caretTooFarRight(line, caretLine)
              else {
                caretLine.caretIndexes.map { caretIndex =>
                  val offset = prefixLen + line.prefix.length + caretIndex
                  val lineNo = cleanInput.size + 1
                  val columnNo = caretIndex + 1
                  InputPositionFromCaret(offset, lineNo, columnNo)
                }
              }
            }
            (Tail, cleanInput :+ line, prefixLen + line.fullLength, positions ++ position)
          // catch all case to make the compiler happy
          case _ => throw new IllegalArgumentException(s"Internal error: unexpected match case")
        }
    }
    (cleanInput.map(_.full).mkString, positions)
  }
}

case class InputPositionFromCaret(offset: Int, line: Int, column: Int)
