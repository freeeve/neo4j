/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.physicalplanning.ast.prettifier

import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.prettifier.ExpressionStringifier
import org.neo4j.cypher.internal.expressions.NODE_TYPE
import org.neo4j.cypher.internal.expressions.SemanticDirection.BOTH
import org.neo4j.cypher.internal.expressions.SemanticDirection.INCOMING
import org.neo4j.cypher.internal.expressions.SemanticDirection.OUTGOING
import org.neo4j.cypher.internal.physicalplanning.ast.GetDegreePrimitive
import org.neo4j.cypher.internal.physicalplanning.ast.HasALabelFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.HasAnyLabelFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.HasDegreeGreaterThanOrEqualPrimitive
import org.neo4j.cypher.internal.physicalplanning.ast.HasDegreeGreaterThanPrimitive
import org.neo4j.cypher.internal.physicalplanning.ast.HasDegreeLessThanOrEqualPrimitive
import org.neo4j.cypher.internal.physicalplanning.ast.HasDegreeLessThanPrimitive
import org.neo4j.cypher.internal.physicalplanning.ast.HasDegreePrimitive
import org.neo4j.cypher.internal.physicalplanning.ast.HasLabelsFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.HasTypesFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.IdFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.IsEmpty
import org.neo4j.cypher.internal.physicalplanning.ast.IsPrimitiveNull
import org.neo4j.cypher.internal.physicalplanning.ast.LabelsFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.NodeElementIdFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.NodeProperty
import org.neo4j.cypher.internal.physicalplanning.ast.NodePropertyExists
import org.neo4j.cypher.internal.physicalplanning.ast.NodePropertyExistsLate
import org.neo4j.cypher.internal.physicalplanning.ast.NodePropertyLate
import org.neo4j.cypher.internal.physicalplanning.ast.NonEmpty
import org.neo4j.cypher.internal.physicalplanning.ast.NullCheck
import org.neo4j.cypher.internal.physicalplanning.ast.NullCheckProperty
import org.neo4j.cypher.internal.physicalplanning.ast.NullCheckReferenceProperty
import org.neo4j.cypher.internal.physicalplanning.ast.PrimitiveAnds
import org.neo4j.cypher.internal.physicalplanning.ast.PrimitiveEquals
import org.neo4j.cypher.internal.physicalplanning.ast.PrimitiveNotEquals
import org.neo4j.cypher.internal.physicalplanning.ast.RelationshipElementIdFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.RelationshipProperty
import org.neo4j.cypher.internal.physicalplanning.ast.RelationshipPropertyExists
import org.neo4j.cypher.internal.physicalplanning.ast.RelationshipPropertyExistsLate
import org.neo4j.cypher.internal.physicalplanning.ast.RelationshipPropertyLate
import org.neo4j.cypher.internal.physicalplanning.ast.RelationshipTypeFromSlot
import org.neo4j.cypher.internal.physicalplanning.ast.SlottedCachedHasPropertyWithPropertyToken
import org.neo4j.cypher.internal.physicalplanning.ast.SlottedCachedHasPropertyWithoutPropertyToken
import org.neo4j.cypher.internal.physicalplanning.ast.SlottedCachedPropertyWithPropertyToken
import org.neo4j.cypher.internal.physicalplanning.ast.SlottedCachedPropertyWithoutPropertyToken
import org.neo4j.cypher.internal.runtime.ast.DefaultValueLiteral
import org.neo4j.cypher.internal.runtime.ast.MakeTraversable
import org.neo4j.cypher.internal.runtime.ast.ParameterFromSlot
import org.neo4j.cypher.internal.runtime.ast.PropertiesUsingCachedProperties
import org.neo4j.cypher.internal.runtime.ast.RuntimeConstant
import org.neo4j.cypher.internal.runtime.ast.RuntimeExpression
import org.neo4j.cypher.internal.runtime.ast.RuntimeProperty
import org.neo4j.cypher.internal.runtime.ast.TraversalEndpoint
import org.neo4j.cypher.internal.runtime.ast.TraversalEndpoint.Endpoint.To
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.symbols.IntegerType
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.values.storable.Values
import org.reflections.Reflections
import org.scalatest.prop.TableDrivenPropertyChecks

