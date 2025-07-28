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
package org.neo4j.cypher.internal.rewriting

import org.neo4j.cypher.internal.CypherVersionHelpers
import org.neo4j.cypher.internal.ast.AliasedReturnItem
import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.FreeProjection
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.ShowConstraintsClause
import org.neo4j.cypher.internal.ast.ShowFunctionsClause
import org.neo4j.cypher.internal.ast.ShowIndexesClause
import org.neo4j.cypher.internal.ast.ShowProceduresClause
import org.neo4j.cypher.internal.ast.ShowSettingsClause
import org.neo4j.cypher.internal.ast.ShowTransactionsClause
import org.neo4j.cypher.internal.ast.SingleQuery
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.TerminateTransactionsClause
import org.neo4j.cypher.internal.ast.With
import org.neo4j.cypher.internal.ast.semantics.SemanticState
import org.neo4j.cypher.internal.rewriting.rewriters.astRewriters.ExpandStar
import org.neo4j.cypher.internal.rewriting.rewriters.preparatoryRewriters.ExpandShowWhere
import org.neo4j.cypher.internal.rewriting.rewriters.preparatoryRewriters.NormalizeWithAndReturnClauses
import org.neo4j.cypher.internal.rewriting.rewriters.preparatoryRewriters.RewriteShowQuery
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.Neo4jCypherExceptionFactory
import org.neo4j.cypher.internal.util.inSequence
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class ExpandStarTest extends CypherFunSuite with AstRewritingTestSupport {

  test("rewrites * in return") {
    assertRewrite(
      "match (n) return *",
      "match (n) return n"
    )

    assertRewrite(
      "match (n),(c) return *",
      "match (n),(c) return c,n"
    )

    assertRewrite(
      "match (n)-->(c) return *",
      "match (n)-->(c) return c,n"
    )

    assertRewrite(
      "match (n)-[r]->(c) return *",
      "match (n)-[r]->(c) return c,n,r"
    )

    assertRewrite(
      "create (n) return *",
      "create (n) return n"
    )

    assertRewrite(
      "match p = shortestPath((a)-[r*]->(x)) return *",
      "match p = shortestPath((a)-[r*]->(x)) return a,p,r,x"
    )

    assertRewrite(
      "match p=(a:Start)-->(b) return *",
      "match p=(a:Start)-->(b) return a, b, p"
    )

  }

  test("rewrites * in return in subquery expression") {

    assertRewrite(
      "MATCH (a) WHERE EXISTS { MATCH (b) RETURN * } RETURN a",
      "MATCH (a) WHERE EXISTS { MATCH (b) RETURN b } RETURN a"
    )

    assertRewrite(
      "MATCH (a) WHERE COUNT { MATCH (b) RETURN * } = 3 RETURN a",
      "MATCH (a) WHERE COUNT { MATCH (b) RETURN b } = 3 RETURN a"
    )

    assertRewrite(
      "MATCH (a)-[r]->(b) WHERE EXISTS { MATCH (c)-[r1]->(d) RETURN * } RETURN a",
      "MATCH (a)-[r]->(b) WHERE EXISTS { MATCH (c)-[r1]->(d) RETURN c, d, r1 } RETURN a"
    )

    assertRewrite(
      "MATCH (a)-[r]->(b) WHERE COUNT { MATCH (c)-[r1]->(d) RETURN * } = 3 RETURN a",
      "MATCH (a)-[r]->(b) WHERE COUNT { MATCH (c)-[r1]->(d) RETURN c, d, r1 } = 3 RETURN a"
    )

  }

  test("rewrites * in subquery") {
    assertRewrite(
      "match (n) call (*) { return n as res} return res",
      "match (n) call (n) { return n as res} return res"
    )

    assertRewrite(
      "match (n)-[r]-(m) call (*) { return n as res} return res",
      "match (n)-[r]-(m) call (n, m, r) { return n as res} return res"
    )

    assertRewrite(
      "match (n)-[r]-(m) call (n) { match (a) call (*) { return n as res} return res } return res",
      "match (n)-[r]-(m) call (n) { match (a) call (n, a) { return n as res} return res } return res"
    )

    assertRewrite(
      "match (n)-[r]-(m) call (n) { with 1 AS a call (*) { return n as res} return res } return res",
      "match (n)-[r]-(m) call (n) { with 1 AS a call (n, a) { return n as res} return res } return res"
    )

    assertRewrite(
      "match (n)-[r]-(m) call () { match (a) call (*) { return n as res} return res } return res",
      "match (n)-[r]-(m) call () { match (a) call (a) { return n as res} return res } return res"
    )

    assertRewrite(
      "match (n)-[r]-(m) call (*) { match (a) call (*) { return n as res} return res } return res",
      "match (n)-[r]-(m) call (n, m, r) { match (a) call (n, a, m, r) { return n as res} return res } return res"
    )

    assertRewrite(
      "with 1 as a, 2 as b, 3 as c match(n) with n as m, a, c call(*){return 1 as d} return a, c, d, m",
      "with 1 as a, 2 as b, 3 as c match(n) with n as m, a, c call(a, m, c){return 1 as d} return a, c, d, m"
    )

    assertRewrite(
      "with 1 as a, 2 as b, 3 as c match(n) with n as m, a, c call(*){with 7 as b call(*){return 'hello' as res} return 1 as d} return a, c, d, m",
      "with 1 as a, 2 as b, 3 as c match(n) with n as m, a, c call(a, m, c){with 7 as b call(a, m, b, c){return 'hello' as res} return 1 as d} return a, c, d, m"
    )

    assertRewrite(
      "call (*) { with 1 as x return * } return *",
      "call () { with 1 as x return x } return x"
    )

    assertRewrite(
      "with 1 as a, 2 as b call (*) { with 1 as x return * } return *",
      "with 1 as a, 2 as b call (a, b) { with 1 as x return x } return a, b, x"
    )

    // This query is invalid as it is returning variables already declared in outer scope
    // This is handled elsewhere.
    assertRewrite(
      "with 1 as x call (*) { with 2 as y with x, y return * } return *",
      "with 1 as x call (x) { with 2 as y with x, y return y } return x, y"
    )

    assertRewrite(
      "with 1 as x call (*) { with 2 as y return * } return *",
      "with 1 as x call (x) { with 2 as y return y } return x, y"
    )

    assertRewrite(
      "call (*) { call (*) { call (*) { with 1 as x return * } return * } return * } return *",
      "call () { call () { call () { with 1 as x return x } return x } return x } return x"
    )
  }

  test("rewrites * in with") {
    assertRewrite(
      "match (n) with * return n",
      "match (n) with n return n"
    )

    assertRewrite(
      "match (n),(c) with * return n",
      "match (n),(c) with c,n return n"
    )

    assertRewrite(
      "match (n)-->(c) with * return n",
      "match (n)-->(c) with c,n return n"
    )

    assertRewrite(
      "match (n)-[r]->(c) with * return n",
      "match (n)-[r]->(c) with c,n,r return n"
    )

    assertRewrite(
      "match (n)-[r]->(c) with *, r.pi as x return n",
      "match (n)-[r]->(c) with c, n, r, r.pi as x return n"
    )

    assertRewrite(
      "create (n) with * return n",
      "create (n) with n return n"
    )

    assertRewrite(
      "match p = shortestPath((a)-[r*]->(x)) with * return p",
      "match p = shortestPath((a)-[r*]->(x)) with a,p,r,x return p"
    )
  }

  test("symbol shadowing should be taken into account") {
    assertRewrite(
      "match (a),(x),(y) with a match (b) return *",
      "match (a),(x),(y) with a match (b) return a, b"
    )
  }

  test("keeps listed items during expand") {
    assertRewrite(
      "MATCH (n) WITH *, 1 AS b RETURN *",
      "MATCH (n) WITH n, 1 AS b RETURN b, n"
    )
  }

  test("should rewrite show commands properly") {
    assertRewrite(
      "SHOW FUNCTIONS YIELD *",
      """SHOW FUNCTIONS
        |YIELD name, category, description, signature, isBuiltIn, argumentDescription, returnDescription, aggregating, rolesExecution, rolesBoostedExecution, isDeprecated, deprecatedBy
        |RETURN name, category, description, signature, isBuiltIn, argumentDescription, returnDescription, aggregating, rolesExecution, rolesBoostedExecution, isDeprecated, deprecatedBy""".stripMargin,
      rewriteShowCommand = true,
      showCommandReturnAddedInRewrite = true,
      moveYieldToWith = true
    )

    assertRewrite(
      "SHOW FUNCTIONS YIELD * RETURN *",
      """SHOW FUNCTIONS
        |YIELD name, category, description, signature, isBuiltIn, argumentDescription, returnDescription, aggregating, rolesExecution, rolesBoostedExecution, isDeprecated, deprecatedBy
        |RETURN name, category, description, signature, isBuiltIn, argumentDescription, returnDescription, aggregating, rolesExecution, rolesBoostedExecution, isDeprecated, deprecatedBy""".stripMargin,
      rewriteShowCommand = true,
      moveYieldToWith = true
    )

    assertRewrite(
      "SHOW TRANSACTIONS YIELD * RETURN *",
      """SHOW TRANSACTIONS
        |YIELD database, transactionId, currentQueryId, outerTransactionId, connectionId, clientAddress, username, metaData, currentQuery, parameters, planner, runtime,
        |indexes, startTime, currentQueryStartTime, protocol, requestUri, status, currentQueryStatus, statusDetails, resourceInformation, activeLockCount, currentQueryActiveLockCount,
        |elapsedTime, cpuTime, waitTime, idleTime, currentQueryElapsedTime, currentQueryCpuTime, currentQueryWaitTime, currentQueryIdleTime, currentQueryAllocatedBytes, allocatedDirectBytes,
        |estimatedUsedHeapMemory, pageHits, pageFaults, currentQueryPageHits, currentQueryPageFaults, initializationStackTrace
        |RETURN database, transactionId, currentQueryId, outerTransactionId, connectionId, clientAddress, username, metaData, currentQuery, parameters, planner, runtime,
        |indexes, startTime, currentQueryStartTime, protocol, requestUri, status, currentQueryStatus, statusDetails, resourceInformation, activeLockCount, currentQueryActiveLockCount,
        |elapsedTime, cpuTime, waitTime, idleTime, currentQueryElapsedTime, currentQueryCpuTime, currentQueryWaitTime, currentQueryIdleTime, currentQueryAllocatedBytes, allocatedDirectBytes,
        |estimatedUsedHeapMemory, pageHits, pageFaults, currentQueryPageHits, currentQueryPageFaults, initializationStackTrace""".stripMargin,
      rewriteShowCommand = true,
      moveYieldToWith = true
    )

    assertRewrite(
      "TERMINATE TRANSACTIONS 'db-transaction-123' YIELD *",
      """TERMINATE TRANSACTIONS 'db-transaction-123'
        |YIELD transactionId, username, message
        |RETURN transactionId, username, message""".stripMargin,
      rewriteShowCommand = true,
      showCommandReturnAddedInRewrite = true,
      moveYieldToWith = true
    )

    assertRewrite(
      "SHOW USERS YIELD *",
      """SHOW USERS
        |YIELD user, roles, passwordChangeRequired, suspended, home""".stripMargin,
      rewriteShowCommand = true
    )

    assertRewrite(
      "SHOW USERS WITH AUTH YIELD * RETURN *",
      """SHOW USERS WITH AUTH
        |YIELD user, roles, passwordChangeRequired, suspended, home, provider, auth
        |RETURN user, roles, passwordChangeRequired, suspended, home, provider, auth""".stripMargin,
      rewriteShowCommand = true
    )
  }

  test("uses the position of the clause for variables in new return items") {
    // This is quite important. If the position of a variable in new return item is a previous declaration,
    // that can destroy scoping. In a query like
    // MATCH (owner)
    // WITH HEAD(COLLECT(42)) AS sortValue, owner
    // RETURN *
    // owner would not be scoped/namespaced correctly after having done `isolateAggregation`.
    val wizz = "WITH 1 AS foo "
    val expressionPos = InputPosition(wizz.length, 1, wizz.length + 1)

    val original = prepRewrite(s"${wizz}RETURN *")
    val checkResult = original.semanticCheck.run(SemanticState.clean, CypherVersionHelpers.arbitrarySemanticContext)
    val after = original.rewrite(ExpandStar(checkResult.state))
    val returnItem = after.asInstanceOf[Query].asInstanceOf[SingleQuery]
      .clauses.last.asInstanceOf[Return].returnItems.items.head.asInstanceOf[AliasedReturnItem]
    returnItem.expression.position should equal(expressionPos)
    returnItem.variable.position.offset should equal(expressionPos.offset)
  }

  private def assertRewrite(
    originalQuery: String,
    expectedQuery: String,
    rewriteShowCommand: Boolean = false,
    showCommandReturnAddedInRewrite: Boolean = false,
    moveYieldToWith: Boolean = false
  ): Unit = {
    val original = prepRewrite(originalQuery, rewriteShowCommand)
    val expected = prepRewrite(expectedQuery)
    val expectedUpdatedReturn =
      if (showCommandReturnAddedInRewrite) {
        updateClauses(
          expected,
          clauses => {
            // update `addedInRewrite` flag on the return
            val ret = clauses.last.asInstanceOf[Return]
            val newRet = ret.copy(addedInRewrite = true)(ret.position)
            clauses.dropRight(1) :+ newRet
          }
        )
      } else expected
    val expectedUpdatedYield =
      if (moveYieldToWith) {
        updateClauses(
          expectedUpdatedReturn,
          clauses => {
            // show and terminate commands parses YIELD as WITH *
            clauses.flatMap {
              case s: ShowTransactionsClause =>
                Seq[Clause](s.copy(yieldAll = true, yieldItems = List.empty, yieldWith = None)(s.position)) ++
                  s.yieldWith.map(rewriteWithForShowCommands)

              case t: TerminateTransactionsClause =>
                Seq[Clause](t.copy(yieldAll = true, yieldItems = List.empty, yieldWith = None)(t.position)) ++
                  t.yieldWith.map(rewriteWithForShowCommands)

              case s: ShowSettingsClause =>
                Seq[Clause](s.copy(yieldAll = true, yieldItems = List.empty, yieldWith = None)(s.position)) ++
                  s.yieldWith.map(rewriteWithForShowCommands)

              case s: ShowFunctionsClause =>
                Seq[Clause](s.copy(yieldAll = true, yieldItems = List.empty, yieldWith = None)(s.position)) ++
                  s.yieldWith.map(rewriteWithForShowCommands)

              case s: ShowProceduresClause =>
                Seq[Clause](s.copy(yieldAll = true, yieldItems = List.empty, yieldWith = None)(s.position)) ++
                  s.yieldWith.map(rewriteWithForShowCommands)

              case s: ShowConstraintsClause =>
                Seq[Clause](s.copy(yieldAll = true, yieldItems = List.empty, yieldWith = None)(s.position)) ++
                  s.yieldWith.map(rewriteWithForShowCommands)

              case s: ShowIndexesClause =>
                Seq[Clause](s.copy(yieldAll = true, yieldItems = List.empty, yieldWith = None)(s.position)) ++
                  s.yieldWith.map(rewriteWithForShowCommands)

              case c => Seq(c)
            }
          }
        )
      } else expectedUpdatedReturn

    val checkResult = original.semanticCheck.run(SemanticState.clean, CypherVersionHelpers.arbitrarySemanticContext)
    val rewriter = ExpandStar(checkResult.state)

    val result = original.rewrite(rewriter)
    assert(result === expectedUpdatedYield)
  }

  private def rewriteWithForShowCommands(w: With): With = {
    val returnItems = w.returnItems.defaultOrderOnColumns.map(c =>
      c.map(v => aliasedReturnItem(varFor(v)))
    ).getOrElse(List.empty)
    w.copy(returnItems =
      w.returnItems.copy(
        FreeProjection,
        items = returnItems,
        defaultOrderOnColumns = None
      )(w.returnItems.position)
    )(w.position)
  }

  private def prepRewrite(q: String, rewriteShowCommand: Boolean = false) = {
    val exceptionFactory = Neo4jCypherExceptionFactory(q, None)
    val rewriter =
      if (rewriteShowCommand)
        inSequence(
          NormalizeWithAndReturnClauses(exceptionFactory),
          RewriteShowQuery.instance,
          ExpandShowWhere.instance
        )
      else
        inSequence(NormalizeWithAndReturnClauses(exceptionFactory))
    parse(q, exceptionFactory).endoRewrite(rewriter)
  }

  private def updateClauses(statement: Statement, updateClauses: Seq[Clause] => Seq[Clause]): Statement = {
    val query = statement.asInstanceOf[Query]
    val singleQuery = query.asInstanceOf[SingleQuery]
    val clauses = singleQuery.clauses
    val newClauses = updateClauses(clauses)
    singleQuery.copy(newClauses)(singleQuery.position)
  }
}
