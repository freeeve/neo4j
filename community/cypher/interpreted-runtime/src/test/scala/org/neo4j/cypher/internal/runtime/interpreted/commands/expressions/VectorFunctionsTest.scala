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
package org.neo4j.cypher.internal.runtime.interpreted.commands.expressions

import org.neo4j.cypher.internal.runtime.CypherRow
import org.neo4j.cypher.internal.runtime.interpreted.QueryStateHelper
import org.neo4j.cypher.internal.runtime.interpreted.commands.LiteralHelper.literal
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite
import org.neo4j.exceptions.CypherTypeException
import org.neo4j.exceptions.InvalidArgumentException
import org.neo4j.values.storable.Values
import org.neo4j.values.storable.Values.stringValue

class VectorFunctionsTest extends CypherFunSuite {

  private val expectedNull = Values.NO_VALUE

  test("vectorValueConstructor") {
    def vectorValueConstructorWithDimensionAndCoordinateType(x: Any, y: Any, z: Any) =
      VectorValueConstructorFunction(literal(x), literal(y), literal(z))(
        CypherRow.empty,
        QueryStateHelper.empty
      )

    // TODO: ADD cases checking the actual use cases

    // Dimension size handling
    intercept[InvalidArgumentException](vectorValueConstructorWithDimensionAndCoordinateType(
      Seq(1, 2, 3, 4),
      -1,
      stringValue("FLOAT32")
    ))
    intercept[InvalidArgumentException](vectorValueConstructorWithDimensionAndCoordinateType(
      Seq(1, 2, 3, 4),
      4097,
      stringValue("FLOAT32")
    ))

    // Null handling
    vectorValueConstructorWithDimensionAndCoordinateType(null, null, null) should equal(expectedNull)
    vectorValueConstructorWithDimensionAndCoordinateType(null, 1, null) should equal(expectedNull)
    vectorValueConstructorWithDimensionAndCoordinateType(null, null, stringValue("FLOAT32")) should equal(expectedNull)
    vectorValueConstructorWithDimensionAndCoordinateType(Seq(1, 2, 3, 4), null, null) should equal(expectedNull)
    vectorValueConstructorWithDimensionAndCoordinateType(
      Seq(1, 2, 3, 4),
      null,
      stringValue("FLOAT32")
    ) should equal(expectedNull)
    vectorValueConstructorWithDimensionAndCoordinateType(Seq(1, 2, 3, 4), 1, null) should equal(expectedNull)

    // Type Exceptions
    intercept[CypherTypeException](vectorValueConstructorWithDimensionAndCoordinateType(
      Seq(1, 2, 3, 4),
      "1",
      stringValue("FLOAT32")
    ))
    intercept[CypherTypeException](vectorValueConstructorWithDimensionAndCoordinateType(
      1042,
      1,
      stringValue("FLOAT32")
    ))
  }

}
