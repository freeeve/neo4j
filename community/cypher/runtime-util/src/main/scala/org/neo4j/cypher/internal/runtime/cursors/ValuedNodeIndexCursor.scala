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
package org.neo4j.cypher.internal.runtime.cursors

import org.neo4j.internal.kernel.api.DefaultCloseListenable
import org.neo4j.internal.kernel.api.KernelReadTracer
import org.neo4j.internal.kernel.api.NodeCursor
import org.neo4j.internal.kernel.api.NodeValueIndexCursor
import org.neo4j.internal.kernel.api.PropertyCursor
import org.neo4j.internal.kernel.api.RelationshipTraversalCursor
import org.neo4j.internal.kernel.api.TokenSet
import org.neo4j.storageengine.api.Degrees
import org.neo4j.storageengine.api.PropertySelection
import org.neo4j.storageengine.api.Reference
import org.neo4j.storageengine.api.RelationshipSelection
import org.neo4j.values.storable.Value

class ValuedNodeIndexCursor(val inner: NodeValueIndexCursor, values: Array[Value]) extends DefaultCloseListenable
    with NodeValueIndexCursor {

  override def numberOfProperties(): Int = values.length

  override def hasValue: Boolean = true

  override def propertyValue(offset: Int): Value = values(offset)

  override def node(cursor: NodeCursor): Unit = inner.node(cursor)

  override def nodeReference(): Long = inner.nodeReference()

  override def next(): Boolean = inner.next()

  override def closeInternal(): Unit = inner.close()

  override def isClosed: Boolean = inner.isClosed

  override def score(): Float = inner.score()

  override def setTracer(tracer: KernelReadTracer): Unit = inner.setTracer(tracer)

  override def removeTracer(): Unit = inner.removeTracer()

  override def readFromStore(): Boolean = inner.readFromStore()

  override def labels(): TokenSet = inner.labels()

  override def labelsIgnoringTxStateSetRemove(): TokenSet = inner.labelsIgnoringTxStateSetRemove()

  override def hasLabel(label: Int): Boolean = inner.hasLabel(label)

  override def hasLabel: Boolean = inner.hasLabel

  override def labelsAndProperties(propertyCursor: PropertyCursor, selection: PropertySelection): TokenSet =
    inner.labelsAndProperties(propertyCursor, selection)

  override def relationships(relationships: RelationshipTraversalCursor, selection: RelationshipSelection): Unit =
    inner.relationships(relationships, selection)

  override def supportsFastRelationshipsTo(): Boolean = inner.supportsFastRelationshipsTo()

  override def relationshipsTo(
    relationships: RelationshipTraversalCursor,
    selection: RelationshipSelection,
    neighbourNodeReference: Long
  ): Unit = inner.relationshipsTo(relationships, selection, neighbourNodeReference)

  override def relationshipsReference(): Long = inner.relationshipsReference()

  override def supportsFastDegreeLookup(): Boolean = inner.supportsFastDegreeLookup()

  override def relationshipTypes(): Array[Int] = inner.relationshipTypes()

  override def degrees(selection: RelationshipSelection): Degrees = inner.degrees(selection)

  override def degree(selection: RelationshipSelection): Long = inner.degree(selection)

  override def degreeWithMax(maxDegree: Long, selection: RelationshipSelection): Long =
    inner.degreeWithMax(maxDegree, selection)

  override def properties(cursor: PropertyCursor, selection: PropertySelection): Unit =
    inner.properties(cursor, selection)

  override def propertiesReference(): Reference = inner.propertiesReference()
}
