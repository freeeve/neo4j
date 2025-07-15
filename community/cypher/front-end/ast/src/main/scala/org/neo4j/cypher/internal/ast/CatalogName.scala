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
package org.neo4j.cypher.internal.ast

import org.neo4j.cypher.internal.ast.CatalogName.quote
import org.neo4j.cypher.internal.ast.CatalogName.separatorChar
import org.neo4j.cypher.internal.ast.CatalogName.separatorString
import org.neo4j.kernel.database.NormalizedCatalogEntry

import java.util

import scala.jdk.CollectionConverters.SeqHasAsJava

object CatalogName {

  def apply(head: String, tail: List[String], resolveStrictly: Boolean): CatalogName = {
    CatalogName(head :: tail, resolveStrictly)
  }

  def apply(resolveStrictly: Boolean, parts: String*): CatalogName = {
    CatalogName(parts.head, parts.tail.toList, resolveStrictly)
  }

  /** Java helper */
  def of(part: String, resolveStrictly: Boolean): CatalogName = CatalogName(resolveStrictly, part)

  val separatorChar: Char = '.'
  val separatorString: String = separatorChar.toString
  private val quoteChar = "`"

  def quote(str: String): String = quoteChar ++ str.replace("`", "``") ++ quoteChar
}

case class CatalogName(parts: List[String], resolveByDisplayName: Boolean) {

  def simplifiedQualifiedNameString: String =
    parts
      .mkString(separatorString)

  /**
   * @return the catalog name used in catalog lookups
   */
  def qualifiedNameString: String =
    parts
      .map(part => if (part.contains(separatorChar)) quote(part) else part)
      .mkString(separatorString)

  /**
   * @return the catalog name guaranteed to be parsed in a Cypher statement
   */
  def asCanonicalNameString: String =
    if (resolveByDisplayName) {
      quote(parts.mkString(separatorString))
    } else {
      parts
        .map(quote)
        .mkString(separatorString)
    }

  override def equals(obj: Any): Boolean = {
    obj match {
      case name: CatalogName =>
        name.qualifiedNameString.toLowerCase.equals(
          this.qualifiedNameString.toLowerCase
        ) && name.resolveByDisplayName == this.resolveByDisplayName
      case _ =>
        false
    }
  }

  def names(): util.List[String] = {
    parts.asJava
  }

  override def hashCode(): Int = qualifiedNameString.toLowerCase.hashCode

  /**
   * @return the catalog name as a normalized catalog entry
   * @throws if there are more than 2 catalog parts
   */
  def toCatalogEntry: NormalizedCatalogEntry = NormalizedCatalogEntry.fromList(parts.asJava)
}
