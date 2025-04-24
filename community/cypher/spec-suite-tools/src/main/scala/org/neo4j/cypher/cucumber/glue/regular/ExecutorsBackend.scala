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

import io.cucumber.core.backend.Backend
import io.cucumber.core.backend.BackendProviderService
import io.cucumber.core.backend.Container
import io.cucumber.core.backend.Glue
import io.cucumber.core.backend.Lookup
import io.cucumber.core.backend.Snippet
import io.cucumber.scala.ScalaStaticHookDefinition
import io.cucumber.scala.ScalaStaticHookDetails

import java.lang.reflect.Type
import java.net.URI
import java.text.MessageFormat
import java.util
import java.util.function.Supplier

class ExecutorsBackend(lookup: Lookup) extends Backend {

  override def loadGlue(glue: Glue, gluePaths: util.List[URI]): Unit = registerHooks(glue)

  // This is a workaround to be able to access the dependency injector in before/after all hooks.
  // There's no way to do that from regular BeforeAll/AfterAll hooks (that I know of).
  private def registerHooks(glue: Glue): Unit = {
    val hooks = lookup.getInstance(classOf[BeforeAndAfterAll])
    glue.addBeforeAllHook(ScalaStaticHookDefinition(ScalaStaticHookDetails(
      Int.MinValue,
      () => hooks.beforeAll(),
      Thread.currentThread().getStackTrace.head
    )))
    glue.addAfterAllHook(ScalaStaticHookDefinition(ScalaStaticHookDetails(
      Int.MaxValue,
      () => hooks.afterAll(),
      Thread.currentThread().getStackTrace.head
    )))
  }

  override def buildWorld(): Unit = {}

  override def disposeWorld(): Unit = {}

  override def getSnippet: Snippet = new Snippet {
    override def template(): MessageFormat = new MessageFormat("")
    override def tableHint(): String = ""
    override def arguments(arguments: util.Map[String, Type]): String = ""
    override def escapePattern(pattern: String): String = pattern
  }
}

class ExecutorsBackendProvider extends BackendProviderService {

  override def create(lookup: Lookup, container: Container, classLoader: Supplier[ClassLoader]): Backend = {
    new ExecutorsBackend(lookup)
  }
}
