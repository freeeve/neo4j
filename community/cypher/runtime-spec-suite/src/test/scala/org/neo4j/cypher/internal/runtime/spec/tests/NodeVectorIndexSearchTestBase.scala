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
package org.neo4j.cypher.internal.runtime.spec.tests

import org.neo4j.configuration.GraphDatabaseInternalSettings
import org.neo4j.cypher.internal.CypherRuntime
import org.neo4j.cypher.internal.LogicalQuery
import org.neo4j.cypher.internal.RuntimeContext
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.logical.plans.ExclusiveBound
import org.neo4j.cypher.internal.logical.plans.InclusiveBound
import org.neo4j.cypher.internal.logical.plans.InequalitySeekRange
import org.neo4j.cypher.internal.logical.plans.InequalitySeekRangeWrapper
import org.neo4j.cypher.internal.logical.plans.RangeBetween
import org.neo4j.cypher.internal.logical.plans.RangeGreaterThan
import org.neo4j.cypher.internal.logical.plans.RangeLessThan
import org.neo4j.cypher.internal.logical.plans.RangeQueryExpression
import org.neo4j.cypher.internal.logical.plans.SingleQueryExpression
import org.neo4j.cypher.internal.runtime.spec.Edition
import org.neo4j.cypher.internal.runtime.spec.LogicalQueryBuilder
import org.neo4j.cypher.internal.runtime.spec.RuntimeTestSuite
import org.neo4j.cypher.internal.util.NonEmptyList
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.dbms.database.DbmsRuntimeVersion
import org.neo4j.exceptions.CypherTypeException
import org.neo4j.exceptions.InvalidArgumentException
import org.neo4j.graphdb.schema.IndexType
import org.neo4j.internal.kernel.api.exceptions.schema.IndexNotFoundKernelException
import org.neo4j.kernel.KernelVersion
import org.neo4j.values.storable.BooleanValue
import org.neo4j.values.storable.NumberValue
import org.neo4j.values.storable.RandomValues
import org.neo4j.values.storable.Value
import org.neo4j.values.storable.ValueType
import org.neo4j.values.storable.Values
import org.neo4j.values.storable.Values.booleanValue
import org.neo4j.values.storable.Values.byteValue
import org.neo4j.values.storable.Values.doubleValue
import org.neo4j.values.storable.Values.float32Vector
import org.neo4j.values.storable.Values.float64Vector
import org.neo4j.values.storable.Values.floatValue
import org.neo4j.values.storable.Values.int16Vector
import org.neo4j.values.storable.Values.int32Vector
import org.neo4j.values.storable.Values.int64Vector
import org.neo4j.values.storable.Values.int8Vector
import org.neo4j.values.storable.Values.intValue
import org.neo4j.values.storable.Values.longValue
import org.neo4j.values.storable.Values.shortValue
import org.neo4j.values.storable.Values.stringValue
import org.neo4j.values.storable.VectorValue

import scala.math.Ordering.comparatorToOrdering
import scala.util.Random

