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
 *  - containing only whitespace, '^' and '-' characters
 *
 * Any line that does not satisfy these rules is treated as normal input and
 * remains part of the cleaned input.
 *
 * Markers on a caret line:
 *  - Simple position: '^'
 *  - Range position:  '^---' (one '^' followed by one or more uninterrupted '-' characters)
 *    The range length is 1 + number of following '-' characters.
 *
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
 *  - For ranges, (column - 1) + length must not exceed the length of the referenced line content.
 *
 * Returns:
 *  - cleanInput: the input string with all caret lines removed, preserving original line breaks
 *  - positions:  all extracted InputPositionFromCaret, ordered by increasing offset
 */
case class CaretPosition(input: String) {
  final private val caret = '^'
  final private val hyphen = '-'

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
      content.nonEmpty &&
        content.forall(ch => ch.isWhitespace || ch == caret || ch == hyphen) &&
        content.exists(_ == caret)

    /**
     * Extract markers from this caret line in left-to-right order.
     * Each marker starts at a '^' and may be followed by uninterrupted '-' to form a range.
     * Returns (caretIndex0Based, lengthInChars)
     */
    lazy val markers: Seq[(Int, Int)] = {
      if (!isCaretLine) Seq.empty
      else {
        val buf = Vector.newBuilder[(Int, Int)]
        var i = 0

        while (i < content.length) {
          if (content.charAt(i) == caret) {
            var j = i + 1
            while (j < content.length && content.charAt(j) == hyphen) j += 1
            val len = 1 + (j - (i + 1)) // caret + uninterrupted hyphens
            buf += ((i, len))
          }
          i += 1
        }
        buf.result()
      }
    }

    lazy val caretIndexes: Seq[Int] = markers.map(_._1)
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

      // If the input ends right after a terminator, preserve the trailing empty line.
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

  private def rangeTooLong(line: Segment, caretIndex: Int, length: Int): Nothing = {
    val column = caretIndex + 1
    throw new IllegalArgumentException(
      s"Range starting at column $column with length $length exceeds length of preceding line (${line.content.length})."
    )
  }

  val (cleanInput: String, positions: Seq[InputPositionFromCaret]) = {
    val segments: Vector[Segment] = splitIntoPrefixedLines(input) :+ Segment("", "")

    sealed trait SegmentPair
    object Head extends SegmentPair
    object Tail extends SegmentPair

    val (_, cleanInput: Vector[Segment], _, positions: Seq[InputPositionFromCaret]) = segments match {
      case Seq() =>
        (false, Vector.empty[Segment], 0, Seq.empty[InputPositionFromCaret])

      case Seq(CaretLine(_)) =>
        caretInFirstLine()

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
            // collect positions (simple + ranges)
            val newPositions: Seq[InputPositionFromCaret] = {
              // First, basic caret column validation (start column must be within line)
              if (caretLine.caretIndexes.exists(_ >= line.content.length))
                caretTooFarRight(line, caretLine)
              else {
                caretLine.markers.map {
                  case (caretIndex, length) =>
                    // Then validate range length if applicable
                    if (caretIndex + length > line.content.length) rangeTooLong(line, caretIndex, length)

                    val offset = prefixLen + line.prefix.length + caretIndex
                    val lineNo = cleanInput.size + 1
                    val columnNo = caretIndex + 1

                    if (length == 1) InputPositionFromCaret.Simple(offset, lineNo, columnNo)
                    else InputPositionFromCaret.Range(offset, lineNo, columnNo, length)
                }
              }
            }
            (Tail, cleanInput :+ line, prefixLen + line.fullLength, positions ++ newPositions)
          // catch all case to make the compiler happy
          case _ => throw new IllegalArgumentException(s"Internal error: unexpected match case")
        }
    }
    (cleanInput.map(_.full).mkString, positions)
  }
}

sealed trait InputPositionFromCaret {

  /** The offset in characters (not codepoints!) from the beginning of the query string */
  def offset: Int

  /** The line in the query string */
  def line: Int

  /** The column in the query string */
  def column: Int

  /** Length in characters in the query string. */
  def length: Option[Int]
}

object InputPositionFromCaret {

  case class Simple(offset: Int, line: Int, column: Int) extends InputPositionFromCaret {
    override def length: Option[Int] = None
  }

  case class Range(offset: Int, line: Int, column: Int, inputLength: Int) extends InputPositionFromCaret {
    override def length: Option[Int] = Some(inputLength)
  }

  def apply(offset: Int, line: Int, column: Int): InputPositionFromCaret = Simple(offset, line, column)

  def withLength(offset: Int, line: Int, column: Int, length: Int): InputPositionFromCaret =
    Range(offset, line, column, length)
}
