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
package org.neo4j.cypher.internal.planner.spi

import org.neo4j.internal.kernel.api.Read
import org.neo4j.internal.kernel.api.SchemaRead
import org.neo4j.internal.schema

import java.util.Comparator

trait IndexComparatorFactory {
  def createComparator(read: Read, schemaRead: SchemaRead): Comparator[schema.IndexDescriptor]
}

object NoPreferenceIndexComparatorFactory extends IndexComparatorFactory {

  override def createComparator(read: Read, schemaRead: SchemaRead): Comparator[schema.IndexDescriptor] = {
    (a: schema.IndexDescriptor, b: schema.IndexDescriptor) => a.hashCode().compareTo(b.hashCode())
  }
}

object ThrowingIndexComparatorFactory extends IndexComparatorFactory {

  override def createComparator(read: Read, schemaRead: SchemaRead): Comparator[schema.IndexDescriptor] = {
    throw new NotImplementedError(
      "Index comparator for dynamic lookups is not supported here; supply a real comparator to make it work."
    )
  }
}
