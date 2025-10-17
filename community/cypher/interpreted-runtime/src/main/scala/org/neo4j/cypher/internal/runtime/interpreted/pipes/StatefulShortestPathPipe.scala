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
package org.neo4j.cypher.internal.runtime.interpreted.pipes

import org.neo4j.cypher.internal.logical.plans.StatefulShortestPath
import org.neo4j.cypher.internal.logical.plans.StatefulShortestPath.LengthBounds
import org.neo4j.cypher.internal.logical.plans.TraversalPathMode
import org.neo4j.cypher.internal.runtime.CastSupport
import org.neo4j.cypher.internal.runtime.ClosingIterator
import org.neo4j.cypher.internal.runtime.ClosingIterator.JavaAutoCloseableIteratorAsClosingIterator
import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.IsNoValue
import org.neo4j.cypher.internal.runtime.interpreted.commands.CommandNFA
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.commands.predicates.Predicate
import org.neo4j.cypher.internal.runtime.interpreted.pipes.StatefulShortestPathPipe.getPathCount
import org.neo4j.cypher.internal.runtime.interpreted.pipes.StatefulShortestPathPipe.traversalPathModeFactory
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.operations.CypherTypeValueMapper
import org.neo4j.exceptions.CypherTypeException
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation
import org.neo4j.gqlstatus.GqlParams
import org.neo4j.gqlstatus.GqlStatusInfoCodes
import org.neo4j.internal.kernel.api.helpers.traversal.SlotOrName
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.PGPathPropagatingBFS
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.PathTracer
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.PathWriter
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.SignpostStack
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.TraversalPathModeFactory
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.hooks.PPBFSHooks
import org.neo4j.kernel.api.StatementConstants.NO_SUCH_ENTITY
import org.neo4j.kernel.api.exceptions.InvalidArgumentsException
import org.neo4j.memory.MemoryTracker
import org.neo4j.values.VirtualValue
import org.neo4j.values.storable.IntegralValue
import org.neo4j.values.storable.Value
import org.neo4j.values.virtual.ListValueBuilder
import org.neo4j.values.virtual.VirtualNodeValue
import org.neo4j.values.virtual.VirtualValues

import scala.collection.mutable

