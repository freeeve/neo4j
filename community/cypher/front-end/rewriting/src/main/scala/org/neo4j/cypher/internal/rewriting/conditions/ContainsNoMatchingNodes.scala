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
package org.neo4j.cypher.internal.rewriting.conditions

import org.neo4j.cypher.internal.rewriting.LogicalPlanValidatingCondition
import org.neo4j.cypher.internal.rewriting.StatementValidatingCondition
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.CancellationChecker
import org.neo4j.cypher.internal.util.Foldable.FoldableAny
import org.neo4j.cypher.internal.util.InputPosition

trait ContainsNoMatchingStatementNodes extends StatementValidatingCondition {

  val matcher: PartialFunction[ASTNode, String]

  override def check(that: Any)(cancellationChecker: CancellationChecker): Seq[String] =
    ContainsNoCheck.check(that, matcher)(cancellationChecker)

  override def hashCode(): Int = name.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case vc: ContainsNoMatchingStatementNodes => this.name.equals(vc.name)
    case _                                    => false
  }
}

trait ContainsNoMatchingPlanNodes extends LogicalPlanValidatingCondition[AnyRef] {

  val matcher: PartialFunction[ASTNode, String]

  override def check(that: Any)(cancellationChecker: CancellationChecker): Seq[String] =
    ContainsNoCheck.check(that, matcher)(cancellationChecker)

  override def hashCode(): Int = name.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case vc: ContainsNoMatchingPlanNodes => this.name.equals(vc.name)
    case _                               => false
  }

  override def targetClass: Class[AnyRef] = classOf[AnyRef]
}

object ContainsNoCheck {

  def check(
    that: Any,
    matcher: PartialFunction[ASTNode, String]
  )(cancellationChecker: CancellationChecker): Seq[String] = {
    that.folder(cancellationChecker).fold(Seq.empty[(String, InputPosition)]) {
      case node: ASTNode if matcher.isDefinedAt(node) =>
        acc => acc :+ ((matcher(node), node.position))
    }.map { case (name, position) => s"Expected none but found $name at position $position" }
  }
}
