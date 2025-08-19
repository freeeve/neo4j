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
package org.neo4j.cypher.internal.compiler.planner.logical.schema

import org.neo4j.cypher.internal.compiler.planner.logical.Metrics.LabelInfo
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.HasLabel
import org.neo4j.cypher.internal.expressions.HasLabels
import org.neo4j.cypher.internal.expressions.LabelName
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.expressions.SemanticDirection.BOTH
import org.neo4j.cypher.internal.ir.PatternRelationship
import org.neo4j.cypher.internal.ir.helpers.CachedFunction
import org.neo4j.cypher.internal.planner.spi.PlanContext
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.internal.schema.EndpointType

sealed trait GraphSchemaOptimizations {

  /**
   * Removes labels that are implied by other labels, ensuring that only non-redundant labels remain.
   */
  def pruneImpliedLabels(labels: Set[LabelName]): Set[LabelName]

  /**
   * Removes labels that are constrained to imply other labels, ensuring that only non-redundant labels remain.
   */
  def pruneConstrainedLabels(labels: Seq[LabelName]): Seq[LabelName]

  /**
   * Removes labels from the given LabelInfo that are implied by other labels, ensuring that
   * only non-redundant labels remain.
   */
  def pruneImpliedLabels(labelInfo: LabelInfo): LabelInfo

  /**
   * Adds labels implied by existing labels to the set.
   */
  def addImpliedLabels(labels: Set[LabelName]): Set[LabelName]

  /**
   * For each entry in `labelInfo`, adds labels implied by existing labels to the set.
   */
  def addImpliedLabels(labelInfo: LabelInfo): LabelInfo

  /**
   * Returns true if the presence of `labelToCheck` is implied by one or more of the `knownLabels`.
   */
  def isLabelImplied(labelToCheck: LabelName, knownLabels: Set[LabelName]): Boolean

  type DisjunctiveTypes = Set[String]

  /**
   *
   * @param labelToCheck Is this label implied by one of its adjacent relationships
   * @param outRelTypes The set of all disjunctive types of each outgoing relationship. For example ()<-[:A|B|C]-(v:X)-[B]->() gives for variable v: {{A,B,C}, {B}}.
   * @param inRelTypes The set of all disjunctive types of each incoming relationship. For example ()-[:A|B|C]->(v:X)<-[B]-() gives for variable v: {{A,B,C}, {B}}.
   * @param undirectedRelTypes The set of all disjunctive types of each undirected relationship. For example ()-[:A|B|C]-(v:X)-[B]->() gives for variable v: {{A,B,C}}.
   * @return true if the presence of `labelToCheck` is implied by a type on at least one of its outgoing or incoming relationships
   */
  def isLabelImplied(
    labelToCheck: String,
    outRelTypes: Set[DisjunctiveTypes],
    inRelTypes: Set[DisjunctiveTypes],
    undirectedRelTypes: Set[DisjunctiveTypes]
  ): Boolean

  def partitionImpliedHasLabelsPredicates(unsolvedPredicates: Set[Expression]): (Set[Expression], Set[Expression])

  def impliedEndpointLabelsMap(patternRelationships: Set[PatternRelationship]): LabelInfo

  def implicationsFromRelationship(
    fromNode: LogicalVariable,
    toNode: LogicalVariable,
    relationshipTypes: Seq[RelTypeName],
    relationshipDirection: SemanticDirection
  ): Set[(LogicalVariable, LabelName)]
}

object GraphSchemaOptimizations {

  def fromConfig(enabled: Boolean, planContext: PlanContext): GraphSchemaOptimizations = {
    if (!enabled) Disabled
    else new Enabled(planContext)
  }

