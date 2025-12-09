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
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.VectorSearch
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.VectorSingleStageFilteringEnabled
import org.neo4j.cypher.internal.frontend.SemanticAnalysisTestSuite.Pipeline
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.AstRewriting
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.PreparatoryRewriting
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.SemanticAnalysis
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.SemanticTypeCheck
import org.neo4j.cypher.internal.frontend.phases.rewriting.cnf.rewriteEqualityToInPredicate
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.gqlstatus.ErrorGqlStatusObjectImplementation
import org.neo4j.gqlstatus.GqlHelper
import org.neo4j.gqlstatus.GqlParams
import org.neo4j.gqlstatus.GqlStatusInfoCodes

class SearchSemanticAnalysisTest extends CypherFunSuite with NameBasedSemanticAnalysisTestSuite {

  private val pipelineWithAstRewriting: Pipeline =
    PreparatoryRewriting andThen
      SemanticAnalysis(warn = Some(true), VectorSearch, VectorSingleStageFilteringEnabled) andThen
      AstRewriting() andThen
      rewriteEqualityToInPredicate andThen
      SemanticAnalysis(warn = Some(false), VectorSearch, VectorSingleStageFilteringEnabled) andThen
      SemanticTypeCheck

  private def runSearchWithRewriter(): AnalysisAssertions = {
    runWith(Set(CypherVersion.Cypher5), pipelineWithAstRewriting)
  }

  private def runSearch(): AnalysisAssertions =
    runWith(
      disabledCypherVersions = Set(CypherVersion.Cypher5),
      VectorSearch,
      VectorSingleStageFilteringEnabled
    )

