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
package org.neo4j.cypher.internal.ast.prettifier

import org.neo4j.cypher.internal.ast.CollectExpression
import org.neo4j.cypher.internal.ast.CountExpression
import org.neo4j.cypher.internal.ast.ExistsExpression
import org.neo4j.cypher.internal.ast.IsNormalized
import org.neo4j.cypher.internal.ast.IsNotNormalized
import org.neo4j.cypher.internal.ast.IsNotTyped
import org.neo4j.cypher.internal.ast.IsTyped
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.VectorValueConstructor
import org.neo4j.cypher.internal.expressions.Add
import org.neo4j.cypher.internal.expressions.AllIterablePredicate
import org.neo4j.cypher.internal.expressions.AllPropertiesSelector
import org.neo4j.cypher.internal.expressions.AllReducePredicate
import org.neo4j.cypher.internal.expressions.And
import org.neo4j.cypher.internal.expressions.AndedPropertyInequalities
import org.neo4j.cypher.internal.expressions.Ands
import org.neo4j.cypher.internal.expressions.AndsReorderable
import org.neo4j.cypher.internal.expressions.AnyIterablePredicate
import org.neo4j.cypher.internal.expressions.AssertIsNode
import org.neo4j.cypher.internal.expressions.BinaryOperatorExpression
import org.neo4j.cypher.internal.expressions.BinaryPredicateExpression
import org.neo4j.cypher.internal.expressions.CaseExpression
import org.neo4j.cypher.internal.expressions.ChainableBinaryOperatorExpression
import org.neo4j.cypher.internal.expressions.CoerceTo
import org.neo4j.cypher.internal.expressions.Concatenate
import org.neo4j.cypher.internal.expressions.ContainerIndex
import org.neo4j.cypher.internal.expressions.Contains
import org.neo4j.cypher.internal.expressions.CountStar
import org.neo4j.cypher.internal.expressions.DesugaredMapProjection
import org.neo4j.cypher.internal.expressions.DifferentRelationships
import org.neo4j.cypher.internal.expressions.Disjoint
import org.neo4j.cypher.internal.expressions.Divide
import org.neo4j.cypher.internal.expressions.ElementIdToLongId
import org.neo4j.cypher.internal.expressions.EndsWith
import org.neo4j.cypher.internal.expressions.Equals
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.ExtractScope
import org.neo4j.cypher.internal.expressions.FilterScope
import org.neo4j.cypher.internal.expressions.FunctionInvocation
import org.neo4j.cypher.internal.expressions.FunctionInvocation.ArgumentAsc
import org.neo4j.cypher.internal.expressions.FunctionInvocation.ArgumentDesc
import org.neo4j.cypher.internal.expressions.FunctionInvocation.ArgumentUnordered
import org.neo4j.cypher.internal.expressions.FunctionName
import org.neo4j.cypher.internal.expressions.GreaterThan
import org.neo4j.cypher.internal.expressions.GreaterThanOrEqual
import org.neo4j.cypher.internal.expressions.HasALabel
import org.neo4j.cypher.internal.expressions.HasALabelOrType
import org.neo4j.cypher.internal.expressions.HasAnyDynamicLabel
import org.neo4j.cypher.internal.expressions.HasAnyDynamicLabelsOrTypes
import org.neo4j.cypher.internal.expressions.HasAnyDynamicType
import org.neo4j.cypher.internal.expressions.HasAnyLabel
import org.neo4j.cypher.internal.expressions.HasDynamicLabels
import org.neo4j.cypher.internal.expressions.HasDynamicLabelsOrTypes
import org.neo4j.cypher.internal.expressions.HasDynamicType
import org.neo4j.cypher.internal.expressions.HasLabels
import org.neo4j.cypher.internal.expressions.HasLabelsOrTypes
import org.neo4j.cypher.internal.expressions.HasTypes
import org.neo4j.cypher.internal.expressions.In
import org.neo4j.cypher.internal.expressions.IntegerLiteral
import org.neo4j.cypher.internal.expressions.InvalidNotEquals
import org.neo4j.cypher.internal.expressions.IsNotNull
import org.neo4j.cypher.internal.expressions.IsNull
import org.neo4j.cypher.internal.expressions.IsRepeatTrailUnique
import org.neo4j.cypher.internal.expressions.LessThan
import org.neo4j.cypher.internal.expressions.LessThanOrEqual
import org.neo4j.cypher.internal.expressions.ListComprehension
import org.neo4j.cypher.internal.expressions.ListLiteral
import org.neo4j.cypher.internal.expressions.ListSlice
import org.neo4j.cypher.internal.expressions.Literal
import org.neo4j.cypher.internal.expressions.LiteralEntry
import org.neo4j.cypher.internal.expressions.MapExpression
import org.neo4j.cypher.internal.expressions.MapProjection
import org.neo4j.cypher.internal.expressions.Modulo
import org.neo4j.cypher.internal.expressions.Multiply
import org.neo4j.cypher.internal.expressions.NODE_TYPE
import org.neo4j.cypher.internal.expressions.Namespace
import org.neo4j.cypher.internal.expressions.NoneIterablePredicate
import org.neo4j.cypher.internal.expressions.NoneOfRelationships
import org.neo4j.cypher.internal.expressions.NormalForm
import org.neo4j.cypher.internal.expressions.Not
import org.neo4j.cypher.internal.expressions.NotEquals
import org.neo4j.cypher.internal.expressions.Or
import org.neo4j.cypher.internal.expressions.Ors
import org.neo4j.cypher.internal.expressions.Parameter
import org.neo4j.cypher.internal.expressions.PathExpression
import org.neo4j.cypher.internal.expressions.PatternComprehension
import org.neo4j.cypher.internal.expressions.PatternExpression
import org.neo4j.cypher.internal.expressions.Pow
import org.neo4j.cypher.internal.expressions.Property
import org.neo4j.cypher.internal.expressions.PropertySelector
import org.neo4j.cypher.internal.expressions.RELATIONSHIP_TYPE
import org.neo4j.cypher.internal.expressions.ReduceExpression
import org.neo4j.cypher.internal.expressions.ReduceScope
import org.neo4j.cypher.internal.expressions.RegexMatch
import org.neo4j.cypher.internal.expressions.RelationshipsPattern
import org.neo4j.cypher.internal.expressions.SensitiveAutoParameter
import org.neo4j.cypher.internal.expressions.SensitiveLiteral
import org.neo4j.cypher.internal.expressions.ShortestPathExpression
import org.neo4j.cypher.internal.expressions.SingleIterablePredicate
import org.neo4j.cypher.internal.expressions.StartsWith
import org.neo4j.cypher.internal.expressions.StringLiteral
import org.neo4j.cypher.internal.expressions.Subtract
import org.neo4j.cypher.internal.expressions.SymbolicName
import org.neo4j.cypher.internal.expressions.UnaryAdd
import org.neo4j.cypher.internal.expressions.UnarySubtract
import org.neo4j.cypher.internal.expressions.Unique
import org.neo4j.cypher.internal.expressions.VarLengthLowerBound
import org.neo4j.cypher.internal.expressions.VarLengthUpperBound
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.expressions.VariableSelector
import org.neo4j.cypher.internal.expressions.VectorDistanceMetric
import org.neo4j.cypher.internal.expressions.Xor
import org.neo4j.cypher.internal.expressions.functions.UserDefinedFunctionInvocation
import org.neo4j.cypher.internal.label_expressions.LabelExpression
import org.neo4j.cypher.internal.label_expressions.LabelExpression.ColonConjunction
import org.neo4j.cypher.internal.label_expressions.LabelExpression.ColonDisjunction
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Conjunctions
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Disjunctions
import org.neo4j.cypher.internal.label_expressions.LabelExpression.DynamicLeaf
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Leaf
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Negation
import org.neo4j.cypher.internal.label_expressions.LabelExpression.Wildcard
import org.neo4j.cypher.internal.label_expressions.LabelExpressionDynamicLeafExpression
import org.neo4j.cypher.internal.label_expressions.LabelExpressionPredicate
import org.neo4j.cypher.internal.logical.plans.CoerceToPredicate
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.util.Stringifier

