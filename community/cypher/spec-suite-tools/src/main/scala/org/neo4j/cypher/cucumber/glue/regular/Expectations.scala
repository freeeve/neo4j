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
import org.neo4j.cypher.cucumber.glue.regular.Expectations.DynamicTag
import org.neo4j.cypher.cucumber.glue.regular.Expectations.readDynamicTags

import scala.io.Source
import scala.util.Using

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

  private[this] val dynamicTagsByScenarioName: Map[String, Seq[DynamicTag]] = readDynamicTags()
    .filter(t => failTags.contains(t.tag) || ignoreTags.contains(t.tag))
    .groupBy(_.scenarioName)

  override def fails(scenario: Scenario): Boolean = scenarioHasAnyTag(scenario, failTags)
  override def ignore(scenario: Scenario): Boolean = scenarioHasAnyTag(scenario, ignoreTags)

  private def scenarioHasAnyTag(scenario: Scenario, targetTags: Set[String]): Boolean = {
    val tags = scenario.getSourceTagNames
    if (!tags.isEmpty && tags.stream().anyMatch(t => targetTags.contains(t))) true
    else if (dynamicTagsByScenarioName.contains(scenario.getName)) {
      val featurePath = scenario.getUri.getSchemeSpecificPart
      dynamicTagsByScenarioName(scenario.getName).exists { dynamicTag =>
        targetTags.contains(dynamicTag.tag) &&
        featurePath.endsWith(dynamicTag.path) && // Multiple features can have the same scenario name
        dynamicTag.line.forall(_ == scenario.getLine) // Examples are identified with their line number
      }
    } else false
  }
}

object Expectations {
  val ExternalTagResource = "/cypher/features/tck/tags/tck-tags.txt"

  case class DynamicTag(tag: String, scenarioName: String, path: String, line: Option[Long]) {
    def render: String = s"$tag $path${line.map(l => "@" + l).getOrElse("")}:$scenarioName"
  }

  def readDynamicTags(): Seq[Expectations.DynamicTag] = {
    val tagPattern = """^(@\S+) ([^:@]+)(@\d+)?:(.*)$""".r

    Option(getClass.getResourceAsStream(ExternalTagResource)) match {
      case Some(stream) => Using.resource(Source.fromInputStream(stream)) { source =>
          source.getLines()
            .filterNot(l => l.isBlank || l.startsWith("//"))
            .map {
              case tagPattern(tag, path, line, name) =>
                DynamicTag(tag, name, path, Option(line).map(_.substring(1).toInt))
              case other => throw new IllegalArgumentException(s"Could not parse: " + other)
            }
            .toSeq
        }
      case None => Seq.empty
    }
  }
}
