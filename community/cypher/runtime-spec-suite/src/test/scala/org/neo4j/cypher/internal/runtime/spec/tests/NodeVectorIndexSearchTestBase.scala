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
import org.neo4j.configuration.GraphDatabaseInternalSettings.cypher_pipelined_batch_size_big
import org.neo4j.cypher.internal.CypherRuntime
import org.neo4j.cypher.internal.RuntimeContext
import org.neo4j.cypher.internal.runtime.spec.Edition
import org.neo4j.cypher.internal.runtime.spec.LogicalQueryBuilder
import org.neo4j.cypher.internal.runtime.spec.RuntimeTestSuite
import org.neo4j.dbms.database.DbmsRuntimeVersion
import org.neo4j.exceptions.CypherTypeException
import org.neo4j.exceptions.InvalidArgumentException
import org.neo4j.graphdb.schema.IndexType
import org.neo4j.internal.kernel.api.exceptions.schema.IndexNotFoundKernelException
import org.neo4j.kernel.KernelVersion
import org.neo4j.values.storable.NumberValue
import org.neo4j.values.storable.Values.float32Vector
import org.neo4j.values.storable.Values.float64Vector
import org.neo4j.values.storable.Values.int16Vector
import org.neo4j.values.storable.Values.int32Vector
import org.neo4j.values.storable.Values.int64Vector
import org.neo4j.values.storable.Values.int8Vector
import org.neo4j.values.storable.Values.longValue
import org.neo4j.values.storable.VectorValue

import scala.util.Random

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