//noinspection ScalaDeprecation,RedundantDefaultArgument
abstract class NodeVectorIndexSearchTestBase[CONTEXT <: RuntimeContext](
  edition: Edition[CONTEXT],
  runtime: CypherRuntime[CONTEXT],
  sizeHint: Int
) extends RuntimeTestSuite[CONTEXT](
      edition.copyWith(
        additionalConfigs =
          GraphDatabaseInternalSettings.vector_single_stage_filtering_enabled -> java.lang.Boolean.TRUE,
        GraphDatabaseInternalSettings.latest_kernel_version -> java.lang.Byte.valueOf(
          KernelVersion.VERSION_VECTOR_INDEX_SINGLE_STAGE_FILTERING.version
        ),
        GraphDatabaseInternalSettings.latest_runtime_version -> Integer.valueOf(
          DbmsRuntimeVersion.GLORIOUS_FUTURE.getVersion
        )
      ),
      runtime
    ) {

  private val configurations = Seq(
    ("INT64", int64Vector(1L to sizeHint: _*)),
    ("INT32", int32Vector(1 to sizeHint: _*)),
    ("INT16", int16Vector((1 to sizeHint).map(_.toShort): _*)),
    ("INT8", int8Vector((1 to sizeHint).map(_.toByte): _*)),
    ("FLOAT64", float64Vector((1 to sizeHint).map(_.toDouble): _*)),
    ("FLOAT32", float32Vector((1 to sizeHint).map(_.toFloat): _*))
  )

  configurations.foreach {
    case (name, vector) =>
      test(s"$name vector index should index vector values with score variable") {
        givenGraph {
          nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v")
          nodeGraph(1, "Foo").foreach(n => {
            n.setProperty("id", 1)
            n.setProperty("v", vector)
          })
        }

        // when
        val logicalQuery = new LogicalQueryBuilder(this)
          .produceResults("id", "score")
          .projection("n.id AS id")
          .nodeVectorIndexSearch(
            node = "n",
            labelNames = Seq("Foo"),
            properties = Seq("v"),
            indexName = "VectorIndex",
            vector = "$vector",
            limit = "20",
            score = "score"
          )
          .build()

        // then
        val runtimeResult = execute(logicalQuery, runtime, parameters = Map("vector" -> vector))
        runtimeResult should beColumns("id", "score").withRows(matching {
          case Seq(Array(id, score: NumberValue)) if id == longValue(1) && tolerantEquals(1.0, score.doubleValue()) =>
        })
      }

      test(s"$name vector index should index vector values without score variable") {
        givenGraph {
          nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v")
          nodeGraph(1, "Foo").foreach(n => {
            n.setProperty("id", 1)
            n.setProperty("v", vector)
          })
        }

        // when
        val logicalQuery = new LogicalQueryBuilder(this)
          .produceResults("id")
          .projection("n.id AS id")
          .nodeVectorIndexSearch(
            node = "n",
            labelNames = Seq("Foo"),
            properties = Seq("v"),
            indexName = "VectorIndex",
            vector = "$vector",
            limit = "20"
          )
          .build()

        // then
        val runtimeResult = execute(logicalQuery, runtime, parameters = Map("vector" -> vector))
        runtimeResult should beColumns("id").withSingleRow(1)
      }

      test(s"$name should be able to search using a list of integers instead of an explicit vector") {
        givenGraph {
          nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v")
          nodeGraph(1, "Foo").foreach(n => {
            n.setProperty("id", 1)
            n.setProperty("v", vector)
          })
        }

        // when
        val logicalQuery = new LogicalQueryBuilder(this)
          .produceResults("id", "score")
          .projection("n.id AS id")
          .nodeVectorIndexSearch(
            node = "n",
            labelNames = Seq("Foo"),
            properties = Seq("v"),
            indexName = "VectorIndex",
            vector = s"${vectorAsCypherList(vector)}",
            limit = "20",
            score = "score"
          ).build()

        val runtimeResult = execute(logicalQuery, runtime, parameters = Map("vector" -> vector))
        runtimeResult should beColumns("id", "score").withRows(matching {
          case Seq(Array(id, score: NumberValue)) if id == longValue(1) && tolerantEquals(1.0, score.doubleValue()) =>
        })
      }
  }

  test(s"should be able to query the index with multiple inputs from a property") {
    val random = new Random()

    givenGraph {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v")
      nodeGraph(sizeHint, "Foo").zipWithIndex.foreach({
        case (n, i) =>
          n.setProperty("id", i)
          n.setProperty("v", float32Vector((1 to sizeHint).map(_ => random.between(0f, 10f)): _*))
      })
    }

    // when
    val limit = 11
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("id", "score")
      .projection("n.id AS id")
      .apply()
      .|.nodeVectorIndexSearch(
        node = "m",
        labelNames = Seq("Foo"),
        properties = Seq("v"),
        indexName = "VectorIndex",
        vector = "n.v",
        limit = s"$limit",
        score = "score",
        argumentIds = Set("n")
      )
      .filter("n.id < 20")
      .nodeByLabelScan("n", "Foo")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("id", "score").withRows(rowCount(20 * limit))
  }

  test("should fail if index doesn't exists") {
    val theVector = float32Vector((1 to sizeHint).map(_.toFloat): _*)
    givenGraph {
      nodeGraph(1, "Foo").zipWithIndex.foreach({
        case (n, i) =>
          n.setProperty("id", i)
          n.setProperty("v", theVector)
      })
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("id", "score")
      .projection("n.id AS id")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo"),
        properties = Seq("v"),
        indexName = "VectorIndex",
        vector = s"${vectorAsCypherList(theVector)}",
        limit = "13",
        score = "score"
      ).build()

    // then
    val error = the[IndexNotFoundKernelException] thrownBy consume(execute(logicalQuery, runtime))
    error.gqlStatus() shouldBe "22N69"
  }

  test("should fail if index isn't a vector index") {
    val theVector = float32Vector((1 to sizeHint).map(_.toFloat): _*)
    givenGraph {
      nodeIndex("VectorIndex", IndexType.RANGE, Seq("Foo"), "v")
      nodeGraph(1, "Foo").zipWithIndex.foreach({
        case (n, i) =>
          n.setProperty("id", i)
          n.setProperty("v", "theVector")
      })
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("id", "score")
      .projection("n.id AS id")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo"),
        properties = Seq("v"),
        indexName = "VectorIndex",
        vector = s"${vectorAsCypherList(theVector)}",
        limit = "13",
        score = "score"
      ).build()

    // then
    the[InvalidArgumentException] thrownBy consume(execute(
      logicalQuery,
      runtime
    )) should have message "22NCG: Expected the index `VectorIndex` to be a vector index but was a range index."
  }

  test("should fail if search item has the wrong type") {
    val theVector = float32Vector((1 to sizeHint).map(_.toFloat): _*)
    givenGraph {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v")
      nodeGraph(1, "Foo").zipWithIndex.foreach({
        case (n, i) =>
          n.setProperty("id", i)
          n.setProperty("v", theVector)
      })
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("id", "score")
      .projection("n.id AS id")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo"),
        properties = Seq("v"),
        indexName = "VectorIndex",
        vector = s"'THIS IS NOT A VECTOR'",
        limit = "13",
        score = "score"
      ).build()

    // then
    val error = the[CypherTypeException] thrownBy consume(execute(
      logicalQuery,
      runtime
    ))
    error.gqlStatus() shouldBe "22G03"
    error.cause().get().gqlStatus() shouldBe "22N01"
  }

  test("should return empty if search item is null") {
    val theVector = float32Vector((1 to sizeHint).map(_.toFloat): _*)
    givenGraph {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v")
      nodeGraph(1, "Foo").zipWithIndex.foreach({
        case (n, i) =>
          n.setProperty("id", i)
          n.setProperty("v", theVector)
      })
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("id", "score")
      .projection("n.id AS id")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo"),
        properties = Seq("v"),
        indexName = "VectorIndex",
        vector = "NULL",
        limit = "13",
        score = "score"
      ).build()

    // then
    val runtimeResult = execute(logicalQuery, runtime)
    runtimeResult should beColumns("id", "score").withNoRows()
  }

  test("should fail if search item is a list containing null") {
    givenGraph {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v")
      nodeGraph(1, "Foo").zipWithIndex.foreach({
        case (n, i) =>
          n.setProperty("id", i)
          n.setProperty("v", int32Vector(1, 2, 3))
      })
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("id", "score")
      .projection("n.id AS id")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo"),
        properties = Seq("v"),
        indexName = "VectorIndex",
        vector = s"[1, NULL, 3]",
        limit = "13",
        score = "score"
      ).build()

    // then
    val error = the[InvalidArgumentException] thrownBy consume(execute(
      logicalQuery,
      runtime
    ))
    error.gqlStatus() shouldBe "22NBG"
  }

  test("should respect the limit") {
    val random = new Random()

    givenGraph {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v")
      nodeGraph(sizeHint, "Foo").zipWithIndex.foreach({
        case (n, i) =>
          n.setProperty("id", i)
          n.setProperty("v", float32Vector((1 to sizeHint).map(_ => random.between(0f, 10f)): _*))
      })
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("id", "score")
      .projection("n.id AS id")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo"),
        properties = Seq("v"),
        indexName = "VectorIndex",
        vector = "$vector",
        limit = "13",
        score = "score"
      ).build()

    val runtimeResult =
      execute(logicalQuery, runtime, parameters = Map("vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*)))
    runtimeResult should beColumns("id", "score").withRows(rowCount(13))
  }

  test("should handle limit 0") {
    val random = new Random()

    givenGraph {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v")
      nodeGraph(sizeHint, "Foo").zipWithIndex.foreach({
        case (n, i) =>
          n.setProperty("id", i)
          n.setProperty("v", float32Vector((1 to sizeHint).map(_ => random.between(0f, 10f)): _*))
      })
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("id", "score")
      .projection("n.id AS id")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo"),
        properties = Seq("v"),
        indexName = "VectorIndex",
        vector = "$vector",
        limit = "0",
        score = "score"
      ).build()

    val runtimeResult =
      execute(logicalQuery, runtime, parameters = Map("vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*)))
    runtimeResult should beColumns("id", "score").withNoRows()
  }

  test("should fail on negative limits") {
    val random = new Random()

    givenGraph {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v")
      nodeGraph(sizeHint, "Foo").zipWithIndex.foreach({
        case (n, i) =>
          n.setProperty("id", i)
          n.setProperty("v", float32Vector((1 to sizeHint).map(_ => random.between(0f, 10f)): _*))
      })
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("id", "score")
      .projection("n.id AS id")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo"),
        properties = Seq("v"),
        indexName = "VectorIndex",
        vector = "$vector",
        limit = "-1",
        score = "score"
      ).build()

    val error = the[InvalidArgumentException] thrownBy consume(execute(
      logicalQuery,
      runtime,
      parameters = Map("vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*))
    ))
    error.gqlStatus() shouldBe "22003"
    error.cause().get().gqlStatus() shouldBe "22N03"
  }

  test("should fail on too large limits") {
    val random = new Random()

    givenGraph {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v")
      nodeGraph(sizeHint, "Foo").zipWithIndex.foreach({
        case (n, i) =>
          n.setProperty("id", i)
          n.setProperty("v", float32Vector((1 to sizeHint).map(_ => random.between(0f, 10f)): _*))
      })
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("id", "score")
      .projection("n.id AS id")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo"),
        properties = Seq("v"),
        indexName = "VectorIndex",
        vector = "$vector",
        limit = "9223372036854775807",
        score = "score"
      ).build()

    val error = the[InvalidArgumentException] thrownBy consume(execute(
      logicalQuery,
      runtime,
      parameters = Map("vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*))
    ))
    error.gqlStatus() shouldBe "22003"
    error.cause().get().gqlStatus() shouldBe "22N03"
  }

  test("should support multiple labels (on same node)") {
    val random = new Random()

    givenGraph {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo", "Bar", "Baz"), "v")
      nodeGraph(sizeHint, "Foo", "Bar", "Baz").zipWithIndex.foreach({
        case (n, i) =>
          n.setProperty("id", i)
          n.setProperty("v", float32Vector((1 to sizeHint).map(_ => random.between(0f, 10f)): _*))
      })
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("labels")
      .projection("labels(n) AS labels")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo", "Bar", "Baz"),
        properties = Seq("v"),
        indexName = "VectorIndex",
        vector = "$vector",
        limit = "13"
      ).build()

    val runtimeResult =
      execute(logicalQuery, runtime, parameters = Map("vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*)))
    runtimeResult should beColumns("labels").withRows(
      Seq.fill(13)(Array(Array("Foo", "Bar", "Baz"))),
      listInAnyOrder = true
    )
  }

  test("should support multiple labels (on different nodes)") {
    val random = new Random()

    givenGraph {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo", "Bar", "Baz"), "v")
      nodeGraph(1, "Foo").foreach(n =>
        n.setProperty("v", float32Vector((1 to sizeHint).map(_ => random.between(0f, 10f)): _*))
      )
      nodeGraph(1, "Bar").foreach(n =>
        n.setProperty("v", float32Vector((1 to sizeHint).map(_ => random.between(0f, 10f)): _*))
      )
      nodeGraph(1, "Baz").foreach(n =>
        n.setProperty("v", float32Vector((1 to sizeHint).map(_ => random.between(0f, 10f)): _*))
      )
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("labels")
      .projection("labels(n) AS labels")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo", "Bar", "Baz"),
        properties = Seq("v"),
        indexName = "VectorIndex",
        vector = "$vector",
        limit = "13"
      ).build()

    val runtimeResult =
      execute(logicalQuery, runtime, parameters = Map("vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*)))
    runtimeResult should beColumns("labels").withRows(Seq(
      Array(Array("Foo")),
      Array(Array("Bar")),
      Array(Array("Baz"))
    ))
  }

  // TODO: arrays and temporals not supported yet
  val ranges: Seq[Int => Value] = Seq(
    longValue(_),
    intValue,
    i => shortValue(i.shortValue()),
    i => byteValue((Byte.MinValue + i).byteValue),
    i => doubleValue(i.doubleValue()),
    i => floatValue(i.floatValue()),
    i => stringValue("A".repeat(i))
  )

  ranges.foreach(range => {
    val random = new Random()
    // we use a size small enough to have unique sequence of byte values
    val size = 256
    def createGraph(): Unit = {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v", "id")
      val write = tx.kernelTransaction().dataWrite
      val vectorToken = tx.kernelTransaction().tokenRead().propertyKey("v")
      val idToken = tx.kernelTransaction().tokenRead().propertyKey("id")
      nodeGraph(size, "Foo").zipWithIndex.foreach({
        case (n, i) =>
          write.nodeSetProperty(n.getId, idToken, range(i))
          write.nodeSetProperty(
            n.getId,
            vectorToken,
            float32Vector((1 to sizeHint).map(_ => random.between(0f, 10f)): _*)
          )
      })
    }

    val indexToSearch = random.nextInt(size)
    val valueToSearch = range(indexToSearch)

    test(s"should support single-stage filtering single exact seek with  n.id = $valueToSearch") {
      // given
      givenGraph(createGraph())

      // when
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("id")
        .projection("n.id AS id")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = "13",
          filter = Some(SingleQueryExpression(param("seekValue")))
        ).build()

      val runtimeResult =
        execute(
          logicalQuery,
          runtime,
          parameters =
            Map(
              "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
              "seekValue" -> valueToSearch
            )
        )

      // then
      runtimeResult should beColumns("id").withSingleRow(valueToSearch)
    }

    test(s"should support single-stage filtering single range seek $valueToSearch <= n.id <= $valueToSearch") {
      // given
      givenGraph(createGraph())

      // when
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("id")
        .projection("n.id AS id")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = "1000000",
          filter = Some(between(gte(param("seekValue")), lte(param("seekValue"))))
        )
        .build()

      val runtimeResult =
        execute(
          logicalQuery,
          runtime,
          parameters =
            Map(
              "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
              "seekValue" -> valueToSearch
            )
        )

      // then
      runtimeResult should beColumns("id").withSingleRow(valueToSearch)
    }

    test(s"should support single-stage filtering single range seek $valueToSearch < n.id <= $valueToSearch") {
      // given
      givenGraph(createGraph())

      // when
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("id")
        .projection("n.id AS id")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = "1000000",
          filter = Some(between(gt(param("seekValue")), lte(param("seekValue"))))
        )
        .build()

      val runtimeResult =
        execute(
          logicalQuery,
          runtime,
          parameters =
            Map(
              "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
              "seekValue" -> valueToSearch
            )
        )

      // then
      runtimeResult should beColumns("id").withNoRows()
    }

    test(s"should support single-stage filtering single range seek $valueToSearch <= n.id < $valueToSearch") {
      // given
      givenGraph(createGraph())

      // when
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("id")
        .projection("n.id AS id")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = "1000000",
          filter = Some(between(gte(param("seekValue")), lt(param("seekValue"))))
        )
        .build()

      val runtimeResult =
        execute(
          logicalQuery,
          runtime,
          parameters =
            Map(
              "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
              "seekValue" -> valueToSearch
            )
        )

      // then
      runtimeResult should beColumns("id").withNoRows()
    }

    test(s"should support single-stage filtering single range seek $valueToSearch < n.id < $valueToSearch") {
      // given
      givenGraph(createGraph())

      // when
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("id")
        .projection("n.id AS id")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = "1000000",
          filter = Some(between(gt(param("seekValue")), lt(param("seekValue"))))
        )
        .build()

      val runtimeResult =
        execute(
          logicalQuery,
          runtime,
          parameters =
            Map(
              "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
              "seekValue" -> valueToSearch
            )
        )

      // then
      runtimeResult should beColumns("id").withNoRows()
    }

    test(s"should support single-stage filtering single range seek ${range(size - 1)} <= n.id <= ${range(0)}") {
      // given
      givenGraph(createGraph())

      // when
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("id")
        .projection("n.id AS id")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = "1000000",
          filter = Some(between(gte(param("min")), lte(param("max"))))
        )
        .build()

      val runtimeResult =
        execute(
          logicalQuery,
          runtime,
          parameters =
            Map(
              "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
              "min" -> range(size - 1),
              "max" -> range(0)
            )
        )

      // then
      runtimeResult should beColumns("id").withNoRows()
    }

    test(s"should support single-stage filtering single range seek ${range(0)} <= n.id < ${range(size - 1)}") {
      // given
      givenGraph(createGraph())

      // when
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("id")
        .projection("n.id AS id")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = "1000000",
          filter = Some(between(gte(param("min")), lt(param("max"))))
        )
        .build()

      val runtimeResult =
        execute(
          logicalQuery,
          runtime,
          parameters =
            Map(
              "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
              "min" -> range(0),
              "max" -> range(size - 1)
            )
        )

      // then
      val expected = (0 until size - 1).map(range)
      runtimeResult should beColumns("id").withRows(singleColumn(expected))
    }

    test(s"should support single-stage filtering single range seek ${range(0)} < n.id <= ${range(size - 1)}") {
      // given
      givenGraph(createGraph())

      // when
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("id")
        .projection("n.id AS id")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = "1000000",
          filter = Some(between(gt(param("min")), lte(param("max"))))
        )
        .build()

      val runtimeResult =
        execute(
          logicalQuery,
          runtime,
          parameters =
            Map(
              "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
              "min" -> range(0),
              "max" -> range(size - 1)
            )
        )

      // then
      val expected = (1 until size).map(range)
      runtimeResult should beColumns("id").withRows(singleColumn(expected))
    }

    test(s"should support single-stage filtering single range seek ${range(0)} <= n.id <= ${range(size - 1)}") {
      // given
      givenGraph(createGraph())

      // when
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("id")
        .projection("n.id AS id")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = "1000000",
          filter = Some(between(gte(param("min")), lte(param("max"))))
        )
        .build()

      val runtimeResult =
        execute(
          logicalQuery,
          runtime,
          parameters =
            Map(
              "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
              "min" -> range(0),
              "max" -> range(size - 1)
            )
        )

      // then
      val expected = (0 until size).map(range)
      runtimeResult should beColumns("id").withRows(singleColumn(expected))
    }

    test(s"should support single-stage filtering single range seek $valueToSearch < n.id") {
      // given
      givenGraph(createGraph())

      // when
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("r")
        .projection("n.id > $seekValue AS r")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = "1000000",
          filter = Some(rangeExpression(gt(param("seekValue"))))
        )
        .build()

      val runtimeResult = {
        execute(
          logicalQuery,
          runtime,
          parameters =
            Map(
              "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
              "seekValue" -> valueToSearch
            )
        )
      }

      // then
      val expectedNumberOfRows = size - indexToSearch - 1
      runtimeResult should beColumns("r").withRows(singleColumn(Seq.fill(expectedNumberOfRows)(true)))
    }

    test(s"should support single-stage filtering single range seek $valueToSearch <= n.id") {
      // given
      givenGraph(createGraph())

      // when
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("r")
        .projection("n.id >= $seekValue AS r")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = "1000000",
          filter = Some(rangeExpression(gte(param("seekValue"))))
        )
        .build()

      val runtimeResult =
        execute(
          logicalQuery,
          runtime,
          parameters =
            Map(
              "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
              "seekValue" -> valueToSearch
            )
        )

      // then
      val expectedNumberOfRows = size - indexToSearch
      runtimeResult should beColumns("r").withRows(singleColumn(Seq.fill(expectedNumberOfRows)(true)))
    }

    test(s"should support single-stage filtering single range seek $valueToSearch > n.id") {
      // given
      givenGraph(createGraph())

      // when
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("r")
        .projection("n.id < $seekValue AS r")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = "1000000",
          filter = Some(rangeExpression(lt(param("seekValue"))))
        )
        .build()

      val runtimeResult =
        execute(
          logicalQuery,
          runtime,
          parameters =
            Map(
              "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
              "seekValue" -> valueToSearch
            )
        )

      // then
      val expectedNumberOfRows = indexToSearch
      runtimeResult should beColumns("r").withRows(singleColumn(Seq.fill(expectedNumberOfRows)(true)))
    }

    test(s"should support single-stage filtering single range seek $valueToSearch >= n.id") {
      // given
      givenGraph(createGraph())

      // when
      val logicalQuery = new LogicalQueryBuilder(this)
        .produceResults("r")
        .projection("n.id <= $seekValue AS r")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = "1000000",
          filter = Some(rangeExpression(lte(param("seekValue"))))
        )
        .build()

      val runtimeResult =
        execute(
          logicalQuery,
          runtime,
          parameters =
            Map(
              "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
              "seekValue" -> valueToSearch
            )
        )

      // then
      val expectedNumberOfRows = indexToSearch + 1
      runtimeResult should beColumns("r").withRows(singleColumn(Seq.fill(expectedNumberOfRows)(true)))
    }
  })

  test("should support single-stage filtering single range seek with different types") {
    val random = new Random()

    givenGraph {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v", "id")
      val write = tx.kernelTransaction().dataWrite
      val vectorToken = tx.kernelTransaction().tokenRead().propertyKey("v")
      val idToken = tx.kernelTransaction().tokenRead().propertyKey("id")
      nodeGraph(sizeHint, "Foo").zipWithIndex.foreach({
        case (n, i) =>
          write.nodeSetProperty(n.getId, idToken, longValue(i))
          write.nodeSetProperty(
            n.getId,
            vectorToken,
            float32Vector((1 to sizeHint).map(_ => random.between(0f, 10f)): _*)
          )
      })
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("id")
      .projection("n.id AS id")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo"),
        properties = Seq("v", "id"),
        indexName = "VectorIndex",
        vector = "$vector",
        limit = "1000000",
        filter = Some(between(gte(param("seekValue1")), lte(param("seekValue2"))))
      )
      .build()

    val runtimeResult =
      execute(
        logicalQuery,
        runtime,
        parameters =
          Map(
            "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
            "seekValue1" -> longValue(0),
            "seekValue2" -> stringValue("10000")
          )
      )
    runtimeResult should beColumns("id").withNoRows()
  }

  test("should support single-stage filtering single range seek between values") {
    val random = new Random()

    givenGraph {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v", "id")
      val write = tx.kernelTransaction().dataWrite
      val vectorToken = tx.kernelTransaction().tokenRead().propertyKey("v")
      val idToken = tx.kernelTransaction().tokenRead().propertyKey("id")
      nodeGraph(sizeHint, "Foo").zipWithIndex.foreach({
        case (n, i) =>
          write.nodeSetProperty(n.getId, idToken, longValue(i))
          write.nodeSetProperty(
            n.getId,
            vectorToken,
            float32Vector((1 to sizeHint).map(_ => random.between(0f, 10f)): _*)
          )
      })
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("id")
      .projection("n.id AS id")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo"),
        properties = Seq("v", "id"),
        indexName = "VectorIndex",
        vector = "$vector",
        limit = "1000000",
        filter = Some(between(gte(param("seekValue1")), lte(param("seekValue2"))))
      )
      .build()

    val runtimeResult =
      execute(
        logicalQuery,
        runtime,
        parameters =
          Map(
            "vector" -> float32Vector(Seq.fill(sizeHint)(5.0f): _*),
            "seekValue1" -> null,
            "seekValue2" -> null
          )
      )
    runtimeResult should beColumns("id").withNoRows()
  }

  test("should support single-stage filtering single range seek of booleans") {
    // given
    givenGraph(booleanVectorGraph(20))

    // [FALSE, TRUE] < n.bool < [FALSE, TRUE]
    executeBooleanPlan(
      between(gt(param("min")), lt(param("max"))),
      min = false,
      max = true
    ) should beColumns("r").withNoRows()
    executeBooleanPlan(
      between(gt(param("min")), lt(param("max"))),
      min = true,
      max = true
    ) should beColumns("r").withNoRows()
    executeBooleanPlan(
      between(gt(param("min")), lt(param("max"))),
      min = true,
      max = false
    ) should beColumns("r").withNoRows()
    executeBooleanPlan(
      between(gt(param("min")), lt(param("max"))),
      min = false,
      max = false
    ) should beColumns("r").withNoRows()
    // [FALSE, TRUE] <= n.bool < [FALSE, TRUE]
    executeBooleanPlan(
      between(gte(param("min")), lt(param("max"))),
      min = false,
      max = true
    ) should beColumns("r").withRows(singleColumn(Seq.fill(10)(false)))
    executeBooleanPlan(
      between(gte(param("min")), lt(param("max"))),
      min = true,
      max = true
    ) should beColumns("r").withNoRows()
    executeBooleanPlan(
      between(gte(param("min")), lt(param("max"))),
      min = true,
      max = false
    ) should beColumns("r").withNoRows()
    executeBooleanPlan(
      between(gte(param("min")), lt(param("max"))),
      min = false,
      max = false
    ) should beColumns("r").withNoRows()
    // [FALSE, TRUE] < n.bool <= [FALSE, TRUE]
    executeBooleanPlan(
      between(gt(param("min")), lte(param("max"))),
      min = false,
      max = true
    ) should beColumns("r").withRows(singleColumn(Seq.fill(10)(true)))
    executeBooleanPlan(
      between(gt(param("min")), lte(param("max"))),
      min = true,
      max = true
    ) should beColumns("r").withNoRows()
    executeBooleanPlan(
      between(gt(param("min")), lte(param("max"))),
      min = true,
      max = false
    ) should beColumns("r").withNoRows()
    executeBooleanPlan(
      between(gt(param("min")), lte(param("max"))),
      min = false,
      max = false
    ) should beColumns("r").withNoRows()
    // [FALSE, TRUE] <= n.bool <= [FALSE, TRUE]
    executeBooleanPlan(
      between(gte(param("min")), lte(param("max"))),
      min = false,
      max = true
    ) should beColumns("r").withRows(singleColumn(Seq.fill(10)(false) ++ Seq.fill(10)(true)))
    executeBooleanPlan(
      between(gte(param("min")), lte(param("max"))),
      min = true,
      max = true
    ) should beColumns("r").withRows(singleColumn(Seq.fill(10)(true)))
    executeBooleanPlan(
      between(gte(param("min")), lte(param("max"))),
      min = true,
      max = false
    ) should beColumns("r").withNoRows()
    executeBooleanPlan(
      between(gte(param("min")), lte(param("max"))),
      min = false,
      max = false
    ) should beColumns("r").withRows(singleColumn(Seq.fill(10)(false)))

    // [FALSE, TRUE] <= n.bool
    executeBooleanPlan(
      rangeExpression(gte(param("min"))),
      min = false
    ) should beColumns("r").withRows(singleColumn(Seq.fill(10)(false) ++ Seq.fill(10)(true)))
    executeBooleanPlan(
      rangeExpression(gte(param("min"))),
      min = true
    ) should beColumns("r").withRows(singleColumn(Seq.fill(10)(true)))
    // [FALSE, TRUE] < n.bool
    executeBooleanPlan(
      rangeExpression(gt(param("min"))),
      min = false
    ) should beColumns("r").withRows(singleColumn(Seq.fill(10)(true)))
    executeBooleanPlan(rangeExpression(gt(param("min"))), min = true) should beColumns("r").withNoRows()
    // [FALSE, TRUE] >= n.bool
    executeBooleanPlan(
      rangeExpression(lte(param("min"))),
      min = false
    ) should beColumns("r").withRows(singleColumn(Seq.fill(10)(false)))
    executeBooleanPlan(
      rangeExpression(lte(param("min"))),
      min = true
    ) should beColumns("r").withRows(singleColumn(Seq.fill(10)(false) ++ Seq.fill(10)(true)))
    // [FALSE, TRUE] > n.bool
    executeBooleanPlan(rangeExpression(lt(param("min"))), min = false) should beColumns("r").withNoRows()
    executeBooleanPlan(
      rangeExpression(lt(param("min"))),
      min = true
    ) should beColumns("r").withRows(singleColumn(Seq.fill(10)(false)))
  }

  /**
   * Tests so that we get equivalent results from a normal range index query
   * compared to what we get from a filtering vector stage query.
   */
  private val seed: Long = System.currentTimeMillis()
  private val random = RandomValues.create(new java.util.Random(seed))

  (1 to 100).foreach(i => {
    val dimension = random.intBetween(128, 1028)
    def randomVector = random.nextFloat32Vector(dimension, dimension)
    def randomValue: Value = random.nextValueOfTypes(
      ValueType.BOOLEAN,
      ValueType.STRING,
      ValueType.BYTE,
      ValueType.SHORT,
      ValueType.INT,
      ValueType.LONG,
      ValueType.FLOAT,
      ValueType.DOUBLE
    )
    val Seq(min, max) = Seq(randomValue, randomValue).sorted(comparatorToOrdering(Values.COMPARATOR))
    def randomSearchPredicate = {
      random.nextInt(6) match {
        case 0 => (rangeExpression(gt(param("min"))), s"n.prop > $min")
        case 1 => (rangeExpression(gte(param("min"))), s"n.prop >= $min")
        case 2 => (rangeExpression(lt(param("min"))), s"n.prop < $min")
        case 3 => (rangeExpression(lte(param("min"))), s"n.prop <= $min")
        case 4 => (between(gte(param("min")), lte(param("max"))), s"$min <= n.prop <= $max")
        case 5 => (SingleQueryExpression(param("min")), s"n.prop = $min")
        case _ => throw new IllegalStateException
      }
    }
    val (predicate, predicateString) = randomSearchPredicate
    val searchVector = randomVector

    def run(query: LogicalQuery) = {
      consume(execute(
        query,
        runtime,
        parameters =
          Map(
            "vector" -> searchVector,
            "min" -> min,
            "max" -> max
          )
      )).map(_(0))
    }

    def randomGraph(): Unit = {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v", "id")
      nodeIndex("RangeIndex", IndexType.RANGE, Seq("Foo"), "id")
      val write = tx.kernelTransaction().dataWrite
      val vectorToken = tx.kernelTransaction().tokenRead().propertyKey("v")
      val idToken = tx.kernelTransaction().tokenRead().propertyKey("id")
      nodeGraph(sizeHint, "Foo").foreach({
        n =>
          write.nodeSetProperty(n.getId, idToken, randomValue)
          write.nodeSetProperty(
            n.getId,
            vectorToken,
            randomVector
          )
      })
    }

    test(s"index equivalence test iteration=$i, seed=$seed, predicate: $predicateString") {
      // given
      givenGraph(randomGraph())

      // when
      val vectorQuery = new LogicalQueryBuilder(this)
        .produceResults("id")
        .projection("n.id AS id")
        .nodeVectorIndexSearch(
          node = "n",
          labelNames = Seq("Foo"),
          properties = Seq("v", "id"),
          indexName = "VectorIndex",
          vector = "$vector",
          limit = s"10000000",
          filter = Some(predicate)
        )
        .build()

      val rangeQuery = new LogicalQueryBuilder(this)
        .produceResults("id")
        .projection("n.id AS id")
        .nodeIndexOperator("n:Foo(id)", customQueryExpression = Some(predicate))
        .build()

      // then
      run(vectorQuery) should contain theSameElementsAs run(rangeQuery)
    }
  })

  test("comparing floating point and integer") {
    // given
    givenGraph {
      nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v", "id")
      val write = tx.kernelTransaction().dataWrite
      val vectorToken = tx.kernelTransaction().tokenRead().propertyKey("v")
      val idToken = tx.kernelTransaction().tokenRead().propertyKey("id")
      nodeGraph(1, "Foo").foreach({
        n =>
          write.nodeSetProperty(n.getId, idToken, longValue(1))
          write.nodeSetProperty(
            n.getId,
            vectorToken,
            random.nextFloat32Vector(128, 128)
          )
      })
    }

    // when
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("id")
      .projection("n.id AS id")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo"),
        properties = Seq("v", "id"),
        indexName = "VectorIndex",
        vector = "$vector",
        limit = s"10000000",
        filter = Some(SingleQueryExpression(param("p")))
      )
      .build()

    val runtimeResult =
      execute(
        logicalQuery,
        runtime,
        parameters =
          Map(
            "vector" -> random.nextFloat32Vector(128, 128),
            "p" -> 1.1
          )
      )

    // then
    runtimeResult should beColumns("id").withNoRows()
  }

  private def booleanVectorGraph(size: Int): Unit = {
    val random = new Random()
    nodeIndex("VectorIndex", IndexType.VECTOR, Seq("Foo"), "v", "bool")
    val write = tx.kernelTransaction().dataWrite
    val vectorToken = tx.kernelTransaction().tokenRead().propertyKey("v")
    val boolToken = tx.kernelTransaction().tokenRead().propertyKey("bool")
    nodeGraph(size, "Foo").zipWithIndex.foreach({
      case (n, i) =>
        write.nodeSetProperty(n.getId, boolToken, Values.booleanValue(i < size / 2))
        write.nodeSetProperty(
          n.getId,
          vectorToken,
          float32Vector((1 to sizeHint).map(_ => random.between(0f, 10f)): _*)
        )
    })
  }

  private def executeBooleanPlan(
    rangePredicate: RangeQueryExpression[InequalitySeekRangeWrapper],
    min: Boolean = false,
    max: Boolean = true
  ) = {
    val logicalQuery = new LogicalQueryBuilder(this)
      .produceResults("r")
      .projection("n.bool AS r")
      .nodeVectorIndexSearch(
        node = "n",
        labelNames = Seq("Foo"),
        properties = Seq("v", "bool"),
        indexName = "VectorIndex",
        vector = vectorAsCypherList(float32Vector(Seq.fill(sizeHint)(5.0f): _*)),
        limit = "10000",
        filter = Some(rangePredicate)
      )
      .build()

    execute(
      logicalQuery,
      runtime,
      parameters =
        Map(
          "min" -> Values.booleanValue(min),
          "max" -> Values.booleanValue(max)
        )
    )
  }

  private def between(gt: RangeGreaterThan[Expression], lt: RangeLessThan[Expression]) = {
    rangeExpression(
      RangeBetween(
        gt,
        lt
      )
    )
  }

  private def rangeExpression(e: InequalitySeekRange[Expression]): RangeQueryExpression[InequalitySeekRangeWrapper] = {
    RangeQueryExpression(
      InequalitySeekRangeWrapper(e)(pos)
    )
  }
  private def gt(e: Expression) = RangeGreaterThan(NonEmptyList(ExclusiveBound(e)))
  private def gte(e: Expression) = RangeGreaterThan(NonEmptyList(InclusiveBound(e)))
  private def lt(e: Expression) = RangeLessThan(NonEmptyList(ExclusiveBound(e)))
  private def lte(e: Expression) = RangeLessThan(NonEmptyList(InclusiveBound(e)))
  private def param(name: String) = parameter(name, CTAny)

  private def vectorAsCypherList(v: VectorValue): String = {
    var i = 0
    val builder = new StringBuilder
    builder.append("[")
    while (i < v.dimensions()) {
      builder.append(v.doubleValue(i))
      if (i < v.dimensions() - 1) {
        builder.append(", ")
      }
      i += 1
    }
    builder.append("]")
    builder.toString()
  }
}

object NodeVectorIndexSearchTestBase {}
