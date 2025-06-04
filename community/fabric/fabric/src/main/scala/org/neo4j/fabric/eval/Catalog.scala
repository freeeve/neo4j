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
package org.neo4j.fabric.eval

import org.neo4j.configuration.helpers.NormalizedGraphName
import org.neo4j.cypher.internal.ast.CatalogName
import org.neo4j.exceptions.EntityNotFoundException
import org.neo4j.fabric.eval.Catalog.Graph
import org.neo4j.fabric.eval.Catalog.normalize
import org.neo4j.fabric.util.Errors
import org.neo4j.fabric.util.Errors.show
import org.neo4j.graphdb.InputPosition
import org.neo4j.internal.kernel.api.security.SecurityContext
import org.neo4j.kernel.api.QueryLanguage
import org.neo4j.kernel.database.DatabaseReference
import org.neo4j.kernel.database.DatabaseReferenceImpl
import org.neo4j.kernel.database.DatabaseReferenceImpl.External
import org.neo4j.kernel.database.NormalizedCatalogEntry
import org.neo4j.kernel.database.NormalizedDatabaseName
import org.neo4j.notifications.NotificationCodeWithDescription
import org.neo4j.notifications.NotificationImplementation
import org.neo4j.values.AnyValue
import org.neo4j.values.ElementIdDecoder
import org.neo4j.values.storable.StringValue

import java.util.UUID

import scala.jdk.OptionConverters.RichOptional

object Catalog {

  case class GraphWithNotification(graph: Graph, notification: Option[NotificationImplementation])

  sealed trait Entry

  sealed trait Graph extends Entry {
    def id: Long
    def reference: DatabaseReference
    def uuid: UUID = reference.id()
    def name: NormalizedGraphName = toGraphName(reference.alias())
    def namespace: Option[NormalizedGraphName] = reference.namespace().toScala.map(toGraphName)
  }

  sealed trait Alias extends Graph

  case class InternalAlias(
    id: Long,
    reference: DatabaseReferenceImpl.Internal
  ) extends Alias

  case class ExternalAlias(
    id: Long,
    reference: DatabaseReferenceImpl.External
  ) extends Alias

  case class Composite(
    id: Long,
    reference: DatabaseReferenceImpl.Composite
  ) extends Graph {
    override def namespace: Option[NormalizedGraphName] = None
  }

  private def toGraphName(name: NormalizedDatabaseName) =
    new NormalizedGraphName(name.name())

  trait View extends Entry {
    val arity: Int
    val signature: Seq[Arg[_]]

    def eval(
      args: Seq[AnyValue],
      catalog: Catalog,
      sessionDb: DatabaseReference,
      resolveByDisplayName: Option[Boolean],
      cypher25Enabled: Boolean
    ): GraphWithNotification

    def checkArity(args: Seq[AnyValue]): Unit =
      if (args.size != arity) wrongArity(args)

    def wrongArity(args: Seq[AnyValue]): Unit

    def cast[T <: AnyValue](a: Arg[T], v: AnyValue, args: Seq[AnyValue]): T =
      try a.tpe.cast(v)
      catch {
        case _: ClassCastException =>
          Errors.invalidType(show(args), a.tpe.getSimpleName, v.getTypeName, show(signature))
      }
  }

  abstract class View1[A1 <: AnyValue](a1: Arg[A1]) extends View {
    val arity: Int = 1
    val signature: Seq[Arg[A1]] = Seq(a1)

    def eval(
      args: Seq[AnyValue],
      catalog: Catalog,
      sessionDb: DatabaseReference,
      resolveByDisplayName: Option[Boolean],
      cypher25Enabled: Boolean
    ): GraphWithNotification = {
      checkArity(args)
      eval(cast(a1, args(0), args), catalog, sessionDb, resolveByDisplayName, cypher25Enabled)
    }

    def eval(
      a1Value: A1,
      catalog: Catalog,
      sessionDb: DatabaseReference,
      resolveByDisplayName: Option[Boolean],
      cypher25Enabled: Boolean
    ): GraphWithNotification

  }

  case class Arg[T <: AnyValue](name: String, tpe: Class[T])

  def create(
    graphAliases: Seq[Alias],
    composites: Seq[(Composite, Seq[Alias])]
  ): Catalog = {
    val databasesAndAliases = byQualifiedName(graphAliases)
    val compositesAndAliases = composites.foldLeft(Catalog.empty) { case (catalog, (composite, aliases)) =>
      val byName = byQualifiedName(composite +: aliases)
      catalog ++ byName
    }

    databasesAndAliases ++ compositesAndAliases ++ graphByNameView ++ graphByElementIdView
  }

  def empty: Catalog = Catalog(Map())

