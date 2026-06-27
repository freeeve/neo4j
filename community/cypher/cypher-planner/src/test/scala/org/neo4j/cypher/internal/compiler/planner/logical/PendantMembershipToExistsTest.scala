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
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.semantics.SemanticChecker
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.ast.semantics.SemanticTable
import org.neo4j.cypher.internal.compiler.CypherPlannerConfiguration
import org.neo4j.cypher.internal.compiler.CypherPlannerTestSuite
import org.neo4j.cypher.internal.compiler.SyntaxExceptionCreator
import org.neo4j.cypher.internal.compiler.ast.convert.plannerQuery.StatementConverters
import org.neo4j.cypher.internal.compiler.phases.LogicalPlanState
import org.neo4j.cypher.internal.compiler.phases.PlannerContext
import org.neo4j.cypher.internal.frontend.phases.rewriting.cnf.flattenBooleanOperators
import org.neo4j.cypher.internal.ir.PlannerQuery
import org.neo4j.cypher.internal.ir.ast.ExistsIRExpression
import org.neo4j.cypher.internal.parser.AstParserFactory
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.AddElementUniquenessPredicates
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.LabelExpressionPredicateNormalizer
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.NameAllPatternElements
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.NormalizeExistsPatternExpressions
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.NormalizeHasLabelsAndHasType
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.NormalizePredicates
import org.neo4j.cypher.internal.rewriting.rewriters.computeDependenciesForExpressions
import org.neo4j.cypher.internal.rewriting.rewriters.preparatoryRewriters.NormalizeWithAndReturnClauses
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.CancellationChecker
import org.neo4j.cypher.internal.util.CypherExceptionFactory
import org.neo4j.cypher.internal.util.DummyPosition
import org.neo4j.cypher.internal.util.Foldable.FoldableAny
import org.neo4j.cypher.internal.util.Neo4jCypherExceptionFactory
import org.neo4j.cypher.internal.util.Rewritable.RewritableAny
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.helpers.fixedPoint
import org.neo4j.cypher.internal.util.inSequence

class PendantMembershipToExistsTest extends CypherPlannerTestSuite with PlannerQueryRewriterTest {

  override def rewriter(anonymousVariableNameGenerator: AnonymousVariableNameGenerator): Rewriter = {
    val state = mock[LogicalPlanState]
    when(state.anonymousVariableNameGenerator).thenReturn(anonymousVariableNameGenerator)
    val plannerContext = mock[PlannerContext]
    when(plannerContext.cancellationChecker).thenReturn(CancellationChecker.NeverCancelled)
    when(plannerContext.config).thenReturn(mock[CypherPlannerConfiguration])
    PendantMembershipToExists.instance(state, plannerContext)
  }

  override def rewriteAST(
    astOriginal: Statement,
    cypherExceptionFactory: CypherExceptionFactory,
    anonymousVariableNameGenerator: AnonymousVariableNameGenerator
  ): Statement = {
    // Preparatory rewrite that aliases WITH/RETURN items (e.g. `WITH DISTINCT f`), which the planner
    // query converter requires; the production pipeline runs it before logical planning.
    val prepared = astOriginal.endoRewrite(NormalizeWithAndReturnClauses(cypherExceptionFactory))
    val orgAstState = SemanticChecker.check(prepared, SemanticState.clean, arbitrarySemanticContext()).state
    val ast_0 = prepared.endoRewrite(inSequence(
      LabelExpressionPredicateNormalizer.instance,
      NormalizeExistsPatternExpressions(orgAstState),
      NormalizeHasLabelsAndHasType(orgAstState),
      NameAllPatternElements.getRewriter(
        orgAstState,
        Map.empty,
        anonymousVariableNameGenerator,
        CancellationChecker.neverCancelled(),
        CypherVersion.Cypher5
      ),
      // Lift inline `{prop: ...}` and labels into WHERE predicates so the membership node carries a
      // seekable predicate, mirroring the production AST normalization.
      NormalizePredicates.getRewriter(
        orgAstState,
        Map.empty,
        anonymousVariableNameGenerator,
        CancellationChecker.neverCancelled(),
        CypherVersion.Cypher5
      ),
      AddElementUniquenessPredicates.rewriter,
      flattenBooleanOperators.instance(CancellationChecker.NeverCancelled)
    ))
    ast_0.endoRewrite(computeDependenciesForExpressions(
      SemanticChecker.check(ast_0, SemanticState.clean, arbitrarySemanticContext()).state
    ))
  }

