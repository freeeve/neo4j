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
package org.neo4j.cypher.internal.compiler.planner.logical.plans.rewriter

import org.neo4j.cypher.internal.compiler.planner.logical.InlinedPredicates
import org.neo4j.cypher.internal.compiler.planner.logical.convertToInlinedPredicates
import org.neo4j.cypher.internal.expressions.AllReduceAccumulator
import org.neo4j.cypher.internal.expressions.Ands
import org.neo4j.cypher.internal.expressions.Disjoint
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.IsRepeatTrailUnique
import org.neo4j.cypher.internal.expressions.LogicalProperty
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.NoneOfRelationships
import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.expressions.VariableGrouping
import org.neo4j.cypher.internal.ir.VarPatternLength
import org.neo4j.cypher.internal.logical.plans.Argument
import org.neo4j.cypher.internal.logical.plans.Expand
import org.neo4j.cypher.internal.logical.plans.Expand.ExpandAll
import org.neo4j.cypher.internal.logical.plans.Expand.ExpansionMode
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.Repeat
import org.neo4j.cypher.internal.logical.plans.RepeatAcyclic
import org.neo4j.cypher.internal.logical.plans.RepeatTrail
import org.neo4j.cypher.internal.logical.plans.RepeatWalk
import org.neo4j.cypher.internal.logical.plans.Selection
import org.neo4j.cypher.internal.logical.plans.Selection.LabelAndRelTypeInfo
import org.neo4j.cypher.internal.logical.plans.TraversalPathMode
import org.neo4j.cypher.internal.logical.plans.VarExpand
import org.neo4j.cypher.internal.planner.spi.PlanningAttributes.LabelAndRelTypeInfos
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Repetition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.Rewriter.TopDownMergeableRewriter
import org.neo4j.cypher.internal.util.attribution.Attributes
import org.neo4j.cypher.internal.util.attribution.SameId
import org.neo4j.cypher.internal.util.collection.immutable.ListSet
import org.neo4j.cypher.internal.util.topDown

/**
 * This rewriter will sometimes transform a Repeat-Trail or -Walk into a VarExpand, like in the example below.
 *
 * Before
 * .repeat((a) ((n)-[r]->(m))+ (b))
 * .|.filter(isRepeatTrailUnique(r))
 * .|.expandAll((n)-[r]->(m))
 * .|.argument(n)
 * .lhs(a)
 *
 * After
 * .expandAll((a)-[r*]->(b))
 * .lhs(a)
 *
 * Repeat is more powerful than VarExpand, in the sense that Repeat can do more things than VarExpand. We consider Repeat
 * and VarExpand to be equivalent when the following conditions are met:
 *  - the Repeat's relationship pattern contains a single relationship
 *  - the Repeat's node group variables are not used by downstream logical plans and thus empty
 *  - the Repeat's inner node variables are only used during path expansion in predicates within the QPP, and the QPP is a single directional relationship, i.e., in case where it can be substituted with startNode/endNode of the relationship.
 *  - the Repeat's quantifier can be converted lossless from Long to Int
 *
 * This rewriter should run after [[RemoveUnusedGroupVariablesRewriter]], so that unused group variables are pruned.
 * This rewriter should run before [[pruningVarExpander]] so that the [[PruningVarExpand]] optimisation may take place.
 * This rewriter should run before [[VarLengthRewriter]] so that the quantifier may be rewritten.
 * This rewriter should run before [[UniquenessRewriter]] so that relationship uniqueness predicates may be rewritten.
 */
