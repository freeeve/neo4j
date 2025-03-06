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
package org.neo4j.cypher.internal.rewriting

import org.neo4j.cypher.internal.ast.AddedInRewriteGeneral
import org.neo4j.cypher.internal.ast.DefaultWith
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.rewriting.rewriters.preparatoryRewriters.InsertWithBetweenOptionalMatchAndMatch
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class InsertWithBetweenOptionalMatchAndMatchTest extends CypherFunSuite with RewriteTest {
  val rewriterUnderTest: Rewriter = InsertWithBetweenOptionalMatchAndMatch.instance

  test("OPTIONAL MATCH followed by MATCH") {
    assertRewrite(
      "OPTIONAL MATCH (a) MATCH (a) RETURN a",
      "OPTIONAL MATCH (a) WITH * MATCH (a) RETURN a",
      additionalExpectedAstUpdates = expectedStatement => {
        expectedStatement.endoRewrite(bottomUp(Rewriter.lift {
          // The original/rewritten statement will have AddedInRewriteGeneral,
          // the explicit WITH in the expected will have DefaultWith
          // so let's update that before checking the equality
          case w: With if w.withType == DefaultWith =>
            w.copy(withType = AddedInRewriteGeneral)(w.position)
        }))
      }
    )
  }

  test("MATCH followed by OPTIONAL MATCH followed by MATCH") {
    assertRewrite(
      "MATCH (a)-->(b) OPTIONAL MATCH (b)-->(c) MATCH (c)-->(a) RETURN a, b, c",
      "MATCH (a)-->(b) OPTIONAL MATCH (b)-->(c) WITH * MATCH (c)-->(a) RETURN a, b, c",
      additionalExpectedAstUpdates = expectedStatement => {
        expectedStatement.endoRewrite(bottomUp(Rewriter.lift {
          // The original/rewritten statement will have AddedInRewriteGeneral,
          // the explicit WITH in the expected will have DefaultWith
          // so let's update that before checking the equality
          case w: With if w.withType == DefaultWith =>
            w.copy(withType = AddedInRewriteGeneral)(w.position)
        }))
      }
    )
  }

  test("Multiple OPTIONAL MATCHes followed by MATCHes") {
    assertRewrite(
      "OPTIONAL MATCH (a) MATCH (a) WITH a AS b OPTIONAL MATCH (b)--(c) MATCH (c:C) RETURN b, c",
      "OPTIONAL MATCH (a) WITH * MATCH (a) WITH a AS b OPTIONAL MATCH (b)--(c) WITH * MATCH (c:C) RETURN b, c",
      additionalExpectedAstUpdates = expectedStatement => {
        expectedStatement.endoRewrite(bottomUp(Rewriter.lift {
          // The original/rewritten statement will have AddedInRewriteGeneral on the extra WITH,
          // both explicit WITHs in the expected will have DefaultWith
          // so let's update the added WITH before checking the equality
          case w: With if w.returnItems.includeExisting =>
            w.copy(withType = AddedInRewriteGeneral)(w.position)
        }))
      }
    )
  }

  test("OPTIONAL MATCHes before other clauses") {
    assertIsNotRewritten(
      "OPTIONAL MATCH (a) WITH a MATCH (a) RETURN a"
    )
    assertIsNotRewritten(
      "OPTIONAL MATCH (a) RETURN a"
    )
    assertIsNotRewritten(
      "OPTIONAL MATCH (a) UNWIND [5] AS i RETURN a, i"
    )
  }
}
