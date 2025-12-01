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
package org.neo4j.cypher.internal.util.symbols

import org.neo4j.cypher.internal.util.InputPosition

case class VectorType(
  innerType: Option[CypherType],
  dimension: Option[Long],
  isNullable: Boolean
)(val position: InputPosition) extends CypherType {

  val parentType: CypherType = CTAny

  override val (toCypherTypeString, toClassString): (String, String) = (innerType, dimension) match {
    case (Some(t), Some(d)) => (s"VECTOR<${t.description}>($d)", s"Vector<$t>($d)")
    case (Some(t), None)    => (s"VECTOR<${t.description}>", s"Vector<$t>")
    case (None, Some(d))    => (s"VECTOR($d)", s"Vector($d)")
    case _                  => ("VECTOR", "Vector")
  }

  override def hasCypherParserSupport: Boolean = true

  def withDimension(dimension: Long): CypherType = this.copy(dimension = Some(dimension))(position)

  // An inner type should never be nullable
  override def simplify: CypherType = {
    innerType match {
      case Some(value) if value.isNullable => copy(Some(value.withIsNullable(false)))(position)
      case _                               => this
    }
  }

  override def normalizedCypherTypeString(): String = {
    val normalizedType = CypherType.normalizeTypes(this)
    if (normalizedType.isNullable) normalizedType.toCypherTypeString
    else s"${normalizedType.toCypherTypeString} NOT NULL"
  }
  override def sortOrder: Int = CypherTypeOrder.VECTOR.id

  override def couldBeStoredInProperty: Boolean = innerType match {
    case Some(value) => value.couldBeStoredInProperty
    case _           => false
  }

  override def isSubtypeOf(otherCypherType: CypherType): Boolean = {
    otherCypherType match {
      // A vector type is a subtype of any as long as the nullability matches
      // (e.g null is not a subtype of ANY NOT NULL)
      case _: AnyType => isNullableSubtypeOf(this, otherCypherType)
      // A vector type os a subtype of a closed dynamic union if it contains this type or is a subtype of
      // one of the inner types.
      case otherDynamicUnion: ClosedDynamicUnionType =>
        otherDynamicUnion.innerTypes.exists(innerType =>
          this.isSubtypeOf(innerType) && isNullableSubtypeOf(this, innerType)
        )
      // Vector supertype effectively: VECTOR<FLOAT64>(1234) IS :: VECTOR
      case _ @VectorType(None, None, _) => isNullableSubtypeOf(
          this,
          otherCypherType
        )
      // Vectors of matching type, dimension not defined: VECTOR<FLOAT64>(1234) IS :: VECTOR<FLOAT64>
      // We use toCypherTypeString because the nullability doesn't matter (The inner types are always NOT NULL)
      case _ @VectorType(Some(otherInnerType), None, _) if innerType.isDefined =>
        otherInnerType.toCypherTypeString == innerType.get.toCypherTypeString && isNullableSubtypeOf(
          this,
          otherCypherType
        )
      // Vectors of matching dimension, type not defined: VECTOR<FLOAT64>(1234) IS :: VECTOR(1234)
      // We use toCypherTypeString because the nullability doesn't matter (The inner types are always NOT NULL)
      case _ @VectorType(None, Some(otherDimension), _) if dimension.isDefined =>
        otherDimension == dimension.get && isNullableSubtypeOf(
          this,
          otherCypherType
        )
      // Vectors of matching dimension and type not defined: VECTOR<FLOAT64>(1234) IS :: VECTOR<FLOAT64>(1234)
      case _ @VectorType(Some(otherInnerType), Some(otherDimension), _) if innerType.isDefined && dimension.isDefined =>
        otherInnerType.toCypherTypeString == innerType.get.toCypherTypeString && otherDimension == dimension.get && isNullableSubtypeOf(
          this,
          otherCypherType
        )
      case _ => false
    }
  }

  override def withIsNullable(isNullable: Boolean): CypherType = this.copy(isNullable = isNullable)(position)

  def withPosition(newPosition: InputPosition): CypherType = this.copy()(position = newPosition)

  override def rewrite(f: CypherType => CypherType): CypherType = innerType match {
    case Some(value) => f(copy(Some(value.rewrite(f)))(position))
    case _           => this
  }

}