case class RepeatToVarExpandRewriter(
  labelAndRelTypeInfos: LabelAndRelTypeInfos,
  otherAttributes: Attributes[LogicalPlan],
  anonymousVariableNameGenerator: AnonymousVariableNameGenerator,
  rewritableRepeatExtractor: RepeatToVarExpandRewriter.RewritableRepeatExtractor,
  isBlockFormat: Boolean,
  executionModelSupportsCursorReuseInBlockFormat: Boolean,
  isShardedDatabase: Boolean
) extends Rewriter with TopDownMergeableRewriter {

  private def createVarLengthExpand(
    repeat: Repeat,
    expand: Expand,
    predicates: Seq[Expression],
    quantifier: VarPatternLength,
    relationship: Option[VariableGrouping],
    expansionMode: ExpansionMode
  ): Option[LogicalPlan] = {
    val varExpandRel = relationship.map(_.group).getOrElse(repeat.innerRelationships.head)
    convertToInlinedPredicates(
      outerStartNode = repeat.start,
      innerStartNode = repeat.innerStart,
      innerEndNode = repeat.innerEnd,
      outerEndNode = repeat.end,
      innerRelationship = relationship.map(_.singleton).getOrElse(repeat.innerRelationships.head),
      predicatesToInline = predicates,
      pathRepetition = repeat.repetition,
      pathDirection = expand.dir,
      mode = convertToInlinedPredicates.Mode.Repeat,
      anonymousVariableNameGenerator = anonymousVariableNameGenerator
    ).collect {
      case inlinedPredicates if shouldInlinePredicates(inlinedPredicates) =>
        val varExpand = createVarExpand(repeat, expand, quantifier, inlinedPredicates, varExpandRel, expansionMode)
        val expandWithUniqueRel = maybeAddRelUniquenessPredicates(repeat, varExpandRel, varExpand)
        val expandWithUniqueGroupRel = maybeAddGroupRelUniquenessPredicates(repeat, varExpandRel, expandWithUniqueRel)
        expandWithUniqueGroupRel
    }
  }

  override val innerRewriter: Rewriter = Rewriter.lift {
    // Rewrite special cases of Repeat into VarLengthExpand(All)
    case rewritableRepeatExtractor(repeat, expand, inlinablePredicates, quantifier, relationship, repeatExpansionMode)
      if !requiresPropertyAccessFromShards(inlinablePredicates) =>
      // Create the VarLengthExpandAll/VarLengthExpandInto
      createVarLengthExpand(
        repeat,
        expand,
        inlinablePredicates,
        quantifier,
        relationship,
        repeatExpansionMode
      ).getOrElse(repeat)
  }

  private def requiresPropertyAccessFromShards(predicates: Iterable[Expression]): Boolean = {
    isShardedDatabase && predicates.exists(requiresPropertyAccess)
  }

  private def requiresPropertyAccess(expr: Expression): Boolean =
    expr.folder.treeExists {
      case _: LogicalProperty => true
    }

  private val instance: Rewriter = topDown(innerRewriter)

  override def apply(input: AnyRef): AnyRef = instance.apply(input)

  private def createVarExpand(
    repeat: Repeat,
    repeatExpand: Expand,
    repeatQuantifier: VarPatternLength,
    inlinedPredicates: InlinedPredicates,
    expandRel: LogicalVariable,
    expansionMode: ExpansionMode
  ): LogicalPlan = {
    def getProjectedDir: SemanticDirection = (repeatExpand.dir, repeat.reverseGroupVariableProjections) match {
      case (SemanticDirection.BOTH, false) => SemanticDirection.OUTGOING
      case (SemanticDirection.BOTH, true)  => SemanticDirection.INCOMING
      case (direction, false)              => direction
      case (direction, true)               => direction.reversed
    }
    val pathMode = repeat match {
      case _: RepeatTrail   => TraversalPathMode.Trail
      case _: RepeatWalk    => TraversalPathMode.Walk
      case _: RepeatAcyclic => ???
    }

    VarExpand(
      source = repeat.left,
      from = repeat.start,
      maybeTo = Some(repeat.end),
      maybeRelName = Some(expandRel),
      dir = repeatExpand.dir,
      projectedDir = getProjectedDir,
      types = repeatExpand.types,
      length = repeatQuantifier,
      expansionMode = expansionMode,
      nodePredicates = inlinedPredicates.nodePredicates,
      relationshipPredicates = inlinedPredicates.relationshipPredicates,
      pathMode = pathMode
    )(SameId(repeat.id))
  }

  /**
   * If there are other relationship variables in the query, then we may need to add relationship uniqueness
   * predicates. Whether we need to do this or not will depend on whether the relationships are provably disjoint, and
   * also on whether there are any relationship variables bound before the Repeat.
   *
   * Repeat.previouslyBoundRelationships does the heavy lifting for us. During the planning of Repeat, the planner
   * determines whether it needs to populate this field. It will only populate this field if the repeat comes after
   * previously bound relationship variables that are not provably disjoint.
   */
  private def maybeAddRelUniquenessPredicates(
    repeat: Repeat,
    varExpandRel: LogicalVariable,
    source: LogicalPlan
  ): LogicalPlan = {
    def excluded(groupRelationship: LogicalVariable, previouslyBoundedRel: LogicalVariable): Expression =
      NoneOfRelationships(previouslyBoundedRel, groupRelationship)(InputPosition.NONE)

    repeat match {
      case repeat: RepeatTrail if repeat.previouslyBoundRelationships.nonEmpty =>
        val predicates: Set[Expression] = repeat.previouslyBoundRelationships
          .map(boundRel => excluded(varExpandRel, boundRel))
        appendSelection(source, predicates)
      case _ => source
    }
  }

  /**
   * See [[maybeAddRelUniquenessPredicates()]].
   */
  private def maybeAddGroupRelUniquenessPredicates(
    repeat: Repeat,
    varExpandRel: LogicalVariable,
    source: LogicalPlan
  ): LogicalPlan =
    repeat match {
      case repeat: RepeatTrail if repeat.previouslyBoundRelationshipGroups.nonEmpty =>
        val predicates: Set[Expression] = repeat.previouslyBoundRelationshipGroups
          .map(boundRel => Disjoint(varExpandRel, boundRel)(InputPosition.NONE))
        appendSelection(source, predicates)
      case _ => source
    }

  /**
   * We are able to set empty LabelAndRelTypeInfos. This information is only used by [[SortPredicatesBySelectivity]],
   * which does up look up statistics for relationship uniqueness predicates, even if it exists. Instead, it looks up
   * constant heuristics.
   */
  private def appendSelection(source: LogicalPlan, predicates: Set[Expression]): LogicalPlan = {
    val id = otherAttributes.copy(source.id).id()
    labelAndRelTypeInfos.set(id, Some(LabelAndRelTypeInfo(Map.empty, Map.empty)))
    Selection(Ands(predicates)(InputPosition.NONE), source)(SameId(id))
  }

  private def shouldInlinePredicates(inlinedPredicates: InlinedPredicates): Boolean = {
    // special rule for Block format and Pipelined/Parallel runtime
    val shouldNotInline =
      isBlockFormat &&
        executionModelSupportsCursorReuseInBlockFormat &&
        containsRelationshipPropertyPredicate(inlinedPredicates)

    !shouldNotInline
  }

  private def containsRelationshipPropertyPredicate(inlinedPredicates: InlinedPredicates): Boolean = {
    inlinedPredicates.relationshipPredicates.exists { variablePredicate =>
      variablePredicate.predicate.folder.treeExists {
        case LogicalProperty(v: LogicalVariable, _) if v == variablePredicate.variable => true
      }
    }
  }
}

