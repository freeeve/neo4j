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

import java.util

/**
 * We represent values as close to embedded API as possible.
 * This class contains classes for exceptions where that is not convenient/possible.
 */
object ValueRepresentation {
  sealed trait NoIdEntity

  case class NoIdNode(labels: util.Set[String], properties: util.Map[String, AnyRef]) extends NoIdEntity {

    override def toString: String = {
      val labelsString = if (labels.isEmpty) "" else ":" + String.join(":", labels)
      s"($labelsString$properties)"
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
    override def toString: String = start + connections.mkString("")
  }
}
