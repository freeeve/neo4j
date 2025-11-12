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
package org.neo4j.cypher.internal.frontend.scoping

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.Clause
import org.neo4j.cypher.internal.ast.ConditionalQueryBranch
import org.neo4j.cypher.internal.ast.ConditionalQueryWhen
import org.neo4j.cypher.internal.ast.Return
import org.neo4j.cypher.internal.ast.Search
import org.neo4j.cypher.internal.ast.SingleQuery
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.ast.prettifier.Prettifier
import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature.ScopeQueries
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.Pattern
import org.neo4j.cypher.internal.expressions.PatternElement
import org.neo4j.cypher.internal.expressions.PatternPart
import org.neo4j.cypher.internal.expressions.RelationshipPattern
import org.neo4j.cypher.internal.frontend.helpers.ErrorCollectingContext
import org.neo4j.cypher.internal.frontend.helpers.NoPlannerName
import org.neo4j.cypher.internal.frontend.phases.BaseState
import org.neo4j.cypher.internal.frontend.phases.InitialState
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.Parse
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.PreparatoryRewriting
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.ExpressionResult
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.ExpressionScope
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.NoResult
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.OmittedResult
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.PatternIncomingContext
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.PatternScope
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.RegularContext
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.ScopeSurveyor
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.StatementScope
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.TableResult
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.TableResultWithNotYetKnownColumns
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.VariableChecker
import org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping.WorkingScope
import org.neo4j.cypher.internal.label_expressions.LabelExpression
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.ErrorMessageProvider
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.NotImplementedErrorMessageProvider
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.cypher.internal.util.test_helpers.TestName
import org.neo4j.gqlstatus.ErrorGqlStatusObject
import org.scalatest.BeforeAndAfterAll

import java.io.FileWriter
import java.io.Writer

import scala.annotation.tailrec
import scala.jdk.OptionConverters.RichOptional

trait VariableCheckingTestSuite extends CypherFunSuite with TestName with BeforeAndAfterAll {

  val unit = Set.empty[String]

  sealed trait ExpectedCharacteristic
  case class Ast(astNodeString: String) extends ExpectedCharacteristic

  case class Incoming(constants: Set[String] = Set.empty, variables: Set[String] = Set.empty)
      extends ExpectedCharacteristic

  case class PatternIncoming(
    topology: Set[String] = Set.empty,
    predicate: Set[String] = Set.empty,
    path: Set[String] = Set.empty
  ) extends ExpectedCharacteristic

  case class Referenced(variables: Set[String]) extends ExpectedCharacteristic

  case class Declared(constants: Seq[String] = Seq.empty, variables: Seq[String] = Seq.empty)
      extends ExpectedCharacteristic

  case class Outgoing(constants: Set[String] = Set.empty, variables: Set[String] = Set.empty)
      extends ExpectedCharacteristic

  sealed trait ExpectedResult extends ExpectedCharacteristic

  object ExpectedResult {
    case class TableResult(columns: String*) extends ExpectedResult

    case object TableResultWithNotYetKnownColumns extends ExpectedResult

    case object OmittedResult extends ExpectedResult

    case object NoResult extends ExpectedResult

    case object ExpressionResult extends ExpectedResult
  }

  case class ExpectedWorkingScope(expectedCharacteristics: ExpectedCharacteristic*) extends ExpectedCharacteristic

  object ExpectedWorkingScope {

    def varExp(
      name: String,
      incomingConstants: Set[String],
      incomingVariables: Set[String] = Set.empty
    ): ExpectedWorkingScope =
      ExpectedWorkingScope(
        Ast(name),
        Incoming(constants = incomingConstants, variables = incomingVariables),
        Referenced(Set(name))
      )

    def constExp(ast: String, incoming: Set[String] = Set.empty): ExpectedWorkingScope = {
      if (incoming.isEmpty) {
        ExpectedWorkingScope(
          Ast(ast)
        )
      } else {
        ExpectedWorkingScope(
          Ast(ast),
          Incoming(constants = incoming)
        )
      }
    }
  }

  private val prettifier: Prettifier = Prettifier(ExpressionStringifier())

