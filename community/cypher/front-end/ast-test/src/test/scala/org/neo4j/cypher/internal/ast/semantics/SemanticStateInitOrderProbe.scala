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
package org.neo4j.cypher.internal.ast.semantics

object SemanticStateInitOrderProbe {

  def main(args: Array[String]): Unit = {
    val scopeZipperClass = Class.forName(
      "org.neo4j.cypher.internal.ast.semantics.ScopeZipper$",
      true,
      Thread.currentThread.getContextClassLoader
    )
    require(scopeZipperClass.getField("MODULE$").get(null) != null, "ScopeZipper MODULE$ is null")
    require(SemanticState.clean != null, "SemanticState.clean is null")
    println("OK")
  }
}