  // The bound friend `f` is the cheap anchor; the anonymous, seekable, pendant seed tag is demoted to
  // an existential membership check so it can no longer be chosen as the leaf.
  private val ic6 =
    """MATCH (p:Person)-[:KNOWS]-(f:Person)
       WITH DISTINCT f
       MATCH (f)<-[:HAS_CREATOR]-(post:Message)-[:HAS_TAG]->(:Tag {name: 'seed'})
       MATCH (post)-[:HAS_TAG]->(other:Tag) WHERE other.name <> 'seed'
       RETURN other.name AS name, count(DISTINCT post) AS cnt"""

  test("demotes the anonymous seed-tag membership to an existential subquery") {
    val (before, after) = rewriteAndCompare(ic6)
    before.folder.treeExists { case _: ExistsIRExpression => true } shouldBe false
    after.folder.treeExists { case _: ExistsIRExpression => true } shouldBe true
    after should not equal before
  }

  test("does not fire when the membership node is named and used elsewhere") {
    // `knownTag` is referenced by `commonTag <> knownTag`, so it is neither anonymous nor unused.
    assertIsNotRewritten(
      """MATCH (p:Person)-[:KNOWS]-(f:Person)
         WITH DISTINCT f
         MATCH (f)<-[:HAS_CREATOR]-(post:Message)-[:HAS_TAG]->(knownTag:Tag {name: 'seed'})
         MATCH (post)-[:HAS_TAG]->(other:Tag) WHERE other <> knownTag
         RETURN other.name AS name, count(DISTINCT post) AS cnt"""
    )
  }

  test("does not fire without a bound argument anchor (benefit guard)") {
    // No preceding WITH: seeking the single seed tag is the right anchor, so leave it in the pattern.
    assertIsNotRewritten(
      """MATCH (post:Message)-[:HAS_TAG]->(:Tag {name: 'seed'})
         RETURN DISTINCT post AS post"""
    )
  }

  test("does not fire when the horizon is sensitive to row multiplicity (soundness guard)") {
    // `count(*)` (not DISTINCT) counts the duplicated rows, so demoting the membership could change it.
    assertIsNotRewritten(
      """MATCH (p:Person)-[:KNOWS]-(f:Person)
         WITH DISTINCT f
         MATCH (f)<-[:HAS_CREATOR]-(post:Message)-[:HAS_TAG]->(:Tag {name: 'seed'})
         MATCH (post)-[:HAS_TAG]->(other:Tag) WHERE other.name <> 'seed'
         RETURN other.name AS name, count(*) AS cnt"""
    )
  }

  test("does not fire when the membership node has only a label (no seekable predicate)") {
    // `(:Tag)` with no property is not a single-row anchor; getDegree-style handling already covers it.
    assertIsNotRewritten(
      """MATCH (p:Person)-[:KNOWS]-(f:Person)
         WITH DISTINCT f
         MATCH (f)<-[:HAS_CREATOR]-(post:Message)-[:HAS_TAG]->(:Tag)
         MATCH (post)-[:HAS_TAG]->(other:Tag) WHERE other.name <> 'seed'
         RETURN other.name AS name, count(DISTINCT post) AS cnt"""
    )
  }

  private def rewriteAndCompare(query: String): (PlannerQuery, PlannerQuery) = {
    val gen = new AnonymousVariableNameGenerator()
    val original = plannerQueryFrom(query.stripMargin, gen)
    val rewritten = original.endoRewrite(fixedPoint(CancellationChecker.neverCancelled())(rewriter(gen)))
    (original, rewritten)
  }

  private def plannerQueryFrom(query: String, gen: AnonymousVariableNameGenerator): PlannerQuery = {
    val exceptionFactory = Neo4jCypherExceptionFactory(query, Some(DummyPosition(0)))
    val parsed =
      AstParserFactory(CypherVersion.Legacy.legacyVersion())(query, exceptionFactory, None, Seq()).singleStatement()
    val ast = rewriteAST(parsed, exceptionFactory, gen)
    val onError = SyntaxExceptionCreator.throwOnError(exceptionFactory)
    val result = SemanticChecker.check(ast, SemanticState.clean, arbitrarySemanticContext())
    onError(result.errors)
    val table = SemanticTable(
      types = result.state.typeTable,
      recordedScopes = result.state.recordedScopes.view.mapValues(_.scope).toMap
    )
    StatementConverters.withDefaultConfig.convertToPlannerQuery(
      ast.asInstanceOf[Query],
      table,
      gen,
      CancellationChecker.NeverCancelled
    )
  }
}
