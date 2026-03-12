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
package org.neo4j.cypher.internal.compiler.planner.logical.cardinality.assumeIndependence

import org.neo4j.cypher.internal.compiler.planner.logical.Metrics.LabelInfo
import org.neo4j.cypher.internal.compiler.planner.logical.PlannerDefaults.DEFAULT_NODES_UNIQUENESS_SELECTIVITY
import org.neo4j.cypher.internal.compiler.planner.logical.PlannerDefaults.DEFAULT_REL_UNIQUENESS_SELECTIVITY
import org.neo4j.cypher.internal.compiler.planner.logical.cardinality.assumeIndependence.PatternRelationshipCardinalityModel.extractRelevantLabelInfo
import org.neo4j.cypher.internal.compiler.planner.logical.cardinality.assumeIndependence.PatternRelationshipCardinalityModel.extractRelevantRelTypeInfo
import org.neo4j.cypher.internal.compiler.planner.logical.idp.expandSolverStep.VariableList
import org.neo4j.cypher.internal.expressions.DifferentNodes
import org.neo4j.cypher.internal.expressions.DifferentRelationships
import org.neo4j.cypher.internal.expressions.DisjointNodes
import org.neo4j.cypher.internal.expressions.HasLabels
import org.neo4j.cypher.internal.expressions.LabelName
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.NoneOfNodes
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.ir.NodeBinding
import org.neo4j.cypher.internal.ir.PatternRelationship
import org.neo4j.cypher.internal.ir.Predicate
import org.neo4j.cypher.internal.ir.QuantifiedPathPattern
import org.neo4j.cypher.internal.ir.Selections
import org.neo4j.cypher.internal.ir.helpers.CachedFunction
import org.neo4j.cypher.internal.logical.plans.TraversalPathMode
import org.neo4j.cypher.internal.logical.plans.TraversalPathMode.Acyclic
import org.neo4j.cypher.internal.logical.plans.TraversalPathMode.Trail
import org.neo4j.cypher.internal.logical.plans.TraversalPathMode.Walk
import org.neo4j.cypher.internal.util.Cardinality
import org.neo4j.cypher.internal.util.Cardinality.NumericCardinality
import org.neo4j.cypher.internal.util.Multiplier
import org.neo4j.cypher.internal.util.Multiplier.NumericMultiplier
import org.neo4j.cypher.internal.util.NonEmptyList
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.Selectivity
import org.neo4j.cypher.internal.util.topDown

import scala.collection.mutable
import scala.util.chaining.scalaUtilChainingOps

trait QuantifiedPathPatternCardinalityModel extends NodeCardinalityModel with PatternRelationshipCardinalityModel {

  def getQuantifiedPathPatternCardinality(
    context: QueryGraphCardinalityContext,
    labelInfo: LabelInfo,
    quantifiedPathPattern: QuantifiedPathPattern,
    uniqueRelationships: Set[LogicalVariable],
    uniqueNodes: Set[LogicalVariable],
    boundaryNodePredicates: Set[Predicate],
    otherPredicates: Set[Predicate],
    pathMode: TraversalPathMode
  ): Cardinality = {
    cachedDoGetQuantifiedPathPatternCardinality(
      context.copy(relTypeInfo = extractRelevantRelTypeInfo(context.relTypeInfo, quantifiedPathPattern)),
      extractRelevantLabelInfo(labelInfo, quantifiedPathPattern),
      quantifiedPathPattern,
      uniqueRelationships,
      uniqueNodes,
      boundaryNodePredicates,
      otherPredicates,
      pathMode
    )
  }

  private val cachedDoGetQuantifiedPathPatternCardinality = CachedFunction(doGetQuantifiedPathPatternCardinality _)

