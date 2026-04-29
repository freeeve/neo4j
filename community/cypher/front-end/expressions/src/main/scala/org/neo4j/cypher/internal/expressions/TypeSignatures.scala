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
package org.neo4j.cypher.internal.expressions

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.util.helpers.LazyVal

trait TypeSignatures {

  def signatures: Seq[TypeSignature] = Seq.empty

  def signatureLengths: Seq[Int] = lazySignatureLengths.value
  private val lazySignatureLengths: LazyVal[Seq[Int]] = LazyVal(signatures.map(_.argumentTypes.length))
}

trait FunctionTypeSignatures extends TypeSignatures {

  override def signatures: Seq[FunctionTypeSignature] = Seq.empty

  def signaturesByScope(cypherVersion: CypherVersion): Seq[FunctionTypeSignature] =
    signatures.filter(_.scopes.contains(cypherVersion))

  def signatureLengthsByScope(cypherVersion: CypherVersion): Seq[Int] =
    signaturesByScope(cypherVersion).map(_.argumentTypes.length)
}
