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

import org.neo4j.cypher.internal.ast.GraphTypeConstraint.ExistenceConstraint
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.KeyConstraint
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.PropertyTypeConstraint
import org.neo4j.cypher.internal.ast.GraphTypeConstraint.UniquenessConstraint
import org.neo4j.cypher.internal.ast.GraphTypeConstraintKey.NODE
import org.neo4j.cypher.internal.expressions.Property

case class GraphTypeConstraintKey(
  elementType: String,
  element: Option[String],
  properties: String,
  constraintType: String
) {

  override def toString(): String = {
    val elementName = element.getOrElse("")
    if (elementType == NODE) {
      s"'(:$elementName$properties) $constraintType'"
    } else {
      s"'()-[:$elementName$properties]-() $constraintType'"
    }
  }

}

object GraphTypeConstraintKey {
  private val NODE = "NODE"
  private val EDGE = "REL"
  private val EXISTS = "EXISTS"
  private val TYPE = "TYPE"
  private val NONE = "—"

  def apply(elementTypeConstraint: GraphTypeConstraintDefinition): GraphTypeConstraintKey = {
    elementTypeConstraint match {
      case GraphTypeConstraintDefinition(_, nodeType: NodeTypeReference, KeyConstraint(properties), _) =>
        GraphTypeConstraintKey(NODE, getIdentifyingLabel.lift(nodeType), getPropertyNamesString(properties), NONE)
      case GraphTypeConstraintDefinition(_, nodeType: NodeTypeReference, UniquenessConstraint(properties), _) =>
        GraphTypeConstraintKey(NODE, getIdentifyingLabel.lift(nodeType), getPropertyNamesString(properties), NONE)
      case GraphTypeConstraintDefinition(_, nodeType: NodeTypeReference, ExistenceConstraint(properties), _) =>
        GraphTypeConstraintKey(NODE, getIdentifyingLabel.lift(nodeType), getPropertyNamesString(properties), EXISTS)
      case GraphTypeConstraintDefinition(_, nodeType: NodeTypeReference, PropertyTypeConstraint(properties, _), _) =>
        GraphTypeConstraintKey(NODE, getIdentifyingLabel.lift(nodeType), getPropertyNamesString(properties), TYPE)
      case GraphTypeConstraintDefinition(_, edgeType: EdgeTypeReference, KeyConstraint(properties), _) =>
        GraphTypeConstraintKey(EDGE, getIdentifyingLabel.lift(edgeType), getPropertyNamesString(properties), NONE)
      case GraphTypeConstraintDefinition(_, edgeType: EdgeTypeReference, UniquenessConstraint(properties), _) =>
        GraphTypeConstraintKey(EDGE, getIdentifyingLabel.lift(edgeType), getPropertyNamesString(properties), NONE)
      case GraphTypeConstraintDefinition(_, edgeType: EdgeTypeReference, ExistenceConstraint(properties), _) =>
        GraphTypeConstraintKey(EDGE, getIdentifyingLabel.lift(edgeType), getPropertyNamesString(properties), EXISTS)
      case GraphTypeConstraintDefinition(_, edgeType: EdgeTypeReference, PropertyTypeConstraint(properties, _), _) =>
        GraphTypeConstraintKey(EDGE, getIdentifyingLabel.lift(edgeType), getPropertyNamesString(properties), TYPE)
    }
  }

  private val getIdentifyingLabel: PartialFunction[GraphTypeElementReference, String] = {
    // We don't need to worry about the variables because once the constraints are
    // canonicalised the semantic check will run again
    case ntr: NodeTypeReferenceByLabel            => ntr.labelName.name
    case ntr: NodeTypeReferenceByIdentifyingLabel => ntr.labelName.name
    case etr: EdgeTypeReferenceByLabel            => etr.relTypeName.name
    case etr: EdgeTypeReferenceByIdentifyingLabel => etr.relTypeName.name
  }

  private def getPropertyNamesString(propertyNames: Seq[Property]): String = propertyNames match {
    case pn :: Nil => pn.propertyKey.name
    case pns       => pns.map(_.propertyKey.name).mkString("{", ", ", "}")
  }
}