  private def doGetQuantifiedPathPatternCardinality(
    context: QueryGraphCardinalityContext,
    labelInfo: LabelInfo,
    quantifiedPathPattern: QuantifiedPathPattern,
    uniqueRelationships: Set[LogicalVariable],
    uniqueNodes: Set[LogicalVariable],
    boundaryNodePredicates: Set[Predicate],
    outerOtherPredicates: Set[Predicate],
    pathMode: TraversalPathMode
  ): Cardinality = {
    val nodesOutsideTheQpp = pathMode match {
      case Trail => 0
      case Walk  => 0
      case Acyclic =>
        outerOtherPredicates.foldLeft(0) {
          case (acc, Predicate(_, NoneOfNodes(_: Variable, VariableList(nodes))))
            if nodes subsetOf quantifiedPathPattern.groupVariables =>
            acc + 1
          case (
              acc,
              Predicate(
                _,
                DisjointNodes(VariableList(nodes1), VariableList(nodes2), nodes1LowerBound, nodes1MaybeUpperBound)
              )
            )
            if nodes2.subsetOf(quantifiedPathPattern.groupVariables) =>
            // Let's say that nodes1 come from QPP1 with quantifier {3,6}.
            // We assume here (while estimating the cardinality of QPP2 with nodes2) that QPP1 always performed 4 iterations.
            // We take the middle between the lower and upper bound, rounded down.

            val middleNumberOfIterations: Int = {
              val upperBound = nodes1MaybeUpperBound match {
                case Some(x) if x < RepetitionCardinalityModel.MAX_VAR_LENGTH => x.toInt
                case _ => RepetitionCardinalityModel.MAX_VAR_LENGTH
              }
              val lowerBound = Math.min(nodes1LowerBound, upperBound).toInt
              ((upperBound - lowerBound) / 2) + lowerBound
            }

            // On unrolling a QPP, the first node of the current iteration is the last node of the previous iteration.
            val totalNodesInChain = middleNumberOfIterations match {
              case 0 => 1
              case i => nodes1.size * i - i + 1 // same as: nodes1.size + (nodes1.size - 1) * (i - 1)
            }
            // We also assume that both QPPs belong to different equivalent classes.
            // In those cases there are NoneOfNodes-predicates for the boundary nodes.
            // Therefore, we do not include the first and the last node of the full unrolled chain. This explains the -2.
            val totalNodesInChainExpectStartAndEnd =
              if (totalNodesInChain <= 2) {
                0
              } else {
                totalNodesInChain - 2
              }
            acc + totalNodesInChainExpectStartAndEnd
          case (acc, _) => acc
        }
    }

    val predicates = QuantifiedPathPatternPredicates.partitionSelections(labelInfo, quantifiedPathPattern.selections)

    lazy val labelsOnFirstNode =
      predicates.labelsOnNodes(
        quantifiedPathPattern.leftBinding.outer,
        quantifiedPathPattern.leftBinding.inner
      )
    lazy val labelsOnLastNode =
      predicates.labelsOnNodes(
        quantifiedPathPattern.rightBinding.outer,
        quantifiedPathPattern.rightBinding.inner
      )

    lazy val (predicatesSolvedForFirstIteration, otherPredicates) =
      partitionBoundarySolvedPredicates(quantifiedPathPattern, predicates, boundaryNodePredicates)

    lazy val extraRelTypeInfo = quantifiedPathPattern.patternRelationships.collect {
      case PatternRelationship(rel, _, _, Seq(relType), _) => rel -> relType
    }.toMap

    lazy val boundaryNodePredicatesSelectivity: Selectivity =
      context.predicatesSelectivityWithExtraRelTypeInfo(
        labelInfo = predicates.allLabelInfo,
        extraRelTypeInfo = extraRelTypeInfo,
        predicates = predicatesSolvedForFirstIteration
      )

    lazy val otherPredicatesSelectivity: Selectivity =
      context.predicatesSelectivityWithExtraRelTypeInfo(
        labelInfo = predicates.allLabelInfo,
        extraRelTypeInfo = extraRelTypeInfo,
        predicates = otherPredicates
      )

    object inferLabels {

      private lazy val nodeConnections: Seq[PatternRelationship] =
        quantifiedPathPattern.patternRelationships.iterator.toSeq

      def apply(context: QueryGraphCardinalityContext)(labelInfo: LabelInfo)
        : (LabelInfo, QueryGraphCardinalityContext) = {
        context.labelInferenceStrategy.inferLabels(context, labelInfo, nodeConnections)
      }

      lazy val forJunctionNode: (Set[LabelName], QueryGraphCardinalityContext) = {

        val leftInner = quantifiedPathPattern.leftBinding.inner
        val rightInner = quantifiedPathPattern.rightBinding.inner

        // Junction node is both the `rightInner` node of the current iteration, and the `leftInner` node of the next iteration.
        // We add node connections from the next iteration (renaming variable to match the current iteration) to the list
        // to potentially enable better label inference.
        val nodeConnectionsWithJunction: Seq[PatternRelationship] = {
          val junctionNodeConnections = nodeConnections.collect {
            case r if r.left == leftInner  => r.withLeft(rightInner)
            case r if r.right == leftInner => r.withRight(rightInner)
          }
          nodeConnections ++ junctionNodeConnections
        }

        val labelInfoWithJunction = predicates.allLabelInfo
          .updated(rightInner, predicates.labelsOnNodes(leftInner, rightInner))

        context.labelInferenceStrategy.inferLabels(context, labelInfoWithJunction, nodeConnectionsWithJunction) pipe {
          case (labelInfo, context) => (labelInfo.getOrElse(rightInner, Set.empty), context)
        }
      }
    }

    def iterationCardinality(
      context: QueryGraphCardinalityContext,
      leftBindingInnerLabels: Set[LabelName],
      rightBindingInnerLabels: Set[LabelName]
    ): Cardinality = {
      inferLabels(context) {
        predicates.allLabelInfo
          .updated(quantifiedPathPattern.leftBinding.inner, leftBindingInnerLabels)
          .updated(quantifiedPathPattern.rightBinding.inner, rightBindingInnerLabels)
      } pipe {
        case (iterationLabels, context) =>
          getPatternRelationshipsCardinality(context, iterationLabels, quantifiedPathPattern.patternRelationships)
      }
    }

    def iterationMultiplier(
      context: QueryGraphCardinalityContext,
      leftBindingInnerLabels: Set[LabelName],
      rightBindingInnerLabels: Set[LabelName],
      junctionNodeCardinality: Cardinality
    ): Multiplier = {

      val cardinality = iterationCardinality(
        context,
        leftBindingInnerLabels,
        rightBindingInnerLabels
      )
      Multiplier.ofDivision(cardinality, junctionNodeCardinality)
        .getOrElse(Multiplier.ZERO)
    }

    val patternCardinality =
      RepetitionCardinalityModel
        .quantifiedPathPatternRepetitionAsRange(quantifiedPathPattern.repetition)
        .view
        .map {
          case 0 =>
            getEmptyPathPatternCardinality(
              context,
              predicates.allLabelInfo,
              quantifiedPathPattern.left,
              quantifiedPathPattern.right
            )

          case 1 =>
            val singleIterationLabels =
              predicates.allLabelInfo
                .updated(quantifiedPathPattern.leftBinding.inner, labelsOnFirstNode)
                .updated(quantifiedPathPattern.rightBinding.inner, labelsOnLastNode)
            val uniquenessSelectivity = pathMode match {
              case Trail   => DEFAULT_REL_UNIQUENESS_SELECTIVITY ^ predicates.differentRelationships.size
              case Walk    => Selectivity(1)
              case Acyclic =>
                // Node uniqueness between the nodes in the QPP
                val differentNodesPredicatesWithinQpp = predicates.differentNodes.size

                // Node uniqueness for the cases where one node is inside the QPP and one is outside the QPP.
                // Do not include the first and last nodes from within the QPP. DifferentNodes-predicates for the
                // outer-start and outer-end nodes of the QPP handle those, when needed.
                val differentNodesPredicatesWithOneNodeInQppAndOneNodeOutsideQpp =
                  (quantifiedPathPattern.patternNodes.size - 2) * nodesOutsideTheQpp

                DEFAULT_NODES_UNIQUENESS_SELECTIVITY ^ (differentNodesPredicatesWithinQpp + differentNodesPredicatesWithOneNodeInQppAndOneNodeOutsideQpp)
            }
            val patternCardinality =
              getPatternRelationshipsCardinality(
                context,
                singleIterationLabels,
                quantifiedPathPattern.patternRelationships
              )
            patternCardinality * uniquenessSelectivity * otherPredicatesSelectivity

          case i =>
            inferLabels.forJunctionNode pipe {
              case (labelsOnJunctionNode, context) =>
                val junctionNodeCardinality =
                  resolveNodeLabels(context, labelsOnJunctionNode)
                    .map(getLabelsCardinality(context, _))
                    .getOrElse(Cardinality.EMPTY)

                val firstIterationCardinality = iterationCardinality(
                  context,
                  leftBindingInnerLabels = labelsOnFirstNode,
                  rightBindingInnerLabels = labelsOnJunctionNode
                )

                val intermediateIterationMultiplier = iterationMultiplier(
                  context,
                  leftBindingInnerLabels = labelsOnJunctionNode,
                  rightBindingInnerLabels = labelsOnJunctionNode,
                  junctionNodeCardinality = junctionNodeCardinality
                )

                val lastIterationMultiplier = iterationMultiplier(
                  context,
                  leftBindingInnerLabels = labelsOnJunctionNode,
                  rightBindingInnerLabels = labelsOnLastNode,
                  junctionNodeCardinality = junctionNodeCardinality
                )

                val uniquenessSelectivity = pathMode match {
                  case Trail => RepetitionCardinalityModel.relationshipUniquenessSelectivity(
                      differentRelationships = predicates.differentRelationships.size,
                      uniqueRelationships = uniqueRelationships.intersect(
                        quantifiedPathPattern.relationshipVariableGroupings.map(_.group)
                      ).size,
                      repetitions = i
                    )
                  case Walk    => Selectivity(1)
                  case Acyclic =>
                    // innerQppNodeUniqueness takes care of the node uniqueness between the nodes in the QPP
                    val innerQppNodeUniqueness = RepetitionCardinalityModel.nodeUniquenessSelectivity(
                      uniqueNodes =
                        uniqueNodes.intersect(quantifiedPathPattern.nodeVariableGroupings.map(_.group)).size,
                      repetitions = i
                    )
                    // What is left is the node uniqueness between the node in the QPP to nodes outside the QPP
                    // Example:
                    //   MATCH ACYCLIC (n1)((a)--(b)){3,5}(n2)--(n3)((c)--(d)--(e)){2,3}(n4),
                    //   while we are currently in the estimation of ()((c)--(d)){2,3}() for i=2
                    // nbInnerNodes = 3 {c, d, e}
                    // nbNodesInChainAtIterationI = 5 {c1, d1, e1_c2, d2, e2}
                    // nodesOutsideTheQpp = 5 (assume 4 iteration of the QPP with quantifier {3,5}) {n1a1, b1_a2, b2_a3, b3_a4, b4}
                    // For every node outside the QPP, we include a different nodes check for each of the `inner`
                    // nodes of the chain at current iteration i, which leads to
                    //   ((nbNodesInChainAtIterationI - 2) * nodesOutsideTheQpp) number of different nodes checks.
                    // The `inner` nodes do not include the first and last node of the unrolled chain, which explain the `- 2`.
                    // There are `DifferentNodes` and `NoneOfNodes` predicates for those (assuming that both QPP belong to different equivalent classes).
                    val nbInnerNodes = quantifiedPathPattern.patternNodes.size
                    val nbNodesInChainAtIterationI =
                      nbInnerNodes * i - i + 1 // Same as: nbInnerNodes + ((nbInnerNodes - 1) * (i - 1))
                    val nodeUniquenessBetweenNodesInsideAndOutsideQpp =
                      DEFAULT_NODES_UNIQUENESS_SELECTIVITY ^ ((nbNodesInChainAtIterationI - 2) * nodesOutsideTheQpp)
                    innerQppNodeUniqueness * nodeUniquenessBetweenNodesInsideAndOutsideQpp
                }

                firstIterationCardinality *
                  (intermediateIterationMultiplier ^ (i - 2)) *
                  lastIterationMultiplier *
                  uniquenessSelectivity *
                  (otherPredicatesSelectivity ^ i) *
                  (boundaryNodePredicatesSelectivity ^ (i - 1))
            }
        }.sum(NumericCardinality)

    patternCardinality
  }

