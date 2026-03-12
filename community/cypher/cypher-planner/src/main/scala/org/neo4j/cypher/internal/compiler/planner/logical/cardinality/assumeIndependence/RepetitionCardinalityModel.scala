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
package org.neo4j.cypher.internal.compiler.planner.logical.cardinality.assumeIndependence

import org.neo4j.cypher.internal.compiler.planner.logical.PlannerDefaults.DEFAULT_NODES_UNIQUENESS_SELECTIVITY
import org.neo4j.cypher.internal.compiler.planner.logical.PlannerDefaults.DEFAULT_REL_UNIQUENESS_SELECTIVITY
import org.neo4j.cypher.internal.ir.VarPatternLength
import org.neo4j.cypher.internal.util.Repetition
import org.neo4j.cypher.internal.util.Selectivity
import org.neo4j.cypher.internal.util.UpperBound

object RepetitionCardinalityModel {

  val MAX_VAR_LENGTH = 32

  def varPatternLengthAsRange(varPatternLength: VarPatternLength): Range = {
    val maximumBound = varPatternLength.max match {
      case Some(limit) => Math.min(limit, MAX_VAR_LENGTH)
      case None        => MAX_VAR_LENGTH
    }
    val minimumBound = Math.min(varPatternLength.min, maximumBound)
    Range.inclusive(minimumBound, maximumBound)
  }

  def quantifiedPathPatternRepetitionAsRange(repetition: Repetition): Range = {
    val maximumBound = repetition.max match {
      case UpperBound.Unlimited      => MAX_VAR_LENGTH
      case UpperBound.Limited(limit) => if (limit < MAX_VAR_LENGTH) limit.toInt else MAX_VAR_LENGTH
    }
    val minimumBound = Math.min(repetition.min, maximumBound).toInt
    Range.inclusive(minimumBound, maximumBound)
  }

  /**
   * @param differentRelationships number of DifferentRelationships predicates within the pattern (always 0 for var-length relationships, as there is only one relationship).
   * @param uniqueRelationships number of relationships within the pattern that must be distinct across iterations (always 1 for var-length relationships).
   * @param repetitions number of times the pattern is repeated.
   * @return the selectivity of the DifferentRelationships and Unique predicates when unrolling a quantified path pattern or a var-length relationship under relationship uniqueness.
   *
   * For example, given the var-length relationship:
   * {{{()-[r:R*3]->()}}}
   * It would unroll to:
   * {{{()-[r1:R]->()-[r2:R]->()-[r3:R]->() WHERE r1 <> r2 AND r1 <> r3 AND r2 <> r3}}}
   * And so we get a selectivity of:
   * {{{relationshipUniquenessSelectivity(0, 1, 3) = 0.99 ^ 3 =~ 0.970}}}
   *
   * Likewise, given the quantified path pattern:
   * {{{(()-[r:R]->()-[s:S]->()-[t]->()){2} }}}
   * It would unroll to:
   * {{{
   *   ()-[r1:R]->()-[s1:S]->()-[t1]->()-[r2:R]->()-[s2:S]->()-[t2]->()
   *     // r <> t
   *     WHERE r1 <> t1
   *       AND r2 <> t2
   *       AND r1 <> t2
   *       AND r2 <> t1
   *
   *       // s <> t
   *       AND s1 <> t1
   *       AND s2 <> t2
   *       AND s1 <> t2
   *       AND s2 <> t1
   *
   *       // r, s, and t are unique across iterations
   *       AND r1 <> r2
   *       AND s1 <> s2
   *       AND t1 <> t2
   * }}}
   * And so we get a selectivity of:
   * {{{relationshipUniquenessSelectivity(2, 3, 2) = 0.99 ^ 11 =~ 0.895}}}
   */
  def relationshipUniquenessSelectivity(
    differentRelationships: Int,
    uniqueRelationships: Int,
    repetitions: Int
  ): Selectivity = {
    // If we take 3 different relationships: r, s, and t
    // within each iteration, we have `differentRelationships` predicates, in our example: r <> s, r <> t, s <> t
    // for each pair of iterations, we have 2 * `differentRelationships` predicates, in our example: r1 <> s2, r1 <> t2, s1 <> t2, r2 <> s1, r2 <> t1, s2 <> t1
    // there is `repetitions` choose 2 pairs of iterations, calculated as: `repetitions` * (`repetitions` - 1) / 2
    // giving us a total of: differentRelationships * repetitions + 2 * differentRelationships * repetitions * (repetitions - 1) / 2
    //                       differentRelationships * repetitions + differentRelationships * repetitions * (repetitions - 1)
    //                       differentRelationships * repetitions + differentRelationships * (repetitions ^ 2 - repetitions)
    //                       differentRelationships * (repetitions ^ 2 - repetitions + repetitions)
    //                       differentRelationships * (repetitions ^ 2)
    val differentRelationshipsPredicates = repetitions * repetitions * differentRelationships
    // we also have `uniqueRelationships` predicates for each pair of iterations, for example: r1 <> r2, s1 <> s2, t1 <> t2
    val uniqueRelationshipsPredicates = repetitions * (repetitions - 1) / 2 * uniqueRelationships
    DEFAULT_REL_UNIQUENESS_SELECTIVITY ^ (differentRelationshipsPredicates + uniqueRelationshipsPredicates)
  }

  def nodeUniquenessSelectivity(
    uniqueNodes: Int,
    repetitions: Int
  ): Selectivity = {
    // The first repetition gives `uniqueNodes` new nodes, the remaining repetitions give `uniqueNodes`-1 new nodes,
    // since the first node is already included in the previous iterations.
    val totalNumberOfDifferentNodes =
      uniqueNodes * repetitions - repetitions + 1 // Same as: uniqueNodes + (repetitions - 1) * (uniqueNodes - 1)
    // The first node needs to be compared against `totalNumberOfDifferentNodes-1` other nodes,
    // the second against `totalNumberOfDifferentNodes-2` other nodes,
    // the third against `totalNumberOfDifferentNodes-3` other nodes, etc.
    // Instead of summing a sequence like 5,4,3,2,1 in linear time, we can obtain the sum in constant time.
    // Let's see what happens when we sum the sequence twice, once the original and once in reverse order: 1,2,3,4,5.
    // The sum of the ith element of both sequences in 6 for every i.
    // We can get the sum in constant time: 5 * 6
    // Since we counted the sequence twice, we need to divide by 2.
    // More general: (n * n+1) / 2
    // For us, n = totalNumberOfDifferentNodes-1
    val numberOfNodeInequalityComparisons =
      ((totalNumberOfDifferentNodes - 1) * totalNumberOfDifferentNodes) / 2.0 // Same as: Range.inclusive(1, totalNumberOfDifferentNodes-1).sum
    DEFAULT_NODES_UNIQUENESS_SELECTIVITY ^ (numberOfNodeInequalityComparisons)
  }
}