  def byQualifiedName(graphs: Seq[Graph]): Catalog =
    Catalog(graphs = graphs.map(graph => catalogName(graph) -> graph).toMap)

  def catalogName(graph: Graph): CatalogName =
    graph.namespace match {
      case Some(ns) => CatalogName(true, ns.name(), graph.name.name())
      case None     => CatalogName(true, graph.name.name())
    }

  private val graphByNameView: Catalog = {
    Catalog(
      views = Map(normalizedName("graph", "byName") -> new ByNameView())
    )
  }

  private class ByNameView() extends View1(Arg("name", classOf[StringValue])) {

    override def eval(
      arg: StringValue,
      catalog: Catalog,
      sessionDb: DatabaseReference,
      resolveByDisplayName: Option[Boolean],
      cypher25Enabled: Boolean
    ): GraphWithNotification = {
      val graphName = arg.stringValue()
      if (resolveByDisplayName.isDefined && resolveByDisplayName.get) {
        val resolved = catalog.resolveGraphByNameString(graphName, simplified = true)
        GraphWithNotification(resolved, None)
      } else {
        val resolved = catalog.resolveGraphByNameString(graphName, simplified = false)
        val needsDeprecation =
          try {
            val cypher25Resolved = catalog.resolveGraphByNameString(graphName, simplified = true)
            !resolved.equals(cypher25Resolved)
          } catch {
            case _: Throwable => true
          }
        val notification = if (needsDeprecation && cypher25Enabled) {
          Some(NotificationCodeWithDescription.deprecatedQuotedGraphByNameArgument(
            InputPosition.empty,
            graphName,
            resolved.reference.fullName().name()
          ))
        } else {
          None
        }
        GraphWithNotification(resolved, notification)
      }
    }

    def wrongArity(args: Seq[AnyValue]): Unit =
      Errors.wrongArity(
        arity,
        args.size,
        "byName",
        "(" + this.signature.map[String](arg => show(arg)).mkString(",") + ")"
      )
  }

  private val graphByElementIdView: Catalog = {
    Catalog(
      views = Map(normalizedName("graph", "byElementId") -> new ByElementIdView())
    )
  }

  private class ByElementIdView() extends View1(Arg("elementId", classOf[StringValue])) {

    override def eval(
      arg: StringValue,
      catalog: Catalog,
      sessionDb: DatabaseReference,
      parseArguments: Option[Boolean],
      cypher25Enabled: Boolean
    ): GraphWithNotification = {
      val elementIdText = arg.stringValue()
      val aliases = catalog.resolveNamespacedGraph(
        sessionDb.alias().name(),
        ElementIdDecoder.database(elementIdText),
        SecurityContext.AUTH_DISABLED
      ) // TODO: fix!
      if (aliases.isEmpty) {
        throw EntityNotFoundException.databaseWithElementIdNotFound(elementIdText)
      }
      GraphWithNotification(catalog.resolveGraphByNameString(aliases.head, simplified = false), None)
    }

    override def wrongArity(args: Seq[AnyValue]): Unit =
      Errors.wrongArity(
        arity,
        args.size,
        "byElementId",
        "(" + this.signature.map[String](arg => show(arg)).mkString(",") + ")"
      )
  }

  private def normalize(graphName: String): String =
    new NormalizedGraphName(graphName).name()

  private def normalize(name: CatalogName): CatalogName =
    CatalogName(name.parts.map(normalize), resolveByDisplayName = true)

  private def normalizedName(parts: String*): CatalogName =
    normalize(CatalogName(true, parts: _*))
}

