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

import org.neo4j.cypher.internal.compiler.CypherPlannerTestSuite
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningIntegrationTestSupport
import org.neo4j.cypher.internal.expressions.StringLiteral
import org.neo4j.cypher.internal.logical.plans.SemiApply
import org.neo4j.cypher.internal.util.Foldable.FoldableAny

class PendantMembershipToExistsIntegrationTest extends CypherPlannerTestSuite
    with LogicalPlanningIntegrationTestSupport {

  // An LDBC-SNB-shaped slice sufficient to plan IC6 ("tag co-occurrence"). The seed tag is seekable by
  // name, but a popular tag is a hub: far more incoming :HAS_TAG than the average the cost model sees.
  private val planner = plannerBuilder()
    .setAllNodesCardinality(5_000_000)
    .setLabelCardinality("Person", 10_295)
    .setLabelCardinality("Message", 3_055_774)
    .setLabelCardinality("Tag", 16_080)
    .setRelationshipCardinality("()-[:KNOWS]->()", 346_028)
    .setRelationshipCardinality("(:Person)-[:KNOWS]->()", 346_028)
    .setRelationshipCardinality("()-[:KNOWS]->(:Person)", 346_028)
    .setRelationshipCardinality("(:Person)-[:KNOWS]->(:Person)", 346_028)
    .setRelationshipCardinality("()-[:HAS_CREATOR]->()", 3_055_774)
    .setRelationshipCardinality("(:Message)-[:HAS_CREATOR]->()", 3_055_774)
    .setRelationshipCardinality("()-[:HAS_CREATOR]->(:Person)", 3_055_774)
    .setRelationshipCardinality("(:Message)-[:HAS_CREATOR]->(:Person)", 3_055_774)
    .setRelationshipCardinality("()-[:HAS_TAG]->()", 2_932_440)
    .setRelationshipCardinality("(:Message)-[:HAS_TAG]->()", 2_932_440)
    .setRelationshipCardinality("(:Message)-[:HAS_TAG]->(:Tag)", 2_932_440)
    .setRelationshipCardinality("()-[:HAS_TAG]->(:Tag)", 2_932_440)
    .addNodeIndex("Person", List("id"), existsSelectivity = 1.0, uniqueSelectivity = 1.0 / 10_295)
    .addNodeIndex("Tag", List("name"), existsSelectivity = 1.0, uniqueSelectivity = 1.0 / 16_080)
    .build()

  private val ic6 =
    """MATCH (p:Person {id: 42})-[:KNOWS*1..2]-(f:Person) WHERE f.id <> 42
      |WITH DISTINCT f
      |MATCH (f)<-[:HAS_CREATOR]-(post:Message)-[:HAS_TAG]->(:Tag {name: 'Frank_Sinatra'})
      |MATCH (post)-[:HAS_TAG]->(other:Tag) WHERE other.name <> 'Frank_Sinatra'
      |RETURN other.name AS name, count(DISTINCT post) AS cnt
      |ORDER BY cnt DESC, name ASC LIMIT 10""".stripMargin

  test("demotes the anonymous seed tag to a SemiApply so the component is anchored on the bound friend") {
    val plan = planner.plan(ic6)

    // The seed `(:Tag {name})` is no longer a leaf the pattern hangs off; it is an existential
    // membership check whose subquery carries the seed-name literal.
    val seedInSemiApply = plan.folder.findAllByClass[SemiApply].exists { sa =>
      sa.right.folder.treeExists {
        case StringLiteral("Frank_Sinatra") => true
      }
    }
    withClue(plan) {
      seedInSemiApply shouldBe true
    }
  }

  test("does not fire without a bound anchor: a standalone selective membership stays the leaf") {
    // No preceding WITH, so the component has no bound argument. Seeking the single Tag is exactly the
    // right anchor here, so the membership must be left in the pattern (no SemiApply introduced).
    val plan = planner.plan(
      """MATCH (post:Message)-[:HAS_TAG]->(:Tag {name: 'Frank_Sinatra'})
        |RETURN DISTINCT post AS post""".stripMargin
    )
    withClue(plan) {
      plan.folder.findAllByClass[SemiApply] shouldBe empty
    }
  }
}
