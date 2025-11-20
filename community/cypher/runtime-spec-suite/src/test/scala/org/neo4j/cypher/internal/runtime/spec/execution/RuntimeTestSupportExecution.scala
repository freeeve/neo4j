/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.runtime.spec.execution

import org.neo4j.cypher.internal.CypherRuntime
import org.neo4j.cypher.internal.ExecutionPlan
import org.neo4j.cypher.internal.LogicalQuery
import org.neo4j.cypher.internal.RuntimeContext
import org.neo4j.cypher.internal.plandescription.InternalPlanDescription
import org.neo4j.cypher.internal.runtime.InputDataStream
import org.neo4j.cypher.internal.runtime.InputValues
import org.neo4j.cypher.internal.runtime.NoInput
import org.neo4j.cypher.internal.runtime.QueryRuntimeConfig
import org.neo4j.cypher.internal.runtime.spec.NonRecordingRuntimeResult
import org.neo4j.cypher.internal.runtime.spec.RecordingRuntimeResult
import org.neo4j.cypher.internal.runtime.spec.RuntimeTestSupport
import org.neo4j.cypher.internal.runtime.spec.rewriters.TestPlanCombinationRewriter.NoRewrites
import org.neo4j.cypher.internal.runtime.spec.rewriters.TestPlanCombinationRewriter.TestPlanCombinationRewriterHint
import org.neo4j.cypher.result.QueryProfile
import org.neo4j.cypher.result.RuntimeResult
import org.neo4j.kernel.impl.query.QuerySubscriber
import org.neo4j.values.AnyValue

trait RuntimeTestSupportExecution[CONTEXT <: RuntimeContext] {

  protected def runtimeTestSupport: RuntimeTestSupport[CONTEXT]
  protected def runtime: CypherRuntime[CONTEXT]

  // defaults: override these to set the default value for all tests in a suite
  protected def defaultQueryRuntimeConfig: QueryRuntimeConfig =
    runtimeTestSupport.runtimeContextManager.defaultQueryRuntimeConfig
  protected def defaultImplicitTx: Boolean = false

  private def defaultRunner: RuntimeTestSupport[CONTEXT]#PlanRunner[RuntimeResult] =
    runtimeTestSupport.PlanRunner.empty
      .withConfig(defaultQueryRuntimeConfig)
      .withImplicitTx(defaultImplicitTx)

  // Convenience overloaded methods
  protected def execute(executablePlan: ExecutionPlan): RecordingRuntimeResult =
    defaultRunner
      .withPlan(executablePlan)
      .recording
      .run()