object RepeatToVarExpandRewriter {

  object VariableGroupings {

    object Maybe {

      /**
       * Unapplies Set[VariableGrouping] if the set has none or one entry.
       *
       * @return Some(None)        if variableGroupings.size == 0
       * @return Some(Some(head))  if variableGroupings.size == 1
       * @return None              if none of the above
       */
      def unapply(variableGroupings: Set[VariableGrouping]): Option[Option[VariableGrouping]] = {
        Option.when(variableGroupings.size <= 1)(variableGroupings.headOption)
      }
    }

    object Empty {

      def unapply(variableGroupings: Set[VariableGrouping]): Boolean = variableGroupings.isEmpty
    }
  }

  object AllReduceAccumulators {

    object Empty {

      def unapply(allReduceAccumulators: Set[AllReduceAccumulator]): Boolean = {
        allReduceAccumulators.isEmpty
      }
    }
  }

  object RewritableRepeatExtractor {

    /**
     * .repeat(...)
     * .|.filter(..., isRepeatTrailUnique(r))
     * .|.expandAll(...)
     * .|.argument(...)
     * .lhs
     */
    case object FilterAfterExpand extends RewritableRepeatExtractor {

      override protected def rhsBeforeExpandExtractor: PartialFunction[LogicalPlan, ListSet[Expression]] = {
        case _: Argument => ListSet.empty
      }
    }

    /**
     * .repeat(...)
     * .|.filter(..., isRepeatTrailUnique(r))
     * .|.expandAll(...)
     * .|.filter(...)
     * .|.argument(...)
     * .lhs
     */
    case object FilterBeforeAndAfterExpand extends RewritableRepeatExtractor {

      override protected def rhsBeforeExpandExtractor: PartialFunction[LogicalPlan, ListSet[Expression]] = {
        case Selection(Ands(predicates), _: Argument) => predicates
      }
    }
  }

  sealed trait RewritableRepeatExtractor {
    self =>

    protected def rhsBeforeExpandExtractor: PartialFunction[LogicalPlan, ListSet[Expression]]

    def unapply(plan: LogicalPlan): Option[(
      Repeat,
      Expand,
      Seq[Expression],
      VarPatternLength,
      Option[VariableGrouping],
      ExpansionMode
    )] = {
      plan match {
        case repeat @ RepeatTrail(
            _,
            RewritableTrailRhs(
              expand,
              inlinablePredicates
            ),
            RewritableRepeatQuantifier(quantifier),
            _,
            _,
            _,
            _,
            VariableGroupings.Empty(),
            VariableGroupings.Maybe(relationship),
            _,
            _,
            _,
            _,
            expansionMode,
            AllReduceAccumulators.Empty()
          ) => Option((repeat, expand, inlinablePredicates, quantifier, relationship, expansionMode))
        case walk @ RepeatWalk(
            _,
            RewritableWalkRhs(
              expand,
              inlinablePredicates
            ),
            RewritableRepeatQuantifier(quantifier),
            _,
            _,
            _,
            _,
            VariableGroupings.Empty(),
            VariableGroupings.Maybe(relationship),
            _,
            _,
            expansionMode,
            AllReduceAccumulators.Empty()
          ) =>
          Option((walk, expand, inlinablePredicates, quantifier, relationship, expansionMode))
        case _ => None
      }
    }

