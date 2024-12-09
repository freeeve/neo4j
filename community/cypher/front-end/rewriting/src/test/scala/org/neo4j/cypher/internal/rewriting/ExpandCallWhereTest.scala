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

import org.neo4j.cypher.internal.ast.AddedInRewriteProcCall
import org.neo4j.cypher.internal.ast.DefaultWith
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.rewriting.rewriters.expandCallWhere
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class ExpandCallWhereTest extends CypherFunSuite with RewriteTest {

  override val rewriterUnderTest: Rewriter = expandCallWhere.instance

  test("rewrite call yield where") {
    assertRewrite(
      "CALL foo() YIELD a, b WHERE a > b RETURN *",
      "CALL foo() YIELD a, b WITH * WHERE a > b RETURN *",
      additionalExpectedAstUpdates = expectedStatement => {
        expectedStatement.endoRewrite(bottomUp(Rewriter.lift {
          // The original/rewritten statement will have AddedInRewriteProcCall,
          // the explicit WITH in the expected will have DefaultWith
          // so let's update that before checking the equality
          case w: With if w.withType == DefaultWith =>
            w.copy(withType = AddedInRewriteProcCall)(w.position)
        }))
      }
    )
  }

  test("does not rewrite") {
    assertIsNotRewritten("CALL foo() YIELD a, b WITH * WHERE a > b RETURN *")
    assertIsNotRewritten("CALL foo() YIELD a, b RETURN *")
  }
}