  protected def execute(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT]
  ): RecordingRuntimeResult =
    execute(logicalQuery, runtime, NoInput)

  protected def execute(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT],
    parameters: Map[String, Any]
  ): RecordingRuntimeResult =
    executeQuery(
      logicalQuery,
      runtime,
      NoInput,
      parameters,
      Set.empty,
      defaultQueryRuntimeConfig
    )

  protected def execute(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT],
    input: InputValues
  ): RecordingRuntimeResult =
    execute(logicalQuery, runtime, input.stream())

  protected def execute(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT],
    subscriber: QuerySubscriber
  ): RuntimeResult =
    executeWithSubscriber(logicalQuery, runtime, subscriber)

  protected def execute(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT],
    inputStream: InputDataStream
  ): RecordingRuntimeResult =
    executeQuery(
      logicalQuery,
      runtime,
      inputStream,
      Map.empty,
      Set.empty,
      defaultQueryRuntimeConfig
    )

  protected def execute(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT],
    inputStream: InputDataStream,
    subscriber: QuerySubscriber
  ): RuntimeResult =
    executeWithSubscriber(logicalQuery, runtime, subscriber, inputStream)

  protected def profile(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT]
  ): RecordingRuntimeResult =
    profile(logicalQuery, runtime, NoInput)

  protected def profile(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT],
    input: InputValues
  ): RecordingRuntimeResult =
    profile(logicalQuery, runtime, input.stream())

  protected def profile(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT],
    inputStream: InputDataStream
  ): RecordingRuntimeResult =
    profileQuery(
      logicalQuery,
      runtime,
      inputStream,
      Set.empty,
      defaultQueryRuntimeConfig
    )

  protected def profile(
    executionPlan: ExecutionPlan,
    inputStream: InputDataStream = NoInput
  ): RecordingRuntimeResult =
    defaultRunner
      .withPlan(executionPlan)
      .recording
      .profiling
      .withInput(inputStream)
      .run()

  // RuntimeExecutionSupport methods

  protected def buildPlan(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT] = runtime,
    testPlanCombinationRewriterHints: Set[TestPlanCombinationRewriterHint] = Set.empty,
    queryConfig: QueryRuntimeConfig = defaultQueryRuntimeConfig
  ): ExecutionPlan =
    runtimeTestSupport.buildPlan(logicalQuery, runtime, testPlanCombinationRewriterHints, queryConfig)

  protected def buildPlanAndContext(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT] = runtime,
    testPlanCombinationRewriterHints: Set[TestPlanCombinationRewriterHint] = Set.empty,
    queryConfig: QueryRuntimeConfig = defaultQueryRuntimeConfig
  ): (ExecutionPlan, CONTEXT) =
    runtimeTestSupport.buildPlanAndContext(logicalQuery, runtime, testPlanCombinationRewriterHints, queryConfig)

  protected def execute(logicalQuery: LogicalQuery, implicitTx: Boolean): RecordingRuntimeResult =
    defaultRunner
      .withPlan(logicalQuery)
      .recording
      .withImplicitTx(implicitTx)
      .run()

  protected def executeQuery(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT] = runtime,
    inputStream: InputDataStream = NoInput,
    parameters: Map[String, Any] = Map.empty,
    testPlanCombinationRewriterHints: Set[TestPlanCombinationRewriterHint] = Set.empty,
    queryConfig: QueryRuntimeConfig = defaultQueryRuntimeConfig
  ): RecordingRuntimeResult =
    defaultRunner
      .withPlan(logicalQuery, runtime, testPlanCombinationRewriterHints)
      .recording
      .withInput(inputStream)
      .withParams(parameters)
      .withConfig(queryConfig)
      .run()

  protected def executeWithSubscriber(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT] = runtime,
    subscriber: QuerySubscriber,
    inputStream: InputDataStream = NoInput,
    testPlanCombinationRewriterHints: Set[TestPlanCombinationRewriterHint] = Set.empty
  ): RuntimeResult =
    defaultRunner
      .withPlan(logicalQuery, runtime, testPlanCombinationRewriterHints)
      .withSubscriber(subscriber)
      .withInput(inputStream)
      .run()

  protected def executeAs(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT] = runtime,
    username: String,
    password: String
  ): RecordingRuntimeResult = {
    defaultRunner
      .withPlan(logicalQuery, runtime, Set.empty)
      .recording
      .executeAs(username, password)
      .run()
  }

  protected def executeWithoutValuePopulation(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT] = runtime
  ): RecordingRuntimeResult =
    defaultRunner
      .withPlan(logicalQuery, runtime, Set.empty)
      .recording
      .noValues
      .run()

  protected def executeAndConsumeTransactionally(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT] = runtime,
    parameters: Map[String, Any] = Map.empty,
    profileAssertion: Option[QueryProfile => Unit] = None
  ): IndexedSeq[Array[AnyValue]] =
    runtimeTestSupport.executeAndConsumeTransactionally(
      logicalQuery,
      runtime,
      parameters,
      profileAssertion = profileAssertion
    ).run()

  protected def profileQuery(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT] = runtime,
    inputDataStream: InputDataStream = NoInput,
    testPlanCombinationRewriterHints: Set[TestPlanCombinationRewriterHint] = Set.empty,
    queryConfig: QueryRuntimeConfig = defaultQueryRuntimeConfig
  ): RecordingRuntimeResult =
    defaultRunner
      .withPlan(logicalQuery, runtime, testPlanCombinationRewriterHints)
      .recording
      .profiling
      .withInput(inputDataStream)
      .withConfig(queryConfig)
      .run()

  protected def profileNonRecording(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT] = runtime,
    inputDataStream: InputDataStream = NoInput
  ): NonRecordingRuntimeResult =
    defaultRunner
      .withPlan(logicalQuery.copy(doProfile = true), runtime, Set.empty)
      .nonRecording
      .profiling
      .withInput(inputDataStream)
      .run()

  protected def profileWithSubscriber(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT] = runtime,
    subscriber: QuerySubscriber,
    inputDataStream: InputDataStream = NoInput
  ): RuntimeResult =
    defaultRunner
      .withPlan(logicalQuery.copy(doProfile = true), runtime, Set.empty)
      .withSubscriber(subscriber)
      .profiling
      .withInput(inputDataStream)
      .run()

  protected def executeAndContextNonRecording(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT] = runtime,
    input: InputValues = InputValues.EMPTY,
    parameters: Map[String, Any] = Map.empty
  ): (NonRecordingRuntimeResult, CONTEXT) =
    defaultRunner
      .withPlan(logicalQuery, runtime, Set.empty)
      .withInput(input.stream())
      .withParams(parameters)
      .nonRecording
      .withContext
      .run()

  protected def executeAndExplain(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT] = runtime,
    input: InputValues = InputValues.EMPTY
  ): (RecordingRuntimeResult, InternalPlanDescription) = {
    val executionPlan =
      buildPlan(
        logicalQuery,
        runtime,
        testPlanCombinationRewriterHints = Set(NoRewrites),
        defaultQueryRuntimeConfig
      )

    defaultRunner
      .withPlan(executionPlan)
      .recording
      .withInput(input.stream())
      .mapResult(result => (result, runtimeTestSupport.explainDescription(logicalQuery, executionPlan)))
      .run()
  }

  protected def executeAndProfile(
    logicalQuery: LogicalQuery,
    runtime: CypherRuntime[CONTEXT] = runtime
  ): (RecordingRuntimeResult, InternalPlanDescription) = {
    val executionPlan =
      buildPlan(
        logicalQuery.copy(doProfile = true),
        runtime,
        testPlanCombinationRewriterHints = Set(NoRewrites),
        defaultQueryRuntimeConfig
      )

    defaultRunner
      .withPlan(executionPlan)
      .recording
      .profiling
      .mapResult { result =>
        result.consume()

        val executionPlanDescription = runtimeTestSupport
          .profileDescription(logicalQuery, executionPlan, result.runtimeResult.queryProfile())

        (result, executionPlanDescription)
      }
      .run()
  }
}
