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
package org.neo4j.cypher.internal.expressions.functions

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.expressions.FunctionTypeSignature
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.symbols.CTBoolean

// Just like the reduce function, this implementation only exists to raise a syntax error whe
// allReduce is inaccurately parsed as a function invocation.
case object AllReduce extends Function {
  override val name: String = "allReduce"

  override val signatures: Vector[FunctionTypeSignature] = Vector(
    FunctionTypeSignature(
      function = this,
      names = Vector("accumulator = initial", "variable IN list | reducer", "predicate"),
      argumentTypes = Vector(CTAny, CTAny, CTBoolean),
      outputType = CTBoolean,
      description =
        "Evaluates `expression` against each element of `LIST<ANY>`, with the result stored in `accumulator`. Returns true if `predicate` holds for each iteration of `accumulator`.",
      category = Category.PREDICATE,
      overrideDefaultAsString = Some(
        name + "(accumulator :: VARIABLE = initial :: ANY, variable :: VARIABLE IN list :: LIST<ANY> | reducer :: ANY, predicate :: BOOLEAN) :: BOOLEAN"
      ),
      argumentDescriptions = Map(
        "accumulator = initial" -> "The variable holding the result of reducer for each iteration, set to the value initial.",
        "variable IN list | reducer" -> "The iteration over a list that binds each list element to var and evaluates reducer to update the accumulator.",
        "predicate" -> "The predicate applied to the accumulator value for each element in the list."
      ),
      scopes = Set(CypherVersion.Cypher25)
    )
  )
}
