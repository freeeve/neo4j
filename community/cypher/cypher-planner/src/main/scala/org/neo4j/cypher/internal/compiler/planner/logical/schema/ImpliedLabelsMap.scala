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
package org.neo4j.cypher.internal.compiler.planner.logical.schema

import scala.annotation.tailrec

final case class ImpliedLabelsMap(labelToImpliedLabels: Map[String, Set[String]]) {

  def pruneImpliedLabels(labels: Set[String]): Set[String] = {
    labels.foldLeft(labels) {
      (prunedLabels, label) =>
        if (prunedLabels.contains(label)) {
          prunedLabels
            .diff(labelToImpliedLabels.getOrElse(label, Set.empty))
            .incl(label)
        } else {
          prunedLabels
        }
    }
  }
}

object ImpliedLabelsMap {

  def forLabels(relevantLabels: Set[String], getDirectImpliedLabels: String => Set[String]): ImpliedLabelsMap = {
    val inferred: Set[(String, String)] =
      inferImpliedLabels(Set.empty, relevantLabels.map(l => l -> l), getDirectImpliedLabels, 0)
    val grouped: Map[String, Set[String]] =
      inferred.groupMap(_._1)(_._2)
    ImpliedLabelsMap(grouped)
  }

  val INFERENCE_ITERATION_LIMIT = 32

  // A "fact" is a tuple of "label -> implied Label"
  @tailrec
  private def inferImpliedLabels(
    knownFacts: Set[(String, String)],
    previousIterationFacts: Set[(String, String)],
    getDirectImpliedLabels: String => Set[String],
    iteration: Int
  ): Set[(String, String)] = {
    if (iteration >= INFERENCE_ITERATION_LIMIT) {
      knownFacts
    } else {
      val currentIterationFacts = previousIterationFacts.flatMap {
        case (label, impliedLabel) =>
          val newImpliedLabels = getDirectImpliedLabels(impliedLabel)
          newImpliedLabels.map(label -> _)
      }

      val newFacts = currentIterationFacts -- knownFacts
      if (newFacts.isEmpty) {
        knownFacts
      } else {
        inferImpliedLabels(knownFacts ++ newFacts, newFacts, getDirectImpliedLabels, iteration + 1)
      }
    }
  }
}
