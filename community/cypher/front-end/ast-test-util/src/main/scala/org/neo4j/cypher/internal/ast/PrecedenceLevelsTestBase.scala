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
package org.neo4j.cypher.internal.ast

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.expressions.Contains
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.HasLabels
import org.neo4j.cypher.internal.expressions.LiteralEntry
import org.neo4j.cypher.internal.expressions.MapProjection
import org.neo4j.cypher.internal.expressions.NFCNormalForm
import org.neo4j.cypher.internal.expressions.Part2OperatorExpression
import org.neo4j.cypher.internal.expressions.PatternExpression
import org.neo4j.cypher.internal.expressions.RelationshipsPattern
import org.neo4j.cypher.internal.expressions.ShortestPathExpression
import org.neo4j.cypher.internal.expressions.ShortestPathsPatternPart
import org.neo4j.cypher.internal.label_expressions.LabelExpressionPredicate
import org.neo4j.cypher.internal.util.symbols

import scala.util.Random

trait PrecedenceLevelsTestBase extends AstConstructionTestSupport {

  /**
   * Precedence in Cypher:
   * 12: OR
   * 11: XOR
   * 10: AND
   * 9: NOT
   * 8: =, !=, <>, <, >, <=, >=
   * 7: =~, STARS WITH, ENDS WITH, CONTAINS, IN, IS NULL, IS NOT NULL, IS ::, IS NOT ::, IS NORMALIZED, IS NOT NORMALIZED, :Label (Cypher 25),
   * 6: +, -, ||
   * 5: *, /, %
   * 4: POW
   * 3: +(unary), -(unary)
   * 2: .prop, :Label (Cypher 5), [expr], [..]
   * 1: literal, parameter, CASE, COUNT, EXISTS, COLLECT, map projection, list comprehension, pattern comprehension,
   * reduce, all, any, none, single, pattern, shortest path, (expr), functions, variables
   */
  sealed trait Operator {
    def numArgs: Int // number of expression arguments
    def astGen: Seq[Expression] => Expression
    def cypherGen: Seq[String] => String
    def cypherGenPart2: Option[Seq[String] => String]
    def eagerlyConsumedOperatorSymbols: Set[String]
    // argIndexes start counting from 0, the option can provide ast classes that are exception, None means no exception
    def syntacticallyDelimited: Set[(Int, String)]
    def part2Arguments: Set[Int]
    def isParseable: Boolean
    // if an operator is not available in all Cypher versions,
    // list the versions it is available in here
    def onlyInVersions: Set[CypherVersion]

    def astType: Class[_ <: Expression] = astGen((0 until numArgs).map(_ => nullLiteral)).getClass
  }

  case class NormalOperator(
    numArgs: Int, // number of expression arguments
    astGen: Seq[Expression] => Expression,
    cypherGen: Seq[String] => String,
    cypherGenPart2: Option[Seq[String] => String] = None,
    eagerlyConsumedOperatorSymbols: Set[String] = Set(),
    syntacticallyDelimited: Set[(Int, String)] = Set.empty, // argIndexes start counting from 0
    part2Arguments: Set[Int] = Set(),
    isParseable: Boolean = true,
    // if an operator is not available in all Cypher versions,
    // list the versions it is available in here
    onlyInVersions: Set[CypherVersion] = CypherVersion.values().toSet
  ) extends Operator

  case object ParenthesizedExpression extends Operator {
    override def numArgs: Int = 1
    override def astGen: Seq[Expression] => Expression = a => a(0)
    override def cypherGen: Seq[String] => String = a => s"(${a(0)})"
    override def cypherGenPart2: Option[Seq[String] => String] = None
    override def eagerlyConsumedOperatorSymbols: Set[String] = Set()
    override def syntacticallyDelimited: Set[(Int, String)] = Set(0 -> ")")
    override def part2Arguments: Set[Int] = Set()
    override def isParseable: Boolean = true
    // if an operator is not available in all Cypher versions,
    // list the versions it is available in here
    override def onlyInVersions: Set[CypherVersion] = CypherVersion.values().toSet
  }

