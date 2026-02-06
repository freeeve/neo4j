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
package org.neo4j.cypher.internal.frontend.phases

import org.neo4j.cypher.internal.util.FunctionName
import org.neo4j.cypher.internal.util.ProcedureName

trait ProcedureSignatureResolver {
  def procedureSignature(name: ProcedureName, scope: QueryLanguage): ProcedureSignature
  def functionSignature(name: FunctionName, scope: QueryLanguage): Option[UserFunctionSignature]
  def procedureSignatureVersion: Long
}

trait ScopedProcedureSignatureResolver {
  def procedureSignature(name: ProcedureName): ProcedureSignature
  def functionSignature(name: FunctionName): Option[UserFunctionSignature]
  def procedureSignatureVersion: Long
  def queryLanguage: QueryLanguage
}

object ScopedProcedureSignatureResolver {

  def from(r: ProcedureSignatureResolver, scope: QueryLanguage): ScopedProcedureSignatureResolver = {
    new ScopedProcedureSignatureResolver {
      override def procedureSignature(n: ProcedureName): ProcedureSignature = r.procedureSignature(n, scope)
      override def functionSignature(n: FunctionName): Option[UserFunctionSignature] = r.functionSignature(n, scope)
      override def procedureSignatureVersion: Long = r.procedureSignatureVersion
      override def queryLanguage: QueryLanguage = scope
    }
  }
}
