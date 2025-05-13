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
package org.neo4j.cypher.internal.rewriting.conditions

import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.ast.prettifier.Prettifier

object DoNotAffectPrettifier {

  def validate(before: Statement, after: Statement): Unit = {
    if (before != after) {
      val prettifier = Prettifier(ExpressionStringifier())
      val prettyBefore = prettifier.asString(before)
      val prettyAfter = prettifier.asString(after)
      if (prettyBefore != prettyAfter) {
        throw new IllegalStateException(
          s"Prettified AST has changed (and it's not supposed to):\nBefore:\n$prettyBefore\n\nAfter:\n$prettyAfter"
        )
      }
    }
  }
}
