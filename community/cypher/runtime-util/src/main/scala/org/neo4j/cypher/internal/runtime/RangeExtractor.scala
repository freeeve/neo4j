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

import org.neo4j.cypher.internal.logical.plans.InequalitySeekRange
import org.neo4j.cypher.internal.logical.plans.PointBoundingBoxRange
import org.neo4j.cypher.internal.logical.plans.PointDistanceRange
import org.neo4j.cypher.internal.logical.plans.PrefixRange

/**
 * Extracts a [[RangeQueryExpression]]'s leaf-typed wrapper into an [[ExtractedRange]].
 *
 * The wrapper shape differs between the logical plan (uses `*SeekRangeWrapper` classes that wrap
 * [[org.neo4j.cypher.internal.expressions.Expression]]) and the interpreted runtime (uses
 * `*SeekRangeExpression` classes that wrap runtime [[Expression]]s). Callers of
 * [[QueryExpressionSupport.compile]] supply an extractor matching their leaf representation.
 */
trait RangeExtractor[E] {
  def extract(wrapper: E): ExtractedRange[E]
}

sealed trait ExtractedRange[L]

object ExtractedRange {
  final case class Prefix[L](range: PrefixRange[L]) extends ExtractedRange[L]
  final case class Inequality[L](range: InequalitySeekRange[L]) extends ExtractedRange[L]
  final case class PointDistance[L](range: PointDistanceRange[L]) extends ExtractedRange[L]
  final case class PointBoundingBox[L](range: PointBoundingBoxRange[L]) extends ExtractedRange[L]
}