  case object Disabled extends GraphSchemaOptimizations {
    override def pruneImpliedLabels(labels: Set[LabelName]): Set[LabelName] = labels

    override def pruneImpliedLabels(labelInfo: LabelInfo): LabelInfo = labelInfo

    override def pruneConstrainedLabels(labels: Seq[LabelName]): Seq[LabelName] = labels

    override def isLabelImplied(labelToCheck: LabelName, knownLabels: Set[LabelName]): Boolean = false

    override def addImpliedLabels(labels: Set[LabelName]): Set[LabelName] = labels

    override def addImpliedLabels(labelInfo: LabelInfo): LabelInfo = labelInfo

    override def isLabelImplied(
      labelToCheck: String,
      outRelTypes: Set[DisjunctiveTypes],
      inRelTypes: Set[DisjunctiveTypes],
      undirectedRelTypes: Set[DisjunctiveTypes]
    ): Boolean =
      false

    override def impliedEndpointLabelsMap(patternRelationships: Set[PatternRelationship]): LabelInfo =
      LabelInfo.empty

    override def partitionImpliedHasLabelsPredicates(unsolvedPredicates: Set[Expression])
      : (Set[Expression], Set[Expression]) =
      (Set.empty, unsolvedPredicates)

    override def implicationsFromRelationship(
      fromNode: LogicalVariable,
      toNode: LogicalVariable,
      relationshipTypes: Seq[RelTypeName],
      relationshipDirection: SemanticDirection
    ): Set[(LogicalVariable, LabelName)] =
      Set.empty
  }

  final class Enabled(planContext: PlanContext) extends GraphSchemaOptimizations {

    private val impliedLabels: LabelName => Set[LabelName] =
      CachedFunction { (labelName: LabelName) =>
        planContext.getNodeLabelConstraints(labelName.name)
          .map(strLabel => LabelName(strLabel)(InputPosition.NONE))
      }

    override def pruneImpliedLabels(labels: Set[LabelName]): Set[LabelName] = {
      labels.foldLeft(labels) {
        (labelsToKeep, label) => labelsToKeep.diff(impliedLabels(label))
      }
    }

    override def pruneImpliedLabels(labelInfo: LabelInfo): LabelInfo = {
      labelInfo.view.mapValues(pruneImpliedLabels).toMap
    }

    override def pruneConstrainedLabels(labels: Seq[LabelName]): Seq[LabelName] = {
      labels.filterNot { label =>
        impliedLabels(label).exists(labels.contains)
      }
    }

    override def isLabelImplied(labelToCheck: LabelName, knownLabels: Set[LabelName]): Boolean = {
      knownLabels.exists { knownLabel =>
        impliedLabels(knownLabel).contains(labelToCheck)
      }
    }

    override def addImpliedLabels(labels: Set[LabelName]): Set[LabelName] =
      labels.flatMap(l => impliedLabels(l).incl(l))

    override def addImpliedLabels(labelInfo: LabelInfo): LabelInfo =
      labelInfo.view.mapValues(addImpliedLabels).toMap

    override def isLabelImplied(
      labelToCheck: String,
      outRelsTypes: Set[DisjunctiveTypes],
      inRelsTypes: Set[DisjunctiveTypes],
      undirectedRelsTypes: Set[DisjunctiveTypes]
    ): Boolean = {
      def impliedByRels(endpoint: EndpointType, relsTypes: Set[DisjunctiveTypes]) =
        relsTypes.exists(relTypes =>
          relTypes.nonEmpty && relTypes.forall(
            planContext.hasRelationshipEndpointLabelConstraint(_, labelToCheck, endpoint)
          )
        )

      lazy val isImpliedByOutgoingRel = impliedByRels(EndpointType.START, outRelsTypes)
      lazy val isImpliedByIncomingRel = impliedByRels(EndpointType.END, inRelsTypes)
      lazy val isImpliedByUndirectedRel =
        undirectedRelsTypes.exists(relTypes =>
          relTypes.nonEmpty && relTypes.forall(relType =>
            // If the relationship pattern has no direction indicated, it could be matched in either direction.
            // That means, to be able to infer any label information on the end-points, they need to be present on both ends.
            planContext.hasRelationshipEndpointLabelConstraint(relType, labelToCheck, EndpointType.START)
              && planContext.hasRelationshipEndpointLabelConstraint(relType, labelToCheck, EndpointType.END)
          )
        )
      isImpliedByOutgoingRel || isImpliedByIncomingRel || isImpliedByUndirectedRel
    }

