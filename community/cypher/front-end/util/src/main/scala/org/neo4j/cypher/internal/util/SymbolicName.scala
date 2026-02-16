/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.util

import java.util.Locale

trait SymbolicName extends ASTNode {
  def name: String
  def position: InputPosition
  override def asCanonicalStringVal: String = name
}

case class Namespace(parts: List[String] = List.empty)(val position: InputPosition) extends ASTNode {

  def equalsIgnoreCase(that: Namespace): Boolean =
    parts.size == that.parts.size &&
      parts.zip(that.parts).forall {
        case (thisPart, thatPart) => thisPart.equalsIgnoreCase(thatPart)
      }
}

sealed trait CallableName extends SymbolicName {
  def namespace: Namespace
  def name: String

  def fullName: String = namespace.parts.map(_ + ".").mkString("", "", name)

  def fullNameEqual(that: CallableName): Boolean = name == that.name && namespace == that.namespace

  def fullNameCaseInsensitiveEqual(that: CallableName): Boolean =
    name.equalsIgnoreCase(that.name) && namespace.equalsIgnoreCase(that.namespace)
}

object CallableName {

  def unapply(callableName: CallableName): Option[(Namespace, String)] =
    Some((callableName.namespace, callableName.name))
}

case class ProcedureName(namespace: Namespace, name: String)(val position: InputPosition) extends CallableName

case class FunctionName(namespace: Namespace, name: String)(val position: InputPosition) extends CallableName {

  override def equals(x: Any): Boolean = x match {
    case FunctionName(otherNamespace, otherName) =>
      otherNamespace == namespace && otherName.toLowerCase(Locale.ROOT) == name.toLowerCase(Locale.ROOT)
    case _ => false
  }
  override def hashCode: Int = name.toLowerCase(Locale.ROOT).hashCode
}

object FunctionName {

  def apply(name: String)(position: InputPosition): FunctionName = {
    FunctionName(Namespace()(position), name)(position)
  }
}

case class ProcedureOutput(name: String)(val position: InputPosition) extends SymbolicName