  private def prettify(astNode: ASTNode): String = (astNode match {
    case s: Statement           => prettifier.asString(s)
    case c: Clause              => prettifier.asString(SingleQuery(Seq(c))(InputPosition.NONE))
    case s: Search              => prettifier.asString(s)
    case ex: Expression         => prettifier.expr(ex)
    case p: Pattern             => prettifier.expr.patterns(p)
    case p: PatternPart         => prettifier.expr.patterns(p)
    case p: PatternElement      => prettifier.expr.patterns(p)
    case p: RelationshipPattern => prettifier.expr.patterns(p)
    case lex: LabelExpression   => prettifier.expr.stringifyLabelExpression(lex)
    case cqb @ ConditionalQueryBranch(Some(_), _) =>
      prettifier.asString(ConditionalQueryWhen(Seq(cqb), None)(InputPosition.NONE))
    case cqb @ ConditionalQueryBranch(None, _) =>
      prettifier.asString(ConditionalQueryWhen(Seq(), Some(cqb))(InputPosition.NONE))
    case x => x.toString
  })

  private def whitespaceNormalization(cypher: String): String =
    cypher.trim.replaceAll("\\s+", " ")

  private val defaultDatabaseName = "mock"

  private def messageProvider: ErrorMessageProvider = NotImplementedErrorMessageProvider

  private val testLog: Boolean = true
  private var log: Writer = _

  private val logPPrint = pprint.PPrinter.BlackWhite.copy(
    additionalHandlers = {
      case astNode: ASTNode => prepQueryText(prettify(astNode))
    }
  )

  private def prepQueryText(query: String) = {
    val singleLine = whitespaceNormalization(query)
    val shortenedLine =
      if (singleLine.length > 60) {
        singleLine.take(30) + " /* … */ " + singleLine.takeRight(20)
      } else {
        singleLine
      }
    pprint.Tree.Literal(s"\"$shortenedLine\"")
  }

  override def beforeAll(): Unit = {
    if (testLog) {
      try {
        log = new FileWriter(s"target/${getClass.getSimpleName}.log")
      } catch {
        case e: Throwable => new RuntimeException(e)
      }
    }
  }

  private def runQuery(
    query: String,
    version: CypherVersion,
    skipVariableChecker: Boolean = false,
    withPrepRewriting: Boolean = false
  ): Either[BaseState, Seq[SemanticError]] = {
    val context =
      new ErrorCollectingContext(version, isComposite = false, defaultDatabaseName, query, Seq(ScopeQueries)) {
        override def errorMessageProvider: ErrorMessageProvider = messageProvider
      }
    val transformers =
      if (skipVariableChecker) Parse andThen ScopeSurveyor
      else if (withPrepRewriting) Parse andThen PreparatoryRewriting andThen ScopeSurveyor andThen VariableChecker
      else Parse andThen ScopeSurveyor andThen VariableChecker
    val state = transformers.transform(initialStateWithQuery(query), context)
    if (context.errors.isEmpty) {
      Left(state)
    } else {
      Right(context.errors.collect { case e: SemanticError => e })
    }
  }

  def passes(version: CypherVersion): Unit = passes(Array(version))

  def passes(versions: Array[CypherVersion] = Array(CypherVersion.Cypher25), withRewriting: Boolean = true): Unit = {
    val query = testName
    val rewriteOptions = if (withRewriting) Seq(false, true) else Seq(false)
    versions.foreach(version => {
      rewriteOptions.foreach(rewrite => {
        val rewriteMsg = if (rewrite) " after rewrite" else ""
        runQuery(query, version, withPrepRewriting = rewrite) match {
          case Left(state) =>
            state.maybeScopeState should not be empty

            if (testLog) {
              log.append(
                s"""Version: $version $rewriteMsg
                   |Query:
                   |
                   |$query
                   |
                   |passed without errors.
                   |----------
                   |""".stripMargin
              )
            }
          case Right(semanticErrors) =>
            fail(
              s"""Version: $version $rewriteMsg
                 |Query:
                 |
                 |$query
                 |
                 |is expected to be successful, but
                 |
                 |actually threw errors: ${pprint.apply(semanticErrors)}""".stripMargin
            )
        }
      })

    })
  }

  def errorAllVersions(
    expectedGqlStatusCode: String,
    msgContains: String
  ): Unit = error(expectedGqlStatusCode, msgContains, versions = CypherVersion.values())

  def error(
    expectedGqlStatusCode: String,
    msgContains: String,
    version: CypherVersion
  ): Unit = error(expectedGqlStatusCode, msgContains, Array(version))