case class StatefulShortestPathPipe(
  source: Pipe,
  sourceNodeName: String,
  intoTargetNodeName: Option[String],
  commandNFA: CommandNFA,
  bounds: LengthBounds,
  preFilters: Option[Predicate],
  selector: StatefulShortestPath.Selector,
  kExpression: Expression,
  grouped: Set[String],
  reverseGroupVariableProjections: Boolean,
  pathMode: TraversalPathMode
)(val id: Id = Id.INVALID_ID)
    extends PipeWithSource(source) {
  self =>

  override protected def internalCreateResults(
    input: ClosingIterator[CypherRow],
    state: QueryState
  ): ClosingIterator[CypherRow] = {
    val memoryTracker = state.memoryTrackerForOperatorProvider.memoryTrackerForOperator(id.x)

    val nodeCursor = state.query.nodeCursor()
    state.query.resources.trace(nodeCursor)
    val traversalCursor = state.query.traversalCursor()
    state.query.resources.trace(traversalCursor)

    val hooks = PPBFSHooks.getInstance()

    val tracker = traversalPathModeFactory(pathMode, memoryTracker, hooks)
    val pathTracer =
      new PathTracer[CypherRow](memoryTracker, tracker, hooks)
    val pathPredicate =
      preFilters.fold[java.util.function.Predicate[CypherRow]](_ => true)(pred => pred.isTrue(_, state))

    input.flatMap { inputRow =>
      val sourceNode = inputRow.getByName(sourceNodeName)
      val targetNode = intoTargetNodeName.map(inputRow.getByName)

      (sourceNode, targetNode) match {
        case (IsNoValue(), _) | (_, Some(IsNoValue())) => ClosingIterator.empty
        case (s, t) =>
          val source = CastSupport.castOrFail[VirtualNodeValue](s).id()
          val target = t.map(CastSupport.castOrFail[VirtualNodeValue](_).id()).getOrElse(NO_SUCH_ENTITY)
          val (startState, finalState) = commandNFA.compile(inputRow, state)

          PGPathPropagatingBFS.create(
            source,
            startState,
            target,
            finalState,
            state.query.transactionalContext.dataRead,
            nodeCursor,
            traversalCursor,
            pathTracer,
            withPathVariables(inputRow, _),
            pathPredicate,
            selector.isGroup,
            bounds.max.getOrElse(-1),
            getPathCount(kExpression, inputRow, state),
            commandNFA.states.size,
            memoryTracker,
            hooks,
            state.query.transactionalContext.assertTransactionOpen _,
            tracker
          ).asSelfClosingIterator
      }

    }.closing(nodeCursor).closing(traversalCursor)
  }

  private def withPathVariables(original: CypherRow, stack: SignpostStack): CypherRow = {
    val row = original.createClone()

    val groupMap = mutable.HashMap.empty[String, ListValueBuilder]

    def write(slotOrName: SlotOrName, value: VirtualValue): Unit =
      slotOrName match {
        case SlotOrName.VarName(varName, isGroup) =>
          if (isGroup) {
            groupMap.getOrElseUpdate(varName, ListValueBuilder.newListBuilder())
              .add(value)
          } else {
            row.set(varName, value)
          }
        case _: SlotOrName.Slotted => throw new IllegalStateException("Slotted metadata in Legacy runtime")
        case SlotOrName.None       => ()
      }

    stack.materialize(new PathWriter {
      def writeNode(slotOrName: SlotOrName, id: Long): Unit =
        write(slotOrName, VirtualValues.node(id))

      def writeRel(slotOrName: SlotOrName, id: Long): Unit =
        write(slotOrName, VirtualValues.relationship(id))
    })

    grouped.foreach { name =>
      val value = groupMap.get(name) match {
        case Some(list) => if (reverseGroupVariableProjections) list.build().reverse() else list.build()
        case None       => VirtualValues.EMPTY_LIST
      }
      row.set(name, value)
    }

    row
  }

}

object StatefulShortestPathPipe {

  def traversalPathModeFactory(
    pathMode: TraversalPathMode,
    memoryTracker: MemoryTracker,
    hooks: PPBFSHooks
  ): TraversalPathModeFactory = pathMode match {
    case TraversalPathMode.Trail =>
      TraversalPathModeFactory.trailMode(memoryTracker, hooks)
    case TraversalPathMode.Walk    => TraversalPathModeFactory.walkMode()
    case TraversalPathMode.Acyclic => ??? // TODO: implement when we implement acyclic for shortest
  }

  def getPathCount(kExpression: Expression, inputRow: CypherRow, state: QueryState): Int = {
    kExpression.apply(inputRow, state) match {
      case n: IntegralValue if n.longValue().toInt > 0 => n.longValue().toInt
      case n: IntegralValue =>
        val gql = ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22003)
          .withParam(GqlParams.StringParam.value, String.valueOf(n.longValue()))
          .withCause(ErrorGqlStatusObjectImplementation.from(GqlStatusInfoCodes.STATUS_22N02)
            .withParam(GqlParams.StringParam.option, "count").withParam(
              GqlParams.NumberParam.value,
              n.longValue().toInt
            )
            .build).build
        throw new InvalidArgumentsException(
          gql,
          String.format("Count requires positive integer argument, got `%d`", n.longValue().toInt)
        )
      case v: Value => throw CypherTypeException.expectedInteger(
          s"Expected Integer but got ${v.getTypeName}",
          v.prettyPrint(),
          CypherTypeValueMapper.valueType(v)
        )
      case v => throw CypherTypeException.expectedInteger(
          s"Expected Integer but got ${v.getTypeName}",
          String.valueOf(v),
          CypherTypeValueMapper.valueType(v)
        )
    }
  }
}
