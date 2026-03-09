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
package org.neo4j.cypher.internal.rewriting.rewriters

import org.neo4j.cypher.internal.expressions.AllIterablePredicate
import org.neo4j.cypher.internal.expressions.DifferentNodes
import org.neo4j.cypher.internal.expressions.DifferentRelationships
import org.neo4j.cypher.internal.expressions.Disjoint
import org.neo4j.cypher.internal.expressions.DisjointNodes
import org.neo4j.cypher.internal.expressions.Equals
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.In
import org.neo4j.cypher.internal.expressions.InlinedNoneOfNodesInVarLengthRelationship
import org.neo4j.cypher.internal.expressions.ListLiteral
import org.neo4j.cypher.internal.expressions.NoneIterablePredicate
import org.neo4j.cypher.internal.expressions.NoneOfNodes
import org.neo4j.cypher.internal.expressions.NoneOfRelationships
import org.neo4j.cypher.internal.expressions.Not
import org.neo4j.cypher.internal.expressions.SemanticDirection.BOTH
import org.neo4j.cypher.internal.expressions.SemanticDirection.OUTGOING
import org.neo4j.cypher.internal.expressions.SingleIterablePredicate
import org.neo4j.cypher.internal.expressions.Unique
import org.neo4j.cypher.internal.expressions.UniqueNodes
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.expressions.functions.EndNode
import org.neo4j.cypher.internal.expressions.functions.StartNode
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.Rewriter.TopDownMergeableRewriter
import org.neo4j.cypher.internal.util.topDown

/**
 * Removes [[Disjoint]] and [[Unique]] predicates into expressions that the runtime can evaluate.
 */
case class ElementUniquenessRewriter(anonymousVariableNameGenerator: AnonymousVariableNameGenerator) extends Rewriter
    with TopDownMergeableRewriter {

  private def newAnonymousVariable(pos: InputPosition): Variable = {
    Variable(anonymousVariableNameGenerator.nextName)(
      pos,
      Variable.isIsolatedDefault
    )
  }

  private def disjointPredicateToExpression(x: Expression, y: Expression, pos: InputPosition) = {
    val innerX = newAnonymousVariable(x.position)
    NoneIterablePredicate(
      innerX,
      x,
      Some(In(innerX.copyId, y)(pos))
    )(pos)
  }

  private def uniquePredicateToExpression(list: Expression, pos: InputPosition) = {
    val element1 = newAnonymousVariable(list.position)
    val element2 = newAnonymousVariable(list.position)
    AllIterablePredicate(
      element1,
      list,
      Some(SingleIterablePredicate(
        element2,
        list.endoRewrite(copyVariables),
        Some(Equals(element1.copyId, element2.copyId)(list.position))
      )(pos))
    )(pos)
  }

  private def noneOfPredicateToExpression(element: Expression, list: Expression, pos: InputPosition) = {
    Not(In(element, list)(pos))(pos)
  }

  private def differentPredicateToExpression(element1: Expression, element2: Expression, pos: InputPosition) = {
    Not(Equals(element1, element2)(pos))(pos)
  }

  override val innerRewriter: Rewriter = Rewriter.lift {
    case d @ Disjoint(x, y) =>
      disjointPredicateToExpression(x, y, d.position)

    case d @ DisjointNodes(x, y) =>
      disjointPredicateToExpression(x, y, d.position)

    case u @ Unique(list) =>
      uniquePredicateToExpression(list, u.position)

    case u @ UniqueNodes(nodeList, _) =>
      uniquePredicateToExpression(nodeList, u.position)

    case p @ NoneOfRelationships(relationship, relationshipList) =>
      noneOfPredicateToExpression(relationship, relationshipList, p.position)

    case p @ NoneOfNodes(node, nodeList) =>
      noneOfPredicateToExpression(node, nodeList, p.position)

    case p @ InlinedNoneOfNodesInVarLengthRelationship(
        node,
        varLengthRelationshipVariable,
        varLengthRelDirection
      ) =>
      val pos = p.position
      if (varLengthRelDirection == BOTH) {
        // NOT node IN [startNode(varLengthRelationshipVariable), endNode(varLengthRelationshipVariable)]
        // When it would not be inlined, we would need something like
        //   none(innerRel IN varLengthRelationshipVariable WHERE node IN [startNode(innerRel), endNode(innerRel)])
        Not(
          In(
            node,
            ListLiteral(Seq(
              StartNode(varLengthRelationshipVariable)(pos),
              EndNode(varLengthRelationshipVariable)(pos)
            ))(pos)
          )(pos)
        )(pos)
      } else {
        // NOT node = right(varLengthRelationshipVariable)
        // Using this, we will miss a check for the left outer boundary node.
        // This will be handled by a separate DifferentNodes predicate.
        //   The boundary nodes of a VarExpand have the same equivalence class as the VarExpand.
        //   The node has a different equivalence class that the VarExpand.
        //   Therefore, the node will have a different equivalence class than the boundary nodes.
        //   Nodes with different equivalence classes will lead to the generation of DifferentNodes predicates.
        //
        // Using endNode instead of startNode, or the other way around would be equivalent.
        // We just need to include all inner nodes of the var-length relationship and may include the boundary nodes.
        // We chose `right`, i.e. endNode for outgoing relationship and startNode for incoming relationship, to allow
        // pruning to happen one iteration earlier.
        // When the predicate would not be inlined we would need something like this:
        //   NOT node in ([innerRel IN varLengthRelationshipVariable | startNode(innerRel)])
        Not(
          Equals(
            node,
            if (varLengthRelDirection == OUTGOING)
              EndNode(varLengthRelationshipVariable)(pos)
            else {
              // INCOMING
              StartNode(varLengthRelationshipVariable)(pos)
            }
          )(pos)
        )(pos)
      }

    case p @ DifferentRelationships(rel1, rel2) =>
      differentPredicateToExpression(rel1, rel2, p.position)

    case p @ DifferentNodes(node1, node2) =>
      differentPredicateToExpression(node1, node2, p.position)
  }

  private val instance = topDown(innerRewriter)

  override def apply(value: AnyRef): AnyRef = instance(value)
}
