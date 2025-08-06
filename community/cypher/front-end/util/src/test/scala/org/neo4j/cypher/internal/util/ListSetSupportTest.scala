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
package org.neo4j.cypher.internal.util

import org.neo4j.cypher.internal.util.ListSetSupport.RichListSet
import org.neo4j.cypher.internal.util.collection.immutable.ListSet
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ListSetSupportTest extends AnyFunSuite with Matchers {

  test("group strings by length preserving order") {
    val strings = ListSet("foo", "", "a", "bar", "", "b", "a")
    val groups = strings.sequentiallyGroupBy(_.length)
    val expected =
      ListSet(
        3 -> ListSet("foo", "bar"),
        0 -> ListSet(""),
        1 -> ListSet("a", "b")
      )

    groups shouldEqual expected
  }
}
