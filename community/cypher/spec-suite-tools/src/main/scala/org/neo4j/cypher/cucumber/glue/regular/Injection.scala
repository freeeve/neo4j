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

import com.google.inject.AbstractModule
import com.google.inject.Guice.createInjector
import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.Provider
import com.google.inject.Scopes
import com.google.inject.Stage.PRODUCTION
import io.cucumber.core.backend.ObjectFactory
import io.cucumber.guice.CucumberModules.createScenarioModule
import io.cucumber.guice.ScenarioScope

// Cucumber has its own GuiceFactory similar to this one.
// We use this because it's easier to switch between configurations that way imo.
abstract class SingletonInjector(override val injector: Injector) extends GuiceObjectFactory

trait GuiceObjectFactory extends ObjectFactory {
  private[this] var scenarioScope: ScenarioScope = _

  final override def start(): Unit = {
    scenarioScope = injector.getInstance(classOf[ScenarioScope])
    scenarioScope.enterScope()
  }

  final override def stop(): Unit = {
    if (scenarioScope != null) {
      scenarioScope.exitScope()
      scenarioScope = null
    }
  }
  def injector: Injector
  final override def addClass(glueClass: Class[_]): Boolean = true
  final override def getInstance[T](glueClass: Class[T]): T = injector.getInstance(glueClass)
}

object GuiceObjectFactory {

  def injector(modules: com.google.inject.Module*): Injector = {
    val allModules = createScenarioModule(new ThreadSafeScenarioScope()) +: modules
    createInjector(PRODUCTION, allModules: _*)
  }
}

trait CustomInjectedTestConf {
  protected def createInjector(): Injector

  // This is a workaround of a bug in cucumber: https://github.com/cucumber/cucumber-jvm/issues/2961
  // When running in parallel @Singleton injections do not work as expected in cucumber.
  // Making the injector a singleton forces cucumber to not create multiple injectors.
  // There's a risk with this hack, you can only run one test suite at a time (per injector and JVM).
  final protected lazy val injector: Injector = createInjector()
}

trait InjectedTestConf extends CustomInjectedTestConf {
  val conf: TestConf
  final override protected def createInjector(): Injector = GuiceObjectFactory.injector(new Module(conf))
}

class Module(val conf: TestConf) extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[TestConf]).toInstance(conf)
    bind(classOf[Executors]).toProvider(new ExecutorsProvider(conf)).in(Scopes.SINGLETON)
    bind(classOf[BeforeAndAfterAll]).to(classOf[ExecutorsStartAndShutown])
    bind(classOf[Expectations]).to(classOf[DynamicExpectations]).in(Scopes.SINGLETON)
  }
}

final class NoOpBeforeAndAfterAllModule extends AbstractModule {
  override def configure(): Unit = bind(classOf[BeforeAndAfterAll]).toInstance(NoOpBeforeAndAfterAll)
}

// Workaround to be able to use dependency injection in before/after all hooks.
trait BeforeAndAfterAll {
  def beforeAll(): Unit
  def afterAll(): Unit
}

object NoOpBeforeAndAfterAll extends BeforeAndAfterAll {
  override def beforeAll(): Unit = {}
  override def afterAll(): Unit = {}
}

final class ThreadSafeScenarioScope extends ScenarioScope {

  override def scope[T <: AnyRef](key: Key[T], unscoped: Provider[T]): Provider[T] = () => {
    // This method is not used,
    // but can be implemented like this if needed https://stackoverflow.com/a/44519405/865766
    throw new UnsupportedOperationException(s"Scenario scope is not supported, implement me if needed")
  }

  override def enterScope(): Unit = {}
  override def exitScope(): Unit = {}
}