  def error(
    expectedGqlStatusCode: String,
    msgContains: String,
    versions: Array[CypherVersion] = Array(CypherVersion.Cypher25)
  ): Unit = {
    val query = testName

    @tailrec
    def findGqlStatus(gqlStatusObject: ErrorGqlStatusObject): Option[ErrorGqlStatusObject] = gqlStatusObject match {
      case gqlStatusObject if gqlStatusObject.gqlStatus() == expectedGqlStatusCode => Some(gqlStatusObject)
      case gqlStatusObject: ErrorGqlStatusObject =>
        gqlStatusObject.cause().toScala match {
          case Some(cause) => findGqlStatus(cause)
          case None        => None
        }
    }

    versions.foreach(version => {
      runQuery(query, version) match {
        case Left(_) =>
          fail(
            s"""Version: $version
               |Query:
               |
               |$query
               |
               |is expected to throw an error, but
               |
               |actually was successful""".stripMargin
          )
        case Right(semanticErrors) =>
          semanticErrors.collectFirst(Function.unlift {
            semanticError: SemanticError => findGqlStatus(semanticError.gqlStatusObject)
          }) match {
            case Some(gqlStatusObject) =>
              gqlStatusObject.gqlStatus() shouldBe expectedGqlStatusCode
              gqlStatusObject.statusDescription() should include(msgContains)

              if (testLog) {
                log.append(
                  s"""Version: $version
                     |Query:
                     |
                     |$query
                     |
                     |Error:
                     |
                     |${logPPrint(gqlStatusObject).plainText.trim}
                     |----------
                     |""".stripMargin
                )
              }
            case None => fail(
                s"""Version: $version
                   |Query:
                   |
                   |$query
                   |
                   |is expected to throw gql status $expectedGqlStatusCode, but
                   |
                   |actually did not.
                   |
                   |Errors:
                   |${semanticErrors.map(x => logPPrint(x.gqlStatusObject).plainText.trim).mkString(", ")}""".stripMargin
              )
          }
      }
    })

  }

  def hasScope(
    expected: ExpectedWorkingScope,
    version: CypherVersion,
    skipVariableChecker: Boolean
  ): Unit = hasScope(expected, Array(version), skipVariableChecker)

  def hasScope(
    expected: ExpectedWorkingScope,
    versions: Array[CypherVersion] = Array(CypherVersion.Cypher25),
    skipVariableChecker: Boolean = false
  ): Unit = {
    val query = testName
    versions.foreach(version => {
      runQuery(query, version, skipVariableChecker) match {
        case Left(state) =>
          state.maybeScopeState should not be empty
          val ss = state.maybeScopeState.get

          assertExpectation(ss.workingScope, expected)

          if (testLog) {
            log.append(
              s"""Query:
                 |
                 |$query
                 |
                 |Working scope:
                 |
                 |${logPPrint(ss.workingScope)}
                 |----------
                 |""".stripMargin
            )
          }
        case Right(semanticErrors) =>
          fail(
            s"""Version: $version
               |Query:
               |
               |$query
               |
               |is expected to be successful, but
               |
               |actually threw errors: ${pprint.apply(semanticErrors)}""".stripMargin
          )
      }

    })
  }

