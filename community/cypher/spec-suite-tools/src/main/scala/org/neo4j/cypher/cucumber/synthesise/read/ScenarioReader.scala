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
package org.neo4j.cypher.cucumber.synthesise.read

import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.JUNIT_PLATFORM_LONG_NAMING_STRATEGY_EXAMPLE_NAME_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.JUNIT_PLATFORM_NAMING_STRATEGY_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME
import org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage
import org.junit.platform.launcher.EngineFilter
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.neo4j.cypher.cucumber.synthesise.glue.scenario.RecordedScenario
import org.neo4j.cypher.cucumber.synthesise.glue.scenario.ScenarioRecorder
import org.neo4j.cypher.cucumber.synthesise.glue.scenario.ScenarioRecordingSteps

import java.io.ByteArrayOutputStream
import java.io.PrintWriter

class ScenarioReader {

  def readAllScenarios(featurePaths: String*): Seq[RecordedScenario] = {
    val recorder = new ScenarioRecorder
    require(ScenarioRecordingSteps.recorder.compareAndSet(null, recorder))

    val request = featurePaths
      .foldLeft(LauncherDiscoveryRequestBuilder.request()) {
        case (request, path) => request.selectors(selectPackage(path))
      }
      .filters(EngineFilter.includeEngines("cucumber"))
      .configurationParameter(GLUE_PROPERTY_NAME, classOf[ScenarioRecordingSteps].getPackageName)
      .configurationParameter(JUNIT_PLATFORM_NAMING_STRATEGY_PROPERTY_NAME, "long")
      .configurationParameter(JUNIT_PLATFORM_LONG_NAMING_STRATEGY_EXAMPLE_NAME_PROPERTY_NAME, "number")
      .configurationParameter(PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME, "true")
      .configurationParameter(OBJECT_FACTORY_PROPERTY_NAME, classOf[ScenarioRecordingSteps.ObjectFactory].getName)
      .build()

    val summaryListener = new SummaryGeneratingListener()
    LauncherFactory.create().execute(request, summaryListener)

    if (!summaryListener.getSummary.getFailures.isEmpty) {
      val stream = new ByteArrayOutputStream()
      val writer = new PrintWriter(stream)
      summaryListener.getSummary.printTo(writer)
      summaryListener.getSummary.printFailuresTo(writer, 8)
      throw new RuntimeException("Failed to record Cucumber steps:\n" + stream.toString)
    }

    ScenarioRecordingSteps.recorder.set(null)
    recorder.recordedScenarios
  }

}