  /**
   * Partition qpp predicates depending on whether they've already been solved once on the boundary node or not.
   * It's important to treat these predicates separately in order to not overestimate their selectivity.
   * Since they already exist on the boundary node, we exclude their selectivity for the first iteration of the qpp,
   * since it's already solved for that iteration.
   *
   * @param qpp The quantified path pattern
   * @param predicates The predicates associated with the quantified path pattern
   * @param boundaryNodePredicates all predicates that are present on the boundary nodes
   *                               (juxtaposed nodes outside of the quantified path pattern)
   * @return A tuple containing predicates solved once on boundary nodes, and all other predicates
   */
  private def partitionBoundarySolvedPredicates(
    qpp: QuantifiedPathPattern,
    predicates: QuantifiedPathPatternPredicates,
    boundaryNodePredicates: Set[Predicate]
  ): (Set[Predicate], Set[Predicate]) = {
    val rewriter = (binding: NodeBinding) =>
      topDown(Rewriter.lift {
        case variable if variable == binding.outer => binding.inner
      })

    val leftBindingRewriter = rewriter(qpp.leftBinding)
    val rightBindingRewriter = rewriter(qpp.rightBinding)

    // rewrite boundary node predicates to their corresponding inner representation
    val rewrittenPredicates = boundaryNodePredicates.flatMap(pred =>
      Seq(leftBindingRewriter(pred), rightBindingRewriter(pred))
    )

    predicates.otherPredicates.partition(rewrittenPredicates.contains)
  }