  val precedenceLevel: Seq[Seq[Operator]] = Seq(
    Seq( // level 12 — lowest precedence
      NormalOperator(2, a => or(a(0), a(1)), a => s"${a(0)} OR ${a(1)}", None, Set("OR"))),
    Seq( // level 11
      NormalOperator(2, a => xor(a(0), a(1)), a => s"${a(0)} XOR ${a(1)}", None, Set("XOR"))),
    Seq( // level 10
      NormalOperator(2, a => and(a(0), a(1)), a => s"${a(0)} AND ${a(1)}", None, Set("AND"))),
    Seq( // level 9
      NormalOperator(1, a => not(a(0)), a => s"NOT ${a(0)}")),
    Seq( // level 8
      NormalOperator(
        2,
        a => equals(a(0), a(1)),
        a => s"${a(0)} = ${a(1)}",
        Some(a => s"${a(1)}"),
        Set("=", "<>", "<", ">", "<=", ">=")
      ),
      NormalOperator(
        2,
        a => notEquals(a(0), a(1)),
        a => s"${a(0)} <> ${a(1)}",
        Some(a => s"<> ${a(1)}"),
        Set("=", "<>", "<", ">", "<=", ">=")
      ),
      NormalOperator(
        2,
        a => lessThan(a(0), a(1)),
        a => s"${a(0)} < ${a(1)}",
        Some(a => s"< ${a(1)}"),
        Set("=", "<>", "<", ">", "<=", ">=")
      ),
      NormalOperator(
        2,
        a => greaterThan(a(0), a(1)),
        a => s"${a(0)} > ${a(1)}",
        Some(a => s"> ${a(1)}"),
        Set("=", "<>", "<", ">", "<=", ">=")
      ),
      NormalOperator(
        2,
        a => lessThanOrEqual(a(0), a(1)),
        a => s"${a(0)} <= ${a(1)}",
        Some(a => s"<= ${a(1)}"),
        Set("=", "<>", "<", ">", "<=", ">=")
      ),
      NormalOperator(
        2,
        a => greaterThanOrEqual(a(0), a(1)),
        a => s"${a(0)} >= ${a(1)}",
        Some(a => s">= ${a(1)}"),
        Set("=", "<>", "<", ">", "<=", ">=")
      )
    ),
    Seq( // level 7
      NormalOperator(2, a => regex(a(0), a(1)), a => s"${a(0)} =~ ${a(1)}", Some(a => s"=~ ${a(1)}")),
      NormalOperator(
        2,
        a => startsWith(a(0), a(1)),
        a => s"${a(0)} STARTS WITH ${a(1)}",
        Some(a => s"STARTS WITH ${a(1)}")
      ),
      NormalOperator(2, a => endsWith(a(0), a(1)), a => s"${a(0)} ENDS WITH ${a(1)}", Some(a => s"ENDS WITH ${a(1)}")),
      NormalOperator(
        2,
        a => contains(a(0), a(1)),
        a => s"${a(0)} CONTAINS ${a(1)}",
        Some(a => s"CONTAINS ${a(1)}"),
        onlyInVersions = Set(CypherVersion.Cypher25)
      ),
      NormalOperator(
        2,
        a => contains(a(0), a(1)),
        a => s"${a(0)} CONTAINS ${a(1)}",
        None,
        onlyInVersions = Set(CypherVersion.Cypher5)
      ),
      NormalOperator(2, a => in(a(0), a(1)), a => s"${a(0)} IN ${a(1)}", Some(a => s"IN ${a(1)}")),
      NormalOperator(1, a => isNull(a(0)), a => s"${a(0)} IS NULL", Some(_ => s"IS NULL")),
      NormalOperator(1, a => isNotNull(a(0)), a => s"${a(0)} IS NOT NULL", Some(_ => s"IS NOT NULL")),
      NormalOperator(
        1,
        a => isTyped(a(0), symbols.CTInteger),
        a => s"${a(0)} IS :: INTEGER",
        Some(_ => s"IS TYPED INTEGER"), // this is inconsistency with motivation in Cypher 5 but not in Cypher 25
        eagerlyConsumedOperatorSymbols = Set("|")
      ),
      NormalOperator(
        1,
        a => isNotTyped(a(0), symbols.CTInteger),
        a => s"${a(0)} IS NOT :: INTEGER",
        Some(_ => s"IS NOT TYPED INTEGER"), // this is inconsistency with motivation in Cypher 5 but not in Cypher 25
        eagerlyConsumedOperatorSymbols = Set("|")
      ),
      NormalOperator(
        1,
        a => isNormalized(a(0), NFCNormalForm),
        a => s"${a(0)} IS NFC NORMALIZED",
        Some(_ => s"IS NFC NORMALIZED")
      ),
      NormalOperator(
        1,
        a => isNotNormalized(a(0), NFCNormalForm),
        a => s"${a(0)} IS NOT NFC NORMALIZED",
        Some(_ => s"IS NOT NFC NORMALIZED")
      ),
      NormalOperator(
        1,
        a => hasLabels(a(0), Seq(labelName("Label1"), labelName("Label2")), isPostfix = HasLabels.isPostfixDefault),
        a => s"${a(0)}:Label1:Label2",
        Some(_ => s":Label1:Label2"),
        eagerlyConsumedOperatorSymbols = Set(":"),
        isParseable = false,
        onlyInVersions = Set(CypherVersion.Cypher25)
      ),
      NormalOperator(
        1,
        a =>
          labelExpressionPredicate(
            a(0),
            labelConjunction(labelOrRelTypeLeaf("Label1"), labelOrRelTypeLeaf("Label2")),
            isParenthesized = false,
            LabelExpressionPredicate.isPostfixDefault
          ),
        a => s"${a(0)}:Label1&Label2",
        Some(_ => s":Label1&Label2"),
        eagerlyConsumedOperatorSymbols = Set("|", "&"),
        onlyInVersions = Set(CypherVersion.Cypher25)
      ),
      NormalOperator(
        1,
        a =>
          labelExpressionPredicate(
            a(0),
            labelDisjunction(labelOrRelTypeLeaf("Label1"), labelOrRelTypeLeaf("Label2")),
            isParenthesized = false,
            LabelExpressionPredicate.isPostfixDefault
          ),
        a => s"${a(0)}:Label1|Label2",
        Some(_ => s":Label1|Label2"),
        eagerlyConsumedOperatorSymbols = Set("|", "&"),
        onlyInVersions = Set(CypherVersion.Cypher25)
      )
    ),
    Seq( // level 6
      NormalOperator(2, a => add(a(0), a(1)), a => s"${a(0)} + ${a(1)}", None, Set("+", "-", "||")),
      NormalOperator(2, a => subtract(a(0), a(1)), a => s"${a(0)} - ${a(1)}", None, Set("+", "-", "||")),
      NormalOperator(2, a => concatenate(a(0), a(1)), a => s"${a(0)} || ${a(1)}", None, Set("+", "-", "||"))
    ),
    Seq( // level 5
      NormalOperator(2, a => multiply(a(0), a(1)), a => s"${a(0)} * ${a(1)}", None, Set("*", "/", "%")),
      NormalOperator(2, a => divide(a(0), a(1)), a => s"${a(0)} / ${a(1)}", None, Set("*", "/", "%")),
      NormalOperator(2, a => modulo(a(0), a(1)), a => s"${a(0)} % ${a(1)}", None, Set("*", "/", "%"))
    ),
    Seq( // level 4
      NormalOperator(2, a => pow(a(0), a(1)), a => s"${a(0)} ^ ${a(1)}", None, Set("^"))),
    Seq( // level 3
      NormalOperator(1, a => unaryAdd(a(0)), a => s"+${a(0)}"),
      NormalOperator(1, a => unarySubtract(a(0)), a => s"-${a(0)}")
    ),
    Seq( // level 2
      NormalOperator(1, a => prop(a(0), "prop"), a => s"${a(0)}.prop"),
      NormalOperator(
        2,
        a => containerIndex(a(0), a(1)),
        a => s"${a(0)}[${a(1)}]",
        syntacticallyDelimited = Set(1 -> "]")
      ),
      NormalOperator(2, a => sliceFrom(a(0), a(1)), a => s"${a(0)}[${a(1)}..]", syntacticallyDelimited = Set(1 -> "]")),
      NormalOperator(2, a => sliceTo(a(0), a(1)), a => s"${a(0)}[..${a(1)}]", syntacticallyDelimited = Set(1 -> "]")),
      NormalOperator(
        3,
        a => sliceFull(a(0), a(1), a(2)),
        a => s"${a(0)}[${a(1)}..${a(2)}]",
        syntacticallyDelimited = Set(1 -> "..", 2 -> "]")
      ),
      NormalOperator(
        1,
        a => hasLabels(a(0), Seq(labelName("Label1"), labelName("Label2")), isPostfix = true),
        a => s"${a(0)}:Label1:Label2",
        Some(_ => s":Label1:Label2"),
        eagerlyConsumedOperatorSymbols = Set(":"),
        isParseable = false,
        onlyInVersions = Set(CypherVersion.Cypher5)
      ),
      NormalOperator(
        1,
        a =>
          labelExpressionPredicate(
            a(0),
            labelConjunction(labelOrRelTypeLeaf("Label1"), labelOrRelTypeLeaf("Label2")),
            isParenthesized = false,
            isPostfix = true
          ),
        a => s"${a(0)}:Label1&Label2",
        Some(_ => s":Label1&Label2"),
        eagerlyConsumedOperatorSymbols = Set("|", "&"),
        onlyInVersions = Set(CypherVersion.Cypher5)
      ),
      NormalOperator(
        1,
        a =>
          labelExpressionPredicate(
            a(0),
            labelDisjunction(labelOrRelTypeLeaf("Label1"), labelOrRelTypeLeaf("Label2")),
            isParenthesized = false,
            isPostfix = true
          ),
        a => s"${a(0)}:Label1|Label2",
        Some(_ => s":Label1|Label2"),
        eagerlyConsumedOperatorSymbols = Set("|", "&"),
        onlyInVersions = Set(CypherVersion.Cypher5)
      )
    ),
    Seq( // level 1
      NormalOperator(0, _ => trueLiteral, _ => "true"),
      NormalOperator(0, _ => falseLiteral, _ => "false"),
      NormalOperator(0, _ => literalInt(-123), _ => "-123"),
      NormalOperator(0, _ => literalFloat(-0.456), _ => "-0.456"),
      NormalOperator(0, _ => literalString("abc"), _ => "\"abc\""),
      NormalOperator(0, _ => varFor("n"), _ => "n"),
      NormalOperator(0, _ => parameter("para", symbols.CTAny), _ => "$para"),
      NormalOperator(
        2,
        a => caseExpression(None, None, a(0) -> a(1)),
        a =>
          s"""CASE
             |  WHEN ${a(0)} THEN ${a(1)}
             |END""".stripMargin,
        syntacticallyDelimited = Set(0 -> "THEN", 1 -> "END")
      ),
      NormalOperator(
        3,
        a => caseExpression(None, Some(a(2)), a(0) -> a(1)),
        a =>
          s"""CASE
             |  WHEN ${a(0)} THEN ${a(1)}
             |  ELSE ${a(2)}
             |END""".stripMargin,
        syntacticallyDelimited = Set(0 -> "THEN", 1 -> "ELSE", 2 -> "END")
      ),
      NormalOperator(
        3,
        a => {
          val (a0, a1, a2) = (a(0), a(1), a(2))
          val a1wrapped: Expression = a1 match {
            case contains: Contains             => contains
            case part2: Part2OperatorExpression => part2.dup(a0 +: part2.treeChildren.toSeq.tail)
            case a1naked                        => equals(a0, a1naked)
          }
          caseExpression(Some(a0), None, a1wrapped -> a2)
        },
        a =>
          s"""CASE ${a(0)}
             |  WHEN ${a(1)} THEN ${a(2)}
             |END""".stripMargin,
        syntacticallyDelimited = Set(0 -> "WHEN", 1 -> "THEN", 2 -> "END"),
        part2Arguments = Set(1),
        onlyInVersions = Set(CypherVersion.Cypher5)
      ),
      NormalOperator(
        3,
        a => {
          val (a0, a1, a2) = (a(0), a(1), a(2))
          val a1wrapped: Expression = a1 match {
            case part2: Part2OperatorExpression => part2.dup(a0 +: part2.treeChildren.toSeq.tail)
            case a1naked                        => equals(a0, a1naked)
          }
          caseExpression(Some(a0), None, a1wrapped -> a2)
        },
        a =>
          s"""CASE ${a(0)}
             |  WHEN ${a(1)} THEN ${a(2)}
             |END""".stripMargin,
        syntacticallyDelimited = Set(0 -> "WHEN", 1 -> "THEN", 2 -> "END"),
        part2Arguments = Set(1),
        onlyInVersions = Set(CypherVersion.Cypher25)
      ),
      NormalOperator(
        4,
        a => {
          val (a0, a1, a2, a3) = (a(0), a(1), a(2), a(3))
          val a1wrapped: Expression = a1 match {
            case part2: Part2OperatorExpression => part2.dup(a0 +: part2.treeChildren.toSeq.tail)
            case a1naked                        => equals(a0, a1naked)
          }
          caseExpression(Some(a0), Some(a3), a1wrapped -> a2)
        },
        a =>
          s"""CASE ${a(0)}
             |  WHEN ${a(1)} THEN ${a(2)}
             |  ELSE ${a(3)}
             |END""".stripMargin,
        syntacticallyDelimited = Set(0 -> "WHEN", 1 -> "THEN", 2 -> "ELSE", 3 -> "END"),
        part2Arguments = Set(1)
      ),
      NormalOperator(
        0,
        _ => CountExpression(singleQuery(return_(returnItem(literalInt(1), "1"))))(pos, None, None),
        _ => s"COUNT { RETURN 1 }"
      ),
      NormalOperator(
        0,
        _ => ExistsExpression(singleQuery(return_(returnItem(literalInt(2), "2"))))(pos, None, None),
        _ => s"EXISTS { RETURN 2 }"
      ),
      NormalOperator(
        0,
        _ => CollectExpression(singleQuery(return_(returnItem(literalInt(3), "3"))))(pos, None, None),
        _ => s"COLLECT { RETURN 3 }"
      ),
      NormalOperator(
        1,
        a => MapProjection(varFor("n"), Seq(LiteralEntry(propName("le"), a(0))(pos)))(pos),
        a => s"n{le: ${a(0)}}",
        syntacticallyDelimited = Set(0 -> "}")
      ),
      NormalOperator(
        1,
        a => listComprehension(varFor("x"), a(0), None, None),
        a => s"[x IN ${a(0)}]",
        syntacticallyDelimited = Set(0 -> "|", 0 -> "]")
      ),
      NormalOperator(
        2,
        a => listComprehension(varFor("x"), a(0), None, Some(a(1))),
        a => s"[x IN ${a(0)} | ${a(1)}]",
        syntacticallyDelimited = Set(0 -> "|", 1 -> "]")
      ),
      NormalOperator(
        2,
        a => listComprehension(varFor("x"), a(0), Some(a(1)), None),
        a => s"[x IN ${a(0)} WHERE ${a(1)}]",
        syntacticallyDelimited = Set(0 -> "WHERE", 1 -> "]")
      ),
      NormalOperator(
        3,
        a => listComprehension(varFor("x"), a(0), Some(a(1)), Some(a(2))),
        a => s"[x IN ${a(0)} WHERE ${a(1)} | ${a(2)}]",
        syntacticallyDelimited = Set(0 -> "WHERE", 1 -> "|", 2 -> "]")
      ),
      NormalOperator(
        1,
        a => patternComprehension(relationshipChain(nodePat(Some("n")), relPat(None), nodePat(None)), a(0)),
        a => s"[(n)-->() | ${a(0)}]",
        syntacticallyDelimited = Set(0 -> "]")
      ),
      NormalOperator(
        3,
        a => reduce(varFor("acc"), a(0), varFor("elt"), a(1), a(2)),
        a => s"reduce(acc = ${a(0)}, elt IN ${a(1)} | ${a(2)})",
        syntacticallyDelimited = Set(0 -> ",", 1 -> "|", 2 -> ")")
      ),
      NormalOperator(
        2,
        a => noneInList(varFor("x"), a(0), a(1)),
        a => s"none(x IN ${a(0)} WHERE ${a(1)})",
        syntacticallyDelimited = Set(0 -> "WHERE", 1 -> ")")
      ),
      NormalOperator(
        2,
        a => singleInList(varFor("x"), a(0), a(1)),
        a => s"single(x IN ${a(0)} WHERE ${a(1)})",
        syntacticallyDelimited = Set(0 -> "WHERE", 1 -> ")")
      ),
      NormalOperator(
        2,
        a => anyInList(varFor("x"), a(0), a(1)),
        a => s"any(x IN ${a(0)} WHERE ${a(1)})",
        syntacticallyDelimited = Set(0 -> "WHERE", 1 -> ")")
      ),
      NormalOperator(
        2,
        a => allInList(varFor("x"), a(0), a(1)),
        a => s"all(x IN ${a(0)} WHERE ${a(1)})",
        syntacticallyDelimited = Set(0 -> "WHERE", 1 -> ")")
      ),
      NormalOperator(
        0,
        _ =>
          PatternExpression(RelationshipsPattern(relationshipChain(
            nodePat(Some("n")),
            relPat(None),
            nodePat(None)
          ))(pos))(None, None),
        _ => s"(n)-->()"
      ),
      NormalOperator(
        0,
        _ =>
          ShortestPathExpression(ShortestPathsPatternPart(
            relationshipChain(nodePat(Some("a")), relPat(), nodePat(Some("b"))),
            single = true
          )(pos)),
        _ => s"shortestPath((a)-->(b))"
      ),
      NormalOperator(
        3,
        a => function("funFunction", a(0), a(1), a(2)),
        a => s"funFunction(${a(0)}, ${a(1)}, ${a(2)})",
        None,
        syntacticallyDelimited = Set(0 -> ",", 1 -> ",", 2 -> ")")
      ),
      ParenthesizedExpression
    )
  )

