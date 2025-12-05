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
package org.neo4j.cypher.internal.compiler.ast.convert.plannerQuery

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.VariableStringInterpolator
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.compiler.planner.LogicalPlanningTestSupport
import org.neo4j.cypher.internal.expressions.SignedDecimalIntegerLiteral
import org.neo4j.cypher.internal.ir.VectorSearchClause
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class SearchConverterTest extends CypherFunSuite with LogicalPlanningTestSupport {

  override val semanticFeatures: List[SemanticFeature] = List(
    SemanticFeature.VectorSearch
  )

  test(
    "should convert query with Match-Search, converting the search clause into VectorSearch expression and moving it to Selections"
  ) {
    val q = """MATCH (m:Movie)
              |MATCH (movie:Movie)
              |  SEARCH movie IN (
              |    VECTOR INDEX moviePlots
              |    FOR m.embedding
              |    LIMIT 5
              |  )
              |RETURN movie.title AS title """.stripMargin
    val query = buildPlannerQuery(
      CypherVersion.Cypher25,
      q,
      None,
      None,
      compareVersions = false,
      Map.empty
    )

    val vectorSearchExpression = VectorSearchClause(
      bindingVariable = v"movie",
      indexName = "moviePlots",
      embedding = prop("m", "embedding"),
      limit = SignedDecimalIntegerLiteral("5")(pos),
      scoreVariable = None
    )(pos)
    val expectedExpressions = Seq(hasLabels("movie", "Movie"), hasLabels("m", "Movie"))

    query.asSinglePlannerQuery.queryGraph.selections.flatPredicates shouldEqual expectedExpressions
    query.asSinglePlannerQuery.queryGraph.searchClause shouldEqual Some(vectorSearchExpression)
  }

  test("Match Search should allow returning a Score.") {
    val q = """MATCH (m:Movie)
              |MATCH (movie:Movie)
              |  SEARCH movie IN (
              |    VECTOR INDEX moviePlots
              |    FOR m.embedding
              |    LIMIT 5
              |  ) SCORE as score
              |RETURN score, movie.title AS title """.stripMargin

    val query = buildPlannerQuery(
      CypherVersion.Cypher25,
      q,
      None,
      None,
      compareVersions = false,
      Map.empty
    )

    val vectorSearchExpression = VectorSearchClause(
      bindingVariable = v"movie",
      indexName = "moviePlots",
      embedding = prop("m", "embedding"),
      limit = SignedDecimalIntegerLiteral("5")(pos),
      scoreVariable = Some(v"score")
    )(pos)
    val expectedExpressions = Seq(hasLabels("movie", "Movie"), hasLabels("m", "Movie"))

    query.asSinglePlannerQuery.queryGraph.selections.flatPredicates shouldEqual expectedExpressions
    query.asSinglePlannerQuery.queryGraph.searchClause shouldEqual Some(vectorSearchExpression)
  }

}
