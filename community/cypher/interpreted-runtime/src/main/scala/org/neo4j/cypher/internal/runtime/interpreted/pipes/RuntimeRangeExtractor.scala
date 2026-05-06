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

import org.neo4j.cypher.internal.runtime.ExtractedRange
import org.neo4j.cypher.internal.runtime.RangeExtractor
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.Expression
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.InequalitySeekRangeExpression
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.PointBoundingBoxSeekRangeExpression
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.PointDistanceSeekRangeExpression
import org.neo4j.cypher.internal.runtime.interpreted.commands.expressions.PrefixSeekRangeExpression
import org.neo4j.exceptions.InternalException

/**
 * Extracts runtime `*SeekRangeExpression` wrappers used by the interpreted/slotted/pipelined
 * runtimes into an [[ExtractedRange]]
 */
object RuntimeRangeExtractor extends RangeExtractor[Expression] {

  override def extract(wrapper: Expression): ExtractedRange[Expression] = wrapper match {
    case PrefixSeekRangeExpression(r)           => ExtractedRange.Prefix(r)
    case InequalitySeekRangeExpression(r)       => ExtractedRange.Inequality(r)
    case PointDistanceSeekRangeExpression(r)    => ExtractedRange.PointDistance(r)
    case PointBoundingBoxSeekRangeExpression(r) => ExtractedRange.PointBoundingBox(r)
    case other => throw InternalException.internalError(
        getClass.getSimpleName,
        s"Unexpected range expression: $other"
      )
  }
}
