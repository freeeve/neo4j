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

import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.FunctionTypeSignature
import org.neo4j.cypher.internal.util.FunctionName
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.symbols.CypherType

case class LocalFunction(
  functionName: FunctionName,
  parameterTypes: IndexedSeq[(String, CypherType)],
  optionalParameterTypes: IndexedSeq[(String, CypherType)] = Vector.empty,
  defaultArguments: IndexedSeq[Expression] = Vector.empty,
  outputSignature: Option[CypherType]
) extends Function {
  override def name: String = functionName.fullName

  override val signatures: Seq[FunctionTypeSignature] = {
    val baseSignature =
      FunctionTypeSignature(
        this,
        names = parameterTypes.map(_._1),
        argumentTypes = parameterTypes.map(_._2),
        outputType = returnType,
        description = s"Local function $name",
        category = Category.LOCAL
      )
    optionalParameterTypes.scanLeft(baseSignature) {
      case (last, optionalParameterType) =>
        last.copy(
          names = last.names :+ optionalParameterType._1,
          argumentTypes = last.argumentTypes :+ optionalParameterType._2
        )
    }
  }

  def returnType: CypherType = outputSignature.getOrElse(CTAny)
}
