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
package org.neo4j.cypher.internal.spi

import org.neo4j.cypher.internal.compiler.planner.logical.cardinality.ExpressionSelectivityCalculator
import org.neo4j.cypher.internal.compiler.planner.logical.cardinality.IndependenceCombiner
import org.neo4j.cypher.internal.planner.spi.IndexComparatorFactory
import org.neo4j.cypher.internal.util.Selectivity
import org.neo4j.internal.kernel.api.Read
import org.neo4j.internal.kernel.api.SchemaRead
import org.neo4j.internal.schema

import java.util.Comparator

object TransactionBoundIndexComparatorFactory extends IndexComparatorFactory {

  override def createComparator(read: Read, schemaRead: SchemaRead): Comparator[schema.IndexDescriptor] =
    new ComparatorImpl(read, schemaRead)

  private class ComparatorImpl(read: Read, schemaRead: SchemaRead) extends Comparator[schema.IndexDescriptor] {

    private def selectivity(indexDescriptor: schema.IndexDescriptor): Option[Selectivity] = {
      ExpressionSelectivityCalculator.selectivityForPropertyEquality(
        propertySelectivity =
          TransactionBoundGraphStatistics.indexPropertyIsNotNullSelectivity(indexDescriptor, read, schemaRead),
        uniqueValueSelectivity =
          TransactionBoundGraphStatistics.uniqueValueSelectivity(indexDescriptor, schemaRead),
        size = 1,
        combiner = IndependenceCombiner
      )
    }

    private def rank(indexDescriptor: schema.IndexDescriptor): Double = {
      selectivity(indexDescriptor)
        .getOrElse(Selectivity.ONE)
        .negate // low selectivity => high rank
        .factor
    }

    override def compare(a: schema.IndexDescriptor, b: schema.IndexDescriptor): Int = rank(a).compareTo(rank(b))
  }
}
