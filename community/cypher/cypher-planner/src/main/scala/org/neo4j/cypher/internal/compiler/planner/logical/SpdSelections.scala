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
package org.neo4j.cypher.internal.compiler.planner.logical

import org.neo4j.cypher.internal.expressions.Ands
import org.neo4j.cypher.internal.expressions.CachedProperty
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.RemoteBatchProperties
import org.neo4j.cypher.internal.logical.plans.RemoteBatchPropertiesWithFilter
import org.neo4j.cypher.internal.logical.plans.Selection
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp

object SpdSelections {

  lazy val dummyVar: LogicalVariable = Variable("dummy")(InputPosition.NONE, isIsolated = false)

  case class SpdSelectionAndChild(
    selections: ShardPredicatePushdownPartition,
    child: LogicalPlan
  )

  private object CachedPropertiesToPropertiesRewriter extends Rewriter {

    final private val instance: Rewriter = bottomUp(Rewriter.lift {
      case cachedProperty @ CachedProperty(_, entity, propKey, _, _, _) =>
        Property(entity, propKey)(cachedProperty.position)
    })
    override def apply(v: AnyRef): AnyRef = instance.apply(v)
  }

  def unapply(plan: LogicalPlan): Option[SpdSelectionAndChild] = {
    // Total options to match:
    //   Filter y/n                            -> 2
    //   RemoteBatchProperties y/n             -> 2
    //   RemoteBatchPropertiesWithFilter y/n   -> 2
    //   Filter y/n                            -> 2
    // This gives 2*2*2*2=16 options. But we do not want to match those where `RemoteBatchProperties` and
    // `RemoteBatchPropertiesWithFilter` are absence. There 4 of those.
    // This gives a total of 16-4=12 cases.
    // The order of the cases is important to populate `SpdSelectionAndChild` with all info.
    // Order based on the binary representation of inclusion: for example, 0011 means
    //    Filter (property predicates evaluated on the main shard) is NOT included
    //    RemoteBatchProperties is NOT included
    //    RemoteBatchPropertiesWithFilter IS included
    //    Filter (non-property predicates on the main shard) IS included
    plan match {
      // 1111
      // Filter
      // RemoteBatchProperties
      // RemoteBatchPropertiesWithFilter
      // Filter
      case Selection(
          Ands(propertyPredsOnMain),
          RemoteBatchProperties(
            RemoteBatchPropertiesWithFilter(
              Selection(Ands(nonPropertyPredsOnMain), child),
              rbpwfPropsPredicates,
              _
            ),
            _
          )
        ) =>
        Some(SpdSelectionAndChild(
          ShardPredicatePushdownPartition(
            preFilterBeforePushdown = nonPropertyPredsOnMain,
            filterOnShards = Some(PushedPredicatesDetails(dummyVar, rbpwfPropsPredicates, Set.empty, Set.empty)),
            filterOnMainWithRemoteProperties = propertyPredsOnMain.endoRewrite(CachedPropertiesToPropertiesRewriter)
          ),
          child = child
        ))

      // 1110
      // Filter
      // RemoteBatchProperties
      // RemoteBatchPropertiesWithFilter
      case Selection(
          Ands(propertyPredsOnMain),
          RemoteBatchProperties(RemoteBatchPropertiesWithFilter(child, rbpwfPropsPredicates, _), _)
        ) =>
        Some(SpdSelectionAndChild(
          ShardPredicatePushdownPartition(
            preFilterBeforePushdown = Set.empty,
            filterOnShards = Some(PushedPredicatesDetails(dummyVar, rbpwfPropsPredicates, Set.empty, Set.empty)),
            filterOnMainWithRemoteProperties = propertyPredsOnMain.endoRewrite(CachedPropertiesToPropertiesRewriter)
          ),
          child = child
        ))

      // 1101
      // Filter
      // RemoteBatchProperties
      // Filter
      case Selection(
          Ands(propertyPredsOnMain),
          RemoteBatchProperties(Selection(Ands(nonPropertyPredsOnMain), child), _)
        ) =>
        Some(SpdSelectionAndChild(
          ShardPredicatePushdownPartition(
            preFilterBeforePushdown = nonPropertyPredsOnMain,
            filterOnShards = None,
            filterOnMainWithRemoteProperties = propertyPredsOnMain.endoRewrite(CachedPropertiesToPropertiesRewriter)
          ),
          child = child
        ))

      // 1100
      // Filter
      // RemoteBatchProperties
      case Selection(Ands(propertyPredsOnMain), RemoteBatchProperties(child, _)) =>
        Some(SpdSelectionAndChild(
          ShardPredicatePushdownPartition(
            preFilterBeforePushdown = Set.empty,
            filterOnShards = None,
            filterOnMainWithRemoteProperties = propertyPredsOnMain.endoRewrite(CachedPropertiesToPropertiesRewriter)
          ),
          child = child
        ))

      // 1011
      // Filter
      // RemoteBatchPropertiesWithFilter
      // Filter
      case Selection(
          Ands(propertyPredsOnMain),
          RemoteBatchPropertiesWithFilter(Selection(Ands(nonPropertyPredsOnMain), child), rbpwfPropsPredicates, _)
        ) =>
        Some(SpdSelectionAndChild(
          ShardPredicatePushdownPartition(
            preFilterBeforePushdown = nonPropertyPredsOnMain,
            filterOnShards = Some(PushedPredicatesDetails(dummyVar, rbpwfPropsPredicates, Set.empty, Set.empty)),
            filterOnMainWithRemoteProperties = propertyPredsOnMain.endoRewrite(CachedPropertiesToPropertiesRewriter)
          ),
          child = child
        ))

      // 1010
      // Filter
      // RemoteBatchPropertiesWithFilter
      case Selection(Ands(propertyPredsOnMain), RemoteBatchPropertiesWithFilter(child, rbpwfPropsPredicates, _)) =>
        Some(SpdSelectionAndChild(
          ShardPredicatePushdownPartition(
            preFilterBeforePushdown = Set.empty,
            filterOnShards = Some(PushedPredicatesDetails(dummyVar, rbpwfPropsPredicates, Set.empty, Set.empty)),
            filterOnMainWithRemoteProperties = propertyPredsOnMain.endoRewrite(CachedPropertiesToPropertiesRewriter)
          ),
          child = child
        ))

      // 0111
      // RemoteBatchProperties
      // RemoteBatchPropertiesWithFilter
      // Filter
      case RemoteBatchProperties(
          RemoteBatchPropertiesWithFilter(Selection(Ands(nonPropertyPredsOnMain), child), rbpwfPropsPredicates, _),
          _
        ) =>
        Some(SpdSelectionAndChild(
          ShardPredicatePushdownPartition(
            preFilterBeforePushdown = nonPropertyPredsOnMain,
            filterOnShards = Some(PushedPredicatesDetails(dummyVar, rbpwfPropsPredicates, Set.empty, Set.empty)),
            filterOnMainWithRemoteProperties = Set.empty
          ),
          child = child
        ))

      // 0110
      // RemoteBatchProperties
      // RemoteBatchPropertiesWithFilter
      case RemoteBatchProperties(RemoteBatchPropertiesWithFilter(child, rbpwfPropsPredicates, rbpwfProps), rbpProps) =>
        Some(SpdSelectionAndChild(
          ShardPredicatePushdownPartition(
            preFilterBeforePushdown = Set.empty,
            filterOnShards = Some(PushedPredicatesDetails(dummyVar, rbpwfPropsPredicates, Set.empty, Set.empty)),
            filterOnMainWithRemoteProperties = Set.empty
          ),
          child = child
        ))

      // 0101
      // RemoteBatchProperties
      // Filter
      case RemoteBatchProperties(Selection(Ands(nonPropertyPredsOnMain), child), rbpProps) =>
        Some(SpdSelectionAndChild(
          ShardPredicatePushdownPartition(
            preFilterBeforePushdown = nonPropertyPredsOnMain,
            filterOnShards = None,
            filterOnMainWithRemoteProperties = Set.empty
          ),
          child = child
        ))

      // 0100
      // RemoteBatchProperties
      case RemoteBatchProperties(child, _) =>
        Some(SpdSelectionAndChild(
          ShardPredicatePushdownPartition(
            preFilterBeforePushdown = Set.empty,
            filterOnShards = None,
            filterOnMainWithRemoteProperties = Set.empty
          ),
          child = child
        ))

      // 0011
      // RemoteBatchPropertiesWithFilter
      // Selection
      case RemoteBatchPropertiesWithFilter(
          Selection(Ands(nonPropertyPredsOnMain), child),
          rbpwfPropsPredicates,
          _
        ) =>
        Some(SpdSelectionAndChild(
          ShardPredicatePushdownPartition(
            preFilterBeforePushdown = nonPropertyPredsOnMain,
            filterOnShards = Some(PushedPredicatesDetails(dummyVar, rbpwfPropsPredicates, Set.empty, Set.empty)),
            filterOnMainWithRemoteProperties = Set.empty
          ),
          child = child
        ))

      // 0010
      // RemoteBatchPropertiesWithFilter
      case RemoteBatchPropertiesWithFilter(child, rbpwfPropsPredicates, _) =>
        Some(SpdSelectionAndChild(
          ShardPredicatePushdownPartition(
            preFilterBeforePushdown = Set.empty,
            filterOnShards = Some(PushedPredicatesDetails(dummyVar, rbpwfPropsPredicates, Set.empty, Set.empty)),
            filterOnMainWithRemoteProperties = Set.empty
          ),
          child = child
        ))

      case _ => None
    }
  }
}
