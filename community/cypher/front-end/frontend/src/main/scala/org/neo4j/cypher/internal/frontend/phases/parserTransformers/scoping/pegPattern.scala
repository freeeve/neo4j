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
package org.neo4j.cypher.internal.frontend.phases.parserTransformers.scoping

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.expressions.LogicalVariable
import org.neo4j.cypher.internal.expressions.NamedPatternPart
import org.neo4j.cypher.internal.expressions.NodePattern
import org.neo4j.cypher.internal.expressions.ParenthesizedPath
import org.neo4j.cypher.internal.expressions.PathConcatenation
import org.neo4j.cypher.internal.expressions.PathPatternPart
import org.neo4j.cypher.internal.expressions.Pattern
import org.neo4j.cypher.internal.expressions.Pattern.ForMatch
import org.neo4j.cypher.internal.expressions.Pattern.ForUpdate
import org.neo4j.cypher.internal.expressions.PatternAtom
import org.neo4j.cypher.internal.expressions.PatternElement
import org.neo4j.cypher.internal.expressions.PatternPart
import org.neo4j.cypher.internal.expressions.PatternPart.AllPaths
import org.neo4j.cypher.internal.expressions.PatternPartWithSelector
import org.neo4j.cypher.internal.expressions.QuantifiedPath
import org.neo4j.cypher.internal.expressions.RelationshipChain
import org.neo4j.cypher.internal.expressions.RelationshipPattern
import org.neo4j.cypher.internal.expressions.ShortestPathsPatternPart
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator

import scala.annotation.tailrec