  trait CombinationFilter extends ((Int, Int) => Boolean) {
    // note that level = num of levels - index
    def apply(levelInnerIndex: Int, levelOuterIndex: Int): Boolean
  }

  object AcceptAllCombinations extends CombinationFilter {
    override def apply(levelInnerIndex: Int, levelOuterIndex: Int): Boolean = true
  }

  trait OperatorFilter extends (Operator => Boolean) {
    // note that level = num of levels - index
    def apply(operator: Operator): Boolean
  }

  object AcceptAllOperators extends OperatorFilter {
    override def apply(operator: Operator): Boolean = true
  }

  object IsParseable extends OperatorFilter {
    override def apply(operator: Operator): Boolean = operator.isParseable
  }

  // precedence — all 2-level combinations with random base
  def all2LevelCombinationsWithRandomBase(
    combinationFilter: CombinationFilter = AcceptAllCombinations,
    innerFilter: OperatorFilter = AcceptAllOperators,
    outerFilter: OperatorFilter = AcceptAllOperators
  ): Seq[(Expression, String, Set[CypherVersion])] = {
    val levelsToTestWithIndex = precedenceLevel.dropRight(1).zipWithIndex
    val baseLevel = precedenceLevel.last.filter(_.numArgs == 0)
    for {
      (levelInner, levelInnerIndex) <- levelsToTestWithIndex
      (levelOuter, levelOuterIndex) <- levelsToTestWithIndex
      if combinationFilter(levelInnerIndex, levelOuterIndex)
      opInner <- levelInner
      if innerFilter(opInner)
      opOuter <- levelOuter
      if outerFilter(opOuter)
      onlyInVersions = opInner.onlyInVersions intersect opOuter.onlyInVersions
      if onlyInVersions.nonEmpty
    } yield {
      val base = (0 until opOuter.numArgs).map(_ => pick(baseLevel, opInner.numArgs))
      val ast = opOuter.astGen(
        (0 until opOuter.numArgs).map(i => opInner.astGen(base(i).map(_.astGen(Seq()))))
      )
      val argsOuter = (0 until opOuter.numArgs).map(i => {
        val argsInner = base(i).map(_.cypherGen(Seq()))
        val arg = opInner.cypherGenPart2 match {
          case Some(cypherGenPart2) if opOuter.part2Arguments contains i => cypherGenPart2(argsInner)
          case _                                                         => opInner.cypherGen(argsInner)
        }
        if (levelInnerIndex > levelOuterIndex || opOuter.syntacticallyDelimited.exists(_._1 == i)) arg
        else s"($arg)"
      })
      val cypher = opOuter.cypherGen(argsOuter)
      val versions = base.flatten.foldLeft(onlyInVersions) {
        case (accVersions, arg) => accVersions intersect arg.onlyInVersions
      }
      (ast, cypher, versions)
    }
  }

