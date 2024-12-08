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
package cypher.features

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.util.test_helpers.FeatureTest
import org.opencypher.tools.tck.api.CypherTCK
import org.opencypher.tools.tck.api.Scenario

abstract class BaseFeatureTest extends FeatureTest with ScenarioTestHelper {

  def filterScenarios(
    allScenarios: Seq[Scenario],
    categoryToRun: String,
    featureToRun: String,
    scenarioToRun: String
  ): Seq[Scenario] = {
    if (categoryToRun.isEmpty && featureToRun.isEmpty && scenarioToRun.isEmpty) allScenarios
    else allScenarios
      .filter(s => categoryToRun.isEmpty || s.categories.exists(c => c.contains(categoryToRun)))
      .filter(s => featureToRun.isEmpty || s.featureName.contains(featureToRun))
      .filter(s => scenarioToRun.isEmpty || s.name.contains(scenarioToRun))
  }
}

object BaseFeatureTestHolder {

  lazy val allTckScenarios: Seq[Scenario] = CypherTCK.allTckScenarios

  def acceptanceScenarios(version: CypherVersion): Seq[Scenario] = version match {
    case CypherVersion.Cypher25 => cypher25AcceptanceScenarios
    case CypherVersion.Cypher5  => cypher5AcceptanceScenarios
  }

  private lazy val cypher5AcceptanceScenarios: Seq[Scenario] =
    loadScenariosFromResources("/acceptance/features")

  private lazy val cypher25AcceptanceScenarios: Seq[Scenario] =
    cypher5AcceptanceScenarios ++ loadScenariosFromResources("/cypher25/acceptance/features")

  private def loadScenariosFromResources(path: String): Seq[Scenario] =
    CypherTCK.parseFeatures(getClass.getResource(path).toURI).flatMap(_.scenarios)
}