case class pegPattern(anonVarGen: AnonymousVariableNameGenerator) {

  def apply(pattern: Pattern, incoming: RegularContext, version: CypherVersion): WorkingScope = {
    implicit val astNode: ASTNode = pattern
    val patternIncomingContext = PatternIncomingContext(
      topologicalConstants = incoming.constants,
      predicateConstants = incoming.constants union (pattern match {
        case _: ForMatch => collectVisibleVariables(pattern) diff incoming.constants
        // TODO in Cypher 5, expressions can still see variables bound by previous patterns
        case _: ForUpdate => Set.empty
      }),
      pathConstants = pattern match {
        case _: ForMatch  => collectPathVariables(pattern)
        case _: ForUpdate => Set.empty
      },
      groupConstants = pattern match {
        case _: ForMatch  => collectGroupVariables(pattern)
        case _: ForUpdate => Set.empty
      }
    )
    val children =
      pattern.patternParts.scanLeft(WorkingScope.aprioriPattern(patternIncomingContext, RegularContext.unit)) {
        case (precedingPartsScope, currentPart) =>
          val newIncoming =
            precedingPartsScope.patternIncoming.amendedWithTopologicalConstant(precedingPartsScope.outgoing.variables)
          scopePatternPart(currentPart, newIncoming, version)
      }.tail
    if (children.size == 1) {
      children.head
    } else {
      val declared = collectDeclaredFromChildren(children)
      val columns = collectColumnsFromChildren(children)
      patternIncomingContext.resultScope(TableResult(columns), children, declared = declared)
    }
  }

  def apply(patternPart: PatternPart, incoming: RegularContext, version: CypherVersion): WorkingScope = {
    val patternIncomingContext = PatternIncomingContext(
      topologicalConstants = incoming.constants,
      predicateConstants = incoming.constants,
      pathConstants = Set.empty,
      groupConstants = Set.empty
    )
    scopePatternPart(patternPart, patternIncomingContext, version)
  }

  def apply(patternElement: PatternElement, incoming: RegularContext, version: CypherVersion): WorkingScope = {
    val patternIncomingContext = PatternIncomingContext(
      topologicalConstants = incoming.constants,
      predicateConstants =
        incoming.constants union (collectVisibleVariablesOfPatternElement(patternElement) diff incoming.constants),
      pathConstants = Set.empty,
      groupConstants = Set.empty
    )
    scopePatternElement(patternElement, patternIncomingContext, version)
  }

  private def scopePatternPart(
    patternPart: PatternPart,
    incoming: PatternIncomingContext,
    version: CypherVersion
  ): PatternScope = {
    implicit val astNode: ASTNode = patternPart
    patternPart match {
      case PatternPartWithSelector(selector, patternPart) =>
        selector match {
          case AllPaths() => scopePatternPart(patternPart, incoming, version)
          case _ =>
            val patternPartIncoming = incoming.removePathConstants()
            val patternPartScope = scopePatternPart(patternPart, patternPartIncoming, version)
            val children = Seq(patternPartScope)
            incoming.resultScope(
              patternPartScope.result,
              children,
              patternPartScope.declared
            )
        }
      case NamedPatternPart(variable, patternPart) =>
        val child = scopePatternPart(patternPart, incoming, version)
        val declared = Declarations(Seq.empty, variable +: child.declared.variables)
        val columns = variable +: child.result.columns
        incoming.resultScope(TableResult(columns), Seq(child), declared)
      case PathPatternPart(element) => scopePatternElement(element, incoming, version)
      case sppp @ ShortestPathsPatternPart(element, _) =>
        scopePatternElement(element, incoming, version).withAstNode(sppp)
    }
  }

  private def scopePatternElement(
    patternElement: PatternElement,
    incoming: PatternIncomingContext,
    version: CypherVersion
  ): PatternScope = {
    implicit val astNode: ASTNode = patternElement
    patternElement match {
      case PathConcatenation(pathFactors) =>
        val children = pathFactors.scanLeft(WorkingScope.aprioriPattern(incoming, RegularContext.unit)) {
          case (precedingFactorsScope, currentFactor) =>
            val newIncoming =
              precedingFactorsScope.patternIncoming.amendedWithTopologicalConstant(
                precedingFactorsScope.outgoing.variables
              )
            scopePatternElement(currentFactor, newIncoming, version)
        }.tail
        val declared = collectDeclaredFromChildren(children)
        val columns = collectColumnsFromChildren(children)
        incoming.resultScope(TableResult(columns), children, declared = declared)
      case quantifiedPath: QuantifiedPath =>
        scopeQuantifiedPath(quantifiedPath, incoming, version)
      case parenthesizedPath: ParenthesizedPath =>
        scopeParenthesizedPath(parenthesizedPath, incoming, version)
      case nodePattern: NodePattern =>
        scopePatternAtom(nodePattern, incoming, version)
      case relationshipChain: RelationshipChain =>
        val patternAtoms = collectPatternAtoms(relationshipChain)
        val children =
          patternAtoms.scanLeft(WorkingScope.aprioriPattern(incoming, RegularContext.unit)) {
            case (precedingAtomsScope, currentAtom) =>
              val newIncoming = precedingAtomsScope.patternIncoming.amendedWithTopologicalConstant(
                precedingAtomsScope.outgoing.variables
              )
              scopePatternAtom(currentAtom, newIncoming, version)
          }.tail
        val declared = collectDeclaredFromChildren(children)
        val columns = collectColumnsFromChildren(children)
        incoming.resultScope(TableResult(columns), children, declared)
    }
  }

  private def scopePatternAtom(
    patternAtom: PatternAtom,
    incoming: PatternIncomingContext,
    version: CypherVersion
  ): PatternScope = {
    implicit val astNode: ASTNode = patternAtom
    patternAtom match {
      case NodePattern(variableOpt, labelExpressionOpt, propertiesOpt, predicateOpt) =>
        val predicateIncoming = variableOpt.map(v => incoming.amendedWithTopologicalConstant(v)).getOrElse(incoming)
        val labelExpressionScopeOpt =
          labelExpressionOpt.map(labelExpression => scopePredicate(labelExpression, predicateIncoming, version))
        val propertiesScopeOpt =
          propertiesOpt.map(expression => scopePredicate(expression, predicateIncoming, version))
        val predicateScopeOpt = predicateOpt.map(predicate => scopePredicate(predicate, predicateIncoming, version))
        val (boundVariables, newVariables) = variableOpt.partition(v => incoming.topologicalConstants contains v)
        val newVariablesWithAnon =
          if (variableOpt.isEmpty) Seq(Variable(anonVarGen.nextName)(patternAtom.position, isIsolated = false))
          else newVariables.toSeq
        val columns = variableOpt.toSeq
        val children = Seq(labelExpressionScopeOpt, propertiesScopeOpt, predicateScopeOpt).flatten
        val declared = Declarations(Seq.empty, newVariablesWithAnon)
        incoming.resultScope(TableResult(columns), children, declared, boundVariables)

      case RelationshipPattern(variableOpt, labelExpressionOpt, varLengthOpt, propertiesOpt, predicateOpt, _) =>
        val varLengthIncoming =
          if (varLengthOpt.isEmpty) incoming
          else PatternIncomingContext.unit
        val predicateIncoming =
          variableOpt.map(v => varLengthIncoming.amendedWithTopologicalConstant(v)).getOrElse(varLengthIncoming)
        val labelExpressionScopeOpt =
          labelExpressionOpt.map(labelExpression => scopePredicate(labelExpression, predicateIncoming, version))
        val propertiesScopeOpt = propertiesOpt.map(expression => scopePredicate(expression, predicateIncoming, version))
        val predicateScopeOpt = predicateOpt.map(predicate => scopePredicate(predicate, predicateIncoming, version))
        val (boundVariables, newVariables) = variableOpt.partition(v => incoming.topologicalConstants contains v)
        val newVariablesWithAnon =
          if (variableOpt.isEmpty) Seq(Variable(anonVarGen.nextName)(patternAtom.position, isIsolated = false))
          else newVariables.toSeq
        val columns = variableOpt.toSeq
        val children = Seq(labelExpressionScopeOpt, propertiesScopeOpt, predicateScopeOpt).flatten
        val declared = Declarations(Seq.empty, newVariablesWithAnon)
        incoming.resultScope(TableResult(columns), children, declared, boundVariables)

      case parenthesizedPath: ParenthesizedPath => scopeParenthesizedPath(parenthesizedPath, incoming, version)
      case quantifiedPath: QuantifiedPath       => scopeQuantifiedPath(quantifiedPath, incoming, version)
    }
  }

  private def scopeParenthesizedPath(
    parenthesizedPath: ParenthesizedPath,
    incoming: PatternIncomingContext,
    version: CypherVersion
  ): PatternScope = {
    implicit val astNode: ASTNode = parenthesizedPath
    val ParenthesizedPath(patternPart, whereExpressionOpt) = parenthesizedPath
    val newIncoming = incoming.removePathConstants()
    val patternPartScope = scopePatternPart(patternPart, newIncoming, version)
    val whereExpressionScopes = whereExpressionOpt.map(whereExpression =>
      Seq(scopePredicate(whereExpression, newIncoming, version))
    ).getOrElse(Seq.empty[WorkingScope])
    val children = patternPartScope +: whereExpressionScopes
    incoming.resultScope(
      patternPartScope.result,
      children,
      patternPartScope.declared
    )
  }

  private def scopeQuantifiedPath(
    quantifiedPath: QuantifiedPath,
    incoming: PatternIncomingContext,
    version: CypherVersion
  ): PatternScope = {
    implicit val astNode: ASTNode = quantifiedPath
    val QuantifiedPath(patternPart, _, whereExpressionOpt, _) = quantifiedPath
    val newIncoming = incoming.removePathConstants()
    val patternPartScope = scopePatternPart(patternPart, newIncoming, version)
    val whereExpressionScopes = whereExpressionOpt.map(whereExpression =>
      Seq(scopePredicate(whereExpression, newIncoming, version))
    ).getOrElse(Seq.empty[WorkingScope])
    val children = patternPartScope +: whereExpressionScopes
    incoming.resultScope(
      patternPartScope.result,
      children,
      declared = patternPartScope.declared
    )
  }

  @inline private def scopePredicate(
    astNode: ASTNode,
    incoming: PatternIncomingContext,
    version: CypherVersion
  ): WorkingScope = {
    ScopeSurveyor.scope(
      astNode,
      RegularContext(
        constants = incoming.predicateConstants union incoming.pathConstants,
        variables = ScopeSurveyor.unitVariables
      ),
      anonVarGen,
      version
    )
  }

  private def collectPathVariables(pattern: Pattern): Set[LogicalVariable] = {
    // path variable can only be assigned at the top
    def collectPathVariablesOfPatternPart(patternPart: PatternPart): Set[LogicalVariable] = {
      patternPart match {
        case NamedPatternPart(variable, _) => Set(variable)
        case PatternPartWithSelector(_, patternPart) =>
          collectPathVariablesOfPatternPart(patternPart)
        case _ => Set.empty
      }
    }.toSet

    pattern.patternParts.flatMap(pp => collectPathVariablesOfPatternPart(pp)).toSet
  }

  private def collectGroupVariables(pattern: Pattern): Set[LogicalVariable] = {
    def collectGroupsFromElement(element: PatternElement): Set[LogicalVariable] = {
      element match {
        case PathConcatenation(factors) => factors.flatMap(collectGroupsFromElement).toSet
        case QuantifiedPath(part, _, _, variableGroupings) =>
          collectPathVariablesOfPatternPart(part) union variableGroupings.map(_.singleton)
        case ParenthesizedPath(part, _) => collectPathVariablesOfPatternPart(part)
        case _                          => Set.empty
      }
    }

    @tailrec
    def collectPathVariablesOfPatternPart(patternPart: PatternPart): Set[LogicalVariable] = {
      patternPart match {
        case NamedPatternPart(_, anon)               => collectPathVariablesOfPatternPart(anon)
        case PatternPartWithSelector(_, patternPart) => collectPathVariablesOfPatternPart(patternPart)
        case PathPatternPart(element)                => collectGroupsFromElement(element)
        case _                                       => Set.empty
      }
    }

    pattern.patternParts.flatMap(pp => collectPathVariablesOfPatternPart(pp)).toSet
  }

  private def collectVisibleVariables(pattern: Pattern): Set[LogicalVariable] = {
    pattern.patternParts.flatMap(pp => collectVisibleVariablesOfPatternPart(pp)).toSet
  }

  @tailrec
  private def collectVisibleVariablesOfPatternPart(patternPart: PatternPart): Set[LogicalVariable] = {
    patternPart match {
      case PatternPartWithSelector(_, patternPart) =>
        collectVisibleVariablesOfPatternPart(patternPart)
      case NamedPatternPart(_, patternPart) =>
        // the path variables are only visible after the match
        collectVisibleVariablesOfPatternPart(patternPart)
      case PathPatternPart(element) =>
        collectVisibleVariablesOfPatternElement(element)
      case ShortestPathsPatternPart(element, _) =>
        collectVisibleVariablesOfPatternElement(element)
    }
  }

  private def collectVisibleVariablesOfPatternElement(patternElement: PatternElement): Set[LogicalVariable] = {
    patternElement match {
      case PathConcatenation(factors) =>
        factors.flatMap(pe => collectVisibleVariablesOfPatternElement(pe)).toSet
      case quantifiedPath: QuantifiedPath =>
        collectVisibleVariablesOfQuantifiedPath(quantifiedPath)
      case ParenthesizedPath(patternPart, _) =>
        collectVisibleVariablesOfPatternPart(patternPart)
      case nodePattern: NodePattern =>
        collectVisibleVariablesOfPatternAtom(nodePattern)
      case relationshipChain: RelationshipChain =>
        collectPatternAtoms(relationshipChain).flatMap(pa => collectVisibleVariablesOfPatternAtom(pa)).toSet
    }
  }

  private def collectVisibleVariablesOfPatternAtom(patternAtom: PatternAtom): Set[LogicalVariable] = {
    patternAtom match {
      case NodePattern(Some(variable), _, _, _)               => Set(variable)
      case RelationshipPattern(Some(variable), _, _, _, _, _) => Set(variable)
      case quantifiedPath: QuantifiedPath                     => collectVisibleVariablesOfQuantifiedPath(quantifiedPath)
      case _                                                  => Set.empty
    }
  }

  @inline
  private def collectVisibleVariablesOfQuantifiedPath(quantifiedPath: QuantifiedPath): Set[LogicalVariable] = {
    quantifiedPath.variableGroupings.map(_.group)
  }

  private def collectPatternAtoms(relationshipChain: RelationshipChain): Seq[PatternAtom] = {
    val RelationshipChain(leftElement, relationship, rightNodePattern) = relationshipChain
    leftElement match {
      case leftNodePattern: NodePattern => Seq(leftNodePattern, relationship, rightNodePattern)
      case leftRelationshipChain: RelationshipChain =>
        collectPatternAtoms(leftRelationshipChain) ++ Seq(relationship, rightNodePattern)
    }
  }

  private def collectColumnsFromChildren(children: Seq[PatternScope]): Seq[LogicalVariable] = {
    // keep them in order, i.e. first appearance wins
    children.foldLeft(Seq.empty[LogicalVariable]) {
      case (acc, child) => acc ++ child.result.columns.filterNot(v => acc contains v)
    }
  }

  private def collectDeclaredFromChildren(children: Seq[PatternScope]): Declarations = {
    // no duplicates in declared
    Declarations(Seq.empty, children.flatMap(_.declared.variables))
  }

//  private def collectAnonDeclaredFromChildren(children: Seq[PatternScope], removeAll: Boolean): Declarations = {
//    // no duplicates in declared
//    Declarations(
//      Seq.empty,
//      children.flatMap {
//        case PatternScope(_, _, _, declared, result, _) if removeAll =>
//          declared.variables.filter(v => result.columns.exists(_.name == v.name))
//        case PatternScope(_: NodePattern, _, _, declared, result, _) =>
//          declared.variables.filter(v => result.columns.exists(_.name == v.name))
//        case x => x.declared.variables
//      }
//    )
//  }
}
