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
package org.neo4j.cypher.cucumber.glue.regular

import com.google.inject.Inject
import io.cucumber.scala.Scenario

trait Expectations {

  /** Returns true if scenario is expected to fail. */
  def fails(scenario: Scenario): Boolean

  /** Returns true if scenario should be ignored. */
  def ignore(scenario: Scenario): Boolean
}

@com.google.inject.Singleton
final class DynamicExpectations @Inject() (conf: TestConf) extends Expectations {

  private[this] val failTags: Set[String] = conf.expectFailureTags
  private[this] val ignoreTags: Set[String] = conf.ignoreTags

  override def fails(scenario: Scenario): Boolean = scenarioHasAnyTag(scenario, failTags)
  override def ignore(scenario: Scenario): Boolean = scenarioHasAnyTag(scenario, ignoreTags)

  private def scenarioHasAnyTag(scenario: Scenario, targetTags: Set[String]): Boolean = {
    val tags = scenario.getSourceTagNames
    !tags.isEmpty && tags.stream().anyMatch(t => targetTags.contains(t))
  }
}