  private def getPatternRelationshipsCardinality(
    context: QueryGraphCardinalityContext,
    labelInfo: LabelInfo,
    patternRelationships: NonEmptyList[PatternRelationship]
  ): Cardinality = {
    val firstRelationship = patternRelationships.head
    val firstRelationshipCardinality = getSimpleRelationshipCardinality(
      context = context,
      labelInfo = labelInfo,
      leftNode = firstRelationship.left,
      rightNode = firstRelationship.right,
      relationshipTypes = firstRelationship.types,
      relationshipDirection = firstRelationship.dir
    )
    val otherRelationshipsMultiplier = patternRelationships.tail.view.map { relationship =>
      Multiplier.ofDivision(
        dividend = getSimpleRelationshipCardinality(
          context = context,
          labelInfo = labelInfo,
          leftNode = relationship.left,
          rightNode = relationship.right,
          relationshipTypes = relationship.types,
          relationshipDirection = relationship.dir
        ),
        divisor = getNodeCardinality(context, labelInfo, relationship.left).getOrElse(Cardinality.EMPTY)
      ).getOrElse(Multiplier.ZERO)
    }.product(NumericMultiplier)
    firstRelationshipCardinality * otherRelationshipsMultiplier
  }
}

case class QuantifiedPathPatternPredicates(
  allLabelInfo: LabelInfo,
  differentRelationships: Set[DifferentRelationships],
  differentNodes: Set[DifferentNodes],
  otherPredicates: Set[Predicate]
) {
  def labelsOnNode(nodeName: LogicalVariable): Set[LabelName] = allLabelInfo.getOrElse(nodeName, Set.empty)

  def labelsOnNodes(nodeNames: LogicalVariable*): Set[LabelName] = nodeNames.foldLeft(Set.empty[LabelName]) {
    case (labels, nodeName) => labels.union(labelsOnNode(nodeName))
  }
}