trait ExpressionStringifier {
  def apply(ast: Expression): String = apply(ast, shouldBacktickEmpty = false)
  def apply(ast: Expression, shouldBacktickEmpty: Boolean): String

  def apply(expressions: Seq[Expression], separator: String): String =
    apply(expressions, separator, shouldBacktickEmpty = false)

  def apply(expressions: Seq[Expression], separator: String, shouldBacktickEmpty: Boolean): String =
    expressions.map(apply(_, shouldBacktickEmpty)).mkString(separator)
  def apply(s: SymbolicName): String = apply(s, shouldBacktickEmpty = false)
  def apply(s: SymbolicName, shouldBacktickEmpty: Boolean): String
  def apply(ns: Namespace): String = apply(ns, shouldBacktickEmpty = false)
  def apply(ns: Namespace, shouldBacktickEmpty: Boolean): String
  def patterns: PatternStringifier
  def pathSteps: PathStepStringifier
  def backtick(in: String): String
  def backtick(in: String, shouldBacktickEmpty: Boolean): String
  def quote(txt: String): String
  def escapePassword(password: Expression): String
  def stringifyLabelExpression(le: LabelExpression): String
}

private class DefaultExpressionStringifier(
  extensionStringifier: ExpressionStringifier.Extension,
  alwaysParens: Boolean,
  alwaysBacktick: Boolean,
  preferSingleQuotes: Boolean,
  sensitiveParamsAsParams: Boolean,
  javaCompatible: Boolean
) extends ExpressionStringifier {

  // There is an APOC dependency on this constructor in apoc.util.LogsUtil
  // Any change to this constructor will break APOC.
  def this(
    extensionStringifier: ExpressionStringifier.Extension,
    alwaysParens: Boolean,
    alwaysBacktick: Boolean,
    preferSingleQuotes: Boolean,
    sensitiveParamsAsParams: Boolean
  ) = this(extensionStringifier, alwaysParens, alwaysBacktick, preferSingleQuotes, sensitiveParamsAsParams, false)

  override val patterns: PatternStringifier = PatternStringifier(this)

  override val pathSteps: PathStepStringifier = PathStepStringifier(this)

  private val prettifier: Prettifier = Prettifier(this)

  override def apply(ast: Expression, shouldBacktickEmpty: Boolean): String =
    stringify(ast, shouldBacktickEmpty)._1

  override def apply(s: SymbolicName, shouldBacktickEmpty: Boolean): String =
    backtick(s.name, shouldBacktickEmpty)

  override def apply(ns: Namespace, shouldBacktickEmpty: Boolean): String =
    ns.parts.map(backtick(_, shouldBacktickEmpty)).mkString(".")

  @inline
  private def delimitedInner(
    outer: Expression,
    shouldBacktickEmpty: Boolean,
    isCaseExpression: Boolean = false,
    symbolicDelimiter: String = ""
  )(innerExp: Expression): String =
    inner(outer, shouldBacktickEmpty, isCaseExpression, isSyntactic = true, symbolicDelimiter)(innerExp)._1

  @inline
  private def nonLastInner(
    outer: Expression,
    shouldBacktickEmpty: Boolean,
    isCaseExpression: Boolean = false,
    isSyntactic: Boolean = false,
    symbolicDelimiter: String = ""
  )(innerExp: Expression): String =
    inner(outer, shouldBacktickEmpty, isCaseExpression, isSyntactic, symbolicDelimiter)(innerExp)._1

  private def inner(
    outer: Expression,
    shouldBacktickEmpty: Boolean,
    isCaseExpression: Boolean = false,
    isSyntactic: Boolean = false,
    symbolicDelimiter: String = ""
  )(inner: Expression): (String, EagerConsumption) = {
    val (str, eagerConsumption) = stringify(inner, shouldBacktickEmpty, isCaseExpression)

    def parens = (binding(outer), binding(inner)) match {
      case (_, Syntactic)                 => false
      case (Syntactic, _)                 => false
      case (Precedence(o), Precedence(i)) => i >= o
    }

    // Don't add parenthesis around expressions missing their LHS or are overridden to be syntactic
    // or overridden by avoid parsing ambiguity due to eager consumption of delimiting symbol
    if (((alwaysParens || parens) && !isCaseExpression && !isSyntactic) || eagerConsumption.includes(symbolicDelimiter))
      noEagerConsumption("(" + str + ")")
    else
      (str, eagerConsumption)
  }

  private def prettifySubqueryInBraces(q: Query): String = {
    // TODO: does this need shouldBacktickEmpty?
    val p = prettifier.asString(q)
    if (p.contains(prettifier.NL)) {
      val indented = p.split(prettifier.NL).map(l => s"${prettifier.BASE_INDENT}$l").mkString(
        prettifier.NL,
        prettifier.NL,
        prettifier.NL
      )
      s"{$indented}"
    } else {
      s"{ $p }"
    }
  }

  /*
   * Eager consumption expresses behavior of the parser that requires the prettifier to
   * generate parentheses around a sub expression subEx certain situations.
   *
   * If the parsing of subEx eagerly consumes a string foo such that the absence of foo
   * signals the end of subEx, e.g. in A + B + B + ... the + is the eagerly consumed string,
   * then subEx needs to parenthesized if it appears in place that is delimited by the string
   * that it eagerly consumes. Otherwise, parsing of the prettifier output will to correctly
   * detect where subEx ends.
   *
   * Since these situation are rare, we only record the eagerly consumes strings and the
   * delimiting string for these situations and not in general. If new syntax is added that
   * results in more such situations, then the prettifier needs to be adjusted accordingly.
   */
  sealed private trait EagerConsumption {
    def includes(symbol: String): Boolean
  }

  private case class EagerlyConsuming(symbols: String*) extends EagerConsumption {
    override def includes(symbol: String): Boolean = symbol.nonEmpty && (symbols contains symbol)
  }

  private case object NoEagerConsumption extends EagerConsumption {
    override def includes(symbol: String): Boolean = false
  }
  @inline private def noEagerConsumption(cypher: String): (String, EagerConsumption) = (cypher, NoEagerConsumption)

  // withLHS is for stringifying simple CASE expressions where the LHS has been
  // inferred from the case expression
  private def stringify(
    ast: Expression,
    shouldBacktickEmpty: Boolean,
    isCaseExpression: Boolean = false
  ): (String, EagerConsumption) = {
    ast match {

      case StringLiteral(txt) =>
        noEagerConsumption(quote(txt))

      case l: Literal =>
        noEagerConsumption(if (javaCompatible) {
          l match {
            case n: IntegerLiteral if n.value > Int.MaxValue => n.value.toString + "L"
            case _                                           => l.asCanonicalStringVal
          }
        } else {
          l.asCanonicalStringVal
        })

      // Special case for SIMPLE CASE, when it is an equals, remove the LHS and =
      case e: Equals if isCaseExpression =>
        noEagerConsumption(delimitedInner(ast, shouldBacktickEmpty)(e.rhs))

      case e: BinaryPredicateExpression if isCaseExpression =>
        val (rhs, eagerConsumption) = inner(ast, shouldBacktickEmpty)(e.rhs)
        (s"${e.canonicalOperatorSymbol} $rhs", eagerConsumption)

      case e: BinaryOperatorExpression =>
        val (rhs, eagerConsumption) = inner(ast, shouldBacktickEmpty)(e.rhs)
        (
          s"${nonLastInner(ast, shouldBacktickEmpty)(e.lhs)} ${e.canonicalOperatorSymbol} $rhs",
          eagerConsumption
        )

      case Variable(v) =>
        noEagerConsumption(backtick(v, shouldBacktickEmpty))

      case ListLiteral(expressions) =>
        noEagerConsumption(expressions.map(apply(_, shouldBacktickEmpty)).mkString("[", ", ", "]"))

      // This hack is needed because the following GQL functions do not have their own AST
      case FunctionInvocation(
          FunctionName(Namespace(Nil), "normalize"),
          false,
          IndexedSeq(value, StringLiteral(NormalForm(form))),
          ArgumentUnordered,
          _
        ) =>
        val fn = "normalize" // Can't have backticks because that does not parse as a normalizeFunction
        val as =
          Seq(delimitedInner(ast, shouldBacktickEmpty)(value), form.formName).mkString(", ")
        noEagerConsumption(s"$fn($as)")

      case FunctionInvocation(
          FunctionName(Namespace(Nil), "vector_distance"),
          false,
          IndexedSeq(vector1, vector2, StringLiteral(VectorDistanceMetric(metric))),
          ArgumentUnordered,
          _
        ) =>
        val fn = "vector_distance" // Can't have backticks because that does not parse as a vectorDistanceFunction
        val as = Seq(
          delimitedInner(ast, shouldBacktickEmpty)(vector1),
          delimitedInner(ast, shouldBacktickEmpty)(vector2),
          metric.metricName
        ).mkString(", ")
        noEagerConsumption(s"$fn($as)")

      case FunctionInvocation(
          FunctionName(Namespace(Nil), "vector_norm"),
          false,
          IndexedSeq(vector, StringLiteral(VectorDistanceMetric(metric))),
          ArgumentUnordered,
          _
        ) =>
        val fn = "vector_norm" // Can't have backticks because that does not parse as a vectorNormFunction
        val as =
          Seq(delimitedInner(ast, shouldBacktickEmpty)(vector), metric.metricName).mkString(", ")
        noEagerConsumption(s"$fn($as)")

      case FunctionInvocation(FunctionName(namespace, functionName), distinct, args, order, _) =>
        val ns = apply(namespace, shouldBacktickEmpty)
        val fn = backtick(functionName, shouldBacktickEmpty)
        val np = if (namespace.parts.isEmpty) "" else "."
        val ds = if (distinct) "DISTINCT " else ""
        val as = args.map(delimitedInner(ast, shouldBacktickEmpty)).mkString(", ")
        // NOTE: because order is rendered this will produce Cypher that cannot be parsed
        val o = order match {
          case ArgumentAsc       => " ASC"
          case ArgumentDesc      => " DESC"
          case ArgumentUnordered => ""
        }
        noEagerConsumption(s"$ns$np$fn($ds$as)$o")

      case functionInvocation: UserDefinedFunctionInvocation =>
        // noinspection RedundantDefaultArgument
        stringify(functionInvocation.asUnresolvedFunction, shouldBacktickEmpty, isCaseExpression = false)

      case Property(m, k) =>
        noEagerConsumption(
          s"${nonLastInner(ast, shouldBacktickEmpty)(m)}.${apply(k, shouldBacktickEmpty)}"
        )

      case MapExpression(items) =>
        noEagerConsumption(items.map({
          case (k, i) => s"${apply(k, shouldBacktickEmpty)}: ${apply(i, shouldBacktickEmpty)}"
        }).mkString("{", ", ", "}"))

      case Parameter(name, _, _) =>
        noEagerConsumption(s"$$${backtick(name, shouldBacktickEmpty = true)}")

      case cs: CountStar =>
        noEagerConsumption(cs.asCanonicalStringVal)

      case e @ IsNull(arg) if !isCaseExpression =>
        noEagerConsumption(
          s"${nonLastInner(ast, shouldBacktickEmpty)(arg)} ${e.canonicalOperatorSymbol}"
        )

      case e @ IsNull(_) =>
        noEagerConsumption(e.canonicalOperatorSymbol)

      case e @ IsNotNull(arg) if !isCaseExpression =>
        noEagerConsumption(
          s"${nonLastInner(ast, shouldBacktickEmpty)(arg)} ${e.canonicalOperatorSymbol}"
        )

      case e @ IsNotNull(_) =>
        noEagerConsumption(e.canonicalOperatorSymbol)

      case e @ IsTyped(arg, predicateType) if !isCaseExpression =>
        noEagerConsumption(
          s"${nonLastInner(ast, shouldBacktickEmpty)(arg)} ${e.canonicalOperatorSymbol} ${predicateType.description}"
        )

      // For the 5.x series it is breaking to use `IS ::` alone in a Case Expression
      case _ @IsTyped(_, predicateType) =>
        noEagerConsumption(s"IS TYPED ${predicateType.description}")

      case e @ IsNotTyped(arg, predicateType) if !isCaseExpression =>
        noEagerConsumption(
          s"${nonLastInner(ast, shouldBacktickEmpty)(arg)} ${e.canonicalOperatorSymbol} ${predicateType.description}"
        )

      // For the 5.x series it is breaking to use `IS ::` alone in a Case Expression
      case _ @IsNotTyped(_, predicateType) =>
        noEagerConsumption(s"IS NOT TYPED ${predicateType.description}")

      case IsNormalized(arg, normalForm) if !isCaseExpression =>
        noEagerConsumption(
          s"${nonLastInner(ast, shouldBacktickEmpty)(arg)} IS ${normalForm.description} NORMALIZED"
        )

      case IsNormalized(_, normalForm) =>
        noEagerConsumption(s"IS ${normalForm.description} NORMALIZED")

      case IsNotNormalized(arg, normalForm) if !isCaseExpression =>
        noEagerConsumption(
          s"${nonLastInner(ast, shouldBacktickEmpty)(arg)} IS NOT ${normalForm.description} NORMALIZED"
        )

      case IsNotNormalized(_, normalForm) =>
        noEagerConsumption(s"IS NOT ${normalForm.description} NORMALIZED")

      case VectorValueConstructor(vectorCandidateType, dimension, candidateType) =>
        noEagerConsumption(
          s"vector(${delimitedInner(ast, shouldBacktickEmpty)(vectorCandidateType)}, ${delimitedInner(ast, shouldBacktickEmpty)(dimension)}, ${candidateType.description})"
        )

      case lep: LabelExpressionPredicate if !isCaseExpression =>
        (
          s"${nonLastInner(ast, shouldBacktickEmpty)(lep.entity)}:${stringifyLabelExpression(lep.labelExpression)}",
          EagerlyConsuming("|")
        )

      case lep: LabelExpressionPredicate =>
        (s":${stringifyLabelExpression(lep.labelExpression)}", EagerlyConsuming("|"))

      case ContainerIndex(exp, idx) =>
        noEagerConsumption(
          s"${nonLastInner(ast, shouldBacktickEmpty)(exp)}[${delimitedInner(ast, shouldBacktickEmpty)(idx)}]"
        )

      case ListSlice(list, start, end) =>
        val l = start.map(delimitedInner(ast, shouldBacktickEmpty)).getOrElse("")
        val r = end.map(delimitedInner(ast, shouldBacktickEmpty)).getOrElse("")
        noEagerConsumption(s"${nonLastInner(ast, shouldBacktickEmpty)(list)}[$l..$r]")

      case PatternExpression(RelationshipsPattern(relChain)) =>
        noEagerConsumption(patterns.apply(relChain))

      case Not(arg) =>
        val (argCypher, eagerConsumption) = inner(ast, shouldBacktickEmpty)(arg)
        (s"NOT $argCypher", eagerConsumption)

      case ListComprehension(s, expression) =>
        val v = apply(s.variable, shouldBacktickEmpty)
        val p = s.innerPredicate.map(pr =>
          " WHERE " + (s.extractExpression match {
            // if there is an extractExpression, then innerPredicate is delimited by a vertical bar (|)
            case Some(_) => delimitedInner(ast, shouldBacktickEmpty, symbolicDelimiter = "|")(pr)
            // otherwise, it is not delimited by a vertical bar (|)
            case None => delimitedInner(ast, shouldBacktickEmpty)(pr)
          })
        ).getOrElse("")
        val e = s.extractExpression.map(ex =>
          " | " + delimitedInner(ast, shouldBacktickEmpty)(ex)
        ).getOrElse("")
        val expr = (s.innerPredicate, s.extractExpression) match {
          // if there is no innerPredicate but an extractExpression, then expression is delimited by a vertical bar (|)
          case (None, Some(_)) =>
            delimitedInner(ast, shouldBacktickEmpty, symbolicDelimiter = "|")(expression)
          // otherwise, it is not delimited by a vertical bar (|)
          case (_, _) => delimitedInner(ast, shouldBacktickEmpty)(expression)
        }
        noEagerConsumption(s"[$v IN $expr$p$e]")

      case PatternComprehension(variable, RelationshipsPattern(relChain), predicate, proj) =>
        val v = variable.map(apply(_, shouldBacktickEmpty)).map(_ + " = ").getOrElse("")
        val p = patterns.apply(relChain)
        val w = predicate.map(delimitedInner(
          ast,
          shouldBacktickEmpty,
          symbolicDelimiter = "|"
        )).map(" WHERE " + _).getOrElse("")
        val b = delimitedInner(ast, shouldBacktickEmpty)(proj)
        noEagerConsumption(s"[$v$p$w | $b]")

      case HasLabelsOrTypes(arg, labels) =>
        val l = labels.map(apply(_, shouldBacktickEmpty)).mkString(":", ":", "")
        noEagerConsumption(s"${nonLastInner(ast, shouldBacktickEmpty)(arg)}$l")

      case HasLabels(arg, labels) =>
        val l = labels.map(apply(_, shouldBacktickEmpty)).mkString(":", ":", "")
        noEagerConsumption(s"${nonLastInner(ast, shouldBacktickEmpty)(arg)}$l")

      case HasDynamicLabels(arg, labels) =>
        val l = labels.map(apply(_, shouldBacktickEmpty)).map(l => s":$$all($l)").mkString
        noEagerConsumption(s"${nonLastInner(ast, shouldBacktickEmpty)(arg)}$l")

      case HasAnyLabel(arg, labels) =>
        val l = labels.map(apply(_, shouldBacktickEmpty)).mkString(":", "|", "")
        (s"${nonLastInner(ast, shouldBacktickEmpty)(arg)}$l", EagerlyConsuming("|"))

      case HasAnyDynamicLabel(arg, labels) =>
        val l = labels.map(apply(_, shouldBacktickEmpty)).map(l => s"$$any($l)").mkString(":", "|", "")
        (s"${nonLastInner(ast, shouldBacktickEmpty)(arg)}$l", EagerlyConsuming("|"))

      case HasALabel(arg) =>
        noEagerConsumption(s"${nonLastInner(ast, shouldBacktickEmpty)(arg)}:%")

      case HasALabelOrType(arg) =>
        noEagerConsumption(s"${nonLastInner(ast, shouldBacktickEmpty)(arg)}:%")

      case HasTypes(arg, types) =>
        val t = types.map(apply(_, shouldBacktickEmpty)).mkString(":", ":", "")
        noEagerConsumption(s"${nonLastInner(ast, shouldBacktickEmpty)(arg)}$t")

      case HasDynamicType(arg, types) =>
        val t = types.map(t => s"$$all(${apply(t, shouldBacktickEmpty)})").mkString(":", "&", "")
        // this is parsers as a label expression predicate which eagerly consume vertical bar (|)
        (s"${nonLastInner(ast, shouldBacktickEmpty)(arg)}$t", EagerlyConsuming("|"))

      case HasAnyDynamicType(arg, types) =>
        val t = types.map(t => s"$$any(${apply(t, shouldBacktickEmpty)})").mkString(":", "|", "")
        (s"${nonLastInner(ast, shouldBacktickEmpty)(arg)}$t", EagerlyConsuming("|"))

      case HasDynamicLabelsOrTypes(arg, labelsOrTypes) =>
        val t = labelsOrTypes.map(t => s"$$all(${apply(t, shouldBacktickEmpty)})").mkString(":", "&", "")
        // this is parsers as a label expression predicate which eagerly consume vertical bar (|)
        (s"${nonLastInner(ast, shouldBacktickEmpty)(arg)}$t", EagerlyConsuming("|"))

      case HasAnyDynamicLabelsOrTypes(arg, labelsOrTypes) =>
        val t = labelsOrTypes.map(t => s"$$any(${apply(t, shouldBacktickEmpty)})").mkString(":", "|", "")
        (s"${nonLastInner(ast, shouldBacktickEmpty)(arg)}$t", EagerlyConsuming("|"))

      case AllIterablePredicate(scope, e) =>
        noEagerConsumption(s"all${prettyScope(scope, e, shouldBacktickEmpty)}")

      case AnyIterablePredicate(scope, expression) =>
        noEagerConsumption(s"any${prettyScope(scope, expression, shouldBacktickEmpty)}")

      case SingleIterablePredicate(scope, e) =>
        noEagerConsumption(s"single${prettyScope(scope, e, shouldBacktickEmpty)}")

      case NoneIterablePredicate(scope, e) =>
        noEagerConsumption(s"none${prettyScope(scope, e, shouldBacktickEmpty)}")

      case MapProjection(variable, items) =>
        val itemsText = items.map(apply(_, shouldBacktickEmpty)).mkString(", ")
        noEagerConsumption(s"${apply(variable, shouldBacktickEmpty)}{$itemsText}")

      case DesugaredMapProjection(entity, items, includeAllProps) =>
        val itemsText = {
          val allItems = if (!includeAllProps) items else items :+ AllPropertiesSelector()(InputPosition.NONE)
          allItems.map(apply(_, shouldBacktickEmpty)).mkString(", ")
        }
        noEagerConsumption(s"${apply(entity, shouldBacktickEmpty)}{$itemsText}")

      case LiteralEntry(k, e) =>
        noEagerConsumption(s"${apply(k)}: ${delimitedInner(ast, shouldBacktickEmpty)(e)}")

      case VariableSelector(v) =>
        // noinspection RedundantDefaultArgument
        stringify(v, shouldBacktickEmpty, isCaseExpression = false)

      case PropertySelector(v) =>
        noEagerConsumption(s".${apply(v, shouldBacktickEmpty)}")

      case AllPropertiesSelector() => noEagerConsumption(".*")

      // Generic Case
      case CaseExpression(None, alternatives, default) =>
        noEagerConsumption(Seq(
          Seq("CASE"),
          for {
            (e1, e2) <- alternatives
            i <- Seq(
              s"${prettifier.BASE_INDENT}WHEN ${delimitedInner(ast, shouldBacktickEmpty)(e1)} THEN ${delimitedInner(ast, shouldBacktickEmpty)(e2)}"
            )
          } yield i,
          for {
            e <- default.toSeq
            i <-
              Seq(s"${prettifier.BASE_INDENT}ELSE ${delimitedInner(ast, shouldBacktickEmpty)(e)}")
          } yield i,
          Seq("END")
        ).flatten.mkString(prettifier.NL))

      case CaseExpression(Some(expression), alternatives, default) =>
        noEagerConsumption(Seq(
          Seq(s"CASE ${delimitedInner(ast, shouldBacktickEmpty)(expression)}"),
          for {
            (e1, e2) <- alternatives
            i <- Seq(
              s"${prettifier.BASE_INDENT}WHEN ${delimitedInner(ast, shouldBacktickEmpty, isCaseExpression = true)(e1)} THEN ${delimitedInner(ast, shouldBacktickEmpty)(e2)}"
            )
          } yield i,
          for {
            e <- default.toSeq
            i <-
              Seq(s"${prettifier.BASE_INDENT}ELSE ${delimitedInner(ast, shouldBacktickEmpty)(e)}")
          } yield i,
          Seq("END")
        ).flatten.mkString(prettifier.NL))

      case Ands(expressions) =>
        type ChainOp = Expression with ChainableBinaryOperatorExpression

        def findChain: Option[List[ChainOp]] = {
          val chainable = expressions.collect { case e: ChainableBinaryOperatorExpression => e }
          def allChainable = chainable.size == expressions.size && chainable.size > 1
          def formsChain = chainable.sliding(2).forall(p => p.head.rhs == p.last.lhs)
          if (allChainable && formsChain) Some(chainable.toList) else None
        }

        findChain match {
          case Some(chain) =>
            val head = apply(chain.head, shouldBacktickEmpty)
            val (tailUnflattened, eagerConsumptions) = chain.tail.map(o => {
              val (rhs, eagerConsumption) = inner(ast, shouldBacktickEmpty)(o.rhs)
              (List(o.canonicalOperatorSymbol, rhs), eagerConsumption)
            }).unzip
            ((head :: tailUnflattened.flatten).mkString(" "), eagerConsumptions.last)
          case None =>
            val (operands, eagerConsumptions) =
              expressions.map(x => inner(ast, shouldBacktickEmpty)(x)).unzip
            (operands.mkString(" AND "), eagerConsumptions.last)
        }

      case AndsReorderable(expressions) =>
        val ands = Ands(expressions)(InputPosition.NONE)
        noEagerConsumption(s"(${apply(ands, shouldBacktickEmpty)})")

      case AndedPropertyInequalities(_, _, exprs) =>
        val (operands, eagerConsumptions) =
          // noinspection RedundantDefaultArgument
          exprs.map(x => stringify(x, shouldBacktickEmpty, isCaseExpression = false)).toIndexedSeq.unzip
        (operands.mkString(" AND "), eagerConsumptions.last)

      case Ors(expressions) =>
        val (operands, eagerConsumptions) =
          expressions.map(x => inner(ast, shouldBacktickEmpty)(x)).unzip
        (operands.mkString(" OR "), eagerConsumptions.last)

      case ShortestPathExpression(pattern) =>
        noEagerConsumption(patterns.apply(pattern))

      case PathExpression(pathStep) =>
        noEagerConsumption(pathSteps(pathStep))

      case x: AllReducePredicate =>
        val a = backtick(x.accumulator.name, shouldBacktickEmpty)
        val i = delimitedInner(ast, shouldBacktickEmpty)(x.init)
        val rVar = backtick(x.reductionStepVariable.name, shouldBacktickEmpty)
        val rGroup = delimitedInner(ast, shouldBacktickEmpty, symbolicDelimiter = "|")(x.list)
        val r = delimitedInner(ast, shouldBacktickEmpty)(x.reductionStep)
        val p = delimitedInner(ast, shouldBacktickEmpty)(x.predicate)
        noEagerConsumption(s"allReduce($a = $i, $rVar IN $rGroup | $r, $p)")

      case ReduceExpression(ReduceScope(Variable(acc), Variable(identifier), expression), init, list) =>
        val a = backtick(acc, shouldBacktickEmpty)
        val v = backtick(identifier, shouldBacktickEmpty)
        val i = delimitedInner(ast, shouldBacktickEmpty)(init)
        val l = delimitedInner(ast, shouldBacktickEmpty, symbolicDelimiter = "|")(list)
        val e = delimitedInner(ast, shouldBacktickEmpty)(expression)
        noEagerConsumption(s"reduce($a = $i, $v IN $l | $e)")

      case _: ExtractScope | _: FilterScope | _: ReduceScope =>
        // These are not really expressions, they are part of expressions
        noEagerConsumption("")

      case ExistsExpression(q) =>
        noEagerConsumption(s"EXISTS ${prettifySubqueryInBraces(q)}")

      case CollectExpression(q) =>
        noEagerConsumption(s"COLLECT ${prettifySubqueryInBraces(q)}")

      case CountExpression(q) =>
        noEagerConsumption(s"COUNT ${prettifySubqueryInBraces(q)}")

      case UnaryAdd(r) =>
        val (i, eagerConsumption) = inner(ast, shouldBacktickEmpty)(r)
        (s"+$i", eagerConsumption)

      case UnarySubtract(r) =>
        val (i, eagerConsumption) = inner(ast, shouldBacktickEmpty)(r)
        (s"-$i", eagerConsumption)

      case CoerceTo(expr, _) =>
        // noinspection RedundantDefaultArgument
        stringify(expr, shouldBacktickEmpty, isCaseExpression = false)

      case CoerceToPredicate(expr) =>
        val inner = apply(expr, shouldBacktickEmpty)
        noEagerConsumption(s"CoerceToPredicate($inner)")

      case AssertIsNode(argument) =>
        noEagerConsumption(s"assertIsNode(${apply(argument, shouldBacktickEmpty)})")

      case e @ ElementIdToLongId(_, _, elementIdExpr) =>
        val prefix = e match {
          case ElementIdToLongId(NODE_TYPE, ElementIdToLongId.Mode.Single, _) =>
            "elementIdToNodeId"
          case ElementIdToLongId(NODE_TYPE, ElementIdToLongId.Mode.Many, _) =>
            "elementIdListToNodeIdList"
          case ElementIdToLongId(RELATIONSHIP_TYPE, ElementIdToLongId.Mode.Single, _) =>
            "elementIdToRelationshipId"
          case ElementIdToLongId(RELATIONSHIP_TYPE, ElementIdToLongId.Mode.Many, _) =>
            "elementIdListToRelationshipIdList"
        }
        noEagerConsumption(s"$prefix(${apply(elementIdExpr, shouldBacktickEmpty)})")

      case NoneOfRelationships(rel, relList) =>
        noEagerConsumption(s"NOT ${apply(rel, shouldBacktickEmpty)} IN ${apply(relList, shouldBacktickEmpty)}")

      case DifferentRelationships(rel1, rel2) =>
        noEagerConsumption(s"NOT ${apply(rel1, shouldBacktickEmpty)} = ${apply(rel2, shouldBacktickEmpty)}")

      case Disjoint(rel1, rel2) =>
        noEagerConsumption(s"disjoint(${apply(rel1, shouldBacktickEmpty)}, ${apply(rel2, shouldBacktickEmpty)})")

      case Unique(rel) =>
        noEagerConsumption(s"unique(${apply(rel, shouldBacktickEmpty)})")

      case VarLengthLowerBound(relName, bound) =>
        noEagerConsumption(s"size(${apply(relName, shouldBacktickEmpty)}) >= $bound")
      case VarLengthUpperBound(relName, bound) =>
        noEagerConsumption(s"size(${apply(relName, shouldBacktickEmpty)}) <= $bound")

      case IsRepeatTrailUnique(argument) =>
        noEagerConsumption(s"isRepeatTrailUnique(${apply(argument, shouldBacktickEmpty)})")

      case _ =>
        noEagerConsumption(extensionStringifier(this)(ast))
    }
  }

  private def prettyScope(s: FilterScope, expression: Expression, shouldBacktickEmpty: Boolean) = {
    Seq(
      for {
        i <- Seq(
          apply(s.variable, shouldBacktickEmpty),
          "IN",
          delimitedInner(s, shouldBacktickEmpty)(expression)
        )
      } yield i,
      for {
        p <- s.innerPredicate.toSeq; i <- Seq("WHERE", delimitedInner(s, shouldBacktickEmpty)(p))
      } yield i
    ).flatten.mkString("(", " ", ")")
  }

  sealed private trait Binding
  private case object Syntactic extends Binding
  private case class Precedence(level: Int) extends Binding

  private def binding(in: Expression): Binding = in match {
    case _: Or |
      _: Ors =>
      Precedence(12)

    case _: Xor =>
      Precedence(11)

    case _: And |
      _: Ands =>
      Precedence(10)

    case _: Not =>
      Precedence(9)

    case _: Equals |
      _: NotEquals |
      _: InvalidNotEquals |
      _: GreaterThan |
      _: GreaterThanOrEqual |
      _: LessThan |
      _: LessThanOrEqual =>
      Precedence(8)

    case _: RegexMatch |
      _: In |
      _: StartsWith |
      _: EndsWith |
      _: Contains |
      _: IsNull |
      _: IsNotNull |
      _: IsTyped |
      _: IsNotTyped |
      _: IsNormalized |
      _: IsNotNormalized =>
      Precedence(7)

    case h: HasLabels if !h.isPostfix =>
      Precedence(7)
    case h: HasLabels if h.isPostfix =>
      Precedence(2)

    case l: LabelExpressionPredicate if !l.isPostfix =>
      Precedence(7)
    case l: LabelExpressionPredicate if l.isPostfix =>
      Precedence(2)

    case _: Add |
      _: Subtract |
      _: Concatenate =>
      Precedence(6)

    case _: Multiply |
      _: Divide |
      _: Modulo =>
      Precedence(5)

    case _: Pow =>
      Precedence(4)

    case _: UnaryAdd |
      _: UnarySubtract =>
      Precedence(3)

    case _: Property |
      _: ContainerIndex |
      _: ListSlice =>
      Precedence(2)

    case _ =>
      Syntactic

  }

  override def backtick(txt: String): String = {
    Stringifier.backtick(txt, alwaysBacktick)
  }

  override def backtick(txt: String, shouldBacktickEmpty: Boolean): String = {
    Stringifier.backtick(txt, alwaysBacktick, false, shouldBacktickEmpty)
  }

  override def quote(txt: String): String = {
    val str = txt.replaceAll("\\\\", "\\\\\\\\")
    val containsSingle = str.contains('\'')
    val containsDouble = str.contains('"')
    if (containsDouble && containsSingle)
      "\"" + str.replaceAll("\"", "\\\\\"") + "\""
    else if (containsDouble || preferSingleQuotes)
      "'" + str + "'"
    else
      "\"" + str + "\""
  }

  override def escapePassword(password: Expression): String = password match {
    case _: SensitiveAutoParameter if !sensitiveParamsAsParams => "'******'"
    case _: SensitiveLiteral                                   => "'******'"
    case param: Parameter                                      => s"$$${Stringifier.backtickEmpty(param.name)}"
    case _                                                     => throw new InternalError("illegal password expression")
  }

  // TODO: pass the shouldBacktickEmpty along here as well
  override def stringifyLabelExpression(labelExpression: LabelExpression): String = labelExpression match {
    case le: Disjunctions =>
      le.children.map(stringifyLabelExpressionHalfAtom).mkString("|")
    case le: ColonDisjunction =>
      s"${stringifyLabelExpressionInColonDisjunction(le.lhs)}|:${stringifyLabelExpressionHalfAtom(le.rhs)}"
    case le: Conjunctions =>
      le.children.map(stringifyLabelExpressionHalfAtom).mkString("&")
    case le: ColonConjunction =>
      s"${stringifyLabelExpressionInColonConjunction(le.lhs)}:${stringifyLabelExpressionHalfAtom(le.rhs)}"
    case le => s"${stringifyLabelExpressionHalfAtom(le)}"
  }

  private def stringifyLabelExpressionInColonDisjunction(labelExpression: LabelExpression): String =
    labelExpression match {
      case le: ColonDisjunction =>
        s"${stringifyLabelExpressionInColonDisjunction(le.lhs)}|:${stringifyLabelExpressionHalfAtom(le.rhs)}"
      case le => s"${stringifyLabelExpressionHalfAtom(le)}"
    }

  private def stringifyLabelExpressionInColonConjunction(labelExpression: LabelExpression): String =
    labelExpression match {
      case le: ColonConjunction =>
        s"${stringifyLabelExpressionInColonConjunction(le.lhs)}:${stringifyLabelExpressionHalfAtom(le.rhs)}"
      case le => s"${stringifyLabelExpressionHalfAtom(le)}"
    }

  private def stringifyLabelExpressionHalfAtom(labelExpression: LabelExpression): String = labelExpression match {
    case le: Negation => s"!${stringifyLabelExpressionHalfAtom(le.e)}"
    case le           => s"${stringifyLabelExpressionAtom(le)}"
  }

  private def stringifyLabelExpressionAtom(labelExpression: LabelExpression): String = labelExpression match {
    case Leaf(name, _)                                                    => apply(name)
    case DynamicLeaf(l: LabelExpressionDynamicLeafExpression, _) if l.all => s"$$all(${apply(l.expression)})"
    case DynamicLeaf(l: LabelExpressionDynamicLeafExpression, _)          => s"$$any(${apply(l.expression)})"
    case _: Wildcard                                                      => s"%"
    case le                                                               => s"(${stringifyLabelExpression(le)})"
  }
}