import java.lang.reflect.Modifier

import scala.jdk.CollectionConverters.CollectionHasAsScala

class RuntimeExpressionStringifierTest extends CypherFunSuite with AstConstructionTestSupport
    with TableDrivenPropertyChecks {

  private val ctx = ExpressionStringifier.apply()
  private val stringifier = RuntimeExpressionStringifier

  private val supportedRuntimeExpressions = Table(
    ("runtime expression", "stringified"),
    (ParameterFromSlot(1, "p", IntegerType(isNullable = false)(InputPosition.NONE)), "$p"),
    (
      SlottedCachedPropertyWithPropertyToken(
        "x",
        propName("prop"),
        1,
        offsetIsForLongSlot = true,
        2,
        3,
        NODE_TYPE,
        nullable = false,
        needsValue = true
      ).asInstanceOf[RuntimeExpression],
      "x.prop"
    ),
    (SlottedCachedPropertyWithoutPropertyToken("x", propName("prop"), 1, true, "prop", 1, NODE_TYPE, false), "x.prop"),
    (SlottedCachedHasPropertyWithPropertyToken("x", propName("prop"), 0, true, 1, 2, NODE_TYPE, false), "x.prop"),
    (
      SlottedCachedHasPropertyWithoutPropertyToken("x", propName("prop"), 0, true, "prop", 1, NODE_TYPE, false),
      "x.prop"
    )
  )

  private val unsupportedExpressions = Table(
    "expressions",
    RelationshipTypeFromSlot(1),
    PropertiesUsingCachedProperties(varFor("x"), Set(cachedNodeProp("x", "prop"))),
    RelationshipElementIdFromSlot(0),
    NodeElementIdFromSlot(0),
    HasDegreeLessThanPrimitive(0, None, OUTGOING, literalInt(1)),
    HasDegreeLessThanOrEqualPrimitive(0, None, OUTGOING, literalInt(1)),
    HasDegreePrimitive(0, None, OUTGOING, literalInt(1)),
    HasTypesFromSlot(0, Seq(1), Seq()),
    HasDegreeGreaterThanOrEqualPrimitive(0, None, OUTGOING, literalInt(1)),
    PrimitiveEquals(0, 1),
    PrimitiveNotEquals(0, 1),
    PrimitiveAnds(Seq(PrimitiveEquals(0, 1), PrimitiveNotEquals(0, 1))),
    IsPrimitiveNull(1),
    HasAnyLabelFromSlot(1, Seq(1), Seq()),
    HasALabelFromSlot(1),
    HasDegreeGreaterThanPrimitive(1, None, INCOMING, literalInt(1)),
    HasLabelsFromSlot(1, Seq(1, 2), Seq("L")),
    NullCheck(1, varFor("x")),
    IdFromSlot(1),
    DefaultValueLiteral(Values.intValue(1)),
    GetDegreePrimitive(1, None, BOTH),
    LabelsFromSlot(1),
    RuntimeConstant(varFor("x"), literalString("y")),
    MakeTraversable(varFor("x")),
    TraversalEndpoint(varFor("x"), To),
    NonEmpty,
    IsEmpty
  )

  private val supportedRuntimeProperties = Table(
    ("runtime property", "stringified"),
    (NodeProperty(0, 1, "x.prop")(prop("x", "prop", InputPosition.NONE)), "x.prop"),
    (NodePropertyLate(0, "prop", "x.prop")(prop("x", "prop", InputPosition.NONE)), "x.prop"),
    (NodePropertyExists(0, 1, "x.prop")(prop("x", "prop", InputPosition.NONE)), "x.prop IS NOT NULL"),
    (NodePropertyExistsLate(0, "prop", "x.prop")(prop("x", "prop", InputPosition.NONE)), "x.prop IS NOT NULL"),
    (RelationshipProperty(0, 1, "x.prop")(prop("x", "prop", InputPosition.NONE)), "x.prop"),
    (RelationshipPropertyLate(0, "prop", "x.prop")(prop("x", "prop", InputPosition.NONE)), "x.prop"),
    (RelationshipPropertyExists(0, 1, "x.prop")(prop("x", "prop", InputPosition.NONE)), "x.prop IS NOT NULL"),
    (RelationshipPropertyExistsLate(0, "prop", "x.prop")(prop("x", "prop", InputPosition.NONE)), "x.prop IS NOT NULL")
  )

  private val unsupportedRuntimeProperties = Table(
    "runtime property",
    NullCheckProperty(0, prop("x", "prop", InputPosition.NONE)),
    NullCheckReferenceProperty(0, cachedNodeProp("n", "prop"))
  )

  test("should stringify runtime expression") {
    forEvery(supportedRuntimeExpressions) { (expression, stringified) =>
      stringifier(ctx)(expression) shouldBe stringified
    }
  }

  test("should throw for unsupported expressions") {
    forEvery(unsupportedExpressions) { expression =>
      assertThrows[UnsupportedOperationException](stringifier(ctx)(expression))
    }
  }

  test("should deduplicate generated names") {
    val generatedVariables = Table(
      ("expression", "stringified"),
      (
        SlottedCachedPropertyWithPropertyToken(
          "  x@10",
          propName("prop"),
          0,
          true,
          1,
          2,
          NODE_TYPE,
          false
        ),
        "x.prop"
      ),
      (NodeProperty(0, 1, "  x@10.prop")(prop("  x@10", "prop", InputPosition.NONE)), "x.prop"),
      (NodePropertyExists(0, 1, "  x@10.prop")(prop("  x@10", "prop", InputPosition.NONE)), "x.prop IS NOT NULL")
    )

    forEvery(generatedVariables) { (expression, stringified) =>
      stringifier(ctx)(expression) shouldBe stringified
    }

  }

  test("should backtick generated parameter names") {
    stringifier(ctx)(ParameterFromSlot(0, " p0", IntegerType(isNullable = false)(InputPosition.NONE))) shouldBe "$` p0`"
  }

  test("should test all runtime expressions") {
    val reflections = new Reflections("org.neo4j.cypher")

    val allRuntimeExpressions: Set[Class[_ <: RuntimeExpression]] =
      reflections.getSubTypesOf[RuntimeExpression](classOf[RuntimeExpression]).asScala
        .filter(planClass => !Modifier.isAbstract(planClass.getModifiers)).toSet

    val testedClasses: Set[Class[_ <: RuntimeExpression]] =
      supportedRuntimeExpressions.map(_._1.getClass).toSet ++ unsupportedExpressions.map(_.getClass).toSet

    withClue("tests missing for these runtime expressions: ") {
      allRuntimeExpressions should not be empty
      allRuntimeExpressions -- testedClasses should be(empty)
    }
  }

  test("should stringify runtime properties") {
    forEvery(supportedRuntimeProperties) { (expression, stringified) =>
      stringifier(ctx)(expression) shouldBe stringified
    }
  }

  test("should throw for unsupported runtime properties") {
    forEvery(unsupportedRuntimeProperties) { expression =>
      assertThrows[UnsupportedOperationException](stringifier(ctx)(expression))
    }
  }

  test("should test all RuntimeProperties") {
    val reflections = new Reflections("org.neo4j.cypher")

    val allRuntimeProperties: Set[Class[_ <: RuntimeProperty]] =
      reflections.getSubTypesOf[RuntimeProperty](classOf[RuntimeProperty]).asScala
        .filter(planClass => !Modifier.isAbstract(planClass.getModifiers)).toSet

    val testedClasses: Set[Class[_ <: RuntimeProperty]] =
      supportedRuntimeProperties.map(_._1.getClass).toSet ++ unsupportedRuntimeProperties.map(_.getClass).toSet

    withClue("tests missing for these runtime expressions: ") {
      allRuntimeProperties should not be empty
      allRuntimeProperties -- testedClasses should be(empty)
    }
  }
}
