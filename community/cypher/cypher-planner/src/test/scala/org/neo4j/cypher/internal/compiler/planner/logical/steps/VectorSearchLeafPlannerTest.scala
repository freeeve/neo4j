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
package org.neo4j.cypher.internal.compiler.planner.logical.steps

import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.VariableStringInterpolator
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningTestSupport2
import org.neo4j.cypher.internal.compiler.planner.logical.ordering.InterestingOrderConfig
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.SemanticDirection.OUTGOING
import org.neo4j.cypher.internal.ir.PatternRelationship
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.ir.SimplePatternLength
import org.neo4j.cypher.internal.ir.VectorSearchClause
import org.neo4j.cypher.internal.logical.plans.DirectedRelationshipVectorIndexSearch
import org.neo4j.cypher.internal.logical.plans.NodeVectorIndexSearch
import org.neo4j.cypher.internal.util.symbols.CTNode
import org.neo4j.cypher.internal.util.symbols.CTRelationship
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.exceptions.VectorIndexSearchException

class VectorSearchLeafPlannerTest extends CypherFunSuite with LogicalPlanningTestSupport2
    with AstConstructionTestSupport {

  private val embedding = listOfInt(1, 2, 3)
  private val limit = literalInt(10)

  test("plans nodeVectorIndexSearch when skipIds do not contain the binding variable") {
    val bindingVariable = v"movie"
    new givenConfig {
      addTypeToSemanticTable(bindingVariable, CTNode)
      nodeVectorIndexOn("moviePlots", "Movie", "plot")
    } withLogicalPlanningContext { (_, context) =>
      val vectorSearchClause = VectorSearchClause(
        bindingVariable = bindingVariable,
        indexName = "moviePlots",
        embedding = embedding,
        limit = limit,
        scoreVariable = None
      )(pos)
      val qg = QueryGraph(
        patternNodes = Set(bindingVariable),
        searchClause = Some(vectorSearchClause),
        argumentIds = Set.empty
      )

      // When skipIds is empty, the planner should plan the vector search
      val planner = VectorSearchLeafPlanner(skipIDs = Set.empty)
      val plans = planner(qg, InterestingOrderConfig.empty, context)

      plans should have size 1
      val plan = plans.head
      plan shouldBe a[NodeVectorIndexSearch]
      val nodeVectorSearch = plan.asInstanceOf[NodeVectorIndexSearch]
      nodeVectorSearch.idName should equal(bindingVariable)
    }
  }

  test("does not plan nodeVectorIndexSearch when skipIds contain the binding variable") {
    val bindingVariable = v"movie"
    new givenConfig {
      addTypeToSemanticTable(bindingVariable, CTNode)
      nodeVectorIndexOn("moviePlots", "Movie", "plot")
    } withLogicalPlanningContext { (_, context) =>
      val vectorSearchClause = VectorSearchClause(
        bindingVariable = bindingVariable,
        indexName = "moviePlots",
        embedding = embedding,
        limit = limit,
        scoreVariable = None
      )(pos)
      val qg = QueryGraph(
        patternNodes = Set(bindingVariable),
        searchClause = Some(vectorSearchClause),
        argumentIds = Set.empty
      )

      // When skipIds contains the binding variable, the planner should not plan the vector search
      val planner = VectorSearchLeafPlanner(skipIDs = Set(bindingVariable))
      val plans = planner(qg, InterestingOrderConfig.empty, context)

      plans shouldBe empty
    }
  }

  test("plans relationshipVectorIndexSearch when skipIds do not contain the binding variable") {
    val bindingVariable = v"knows"
    new givenConfig {
      addTypeToSemanticTable(bindingVariable, CTRelationship)
      relationshipVectorIndexOn("knowsEmbedding", "KNOWS", "embedding")
    } withLogicalPlanningContext { (_, context) =>
      val from = v"a"
      val to = v"b"

      val patternRelationship = PatternRelationship(
        variable = bindingVariable,
        (from, to),
        dir = OUTGOING,
        types = Seq(RelTypeName("KNOWS")(pos)),
        length = SimplePatternLength
      )

      val vectorSearchClause = VectorSearchClause(
        bindingVariable = bindingVariable,
        indexName = "knowsEmbedding",
        embedding = embedding,
        limit = limit,
        scoreVariable = None
      )(pos)

      val qg = QueryGraph(
        patternNodes = Set(from, to),
        patternRelationships = Set(patternRelationship),
        searchClause = Some(vectorSearchClause),
        argumentIds = Set.empty
      )

      // When skipIds is empty, the planner should plan the relationship vector search
      val planner = VectorSearchLeafPlanner(skipIDs = Set.empty)
      val plans = planner(qg, InterestingOrderConfig.empty, context)

      plans should have size 1
      val plan = plans.head
      plan shouldBe a[DirectedRelationshipVectorIndexSearch]
      val relVectorSearch = plan.asInstanceOf[DirectedRelationshipVectorIndexSearch]
      relVectorSearch.idName shouldEqual Some(bindingVariable)
    }
  }

  test("does not plan relationshipVectorIndexSearch when skipIds contain the binding variable") {
    val bindingVariable = v"knows"
    new givenConfig {
      addTypeToSemanticTable(bindingVariable, CTRelationship)
      relationshipVectorIndexOn("knowsEmbedding", "KNOWS", "embedding")
    } withLogicalPlanningContext { (_, context) =>
      val from = v"a"
      val to = v"b"

      val patternRelationship = PatternRelationship(
        variable = bindingVariable,
        (from, to),
        dir = OUTGOING,
        types = Seq(RelTypeName("KNOWS")(pos)),
        length = SimplePatternLength
      )

      val vectorSearchClause = VectorSearchClause(
        bindingVariable = bindingVariable,
        indexName = "knowsEmbedding",
        embedding = embedding,
        limit = limit,
        scoreVariable = None
      )(pos)

      val qg = QueryGraph(
        patternNodes = Set(from, to),
        patternRelationships = Set(patternRelationship),
        searchClause = Some(vectorSearchClause),
        argumentIds = Set.empty
      )

      // When skipIds contains the binding variable, the planner should not plan the relationship vector search
      val planner = VectorSearchLeafPlanner(skipIDs = Set(bindingVariable))
      val plans = planner(qg, InterestingOrderConfig.empty, context)

      plans shouldBe empty
    }
  }

  test("throws error if embedding has unresolved dependencies not in argumentIds") {
    val bindingVariable = v"movie"
    new givenConfig {
      addTypeToSemanticTable(bindingVariable, CTNode)
      nodeVectorIndexOn("moviePlots", "Movie", "plot")
    } withLogicalPlanningContext { (_, context) =>
      val embeddingWithDeps = listOf(v"x", literalInt(2), literalInt(3))

      val vectorSearchClause = VectorSearchClause(
        bindingVariable = bindingVariable,
        indexName = "moviePlots",
        embedding = embeddingWithDeps,
        limit = limit,
        scoreVariable = None
      )(pos)

      val qg = QueryGraph(
        patternNodes = Set(bindingVariable),
        searchClause = Some(vectorSearchClause),
        argumentIds = Set.empty // x is not in argumentIds
      )

      val planner = VectorSearchLeafPlanner(skipIDs = Set.empty)

      an[AssertionError] should be thrownBy {
        planner(qg, InterestingOrderConfig.empty, context)
      }
    }
  }

  test("throws error if limit has unresolved dependencies not in argumentIds") {
    val bindingVariable = v"movie"
    new givenConfig {
      addTypeToSemanticTable(bindingVariable, CTNode)
      nodeVectorIndexOn("moviePlots", "Movie", "plot")
    } withLogicalPlanningContext { (_, context) =>
      val limitWithDeps = v"y"

      val vectorSearchClause = VectorSearchClause(
        bindingVariable = bindingVariable,
        indexName = "moviePlots",
        embedding = embedding,
        limit = limitWithDeps,
        scoreVariable = None
      )(pos)

      val qg = QueryGraph(
        patternNodes = Set(bindingVariable),
        searchClause = Some(vectorSearchClause),
        argumentIds = Set.empty // y is not in argumentIds
      )

      val planner = VectorSearchLeafPlanner(skipIDs = Set.empty)

      an[AssertionError] should be thrownBy {
        planner(qg, InterestingOrderConfig.empty, context)
      }
    }
  }

  test("throws error if node vector search binding variable type is not CTNode") {
    val bindingVariable = v"knows"
    new givenConfig {
      addTypeToSemanticTable(bindingVariable, CTRelationship)
      nodeVectorIndexOn("moviePlots", "Movie", "plot")
    } withLogicalPlanningContext { (_, context) =>
      val vectorSearchClause = VectorSearchClause(
        bindingVariable = bindingVariable,
        indexName = "moviePlots",
        embedding = embedding,
        limit = limit,
        scoreVariable = None
      )(pos)

      val qg = QueryGraph(
        patternNodes = Set.empty,
        patternRelationships = Set(
          PatternRelationship(
            bindingVariable,
            (v"a", v"b"),
            dir = OUTGOING,
            types = Seq.empty,
            length = SimplePatternLength
          )
        ),
        searchClause = Some(vectorSearchClause),
        argumentIds = Set.empty
      )

      val planner = VectorSearchLeafPlanner(skipIDs = Set.empty)

      an[VectorIndexSearchException] should be thrownBy {
        planner(qg, InterestingOrderConfig.empty, context)
      }
    }
  }

  test("throws error if relationship vector search binding variable type is not CTRelationship") {
    val bindingVariable = v"movie"
    new givenConfig {
      addTypeToSemanticTable(bindingVariable, CTNode)
      relationshipVectorIndexOn("knowsEmbedding", "KNOWS", "embedding")
    } withLogicalPlanningContext { (_, context) =>
      val vectorSearchClause = VectorSearchClause(
        bindingVariable = bindingVariable,
        indexName = "knowsEmbedding",
        embedding = embedding,
        limit = limit,
        scoreVariable = None
      )(pos)

      val qg = QueryGraph(
        patternNodes = Set(bindingVariable),
        searchClause = Some(vectorSearchClause),
        argumentIds = Set.empty
      )

      val planner = VectorSearchLeafPlanner(skipIDs = Set.empty)

      an[VectorIndexSearchException] should be thrownBy {
        planner(qg, InterestingOrderConfig.empty, context)
      }
    }
  }

  test("plans nodeVectorIndexSearch with score variable") {
    val bindingVariable = v"movie"
    new givenConfig {
      addTypeToSemanticTable(bindingVariable, CTNode)
      nodeVectorIndexOn("moviePlots", "Movie", "plot")
    } withLogicalPlanningContext { (_, context) =>
      val scoreVariable = v"similarity"
      val vectorSearchClause = VectorSearchClause(
        bindingVariable = bindingVariable,
        indexName = "moviePlots",
        embedding = embedding,
        limit = limit,
        scoreVariable = Some(scoreVariable)
      )(pos)

      val qg = QueryGraph(
        patternNodes = Set(bindingVariable),
        searchClause = Some(vectorSearchClause),
        argumentIds = Set.empty
      )

      val planner = VectorSearchLeafPlanner(skipIDs = Set.empty)
      val plans = planner(qg, InterestingOrderConfig.empty, context)

      plans should have size 1
      val plan = plans.head
      plan shouldBe a[NodeVectorIndexSearch]
      val nodeVectorSearch = plan.asInstanceOf[NodeVectorIndexSearch]
      nodeVectorSearch.score should equal(Some(scoreVariable))
    }
  }

  test("plans nodeVectorIndexSearch with resolved dependencies in argumentIds") {
    val bindingVariable = v"movie"
    val parameter = v"embeddingParam"
    new givenConfig {
      addTypeToSemanticTable(bindingVariable, CTNode)
      addTypeToSemanticTable(parameter, CTNode)
      nodeVectorIndexOn("moviePlots", "Movie", "plot")
    } withLogicalPlanningContext { (_, context) =>
      val embeddingWithDeps = v"embeddingParam"

      val vectorSearchClause = VectorSearchClause(
        bindingVariable = bindingVariable,
        indexName = "moviePlots",
        embedding = embeddingWithDeps,
        limit = limit,
        scoreVariable = None
      )(pos)

      val qg = QueryGraph(
        patternNodes = Set(bindingVariable),
        searchClause = Some(vectorSearchClause),
        argumentIds = Set(parameter) // Parameter is in argumentIds, so no error should occur
      )

      val planner = VectorSearchLeafPlanner(skipIDs = Set.empty)
      val plans = planner(qg, InterestingOrderConfig.empty, context)

      plans should have size 1
      plans.head shouldBe a[NodeVectorIndexSearch]
    }
  }
}