case class Catalog(
  graphs: Map[CatalogName, Catalog.Graph] = Map(),
  views: Map[CatalogName, Catalog.View] = Map()
) {

  def resolveGraph(name: CatalogName): Catalog.Graph = {
    resolveGraphOption(name)
      .getOrElse(throw EntityNotFoundException.databaseNotFound("Graph", name.qualifiedNameString))
  }

  def resolveGraphOption(name: CatalogName): Option[Catalog.Graph] = {
    if (name.resolveByDisplayName) {
      resolveGraphOptionByNameString(name.simplifiedQualifiedNameString, resolveByDisplayName = true)
    } else {
      graphs.get(normalize(name))
    }
  }

  def resolveGraphByNameString(name: String, simplified: Boolean): Catalog.Graph = {
    val resolvedGraph = resolveGraphOptionByNameString(name, simplified: Boolean)
    if (resolvedGraph.isDefined)
      resolvedGraph.get
    else
      throw EntityNotFoundException.databaseNotFound("Graph", name)
  }

  def resolveGraphByDisplayName(name: NormalizedCatalogEntry): Catalog.Graph = {
    val catalogName = if (name.compositeDb().isPresent) {
      CatalogName(List(name.compositeDb().get(), name.databaseAlias()), resolveByDisplayName = true)
    } else {
      CatalogName(List(name.databaseAlias()), resolveByDisplayName = true)
    }
    resolveGraphOption(catalogName)
      .getOrElse(throw EntityNotFoundException.databaseNotFound("Graph", name.stringRepresentation()))
  }

  def resolveGraphByNameString(name: String, securityContext: SecurityContext, cypherVersion: QueryLanguage): Graph =
    resolveGraphOptionByNameString(name, securityContext, cypherVersion)
      .getOrElse(throw EntityNotFoundException.databaseNotFound("Graph", name))

  private def resolveGraphOptionByNameString(
    name: String,
    securityContext: SecurityContext,
    cypherVersion: QueryLanguage
  ): Option[Graph] = {
    val normalizedName = Catalog.normalize(name)
    if (cypherVersion.equals(QueryLanguage.CYPHER_5)) {
      graphs.collectFirst {
        case (cn, graph) if cn.qualifiedNameString == normalizedName && canAccessDatabase(graph, securityContext) =>
          graph
      }
    } else {
      graphs.collectFirst {
        case (cn, graph)
          if cn.simplifiedQualifiedNameString == normalizedName && canAccessDatabase(graph, securityContext) =>
          graph
      }
    }
  }

  private def resolveGraphOptionByNameString(name: String, resolveByDisplayName: Boolean): Option[Catalog.Graph] = {
    val normalizedName = Catalog.normalize(name)
    val result = graphs.collectFirst {
      case (cn, graph) if resolveByDisplayName && cn.simplifiedQualifiedNameString == normalizedName => graph
      case (cn, graph) if !resolveByDisplayName && cn.qualifiedNameString == normalizedName          => graph
    }
    result
  }

  def resolveView(
    name: CatalogName,
    args: Seq[AnyValue],
    sessionDb: DatabaseReference,
    resolveByDisplayName: Option[Boolean],
    cypher25Enabled: Boolean
  ): Catalog.GraphWithNotification =
    resolveViewOption(
      name,
      args,
      sessionDb,
      resolveByDisplayName,
      cypher25Enabled
    ).getOrElse(throw EntityNotFoundException.databaseNotFound(
      "View",
      show(name)
    ))

  private def resolveViewOption(
    name: CatalogName,
    args: Seq[AnyValue],
    sessionDb: DatabaseReference,
    resolveByDisplayName: Option[Boolean],
    cypher25Enabled: Boolean
  ): Option[Catalog.GraphWithNotification] =
    views.get(normalize(name)).map(v => v.eval(args, this, sessionDb, resolveByDisplayName, cypher25Enabled))

  def graphNamesIn(namespace: String, securityContext: SecurityContext, queryLanguage: QueryLanguage): Array[String] = {
    graphs.collect {
      case (cn @ CatalogName(List(`namespace`, _), true), graph: Catalog.Graph)
        if canAccessDatabase(graph, securityContext) =>
        if (queryLanguage == QueryLanguage.CYPHER_25) {
          cn.simplifiedQualifiedNameString
        } else {
          cn.qualifiedNameString
        }
    }.toArray
  }

  def resolveNamespacedGraph(namespace: String, database: UUID, securityContext: SecurityContext): Array[String] = {
    graphs.collect {
      case (cn @ CatalogName(List(`namespace`, _), true), graph: Catalog.Graph)
        if canAccessDatabase(graph, securityContext) && graph.uuid.equals(database) =>
        cn.qualifiedNameString
    }.toArray
  }

  def nameById(databaseId: UUID, securityContext: SecurityContext): Option[String] = {
    val alias = findPrimaryAlias(databaseId)
    val canSeeDb = ref => securityContext.databaseAccessMode.canSeeDatabase(ref)

    alias.filter(canSeeDb).map(_.alias().name())
  }

  private def canAccessDatabase(graph: Catalog.Graph, securityContext: SecurityContext): Boolean = {
    val hasAccessToDb = ref => securityContext.databaseAccessMode.canAccessDatabase(ref)

    graph.reference.isInstanceOf[External] && hasAccessToDb(graph.reference) || findPrimaryAlias(
      graph.reference.id
    ).exists(hasAccessToDb)
  }

  private def findPrimaryAlias(uuid: UUID): Option[DatabaseReference] = {
    graphs
      .map { case (_, graph) => graph.reference }
      .find { ref => ref.id().equals(uuid) && ref.isPrimary }
  }

  def ++(that: Catalog): Catalog = Catalog(this.graphs ++ that.graphs, this.views ++ that.views)

}