object ExpressionStringifier {

  def apply(
    extensionStringifier: ExpressionStringifier.Extension,
    alwaysParens: Boolean,
    alwaysBacktick: Boolean,
    preferSingleQuotes: Boolean,
    sensitiveParamsAsParams: Boolean,
    javaCompatible: Boolean
  ): ExpressionStringifier = new DefaultExpressionStringifier(
    extensionStringifier,
    alwaysParens,
    alwaysBacktick,
    preferSingleQuotes,
    sensitiveParamsAsParams,
    javaCompatible
  )

  def apply(
    extender: Expression => String = failingExtender,
    alwaysParens: Boolean = false,
    alwaysBacktick: Boolean = false,
    preferSingleQuotes: Boolean = false,
    sensitiveParamsAsParams: Boolean = false
  ): ExpressionStringifier = new DefaultExpressionStringifier(
    Extension.simple(extender),
    alwaysParens,
    alwaysBacktick,
    preferSingleQuotes,
    sensitiveParamsAsParams,
    false
  )

  /**
   * Generates pretty strings from expressions.
   */
  def pretty(onFailure: Expression => String): ExpressionStringifier = {
    new PrettyExpressionStringifier(ExpressionStringifier(onFailure))
  }

  trait Extension {
    def apply(ctx: ExpressionStringifier)(expression: Expression): String
  }

  object Extension {

    def simple(func: Expression => String): Extension = new Extension {
      def apply(ctx: ExpressionStringifier)(expression: Expression): String = func(expression)
    }
  }

  val failingExtender: Expression => String =
    e => throw new IllegalStateException(s"failed to pretty print $e")
}
