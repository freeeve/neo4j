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
package org.neo4j.cypher.internal.planner.spi.histogram

import org.neo4j.cypher.internal.expressions.EntityType

/**
 *
 * A histogram summarizes a data distribution.
 * The data consists of all values for a certain property-key for all nodes with a certain label, or all relationships with a certain type.
 * The number of buckets determine a trade-off between accuracy vs (storage space, construction time, estimation time).
 * A histogram with a single bucket is similar to assuming a uniform distribution between the lowest and the largest value.
 *
 *
 * @param nodeOrRelationship NODE_TYPE if this is a histogram for the property values of a set of nodes with a certain label
 *                           RELATIONSHIP_TYPE if this is a histogram for the property values of a set of relationships with a certain type
 * @param labelOrTypeName    The label name or type name that defines the set of nodes or relationships
 * @param property           The property-key for which this histogram summarizes the values
 * @param buckets            The actual data of this histogram: a collection of buckets which summarizes the data distribution
 *                           of the property values of the set of nodes or relationships
 */
case class Histogram(
  nodeOrRelationship: EntityType,
  labelOrTypeName: String,
  property: String,
  buckets: Set[Bucket]
) {}

trait Bucket {
  val minInclusive: Double
  val maxExclusive: Double
  val selectivity: Double

  /**
   *
   * @param v The constant value in the inequality predicate: n.prop < v
   * @return the estimated selectivity for n.prop < v in this bucket
   */
  def estimatePropLessThanValue(v: Double): Double

  /**
   *
   * @param v The constant value in the inequality predicate: n.prop <= v
   * @param e A constant value to distinguish between integer and floating point domains.
   *          For an integer domain      : e = 1
   *          For a floating point domain: e = Double.MinPositiveValue
   *          n.prop <= 4   on a bucket [3, 5) will include two values ('3' and '4') on an integer domain.
   *          n.prop <= 4.0 on a bucket [3, 5) will include all values in the range [3, 4] (roughly 1) on a floating point domain
   * @return the estimated selectivity for n.prop <= v in this bucket
   */
  def estimatePropLessOrEqualThanValue(v: Double, e: Double): Double
}

/**
 * Standard bucket type that only keeps the selectivity per bucket and assumes
 * - that all values in the range of the bucket occur
 * - that the frequency of the occurrence of each value is the same (uniform distribution assumption)
 *
 * @param minInclusive The minimum value (inclusive) for which this bucket keeps statistics
 * @param maxExclusive The maximum value (exclusive) for which this bucket keeps statistics
 * @param selectivity The number of elements that fall in the range of this bucket divided by the total number of elements
 */
case class StandardBucket(
  override val minInclusive: Double,
  override val maxExclusive: Double,
  override val selectivity: Double
) extends Bucket {

  override def estimatePropLessThanValue(v: Double): Double = {
    // [min, v) / [min, max)
    val fraction = (v - minInclusive) / (maxExclusive - minInclusive)
    selectivity * fraction
  }

  override def estimatePropLessOrEqualThanValue(v: Double, e: Double): Double = {
    // [min, v]   / [min, max)
    // Transform the inclusive v to an exclusive v' by adding e
    // [min, v+e) / [min, max)
    val fraction = (Math.min(v + e, maxExclusive) - minInclusive) / (maxExclusive - minInclusive)
    selectivity * fraction
  }
}
