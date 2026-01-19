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
package org.neo4j.cypher.internal.runtime

import org.neo4j.cypher.internal.logical.plans.TraversalPathMode
import org.neo4j.cypher.internal.runtime.HeapTrackingLongImmutableSet.HeapTrackingImmutableSet1
import org.neo4j.memory.MemoryTracker
import org.neo4j.values.virtual.ListValue
import org.neo4j.values.virtual.VirtualNodeValue
import org.neo4j.values.virtual.VirtualRelationshipValue
import org.neo4j.values.virtual.VirtualValues.EMPTY_LIST

abstract class TraversalContainer extends AutoCloseable {
  def append(node: VirtualNodeValue, relationship: VirtualRelationshipValue): TraversalContainer

  final def canAdd(node: VirtualNodeValue, relationship: VirtualRelationshipValue): Boolean =
    canAdd(node.id(), relationship.id())

  def canAdd(nodeId: Long, relationshipId: Long): Boolean = true

  def size: Int
  def relationshipsAsList: ListValue
  def withReversedRelationships: TraversalContainer
}

object TraversalContainer {

  /**
   * Utility class that has constant time `append`, `contains`, and `size` methods
   */
  private class TrailModeTraversalContainer private[TraversalContainer] (
    private val maybeList: Option[ListValue],
    val size: Int,
    private val set: HeapTrackingLongImmutableSet
  ) extends TraversalContainer {

    override def append(node: VirtualNodeValue, rel: VirtualRelationshipValue): TrailModeTraversalContainer =
      new TrailModeTraversalContainer(maybeList.map(_.append(rel)), size + 1, set + rel.id())

    override def canAdd(nodeId: Long, relationshipId: Long): Boolean = !set.contains(relationshipId)

    override def relationshipsAsList: ListValue = maybeList.get

    override def withReversedRelationships: TrailModeTraversalContainer =
      new TrailModeTraversalContainer(maybeList.map(_.reverse()), size, set)

    override def close(): Unit =
      set.close()
  }

  private class WalkModeTraversalContainer private[TraversalContainer] (
    private val maybeList: Option[ListValue],
    val size: Int
  ) extends TraversalContainer {

    override def append(node: VirtualNodeValue, rel: VirtualRelationshipValue): WalkModeTraversalContainer =
      new WalkModeTraversalContainer(maybeList.map(_.append(rel)), size + 1)

    override def relationshipsAsList: ListValue = maybeList.get

    override def withReversedRelationships: WalkModeTraversalContainer =
      new WalkModeTraversalContainer(maybeList.map(_.reverse()), size)

    override def close(): Unit = {
      // nothing to close
    }
  }

  private class AcyclicModeTraversalContainer private[TraversalContainer] (
    private val maybeList: Option[ListValue],
    val size: Int,
    nodes: HeapTrackingLongImmutableSet,
    relationships: HeapTrackingLongImmutableSet
  ) extends TraversalContainer {

    override def append(node: VirtualNodeValue, rel: VirtualRelationshipValue): TraversalContainer = {
      new AcyclicModeTraversalContainer(
        maybeList.map(_.append(rel)),
        size + 1,
        nodes + node.id(),
        relationships + rel.id()
      )
    }

    override def canAdd(nodeId: Long, relationshipId: Long): Boolean =
      !nodes.contains(nodeId) && !relationships.contains(relationshipId)

    override def close(): Unit = {
      nodes.close()
      relationships.close()
    }

    override def relationshipsAsList: ListValue =
      maybeList.get

    override def withReversedRelationships: TraversalContainer =
      new AcyclicModeTraversalContainer(maybeList.map(_.reverse()), size, nodes, relationships)
  }

  def withStartNode(
    nodeId: Long,
    memoryTracker: MemoryTracker,
    traversalPathMode: TraversalPathMode,
    storeList: Boolean
  ): TraversalContainer = {
    val maybeList = if (storeList) {
      Some(EMPTY_LIST)
    } else {
      None
    }

    traversalPathMode match {
      case TraversalPathMode.Walk =>
        new WalkModeTraversalContainer(maybeList, 0)
      case TraversalPathMode.Trail =>
        new TrailModeTraversalContainer(maybeList, 0, HeapTrackingLongImmutableSet.emptySet(memoryTracker))
      case TraversalPathMode.Acyclic =>
        new AcyclicModeTraversalContainer(
          maybeList,
          0,
          HeapTrackingImmutableSet1.newSet1(memoryTracker, nodeId),
          HeapTrackingLongImmutableSet.emptySet(memoryTracker)
        )
    }
  }
}
