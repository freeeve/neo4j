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
package org.neo4j.cypher.internal.ast.factory.query

import org.neo4j.cypher.internal.ast.Statements
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher25
import org.neo4j.cypher.internal.ast.test.util.AstParsing.Cypher5
import org.neo4j.cypher.internal.ast.test.util.AstParsingTestBase
import org.neo4j.cypher.internal.expressions.CosineVectorDistanceMetric
import org.neo4j.cypher.internal.expressions.DotVectorDistanceMetric
import org.neo4j.cypher.internal.expressions.EuclideanSquaredVectorDistanceMetric
import org.neo4j.cypher.internal.expressions.EuclideanVectorDistanceMetric
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.HammingVectorDistanceMetric
import org.neo4j.cypher.internal.expressions.ManhattanVectorDistanceMetric
import org.neo4j.cypher.internal.expressions.VectorDistanceMetric
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.gqlstatus.GqlStatusInfoCodes

class VectorGQLFunctionsTest extends AstParsingTestBase {

  val metrics: Seq[VectorDistanceMetric] = Seq(
    EuclideanVectorDistanceMetric,
    EuclideanSquaredVectorDistanceMetric,
    ManhattanVectorDistanceMetric,
    CosineVectorDistanceMetric,
    DotVectorDistanceMetric,
    HammingVectorDistanceMetric
  )

  val normMetrics: Seq[VectorDistanceMetric] = Seq(
    EuclideanVectorDistanceMetric,
    ManhattanVectorDistanceMetric
  )

  metrics.foreach { case metric =>
    test(s"VECTOR_DISTANCE(v1, v2, ${metric.metricName})") {
      parsesIn[Expression] {
        case Cypher5 =>
          _.toAstIgnorePos { function("vector_distance", varFor("v1"), varFor("v2"), varFor(metric.metricName)) }
        case Cypher25 =>
          _.toAstIgnorePos {
            function("vector_distance", varFor("v1"), varFor("v2"), literalString(metric.metricName))
          }
      }
    }
    test(s"RETURN my.own.vector_distance(v1, v2, ${metric.metricName})") {
      parsesIn[Statements] {
        _ =>
          _.toAstIgnorePos {
            Statements(Seq(singleQuery(return_(returnItem(
              function(Seq("my", "own"), "vector_distance", varFor("v1"), varFor("v2"), varFor(metric.metricName)),
              s"my.own.vector_distance(v1, v2, ${metric.metricName})"
            )))))
          }
      }
    }
  }

  normMetrics.foreach { case metric =>
    test(s"VECTOR_NORM(v1, ${metric.metricName})") {
      parsesIn[Expression] {
        case Cypher5 =>
          _.toAstIgnorePos { function("vector_norm", varFor("v1"), varFor(metric.metricName)) }
        case Cypher25 =>
          _.toAstIgnorePos {
            function("vector_norm", varFor("v1"), literalString(metric.metricName))
          }
      }
    }
    test(s"RETURN my.own.vector_norm(v1, ${metric.metricName})") {
      parsesIn[Statements] {
        _ =>
          _.toAstIgnorePos {
            Statements(Seq(singleQuery(return_(returnItem(
              function(Seq("my", "own"), "vector_norm", varFor("v1"), varFor(metric.metricName)),
              s"my.own.vector_norm(v1, ${metric.metricName})"
            )))))
          }
      }
    }
  }

  // Failing tests
  test("RETURN vector_distance(v1, v2, FOO)") {
    parsesIn[Statements] {
      case Cypher5 => _.toAstIgnorePos {
          Statements(Seq(singleQuery(return_(returnItem(
            function("vector_distance", varFor("v1"), varFor("v2"), varFor("FOO")),
            "vector_distance(v1, v2, FOO)"
          )))))
        }
      case Cypher25 =>
        _.withSyntaxErrorContaining(
          """Invalid vector distance metric, expected EUCLIDEAN, EUCLIDEAN_SQUARED, MANHATTAN, COSINE, DOT or HAMMING (line 1, column 32 (offset: 31))
            |"RETURN vector_distance(v1, v2, FOO)"
            |                                ^""".stripMargin,
          GqlStatusInfoCodes.STATUS_42I62,
          "error: syntax error or access rule violation - unsupported distance metric. Unknown distance metric: 'FOO'.",
          position = Some(InputPosition(31, 1, 32))
        )
    }
  }

