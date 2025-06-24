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
package org.neo4j.cypher.cucumber

/**
 * Configuration constants for Cypher Cucumber (.feature) tests.
 */
object CypherCucumber {

  final object Tag {

    /**
     * Scenario needs an extra neo4j settings.
     * Example: @conf:internal.cypher.enable_extra_semantic_features=MatchMode
     */
    final val ConfPrefix = "@conf:"

    /** Scenario is expected to fail in all configurations. */
    final val FailsAll = "@fails"

    /**
     * Scenario is expected to fail in a certain named configuration.
     * Example: @fails:parallel-runtime
     */
    final var FailsPrefix = "@fails:"

    /** Scenario is ignored in all configrations. Avoid when possible! */
    final val IgnoreAll = "@ignore"

    /**
     * Scenario is ignored in a certain configuration.
     * Prefer @fails:... when possible!
     * Example: @ignore:parallel-runtime
     */
    final val IgnorePrefix = "@ignore:"
  }

  /** The glue path for different ways of running Cypher Cucumber tests. */
  final object Glue {

    /**
     * This is the normal way of running Cypher cucumber tests.
     * Ignores scenarios that are expected to fail (has @fails... tag).
     */
    final val IgnoreFailTaggedScenarios =
      "org.neo4j.cypher.cucumber.glue.regular,org.neo4j.cypher.cucumber.glue.fails.ignore"

    /**
     * This is a special configuration to only run scenarios that are expected to fail.
     * Used to verify that the @fails... tags are correct.
     */
    final val IgnorePassingScenarios =
      "org.neo4j.cypher.cucumber.glue.regular,org.neo4j.cypher.cucumber.glue.fails.only"

    /**
     * This is special glue that only test the prettifier.
     * No queries or normal assertions are run.
     * Finds query strings in all scenarios and test the prettifier with them.
     */
    final val Prettifier = "org.neo4j.cypher.cucumber.glue.prettifier"

    /**
     * This is special glue that only test query log obfuscation.
     */
    final val Obfuscator = "org.neo4j.cypher.cucumber.glue.obfuscator"
  }
}
