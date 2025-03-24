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

import org.neo4j.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase
import org.neo4j.cypher.internal.util.StepSequencer

case object SetSemanticsNotUpToDate extends Phase[BaseContext, BaseState, BaseState] {

  override def phase: CompilationPhaseTracer.CompilationPhase = CompilationPhase.AST_REWRITE

  override def process(from: BaseState, context: BaseContext): BaseState = from.withSemanticsUpToDate(false)

  override def postConditions: Set[StepSequencer.Condition] = Set.empty
}

case object IfChangedSetSemantics extends Phase[BaseContext, BaseState, BaseState] {

  override def phase: CompilationPhaseTracer.CompilationPhase = CompilationPhase.AST_REWRITE

  override def process(from: BaseState, context: BaseContext): BaseState = from.withSemanticsUpToDate(false)

  override def postConditions: Set[StepSequencer.Condition] = Set.empty

  def using(inner: Transformer[BaseContext, BaseState, BaseState]): Transformer[BaseContext, BaseState, BaseState] =
    IfChanged((from: BaseState, to: BaseState) =>
      from.semanticsUpToDate && !from.maybeStatement.equals(to.maybeStatement)
    )(inner)(SetSemanticsNotUpToDate.asInstanceOf[Transformer[BaseContext, BaseState, BaseState]])

}
