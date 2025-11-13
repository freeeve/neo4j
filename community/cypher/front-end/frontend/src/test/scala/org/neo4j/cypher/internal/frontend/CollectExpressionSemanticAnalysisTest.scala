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
package org.neo4j.cypher.internal.frontend

import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.p
import org.neo4j.cypher.internal.ast.Union
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.gqlstatus.GqlHelper.getGql42001_42N07
import org.neo4j.gqlstatus.GqlHelper.getGql42001_42N22
import org.neo4j.gqlstatus.GqlHelper.getGql42001_42N39
import org.neo4j.gqlstatus.GqlHelper.getGql42001_42N57
import org.neo4j.gqlstatus.GqlHelper.getGql42001_42N71

class CollectExpressionSemanticAnalysisTest
    extends CypherFunSuite
    with NameBasedSemanticAnalysisTestSuite {

  test("RETURN COLLECT { MATCH (a) }") {
    run().hasErrors(
      SemanticError(
        getGql42001_42N71(17, 1, 18),
        "Query cannot conclude with MATCH (must be a RETURN clause, a FINISH clause, an update clause, a unit subquery call, or a procedure call with no YIELD).",
        p(17, 1, 18)
      ),
      SemanticError(
        getGql42001_42N22(7, 1, 8),
        "A Collect Expression must end with a single return column.",
        p(7, 1, 8)
      )
    )
  }

  test("RETURN COLLECT { MATCH (n) RETURN n }") {
    run().hasNoErrors
  }

  test(
    """MATCH (m)
      |WHERE COLLECT { OPTIONAL MATCH (a)-[r]->(b) RETURN a.prop } = [5]
      |RETURN m
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH (a)
      |WHERE COLLECT {
      |  MATCH (a)
      |  RETURN a.prop
      |}[0] = a
      |RETURN a
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH (m)
      |WHERE COLLECT { MATCH (a:A)-[r]->(b) USING SCAN a:A RETURN a } = [m]
      |RETURN m
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { SET a.name = 1 }
      |""".stripMargin
  ) {
    run().hasErrors(
      getGql42001_42N57("Collect", 17, 2, 8),
      "A Collect Expression cannot contain any updates",
      InputPosition(17, 2, 8),
      getGql42001_42N22(17, 2, 8),
      "A Collect Expression must end with a single return column.",
      InputPosition(17, 2, 8)
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { MATCH (b) WHERE b.a = a.a DETACH DELETE b }
      |""".stripMargin
  ) {
    run().hasErrors(
      getGql42001_42N57("Collect", 17, 2, 8),
      "A Collect Expression cannot contain any updates",
      InputPosition(17, 2, 8),
      getGql42001_42N22(17, 2, 8),
      "A Collect Expression must end with a single return column.",
      InputPosition(17, 2, 8)
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { MATCH (b) MERGE (b)-[:FOLLOWS]->(:Person) }
      |""".stripMargin
  ) {
    run().hasErrors(
      getGql42001_42N57("Collect", 17, 2, 8),
      "A Collect Expression cannot contain any updates",
      InputPosition(17, 2, 8),
      getGql42001_42N22(17, 2, 8),
      "A Collect Expression must end with a single return column.",
      InputPosition(17, 2, 8)
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { CALL db.labels() YIELD label RETURN label  }
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH (a)
      |RETURN COLLECT {
      |   MATCH (a)-[:KNOWS]->(b)
      |   RETURN b.name as name
      |   UNION ALL
      |   MATCH (a)-[:LOVES]->(b)
      |   RETURN b.name as name
      |}""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { MATCH (m)-[r]->(p), (a)-[r2]-(c) RETURN m.prop }
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { MATCH (a)-->(b) WHERE b.prop = 5 RETURN b }
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MERGE p=(a)-[:T]->()
      |WITH *
      |WHERE COLLECT { WITH p AS n RETURN 1 } = [1]
      |RETURN 1
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH p=(a)-[:T]->()
      |WITH *
      |WHERE COLLECT { RETURN p } = [1]
      |RETURN 1
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH p=(a)-[]-()
      |WITH p
      |WHERE COLLECT { WITH a RETURN 1 } = [1]
      |RETURN 1
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH p=()-[]->()
      |RETURN * ORDER BY COLLECT {
      |  WITH p
      |  RETURN 1
      |}
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """WITH 5 as aNum
      |MATCH (a)
      |RETURN COLLECT {
      |  WITH 6 as aNum
      |  MATCH (a)-->(b) WHERE b.prop = aNum
      |  RETURN a
      |}
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N07("aNum", 54, 4, 13),
      "The variable `aNum` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      InputPosition(54, 4, 13)
    )
  }

  test(
    """WITH 5 as aNum
      |MATCH (a)
      |RETURN COLLECT {
      |  MATCH (a)-->(b) WHERE b.prop = aNum
      |  WITH 6 as aNum
      |  MATCH (b)-->(c) WHERE c.prop = aNum
      |  RETURN a
      |}
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N07("aNum", 92, 5, 13),
      "The variable `aNum` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      InputPosition(92, 5, 13)
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT {
      |  MATCH (a)-->(b)
      |  WITH b as a
      |  MATCH (b)-->(c)
      |  RETURN a
      |}
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N07("a", 57, 4, 13),
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      InputPosition(57, 4, 13)
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT {
      |  MATCH (b)
      |  RETURN b AS a
      |  UNION
      |  MATCH (a)
      |  RETURN a
      |}
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N07("a", 53, 4, 15),
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      InputPosition(53, 4, 15)
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT {
      |  MATCH (a)
      |  RETURN a
      |  UNION ALL
      |  MATCH ()-->(a)
      |  RETURN a
      |  UNION ALL
      |  MATCH (b)
      |  RETURN b AS a
      |}
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N07("a", 128, 10, 15),
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      InputPosition(128, 10, 15)
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT {
      |  MATCH (a)-->(b)
      |  WITH b as c
      |  MATCH (c)-->(d)
      |  RETURN a
      |}
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH (a)
      |RETURN COLLECT {
      |  MATCH (a)
      |  RETURN a
      |  UNION
      |  MATCH (a)
      |  RETURN a
      |  UNION
      |  MATCH (a)
      |  RETURN a
      |}
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH (a)
      |RETURN COLLECT {
      |  MATCH (a)
      |  RETURN a
      |  UNION ALL
      |  MATCH (a)
      |  RETURN a
      |}
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    RETURN CASE
      |       WHEN true THEN 1
      |       ELSE 2
      |    END
      |}[0] > 1
      |RETURN person.name
     """.stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    RETURN n as a
      |    UNION ALL
      |    MATCH (m)
      |    RETURN m as a
      |}[0] > 1
      |RETURN person.name
     """.stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """RETURN COLLECT {
      |    MATCH (n)
      |    RETURN n
      |    UNION
      |    MATCH (n)
      |    RETURN n
      |}
     """.stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    UNION
      |    MATCH (m)
      |}[1] > 1
      |RETURN person.name
     """.stripMargin
  ) {
    run().hasErrors(
      getGql42001_42N71(42, 3, 5),
      "Query cannot conclude with MATCH (must be a RETURN clause, a FINISH clause, an update clause, a unit subquery call, or a procedure call with no YIELD).",
      p(42, 3, 5),
      getGql42001_42N71(66, 5, 5),
      "Query cannot conclude with MATCH (must be a RETURN clause, a FINISH clause, an update clause, a unit subquery call, or a procedure call with no YIELD).",
      p(66, 5, 5),
      getGql42001_42N22(28, 2, 7),
      "A Collect Expression must end with a single return column.",
      InputPosition(28, 2, 7)
    )
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    RETURN n.prop
      |    UNION ALL
      |    MATCH (m)
      |} = [1, 2]
      |RETURN person.name
     """.stripMargin
  ) {
    run().hasErrors(
      getGql42001_42N39(Union.errorParam, 74, 5, 5),
      "All sub queries in an UNION must have the same return column names",
      InputPosition(74, 5, 5),
      getGql42001_42N71(88, 6, 5),
      "Query cannot conclude with MATCH (must be a RETURN clause, a FINISH clause, an update clause, a unit subquery call, or a procedure call with no YIELD).",
      p(88, 6, 5),
      getGql42001_42N22(28, 2, 7),
      "A Collect Expression must end with a single return column.",
      InputPosition(28, 2, 7)
    )
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    UNION ALL
      |    MATCH (m)
      |    RETURN m
      |} = [1, 2]
      |RETURN person.name
     """.stripMargin
  ) {
    run().hasErrors(
      getGql42001_42N39(Union.errorParam, 56, 4, 5),
      "All sub queries in an UNION must have the same return column names",
      InputPosition(56, 4, 5),
      getGql42001_42N71(42, 3, 5),
      "Query cannot conclude with MATCH (must be a RETURN clause, a FINISH clause, an update clause, a unit subquery call, or a procedure call with no YIELD).",
      p(42, 3, 5),
      getGql42001_42N22(28, 2, 7),
      "A Collect Expression must end with a single return column.",
      InputPosition(28, 2, 7)
    )
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    RETURN n
      |    UNION ALL
      |    MATCH (m)
      |} = [1]
      |RETURN person.name
     """.stripMargin
  ) {
    run().hasErrors(
      getGql42001_42N39(Union.errorParam, 69, 5, 5),
      "All sub queries in an UNION must have the same return column names",
      InputPosition(69, 5, 5),
      getGql42001_42N71(83, 6, 5),
      "Query cannot conclude with MATCH (must be a RETURN clause, a FINISH clause, an update clause, a unit subquery call, or a procedure call with no YIELD).",
      p(83, 6, 5),
      getGql42001_42N22(28, 2, 7),
      "A Collect Expression must end with a single return column.",
      InputPosition(28, 2, 7)
    )
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    UNION ALL
      |    MATCH (m)
      |    RETURN m.prop
      |} = [1]
      |RETURN person.name
     """.stripMargin
  ) {
    run().hasErrors(
      getGql42001_42N39(Union.errorParam, 56, 4, 5),
      "All sub queries in an UNION must have the same return column names",
      InputPosition(56, 4, 5),
      getGql42001_42N71(42, 3, 5),
      "Query cannot conclude with MATCH (must be a RETURN clause, a FINISH clause, an update clause, a unit subquery call, or a procedure call with no YIELD).",
      p(42, 3, 5),
      getGql42001_42N22(28, 2, 7),
      "A Collect Expression must end with a single return column.",
      InputPosition(28, 2, 7)
    )
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    UNION ALL
      |    MATCH (m)
      |    RETURN m
      |    UNION ALL
      |    MATCH (l)
      |} = [1]
      |RETURN person.name
     """.stripMargin
  ) {
    run().hasErrors(
      SemanticError(
        getGql42001_42N39(Union.errorParam, 56, 4, 5),
        "All sub queries in an UNION must have the same return column names",
        InputPosition(56, 4, 5)
      ),
      SemanticError(
        getGql42001_42N71(42, 3, 5),
        "Query cannot conclude with MATCH (must be a RETURN clause, a FINISH clause, an update clause, a unit subquery call, or a procedure call with no YIELD).",
        p(42, 3, 5)
      ),
      SemanticError(
        getGql42001_42N71(111, 8, 5),
        "Query cannot conclude with MATCH (must be a RETURN clause, a FINISH clause, an update clause, a unit subquery call, or a procedure call with no YIELD).",
        p(111, 8, 5)
      ),
      SemanticError(
        getGql42001_42N22(28, 2, 7),
        "A Collect Expression must end with a single return column.",
        InputPosition(28, 2, 7)
      )
    )
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    RETURN n
      |    UNION
      |    MATCH (m)
      |    RETURN m
      |    UNION
      |    MATCH (l)
      |    RETURN l
      |} = [1, 2, 3]
      |RETURN person.name
     """.stripMargin
  ) {
    run().hasErrors(
      SemanticError(
        getGql42001_42N39(Union.errorParam, 69, 5, 5),
        "All sub queries in an UNION must have the same return column names",
        p(69, 5, 5)
      ),
      SemanticError(
        getGql42001_42N39(Union.errorParam, 106, 8, 5),
        "All sub queries in an UNION must have the same return column names",
        p(106, 8, 5)
      ),
      SemanticError(
        getGql42001_42N22(28, 2, 7),
        "A Collect Expression must end with a single return column.",
        p(28, 2, 7)
      )
    )
  }

  test(
    """RETURN COLLECT {
      |  MATCH (a)
      |  RETURN *
      |}
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N22(7, 1, 8),
      "A Collect Expression must end with a single return column.",
      InputPosition(7, 1, 8)
    )
  }

  test(
    """RETURN COLLECT {
      |  MATCH (a)
      |  RETURN a.prop1, a.prop2
      |}
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N22(7, 1, 8),
      "A Collect Expression must end with a single return column.",
      InputPosition(7, 1, 8)
    )
  }

  test(
    """MATCH (a)
      |WHERE COLLECT {
      |  MATCH (a)
      |  RETURN *
      |}[0] = a
      |RETURN a
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N22(16, 2, 7),
      "A Collect Expression must end with a single return column.",
      InputPosition(16, 2, 7)
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { MATCH (m)-[r]->(p), (a)-[r2]-(c) RETURN * }
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N22(17, 2, 8),
      "A Collect Expression must end with a single return column.",
      InputPosition(17, 2, 8)
    )
  }

  test(
    """MATCH (n)
      |RETURN COLLECT {
      |   CALL {
      |     MATCH (n)
      |     RETURN 1 AS a
      |   }
      |   RETURN a
      |}
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """
      |MATCH (n)
      |RETURN COLLECT {
      |   CALL {
      |     MATCH (n)
      |     RETURN COLLECT { CALL { MATCH (n) RETURN n AS a } RETURN a } AS a
      |   }
      |   RETURN a
      |}
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """UNWIND [1, 2, 3] AS x
      |CALL {
      |    WITH x
      |    RETURN x * 10 AS y
      |}
      |RETURN COLLECT {
      |   WITH 10 as x
      |   MATCH (n) WHERE n.prop = x
      |   RETURN n.prop
      |}
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N07("x", 96, 7, 15),
      "The variable `x` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      InputPosition(96, 7, 15)
    )
  }

  test(
    """WITH 1 AS x, 2 AS y
      |RETURN COLLECT {
      |   CALL {
      |     WITH y
      |     WITH y, 3 AS x
      |     MATCH (n) WHERE n.prop = x
      |     RETURN 1 AS a
      |   }
      |   RETURN a
      |}
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """WITH 5 AS y
      |RETURN COLLECT {
      |    UNWIND [0, 1, 2] AS x
      |    CALL {
      |        WITH x
      |        RETURN x * 10 AS y
      |    }
      |    RETURN y
      |}
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N07("y", 106, 6, 26),
      "The variable `y` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      InputPosition(106, 6, 26)
    )
  }
}
