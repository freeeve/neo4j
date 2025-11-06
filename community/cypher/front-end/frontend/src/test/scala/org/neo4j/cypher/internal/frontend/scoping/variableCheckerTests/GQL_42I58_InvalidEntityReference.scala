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
package org.neo4j.cypher.internal.frontend.scoping.variableCheckerTests

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.frontend.scoping.VariableCheckingTestSuite

/**
 * Test for 42I58 - Invalid Entity Reference
 */
class GQL_42I58_InvalidEntityReference extends VariableCheckingTestSuite {

  test("""CREATE (a)-[:REL]->(b {prop: a.prop})""") {
    errorAllVersions("42I58", "Entity, 'a', cannot be created and referenced in the same clause.")
  }

  test("""CREATE (a)-[r:REL]->(b {prop: r.prop})""") {
    errorAllVersions("42I58", "Entity, 'r', cannot be created and referenced in the same clause.")
  }

  test("""CREATE (n:$(n.prop) {prop:5})""") {
    errorAllVersions("42I58", "Entity, 'n', cannot be created and referenced in the same clause.")
  }

  test("""CREATE ()-[r:$(r.prop) {prop:5}]->()""") {
    errorAllVersions("42I58", "Entity, 'r', cannot be created and referenced in the same clause.")
  }

  test("""CREATE (n)-[r:R {p: 1}]->({p: r.p})
         |RETURN n""".stripMargin) {
    errorAllVersions("42I58", "Entity, 'r', cannot be created and referenced in the same clause.")
  }

  test("""CREATE (n {p: 1})-[:R]->({p: n.p})
         |RETURN n""".stripMargin) {
    errorAllVersions("42I58", "Entity, 'n', cannot be created and referenced in the same clause.")
  }

  test("""CREATE (n {p: n.p})-[:R]->({p: 1})
         |RETURN n""".stripMargin) {
    errorAllVersions("42I58", "Entity, 'n', cannot be created and referenced in the same clause.")
  }

  test("""CREATE (n {p: 1})-[:R]->(:$all("A" + n.p) {p: 1})""".stripMargin) {
    errorAllVersions("42I58", "Entity, 'n', cannot be created and referenced in the same clause.")
  }

  test("""MERGE (n)-[r:R {p: 1}]->({p: r.p})
         |RETURN n""".stripMargin) {
    errorAllVersions("42I58", "Entity, 'r', cannot be created and referenced in the same clause.")
  }

  test("""MERGE (n {p: 1})-[:R]->({p: n.p})
         |RETURN n""".stripMargin) {
    errorAllVersions("42I58", "Entity, 'n', cannot be created and referenced in the same clause.")
  }

  test("""MERGE (n {p: n.p})-[:R]->({p: 1})
         |RETURN n""".stripMargin) {
    errorAllVersions("42I58", "Entity, 'n', cannot be created and referenced in the same clause.")
  }

  test("""MERGE (n {p: 1})-[:R]->(:$all("A" + n.p) {p: 1})""".stripMargin) {
    errorAllVersions("42I58", "Entity, 'n', cannot be created and referenced in the same clause.")
  }

  test("""INSERT (a)-[:REL]->(b {prop: a.prop})""") {
    errorAllVersions("42I58", "Entity, 'a', cannot be created and referenced in the same clause.")
  }

  test("""INSERT (a)-[r:REL]->(b {prop: r.prop})""") {
    errorAllVersions("42I58", "Entity, 'r', cannot be created and referenced in the same clause.")
  }

  // Reference between different patterns

  test("""CREATE (a), (b {prop: a.prop})""") {
    passes(CypherVersion.Cypher5)
    error("42I58", "Entity, 'a', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""CREATE (a)-[r:REL]->(b), (c {prop: r.prop})""") {
    passes(CypherVersion.Cypher5)
    error("42I58", "Entity, 'r', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""CREATE (a:A), (a)-[r:R {p: a.q}]->(b:B {p: a.q})
         |RETURN a""".stripMargin) {
    passes(CypherVersion.Cypher5)
    error("42I58", "Entity, 'a', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""CREATE (a:A {p: 2})-[r:R {p: 1}]->(b:B {p: 1}), (c:C)-[s:S {p: a.p+b.p+r.p}]->(d:D)
         |RETURN a""".stripMargin) {
    passes(CypherVersion.Cypher5)
    error("42I58", "Entity, 'a', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""CREATE (n {p: 1}), ({p: n.p})
         |RETURN n""".stripMargin) {
    passes(CypherVersion.Cypher5)
    error("42I58", "Entity, 'n', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""CREATE (n)-[r:R {p: 1}]->(), ({p: r.p})
         |RETURN n""".stripMargin) {
    passes(CypherVersion.Cypher5)
    error("42I58", "Entity, 'r', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""CREATE p = (n)-[r:R {p: 1}]->(), ({p: length(p)})
         |RETURN n""".stripMargin) {
    passes(CypherVersion.Cypher5)
    error("42I58", "Entity, 'p', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""CREATE (a {prop: 1}), ({prop: a.prop})""".stripMargin) {
    passes(CypherVersion.Cypher5)
    error("42I58", "Entity, 'a', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""INSERT (a), (b {prop: a.prop})""") {
    error("42I58", "Entity, 'a', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""INSERT (a)-[r:REL]->(b), (c {prop: r.prop})""") {
    error("42I58", "Entity, 'r', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""INSERT (a:A), (a)-[r:R {p: a.q}]->(b:B {p: a.q})
         |RETURN a""".stripMargin) {
    error("42I58", "Entity, 'a', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""INSERT (a:A {p: 2})-[r:R {p: 1}]->(b:B {p: 1}), (c:C)-[s:S {p: a.p+b.p+r.p}]->(d:D)
         |RETURN a""".stripMargin) {
    error("42I58", "Entity, 'a', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""INSERT (n {p: 1}), ({p: n.p})
         |RETURN n""".stripMargin) {
    error("42I58", "Entity, 'n', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""INSERT (n)-[r:R {p: 1}]->(), ({p: r.p})
         |RETURN n""".stripMargin) {
    error("42I58", "Entity, 'r', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""INSERT (a {prop: 1}), ({prop: a.prop})""".stripMargin) {
    error("42I58", "Entity, 'a', cannot be created and referenced in the same clause.", CypherVersion.Cypher25)
  }

  test("""CREATE (b {prop: a.prop}), (a)""") {
    passes(CypherVersion.Cypher5)
    error("42I58", "Entity, 'a', cannot be created and referenced in the same clause.")
  }
}
