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

import org.neo4j.configuration.Config
import org.neo4j.configuration.GraphDatabaseSettings
import org.neo4j.cypher.cucumber.CypherCucumber.Tag
import org.neo4j.cypher.cucumber.glue.regular.TestConf.Settings

import scala.jdk.CollectionConverters.MapHasAsJava

/**
 * Cypher Cucumber test configuration.
 *
 * @param neo4jConf base neo4j settings, individual scenarios can add to this
 * @param useBolt true if queries should go though driver
 * @param useEnterprise true if enterprise edition should be used
 * @param useSpd true if spd should be used
 * @param preparserOptions pre-parser options
 * @param tagContext base for fails: and ignore: tags
 */
case class TestConf(
  neo4jConf: Settings,
  useBolt: Boolean,
  useEnterprise: Boolean,
  useSpd: Boolean,
  preparserOptions: Map[String, String],
  private val tagContext: Set[String]
) {
  val preparserPrefix: String = TestConf.preParserPrefix(preparserOptions)

  /** Scenarios with these tags are expected to fail. */
  val expectFailureTags: Set[String] = tagContext.map(name => Tag.FailsPrefix + name) + Tag.FailsAll

  /** Scenarios with these tags are not run. */
  val ignoreTags: Set[String] = tagContext.map(name => Tag.IgnorePrefix + name) + Tag.IgnoreAll
}

object TestConf {
  type Settings = Map[String, String]

  def apply(
    neo4jConf: Settings = Map.empty,
    useBolt: Boolean = false,
    useEnterprise: Boolean = true,
    useSpd: Boolean = false,
    preparserOptions: Map[String, String] = Map.empty,
    tagContext: Set[String] = Set.empty
  ): TestConf = {
    val fullNeo4jConf = Seq(
      Some("server.memory.query_cache.per_db_cache_num_entries" -> "64"),
      Option.when(useEnterprise)("server.metrics.enabled" -> "false"),
      Option.when(useBolt)("server.bolt.enabled" -> "true")
    ).flatten.toMap ++ neo4jConf

    // Allow for example @fails:db-format-multiversion and @ignore:db-format-multiversion
    // Note, NEO4J_OVERRIDE_STORE_FORMAT overrides this value in some testing (through FormatOverrideMigrator)
    val dbFormat = Config.newBuilder()
      .setRaw(fullNeo4jConf.view.filterKeys(_ == GraphDatabaseSettings.db_format.name()).toMap.asJava)
      .build()
      .get(GraphDatabaseSettings.db_format)
    val dbFormatTagContext = s"db-format-$dbFormat"

    new TestConf(fullNeo4jConf, useBolt, useEnterprise, useSpd, preparserOptions, tagContext + dbFormatTagContext)
  }

  private def withCypher5(base: TestConf): TestConf = base.copy(
    tagContext = base.tagContext.incl("cypher-5")
  )

  private def withCypher25(base: TestConf): TestConf = base.copy(
    neo4jConf = base.neo4jConf ++ Seq(
      "internal.db.query.default_language" -> "cypher_25",
      "internal.dbms.cypher.enable_experimental_versions" -> "true"
    ),
    tagContext = base.tagContext.incl("cypher-25")
  )

  object Default {
    private def baseConf: TestConf = TestConf()

    object Cypher25 extends InjectedTestConf {
      final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$Default$Cypher25$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher25(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }

    object Cypher5 extends InjectedTestConf {
      final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$Default$Cypher5$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher5(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }
  }

  object DefaultBolt {

    private def baseConf: TestConf = TestConf(
      neo4jConf = Map("server.bolt.enabled" -> "true"),
      useBolt = true,
      tagContext = Set("bolt")
    )

    object Cypher25 extends InjectedTestConf {
      final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$DefaultBolt$Cypher25$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher25(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }

    object Cypher5 extends InjectedTestConf {
      final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$DefaultBolt$Cypher5$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher5(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }
  }

  object Pipelined {

    private def baseConf: TestConf = TestConf(
      preparserOptions = Map("runtime" -> "pipelined"),
      tagContext = Set("pipelined-runtime")
    )

    object Cypher25 extends InjectedTestConf {
      final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$Pipelined$Cypher25$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher25(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }

    object Cypher5 extends InjectedTestConf {
      final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$Pipelined$Cypher5$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher5(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }
  }

  object PipelinedFallback {

    private def baseConf: TestConf = TestConf(
      preparserOptions = Map(
        "runtime" -> "pipelined",
        "interpretedPipesFallback" -> "all"
      ),
      tagContext = Set("pipelined-runtime", "pipelined-fallback")
    )

    object Cypher25 extends InjectedTestConf {

      final val FactoryName =
        "org.neo4j.cypher.cucumber.glue.regular.TestConf$PipelinedFallback$Cypher25$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher25(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }

    object Cypher5 extends InjectedTestConf {

      final val FactoryName =
        "org.neo4j.cypher.cucumber.glue.regular.TestConf$PipelinedFallback$Cypher5$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher5(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }
  }