    /**
     * Find the HasLabel-predicates that are implied by another from the set of unsolved predicates
     *
     * @param unsolvedPredicates The set of unsolved predicates
     * @return The set of HasLabels-predicates that can be implied by one of the others and the rest of the unsolved predicates
     */
    def partitionImpliedHasLabelsPredicates(unsolvedPredicates: Set[Expression]): (Set[Expression], Set[Expression]) = {
      val unsolvedHasLabelPredicates = unsolvedPredicates collect {
        case label: HasLabels => label
      }

      // A label on node n should not imply a label on node m, therefore we partition by the expression of the HasLabels-predicates
      val unsolvedHasLabelsPerExpression = unsolvedHasLabelPredicates.groupBy(hasLabels => hasLabels.expression)
      // Get the set of all implied HasLabel-predicates
      val impliedUnsolvedLabels = unsolvedHasLabelsPerExpression.map {
        case (expr: Expression, hasLabelsSet: Set[HasLabels]) =>
          val labelsOnExpression = hasLabelsSet.flatMap(_.labels)
          // Find the labels that are implied by one of the others
          (expr, labelsOnExpression.flatMap(impliedLabels) intersect labelsOnExpression)
      }

      unsolvedPredicates.partition {
        case HasLabel(expr, label) => impliedUnsolvedLabels(expr).contains(label)
        case _                     => false
      }
    }

    private def labelName(variable: LogicalVariable, impliedLabel: String) =
      variable -> LabelName(impliedLabel)(variable.position)

    override def implicationsFromRelationship(
      fromNode: LogicalVariable,
      toNode: LogicalVariable,
      relationshipTypes: Seq[RelTypeName],
      relationshipDirection: SemanticDirection
    ): Set[(LogicalVariable, LabelName)] =
      cachedImplicationsFromRelationship(fromNode, toNode, relationshipTypes, relationshipDirection)

    private val cachedImplicationsFromRelationship =
      CachedFunction {
        (fromNode: LogicalVariable, toNode: LogicalVariable, types: Seq[RelTypeName], dir: SemanticDirection) =>
          internalImplicationsFromRelationship(fromNode, toNode, types, dir)
      }

    private def implicationsFromRelationship(patternRelationship: PatternRelationship)
      : Set[(LogicalVariable, LabelName)] = {
      val fromNode = patternRelationship.inOrder._1
      val toNode = patternRelationship.inOrder._2
      val relationshipTypes = patternRelationship.types
      val relationshipDirection = patternRelationship.dir

      implicationsFromRelationship(fromNode, toNode, relationshipTypes, relationshipDirection)
    }

    private def internalImplicationsFromRelationship(
      fromNode: LogicalVariable,
      toNode: LogicalVariable,
      types: Seq[RelTypeName],
      dir: SemanticDirection
    ): Set[(LogicalVariable, LabelName)] =
      types
        .map(relTypeName => implicationsFromRelationshipType(dir, fromNode, toNode, relTypeName.name))
        .reduceOption(_ intersect _)
        .getOrElse(Set.empty)

    private def implicationsFromRelationshipType(
      dir: SemanticDirection,
      fromNode: LogicalVariable,
      toNode: LogicalVariable,
      relTypeName: String
    ): Set[(LogicalVariable, LabelName)] = {
      val constraintsOnRelType = planContext.getRelationshipEndpointLabelConstraints(relTypeName)
      if (fromNode == toNode) {
        constraintsOnRelType
          .values
          .toSet
          .map(labelName(fromNode, _))
      } else {
        dir match {
          case BOTH =>
            (constraintsOnRelType.get(EndpointType.START), constraintsOnRelType.get(EndpointType.END)) match {
              case (Some(impliedStartLabel), Some(impliedEndLabel))
                // If the relationship pattern has no direction indicated, it could be matched in either direction.
                // That means, to be able to infer any label information on the end-points, they need to be present on both ends.
                if impliedStartLabel == impliedEndLabel =>
                Set(fromNode, toNode).map(labelName(_, impliedStartLabel))
              case _ => Set.empty
            }
          case _ =>
            constraintsOnRelType.toSet[(EndpointType, String)]
              .collect {
                case (EndpointType.START, impliedLabel) => labelName(fromNode, impliedLabel)
                case (EndpointType.END, impliedLabel)   => labelName(toNode, impliedLabel)
              }
        }
      }
    }

    /**
     * Returns a map of variables to the set of labels that are implied by the relationship constraints.
     * This is used to infer labels that can be added to the query graph based on the relationship constraints.
     */
    override def impliedEndpointLabelsMap(patternRelationships: Set[PatternRelationship]): LabelInfo = {
      val implications =
        patternRelationships
          .flatMap(implicationsFromRelationship)
      LabelInfo.from(implications.groupMap(_._1)(_._2))
    }
  }
}