    private object RewritableTrailRhs {

      /**
       * This extractor ensures it will never allow a non-rewritable case to be rewritten. The opposite is not
       * true. This extractor will sometimes consider rewritable cases non-rewritable. We tolerate false
       * negatives. This is a tradeoff between code complexity and performance, where we tolerate missing out on a few
       * rare cases if it makes the code significantly more maintainable.
       *
       * This extractor relies on several properties of our compilation pipeline, which are not obvious at first.
       *
       * The first property we rely on has to do with the shape of the RHS of Repeat. We assume that very few rewritable
       * cases that survive planning will deviate from the following shape. As a reminder, we require all rewritable
       * QPPs to have a single relationship chain with a single relationship.
       *
       * .repeat(...)
       * .|.filter(..., isRepeatTrailUnique(r))
       * .|.expandAll(...)
       * .|.argument(...)
       * .lhs
       *
       * The second property we rely on has to do with the binding order of variables. QPP pre-filter predicates can
       * contain references to variables of the same MATCH clause, as can VarExpand. During LogicalPlanning we are careful
       * to order plans based on their dependencies on unbound variables. The variables that the Repeat receives are
       * therefor guaranteed to be solved. Because this rewriter just swaps a Repeat for a VarExpand without changing its
       * position in the overarching LogicalPlan, we do not need to worry about binding orders in our rewriter.
       */
      def unapply(repeatRhs: LogicalPlan): Option[(Expand, Seq[Expression])] = {
        val rhsBeforeExpandExtractor: PartialFunction[LogicalPlan, ListSet[Expression]] = self.rhsBeforeExpandExtractor

        repeatRhs match {
          case Selection(
              Ands(predicatesAfterExpand),
              expand @ Expand(rhsBeforeExpandExtractor(predicatesBeforeExpand), _, _, _, _, _, ExpandAll)
            ) =>
            Some((
              expand,
              (predicatesAfterExpand ++ predicatesBeforeExpand)
                .filterNot(_.isInstanceOf[IsRepeatTrailUnique]).toSeq
            ))
          case _ => None
        }
      }
    }

    private object RewritableWalkRhs {

      /**
       * This extractor ensures it will never allow a non-rewritable case to be rewritten. The opposite is not
       * true. This extractor will sometimes consider rewritable cases non-rewritable. We tolerate false
       * negatives. This is a tradeoff between code complexity and performance, where we tolerate missing out on a few
       * rare cases if it makes the code significantly more maintainable.
       *
       * This extractor relies on several properties of our compilation pipeline, which are not obvious at first.
       *
       * The first property we rely on has to do with the shape of the RHS of Walk. We assume that very few rewritable
       * cases that survive planning will deviate from the following shape. As a reminder, we require all rewritable
       * QPPs to have a single relationship chain with a single relationship.
       *
       * .walk(...)
       * .|.expandAll(...)
       * .|.argument(...)
       * .lhs
       *
       * The second property we rely on has to do with the binding order of variables. QPP pre-filter predicates can
       * contain references to variables of the same MATCH clause, as can VarExpand. During LogicalPlanning we are careful
       * to order plans based on their dependencies on unbound variables. The variables that the Walk receives are
       * therefor guaranteed to be solved. Because this rewriter just swaps a Walk for a VarExpand without changing its
       * position in the overarching LogicalPlan, we do not need to worry about binding orders in our rewriter.
       */
      def unapply(walkRhs: LogicalPlan): Option[(Expand, Seq[Expression])] = {
        val rhsBeforeExpandExtractor: PartialFunction[LogicalPlan, ListSet[Expression]] = self.rhsBeforeExpandExtractor

        walkRhs match {
          case Selection(
              Ands(predicatesAfterExpand),
              expand @ Expand(rhsBeforeExpandExtractor(predicatesBeforeExpand), _, _, _, _, _, ExpandAll)
            ) =>
            Some((
              expand,
              (predicatesAfterExpand ++ predicatesBeforeExpand).toSeq
            ))
          case expand @ Expand(rhsBeforeExpandExtractor(predicatesBeforeExpand), _, _, _, _, _, ExpandAll) =>
            Some((
              expand,
              predicatesBeforeExpand.toSeq
            ))
          case _ => None
        }
      }
    }

    private object RewritableRepeatQuantifier {

      def unapply(repetition: Repetition): Option[VarPatternLength] = {
        for {
          min <- Option.when(repetition.min <= Int.MaxValue.toLong)(repetition.min.toInt)
          max <-
            Option.when(repetition.max.limit.getOrElse(0L) <= Int.MaxValue.toLong)(repetition.max.limit.map(_.toInt))
        } yield VarPatternLength(min, max)
      }
    }
  }
}