  for {
    (maybeOptional, optionalLength) <- Seq(("", 0), ("OPTIONAL ", 9))
  } yield {

    // Tests for variable reference

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH ()-->(x)
         |  SEARCH x IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN x
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH ()-[r:REL]->()
         |  SEARCH r IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN r
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH m IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42N62("m", 30 + optionalLength, 2, 10),
          "Variable `m` not defined",
          p(30 + optionalLength, 2, 10)
        ),
        SemanticError(
          GqlHelper.getGql42001_42I69("m", 30 + optionalLength, 2, 10),
          "The variable `m` in SEARCH must reference a variable from the same MATCH statement.",
          p(30 + optionalLength, 2, 10)
        )
      )
    }

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH null IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42N62("null", 30 + optionalLength, 2, 10),
          "Variable `null` not defined",
          p(30 + optionalLength, 2, 10)
        ),
        SemanticError(
          GqlHelper.getGql42001_42I69("null", 30 + optionalLength, 2, 10),
          "The variable `null` in SEARCH must reference a variable from the same MATCH statement.",
          p(30 + optionalLength, 2, 10)
        )
      )
    }

    test(
      s"""${maybeOptional}MATCH (movie:Movie)
         |MATCH (m:Movie {title:'Matrix, The'})
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I69("movie", 67 + optionalLength, 3, 10),
          "The variable `movie` in SEARCH must reference a variable from the same MATCH statement.",
          p(67 + optionalLength, 3, 10)
        )
      )
    }

    test(
      s"""${maybeOptional}MATCH (movie)
         |MATCH (movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    // Tests for index name

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX null
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    test(
      s"""WITH "myPlotString" AS moviePlots
         |${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX movie
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX score
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  ) SCORE AS score
         |RETURN movie.title AS title, score
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX $$index
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I04(
            "Parameter",
            "VECTOR INDEX",
            58 + optionalLength,
            3,
            18
          ),
          "Parameter cannot be used in a VECTOR INDEX clause.",
          InputPosition(58 + optionalLength, 3, 18)
        )
      )
    }

    // Tests for search query

    val validSearchQueries = Seq(
      "$vector",
      "m.embedding",
      "[0.7, 0.5]",
      "[1, 0.7, 5]",
      "vector([1, 2, 3], 3, INTEGER32)",
      "null",
      "[1, 2, null]", // This is allowed in semantic checking, but will fail at runtime
      "[1.3, null, 2.7]" // This is allowed in semantic checking, but will fail at runtime
    )

    for {
      sq <- validSearchQueries
    } yield {
      test(
        s"""${maybeOptional}MATCH (m: Movie {name: "Matrix, The"})
           |MATCH (movie: Movie)
           |  SEARCH movie IN (
           |    VECTOR INDEX moviePlots
           |    FOR $sq
           |    LIMIT 5
           |  )
           |RETURN movie.title AS title
           |""".stripMargin
      ) {
        runSearch().hasNoErrors
      }
    }

    val invalidSearchQueries = Seq(
      (42, "Integer", "INTEGER", Some(2)),
      ("'[1, 2, 3]'", "String", "STRING", Some(11)),
      ("['1', '2', '3']", "List<String>", "LIST<STRING>", None)
    )

    for {
      (sq, typeString, cypherTypeString, length) <- invalidSearchQueries
    } yield {
      test(
        s"""${maybeOptional}MATCH (movie: Movie)
           |  SEARCH movie IN (
           |    VECTOR INDEX moviePlots
           |    FOR $sq
           |    LIMIT 5
           |  )
           |RETURN movie.title AS title
           |""".stripMargin
      ) {
        runSearch().hasErrors(
          SemanticError(
            GqlHelper.getGql42001_22NB1(
              java.util.List.of("VECTOR", "LIST<INTEGER | FLOAT>"),
              cypherTypeString,
              77 + optionalLength,
              4,
              9
            ),
            s"Type mismatch: expected Vector, List<Float>, List<Integer> or List<Number> but was $typeString",
            if (length.isDefined) InputPosition.withLength(77 + optionalLength, 4, 9, length.get)
            else p(77 + optionalLength, 4, 9)
          )
        )
      }
    }

    test(
      s"""${maybeOptional}MATCH (m: Movie {title:'Matrix, The'})
         |  SEARCH m IN (
         |    VECTOR INDEX moviePlots
         |    FOR m.embedding
         |    LIMIT 5
         |  )
         |RETURN m.title AS title
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR movie.embedding
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    // Tests for LIMIT

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 0
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    val invalidLimits: Seq[Any] = Seq(
      -1,
      "NULL",
      2147483648L
    )

    for {
      limit <- invalidLimits
    } yield {
      test(
        s"""${maybeOptional}MATCH (movie: Movie)
           |  SEARCH movie IN (
           |    VECTOR INDEX moviePlots
           |    FOR [1, 2, 3]
           |    LIMIT $limit
           |  )
           |RETURN movie.title AS title
           |""".stripMargin
      ) {
        runSearch().hasErrors(
          SemanticError(
            ErrorGqlStatusObjectImplementation
              .from(GqlStatusInfoCodes.STATUS_42001)
              .atPosition(97 + optionalLength, 5, 11)
              .withCause(ErrorGqlStatusObjectImplementation
                .from(GqlStatusInfoCodes.STATUS_42N31)
                .withParam(GqlParams.StringParam.component, "LIMIT")
                .withParam(GqlParams.StringParam.valueType, "INTEGER NOT NULL")
                .withParam(GqlParams.NumberParam.lower, 0)
                .withParam(GqlParams.NumberParam.upper, 2147483647L)
                .withParam(GqlParams.StringParam.value, limit.toString)
                .atPosition(97 + optionalLength, 5, 11).build())
              .build(),
            s"Invalid input. '$limit' is not a valid value. Must be a non-negative integer smaller than or equal to 2147483647.",
            InputPosition.withLength(97 + optionalLength, 5, 11, limit.toString.length)
          )
        )
      }
    }

    // Tests for score

    test(
      s"""${maybeOptional}MATCH (movie:Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  ) SCORE AS score
         |RETURN movie.title AS title, score
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    test(
      s"""WITH 0.5 AS score
         |${maybeOptional}MATCH (movie:Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  ) SCORE AS score
         |RETURN movie.title AS title, score
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42N59("score", 129 + optionalLength, 7, 14),
          "Variable `score` already declared",
          p(129 + optionalLength, 7, 14)
        )
      )
    }

    test(
      s"""${maybeOptional}MATCH (x:Movie)
         |  SEARCH x IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  ) SCORE AS x
         |RETURN *
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42N59("x", 103 + optionalLength, 6, 14),
          "Variable `x` already declared",
          p(103 + optionalLength, 6, 14)
        )
      )
    }

    test(
      s"""${maybeOptional}MATCH (movie:Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  ) SCORE AS null
         |RETURN *
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH (movie:Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  ) SCORE AS score
         |  WHERE score > 0.8
         |RETURN movie.title AS title, score
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH (movie:Movie)
         |  WHERE score > 0.8
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  ) SCORE AS score
         |RETURN movie.title AS title, score
         |""".stripMargin
    ) {
      runSearch().hasNoErrors
    }

    // Tests for single-stage filtering - Rule 0 from CIP-240

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    WHERE true
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I73("true", 97 + optionalLength, 5, 11),
          "Single-stage filtering predicates must consist of predicates of the form `x.y <comp> <expr>` with AND between them, where <comp> is <, <=, >, >= or =. 'true' does not fulfill this.",
          p(97 + optionalLength, 5, 11).withInputLength(4)
        )
      )
    }

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    WHERE 2008 < movie.year
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I73("2008 < movie.year", 102 + optionalLength, 5, 16),
          "Single-stage filtering predicates must consist of predicates of the form `x.y <comp> <expr>` with AND between them, where <comp> is <, <=, >, >= or =. '2008 < movie.year' does not fulfill this.",
          p(102 + optionalLength, 5, 16)
        )
      )
    }

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    WHERE movie.imdbRating > 8 OR movie.year > 2010
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I73("movie.imdbRating > 8 OR movie.year > 2010", 118 + optionalLength, 5, 32),
          "Single-stage filtering predicates must consist of predicates of the form `x.y <comp> <expr>` with AND between them, where <comp> is <, <=, >, >= or =. 'movie.imdbRating > 8 OR movie.year > 2010' does not fulfill this.",
          p(118 + optionalLength, 5, 32)
        )
      )
    }

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    WHERE NOT movie.imdbRating = 8
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I73("NOT movie.imdbRating = 8", 97 + optionalLength, 5, 11),
          "Single-stage filtering predicates must consist of predicates of the form `x.y <comp> <expr>` with AND between them, where <comp> is <, <=, >, >= or =. 'NOT movie.imdbRating = 8' does not fulfill this.",
          p(97 + optionalLength, 5, 11)
        )
      )
    }

    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    WHERE movie.imdbRating = 8 AND true
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I73("true", 122 + optionalLength, 5, 36),
          "Single-stage filtering predicates must consist of predicates of the form `x.y <comp> <expr>` with AND between them, where <comp> is <, <=, >, >= or =. 'true' does not fulfill this.",
          p(122 + optionalLength, 5, 36).withInputLength(4)
        )
      )
    }

    // Tests for single-stage filtering - Rule 1 from CIP-240

    test(
      s"""MATCH (m:Movie {title:'Matrix, The'})
         |${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR m.embedding
         |    WHERE m.year > 2000
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I74("m", "movie", 137 + optionalLength, 6, 11),
          "The variable `m` in a single-stage filter property predicate must be the same as the search clause binding variable `movie`.",
          p(137 + optionalLength, 6, 11)
        )
      )
    }

    test(
      s"""MATCH (m:Movie {title:'Matrix, The'})
         |${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR m.embedding
         |    WHERE movie.year > 2000 AND m.imdbRating >= 8
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I74("m", "movie", 159 + optionalLength, 6, 33),
          "The variable `m` in a single-stage filter property predicate must be the same as the search clause binding variable `movie`.",
          p(159 + optionalLength, 6, 33)
        )
      )
    }

    // Tests for single-stage filtering - Rule 2 from CIP-240

    /*
     * As we do not have access to the index in semantic checking,
     * it can only be checked at runtime whether the property is part of the index. =>
     * Assuming an index moviePlots FOR (m:Movie) ON m.embedding WITH [m.year, m.imdbRating],
     * this test will pass semantic checking but fail at runtime.
     */
    test(
      s"""MATCH (m:Movie {title:'Matrix, The'})
         |${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR m.embedding
         |    WHERE movie.imdbVotes > 1000
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasNoErrors
    }

    // Tests for single-stage filtering - Rule 3 from CIP-240

    test(
      s"""MATCH (m:Movie {title:'Matrix, The'})
         |${maybeOptional}MATCH (movie:Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR m.embedding
         |    WHERE movie.imdbRating > 8.2 AND movie.year > 2005
         |    LIMIT 5
         |  ) SCORE AS score
         |RETURN movie.title AS title, movie.imdbRating AS rating, movie.year AS year, score
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasNoErrors
    }

    test(
      s"""MATCH (m:Movie {title:'Matrix, The'})
         |${maybeOptional}MATCH (movie:Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR m.embedding
         |    WHERE movie.imdbRating > 8.2 AND movie.imdbRating <= 8.8
         |    LIMIT 5
         |  ) SCORE AS score
         |RETURN movie.title AS title, movie.imdbRating AS rating, movie.year AS year, score
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasNoErrors
    }

    test(
      s"""MATCH (m:Movie {title:'Matrix, The'})
         |${maybeOptional}MATCH (movie:Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR m.embedding
         |    WHERE movie.imdbRating >= 6.5  AND movie.year = 2010 AND movie.imdbRating < 7.5
         |    LIMIT 5
         |  ) SCORE AS score
         |RETURN movie.title AS title, movie.imdbRating AS rating, movie.year AS year, score
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasNoErrors
    }

    /*
     * This is a case where the AND predicates are dependent and not possible to combine into a single range
     * While we could statically determine this in semantic analysis in this case with literals,
     * it must be checked at runtime in the more general case, so we defer the checking until then.
     * => This test will pass semantic checking but fail at runtime
     */
    test(
      s"""MATCH (m:Movie {title:'Matrix, The'})
         |${maybeOptional}MATCH (movie:Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR m.embedding
         |    WHERE movie.imdbRating < 6.5 AND movie.imdbRating > 7.5
         |    LIMIT 5
         |  ) SCORE AS score
         |RETURN movie.title AS title, movie.imdbRating AS rating, movie.year AS year, score
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasNoErrors
    }

    // Test for single-stage filtering - Rule 4 - Rule 6 from CIP-240

    val validValues = Seq(
      "42",
      "3.14",
      "true",
      "'abc'",
      "date('2025-11-21')",
      "datetime({year:2020, month:12, day:10, hour: 6, minute: 41, second: 23, timezone: 'America/Los Angeles'})",
      "time('10:10:10+01:00')",
      "localdatetime('2018-03-30T10:10:10')",
      "time({hour: 6, minute: 41, second: 23, timezone:'+01:00'})",
      // duration is invalid for range predicates but the function is not resolved until after the semantic check so it will pass here and fail at runtime
      "duration('PT2H20M')",
      "null",
      "1000 * 2 - 1",
      "$value", // this value could be invalid but don't know until runtime
      "m.prop" // this value could be invalid but don't know until runtime
    )

    val invalidValues = Seq(
      ("vector([1, 2, 3], 3, INTEGER)", "Vector"),
      ("['abc']", "List<String>"),
      ("[1.0, -1.0]", "List<Float>"),
      ("point({x:3, y:0})", "Point")
    )

    val operators = Seq(
      "<",
      "<=",
      ">",
      ">=",
      "="
    )

    for {
      value <- validValues
      operator <- operators
    } yield {
      test(
        s"""MATCH (m:Movie {title:'Matrix, The'})
           |${maybeOptional}MATCH (movie: Movie)
           |  SEARCH movie IN (
           |    VECTOR INDEX moviePlots
           |    FOR m.embedding
           |    WHERE movie.prop $operator $value
           |    LIMIT 5
           |  )
           |RETURN movie.title AS title
           |""".stripMargin
      ) {
        runSearchWithRewriter().hasNoErrors
      }
    }

    for {
      (value, actualType) <- invalidValues
      operator <- operators.filterNot(x => x.equals("="))
    } yield {
      test(
        s"""MATCH (m:Movie {title:'Matrix, The'})
           |${maybeOptional}MATCH (movie: Movie)
           |  SEARCH movie IN (
           |    VECTOR INDEX moviePlots
           |    FOR m.embedding
           |    WHERE movie.prop $operator $value
           |    LIMIT 5
           |  )
           |RETURN movie.title AS title
           |""".stripMargin
      ) {
        runSearchWithRewriter().hasErrors(
          SemanticError(
            GqlHelper.getGql42001_22NB1(
              java.util.List.of(
                "BOOLEAN",
                "FLOAT",
                "INTEGER",
                "STRING",
                "DATE",
                "ZONED TIME",
                "LOCAL TIME",
                "LOCAL DATETIME",
                "ZONED DATETIME"
              ),
              actualType.toUpperCase,
              149 + optionalLength + operator.length,
              6,
              23 + operator.length
            ),
            s"Type mismatch: expected Boolean, Float, Integer, String, Date, Time, LocalTime, LocalDateTime or DateTime but was $actualType",
            InputPosition(149 + optionalLength + operator.length, 6, 23 + operator.length)
          )
        )
      }
    }

    for {
      (value, actualType) <- invalidValues
    } yield {
      test(
        s"""MATCH (m:Movie {title:'Matrix, The'})
           |${maybeOptional}MATCH (movie: Movie)
           |  SEARCH movie IN (
           |    VECTOR INDEX moviePlots
           |    FOR m.embedding
           |    WHERE movie.prop = $value
           |    LIMIT 5
           |  )
           |RETURN movie.title AS title
           |""".stripMargin
      ) {
        runSearchWithRewriter().hasErrors(
          SemanticError(
            GqlHelper.getGql42001_22NB1(
              java.util.List.of(
                "BOOLEAN",
                "FLOAT",
                "INTEGER",
                "STRING",
                "DURATION",
                "DATE",
                "ZONED TIME",
                "LOCAL TIME",
                "LOCAL DATETIME",
                "ZONED DATETIME"
              ),
              actualType.toUpperCase,
              150 + optionalLength,
              6,
              24
            ),
            s"Type mismatch: expected Boolean, Float, Integer, String, Duration, Date, Time, LocalTime, LocalDateTime or DateTime but was $actualType",
            InputPosition(150 + optionalLength, 6, 24)
          )
        )
      }
    }

    // Here the rhs is dependent on the node being searched. This will pass semantic checking but fail at runtime.
    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    WHERE movie.imdbRating >= movie.prop
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasNoErrors
    }

    // Test for single-stage filtering - Rule 7 from CIP-240
    test(
      s"""${maybeOptional}MATCH (movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    WHERE movie.imdbRating <> 8
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearch().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I73("movie.imdbRating <> 8", 114 + optionalLength, 5, 28),
          "Single-stage filtering predicates must consist of predicates of the form `x.y <comp> <expr>` with AND between them, where <comp> is <, <=, >, >= or =. 'movie.imdbRating <> 8' does not fulfill this.",
          p(114 + optionalLength, 5, 28)
        )
      )
    }

    // Tests for MATCH restrictions

    test(
      s"""${maybeOptional}MATCH (movie:Movie:Actor {prop:2})
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH (movie:Movie)-[]->()
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH (movie)-[]-()-->()
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH ()-[r:REL {prop:2}]->()
         |  SEARCH r IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN r
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH ()-[r {prop: 42}]->()-[]->()
         |  SEARCH r IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN r
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH REPEATABLE ELEMENTS (movie:Movie)-->()
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH (movie:Movie)-[r]->()
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I70(21 + optionalLength, 1, 22 + optionalLength),
          "In order to have a search clause, a MATCH statement can only have one bound variable.",
          p(21 + optionalLength, 1, 22 + optionalLength)
        )
      )
    }

    test(
      s"""${maybeOptional}MATCH (actor)-[]->(movie: Movie)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I70(7 + optionalLength, 1, 8 + optionalLength),
          "In order to have a search clause, a MATCH statement can only have one bound variable.",
          p(7 + optionalLength, 1, 8 + optionalLength)
        )
      )
    }

    test(
      s"""${maybeOptional}MATCH (movie:Movie)-[r]->()
         |  SEARCH r IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I70(7 + optionalLength, 1, 8 + optionalLength),
          "In order to have a search clause, a MATCH statement can only have one bound variable.",
          p(7 + optionalLength, 1, 8 + optionalLength)
        )
      )
    }

    test(
      s"""${maybeOptional}MATCH p = (movie:Movie)-->()
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasNoErrors
    }

    test(
      s"""${maybeOptional}MATCH p = (movie:Movie)-->(n)
         |  SEARCH movie IN (
         |    VECTOR INDEX moviePlots
         |    FOR [1, 2, 3]
         |    LIMIT 5
         |  )
         |RETURN movie.title AS title
         |""".stripMargin
    ) {
      runSearchWithRewriter().hasErrors(
        SemanticError(
          GqlHelper.getGql42001_42I70(27 + optionalLength, 1, 28 + optionalLength),
          "In order to have a search clause, a MATCH statement can only have one bound variable.",
          p(27 + optionalLength, 1, 28 + optionalLength)
        )
      )
    }

    val invalidPredicatesForNodeSearch = Seq(
      ("(movie:Movie)-->(:Actor)", 24, None),
      ("(movie:Movie)-[]->({prop:'prop'})", 25, None),
      ("(movie:Movie)-->(WHERE true)", 29, Some(4)),
      ("(:Actor|Director)-->(movie)", 13, None),
      ("({name: 'John Travolta'})-->(movie: Movie)", 7, None),
      ("(WHERE false)-[]->(movie)", 13, Some(5)),
      ("(movie)-[:REL]->()", 16, None),
      ("(movie:Movie)<-[{prop: 42}]-()", 22, None),
      ("()-[WHERE true]-(movie)", 16, Some(4)),
      ("()-[]-(:Actor)-[]->(movie)", 14, None),
      ("(:Actor)-[]-()-[]->(movie)", 8, None),
      ("(movie)-[]-()-[]->(:Actor)", 26, None),
      ("()-[:REL]-()-[]->(movie)", 11, None)
    )

    for {
      (pattern, offset, length) <- invalidPredicatesForNodeSearch
    } yield {

      test(
        s"""${maybeOptional}MATCH $pattern
           |  SEARCH movie IN (
           |    VECTOR INDEX moviePlots
           |    FOR [1, 2, 3]
           |    LIMIT 5
           |  )
           |RETURN movie.title AS title
           |""".stripMargin
      ) {
        runSearchWithRewriter().hasErrors(
          SemanticError(
            GqlHelper.getGql42001_42I71(offset + optionalLength, 1, offset + optionalLength + 1),
            "In order to have a search clause, a MATCH statement can only have predicates on the bound variable.",
            if (length.isDefined)
              InputPosition.withLength(offset + optionalLength, 1, offset + optionalLength + 1, length.get)
            else p(offset + optionalLength, 1, offset + optionalLength + 1)
          )
        )
      }
    }

    val invalidPredicatesForRelSearch = Seq(
      ("()-[r]->(:Movie)", 16, None),
      ("()-[r:REL]->({prop:'prop'})", 19, None),
      ("()-[r: REL {prop:42}]->(WHERE true)", 36, Some(4)),
      ("(:Actor|Director)-[r]->()", 13, None),
      ("({name: 'John Travolta'})-[r]-()", 7, None),
      ("(WHERE false)-[r {prop: 42}]->()", 13, Some(5)),
      ("(:Actor)-[]-()-[r]->()", 8, None),
      ("()-[r]-()-[]->(:Movie)", 22, None)
    )

    for {
      (pattern, offset, length) <- invalidPredicatesForRelSearch
    } yield {

      test(
        s"""${maybeOptional}MATCH $pattern
           |  SEARCH r IN (
           |    VECTOR INDEX moviePlots
           |    FOR [1, 2, 3]
           |    LIMIT 5
           |  )
           |RETURN r
           |""".stripMargin
      ) {
        runSearchWithRewriter().hasErrors(
          SemanticError(
            GqlHelper.getGql42001_42I71(offset + optionalLength, 1, offset + optionalLength + 1),
            "In order to have a search clause, a MATCH statement can only have predicates on the bound variable.",
            if (length.isDefined)
              InputPosition.withLength(offset + optionalLength, 1, offset + optionalLength + 1, length.get)
            else p(offset + optionalLength, 1, offset + optionalLength + 1)
          )
        )
      }
    }

    val tooComplexMatchPatterns = Seq(
      ("(movie:Movie), ()", 21),
      ("ANY SHORTEST ()-->(movie:Movie)", 6),
      ("(movie:Movie)-[]->{1,3}()", 6),
      ("()-[*]->(movie:Movie)", 8),
      ("()-[*..3]->(movie:Movie)", 8)
    )

    for {
      (pattern, offset) <- tooComplexMatchPatterns
    } yield {
      test(
        s"""${maybeOptional}MATCH $pattern
           |  SEARCH movie IN (
           |    VECTOR INDEX moviePlots
           |    FOR [1, 2, 3]
           |    LIMIT 5
           |  )
           |RETURN movie.title AS title
           |""".stripMargin
      ) {
        runSearchWithRewriter().hasErrors(
          SemanticError(
            GqlHelper.getGql42001_42I72(offset + optionalLength, 1, offset + optionalLength + 1),
            "In order to have a search clause, a MATCH statement can only have a single node or relationship pattern and no selectors.",
            p(offset + optionalLength, 1, offset + optionalLength + 1)
          )
        )
      }
    }
  }
}
