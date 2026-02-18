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
package org.neo4j.cypher.internal.util.test_helpers.graphtemplate.parsing

import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

/**
 * Finds nodes described on a single line
 */
object InlineParsedNodeFinder extends ParsedNodeExtractor {
  private val simpleNodeRegex = """\(\s*(?<name>[\w\d]*)\s*:?\s*(?<label>[\w\d]*)\s*\)""".r

  def extract(lines: Lines): Iterator[ParsedNode] = {
    for {
      (str, line) <- lines.iterator.zipWithIndex
      nodeMatch <- simpleNodeRegex.findAllMatchIn(str)
    } yield {
      val name = Option(nodeMatch.group("name")).filter(_.nonEmpty)
      val labels = Seq(nodeMatch.group("label")).filter(_.nonEmpty)
      ParsedNode(
        InclusiveRect(line, line, nodeMatch.start, nodeMatch.end - 1),
        name,
        labels
      )
    }
  }
}

class InlineParsedNodeFinderTest extends CypherFunSuite {

  test("Find node in single line") {
    val res = positions("()")

    res shouldBe Seq(InclusiveRect(0, 0, 0, 1))
  }

  test("Find named and labelled node in single line") {
    val res = positions("(n:L)")

    res shouldBe Seq(InclusiveRect(0, 0, 0, 4))
  }

  test("Find two nodes in single line") {
    val res = positions("() ()")

    res shouldBe Seq(InclusiveRect(0, 0, 0, 1), InclusiveRect(0, 0, 3, 4))
  }

  test("Find nodes on multiple lines") {
    val res = positions(
      """()
        |  ()
        | ()""".stripMargin
    )

    res shouldBe Seq(
      InclusiveRect(0, 0, 0, 1),
      InclusiveRect(1, 1, 2, 3),
      InclusiveRect(2, 2, 1, 2)
    )
  }

  private def positions(str: String): Seq[InclusiveRect] = InlineParsedNodeFinder.extract(str).map(_.pos).toSeq
}