  object Slotted {

    private def baseConf: TestConf = TestConf(
      preparserOptions = Map("runtime" -> "slotted")
    )

    object Cypher25 extends InjectedTestConf {
      final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$Slotted$Cypher25$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher25(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }

    object Cypher5 extends InjectedTestConf {
      final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$Slotted$Cypher5$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher5(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }
  }

  object SlottedCompiled {

    private def baseConf: TestConf = TestConf(
      preparserOptions = Map(
        "runtime" -> "slotted",
        "expressionEngine" -> "compiled"
      )
    )

    object Cypher25 extends InjectedTestConf {

      final val FactoryName =
        "org.neo4j.cypher.cucumber.glue.regular.TestConf$SlottedCompiled$Cypher25$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher25(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }

    object Cypher5 extends InjectedTestConf {

      final val FactoryName =
        "org.neo4j.cypher.cucumber.glue.regular.TestConf$SlottedCompiled$Cypher5$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher5(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }
  }

  object Parallel {

    private def baseConf: TestConf = TestConf(
      preparserOptions = Map("runtime" -> "parallel"),
      tagContext = Set("parallel-runtime")
    )

    object Cypher25 extends InjectedTestConf {
      final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$Parallel$Cypher25$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher25(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }

    object Cypher5 extends InjectedTestConf {
      final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$Parallel$Cypher5$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher5(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }
  }

  object ParallelBolt {

    private def baseConf: TestConf = TestConf(
      neo4jConf = Map("server.bolt.enabled" -> "true"),
      useBolt = true,
      preparserOptions = Map("runtime" -> "parallel"),
      tagContext = Set("parallel-runtime", "bolt")
    )

    object Cypher25 extends InjectedTestConf {

      final val FactoryName =
        "org.neo4j.cypher.cucumber.glue.regular.TestConf$ParallelBolt$Cypher25$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher25(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }

    object Cypher5 extends InjectedTestConf {
      final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$ParallelBolt$Cypher5$ObjectFactory"
      final override val conf: TestConf = TestConf.withCypher5(baseConf)
      final class ObjectFactory extends SingletonInjector(injector)
    }
  }

  private val spdConf: Map[String, String] = Map(
    "internal.db.query.default_language" -> "cypher_25",
    "internal.dbms.cypher.enable_experimental_versions" -> "true",
    // For unknown reasons multiversion store format override (NEO4J_OVERRIDE_STORE_FORMAT) fails here
    "db.format" -> "block",
    "internal.dbms.extra_lock_verification" -> "false",
    "server.bolt.enabled" -> "true",
    "server.routing.listen_address" -> "127.0.0.1:0",
    "server.routing.advertised_address" -> "127.0.0.1:0",
    "server.cluster.listen_address" -> "127.0.0.1:0",
    "server.cluster.raft.listen_address" -> "127.0.0.1:0",
    "server.cluster.advertised_address" -> "127.0.0.1:0",
    "server.cluster.raft.advertised_address" -> "127.0.0.1:0",
    "db.cluster.catchup.pull_interval" -> "10ms",
    "internal.dbms.single_raft_enabled" -> "true",
    "internal.dbms.replication_enabled" -> "true",
    "internal.initial.dbms.default_database.enable" -> "false",
    "internal.dbms.sharded_property_database.enabled" -> "true"
  )

  object SpdBolt extends InjectedTestConf {
    final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$SpdBolt$ObjectFactory"

    final override val conf: TestConf = TestConf(
      neo4jConf = spdConf,
      useBolt = true,
      useSpd = true,
      tagContext = Set("spd", "cypher-25", "bolt")
    )
    final class ObjectFactory extends SingletonInjector(injector)
  }

  object SpdParallel extends InjectedTestConf {
    final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$SpdParallel$ObjectFactory"

    final override val conf: TestConf = TestConf(
      neo4jConf = spdConf,
      preparserOptions = Map("runtime" -> "parallel"),
      useSpd = true,
      useBolt = true,
      tagContext = Set("spd", "cypher-25", "parallel-runtime", "bolt")
    )
    final class ObjectFactory extends SingletonInjector(injector)
  }

  object Legacy extends InjectedTestConf {
    final val FactoryName = "org.neo4j.cypher.cucumber.glue.regular.TestConf$Legacy$ObjectFactory"

    final override val conf: TestConf = TestConf(
      // Avoid multiversion store format override (NEO4J_OVERRIDE_STORE_FORMAT) in community
      neo4jConf = Map("db.format" -> "aligned"),
      useEnterprise = false,
      preparserOptions = Map("runtime" -> "legacy"),
      tagContext = Set("cypher-5", "legacy-runtime")
    )
    final class ObjectFactory extends SingletonInjector(injector)
  }

  private def preParserPrefix(options: Map[String, String]): String = {
    if (options.isEmpty) ""
    else options
      .map { case (k, v) => s"$k=$v" }
      .mkString("CYPHER ", " ", "\n")
  }
}