  def sampledNLevelCombinationsWithRandomBase(
    sampleSize: Int,
    levels: Int,
    operatorFilter: OperatorFilter = AcceptAllOperators
  ): Seq[(Expression, String, Set[CypherVersion])] = {
    val levelsToTestWithIndex =
      (precedenceLevel.dropRight(1).map(_.filter(operatorFilter)) :+ precedenceLevel.last.filter {
        case nop: NormalOperator     => nop.numArgs > 0
        case ParenthesizedExpression => false
      }).zipWithIndex
    val baseLevel = precedenceLevel.last.filter(_.numArgs == 0)
    for {
      i <- 0 until sampleSize // sample size
    } yield {
      def buildLevel(
        nestingLevel: Int = levels, // expression depth
        parentIndex: Int = -1,
        syntaxDelimiterByParent: Set[String] = Set.empty,
        isPart2Argument: Boolean = false
      ): (Expression, String, Set[CypherVersion], Set[String]) = {
        if (nestingLevel == 0) {
          val base = pick(baseLevel)
          (base.astGen(Seq()), base.cypherGen(Seq()), base.onlyInVersions, base.eagerlyConsumedOperatorSymbols)
        } else {
          val opLevel = pick(levelsToTestWithIndex)
          val op = pick(opLevel._1)
          val opIndex = opLevel._2
          val syntacticallyDelimited = op.syntacticallyDelimited.groupMap(_._1)(_._2)
          val args = {
            (0 until op.numArgs).map(i =>
              buildLevel(
                nestingLevel - 1,
                opIndex,
                syntacticallyDelimited.getOrElse(i, Set.empty),
                op.part2Arguments contains i
              )
            )
          }
          val ast = op.astGen(args.map(_._1))
          val cypherNaked = op.cypherGenPart2 match {
            case Some(cypherGenPart2) if isPart2Argument => cypherGenPart2(args.map(_._2))
            case _                                       => op.cypherGen(args.map(_._2))
          }

          val lastArgsEagerlyConsumed = args.last._4
          // if the last operand is syntactically delimited the operator does not have eagerly consumed symbols
          // otherwise, the operator eagerly consumes whatever its last operand eagerly consumes or its own symbol
          val eagerlyConsumedOperatorSymbols =
            syntacticallyDelimited.get(op.numArgs - 1).map(_ => Set.empty[String])
              .getOrElse(lastArgsEagerlyConsumed union op.eagerlyConsumedOperatorSymbols)

          // this operator is syntactically delimited if
          val isSyntacticallyDelimitedByParent = {
            // the parent claims to delimit the operand this operator is an argument to
            syntaxDelimiterByParent.nonEmpty &&
            // and none of the parents delimiters symbols for the operand is eagerly consumed by this operator
            !syntaxDelimiterByParent.exists(delimiter => eagerlyConsumedOperatorSymbols contains delimiter)
          }
          val (cypher, eagerlyConsumed) =
            if (isSyntacticallyDelimitedByParent || opIndex > parentIndex) {
              (cypherNaked, eagerlyConsumedOperatorSymbols)
            } else {
              (s"($cypherNaked)", ParenthesizedExpression.eagerlyConsumedOperatorSymbols)
            }
          val versions = args.foldLeft(op.onlyInVersions) {
            case (accVersions, arg) => accVersions intersect arg._3
          }
          (ast, cypher, versions, eagerlyConsumed)
        }
      }
      val (ast, cypher, versions, _) = buildLevel()
      (ast, cypher, versions)
    }
  }

  private val rand = new Random(1)

  def pick[T](list: Seq[T], n: Int): Seq[T] = {
    (1 to n).map(_ => list(rand.nextInt(list.size)))
  }

  def pick[T](list: Seq[T]): T = {
    list(rand.nextInt(list.size))
  }

  def pickWhere[T](list: Seq[T], condition: (T => Boolean)): Option[T] = {
    val validPicks = list.filter(condition)
    if (validPicks.isEmpty) {
      None
    } else {
      Some(pick(validPicks))
    }
  }
}
