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
package org.neo4j.cypher.internal.rewriting

import org.neo4j.cypher.internal.CypherVersion
import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.CreateConstraint
import org.neo4j.cypher.internal.ast.CreateFulltextIndex
import org.neo4j.cypher.internal.ast.CreateIndex
import org.neo4j.cypher.internal.ast.CreateLookupIndex
import org.neo4j.cypher.internal.ast.CreateSingleLabelPropertyIndex
import org.neo4j.cypher.internal.ast.DropConstraintOnName
import org.neo4j.cypher.internal.ast.DropIndexOnName
import org.neo4j.cypher.internal.ast.IsTyped
import org.neo4j.cypher.internal.ast.NodeKey
import org.neo4j.cypher.internal.ast.NodePropertyExistence
import org.neo4j.cypher.internal.ast.NodePropertyUniqueness
import org.neo4j.cypher.internal.ast.PointCreateIndex
import org.neo4j.cypher.internal.ast.RangeCreateIndex
import org.neo4j.cypher.internal.ast.RelationshipKey
import org.neo4j.cypher.internal.ast.RelationshipPropertyExistence
import org.neo4j.cypher.internal.ast.RelationshipPropertyUniqueness
import org.neo4j.cypher.internal.ast.SetExactPropertiesFromMapItem
import org.neo4j.cypher.internal.ast.SetIncludingPropertiesFromMapItem
import org.neo4j.cypher.internal.ast.ShowTransactionsClause
import org.neo4j.cypher.internal.ast.Statement
import org.neo4j.cypher.internal.ast.TextCreateIndex
import org.neo4j.cypher.internal.ast.VectorCreateIndex
import org.neo4j.cypher.internal.expressions.LabelName
import org.neo4j.cypher.internal.expressions.RelTypeName
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.label_expressions.LabelExpressionPredicate
import org.neo4j.cypher.internal.parser.AstParserFactory
import org.neo4j.cypher.internal.util.CypherExceptionFactory
import org.neo4j.cypher.internal.util.Rewriter
import org.neo4j.cypher.internal.util.bottomUp

trait AstRewritingTestSupport extends AstConstructionTestSupport {

  def parse(query: String, exceptionFactory: CypherExceptionFactory): Statement = {
    val defaultStatement = rewriteASTDifferences(parse(CypherVersion.Default, query, exceptionFactory))

    // Quick and dirty hack to try to make sure we have sufficient coverage of all cypher versions.
    // Feel free to improve ¯\_(ツ)_/¯.
    CypherVersion.values().foreach { version =>
      if (version != CypherVersion.Default) {
        val otherStatement = rewriteASTDifferences(parse(version, query, exceptionFactory))
        if (otherStatement != defaultStatement) {
          throw new AssertionError(
            s"""Query parse differently in $version
               |Default statement: $defaultStatement
               |$version statement: $otherStatement
               |""".stripMargin
          )
        }
      }
    }
    defaultStatement
  }

  def parse(version: CypherVersion, query: String, exceptionFactory: CypherExceptionFactory): Statement = {
    AstParserFactory(version)(query, exceptionFactory, None).singleStatement()
  }