  private def assertExpectation(ws: WorkingScope, expected: ExpectedWorkingScope): Unit = {
    val astNodeString = expected.expectedCharacteristics.collectFirst {
      case Ast(s) => s
    }.getOrElse("—no expected ast node string given—")
    val incoming = expected.expectedCharacteristics.collectFirst {
      case i: Incoming => i
    }.getOrElse(Incoming(unit, unit))
    val patternIncoming = expected.expectedCharacteristics.collectFirst {
      case pi: PatternIncoming => pi
    }.getOrElse(PatternIncoming(unit, unit, unit))
    val referenced = expected.expectedCharacteristics.collectFirst {
      case Referenced(refs) => refs
    }.getOrElse(unit)
    val declared = expected.expectedCharacteristics.collectFirst {
      case d: Declared => d
    }.getOrElse(Declared(Seq.empty, Seq.empty))
    val outgoing = expected.expectedCharacteristics.collectFirst {
      case o: Outgoing => o
    }.getOrElse(ws.astNode match {
      // case _: Expression | _: LabelExpression => Outgoing(unit, unit)
      case _ => Outgoing(unit, unit)
    })
    val result = expected.expectedCharacteristics.collectFirst {
      case r: ExpectedResult => r
    }.getOrElse(ws.astNode match {
      case _: Expression | _: LabelExpression => ExpectedResult.ExpressionResult
      case _                                  => ExpectedResult.NoResult
    })
    val children = expected.expectedCharacteristics.collect {
      case c: ExpectedWorkingScope => c
    }

    withClue(s"['${prettify(ws.astNode)}']") {
      withClue("[query]") {
        whitespaceNormalization(prettify(ws.astNode)) shouldBe whitespaceNormalization(astNodeString)
      }
      ws match {
        case StatementScope(_, RegularContext(constants, variables), _, _, _, _, _) =>
          withClue("[statement incoming]") {
            withClue("[constants]") {
              constants.map(_.name) should contain theSameElementsAs incoming.constants
            }
            withClue("[variables]") {
              variables.map(_.name) should contain theSameElementsAs incoming.variables
            }
            withClue("[invariance]") {
              (constants.map(_.name) intersect variables.map(_.name)) shouldBe empty
            }
          }
        case PatternScope(_, PatternIncomingContext(topology, predicate, path, _), _, _, _, _) =>
          withClue("[pattern incoming]") {
            withClue("[topology]") {
              topology.map(_.name) should contain theSameElementsAs patternIncoming.topology
            }
            withClue("[predicate]") {
              predicate.map(_.name) should contain theSameElementsAs patternIncoming.predicate
            }
            withClue("[path]") {
              path.map(_.name) should contain theSameElementsAs patternIncoming.path
            }
            withClue("[invariance]") {
              (topology.map(_.name) intersect path.map(_.name)) shouldBe empty
              (predicate.map(_.name) intersect path.map(_.name)) shouldBe empty
            }
          }
        case ExpressionScope(_, RegularContext(constants, variables), _, _, _) =>
          withClue("[expression incoming]") {
            withClue("[constants]") {
              constants.map(_.name) should contain theSameElementsAs incoming.constants
            }
            withClue("[variables]") {
              variables.map(_.name) should contain theSameElementsAs incoming.variables
            }
            withClue("[invariance]") {
              (constants.map(_.name) intersect variables.map(_.name)) shouldBe empty
            }
          }
        case s => fail(s"unexpected type of scope: ${pprint.apply(s)}")
      }
      withClue("[children]") {
        withClue("[number]") {
          ws.children.size shouldBe children.size
        }
        ws.children.zip(children).foreach {
          case (childWs, childExpected) => assertExpectation(childWs, childExpected)
        }
      }
      withClue("[referenced]") {
        ws.referenced.map(_.name) should contain theSameElementsAs referenced
      }
      withClue("[declared]") {
        withClue("[constants]") {
          ws.declared.constants.map(_.name) should contain theSameElementsInOrderAs declared.constants
        }
        withClue("[variables]") {
          ws.declared.variables.map(_.name) should contain theSameElementsInOrderAs declared.variables
        }
        withClue("[invariance]") {
          (ws.declared.constants.map(_.name) intersect ws.declared.variables.map(_.name)) shouldBe empty
        }
      }
      withClue("[outgoing]") {
        withClue("[constants]") {
          ws.outgoing.constants.map(_.name) should contain theSameElementsAs outgoing.constants
        }
        withClue("[variables]") {
          ws.outgoing.variables.map(_.name) should contain theSameElementsAs outgoing.variables
        }
        withClue("[invariance]") {
          (ws.outgoing.constants.map(_.name) intersect ws.outgoing.variables.map(_.name)) shouldBe empty
        }
        ws.astNode match {
          case _: Return =>
            withClue("[RETURN invariance]") {
              ws.outgoing.constants shouldBe empty
            }
          case _ => ()
        }
      }
      withClue("[result]") {
        (ws.result, result) match {
          case (NoResult, ExpectedResult.NoResult)                                                   => succeed
          case (ExpressionResult, ExpectedResult.ExpressionResult)                                   => succeed
          case (OmittedResult, ExpectedResult.OmittedResult)                                         => succeed
          case (TableResultWithNotYetKnownColumns, ExpectedResult.TableResultWithNotYetKnownColumns) => succeed
          case (TableResult(columns), ExpectedResult.TableResult(expectedColumns @ _*)) =>
            columns.map(_.name) should contain theSameElementsAs expectedColumns
          case (actual, exp) => actual shouldBe exp
        }
        ws.astNode match {
          case _: Return =>
            withClue("[RETURN invariance]") {
              ws.result shouldBe a[TableResult]
            }
          case _ => ()
        }
      }
    }
  }

  override def afterAll(): Unit = {
    if (testLog) {
      log.close()
    }
  }

  private def initialStateWithQuery(query: String): InitialState =
    InitialState(query, NoPlannerName, new AnonymousVariableNameGenerator)
}