object QuantifiedPathPatternPredicates {

  def partitionSelections(labelInfo: LabelInfo, selections: Selections): QuantifiedPathPatternPredicates = {
    val labelsBuilder = mutable.Map.empty[LogicalVariable, mutable.Builder[LabelName, Set[LabelName]]]
    val differentRelationshipsBuilder = Set.newBuilder[DifferentRelationships]
    val differentNodesBuilder = Set.newBuilder[DifferentNodes]
    val otherPredicatesBuilder = Set.newBuilder[Predicate]
    selections.predicates.foreach {
      case Predicate(_, HasLabels(v: Variable, labels)) =>
        labelsBuilder.updateWith(v)(builder => Some(builder.getOrElse(Set.newBuilder).addOne(labels.head)))
      case Predicate(_, differentRelationships: DifferentRelationships) =>
        differentRelationshipsBuilder.addOne(differentRelationships)
      case Predicate(_, differentNodes: DifferentNodes) =>
        differentNodesBuilder.addOne(differentNodes)
      case otherPredicate =>
        otherPredicatesBuilder.addOne(otherPredicate)
    }
    labelInfo.foreach {
      case (name, labels) =>
        labelsBuilder.updateWith(name)(builder => Some(builder.getOrElse(Set.newBuilder).addAll(labels)))
    }
    QuantifiedPathPatternPredicates(
      allLabelInfo = labelsBuilder.view.mapValues(_.result()).toMap,
      differentRelationships = differentRelationshipsBuilder.result(),
      differentNodes = differentNodesBuilder.result(),
      otherPredicates = otherPredicatesBuilder.result()
    )
  }
}
