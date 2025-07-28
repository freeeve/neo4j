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

import org.mockito.Mockito.when
import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.CypherVersionHelpers.arbitrarySemanticContext
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.semantics.SemanticChecker
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.compiler.phases.LogicalPlanState
import org.neo4j.cypher.internal.compiler.phases.PlannerContext
import org.neo4j.cypher.internal.frontend.phases.rewriting.cnf.flattenBooleanOperators
import org.neo4j.cypher.internal.frontend.phases.rewriting.cnf.simplifyPredicates
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.ExpandStar
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.LabelExpressionPredicateNormalizer
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.NameAllPatternElements
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.NormalizeHasLabelsAndHasType
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.NormalizePredicates
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.QuantifiedPathPatternNodeInsertRewriter
import org.neo4j.cypher.internal.rewriting.rewriters.computeDependenciesForExpressions
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.CancellationChecker
import org.neo4j.cypher.internal.util.CypherExceptionFactory
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.inSequence
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.cypher.internal.util.test_helpers.TestName

class InlineRelationshipTypePredicatesTest extends CypherFunSuite with PlannerQueryRewriterTest
    with TestName {

  override def rewriter(anonymousVariableNameGenerator: AnonymousVariableNameGenerator): Rewriter = {
    val state = mock[LogicalPlanState]
    when(state.anonymousVariableNameGenerator).thenReturn(anonymousVariableNameGenerator)
    val plannerContext = mock[PlannerContext]
    when(plannerContext.cancellationChecker).thenReturn(CancellationChecker.NeverCancelled)
    InlineRelationshipTypePredicates.instance(state, plannerContext)
  }

  override def rewriteAST(
    astOriginal: Statement,
    ceF: CypherExceptionFactory,
    anonVarGen: AnonymousVariableNameGenerator
  ): Statement = {
    val orgAstState = SemanticChecker.check(astOriginal, SemanticState.clean, arbitrarySemanticContext()).state
    astOriginal.endoRewrite(inSequence(
      computeDependenciesForExpressions(orgAstState),
      LabelExpressionPredicateNormalizer.instance,
      NormalizeHasLabelsAndHasType(orgAstState),
      QuantifiedPathPatternNodeInsertRewriter.instance,
      NameAllPatternElements.getRewriter(
        orgAstState,
        Map.empty,
        anonVarGen,
        CancellationChecker.neverCancelled(),
        CypherVersion.Cypher5
      ),
      NormalizePredicates.getRewriter(
        orgAstState,
        Map.empty,
        anonVarGen,
        CancellationChecker.neverCancelled(),
        CypherVersion.Cypher5
      ),
      flattenBooleanOperators.instance(CancellationChecker.NeverCancelled),
      ExpandStar(orgAstState),
      simplifyPredicates(orgAstState, CancellationChecker.neverCancelled())
    ))
  }

  test("  MATCH ()-[r]-() WHERE r:X OR r:Y RETURN *") {
    assertRewrite(testName, "MATCH ()-[r:X|Y]-() RETURN *")
  }

  test("MATCH (a)-[r]-(b)  WHERE r:REL AND a.prop = 123  RETURN *") {
    assertRewrite(testName, "MATCH (a)-[r:REL]-(b) WHERE a.prop = 123 RETURN *")
  }

  test("MATCH ()-[r:!!T]-() RETURN *") {
    assertRewrite(testName, "MATCH ()-[r:T]-() RETURN *")
  }

  test("MATCH ()-[r]-() WHERE r:T RETURN *") {
    assertRewrite(testName, "MATCH ()-[r:T]-() RETURN *")
  }

  test("MATCH ()-[r:T]-() RETURN *") {
    assertIsNotRewritten(testName)
  }

  test("MATCH ( ()-[r:!!T]-() )+ RETURN *") {
    assertRewrite(testName, "MATCH ( ()-[r:T]-() )+ RETURN *")
  }

  test("MATCH ( ()-[r:T]-() )+ RETURN *") {
    assertIsNotRewritten(testName)
  }

  test("MATCH ( ()-[:R]->()-[:!!T]-()-[:!T]-()-[:T]-()-[:R]->() )+ RETURN *") {
    assertRewrite(testName, "MATCH ( ()-[:R]->()-[:T]-()-[:!T]-()-[:T]-()-[:R]->() )+ RETURN *")
  }

  test("MATCH ( ()-[r WHERE r:T]-() )+ RETURN *") {
    assertRewrite(testName, "MATCH ( ()-[r:T]-() )+ RETURN *")
  }

  test("MATCH ()-[r]-() WHERE r:!T RETURN *") {
    assertIsNotRewritten(testName)
  }

  test("MATCH ()-[r]-() WHERE r:% RETURN *") {
    assertIsNotRewritten(testName)
  }

  test("MATCH ()-[r]-() WHERE r:X&Y RETURN *") {
    assertRewrite(testName, "MATCH ()-[r:Y]-() WHERE r:X RETURN *")
  }
}
