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
package org.neo4j.cypher.cucumber.value

import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.renderer.Configuration
import org.neo4j.cypherdsl.core.renderer.GeneralizedRenderer

import java.util
import java.util.Objects

import scala.jdk.CollectionConverters.ListHasAsScala
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.util.Try

/**
 * We represent values as close to embedded API as possible.
 * This class contains classes for exceptions where that is not convenient/possible.
 */
object ValueRepresentation {
  sealed trait NoIdEntity

  case class NoIdNode(labels: util.Set[String], properties: util.Map[String, AnyRef]) extends NoIdEntity {

    override def toString: String = {
      val labelsString = if (labels.isEmpty) "" else ":" + String.join(":", labels)
      s"($labelsString $properties)"
    }
  }

  case class NoIdRel(relType: String, properties: util.Map[String, AnyRef]) extends NoIdEntity {
    override def toString: String = s"-[:$relType $properties]->"
  }

  case class Connection(rel: NoIdRel, node: NoIdNode, outgoing: Boolean) {

    override def toString: String = {
      if (outgoing) s"-[:${rel.relType} ${rel.properties}]->$node"
      else s"<-[:${rel.relType} ${rel.properties}]-$node"
    }
  }

  case class NoIdPath(start: NoIdNode, connections: Seq[Connection]) {
    override def toString: String = "<" + start + connections.mkString("") + ">"
  }

  final class Renderer {

    private val renderer = org.neo4j.cypherdsl.core.renderer.Renderer.getRenderer(
      Configuration.defaultConfig(),
      classOf[GeneralizedRenderer]
    )

    def render(value: AnyRef): String = value match {
      case e: NoIdEntity => e match {
          case NoIdNode(labels, props) =>
            val labelsString = if (labels.isEmpty) "" else ":" + String.join(":", labels)
            s"($labelsString ${render(props)})"
          case NoIdRel(relType, props) => s"[:$relType ${render(props)}]"
        }
      case NoIdPath(start, conns) => "<" + render(start) + conns.map(render).mkString("") + ">"
      case Connection(rel, node, outgoing) =>
        if (outgoing) s"-${render(rel)}->${render(node)}"
        else s"<-${render(rel)}-${render(node)}"
      case list: java.util.List[_] =>
        list.asScala.view.map(i => render(i.asInstanceOf[AnyRef])).mkString("[", ", ", "]")
      case map: java.util.Map[_, _] =>
        map.asScala.toSeq
          .sortBy { case (key, _) => Objects.toString(key) }
          .view
          .map { case (key, value) => s"$key: ${render(value.asInstanceOf[AnyRef])}" }
          .mkString("{", ", ", "}")
      case _ =>
        Try(renderer.render(Cypher.literalOf(value))).getOrElse(value.toString)
    }
  }
}
