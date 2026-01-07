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

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport.p
import org.neo4j.cypher.internal.ast.Union
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.gqlstatus.GqlHelper.getGql42001_42I58
import org.neo4j.gqlstatus.GqlHelper.getGql42001_42N07
import org.neo4j.gqlstatus.GqlHelper.getGql42001_42N39
import org.neo4j.gqlstatus.GqlHelper.getGql42001_42N57

class ExistsExpressionSemanticAnalysisTest
    extends CypherFunSuite
    with NameBasedSemanticAnalysisTestSuite {

  test("""MATCH (a)
         |RETURN EXISTS { CREATE (b) }
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N57("Exists", 17, 2, 8),
      "An Exists Expression cannot contain any updates",
      p(17, 2, 8)
    )
  }

  test("""MATCH (m)
         |WHERE EXISTS { OPTIONAL MATCH (a)-[r]->(b) }
         |RETURN m
         |""".stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (a)
         |WHERE EXISTS {
         |  MATCH (a)
         |  RETURN *
         |}
         |RETURN a
         |""".stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (m)
         |WHERE EXISTS { MATCH (a:A)-[r]->(b) USING SCAN a:A }
         |RETURN m
         |""".stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (a)
         |RETURN EXISTS { SET a.name = 1 }
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N57("Exists", 17, 2, 8),
      "An Exists Expression cannot contain any updates",
      p(17, 2, 8)
    )
  }

  test("""MATCH (a)
         |RETURN EXISTS { MATCH (b) WHERE b.a = a.a DETACH DELETE b }
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N57("Exists", 17, 2, 8),
      "An Exists Expression cannot contain any updates",
      p(17, 2, 8)
    )
  }

  test("""MATCH (a)
         |RETURN EXISTS { MATCH (b) MERGE (b)-[:FOLLOWS]->(:Person) }
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N57("Exists", 17, 2, 8),
      "An Exists Expression cannot contain any updates",
      p(17, 2, 8)
    )
  }

  test("""MATCH (a)
         |RETURN EXISTS { CALL db.labels() }
         |""".stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (a)
         |RETURN EXISTS {
         |   MATCH (a)-[:KNOWS]->(b)
         |   RETURN b.name as name
         |   UNION ALL
         |   MATCH (a)-[:LOVES]->(b)
         |   RETURN b.name as name
         |}""".stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (a)
         |RETURN EXISTS { MATCH (m)-[r]->(p), (a)-[r2]-(c) }
         |""".stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (a)
         |RETURN EXISTS { (a)-->(b) WHERE b.prop = 5  }
         |""".stripMargin) {
    run().hasNoErrors
  }

  test("""MERGE p=(a)-[:T]->()
         |WITH *
         |WHERE EXISTS { WITH p AS n }
         |RETURN 1
         |""".stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH p=(a)-[:T]->()
         |WITH *
         |WHERE EXISTS { RETURN p }
         |RETURN 1
         |""".stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH p=(a)-[]-()
         |WITH p
         |WHERE EXISTS { WITH a }
         |RETURN 1
         |""".stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH p=()-[]->()
         |RETURN * ORDER BY EXISTS {
         |  WITH p
         |  RETURN 1
         |}
         |""".stripMargin) {
    run().hasNoErrors
  }

  test("""WITH 5 as aNum
         |MATCH (a)
         |RETURN EXISTS {
         |  WITH 6 as aNum
         |  MATCH (a)-->(b) WHERE b.prop = aNum
         |  RETURN a
         |}
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N07("aNum", 53, 4, 13),
      "The variable `aNum` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      p(53, 4, 13)
    )
  }

  test("""WITH 5 as aNum
         |MATCH (a)
         |RETURN EXISTS {
         |  MATCH (a)-->(b) WHERE b.prop = aNum
         |  WITH 6 as aNum
         |  MATCH (b)-->(c) WHERE c.prop = aNum
         |  RETURN a
         |}
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N07("aNum", 91, 5, 13),
      "The variable `aNum` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      p(91, 5, 13)
    )
  }

  test("""MATCH (a)
         |RETURN EXISTS {
         |  MATCH (a)-->(b)
         |  WITH b as a
         |  MATCH (b)-->(c)
         |  RETURN a
         |}
         |""".stripMargin) {
    run().hasError(
      getGql42001_42N07("a", 56, 4, 13),
      "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      p(56, 4, 13)
    )
  }

  test(
    """MATCH (a)
      |RETURN EXISTS {
      |  MATCH (b)
      |  RETURN b AS a
      |  UNION
      |  MATCH (a)
      |  RETURN a
      |}
      |""".stripMargin
  ) {
    run().hasAtLeastOneGqlErrorIn(_ =>
      Seq(
        // Semantic Analysis
        (
          getGql42001_42N07("a", 52, 4, 15),
          "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
          p(52, 4, 15)
        ),
        // Variable Checker
        (
          getGql42001_42N07("a", 40, 4, 3),
          "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
          p(40, 4, 3)
        )
      )
    )
  }

  test(
    """MATCH (a)
      |RETURN EXISTS {
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
    run().hasAtLeastOneGqlErrorIn(_ =>
      Seq(
        // Semantic Analysis
        (
          getGql42001_42N07("a", 127, 10, 15),
          "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
          p(127, 10, 15)
        ),
        // Variable Checker
        (
          getGql42001_42N07("a", 115, 10, 3),
          "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
          p(115, 10, 3)
        )
      )
    )
  }

  test(
    """MATCH (a) ((n)-->() WHERE EXISTS { RETURN 1 AS a })+
      |RETURN *
      |""".stripMargin
  ) {
    run().hasAtLeastOneGqlErrorIn(_ =>
      Seq(
        // Semantic Analysis
        (
          getGql42001_42N07("a", 47, 1, 48),
          "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
          p(47, 1, 48)
        ),
        // Variable Checker
        (
          getGql42001_42N07("a", 35, 1, 36),
          "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
          p(35, 1, 36)
        )
      )
    )
  }

  test(
    """MATCH (n)
      |RETURN EXISTS {
      |   CALL {
      |     MATCH (n)
      |     RETURN 1 AS a
      |   }
      |}
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """
      |MATCH (n)
      |RETURN EXISTS {
      |   CALL {
      |     MATCH (n)
      |     RETURN EXISTS { CALL { MATCH (n) RETURN n AS a } RETURN a } AS a
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
      |RETURN EXISTS {
      |   WITH 10 as x
      |   MATCH (n) WHERE n.prop = x
      |}
      |""".stripMargin
  ) {
    run().hasError(
      getGql42001_42N07("x", 95, 7, 15),
      "The variable `x` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      p(95, 7, 15)
    )
  }

  test(
    """WITH 1 AS x, 2 AS y
      |RETURN EXISTS {
      |   CALL {
      |     WITH y
      |     WITH y, 3 AS x
      |     MATCH (n) WHERE n.prop = x
      |     RETURN 1 AS a
      |   }
      |}
      |""".stripMargin
  ) {
    run().hasNoErrors
  }

  test(
    """WITH 5 AS y
      |RETURN EXISTS {
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
      getGql42001_42N07("y", 105, 6, 26),
      "The variable `y` is shadowing a variable with the same name from the outer scope and needs to be renamed",
      p(105, 6, 26)
    )
  }

  test("""MATCH (a)
         |RETURN EXISTS {
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
      |RETURN EXISTS {
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
      |RETURN EXISTS {
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
         |WHERE EXISTS {
         |    RETURN CASE
         |       WHEN true THEN 1
         |       ELSE 2
         |    END
         |}
         |RETURN person.name
     """.stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (person:Person)
         |WHERE EXISTS {
         |    MATCH (n)
         |    UNION
         |    MATCH (m)
         |}
         |RETURN person.name
     """.stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (person:Person)
         |WHERE EXISTS {
         |    MATCH (n)
         |    UNION ALL
         |    MATCH (m)
         |}
         |RETURN person.name
     """.stripMargin) {
    run().hasNoErrors
  }

  test("""MATCH (person:Person)
         |WHERE EXISTS {
         |    MATCH (n)
         |    RETURN n.prop
         |    UNION ALL
         |    MATCH (m)
         |}
         |RETURN person.name
     """.stripMargin) {
    run().hasError(
      getGql42001_42N39(Union.errorParam, 73, 5, 5),
      "All sub queries in an UNION must have the same return column names",
      p(73, 5, 5)
    )
  }

  test("""MATCH (person:Person)
         |WHERE EXISTS {
         |    MATCH (n)
         |    UNION ALL
         |    MATCH (m)
         |    RETURN m
         |}
         |RETURN person.name
     """.stripMargin) {
    run().hasError(
      getGql42001_42N39(Union.errorParam, 55, 4, 5),
      "All sub queries in an UNION must have the same return column names",
      p(55, 4, 5)
    )
  }

  test("""MATCH (person:Person)
         |WHERE EXISTS {
         |    MATCH (n)
         |    RETURN n
         |    UNION
         |    MATCH (m)
         |}
         |RETURN person.name
     """.stripMargin) {
    run().hasError(
      getGql42001_42N39(Union.errorParam, 68, 5, 5),
      "All sub queries in an UNION must have the same return column names",
      p(68, 5, 5)
    )
  }

  test("""MATCH (person:Person)
         |WHERE EXISTS {
         |    MATCH (n)
         |    UNION
         |    MATCH (m)
         |    RETURN m.prop
         |}
         |RETURN person.name
     """.stripMargin) {
    run().hasError(
      getGql42001_42N39(Union.errorParam, 55, 4, 5),
      "All sub queries in an UNION must have the same return column names",
      p(55, 4, 5)
    )
  }

  test("""MATCH (person:Person)
         |WHERE EXISTS {
         |    MATCH (n)
         |    UNION
         |    MATCH (m)
         |    RETURN m
         |    UNION
         |    MATCH (l)
         |}
         |RETURN person.name
     """.stripMargin) {
    run().hasError(
      getGql42001_42N39(Union.errorParam, 55, 4, 5),
      "All sub queries in an UNION must have the same return column names",
      p(55, 4, 5)
    )
  }

  test("""MATCH (person:Person)
         |WHERE EXISTS {
         |    MATCH (n)
         |    RETURN n
         |    UNION
         |    MATCH (m)
         |    RETURN m
         |    UNION
         |    MATCH (l)
         |    RETURN l
         |}
         |RETURN person.name
     """.stripMargin) {
    run().hasErrors(
      SemanticError(
        getGql42001_42N39(Union.errorParam, 68, 5, 5),
        "All sub queries in an UNION must have the same return column names",
        p(68, 5, 5)
      ),
      SemanticError(
        getGql42001_42N39(Union.errorParam, 105, 8, 5),
        "All sub queries in an UNION must have the same return column names",
        p(105, 8, 5)
      )
    )
  }

  test("should raise an error on cross reference in graph pattern with exists expression") {
    run(
      """CREATE (a), (b {prop: EXISTS { MATCH (n) WHERE n.prop = a.prop } })
        |RETURN a""".stripMargin,
      disabledVersions = Set(CypherVersion.Cypher5)
    ).hasErrors(
      SemanticError(
        getGql42001_42I58("a", 56, 1, 57),
        "Creating an entity (a) and referencing that entity in a property definition in the same CREATE is not allowed. Only reference variables created in earlier clauses.",
        p(56, 1, 57)
      )
    )
  }

  test("should raise an error on cross reference in graph pattern without exists expression") {
    run(
      """CREATE (a), (b {prop: a.prop})
        |RETURN a""".stripMargin,
      disabledVersions = Set(CypherVersion.Cypher5)
    ).hasErrors(
      SemanticError(
        getGql42001_42I58("a", 22, 1, 23),
        "Creating an entity (a) and referencing that entity in a property definition in the same CREATE is not allowed. Only reference variables created in earlier clauses.",
        p(22, 1, 23)
      )
    )
  }
}