  /**
   * There are some AST changes done at the parser level for semantic analysis that won't affect the plan.
   * This rewriter can be expanded to update those parts.
   */
  def rewriteASTDifferences(statement: Statement): Statement = {
    statement.endoRewrite(bottomUp(Rewriter.lift {
      case u: SetExactPropertiesFromMapItem     => u.copy(rhsMustBeMap = false)(u.position)
      case u: SetIncludingPropertiesFromMapItem => u.copy(rhsMustBeMap = false)(u.position)
      case c @ CreateConstraint(variable, labelName: LabelName, properties, name, _: NodeKey, ifExistsDo, options) =>
        // Create constraint is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateConstraint.createNodeKeyConstraint(
          variable,
          labelName,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false,
          c.useGraph
        )(c.position)
      case c @ CreateConstraint(
          variable,
          relTypeName: RelTypeName,
          properties,
          name,
          _: RelationshipKey,
          ifExistsDo,
          options
        ) =>
        // Create constraint is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateConstraint.createRelationshipKeyConstraint(
          variable,
          relTypeName,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false,
          c.useGraph
        )(c.position)
      case c @ CreateConstraint(
          variable,
          labelName: LabelName,
          properties,
          name,
          _: NodePropertyUniqueness,
          ifExistsDo,
          options
        ) =>
        // Create constraint is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateConstraint.createNodePropertyUniquenessConstraint(
          variable,
          labelName,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false,
          c.useGraph
        )(c.position)
      case c @ CreateConstraint(
          variable,
          relTypeName: RelTypeName,
          properties,
          name,
          _: RelationshipPropertyUniqueness,
          ifExistsDo,
          options
        ) =>
        // Create constraint is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateConstraint.createRelationshipPropertyUniquenessConstraint(
          variable,
          relTypeName,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false,
          c.useGraph
        )(c.position)
      case c @ CreateConstraint(
          variable,
          labelName: LabelName,
          properties,
          name,
          NodePropertyExistence,
          ifExistsDo,
          options
        ) =>
        // Create constraint is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateConstraint.createNodePropertyExistenceConstraint(
          variable,
          labelName,
          properties.head,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false,
          c.useGraph
        )(c.position)
      case c @ CreateConstraint(
          variable,
          relTypeName: RelTypeName,
          properties,
          name,
          RelationshipPropertyExistence,
          ifExistsDo,
          options
        ) =>
        // Create constraint is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateConstraint.createRelationshipPropertyExistenceConstraint(
          variable,
          relTypeName,
          properties.head,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false,
          c.useGraph
        )(c.position)

      case c @ CreateSingleLabelPropertyIndex(
          variable,
          e: LabelName,
          properties,
          name,
          PointCreateIndex,
          ifExistsDo,
          options
        ) =>
        // Create index is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateIndex.createPointNodeIndex(
          variable,
          e,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false
        )(c.position)
      case c @ CreateSingleLabelPropertyIndex(
          variable,
          e: RelTypeName,
          properties,
          name,
          PointCreateIndex,
          ifExistsDo,
          options
        ) =>
        // Create index is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateIndex.createPointRelationshipIndex(
          variable,
          e,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false
        )(c.position)
      case c @ CreateSingleLabelPropertyIndex(
          variable,
          e: LabelName,
          properties,
          name,
          _: RangeCreateIndex,
          ifExistsDo,
          options
        ) =>
        // Create index is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateIndex.createRangeNodeIndex(
          variable,
          e,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false,
          fromDefault = true
        )(c.position)
      case c @ CreateSingleLabelPropertyIndex(
          variable,
          e: RelTypeName,
          properties,
          name,
          _: RangeCreateIndex,
          ifExistsDo,
          options
        ) =>
        // Create index is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateIndex.createRangeRelationshipIndex(
          variable,
          e,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false,
          fromDefault = true
        )(c.position)
      case c @ CreateSingleLabelPropertyIndex(
          variable,
          e: LabelName,
          properties,
          name,
          TextCreateIndex,
          ifExistsDo,
          options
        ) =>
        // Create index is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateIndex.createTextNodeIndex(
          variable,
          e,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false
        )(c.position)
      case c @ CreateSingleLabelPropertyIndex(
          variable,
          e: RelTypeName,
          properties,
          name,
          TextCreateIndex,
          ifExistsDo,
          options
        ) =>
        // Create index is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateIndex.createTextRelationshipIndex(
          variable,
          e,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false
        )(c.position)
      case c @ CreateSingleLabelPropertyIndex(
          variable,
          e: LabelName,
          properties,
          name,
          VectorCreateIndex,
          ifExistsDo,
          options
        ) =>
        // Create index is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateIndex.createVectorNodeIndex(
          variable,
          e,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false
        )(c.position)
      case c @ CreateSingleLabelPropertyIndex(
          variable,
          e: RelTypeName,
          properties,
          name,
          VectorCreateIndex,
          ifExistsDo,
          options
        ) =>
        // Create index is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateIndex.createVectorRelationshipIndex(
          variable,
          e,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false
        )(c.position)
      case c @ CreateLookupIndex(
          variable,
          isNodeIndex,
          function,
          name,
          _,
          ifExistsDo,
          options
        ) =>
        // Create index is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateIndex.createLookupIndex(
          variable,
          isNodeIndex,
          function,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false
        )(c.position)
      case c @ CreateFulltextIndex(
          variable,
          Left(labelNames),
          properties,
          name,
          _,
          ifExistsDo,
          options
        ) =>
        // Create index is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateIndex.createFulltextNodeIndex(
          variable,
          labelNames,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false
        )(c.position)

      case c @ CreateFulltextIndex(
          variable,
          Right(relTypeNames),
          properties,
          name,
          _,
          ifExistsDo,
          options
        ) =>
        // Create index is a trait so it doesn't have a copy method
        // and it doesn't have a public implementing class to match instead either
        CreateIndex.createFulltextRelationshipIndex(
          variable,
          relTypeNames,
          properties,
          name,
          ifExistsDo,
          options,
          // let's just update all of them to be version > 5
          fromCypher5 = false
        )(c.position)
      case d: DropConstraintOnName     => d.copy(fromCypher5 = false)(d.position)
      case d: DropIndexOnName          => d.copy(fromCypher5 = false)(d.position)
      case stc: ShowTransactionsClause =>
        // the columns generated by the apply is what differs in versions
        // so we need to call the apply instead of copy the existing one
        // if we don't want to try and update the columns manually
        ShowTransactionsClause(
          stc.names,
          stc.where,
          stc.yieldItems,
          stc.yieldAll,
          stc.yieldWith,
          // let's just update all of them to be version > 5
          returnCypher5Types = false
        )(stc.position)
      case v: Variable if v.isIsolated =>
        // An isolated variable e.g. "`a`", "(a)" is tracked in the AST by the Cypher5 parser.
        // This is rewrite removes the tracking.
        v.copy()(v.position, Variable.isIsolatedDefault)
      case it: IsTyped if it.withDoubleColonOnly =>
        // Type predicates with only a double column e.g. "x :: INT" are tracked in the AST by the Cypher5 parser.
        // This is rewrite removes the difference.
        it.copy()(it.position, IsTyped.withDoubleColonOnlyDefault)
      case lep: LabelExpressionPredicate if lep.isParenthesized =>
        // Label expression predicates that are parenthesized e.g. "(n:L)" are tracked in the AST by the Cypher5 parser.
        // This is rewrite removes the difference.
        lep.copy()(lep.position, LabelExpressionPredicate.isParenthesizedDefault)
    }))
  }
}
