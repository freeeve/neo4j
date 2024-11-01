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
import org.neo4j.cypher.internal.ast.Ast.p
import org.neo4j.cypher.internal.ast.semantics.SemanticError.invalidEntityType
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.gqlstatus.GqlParams

class SetClauseSemanticAnalysisTest
    extends CypherFunSuite
    with NameBasedSemanticAnalysisTestSuite {

  test("MATCH (n) SET n[\"prop\"] = 3") {
    run().hasNoErrors
  }

  test("MATCH (n)-[r]->(m) SET (CASE WHEN n.prop = 5 THEN n ELSE r END)[\"prop\"] = 3") {
    run().hasNoErrors
  }

  test("MATCH (n), (m) SET n[1] = 3") {
    run().hasErrors(invalidEntityType(
      "Integer",
      "node or relationship property key",
      List("String"),
      "Type mismatch: node or relationship property key must be given as String, but was Integer",
      p(21, 1, 22)
    ))
  }

  test("MATCH (n)-[r]->(m) SET r[5.0] = 3") {
    run().hasErrors(invalidEntityType(
      "Float",
      "node or relationship property key",
      List("String"),
      "Type mismatch: node or relationship property key must be given as String, but was Float",
      p(25, 1, 26)
    ))
  }

  test("WITH 5 AS var MATCH (n) SET n[var] = 3") {
    run().hasErrors(invalidEntityType(
      "Integer",
      "node or relationship property key",
      List("String"),
      "Type mismatch: node or relationship property key must be given as String, but was Integer",
      p(30, 1, 31)
    ))
  }

  test("WITH {key: 1} AS var SET var['key'] = 3") {
    val msg = "Type mismatch: expected Node or Relationship but was Map"
    run().hasErrors(invalidEntityType(
      "Map",
      GqlParams.StringParam.ident.process("var"),
      List("Node", "Relationship"),
      msg,
      p(25, 1, 26)
    ))
  }

  test("WITH \"prop\" as prop MERGE (n) ON MATCH SET n[prop] = 3 ON CREATE SET n[prop] = 4") {
    run().hasNoErrors
  }

  test("MATCH (n) WITH {key: n} AS var SET (var.key)['prop'] = 3") {
    run().hasNoErrors
  }

  test("MATCH (n) SET n[\"prop\"] = 4") {
    run().hasNoErrors
  }

  test("MATCH (n) SET n[$param] = 'hi'") {
    run().hasNoErrors
  }

  test("MATCH ()-[r]->() SET r[\"prop\"] = 4") {
    run().hasNoErrors
  }

  test("MATCH (n) SET n.prop = 4") {
    run().hasNoErrors
  }

  test("MATCH (n)-[r]->(m) SET n = r") {
    run().hasSemanticErrorsIn {
      case CypherVersion.Cypher25 =>
        Seq(invalidEntityType(
          "Relationship",
          GqlParams.StringParam.ident.process("r"),
          Seq("Map"),
          "Type mismatch: expected Map but was Relationship",
          p(27, 1, 28)
        ))
      case CypherVersion.Cypher5 =>
        Seq.empty
    }
  }

  test("MATCH (n)-[r]->() SET r = n") {
    run().hasSemanticErrorsIn {
      case CypherVersion.Cypher25 =>
        Seq(invalidEntityType(
          "Node",
          GqlParams.StringParam.ident.process("n"),
          Seq("Map"),
          "Type mismatch: expected Map but was Node",
          p(26, 1, 27)
        ))
      case CypherVersion.Cypher5 =>
        Seq.empty
    }
  }

  test("MATCH (n)-[r]->(m) SET n += r") {
    run().hasSemanticErrorsIn {
      case CypherVersion.Cypher25 =>
        Seq(invalidEntityType(
          "Relationship",
          GqlParams.StringParam.ident.process("r"),
          Seq("Map"),
          "Type mismatch: expected Map but was Relationship",
          p(28, 1, 29)
        ))
      case CypherVersion.Cypher5 =>
        Seq.empty
    }
  }

  test("MATCH (n)-[r]->() SET r += n") {
    run().hasSemanticErrorsIn {
      case CypherVersion.Cypher25 =>
        Seq(invalidEntityType(
          "Node",
          GqlParams.StringParam.ident.process("n"),
          Seq("Map"),
          "Type mismatch: expected Map but was Node",
          p(27, 1, 28)
        ))
      case CypherVersion.Cypher5 =>
        Seq.empty
    }
  }

  test("MATCH (n)-[r]->() SET n = properties(r)") {
    run().hasNoErrors
  }

  test("MATCH (n)-[r]->() SET n = {prop: 1}") {
    run().hasNoErrors
  }

  test("MATCH (n)-[r]->() SET n += {prop: 1}") {
    run().hasNoErrors
  }
}
