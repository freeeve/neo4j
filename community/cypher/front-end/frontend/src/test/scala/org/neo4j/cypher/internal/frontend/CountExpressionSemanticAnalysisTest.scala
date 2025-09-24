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

import org.neo4j.cypher.internal.ast.Ast.p
import org.neo4j.cypher.internal.ast.Union
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.gqlstatus.GqlHelper.getGql42001_42N07
import org.neo4j.gqlstatus.GqlHelper.getGql42001_42N39
import org.neo4j.gqlstatus.GqlHelper.getGql42001_42N57
import org.neo4j.gqlstatus.GqlHelper.getGql42001_42N71

class CountExpressionSemanticAnalysisTest
    extends CypherFunSuite
    with NameBasedSemanticAnalysisTestSuite {

  test("""MATCH (a)
         |RETURN COUNT { CREATE (b) } > 1
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N57("Count", 17, 2, 8),
      "A Count Expression cannot contain any updates",
      p(17, 2, 8)
    )
  }

  test("""MATCH (m)
         |WHERE COUNT { OPTIONAL MATCH (a)-[r]->(b) } > 1
         |RETURN m
         |""".stripMargin) {
    run().hasNoErrors
  }

  test(
    """MATCH (a)
      |WHERE COUNT {
      |  MATCH (a)
      |  RETURN *
      |} > 3
      |RETURN a
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test("""MATCH (m)
         |WHERE COUNT { MATCH (a:A)-[r]->(b) USING SCAN a:A } > 1
         |RETURN m
         |""".stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (a)
         |RETURN COUNT { SET a.name = 1 } > 1
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N57("Count", 17, 2, 8),
      "A Count Expression cannot contain any updates",
      p(17, 2, 8)
    )
  }

  test("""MATCH (a)
         |RETURN COUNT { MATCH (b) WHERE b.a = a.a DETACH DELETE b } > 1
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N57("Count", 17, 2, 8),
      "A Count Expression cannot contain any updates",
      p(17, 2, 8)
    )
  }

  test("""MATCH (a)
         |RETURN COUNT { MATCH (b) MERGE (b)-[:FOLLOWS]->(:Person) } > 1
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N57("Count", 17, 2, 8),
      "A Count Expression cannot contain any updates",
      p(17, 2, 8)
    )
  }

  test("""MATCH (a)
         |RETURN COUNT { CALL db.labels() } > 1
         |""".stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (a)
         |RETURN COUNT {
         |   MATCH (a)-[:KNOWS]->(b)
         |   RETURN b.name as name
         |   UNION ALL
         |   MATCH (a)-[:LOVES]->(b)
         |   RETURN b.name as name
         |}""".stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (a)
         |RETURN COUNT { MATCH (m)-[r]->(p), (a)-[r2]-(c) }
         |""".stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (a)
         |RETURN COUNT { (a)-->(b) WHERE b.prop = 5  }
         |""".stripMargin) {
    run().hasNoErrors
  }

  test(
    """MERGE p=(a)-[:T]->()
      |WITH *
      |WHERE COUNT { WITH p AS n } = 3
      |RETURN 1
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH p=(a)-[:T]->()
      |WITH *
      |WHERE COUNT { RETURN p } = 3
      |RETURN 1
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH p=(a)-[]-()
      |WITH p
      |WHERE COUNT { WITH a } = 3
      |RETURN 1
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """MATCH p=()-[]->()
      |RETURN * ORDER BY COUNT {
      |  WITH p
      |  RETURN 1
      |}
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test("""WITH 5 as aNum
         |MATCH (a)
         |RETURN COUNT {
         |  WITH 6 as aNum
         |  MATCH (a)-->(b) WHERE b.prop = aNum
         |  RETURN a
         |}
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N07("aNum", 52, 4, 13),
      "The variable `aNum` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      p(52, 4, 13)
    )
  }

  test("""WITH 5 as aNum
         |MATCH (a)
         |RETURN COUNT {
         |  MATCH (a)-->(b) WHERE b.prop = aNum
         |  WITH 6 as aNum
         |  MATCH (b)-->(c) WHERE c.prop = aNum
         |  RETURN a
         |}
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N07("aNum", 90, 5, 13),
      "The variable `aNum` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      p(90, 5, 13)
    )
  }

  test("""MATCH (a)
         |RETURN COUNT {
         |  MATCH (a)-->(b)
         |  WITH b as a
         |  MATCH (b)-->(c)
         |  RETURN a
         |}
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N07("a", 55, 4, 13),
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      p(55, 4, 13)
    )
  }

  test(
    """MATCH (a)
      |RETURN COUNT {
      |  MATCH (b)
      |  RETURN b AS a
      |  UNION
      |  MATCH (a)
      |  RETURN a
      |}
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N07("a", 51, 4, 15),
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      p(51, 4, 15)
    )
  }

  test(
    """MATCH (a)
      |RETURN COUNT {
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
      getGql42001_42N07("a", 126, 10, 15),
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      p(126, 10, 15)
    )
  }

  test("""MATCH (a)
         |RETURN COUNT {
         |  MATCH (a)-->(b)
         |  WITH b as c
         |  MATCH (c)-->(d)
         |  RETURN a
         |}
         |""".stripMargin) {
    run().hasNoErrors
  }

  test(
    """MATCH (a)
      |RETURN COUNT {
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
      |RETURN COUNT {
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

  test("""MATCH (person:Person)
         |WHERE COUNT {
         |    RETURN CASE
         |       WHEN true THEN 1
         |       ELSE 2
         |    END
         |} > 1
         |RETURN person.name
     """.stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (person:Person)
         |WHERE COUNT {
         |    MATCH (n)
         |    UNION ALL
         |    MATCH (m)
         |} > 1
         |RETURN person.name
     """.stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (person:Person)
         |WHERE COUNT {
         |    MATCH (n)
         |    UNION
         |    MATCH (m)
         |} > 1
         |RETURN person.name
     """.stripMargin) {
    run().hasErrors(
      getGql42001_42N71(40, 3, 5),
      "Query cannot conclude with MATCH (must be a RETURN clause, a FINISH clause, an update clause, a unit subquery call, or a procedure call with no YIELD).",
      p(40, 3, 5),
      getGql42001_42N71(64, 5, 5),
      "Query cannot conclude with MATCH (must be a RETURN clause, a FINISH clause, an update clause, a unit subquery call, or a procedure call with no YIELD).",
      p(64, 5, 5)
    )
  }

  test("""MATCH (person:Person)
         |WHERE COUNT {
         |    MATCH (n)
         |    RETURN n.prop
         |    UNION ALL
         |    MATCH (m)
         |} > 1
         |RETURN person.name
     """.stripMargin) {
    run().hasError(
      getGql42001_42N39(Union.errorParam, 72, 5, 5),
      "All sub queries in an UNION must have the same return column names",
      p(72, 5, 5)
    )
  }

  test("""MATCH (person:Person)
         |WHERE COUNT {
         |    MATCH (n)
         |    UNION ALL
         |    MATCH (m)
         |    RETURN m
         |} > 1
         |RETURN person.name
     """.stripMargin) {
    run().hasError(
      getGql42001_42N39(Union.errorParam, 54, 4, 5),
      "All sub queries in an UNION must have the same return column names",
      p(54, 4, 5)
    )
  }

  test("""MATCH (person:Person)
         |WHERE COUNT {
         |    MATCH (n)
         |    RETURN n
         |    UNION ALL
         |    MATCH (m)
         |} > 1
         |RETURN person.name
     """.stripMargin) {
    run().hasError(
      getGql42001_42N39(Union.errorParam, 67, 5, 5),
      "All sub queries in an UNION must have the same return column names",
      p(67, 5, 5)
    )
  }

  test("""MATCH (person:Person)
         |WHERE COUNT {
         |    MATCH (n)
         |    UNION ALL
         |    MATCH (m)
         |    RETURN m.prop
         |} > 1
         |RETURN person.name
     """.stripMargin) {
    run().hasError(
      getGql42001_42N39(Union.errorParam, 54, 4, 5),
      "All sub queries in an UNION must have the same return column names",
      p(54, 4, 5)
    )
  }

  test("""MATCH (person:Person)
         |WHERE COUNT {
         |    MATCH (n)
         |    UNION ALL
         |    MATCH (m)
         |    RETURN m
         |    UNION ALL
         |    MATCH (l)
         |} > 1
         |RETURN person.name
     """.stripMargin) {
    run().hasError(
      getGql42001_42N39(Union.errorParam, 54, 4, 5),
      "All sub queries in an UNION must have the same return column names",
      p(54, 4, 5)
    )
  }

  test("""MATCH (person:Person)
         |WHERE COUNT {
         |    MATCH (n)
         |    RETURN n
         |    UNION
         |    MATCH (m)
         |    RETURN m
         |    UNION
         |    MATCH (l)
         |    RETURN l
         |} > 5
         |RETURN person.name
     """.stripMargin) {
    run().hasErrors(
      SemanticError(
        getGql42001_42N39(Union.errorParam, 67, 5, 5),
        "All sub queries in an UNION must have the same return column names",
        p(67, 5, 5)
      ),
      SemanticError(
        getGql42001_42N39(Union.errorParam, 104, 8, 5),
        "All sub queries in an UNION must have the same return column names",
        p(104, 8, 5)
      )
    )
  }

  test(
    """MATCH (n)
      |RETURN COUNT {
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
      |RETURN COUNT {
      |   CALL {
      |     MATCH (n)
      |     RETURN COUNT { CALL { MATCH (n) RETURN n AS a } RETURN a } AS a
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
      |RETURN COUNT {
      |   WITH 10 as x
      |   MATCH (n) WHERE n.prop = x
      |   RETURN n.prop
      |}
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N07("x", 94, 7, 15),
      "The variable `x` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      p(94, 7, 15)
    )
  }

  test(
    """WITH 1 AS x, 2 AS y
      |RETURN COUNT {
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
      |RETURN COUNT {
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
      getGql42001_42N07("y", 104, 6, 26),
      "The variable `y` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      p(104, 6, 26)
    )
  }
}