  test("RETURN vector_norm(v1, FOO)") {
    parsesIn[Statements] {
      case Cypher5 => _.toAstIgnorePos {
          Statements(Seq(singleQuery(return_(returnItem(
            function("vector_norm", varFor("v1"), varFor("FOO")),
            "vector_norm(v1, FOO)"
          )))))
        }
      case Cypher25 =>
        _.withSyntaxErrorContaining(
          """Invalid vector distance metric, expected EUCLIDEAN or MANHATTAN (line 1, column 24 (offset: 23))
            |"RETURN vector_norm(v1, FOO)"
            |                        ^""".stripMargin,
          GqlStatusInfoCodes.STATUS_42I62,
          "error: syntax error or access rule violation - unsupported distance metric. Unknown distance metric: 'FOO'.",
          position = Some(InputPosition(23, 1, 24))
        )
    }
  }

  test("RETURN vector_distance(v1, v2, null)") {
    parsesIn[Statements] {
      case Cypher5 => _.toAstIgnorePos {

          Statements(Seq(singleQuery(return_(returnItem(
            function("vector_distance", varFor("v1"), varFor("v2"), nullLiteral),
            "vector_distance(v1, v2, null)"
          )))))
        }
      case Cypher25 =>
        _.withSyntaxErrorContaining(
          """Invalid vector distance metric, expected EUCLIDEAN, EUCLIDEAN_SQUARED, MANHATTAN, COSINE, DOT or HAMMING (line 1, column 32 (offset: 31))
            |"RETURN vector_distance(v1, v2, null)"
            |                                ^""".stripMargin,
          GqlStatusInfoCodes.STATUS_42I62,
          "error: syntax error or access rule violation - unsupported distance metric. Unknown distance metric: 'NULL'.",
          position = Some(InputPosition(31, 1, 32))
        )
    }
  }

  test("RETURN vector_norm(v1, null)") {
    parsesIn[Statements] {
      case Cypher5 => _.toAstIgnorePos {

          Statements(Seq(singleQuery(return_(returnItem(
            function("vector_norm", varFor("v1"), nullLiteral),
            "vector_norm(v1, null)"
          )))))
        }
      case Cypher25 =>
        _.withSyntaxErrorContaining(
          """Invalid vector distance metric, expected EUCLIDEAN or MANHATTAN (line 1, column 24 (offset: 23))
            |"RETURN vector_norm(v1, null)"
            |                        ^""".stripMargin,
          GqlStatusInfoCodes.STATUS_42I62,
          "error: syntax error or access rule violation - unsupported distance metric. Unknown distance metric: 'NULL'.",
          position = Some(InputPosition(23, 1, 24))
        )
    }
  }

  test("RETURN vector_distance(v1, v2, INTEGE)") {
    parsesIn[Statements] {
      case Cypher5 => _.toAstIgnorePos {
          Statements(Seq(singleQuery(return_(returnItem(
            function("vector_distance", varFor("v1"), varFor("v2"), varFor("INTEGE")),
            "vector_distance(v1, v2, INTEGE)"
          )))))
        }
      case Cypher25 =>
        _.withSyntaxErrorContaining(
          """Invalid vector distance metric, expected EUCLIDEAN, EUCLIDEAN_SQUARED, MANHATTAN, COSINE, DOT or HAMMING (line 1, column 32 (offset: 31))
            |"RETURN vector_distance(v1, v2, INTEGE)"
            |                                ^""".stripMargin,
          GqlStatusInfoCodes.STATUS_42I62,
          "error: syntax error or access rule violation - unsupported distance metric. Unknown distance metric: 'INTEGE'.",
          position = Some(InputPosition(31, 1, 32))
        )
    }
  }

  test("RETURN vector_norm(v1, INTEGE)") {
    parsesIn[Statements] {
      case Cypher5 => _.toAstIgnorePos {
          Statements(Seq(singleQuery(return_(returnItem(
            function("vector_norm", varFor("v1"), varFor("INTEGE")),
            "vector_norm(v1, INTEGE)"
          )))))
        }
      case Cypher25 =>
        _.withSyntaxErrorContaining(
          """Invalid vector distance metric, expected EUCLIDEAN or MANHATTAN (line 1, column 24 (offset: 23))
            |"RETURN vector_norm(v1, INTEGE)"
            |                        ^""".stripMargin,
          GqlStatusInfoCodes.STATUS_42I62,
          "error: syntax error or access rule violation - unsupported distance metric. Unknown distance metric: 'INTEGE'.",
          position = Some(InputPosition(23, 1, 24))
        )
    }
  }

  test("vector_distance(\"hello\", 2, DOT, anotherVar)") {
    parsesIn[Expression] {
      case _ => _.withoutErrors
    }
  }

  test("vector_norm(\"hello\", MANHATTAN, anotherVar)") {
    parsesIn[Expression] {
      case _ => _.withoutErrors
    }
  }

  test("vector_distance()") {
    parsesIn[Expression] {
      case _ => _.withoutErrors
    }
  }

  test("vector_norm()") {
    parsesIn[Expression] {
      case _ => _.withoutErrors
    }
  }
}
