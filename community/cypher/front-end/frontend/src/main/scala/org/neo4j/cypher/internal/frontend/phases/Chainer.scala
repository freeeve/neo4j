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

object Chainer {

  /**
   * Chain together transformers.
   * Illegal sequences are not caught because of type erasure.
   * They will lead to [[ClassCastException]]s later on.
   */
  def chainTransformers(transformers: Seq[Transformer[_ <: BaseContext, _, _]]): Transformer[_ <: BaseContext, _, _] = {
    transformers.reduceLeft[Transformer[_ <: BaseContext, _, _]] {
      case (t1: Transformer[_, _, _], t2: Transformer[_, _, _]) =>
        // We need these asInstanceOf to make the compiler happy.
        // Because of type erasure, they won't actually do anything.
        // This will even work for Transformers from/to LogicalPlanState.
        t1.asInstanceOf[Transformer[BaseContext, BaseState, BaseState]] andThen t2.asInstanceOf[Transformer[
          BaseContext,
          BaseState,
          BaseState
        ]]
    }
  }
}
